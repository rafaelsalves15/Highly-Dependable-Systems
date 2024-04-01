package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.messages.*;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.keygenerator.KeyGenerator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CommunicationTest {
    private static String nodesConfigPath = "src/test/resources/";

    @BeforeAll
    public void init() {

    }

    @Test
    @DisplayName("Authed Message")
    public void test_authed_message() throws Exception {
        KeyGenerator.generateKeys(nodesConfigPath + "conf1.json", "/tmp/test/keys");

        SystemConfig confs = ProcessConfigBuilder.getSystemConfig();
        ConsensusMessage c = new ConsensusMessage(1, Message.Type.PREPARE, new AuthedMessage(new ClientMessage(1, Message.Type.CHECK_BALANCE, 1)));

        AuthedMessage authed = new AuthedMessage(c);

        //System.out.println(authed.toJson());
        assertTrue(authed.isKeyCorrect());
        assertTrue(authed.isSignatureCorrect());
    }
} 
