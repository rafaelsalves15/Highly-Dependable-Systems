package pt.ulisboa.tecnico.hdsledger.communication.messages;

import java.util.List;

import com.google.gson.Gson;

public class Piggyback {
    private List<AuthedMessage> messages;

    public Piggyback() {
        messages = null;
    }

    public Piggyback(List<AuthedMessage> messages) {
        this.messages = messages;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public List<AuthedMessage> getMessages() {
        return this.messages;
    }

    public static Piggyback fromJson(String json) {
        return new Gson().fromJson(json, Piggyback.class);
    }
}
