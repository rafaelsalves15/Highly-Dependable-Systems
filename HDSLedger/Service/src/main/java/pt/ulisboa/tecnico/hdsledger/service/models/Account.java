package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.security.PublicKey;

public class Account {
    public static final int INITIAL_BALANCE = 10000;

    private int balance = INITIAL_BALANCE;
    private PublicKey key;
    private int id;
    
    public Account(int id) {
        try {
            this.key = KeyGetter.getPublic(id);
            this.id = id;
        } catch (Exception e) {
            e.printStackTrace(); // TODO
        }
    }

    public int getBalance() {
        return this.balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public void addBalance(int balance) {
        synchronized (this) {
            setBalance(getBalance() + balance);
        }
    }

    public void subBalance(int balance) {
        synchronized (this) {
            setBalance(getBalance() - balance);
        }
    }

    public int getId() {
        return id;
    }
}
