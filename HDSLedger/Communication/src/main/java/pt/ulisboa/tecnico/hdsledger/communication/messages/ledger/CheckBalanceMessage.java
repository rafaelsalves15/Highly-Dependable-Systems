package pt.ulisboa.tecnico.hdsledger.communication.messages.ledger;

import com.google.gson.Gson;
import java.security.PublicKey;

public class CheckBalanceMessage {
    private String accountPublicKey;

    // TODO : When to convert to string
    public CheckBalanceMessage(String accountPublicKey){
        this.accountPublicKey = accountPublicKey;
    }

    public String getAccountPubKey() {
        return accountPublicKey;
    }

    public void setAccountPubKey(String accountPublicKey) {
        accountPublicKey = accountPublicKey;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
