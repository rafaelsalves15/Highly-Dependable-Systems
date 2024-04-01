package pt.ulisboa.tecnico.hdsledger.communication.messages.ledger;

import com.google.gson.Gson;

public class TransferMessage {

    private String source;
    private String destination;
    private int amount;

    public TransferMessage(String source, String destination, int amount) { 
        this.source = source;
        this.destination = destination;
        this.amount = amount; 
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
