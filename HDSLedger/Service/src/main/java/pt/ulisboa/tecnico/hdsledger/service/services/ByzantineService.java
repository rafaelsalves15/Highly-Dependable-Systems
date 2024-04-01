package pt.ulisboa.tecnico.hdsledger.service.services;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ledger.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.*;


public class ByzantineService extends NodeService {

    public enum Behaviour {
        SLEEP,
        FALSIFY_CONSENSUS,
        CLIENT_SPECIFIC_IGNORANCE,
        LEADER_OVERCHARGES_FEE,
        LEADER_ECHOES_REQUEST,
        START_CONSENSUS_THEN_SLEEP,
        RESPOND_IMMEDIATELY,
        DUPLICATE_REPLY,
        CHANGE_DATA,
        DO_NOTHING
    }

    public enum FunctionCall {
        READY,
        START_CONSENSUS,
        UPON_PREPREPARE,
        UPON_PREPARE,
        UPON_COMMIT,
        UPON_ROUNDCHANGE,
        APPLY_TRANSACTION,
        UPON_TRANSFER,
        VERIFY,
        LEDGER_REPLY,
    }

    private class Args {
        public String value;
        public int clientId;
        public AuthedMessage message;
        public int leaderId;
        public Transaction transaction;
        public Message genericMessage;
        public boolean success;
        public int requestId;

        public FunctionCall function;

        public Args(FunctionCall function) {
            this.function = function;
        }
        public Args(FunctionCall function, AuthedMessage message) {
            this.function = function;
            this.message = message;
        }
        public Args(FunctionCall function, Message message) {
            this.function = function;
            this.genericMessage = message;
        }
        public Args(FunctionCall function, String value, int clientId, AuthedMessage message) {
            this.function = function;
            this.value = value;
            this.clientId = clientId;
            this.message = message;
        }
        public Args(FunctionCall function, Transaction transaction, int leader){
            this.function = function;
            this.transaction = transaction;
            this.leaderId = leader;
        }
        public Args(FunctionCall function, boolean success, int clientId, int requestId){
            this.function = function;
            this.success = success;
            this.clientId = clientId;
            this.requestId = requestId;
        }
    }

    private List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();

    /*
     * Return value -> continue execution ?
     */
    private boolean doBehaviour(Behaviour behaviour, Args args) {
        try {
            switch (behaviour) {
                case SLEEP ->  {
                    System.out.println("Sleeping");
                    Thread.sleep(10000);
                }
                
                case FALSIFY_CONSENSUS -> {
                    assert(args.function == FunctionCall.READY);
                    System.out.println("Falsifying consensus");

                    // Set initial consensus values
                    int localConsensusInstance = this.consensusInstance.incrementAndGet();

                    int source = ProcessConfigBuilder.getSystemConfig().getClients()[0].getId();
                    int dest = ProcessConfigBuilder.getSystemConfig().getClients()[1].getId();
                    Transaction transaction = new Transaction(KeyGetter.getPublic(source), KeyGetter.getPublic(dest), 100);

                    TransferMessage transferMessage = new TransferMessage(transaction.getSource(), transaction.getDestination(), transaction.getAmount());
                    ClientMessage clientMessage = new ClientMessage(source, Message.Type.TRANSFER, 1);
                    clientMessage.setMessage(transferMessage.toJson());
    
                    AuthedMessage authed = new AuthedMessage(clientMessage);
                    InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(transaction.toJson(), source, authed));

                    InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
                    this.link.broadcast(this.createConsensusMessage(transaction.toJson(), localConsensusInstance, instance.getCurrentRound(), instance.getClientId(), authed));

                    return false;
                }

                case CLIENT_SPECIFIC_IGNORANCE -> {
                    assert(args.function == FunctionCall.START_CONSENSUS);
                    System.out.println("Ignoring requests from a specific client");
                    
                    int ignoredClientId = 6;

                    if (args.clientId == ignoredClientId)
                        return false;
                    
                    return true;
                }

                case LEADER_OVERCHARGES_FEE -> {
                    assert(args.function == FunctionCall.APPLY_TRANSACTION);
                    int leaderId = args.leaderId;
                    Transaction transaction = args.transaction;
                    System.out.println("Leader will overcharge client fee");
            
                    try {
                        PublicKey source = KeyGetter.fromString(transaction.getSource());
                        PublicKey destination = KeyGetter.fromString(transaction.getDestination());

                        int amount = transaction.getAmount();

                        if (isValidTransaction(transaction)) {
                            //OVERCHARGING FEE (10x)
                            accounts.get(source).subBalance(amount + Transaction.FEE * 10);
                            accounts.get(destination).addBalance(amount);
                            accounts.get(KeyGetter.getPublic(leaderId)).addBalance(Transaction.FEE *10);
                            return false;
                        } else {
                            return false;
                        }
                    } catch (Exception e) {
                        return false; // TODO
                    }
                }

                case LEADER_ECHOES_REQUEST -> {
                    assert(args.function == FunctionCall.START_CONSENSUS);
                    System.out.println("Echoing request");
                    super.startConsensus(args.value, args.clientId, args.message);
                    super.startConsensus(args.value, args.clientId, args.message);
                    return false;
                }

                case RESPOND_IMMEDIATELY -> {
                    assert(args.function == FunctionCall.START_CONSENSUS);
                    ledgerReply(false, args.clientId, args.message.getDataAs(ClientMessage.class).getRequestId());
                    return false;
                }

                case START_CONSENSUS_THEN_SLEEP -> {
                    assert(args.function == FunctionCall.START_CONSENSUS);
                    System.out.println("Starting consensus");
                    super.startConsensus(args.value, args.clientId, args.message);
                    System.out.println("Going to sleep");
                    Thread.sleep(20000);
                    return false;
                }

                case DO_NOTHING -> {
                    return false;
                }

                case DUPLICATE_REPLY -> {
                    assert(args.function == FunctionCall.LEDGER_REPLY);
                    super.ledgerReply(args.success, args.clientId, args.requestId);
                    super.ledgerReply(args.success, args.clientId, args.requestId);
                    return false;
                }
                case CHANGE_DATA -> {
                    
                    assert(args.function == FunctionCall.UPON_PREPARE);
                    System.out.println("Altering Transaction data");

                    AuthedMessage authed = args.message;
                    String data = authed.getData();


                    ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

                    int consensusInstance = message.getConsensusInstance();
                    int round = message.getRound();
                    int senderId = 6;

                    PrepareMessage prepareMessage = message.deserializePrepareMessage();

                    String value = prepareMessage.getValue();

                                // Doesn't add duplicate messages
                    prepareMessages.addMessage(authed);

                    // Set instance values
                    this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value, message.getClientId(), message.getClientMessage()));
                    InstanceInfo instance = this.instanceInfo.get(consensusInstance);
                    

                    ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT, message.getClientMessage())
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setClientId(message.getClientId())
                            .setReplyTo(senderId)
                            .setReplyToMessageId(message.getMessageId())
                            .setMessage(instance.getCommitMessage().toJson())
                            .build();

                    link.send(senderId, m);
                  

                    return false;
                }

                default -> {}
            }
        } catch (Exception e) {
            // TODO
        }
        return true;
    }


    /*
     * Return value -> continue execution ?
     */
    private boolean doBehavioursAtFunc(Args args) {
        int overrides = 0;

        for (Pair<FunctionCall, Behaviour> p : this.behaviours) {
            if (p.getFirst() == args.function) {
                overrides += doBehaviour(p.getSecond(), args) ? 0 : 1;
            }
        }

        // If more than one behaviour overrides the function, it is not good :/
        assert(overrides <= 1);

        return overrides == 0;
    }

    public ByzantineService(Link link, ProcessConfig config, SystemConfig systemConfig) {
        super(link, config, systemConfig);
    }

    public ByzantineService addBehaviour(FunctionCall function, Behaviour behaviour) {
        this.behaviours.add(new Pair<>(function, behaviour)); 
        return this;
    }

    public ByzantineService addBehaviour(Pair<FunctionCall, Behaviour> behaviour) {
        this.behaviours.add(behaviour); 
        return this;
    }

    @Override
    public void ready() {
        doBehavioursAtFunc(new Args(FunctionCall.READY));
    }

    @Override
    public synchronized void startConsensus(String value, int clientId, AuthedMessage clientMessage) {
        if (doBehavioursAtFunc(new Args(FunctionCall.START_CONSENSUS, value, clientId, clientMessage)))
            super.startConsensus(value, clientId, clientMessage);
    }

    @Override
    public synchronized void uponPrepare(AuthedMessage message) {
        if (doBehavioursAtFunc(new Args(FunctionCall.UPON_PREPARE, message)))
            super.uponPrepare(message);
    }

    @Override
    public synchronized void uponPrePrepare(AuthedMessage message) {
        if (doBehavioursAtFunc(new Args(FunctionCall.UPON_PREPREPARE, message)))
            super.uponPrePrepare(message);
    }

    @Override
    public synchronized void uponCommit(AuthedMessage message) {
        if (doBehavioursAtFunc(new Args(FunctionCall.UPON_COMMIT, message)))
        super.uponCommit(message);
    }

    @Override
    public synchronized void uponRoundChange(AuthedMessage message) {
        if (doBehavioursAtFunc(new Args(FunctionCall.UPON_ROUNDCHANGE, message)))
            super.uponRoundChange(message);
    }

    @Override
    public synchronized boolean applyTransaction(Transaction transaction, int leader) {
        if (doBehavioursAtFunc(new Args(FunctionCall.APPLY_TRANSACTION, transaction, leader)))
            return super.applyTransaction(transaction,leader);
        return true;    
    }
    
    @Override
    public synchronized void uponTransfer(AuthedMessage message) {
        if (doBehavioursAtFunc(new Args(FunctionCall.UPON_TRANSFER, message)))
            super.uponTransfer(message);
    }

    @Override
    public void verify(Message message) throws Exception {
        if (doBehavioursAtFunc(new Args(FunctionCall.VERIFY, message)))
            super.verify(message);
    } 
    
    @Override
    protected void ledgerReply(boolean success, int clientId, int requestId) {
        if (doBehavioursAtFunc(new Args(FunctionCall.LEDGER_REPLY, success, clientId, requestId)))
            super.ledgerReply(success, clientId, requestId);
    } 

}
