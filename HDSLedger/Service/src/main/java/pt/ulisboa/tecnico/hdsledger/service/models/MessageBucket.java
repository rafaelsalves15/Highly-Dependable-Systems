package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.consensus.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.messages.Message;
import pt.ulisboa.tecnico.hdsledger.communication.messages.AuthedMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    // Instance -> Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, Map<Integer, AuthedMessage>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket() {}

    /*
     * Add a message to the bucket
     * 
     * @param AuthedMessage
     * 
     * @param message
     */
    public void addMessage(AuthedMessage authed) {
        ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), authed);
    }

    /*
     * Returns a pair of : <Value, PreparedMessages>
     */
    public Optional<Pair<String, List<AuthedMessage>>> getPrepareQuorum(int instance, int round) {
        HashMap<String, List<AuthedMessage>> values = new HashMap<>();

        bucket.get(instance).get(round).values().forEach((authed) -> {
            // Here we can assume that every message put on the bucket is well constructed 
            ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            String value = prepareMessage.getValue();
            values.putIfAbsent(value, new ArrayList<>());
            values.get(value).add(authed);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return values.entrySet().stream()
            .filter((Map.Entry<String, List<AuthedMessage>> entry) ->
                 entry.getValue().size() >= ProcessConfigBuilder.getQuorumSize())
            .map((Map.Entry<String, List<AuthedMessage>> entry) -> 
                new Pair<String, List<AuthedMessage>>(entry))
            .findFirst();
    }

    public Optional<String> hasValidPrepareQuorum(int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((authed) -> {
            ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            String value = prepareMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= ProcessConfigBuilder.getQuorumSize();
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<String> hasValidCommitQuorum(int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((authed) -> {
            ConsensusMessage message = authed.getDataAs(ConsensusMessage.class);

            CommitMessage commitMessage = message.deserializeCommitMessage();
            String value = commitMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= ProcessConfigBuilder.getQuorumSize();
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    /*
     * If has received f + 1 round change messages containing round values superior to this process's round value
     * Returns the minimum value
     */
    public Optional<Integer> hasValidRoundChangeFp1(int instance, int currentRound) {
        // If there are no messages for this instance, trivially there is no f + 1
        if (bucket.get(instance) == null)
            return Optional.empty();
        // Create mapping of value to frequency
        int count = 0;
        Optional<Integer> r_min = Optional.empty();

        for (Integer round : bucket.get(instance).keySet()) {
            if (round > currentRound) {
                count += bucket.get(instance).get(round).size();
                if (r_min.isPresent()) {
                    if (round < r_min.get())
                        r_min = Optional.of(round);
                } else  {
                    r_min = Optional.of(round);
                }
            }
        };
        
        if (count >= ProcessConfigBuilder.getMaxFaulty() + 1) 
            return r_min;
        else 
            return Optional.empty();
    }

    /*
     * Qrc <- <ROUND-CHANGE, di, round, pr, pv>
     */
    public Optional<List<AuthedMessage>> hasValidRoundChangeQuorum(int instance, int round, int preparedRound, String preparedValue) {
        // If there are no messages for this instance, trivially there is no Quorum
        if (bucket.get(instance) == null)
            return Optional.empty();

        Map<Integer, AuthedMessage> messages = bucket.get(instance).get(round);

        List<AuthedMessage> quorum = new ArrayList<>();

        for (Map.Entry<Integer, AuthedMessage> entry : messages.entrySet()) {
            ConsensusMessage message = entry.getValue().getDataAs(ConsensusMessage.class);
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            if (roundChangeMessage.getPreparedRound() == preparedRound && roundChangeMessage.getValue() == preparedValue)
                quorum.add(entry.getValue());
        }

        if (quorum.size() >= ProcessConfigBuilder.getQuorumSize()) 
            return Optional.of(quorum);
        else
            return Optional.empty();
    }

    /*
     * Qrc <- <ROUND-CHANGE, di, round, pr, pv>
     */
    public Optional<List<AuthedMessage>> hasValidRoundChangeQuorum(int instance, int round) {
        // If there are no messages for this instance, trivially there is no Quorum
        if (bucket.get(instance) == null)
            return Optional.empty();

        if (bucket.get(instance).get(round) == null)
            return Optional.empty();

        List<AuthedMessage> messages = bucket.get(instance).get(round).values().stream().collect(Collectors.toList());
        
        if (messages == null)
            return Optional.empty();

        if (messages.size() >= ProcessConfigBuilder.getQuorumSize()) 
            return Optional.of(messages);
        else
            return Optional.empty();
    }

    public Map<Integer, ConsensusMessage> getMessages(int instance, int round) {
        Map<Integer, AuthedMessage> authedMessages = bucket.get(instance).get(round);
        // Create mapping of value to frequency
        Map<Integer, ConsensusMessage> messages = new HashMap<>();
        
        for (Map.Entry<Integer, AuthedMessage> entry : authedMessages.entrySet()) {
            messages.put(entry.getKey(), entry.getValue().getDataAs(ConsensusMessage.class));
        }

        return messages;
    }
}
