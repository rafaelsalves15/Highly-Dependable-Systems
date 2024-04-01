package pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.builder;

import pt.ulisboa.tecnico.hdsledger.communication.messages.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.Message;
import pt.ulisboa.tecnico.hdsledger.communication.messages.AuthedMessage;

public class ConsensusMessageBuilder {
    private final ConsensusMessage instance;

    public ConsensusMessageBuilder(int sender, Message.Type type, AuthedMessage clientMessage) {
        instance = new ConsensusMessage(sender, type, clientMessage);
    }

    public ConsensusMessageBuilder setMessage(String message) {
        instance.setMessage(message);
        return this;
    }

    public ConsensusMessageBuilder setConsensusInstance(int consensusInstance) {
        instance.setConsensusInstance(consensusInstance);
        return this;
    }

    public ConsensusMessageBuilder setClientId(int id) {
        instance.setClientId(id);
        return this;
    }
    public ConsensusMessageBuilder setRound(int round) {
        instance.setRound(round);
        return this;
    }

    public ConsensusMessageBuilder setReplyTo(int replyTo) {
        instance.setReplyTo(replyTo);
        return this;
    }

    public ConsensusMessageBuilder setReplyToMessageId(int replyToMessageId) {
        instance.setReplyToMessageId(replyToMessageId);
        return this;
    }

    public ConsensusMessage build() {
        return instance;
    }
}
