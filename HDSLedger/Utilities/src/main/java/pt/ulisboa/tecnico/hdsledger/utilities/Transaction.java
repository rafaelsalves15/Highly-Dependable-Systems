package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.Base64;
import java.security.PublicKey;
import com.google.gson.Gson;
import java.io.Serializable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Transaction implements Serializable {
    public static final int FEE = 10;
    private String source = null;
    private String destination = null;
    private int amount;
    // This is ugly but a last minute change...
    private boolean isCheckBalance = false;

    public Transaction() {
    }

    public Transaction(String source, String destination, int amount) {
        this.source = source;
        this.destination = destination;
        this.amount = amount;
    }

    // Ugly stuff...
    public Transaction(String source) {
        this.source = source;
        this.isCheckBalance = true;
    }

    public Transaction(PublicKey source, PublicKey destination, int amount) {
        this.source = Base64.getEncoder().encodeToString(source.getEncoded());
        this.destination = Base64.getEncoder().encodeToString(destination.getEncoded());
        this.amount = amount;
    }

    public boolean isEmpty() {
        return source == null;
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

    public boolean isCheckBalance() {
        return this.isCheckBalance;
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

    public static Transaction fromJson(String json) {
        return new Gson().fromJson(json, Transaction.class);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Transaction))
            return false;
        
        Transaction otherTransaction = (Transaction) other;
        return 
            (
            this.source.equals(otherTransaction.getSource()) &&
            this.destination.equals(otherTransaction.getDestination()) &&
            this.amount == otherTransaction.getAmount() &&
            this.isCheckBalance == otherTransaction.isCheckBalance &&
            this.isCheckBalance == false
            )
            ||
            (
            this.source.equals(otherTransaction.getSource()) &&
            this.isCheckBalance == otherTransaction.isCheckBalance &&
            this.isCheckBalance == true
            );
    }


    public String getHashString() {
        if (this.isEmpty())
            return "";

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            byte[] serializedData = baos.toByteArray();

            MessageDigest digest = MessageDigest.getInstance("SHA-1");  // Or "SHA-256" for stronger security
            byte[] hashBytes = digest.digest(serializedData);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
              String hexStr = Integer.toHexString(0xFF & b);
              if (hexStr.length() == 1) {
                  sb.append('0');
              }
              sb.append(hexStr);
            }
            return sb.toString().substring(0, 10);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
