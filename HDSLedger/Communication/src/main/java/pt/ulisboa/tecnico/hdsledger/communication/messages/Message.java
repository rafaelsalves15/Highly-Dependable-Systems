package pt.ulisboa.tecnico.hdsledger.communication.messages;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;

public class Message implements Serializable {

    // Sender identifier
    private int senderId;
    // Message identifier
    private int messageId;
    // Message type
    private Type type;
    // Piggybacked messages
    private String piggyback;

    public enum Type {
        IGNORE, PRE_PREPARE, PREPARE, COMMIT, ACK, ROUND_CHANGE, TRANSFER, CHECK_BALANCE, RESPONSE_BALANCE, RESPONSE_TRANSFER; 
    }

    public Message(int senderId, Type type) {
        this.senderId = senderId;
        this.type = type;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Message fromJson(String json) {
        return new Gson().fromJson(json, Message.class);
    }

    public void verify() throws Exception {

    }

    public void setPiggyback(List<AuthedMessage> messages) {
        this.piggyback = new Piggyback(messages).toJson();         
    }

    public void clearPiggyback() {
        this.piggyback = null;
    }

    public List<AuthedMessage> getPiggyback() {
        if (this.piggyback == null)
            return null;
        return Piggyback.fromJson(this.piggyback).getMessages();
    }
}
