package pt.ulisboa.tecnico.hdsledger.utilities;

public class SystemConfig {
    private ProcessConfig[] nodes;
    private ProcessConfig[] clients;

    public SystemConfig() {}

    public ProcessConfig[] getNodes() {
        return this.nodes;
    }

    public ProcessConfig[] getClients() {
        return this.clients;
    }
}
