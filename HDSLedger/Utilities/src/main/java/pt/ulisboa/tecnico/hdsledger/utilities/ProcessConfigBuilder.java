package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ProcessConfigBuilder {
    public static String DEFAULT_CONFIG = "../Service/src/main/resources/regular_config.json";
    private static ProcessConfigBuilder instance = new ProcessConfigBuilder();
    private String path;
    private SystemConfig systemConfig;
    private boolean pathChanged = false;

    private ProcessConfigBuilder() {
        String conf_path = System.getenv("CONF_PATH");
        if (conf_path != null && !conf_path.equals("")) {
            this.path = conf_path;
        } else {
            this.path = ProcessConfigBuilder.DEFAULT_CONFIG;
        }
        this.pathChanged = true;
    }

    public static void setPath(String path) {
        instance.path = path;
        instance.pathChanged = true;
    }

    public static String getPath() {
        return instance.path;
    }

    public static SystemConfig getSystemConfig() {
        if (!instance.pathChanged && instance.systemConfig != null)
            return instance.systemConfig;

        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(instance.path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            instance.systemConfig = gson.fromJson(input, SystemConfig.class);
            instance.pathChanged = false;
            return instance.systemConfig;
        } catch (FileNotFoundException e) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new HDSSException(ErrorMessage.ConfigFileFormat);
        }
    }

    public static ProcessConfig[] getNodeConfigs() {
        return ProcessConfigBuilder.getSystemConfig().getNodes();
    }

    public static ProcessConfig fromId(int id) throws Exception {
        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();
        ProcessConfig[] nodes = Arrays.stream(systemConfig.getNodes()).filter(c -> c.getId() == id).toArray(ProcessConfig[]::new);
        
        if (nodes.length >= 1)
            return nodes[0];

        ProcessConfig[] clients = Arrays.stream(systemConfig.getClients()).filter(c -> c.getId() == id).toArray(ProcessConfig[]::new);

        if (clients.length >= 1)
            return clients[0];
            
        throw new Exception(); // TODO
    }

    public static boolean isClient(int id) {
        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();
        return !Arrays.stream(systemConfig.getClients()).filter(c -> c.getId() == id).findAny().isEmpty();
    }

    public static int getMaxFaulty() {
        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();
        return Math.floorDiv(systemConfig.getNodes().length - 1, 3);
    }

    public static int getQuorumSize() {
        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();
        return Math.floorDiv(systemConfig.getNodes().length + ProcessConfigBuilder.getMaxFaulty(), 2) + 1;
    }

    public static boolean isLeader(int id, int round) {
        return ((round % ProcessConfigBuilder.getNodeConfigs().length) + 1) == id;
    }

    public static int getLeader(int round) {
        int leader = (round % ProcessConfigBuilder.getNodeConfigs().length) + 1;
        assert(ProcessConfigBuilder.isLeader(leader, round));
        return leader;
    }

}
