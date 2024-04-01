package pt.ulisboa.tecnico.hdsledger.tests;

import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.FunctionCall;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.Behaviour;
import pt.ulisboa.tecnico.hdsledger.client.ClientLibrary;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ledger.*;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.models.Account;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Base64;
import java.util.logging.Level;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.collect.Collections2;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ByzantineClientsTest {
    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    private static String nodesConfigPath = "src/test/resources/";
    private static String puppetMasterPath = "../puppet-master.py";

    @Test
    @DisplayName("Clients execute transactions pretending to be other clients")
    public void test_impersonator_client() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config4.json", "/tmp/test/keys/");
        int impersonatorId = 5;
        int victimId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

         //Get impersonator account PublicKey
        PublicKey impersonatorClientPublicKey = KeyGetter.getPublic(impersonatorId);

         //Get victim account PublicKey
        PublicKey victimClientPublicKey = KeyGetter.getPublic(victimId);

        boolean success = system.getClients().get(impersonatorId).transfer(victimClientPublicKey , impersonatorClientPublicKey , 500);
        
        assertFalse(success);
        // If ledger is empty the transfer operation failed and the impersonator attack failed 
        assertTrue(!system.getNodes().values().stream().filter((node)->!node.getLedger().isEmpty()).findAny().isPresent());
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE, system.getNodes()));
    }

    @Test
    @DisplayName("Client attempts DoS attack with timestamp bypassing")
    public void test_dos_attack() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config20.json", "/tmp/test/keys/");
        int attackerId = 5;
        int receiverId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        long waitInterval = 500; // half a second

        //Get attacker account PublicKey
        PublicKey attackerPublicKey = KeyGetter.getPublic(attackerId);

        //Get receiver account PublicKey
        PublicKey receiverPublicKey = KeyGetter.getPublic(receiverId);

        long startTime = System.currentTimeMillis(); // Record start time

        // Simulate multiple requests with short intervals
        for (int i = 0; i < 3 ; i++) {
            system.getClients().get(attackerId).transfer(attackerPublicKey, receiverPublicKey, 1);
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        assertTrue(elapsedTime >= waitInterval * 2);
    }                                                      

    @Test
    @DisplayName("Client sends malformed request")
    public void test_client_sends_malformed_request() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config16.json", "/tmp/test/keys/");

        int source = 5;
        int destination = 6;

        //Get attacker account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(source);

        //Get receiver account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destination);

        ClientLibrary client = system.getClients().get(source);

        TransferMessage req = new TransferMessage(Base64.getEncoder().encodeToString(sourcePublicKey.getEncoded()), Base64.getEncoder().encodeToString(destinationPublicKey.getEncoded()), 100);
        
        ClientMessage clientReq = new ClientMessage(source, Message.Type.CHECK_BALANCE, 0);
        clientReq.setMessage(req.toJson());
        client.getLink().broadcast(clientReq);
        int balance = client.listenBalanceMessages();
        clientReq = new ClientMessage(source, Message.Type.TRANSFER, 0);
        client.getLink().broadcast(clientReq);
        boolean success = client.listenTransferMessages();

        assertTrue(balance == -1);
        assertFalse(success);
        assertTrue(Util.checkLedger(new ArrayList<>(), system.getNodes()));
        assertTrue(Util.checkBalance(source, Account.INITIAL_BALANCE, system.getNodes()));
        assertTrue(Util.checkBalance(destination, Account.INITIAL_BALANCE, system.getNodes()));
    }                                                      

    @Test
    @DisplayName("Clients execute invalid transactions with the help of the leader")
    public void test_impersonator_client_with_leader() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();
        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.VERIFY, Behaviour.DO_NOTHING));
        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);

        HDSSystem system = new HDSSystem(nodesConfigPath + "config19.json", "/tmp/test/keys/", byzantineNodes);

        int impersonatorId = 5;
        int victimId = 6; 

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();

         //Get impersonator account PublicKey
        PublicKey impersonatorClientPublicKey = KeyGetter.getPublic(impersonatorId);

         //Get victim account PublicKey
        PublicKey victimClientPublicKey = KeyGetter.getPublic(victimId);

        boolean success = system.getClients().get(impersonatorId).transfer(victimClientPublicKey , impersonatorClientPublicKey , 500);
        
        // If ledger is empty the transfer operation failed and the impersonator attack failed 
        assertFalse(success);
        assertTrue(!system.getNodes().values().stream().filter((node)->!node.getLedger().isEmpty()).findAny().isPresent());
        assertTrue(Util.checkBalance(5, Account.INITIAL_BALANCE, system.getNodes()));
        assertTrue(Util.checkBalance(6, Account.INITIAL_BALANCE, system.getNodes()));
    }
                    
    @Test
    @DisplayName("Client sends different requests to different nodes")
    public void test_client_sends_different_requests() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config28.json", "/tmp/test/keys/");

        int sourceId = 5;
        int destinationId = 6; 
        
        ClientLibrary client = system.getClients().get(sourceId);
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        //Convert publickKeys to strings
        String source       = Base64.getEncoder().encodeToString(sourcePublicKey.getEncoded());
        String destination  = Base64.getEncoder().encodeToString(destinationPublicKey.getEncoded());
        
        int amount = 10;
        for (Node node : system.getNodes().values()) {
            TransferMessage req = new TransferMessage(source, destination, node.getId() * amount);

            ClientMessage clientReq = new ClientMessage(sourceId, Message.Type.TRANSFER, 0);
            clientReq.setMessage(req.toJson());

            client.getLink().send(node.getId(), clientReq);
        }

        boolean success = client.listenTransferMessages();
        int balance = client.check_balance(sourcePublicKey);
        assertTrue(success);
        assertTrue(balance == Account.INITIAL_BALANCE - ProcessConfigBuilder.getLeader(1)*amount - Transaction.FEE);
    }

    @Test
    @DisplayName("Client sends different requests to different nodes and first leader sleeps")
    public void test_client_sends_diff_requests_leader_sleeps() throws Exception {
        Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes = new HashMap<>();
        List<Pair<FunctionCall, Behaviour>> behaviours = new ArrayList<>();
        behaviours.add(new Pair<>(FunctionCall.START_CONSENSUS, Behaviour.DO_NOTHING));
        byzantineNodes.put(ProcessConfigBuilder.getLeader(1), behaviours);
        HDSSystem system = new HDSSystem(nodesConfigPath + "config31.json", "/tmp/test/keys/", byzantineNodes);

        int sourceId = 5;
        int destinationId = 6; 
        
        ClientLibrary client = system.getClients().get(sourceId);
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        //Convert publickKeys to strings
        String source       = Base64.getEncoder().encodeToString(sourcePublicKey.getEncoded());
        String destination  = Base64.getEncoder().encodeToString(destinationPublicKey.getEncoded());
        
        int amount = 10;
        for (Node node : system.getNodes().values()) {
            TransferMessage req = new TransferMessage(source, destination, node.getId() * amount);

            ClientMessage clientReq = new ClientMessage(sourceId, Message.Type.TRANSFER, 0);
            clientReq.setMessage(req.toJson());

            client.getLink().send(node.getId(), clientReq);
        }

        boolean success = client.listenTransferMessages();
        int balance = client.check_balance(sourcePublicKey);
        assertTrue(success);
        assertTrue(balance == Account.INITIAL_BALANCE - ProcessConfigBuilder.getLeader(2)*amount - Transaction.FEE);
    }
                    
    @Test
    @DisplayName("Client sends a request only to a leader node")
    public void test_client_sends_request_to_leader() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config29.json", "/tmp/test/keys/");

        int sourceId = 5;
        int destinationId = 6; 
        
        ClientLibrary client = system.getClients().get(sourceId);
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        //Convert publickKeys to strings
        String source       = Base64.getEncoder().encodeToString(sourcePublicKey.getEncoded());
        String destination  = Base64.getEncoder().encodeToString(destinationPublicKey.getEncoded());
        
        int amount = 10;
        TransferMessage req = new TransferMessage(source, destination, amount);

        ClientMessage clientReq = new ClientMessage(sourceId, Message.Type.TRANSFER, 0);
        clientReq.setMessage(req.toJson());

        client.getLink().send(ProcessConfigBuilder.getLeader(1), clientReq);

        boolean success = client.listenTransferMessages();
        assertTrue(success);
    }
                    
    @Test
    @DisplayName("Client sends a request only to a non-leader node")
    public void test_client_sends_request_to_nonleader() throws Exception {
        HDSSystem system = new HDSSystem(nodesConfigPath + "config30.json", "/tmp/test/keys/");

        int sourceId = 5;
        int destinationId = 6; 
        
        ClientLibrary client = system.getClients().get(sourceId);
        //Get source account PublicKey
        PublicKey sourcePublicKey = KeyGetter.getPublic(sourceId);
        //Get destination account PublicKey
        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);

        //Convert publickKeys to strings
        String source       = Base64.getEncoder().encodeToString(sourcePublicKey.getEncoded());
        String destination  = Base64.getEncoder().encodeToString(destinationPublicKey.getEncoded());
        
        int amount = 10;
        TransferMessage req = new TransferMessage(source, destination, amount);

        ClientMessage clientReq = new ClientMessage(sourceId, Message.Type.TRANSFER, 0);
        clientReq.setMessage(req.toJson());

        client.getLink().send(ProcessConfigBuilder.getLeader(1)+1, clientReq);

        Thread.sleep(3000); // Wait for node to start round change : can't do it thus nothing happens
        assertTrue(Util.checkLedger(new ArrayList<>(), system.getNodes()));
    }
} 
