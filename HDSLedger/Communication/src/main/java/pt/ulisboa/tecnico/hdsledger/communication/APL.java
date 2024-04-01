package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.messages.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.messages.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ledger.*;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.sql.Timestamp;
import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class APL implements Link {

    private static final CustomLogger LOGGER = new CustomLogger(Link.class.getName());
    // Time to wait for an ACK before resending the message
    private final int BASE_SLEEP_TIME;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<Integer, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Map of all clients in the network
    private final Map<Integer, ProcessConfig> clients = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;
    // Set of received messages from specific node (prevent duplicates)
    private final Map<Integer, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private final CollapsingSet receivedAcks = new CollapsingSet();
    // Message counter
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    // Send messages to self by pushing to queue instead of through the network
    private final Queue<AuthedMessage> localhostQueue = new ConcurrentLinkedQueue<>();
    // Save timestamps for each id
    private Map<Integer, Timestamp> timestampMap = new ConcurrentHashMap<>();

    public final String hostname;
    public final int port;

    public APL(ProcessConfig self, int port, ProcessConfig[] nodes, ProcessConfig[] clients, Class<? extends Message> messageClass) {
        this(self, port, nodes, clients, messageClass, false, 200);
    }

    public APL(ProcessConfig self, int port, ProcessConfig[] nodes, ProcessConfig[] clients, Class<? extends Message> messageClass, boolean activateLogs, int baseSleepTime) {

        this.config = self;
        this.messageClass = messageClass;
        this.BASE_SLEEP_TIME = baseSleepTime;
        this.port = port;
        this.hostname = config.getHostname();

        Arrays.stream(nodes).forEach(node -> {
            int id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        Arrays.stream(clients).forEach(node -> {
            int id = node.getId();
            this.clients.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new HDSSException(ErrorMessage.CannotOpenSocket);
        }
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    public ProcessConfig getNode(int id) {
        return this.nodes.get(id);
    }

    public ProcessConfig getClient(int id) {
        return this.clients.get(id);
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    private boolean isValidTimestamp(int id, int minDifference, Timestamp messageTimestamp) {
        Timestamp lastTimestamp = timestampMap.get(id);
        if (lastTimestamp != null) {
            long timeDifference = messageTimestamp.getTime() - lastTimestamp.getTime();
            if (timeDifference >= minDifference) {
                timestampMap.put(id, messageTimestamp); // Update the map with the new timestamp
                return true;
            }
        } else {
            timestampMap.put(id, messageTimestamp); // Add new user to the map
            return true;
        }
        return false;
    }

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcasted
     */
    public void broadcast(Message data) {
        Gson gson = new Gson();
        nodes.forEach((destId, dest) -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
    }
   

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    public void send(int nodeId, Message data) {

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);

                if (node == null)
                    node = clients.get(nodeId);
                if (node == null)
                    throw new HDSSException(ErrorMessage.NoSuchNode);

                data.setMessageId(messageCounter.getAndIncrement());

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());
                int destPort = node.getPort();
                int count = 1;
                int messageId = data.getMessageId();
                int sleepTime = BASE_SLEEP_TIME;

                // Send message to local queue instead of using network if destination in self
                if (nodeId == this.config.getId()) {
                    this.localhostQueue.add(new AuthedMessage(data));

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} successfully",
                                    config.getId(), data.getType(), destAddress, destPort));

                    return;
                }

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++));

                    unreliableSend(destAddress, destPort, data);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} successfully",
                        config.getId(), data.getType(), destAddress, destPort));
            } catch (Exception e) { // TODO
                e.printStackTrace();
            }
        }).start();
    }

    /*
     * Sends a message to a specific node without guarantee of delivery
     * Mainly used to send ACKs, if they are lost, the original message will be
     * resent
     *
     * @param address The address of the destination node
     *
     * @param port The port of the destination node
     *
     * @param data The message to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, Message data) {
        new Thread(() -> {
            try {
                byte[] buf = new Gson().toJson(new AuthedMessage(data)).getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
                socket.send(packet);
            } catch (Exception e) {
                // TODO : for now, surpress exceptions :/
                //e.printStackTrace();
                //throw new HDSSException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }

    /*
     * Receives a message from any node in the network (blocking)
     */
    public Message receive() {
        Message message = null;
        AuthedMessage authed = null;
        String serialized = "";
        Boolean local = false;
        DatagramPacket response = null;

        while (message == null) {
            try {
                if (this.localhostQueue.size() > 0) {
                    authed = this.localhostQueue.poll();
                    local = true; 
                    this.receivedAcks.add(authed.getMessageId());
                } else {
                    byte[] buf = new byte[65535];
                    response = new DatagramPacket(buf, buf.length);

                    socket.receive(response);

                    byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
                    serialized = new String(buffer);
                    authed = new Gson().fromJson(serialized, AuthedMessage.class);
                }
                
                authed.verify();

                message = authed.getDataAsMessage();

                int senderId = message.getSenderId();
                int messageId = message.getMessageId();

                LOGGER.log(Level.INFO, MessageFormat.format("Received message [{0}] from {1}: {2}", messageId, message.getSenderId(), message.getType()));

                boolean isClient = false;
                if (clients.containsKey(senderId))
                    isClient = true;
                else if (!nodes.containsKey(senderId))
                    throw new HDSSException(ErrorMessage.NoSuchNode);

                // Handle ACKS, since it's possible to receive multiple acks from the same
                // message
                if (message.getType().equals(Message.Type.ACK)) {
                    receivedAcks.add(messageId);
                    return message;
                }

                //To avoid DoS attacks we check if timestamp is within valid window 
                if (!isValidTimestamp(authed.getSenderId(), isClient ? 500 : 0, new Timestamp(System.currentTimeMillis()))) {
                    LOGGER.log(Level.WARNING, "Received message with invalid timestamp from " + message.getSenderId());
                    throw new Exception();
                }

                // It's not an ACK -> Deserialize for the correct type
                if (ProcessConfigBuilder.isClient(senderId) || ProcessConfigBuilder.isClient(config.getId())) { // If a client sent this message, turn it into a ClientMessage 
                    message = authed.getDataAs(ClientMessage.class);
                } else {
                    message = authed.getDataAs(this.messageClass);
                }

                boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
                Type originalType = message.getType();
                // Message already received (add returns false if already exists) => Discard
                if (isRepeated) {
                    message.setType(Message.Type.IGNORE);
                }

                switch (message.getType()) {
                    case CHECK_BALANCE, TRANSFER -> {
                        message = authed; // Important! For client requests, the AuthMessage is retured in order to preserve the MAC signature
                    }
                    case ACK, RESPONSE_BALANCE, RESPONSE_TRANSFER -> {}
                   
                    case IGNORE -> {
                        if (!originalType.equals(Type.COMMIT))
                            return message;
                    }
                    default -> {
                        ConsensusMessage consensusMessage = (ConsensusMessage) message;
                        try {
                            consensusMessage.verify();
                        } catch (Exception e ) {
                            throw e;
                        }

                        if (consensusMessage.getReplyTo() == config.getId())
                            receivedAcks.add(consensusMessage.getReplyToMessageId());

                        message = authed; // Every consensus message is returned as the authed version
                    }
                }

                if (!local) {
                    InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
                    int port = response.getPort();

                    Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
                    responseMessage.setMessageId(messageId);

                    // ACK is sent without needing for another ACK because
                    // we're assuming an eventually synchronous network
                    // Even if a node receives the message multiple times,
                    // it will discard duplicates
                    unreliableSend(address, port, responseMessage);
                }
            } catch (Exception e ) {
                message = null;
            }
        }
        return message;
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public Map<Integer, ProcessConfig> getNodes() {
        return this.nodes;
    }
}
