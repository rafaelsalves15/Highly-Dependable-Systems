package pt.ulisboa.tecnico.hdsledger.communication.messages.ledger;

import java.io.Serializable;
import com.google.gson.Gson;

public class TransferResponseMessage implements Serializable {

    private boolean success;

    public TransferResponseMessage(boolean success) { 
        this.success = success;
    }

    public boolean wasSuccessful() {
        return this.success;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
