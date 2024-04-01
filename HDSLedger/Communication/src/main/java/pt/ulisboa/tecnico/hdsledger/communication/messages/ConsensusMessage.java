package pt.ulisboa.tecnico.hdsledger.communication.messages;

import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ledger.*;
import pt.ulisboa.tecnico.hdsledger.utilities.Transaction;
import com.google.gson.Gson;

public class ConsensusMessage extends Message {

    // Consensus instance
    private int consensusInstance;
    // Round
    private int round;
    // Who sent the previous message
    private int replyTo;
    // Id of the previous message
    private int replyToMessageId;
    // Message (PREPREPARE, PREPARE, COMMIT)
    private String message;
    // AuthedMessage of ClientMessage of the original request
    private String clientMessage;
    // Client ID
    private int clientId;

    public ConsensusMessage(int senderId, Type type, AuthedMessage clientMessage) {
        super(senderId, type);
        this.clientMessage = clientMessage.toJson();
    }

    public PrePrepareMessage deserializePrePrepareMessage() {
        return new Gson().fromJson(this.message, PrePrepareMessage.class);
    }

    public PrepareMessage deserializePrepareMessage() {
        return new Gson().fromJson(this.message, PrepareMessage.class);
    }

    public CommitMessage deserializeCommitMessage() {
        return new Gson().fromJson(this.message, CommitMessage.class);
    }

    public RoundChangeMessage deserializeRoundChangeMessage() {
        return new Gson().fromJson(this.message, RoundChangeMessage.class);
    }
    
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(int replyTo) {
        this.replyTo = replyTo;
    }

    public int getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(int replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public int getClientId() {
        return this.clientId;
    }

    public AuthedMessage getClientMessage() {
        return new Gson().fromJson(this.clientMessage, AuthedMessage.class);
    }

    @Override
    public void verify() throws Exception {
        if (getType() == Message.Type.ACK)
            return;

        AuthedMessage authedMessage = getClientMessage();
        authedMessage.verify();

        ClientMessage clientMessage = authedMessage.getDataAs(ClientMessage.class);
        clientMessage.verify();

        switch (clientMessage.getType()) {
            case TRANSFER -> {
                TransferMessage transferMessage = clientMessage.deserializeTransferMessage();

                // Verify that the author of the request is the owner of the source account
                if (!authedMessage.getKey().equals(transferMessage.getSource()))
                    throw new Exception("Author is not source");

                String value;
                switch (this.getType()) {
                    case PRE_PREPARE -> {
                        PrePrepareMessage prePrepareMessage = deserializePrePrepareMessage();
                        value = prePrepareMessage.getValue();
                    }
                    case PREPARE -> {
                        PrepareMessage prepareMessage = deserializePrepareMessage();
                        value = prepareMessage.getValue();
                    }
                    case COMMIT -> {
                        CommitMessage commitMessage = deserializeCommitMessage();
                        value = commitMessage.getValue();
                    }
                    case ROUND_CHANGE -> {
                        return;
                    }

                    default -> throw new Exception("Invalid consensus message type");
                }
                
                // Verify that the transaction being forwaded during consensus corresponds to the author's intended original transaction
                Transaction transaction = new Transaction(transferMessage.getSource(), transferMessage.getDestination(), transferMessage.getAmount());

                if (!transaction.toJson().equals(value))
                    throw new Exception("Transaction is not the same");
            }

            case CHECK_BALANCE -> {
                CheckBalanceMessage checkBalanceMessage = clientMessage.deserializeCheckBalanceMessage();

                // Verify that the author of the request is the owner of the source account
                if (!authedMessage.getKey().equals(checkBalanceMessage.getAccountPubKey()))
                    throw new Exception("Author is not source");

                String value;
                switch (this.getType()) {
                    case PRE_PREPARE -> {
                        PrePrepareMessage prePrepareMessage = deserializePrePrepareMessage();
                        value = prePrepareMessage.getValue();
                    }
                    case PREPARE -> {
                        PrepareMessage prepareMessage = deserializePrepareMessage();
                        value = prepareMessage.getValue();
                    }
                    case COMMIT -> {
                        CommitMessage commitMessage = deserializeCommitMessage();
                        value = commitMessage.getValue();
                    }
                    case ROUND_CHANGE -> {
                        return;
                    }

                    default -> throw new Exception("Invalid consensus message type");
                }
                
                // Verify that the transaction being forwaded during consensus corresponds to the author's intended original transaction
                Transaction transaction = new Transaction(checkBalanceMessage.getAccountPubKey());

                if (!transaction.toJson().equals(value))
                    throw new Exception("Transaction is not the same");
            }
        }
    }
}
