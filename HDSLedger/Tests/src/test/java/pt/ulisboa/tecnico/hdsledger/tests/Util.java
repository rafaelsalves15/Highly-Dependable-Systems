package pt.ulisboa.tecnico.hdsledger.tests;

import pt.ulisboa.tecnico.hdsledger.client.ClientLibrary;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.Message;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.models.Account;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.collect.Collections2;

public class Util {

    public static boolean waitLedgers(Map<Integer, Node> nodes, int ledgerSize) {
        for (Node node : nodes.values()) {
            List<Transaction> ledger = node.getLedger();
            synchronized (ledger) {
                if (trimLedger(ledger).size() < ledgerSize) 
                    return false;
            }
        }
        return true;
    }

    public static List<Transaction> trimLedger(List<Transaction> ledger) {
        return ledger.stream().filter((t) -> !t.isEmpty()).collect(Collectors.toList());
    }

    public static boolean checkLedger(List<Transaction> ledger, Map<Integer, Node> nodes) {
        int count = 0;
        for (Integer entry : nodes.keySet()) {
            count += trimLedger(nodes.get(entry).getLedger()).equals(ledger) ? 1 : 0; 
        }
        return count >= ProcessConfigBuilder.getMaxFaulty() + 1;
    }

    public static void printLedgers(Map<Integer, Node> nodes) {
        for (Integer entry : nodes.keySet()) {
            System.out.println(nodes.get(entry).getLedger().stream().map((t) -> t.getHashString()).collect(Collectors.toList()));
            System.out.println("----");
        }
        System.out.println("***");
        System.out.println("***");
    }


    public static boolean checkBalance(int id, int balance, Map<Integer, Node> nodes) {
        int count = 0;
        for (Integer entry : nodes.keySet()) {
            count += (nodes.get(entry).getAccount(id).getBalance() == balance) ? 1 : 0; 
        }
        return count >= ProcessConfigBuilder.getMaxFaulty() + 1;
    }

    public static Thread requestTransactionDetached(ClientLibrary client, PublicKey source, PublicKey destination, int amount) {
        Thread thread = new Thread(() -> {
            try {
                client.transfer(source, destination, amount);
            } catch (Exception e) {
                // TODO
            }
        });
        thread.start();
        return thread;
    }

    public static Thread requestTransactionDetached(HDSSystem system, int source, int destination, int amount) {
        try {
            return requestTransactionDetached(system.getClients().get(source), KeyGetter.getPublic(source), KeyGetter.getPublic(destination), amount);
        } catch(Exception e) {
            return null;
        }
    }
}
