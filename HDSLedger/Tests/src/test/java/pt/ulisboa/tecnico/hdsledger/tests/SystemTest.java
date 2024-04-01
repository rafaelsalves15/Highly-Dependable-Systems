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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.collect.Collections2;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SystemTest {
    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    private static String nodesConfigPath = "src/test/resources/";
    private static String puppetMasterPath = "../puppet-master.py";

    @BeforeAll
    public void init() throws Exception {
    }

    @Test
    @DisplayName("Single Client")
    public void test_single_client() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config2.json", "/tmp/test/keys");
        
        int sourceId = 5;
        int destinationId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get victim account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

    @Test
    @DisplayName("Check balance")
    public void test_check_balance() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config26.json", "/tmp/test/keys");
        
        int sourceId = 5;
        int destinationId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get victim account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        int balance = system.getClients().get(sourceId).check_balance(sourcePublicKey);
        
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(balance == Account.INITIAL_BALANCE - 500 - Transaction.FEE);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(5, balance, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

    @Test
    @DisplayName("Single Client Multiple requests")
    public void test_single_client_multiple_requests() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config3.json", "/tmp/test/keys");
        
        int sourceId = 5;
        int destinationId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 240);
        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 300);

        // Give back some money
        system.getClients().get(destinationId).transfer(destinationPublicKey, sourcePublicKey , 400);
        
        List<Transaction> ledger = new ArrayList<>();
        ledger.add(new Transaction(sourcePublicKey, destinationPublicKey, 500));
        ledger.add(new Transaction(sourcePublicKey, destinationPublicKey, 240));
        ledger.add(new Transaction(sourcePublicKey, destinationPublicKey, 300));
        ledger.add(new Transaction(destinationPublicKey, sourcePublicKey, 400));

        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE - 500 - 240 - 300 + 400 - 3 * Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE + 500 + 240 + 300 - 400 - Transaction.FEE, system.getNodes()));
    }

    @Test
    @DisplayName("Client Tries to Transfer More Than Available Balance")
    public void test_transfer_with_insufficient_funds() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config9.json", "/tmp/test/keys");
        
        int sourceId = 5;
        int destinationId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 99999999);
        
        assertFalse(success);
        assertTrue(Util.checkLedger(new ArrayList<>(), system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE , system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE , system.getNodes()));

    }

    @Test
    @DisplayName("Client Tries to Transfer Negative Amount")
    public void test_transfer_with_negative_amount() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config21.json", "/tmp/test/keys");
        
        int sourceId = 5;
        int destinationId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        try {
        // Attempt to transfer a negative amount
        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey, -10);
        assertFalse(success);
        // If the transfer succeeds without throwing an exception, fail the test
        fail("Expected HDSSException to be thrown for negative amount");
        } catch (HDSSException e) {
            
        }   
        
        assertTrue(Util.checkLedger(new ArrayList<>(), system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE , system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE , system.getNodes()));

    }

    @Test
    @DisplayName("Client Tries to Transfer Funds to Himself")
    public void test_transfer_own_account() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config22.json", "/tmp/test/keys");
        
        int sourceId = 5;

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);


        try {
        // Attempt to transfer a negative amount
        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, sourcePublicKey, 10);
        assertFalse(success);
        // If the transfer succeeds without throwing an exception, fail the test
        fail("Expected HDSSException to be thrown for transfering funds to own account");
        } catch (HDSSException e) {
            
        }   
        
        assertTrue(Util.checkLedger(new ArrayList<>(), system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE , system.getNodes()));

    }


    public void addRequest(Pair<List<Thread>, List<Transaction>> lists, HDSSystem system, int source, int destination, int amount) {
        List<Thread> requests = lists.getFirst();
        requests.add(Util.requestTransactionDetached(system, source, destination, amount));

        List<Transaction> ledger = lists.getSecond();
        try {
            ledger.add(new Transaction(KeyGetter.getPublic(source), KeyGetter.getPublic(destination), amount));
        } catch (Exception e) {
            // TODO
        }
    }

    @Test
    @DisplayName("Concurrent Clients")
    public void test_concurrent_clients() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config5.json", "/tmp/test/keys");
        
        Pair<List<Thread>, List<Transaction>> lists = new Pair<>(new ArrayList<Thread>(), new ArrayList<Transaction>());
        addRequest(lists, system, 5, 6, 100);
        addRequest(lists, system, 6, 7, 150);
        addRequest(lists, system, 7, 5, 500);

        List<Transaction> ledger = lists.getSecond();

        for (Thread request : lists.getFirst()) {
            request.join();
        }

        while (!Util.waitLedgers(system.getNodes(), 3));
                            
        boolean isAny = false;
        for (List<Transaction> permutation : Collections2.permutations(ledger)) {
            isAny = isAny || Util.checkLedger(permutation, system.getNodes());
        }

        assertTrue(isAny);
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE - 100 + 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE + 100 - 150 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(7, Account.INITIAL_BALANCE + 150 - 500 - Transaction.FEE, system.getNodes()));
    }
} 
