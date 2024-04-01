# HDSLedger
# Group 28 
## How to run tests

First install all the project dependencies. 
>./install_deps.sh

Our tests are automated using Maven. 
The following command executes all tests across various categories, including Communication Tests, System Tests, Byzantine Nodes tests, and Byzantine Clients tests:

>mvn clean test

The tests may take aproximately **1 minute** or more to complete.

The test categories are distributed as follows:
- Communication Tests are located at [CommunicationTest](Communication/src/test/java/pt/ulisboa/tecnico/hdsledger/communication/CommunicationTest.java)
- System Tests are located [SystemTests](Tests/src/test/java/pt/ulisboa/tecnico/hdsledger/tests/SystemTest.java)
- Byzantine nodes tests are located at [ByzantineNodesTests](Tests/src/test/java/pt/ulisboa/tecnico/hdsledger/tests/ByzantineNodesTest.java)
- Byzantine clients tests are located at [ByzantineClientsTests](Tests/src/test/java/pt/ulisboa/tecnico/hdsledger/tests/ByzantineClientsTest.java )

### How are tests defined

A test is defined by a JUnit test which loads the system through a given configuration file and a list of byzantine behaviours for the wanted nodes, and then proceeds to, for example, start a transaction and check the system state by looking at things like what is perceived by the client, node ledgers, account balances. Byzantine behaviour from the nodes is applied by a system of "behaviours" which are passed at the system initialization. The actual behaviour of each case is defined in [ByzantineService.java](Service/src/main/java/pt/ulisboa/tecnico/hdsledger/service/services/ByzantineService.java). This class intercepts the normal execution of a NodeService and may override or perform other actions, deppending on the assigned behaviours.

## Manual testing
The system can also be run manually using the puppet_master.py script by executing following command:
> python3 puppet_master.py <path_to_configuration>

or

> ./puppet_master.py <path_to_configuration>

The available operations are:
> check_balance

> transfer <destination_id> <amount>

# Configuration Files

### Node configuration

Processes are defined as:
```json
{
    "id": <NODE_ID>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
}
```

Configurations define a set of Nodes and Clients like:
```json
{
    "nodes": [<NODE1>, <NODE2>, ...],
    "clients": [<CLIENT1>, <CLIENT2>, ...]
}
```

An example of a configuration can be found [here](Tests/src/test/resources/config27.json).
