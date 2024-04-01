package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    SocketSendingError("Error while sending message"),
    CannotOpenSocket("Error while opening socket"),
    InvalidMessageWrongSignature("Sent message has incorrect signature"),
    InvalidMessageWrongKey("Sent message has incorrect key"),
    InvalidClientRequest("Client sent an invalid request"),    
    AmountNotValid("Amount not valid"),
    InvalidDestination("Cannot transfer to your own account");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
