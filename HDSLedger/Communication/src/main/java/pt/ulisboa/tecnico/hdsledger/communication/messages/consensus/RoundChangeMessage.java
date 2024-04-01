package pt.ulisboa.tecnico.hdsledger.communication.messages.consensus;

import com.google.gson.Gson;

public class RoundChangeMessage {
    
    // Value
    private String value;
    private int preparedRound;

    public RoundChangeMessage(int preparedRound, String value) {
        this.preparedRound = preparedRound;
        this.value = value;
    }

    public int getPreparedRound() {
        return this.preparedRound;
    }

    public String getValue() {
        return this.value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}   

