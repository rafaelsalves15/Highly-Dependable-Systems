package pt.ulisboa.tecnico.hdsledger.tests;

import pt.ulisboa.tecnico.hdsledger.client.ClientLibrary;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.Message;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.FunctionCall;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.Behaviour;
import pt.ulisboa.tecnico.hdsledger.service.models.Account;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
public class ByzantineNodesTest {
    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    private static String nodesConfigPath = "src/test/resources/";
    private static String puppetMasterPath = "../puppet-master.py";

    @Test
    @DisplayName("Leader sleeps")
    public void test_leader_sleep() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.SLEEP));
        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config6.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6; 
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get victim account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE + 500, system.getNodes()));
        
    }

    @Test
    @DisplayName("Falsify consensus")
    public void test_falsify_consensus() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.READY, Behaviour.FALSIFY_CONSENSUS));
        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config7.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6; 
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get victim account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        List<Transaction> ledger = new ArrayList<>();

        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE, system.getNodes()));
        
    }
    
    @Test
    @DisplayName("Leader ignores requests from specific client")
    public void test_client_specific_ignorance() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.CLIENT_SPECIFIC_IGNORANCE));
        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config10.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6; 
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        //Not Ignored
        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        //Ignored
        system.getClients().get(destinationId).transfer(destinationPublicKey, sourcePublicKey , 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(new Transaction(sourcePublicKey, destinationPublicKey, 500));
        ledger.add(new Transaction(destinationPublicKey, sourcePublicKey, 500));

        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE - Transaction.FEE, system.getNodes()));
    }

    @Test
    @DisplayName("Leader Overcharges Fees")
    public void test_leader_overcharges_fees() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.APPLY_TRANSACTION, Behaviour.LEADER_OVERCHARGES_FEE));
        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config11.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6; 
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);

        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey, 500);
        int sourceBalance       = system.getClients().get(sourceId).check_balance(sourcePublicKey);
        int destinationBalance  = system.getClients().get(destinationId).check_balance(destinationPublicKey);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(sourceBalance == Account.INITIAL_BALANCE - 500 - Transaction.FEE);
        assertTrue(destinationBalance == Account.INITIAL_BALANCE + 500);
    }

    @Test
    @DisplayName("Leader echoes the same request")
    public void test_leader_echoes_request() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.LEADER_ECHOES_REQUEST));
        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config12.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6; 
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
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
    @DisplayName("F Nodes respond immediately to request, including leader")
    public void test_f_respond_immediately() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.RESPOND_IMMEDIATELY));

        for (int i = 1; i <= ProcessConfigBuilder.getMaxFaulty(); i++)
            byzantineNodes.put(i, behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config13.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 7;
        int destinationId = 8; 
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(sourceId, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(destinationId, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

    @Test
    @DisplayName("F Nodes respond immediately to request, excluding leader")
    public void test_f_respond_immediately_excluding_leader() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.RESPOND_IMMEDIATELY));

        for (int i = 1; i <= ProcessConfigBuilder.getMaxFaulty() + 1; i++) {
            if (!ProcessConfigBuilder.isLeader(i, 1))
                byzantineNodes.put(i, behaviours);
        }

        HDSSystem system = new HDSSystem(nodesConfigPath + "config14.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 7;
        int destinationId = 8;
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(sourceId, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(destinationId, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

    @Test
    @DisplayName("F+1 Nodes respond immediately to request")
    public void test_fp1_respond_immediately_excluding_leader() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.RESPOND_IMMEDIATELY));

        for (int i = 1; i <= ProcessConfigBuilder.getMaxFaulty() + 1; i++)
            byzantineNodes.put(i, behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config15.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 7;
        int destinationId = 8;
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        // System state is undefined, but client will perceive it as a failure
        assertFalse(success);
    }

    @Test
    @DisplayName("Leader starts consensus then goes to sleep")
    public void test_leader_starts_consensus_then_sleeps() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.START_CONSENSUS_THEN_SLEEP));

        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config17.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6;
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(sourceId, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(destinationId, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }


    @Test
    @DisplayName("F nodes don't respond on PREPARE")
    public void test_f_nodes_dont_respond_on_prepare() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.UPON_PREPARE, Behaviour.DO_NOTHING));

        for (int i = 1; i <= ProcessConfigBuilder.getMaxFaulty() + 1; i++) {
            if (ProcessConfigBuilder.isLeader(i, 1)) // Ignoring the leader for this behaviour
                continue;
            byzantineNodes.put(i, behaviours);
        }

        HDSSystem system = new HDSSystem(nodesConfigPath + "config18.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6;
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(sourceId, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(destinationId, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

    @Test
    @DisplayName("Leader doesn't respond on PREPARE")
    public void test_leader_dont_respond_on_prepare() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.UPON_PREPARE, Behaviour.DO_NOTHING));

        for (int i = 1; i <= ProcessConfigBuilder.getMaxFaulty(); i++) {
            byzantineNodes.put(i, behaviours);
        }

        HDSSystem system = new HDSSystem(nodesConfigPath + "config23.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6;
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(sourceId, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(destinationId, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

    @Test
    @DisplayName("Leader doesn't respond on COMMIT")
    public void test_leader_dont_respond_on_commit() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.UPON_COMMIT, Behaviour.DO_NOTHING));

        for (int i = 1; i <= ProcessConfigBuilder.getMaxFaulty(); i++) {
            byzantineNodes.put(i, behaviours);
        }

        HDSSystem system = new HDSSystem(nodesConfigPath + "config24.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6;
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(sourceId, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(destinationId, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

    @Test
    @DisplayName("f nodes send duplicate reply")
    public void test_nodes_send_duplicate_reply() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.LEDGER_REPLY, Behaviour.DUPLICATE_REPLY));

        for (int i = 1; i <= ProcessConfigBuilder.getMaxFaulty(); i++) {
            byzantineNodes.put(i, behaviours);
        }

        HDSSystem system = new HDSSystem(nodesConfigPath + "config25.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6;
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        boolean success = system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);
        Transaction transaction = new Transaction(sourcePublicKey, destinationPublicKey, 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(transaction);

        assertTrue(success);
        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(sourceId, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(destinationId, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }

                    
    @Test
    @DisplayName("Leader alters transaction data")
    public void test_leader_alters_data() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> bizantineNodes = new HashMap<>();

        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.UPON_PREPARE, Behaviour.CHANGE_DATA));
        bizantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config27.json", "/tmp/test/keys/", bizantineNodes);

        int sourceId = 5;
        int destinationId = 7; 
        
        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        //Not Ignored
        system.getClients().get(sourceId).transfer(sourcePublicKey, destinationPublicKey , 500);

        List<Transaction> ledger = new ArrayList<>();
        ledger.add(new Transaction(sourcePublicKey, destinationPublicKey, 500));

        assertTrue(Util.checkLedger(ledger, system.getNodes()));
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE - 500 - Transaction.FEE, system.getNodes()));
        assertTrue(Util.checkBalance(7, Account.INITIAL_BALANCE + 500, system.getNodes()));
    }
} 
