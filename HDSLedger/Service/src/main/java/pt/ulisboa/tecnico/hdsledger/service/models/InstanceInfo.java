package pt.ulisboa.tecnico.hdsledger.service.models;


import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.AuthedMessage;
import java.util.Timer;
import java.util.TimerTask;

public class InstanceInfo {

    private int clientId = 0;
    private AuthedMessage clientMessage;
    private int currentRound = 1;
    private int preparedRound = -1;
    private String preparedValue = null; 
    private CommitMessage commitMessage;
    private String inputValue;
    private int committedRound = -1;
    private Timer timer = new Timer();

    public InstanceInfo(String inputValue, int clientId, AuthedMessage clientMessage) {
        this.inputValue = inputValue;
        this.clientId = clientId;
        this.clientMessage = clientMessage;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    /*
     * Sets the timer for a periodic task
     */
    public void setTimer(Runnable task, long delay) {
        TimerTask t = new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        };
        this.timer.schedule(t, delay, delay); 
    }

    public void stopTimer() {
        this.timer.cancel();
        this.timer.purge();
        this.timer = new Timer();
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

    public String getPreparedValue() {
        return preparedValue;
    }

    public void setPreparedValue(String preparedValue) {
        this.preparedValue = preparedValue;
    }

    public String getInputValue() {
        return inputValue;
    }

    public void setInputValue(String inputValue) {
        this.inputValue = inputValue;
    }

    public int getCommittedRound() {
        return committedRound;
    }

    public void setCommittedRound(int committedRound) {
        this.committedRound = committedRound;
    }

    public CommitMessage getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(CommitMessage commitMessage) {
        this.commitMessage = commitMessage;
    }

    public int getClientId() {
        return this.clientId;
    }

    public AuthedMessage getClientMessage() {
        return this.clientMessage;
    }
}
