package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;

import javax.crypto.Cipher;

import java.util.Scanner;

public class Client{

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());

    public static void main(String[] args) {
        try {
            int id = Integer.parseInt(args[0]);
            
            System.out.println(MessageFormat.format("Client {0} running!", id));
            ClientLibrary clientLib = new ClientLibrary(id);

            for (;;) {
                // Read user input
                System.out.print(">>");
                Scanner scanner = new Scanner(System.in);
                String str = scanner.nextLine();

                if (str.isEmpty())
                    break;

                String[] inputWords = str.trim().split("\\s+"); // Split input by whitespace
                String command = inputWords[0].toLowerCase();
                
                switch (command) {
                    case "transfer" :
                        if (inputWords.length < 2) {
                            System.out.println("Please specify the destination ID and ammount.");
                            break;
                        }
                        String destinationIdStr = inputWords[1];
                        String amountStr = inputWords[2];
                        int amount = Integer.parseInt(amountStr);
                        int destinationId = Integer.parseInt(destinationIdStr);

                        //Get destination account PublicKey
                        PublicKey destinationPublicKey = KeyGetter.getPublic(destinationId);
                        //Get sender account PublicKey
                        PublicKey senderPublicKey = KeyGetter.getPublic(id);

                        System.out.println("Transfer initiated to destination: " + destinationId + " with amount:" + amount + " from ClientId:" + id );
                        boolean success = clientLib.transfer(senderPublicKey, destinationPublicKey , amount);
                        System.out.println(success ? "Success" : "Fail");
                        break;
                    case "check_balance":
                        ProcessConfig account = ProcessConfigBuilder.fromId(id);
                        KeyPair accountKeyPair = KeyGetter.getPair(account);
                        PublicKey accountPublicKey = accountKeyPair.getPublic();
                        
                        System.out.println("Checking balance of account with id: " + id);
                        int balance = clientLib.check_balance(accountPublicKey);
                        if (balance != -1)
                            System.out.println("Balance: " + balance);
                        else
                            System.out.println("Failed checking balance");

                        break; 
                    case "exit":
                        System.out.println("Exiting the program.");
                        scanner.close();
                        System.exit(0);
                        break;
                    default:
                        break;
                }
            }    
        }
        catch (Exception e) {
            e.printStackTrace(); // TODO
        }
    }

}

