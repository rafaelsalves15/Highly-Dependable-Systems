package pt.ulisboa.tecnico.hdsledger.tests;

import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.FunctionCall;
import pt.ulisboa.tecnico.hdsledger.service.services.ByzantineService.Behaviour;
import pt.ulisboa.tecnico.hdsledger.client.ClientLibrary;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.keygenerator.KeyGenerator;

import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HDSSystem {
    private SystemConfig systemConfig;
    private Map<Integer, Node> nodes = new ConcurrentHashMap();
    private Map<Integer, ClientLibrary> clients = new ConcurrentHashMap();

    public HDSSystem(String configPath, String keyDirectory, Map<Integer, List<Pair<FunctionCall, Behaviour>>> byzantineNodes) throws Exception {
        KeyGenerator.generateKeys(configPath, keyDirectory);

        this.systemConfig = ProcessConfigBuilder.getSystemConfig();

        for (ProcessConfig conf : this.systemConfig.getNodes()) {
            List<Pair<FunctionCall, Behaviour>> byzantineBehaviour = byzantineNodes.get(conf.getId());
            Node node;
            if (byzantineBehaviour != null) 
                node = new Node(conf.getId(), byzantineBehaviour);
            else
                node = new Node(conf.getId());
            nodes.put(conf.getId(), node);
            node.listen();
        }

        for (ProcessConfig conf : this.systemConfig.getClients()) {
            clients.put(conf.getId(), new ClientLibrary(conf.getId()));
        }
    }

    public HDSSystem(String configPath, String keyDirectory) throws Exception {
        this(configPath, keyDirectory, new HashMap<>());
    }

    public Map<Integer, Node> getNodes() {
        return this.nodes;
    }

    public Map<Integer, ClientLibrary> getClients() {
        return this.clients;
    }
}
