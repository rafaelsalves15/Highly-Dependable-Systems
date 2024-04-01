package pt.ulisboa.tecnico.hdsledger.communication.messages.ledger;

import java.io.Serializable;
import com.google.gson.Gson;

public class BalanceResponseMessage implements Serializable {

    private int balance;

    public BalanceResponseMessage(int balance) { 
        this.balance = balance;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
