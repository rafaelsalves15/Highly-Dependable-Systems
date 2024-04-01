package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.communication.messages.Message;


public interface Link {
    public void broadcast(Message data);
    public void send(int nodeId, Message data);
    public Message receive();
}
