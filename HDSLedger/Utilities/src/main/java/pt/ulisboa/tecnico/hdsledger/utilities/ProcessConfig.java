package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {

    public ProcessConfig() {}

    private String hostname;

    private int id;

    private int port;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

}
