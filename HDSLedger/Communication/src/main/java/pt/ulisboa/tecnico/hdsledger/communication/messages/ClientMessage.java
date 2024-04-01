package pt.ulisboa.tecnico.hdsledger.communication.messages;

import pt.ulisboa.tecnico.hdsledger.communication.messages.ledger.*;
import pt.ulisboa.tecnico.hdsledger.utilities.KeyGetter;
import com.google.gson.Gson;
import java.util.Base64;

public class ClientMessage extends Message {
    
    private String message;
    // ID of the request made by the client
    // This ID is internal to the client
    private int requestId;

    public ClientMessage(int client_id, Type type, int requestId) {
        super(client_id, type);
        this.requestId = requestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int id) {
        this.requestId = id;
    }

    public CheckBalanceMessage deserializeCheckBalanceMessage() {
        return new Gson().fromJson(this.message, CheckBalanceMessage.class);
    }

    public TransferMessage deserializeTransferMessage() {
        return new Gson().fromJson(this.message, TransferMessage.class);
    }

    public BalanceResponseMessage deserializeBalanceResponseMessage() {
        return new Gson().fromJson(this.message, BalanceResponseMessage.class);
    }

    public TransferResponseMessage deserializeTransferResponseMessage() {
        return new Gson().fromJson(this.message, TransferResponseMessage.class);
    }

    @Override
    public void verify() throws Exception {
        String publicKey = Base64.getEncoder().encodeToString(KeyGetter.getPublic(this.getSenderId()).getEncoded());

        switch (this.getType()) {
            case TRANSFER -> {
                TransferMessage message = deserializeTransferMessage();
                if (!publicKey.equals(message.getSource()))
                    throw new Exception("Author must be source");
            }
            case CHECK_BALANCE -> {
                CheckBalanceMessage message = deserializeCheckBalanceMessage();
                if (!publicKey.equals(message.getAccountPubKey()))
                    throw new Exception("Author must be source");
            }
            
            case RESPONSE_BALANCE -> {
                BalanceResponseMessage message = deserializeBalanceResponseMessage();
                if (message.getBalance() < -1)
                    throw new Exception("Invalid balance");
            }
            case RESPONSE_TRANSFER -> {
                TransferResponseMessage message = deserializeTransferResponseMessage();
            }
        }
    }
}
