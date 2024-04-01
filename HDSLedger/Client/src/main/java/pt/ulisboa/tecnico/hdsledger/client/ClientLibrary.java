package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.messages.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ledger.*;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.Optional;
import java.util.logging.Level;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;

import com.google.gson.Gson;

import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.messages.*;

public class ClientLibrary {
    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());

    private Link linkToNodes;
    private int id;
    private Map<Integer, ClientMessage> responses = new ConcurrentHashMap<>();
    private AtomicBoolean waitResponses = new AtomicBoolean(true);
    private AtomicInteger currentRequest = new AtomicInteger(0);
    private SystemConfig systemConfig;

    public ClientLibrary(int id) throws Exception {
        this.systemConfig = ProcessConfigBuilder.getSystemConfig();
        ProcessConfig[] nodeConfigs = systemConfig.getNodes();
        ProcessConfig[] clientConfigs = systemConfig.getClients();
        
        ProcessConfig config = Arrays.stream(clientConfigs).filter(c -> c.getId() == id).findFirst().get();

        this.id = id;
        this.linkToNodes = new APL(config, config.getPort(), nodeConfigs, clientConfigs, ClientMessage.class);
    }
   
    /*
     * If f + 1 messages with the same success value, return that value
     */
    public boolean listenTransferMessages() {
        return listen(() -> {
            Map<Boolean, Integer> count = new ConcurrentHashMap<>();
            for (ClientMessage message : responses.values()) {
                boolean result = message.deserializeTransferResponseMessage().wasSuccessful();
                count.put(result, count.getOrDefault(result, 0) + 1);
            }

            for (Map.Entry<Boolean, Integer> entry : count.entrySet()) {
                if (entry.getValue() >= ProcessConfigBuilder.getMaxFaulty() + 1)
                    return Optional.of(entry.getKey());
            }

            return Optional.empty();
        });
    }
    /*
     * If f + 1 messages with the same balance, return that balance
     */
    public int listenBalanceMessages() {
        return listen(() -> {
            Map<Integer, Integer> count = new ConcurrentHashMap<>();
            for (ClientMessage message : responses.values()) {
                int result = message.deserializeBalanceResponseMessage().getBalance();
                count.put(result, count.getOrDefault(result, 0) + 1);
            }

            for (Map.Entry<Integer, Integer> entry : count.entrySet()) {
                if (entry.getValue() >= ProcessConfigBuilder.getMaxFaulty() + 1) 
                    return Optional.of(entry.getKey());
            }

            return Optional.empty();
        });
    }

    public boolean transfer(PublicKey sourcePublicKey, PublicKey destinationPublicKey, int amount) throws Exception {
        if (amount <= 0 ){
            throw new HDSSException(ErrorMessage.AmountNotValid);
        }
        if (sourcePublicKey.equals(destinationPublicKey)){
            throw new HDSSException(ErrorMessage.InvalidDestination);
        }

        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();
        ProcessConfig[] nodeConfigs = systemConfig.getNodes();
        ProcessConfig[] clientConfigs = systemConfig.getClients();
        
        ProcessConfig config = ProcessConfigBuilder.fromId(id);

        //Convert publickKeys to strings
        String source       = Base64.getEncoder().encodeToString(sourcePublicKey.getEncoded());
        String destination  = Base64.getEncoder().encodeToString(destinationPublicKey.getEncoded());
        
        TransferMessage req = new TransferMessage(source, destination, amount);
        
        ClientMessage clientReq = new ClientMessage(id, Message.Type.TRANSFER, currentRequest.incrementAndGet());
        clientReq.setMessage(req.toJson());

        this.linkToNodes.broadcast(clientReq);
        boolean success = listenTransferMessages();

        return success;
    }

    public int check_balance(PublicKey accountPublicKey){ 
        //Convert publickKey to string
        String publicKeyString = Base64.getEncoder().encodeToString(accountPublicKey.getEncoded());
        CheckBalanceMessage req = new CheckBalanceMessage(publicKeyString);
        
        ClientMessage clientReq = new ClientMessage(id, Message.Type.CHECK_BALANCE, currentRequest.incrementAndGet());
        clientReq.setMessage(req.toJson());

        this.linkToNodes.broadcast(clientReq);

        int balance = listenBalanceMessages();

        return balance;
    }

    public void uponResponse(ClientMessage message) {
        try {
            message.verify();
        } catch (Exception e) {
            return;
        }
        if (message.getRequestId() == currentRequest.get())
            responses.putIfAbsent(message.getSenderId(), message);
    }

    /*
     * Receives a callable stopCondition which returns an Optional
     * If the condition to stop listening to messages is reached, stopCondition() should return a non empty Optional<T> yielding the result
     * If the condition hasn't been reached, return an empty Optional
     */
    public <T> T listen(Callable<Optional<T>> stopCondition) {
        try {
            waitResponses.set(true); 
            responses = new ConcurrentHashMap<>();
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (waitResponses.get()) {
                        Message message = this.linkToNodes.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {
                                case RESPONSE_BALANCE, RESPONSE_TRANSFER -> 
                                    uponResponse((ClientMessage) message);
                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}", this.id, message.getSenderId()));
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start(); 

            Optional<T> result = Optional.empty();
            while (!result.isPresent())
                result = stopCondition.call();

            waitResponses.set(false);  // Stop waiting for responses
            return result.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getId() {
        return this.id;
    }

    public Map<Integer, ClientMessage> getResponses() {
        return this.responses;
    }

    public Link getLink() {
        return this.linkToNodes;
    }

}
