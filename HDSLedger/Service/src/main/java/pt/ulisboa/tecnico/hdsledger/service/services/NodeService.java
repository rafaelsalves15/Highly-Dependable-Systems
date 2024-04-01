package pt.ulisboa.tecnico.hdsledger.service.services;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.io.IOException;
import java.text.MessageFormat;

import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.NoSuchElementException;

import java.sql.Timestamp;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ledger.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.*;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.util.Timer;
import java.util.TimerTask;

public class NodeService implements UDPService {

    protected static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    protected static final int ROUNDCHANGE_TIMEOUT = 2000;
    // Nodes configurations
    protected final ProcessConfig[] nodesConfig;

    protected boolean consensusStarted = false;
    // Current node is leader
    protected final ProcessConfig config;

    // Link to communicate with nodes
    protected final Link link;

    // Consensus instance -> Round -> List of prepare messages
    protected final MessageBucket prepareMessages = new MessageBucket();
    // Consensus instance -> Round -> List of commit messages
    protected final MessageBucket commitMessages = new MessageBucket();
    // Consensus instance -> Round -> List of round_change messages
    protected final MessageBucket roundChangeMessages = new MessageBucket();
    // Store if already received pre-prepare for a given <consensus, round>
    protected final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    protected final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    protected final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    protected final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    // Ledger 
    protected ArrayList<Transaction> ledger = new ArrayList<Transaction>();
    // ClientId -> RequestId | Last client request that has been answered 
    protected Map<Integer, Integer> clientRequests = new ConcurrentHashMap<>();

    protected Map<PublicKey, Account> accounts = new ConcurrentHashMap<>();

    public NodeService(Link link, ProcessConfig config, SystemConfig systemConfig) {

        this.link = link;
        this.config = config;
        this.nodesConfig = systemConfig.getNodes();

        // Create accounts for each Node 
        for (ProcessConfig nodeConf : systemConfig.getNodes()) {
            try {
                accounts.put(KeyGetter.getPublic(nodeConf), new Account(nodeConf.getId()));
            } catch (Exception e) {
                e.printStackTrace(); // TODO
            }
        }

        // Create accounts for each client
        for (ProcessConfig clientConf : systemConfig.getClients()) {
            try {
                accounts.put(KeyGetter.getPublic(clientConf), new Account(clientConf.getId()));
                clientRequests.put(clientConf.getId(), -1);
            } catch (Exception e) {
                e.printStackTrace(); // TODO
            }
        }
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public List<Transaction> getLedger() {
        return this.ledger;
    }

    private boolean isLeader(int round) {
        return ProcessConfigBuilder.isLeader(this.config.getId(), round);
    }

    public Account getAccount(int id) {
        try {
            return this.accounts.get(KeyGetter.getPublic(id));
        } catch (Exception e) {
            return null;
        }
    }

    public void ready() {

    }

    protected boolean isValidTransaction(Transaction transaction) {
        if (transaction.getAmount() < 0)
            return false;

        try {
            Account account = accounts.get(KeyGetter.fromString(transaction.getSource()));
            return account.getBalance() >= (transaction.getAmount() + Transaction.FEE); 
        } catch (Exception e) {
            return false;
        }
    }

    private void setTimer(InstanceInfo instanceInfo, int consensusInstance) {
        instanceInfo.stopTimer();

        instanceInfo.setTimer(() -> {
            synchronized (instanceInfo) {
                instanceInfo.setCurrentRound(instanceInfo.getCurrentRound() + 1);
            }

            RoundChangeMessage roundChangeMessage = new RoundChangeMessage(instanceInfo.getPreparedRound(), instanceInfo.getPreparedValue());

            ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE, instanceInfo.getClientMessage())
                    .setConsensusInstance(consensusInstance)
                    .setRound(instanceInfo.getCurrentRound())
                    .setMessage(roundChangeMessage.toJson())
                    .build();

            List<AuthedMessage> justification = getRoundChangeJustification(consensusMessage);
            consensusMessage.setPiggyback(justification);
            // Broadcast ROUND_CHANGE
            this.link.broadcast(consensusMessage);
        }, ROUNDCHANGE_TIMEOUT);
    }

    public boolean isValidQuorum(List<AuthedMessage> messages) {
        int countValid = 0;
        for (AuthedMessage message : messages){
            try {
                message.verify();
                countValid += 1;
            } catch (Exception e) {
                continue;
            }
        }

        return countValid >= ProcessConfigBuilder.getQuorumSize();
    }

    public List<AuthedMessage> getPrePrepareJustification(int instance, int round) {
        Optional<List<AuthedMessage>> quorum = roundChangeMessages.hasValidRoundChangeQuorum(instance, round);
        if (quorum.isPresent())
            return quorum.get();

        return null;
    }

    public List<AuthedMessage> getRoundChangeJustification(ConsensusMessage message) {
        RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

        if (roundChangeMessage.getPreparedRound() == -1 || roundChangeMessage.getValue() == null)
            return null;

        Optional<Pair<String, List<AuthedMessage>>> quorum = prepareMessages.getPrepareQuorum(message.getConsensusInstance(), roundChangeMessage.getPreparedRound());
        if (!quorum.isPresent())
            return null;

        Pair<String, List<AuthedMessage>> p = quorum.get();
        if (!p.getFirst().equals(roundChangeMessage.getValue()))
            return null;
        return p.getSecond();
    }

    public ConsensusMessage createConsensusMessage(String value, int instance, int round, int clientId, AuthedMessage clientMessage) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        InstanceInfo instanceInfo = this.instanceInfo.get(instance);

        List<AuthedMessage> justification = getPrePrepareJustification(instance, round);
        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE, clientMessage)
                .setConsensusInstance(instance)
                .setRound(round)
                .setClientId(clientId)
                .setMessage(prePrepareMessage.toJson())
                .build();
        consensusMessage.setPiggyback(justification);
        return consensusMessage;
    }

    public synchronized boolean applyTransaction(Transaction transaction, int leader) {
        // Only transfers are possible..
        try {
            PublicKey source = KeyGetter.fromString(transaction.getSource());
            PublicKey destination = KeyGetter.fromString(transaction.getDestination());

            int amount = transaction.getAmount();

            if (isValidTransaction(transaction)) {
                int previousBalance = accounts.get(source).getBalance();
                accounts.get(source).subBalance(amount + Transaction.FEE);
                assert(accounts.get(source).getBalance() == previousBalance - amount - Transaction.FEE);
                accounts.get(destination).addBalance(amount);
                accounts.get(KeyGetter.getPublic(leader)).addBalance(Transaction.FEE);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false; // TODO
        }
    }

    public int getBalance(PublicKey key) {
        return accounts.get(key).getBalance();
    }

    public void verify(Message message) throws Exception {
        try {
            message.verify();
        } catch (Exception e) {
            throw e;
        }
    }

    /*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public synchronized void startConsensus(String value, int clientId, AuthedMessage clientMessage) {
        try {
            verify(clientMessage);
        } catch (Exception e) {
            return; 
        }
        
        // If this client request has already been decided, ignore
        if (clientMessage.getDataAs(ClientMessage.class).getRequestId() <= clientRequests.get(clientId)) {
            return;
        }

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();

        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value, clientId, clientMessage));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return;
        }

        InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
        // Leader broadcasts PRE-PREPARE message
        if (this.isLeader(1)) {
           LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            this.link.broadcast(this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound(), instance.getClientId(), clientMessage));
        } else {
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }

        // Set timer with task to perform upon timeout
        setTimer(instance, localConsensusInstance);
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrePrepare(AuthedMessage authed) {
        try {
            verify(authed);
        } catch (Exception e) {
            return; 
        }

        ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

        ClientMessage clientMessage = message.getClientMessage().getDataAs(ClientMessage.class);
        // If this client request has already been decided, ignore
        if (clientMessage.getRequestId()< clientRequests.get(clientMessage.getSenderId())) {
            return;
        }

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        int senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        String value = prePrepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Verify if pre-prepare was sent by leader
        if (!ProcessConfigBuilder.isLeader(senderId, round))
            return;

        InstanceInfo instanceInfo = new InstanceInfo(value, message.getClientId(), message.getClientMessage());

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, instanceInfo);

        if (!justifyPrePrepare(authed))
            return;

        instanceInfo = this.instanceInfo.get(consensusInstance);
        // Set timer with task to perform upon timeout
        setTimer(instanceInfo, consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE, message.getClientMessage())
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setClientId(message.getClientId())
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

        this.link.broadcast(consensusMessage);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(AuthedMessage authed) {
        try {
            verify(authed);
        } catch (Exception e) {
            return; 
        }

        ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        int senderId = message.getSenderId();

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        String value = prepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(authed);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value, message.getClientId(), message.getClientMessage()));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        
        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getCommittedRound() != -1) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT, message.getClientMessage())
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setClientId(message.getClientId())
                    .setReplyTo(senderId)
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(instance.getCommitMessage().toJson())
                    .build();

            link.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(consensusInstance, round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
            instance.setPreparedValue(preparedValue.get());
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(preparedValue.get());
            instance.setCommitMessage(c);
            //instance.setCommittedRound(round);

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT, sendersMessage.iterator().next().getClientMessage())
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setClientId(instance.getClientId())
                    .setMessage(c.toJson())
                    .build();
            link.broadcast(m);
        }
    }


    protected void ledgerReply(boolean success, int clientId, int requestId) {
        TransferResponseMessage ledgerResponseMessage = new TransferResponseMessage(success);
        ClientMessage newMessage = new ClientMessage(config.getId(), Message.Type.RESPONSE_TRANSFER, requestId);

        newMessage.setMessage(ledgerResponseMessage.toJson());

        this.link.send(clientId, newMessage);
    }

    protected void balanceReply(int balance, int clientId, int requestId) {
        BalanceResponseMessage balanceResponseMessage = new BalanceResponseMessage(balance);
        ClientMessage newMessage = new ClientMessage(config.getId(), Message.Type.RESPONSE_BALANCE, requestId);

        newMessage.setMessage(balanceResponseMessage.toJson());

        this.link.send(clientId, newMessage);
    }


    /*
     * Handle commit messages and decide if there is a valid quorum
     *
     * @param message Message to be handled
     */
    public void uponCommit(AuthedMessage authed) {
        try {
            verify(authed);
        } catch (Exception e) {
            return; 
        }

        ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        Optional<String> commitValue;
        synchronized (commitMessages) {
            commitMessages.addMessage(authed);

            if (instance == null) {
                // Should never happen because only receives commit as a response to a prepare message
                MessageFormat.format(
                        "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                        config.getId(), message.getSenderId(), consensusInstance, round);
                return;
            }

            // Within an instance of the algorithm, each upon rule is triggered at most once
            // for any round r
            if (instance.getCommittedRound() >= round) {
                LOGGER.log(Level.INFO,
                        MessageFormat.format(
                                "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                                config.getId(), consensusInstance, round));
                return;
            }

            commitValue = commitMessages.hasValidCommitQuorum(consensusInstance, round);
        }

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);
            instance.setCommitMessage(message.deserializeCommitMessage());
            instance.stopTimer();

            String value = commitValue.get();

            // Only start a consensus instance if the last one was decided
            // We need to be sure that the previous value has been decided
            while (lastDecidedConsensusInstance.get() < consensusInstance - 1) {
                LOGGER.log(Level.INFO, "Last consensus hasn't finished");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            int requestId = message.getClientMessage().getDataAs(ClientMessage.class).getRequestId();
            boolean success = false; // If transfer
            int balance = -1; // If check balance
            boolean isCheckBalance = false;

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized(ledger) {
                // Increment size of ledger to accommodate current instance
                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() <= consensusInstance - 1) {
                    ledger.add(new Transaction());
                }
                
                // Verify that the request that has been processed hasn't been decided before
                synchronized(clientRequests) {
                    if (requestId > clientRequests.get(instance.getClientId())) {
                        Transaction transaction = Transaction.fromJson(value);
                        isCheckBalance = transaction.isCheckBalance();
                        // UGLY SOLUTION TO DISTINGUISH BETWEEN THE TWO REQUESTS
                        // LAST MINUTE CHANGE, WOULD NEED REFACTORING
                        if (!isCheckBalance) {
                            // Append the transaction to the ledger and apply it
                            success = applyTransaction(transaction, ProcessConfigBuilder.getLeader(instance.getCommittedRound()));
                            if (success) {
                                ledger.set(consensusInstance - 1, transaction);
                                // Update the last decided request
                                clientRequests.put(instance.getClientId(), requestId);
                            }
                        } else {
                            try {
                                PublicKey publicKey = KeyGetter.fromString(transaction.getSource());
                                balance = accounts.get(publicKey).getBalance();
                            } catch (Exception e) {
                                // TODO : Shouldnt get here
                            }
                        }
                    } else {
                        System.out.println(MessageFormat.format("{0}[{1}] - Request {2} was already decided", config.getId(), consensusInstance, requestId));
                        // If this request has been decided before, simply put an empty Transaction on the ledger
                        Transaction transaction = new Transaction();
                        ledger.set(consensusInstance - 1, transaction);
                    }
                }
                
                LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Current Ledger: {1}",
                            config.getId(), ledger));
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));

            if (!isCheckBalance) {
                ledgerReply(success, instance.getClientId(), message.getClientMessage().getDataAs(ClientMessage.class).getRequestId());
            } else {
                balanceReply(balance, instance.getClientId(), message.getClientMessage().getDataAs(ClientMessage.class).getRequestId());
            }
        }
    }

    public synchronized void uponRoundChange(AuthedMessage authed) {
        try {
            verify(authed);
        } catch (Exception e) {
            return; 
        }

        ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received ROUND_CHANGE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        roundChangeMessages.addMessage(authed);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            LOGGER.log(Level.SEVERE, MessageFormat.format(
                    "{0} - CRITICAL: Received ROUND_CHANGE message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round));
            return;
        }

        if (instance.getCommittedRound() != -1) {
            CommitMessage c = new CommitMessage(instance.getCommitMessage().getValue());
            instance.setCommitMessage(c);

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT, instance.getClientMessage())
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setClientId(instance.getClientId())
                    .setReplyTo(message.getSenderId())
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(c.toJson())
                    .build();

            link.send(message.getSenderId(), m);
            return;
        }

        // Check for f+1 messages with rj > ri
        int ri = instance.getCurrentRound(); // ri following the paper's nomenclature
        Optional<Integer> r_min = roundChangeMessages.hasValidRoundChangeFp1(consensusInstance, ri); 
        
        if (r_min.isPresent()) {
            instance.setCurrentRound(r_min.get());
            //roundChangeMessages.purgeLowerRounds(consensusInstance, r_min.get());
        
            RoundChangeMessage roundChangeMessage = new RoundChangeMessage(instance.getPreparedRound(), instance.getPreparedValue());

            ConsensusMessage newMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE, instance.getClientMessage())
                .setConsensusInstance(consensusInstance)
                .setMessage(roundChangeMessage.toJson())
                .setRound(r_min.get())
                .setClientId(instance.getClientId())
                .build();
            setTimer(instance, consensusInstance);

            List<AuthedMessage> justification = getRoundChangeJustification(newMessage);
            newMessage.setPiggyback(justification);
            this.link.broadcast(newMessage);
        }

        // upon Receiving a quorum Qrc of valid <ROUND-CHANGE, di, ri,_,_> ...
        Optional<List<AuthedMessage>> qrc = roundChangeMessages.hasValidRoundChangeQuorum(consensusInstance, ri);

        if (qrc.isPresent()) {
            List<AuthedMessage> quorum = qrc.get();

            try {
                if (this.isLeader(ri) && justifyRoundChange(consensusInstance, quorum)) {
                    Optional<Pair<Integer, String>> highest = highestPrepared(quorum);
                    
                    String value;
                    if (highest.isPresent()) {
                        value = highest.get().getSecond();
                    } else {
                        value = instance.getInputValue();
                    }

                    PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

                    List<AuthedMessage> justification = getPrePrepareJustification(consensusInstance, ri);

                    ConsensusMessage newMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE, instance.getClientMessage())
                        .setConsensusInstance(consensusInstance)
                        .setMessage(prePrepareMessage.toJson())
                        .setRound(ri)
                        .setClientId(instance.getClientId())
                        .build();
                    newMessage.setPiggyback(justification);
                    this.link.broadcast(newMessage);
                }
            } catch (Exception e) {
                e.printStackTrace(); // TODO
            }
        }
    }

    public boolean justifyRoundChange(int instance, List<AuthedMessage> quorum) throws Exception {
        if (quorum == null)
            throw new Exception("Invalid quorum");

        InstanceInfo instanceInfo = this.instanceInfo.get(instance);
        int preparedRound = instanceInfo.getPreparedRound();
        String preparedValue = instanceInfo.getPreparedValue();

        if (!quorum.stream()
            .map((message) -> message.getDataAs(ConsensusMessage.class).deserializeRoundChangeMessage())
            .filter((message) -> message.getPreparedRound() != -1)
            .findAny()
            .isPresent()) {
            return true;
        }

        Optional<Pair<Integer, String>> highest = highestPrepared(quorum);
        if (highest.isPresent()) {
            return quorum.stream().filter((authed) -> {
                RoundChangeMessage roundChangeMessage = authed.getDataAs(ConsensusMessage.class).deserializeRoundChangeMessage();

                return roundChangeMessage.getPreparedRound() == highest.get().getFirst() && roundChangeMessage.getValue().equals(highest.get().getSecond()) && isValidQuorum(authed.getPiggyback());
            }).findAny().isPresent();
        }
        return false;
    }

    public boolean justifyPrePrepare(AuthedMessage authedMessage) {
        ConsensusMessage message = authedMessage.getDataAs(ConsensusMessage.class);
        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        int round = message.getRound();
        int instance = message.getConsensusInstance();
        String value = prePrepareMessage.getValue();

        if (round == 1)
            return true;
        InstanceInfo instanceInfo = this.instanceInfo.get(instance);

        if (round < instanceInfo.getCurrentRound())
            return false;

        List<AuthedMessage> justification = authedMessage.getPiggyback();

        try {
            return justifyRoundChange(instance, justification);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Optional<Pair<Integer, String>> highestPrepared(List<AuthedMessage> quorum) {
        Optional<Integer> highest = Optional.empty();
        Optional<String> value = Optional.empty();
        
        for (AuthedMessage authed : quorum) {
            ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

            if (highest.isPresent()) {
                if (roundChangeMessage.getPreparedRound() >= highest.get()) {
                    highest = Optional.of(roundChangeMessage.getPreparedRound());
                    value = Optional.of(roundChangeMessage.getValue());
                }
            } else {
                if (roundChangeMessage.getPreparedRound() == -1) {
                    return Optional.empty();
                }

                highest = Optional.of(roundChangeMessage.getPreparedRound());
                value = Optional.of(roundChangeMessage.getValue());
            }
        }

        return Optional.of(new Pair<>(highest.get(), value.get()));
    }

    public synchronized void uponTransfer(AuthedMessage message) {
        ClientMessage clientMessage = null;

        // Check message validity
        try {
            clientMessage = message.getDataAs(ClientMessage.class);
            verify(message);
            verify(clientMessage);
        } catch (Exception e) {
            // Reply with failure
            TransferResponseMessage ledgerResponseMessage = new TransferResponseMessage(false);
            ClientMessage newMessage = new ClientMessage(config.getId(), Message.Type.RESPONSE_TRANSFER, clientMessage != null ? clientMessage.getRequestId() : 0);
            newMessage.setMessage(ledgerResponseMessage.toJson());
            this.link.send(message.getSenderId(), newMessage);
            return;
        }

        TransferMessage transferMessage = clientMessage.deserializeTransferMessage();

        Transaction transaction = new Transaction(transferMessage.getSource(), transferMessage.getDestination(), transferMessage.getAmount());

        startConsensus(transaction.toJson(), clientMessage.getSenderId(), message);
    }

    public synchronized void uponCheckBalance(AuthedMessage message) {
        ClientMessage clientMessage = null;

        // Check message validity
        try {
            clientMessage = message.getDataAs(ClientMessage.class);
            verify(message);
            verify(clientMessage);
        } catch (Exception e) {
            // Reply with failure
            BalanceResponseMessage ledgerResponseMessage = new BalanceResponseMessage(-1);
            ClientMessage newMessage = new ClientMessage(config.getId(), Message.Type.RESPONSE_BALANCE, clientMessage != null ? clientMessage.getRequestId() : 0);
            newMessage.setMessage(ledgerResponseMessage.toJson());
            this.link.send(message.getSenderId(), newMessage);
            return;
        }

        try {
            CheckBalanceMessage checkBalanceMessage = clientMessage.deserializeCheckBalanceMessage();
            Transaction transaction = new Transaction(checkBalanceMessage.getAccountPubKey());

            startConsensus(transaction.toJson(), clientMessage.getSenderId(), message);
        } catch (Exception e) {
            e.printStackTrace(); // TODO
        }
    }

    @Override
    public void listen() {
        this.ready();
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        
                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {

                                case PRE_PREPARE ->
                                    uponPrePrepare((AuthedMessage) message);
                                case PREPARE ->
                                    uponPrepare((AuthedMessage) message);
                                case COMMIT ->
                                    uponCommit((AuthedMessage) message);
                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                            config.getId(), message.getSenderId()));
                                case IGNORE ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    config.getId(), message.getSenderId()));
                                case ROUND_CHANGE -> {
                                    uponRoundChange((AuthedMessage) message);
                                }

                                case TRANSFER -> {
                                    uponTransfer((AuthedMessage) message);
                                }

                                case CHECK_BALANCE -> {
                                    uponCheckBalance((AuthedMessage) message);
                                }
                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));

                            }

                        }).start();
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
