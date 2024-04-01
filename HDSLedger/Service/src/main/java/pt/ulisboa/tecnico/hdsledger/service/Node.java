package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.messages.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.APL;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.FunctionCall;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.Behaviour;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Account;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());

    private NodeService service;
    private int id;

    public Node(int id) throws Exception {
        // Create configuration instances
        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();
        ProcessConfig[] nodeConfigs = systemConfig.getNodes();
        ProcessConfig[] clientConfigs = systemConfig.getClients();

        ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId() == id).findAny().get();

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2};", nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort()));

        LOGGER.log(Level.INFO, MessageFormat.format("Config path is: {0}", ProcessConfigBuilder.getPath()));
        LOGGER.log(Level.INFO, MessageFormat.format("Key path is:    {0}", KeyGetter.getKeyDirectory()));

        Link linkToNodes = new APL(nodeConfig, nodeConfig.getPort(), nodeConfigs, clientConfigs, ConsensusMessage.class);

        this.id = id;
        // Services that implement listen from UDPService
        this.service = new NodeService(linkToNodes, nodeConfig, systemConfig);
    }

    public Node(int id, List<Pair<FunctionCall, Behaviour>> behaviours) throws Exception {
        // Create configuration instances
        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();
        ProcessConfig[] nodeConfigs = systemConfig.getNodes();
        ProcessConfig[] clientConfigs = systemConfig.getClients();

        ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId() == id).findAny().get();

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2};", nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort()));

        LOGGER.log(Level.INFO, MessageFormat.format("Config path is: {0}", ProcessConfigBuilder.getPath()));
        LOGGER.log(Level.INFO, MessageFormat.format("Key path is:    {0}", KeyGetter.getKeyDirectory()));

        Link linkToNodes = new APL(nodeConfig, nodeConfig.getPort(), nodeConfigs, clientConfigs, ConsensusMessage.class);

        this.id = id;
        // Services that implement listen from UDPService
        ByzantineService byzantineService = new ByzantineService(linkToNodes, nodeConfig, systemConfig);

        for (Pair<FunctionCall, Behaviour> behaviour : behaviours) {
            byzantineService.addBehaviour(behaviour);
        }

        this.service = byzantineService;
    }

    public List<Transaction> getLedger() {
        return this.service.getLedger();
    }


    public Account getAccount(int id) {
        return this.service.getAccount(id);
    }

    public void listen() {
        service.listen();
    }

    public int getId() {
        return id;
    }

    public static void main(String[] args) {
        try {
            // Command line arguments
            int id = Integer.parseInt(args[0]);

            // If a specific process config path is provided...
            if (args.length >= 2) {
                ProcessConfigBuilder.setPath("src/main/resources/" + args[1]);
            }

            Node self = new Node(id);
            self.listen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
