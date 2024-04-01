package pt.ulisboa.tecnico.hdsledger.keygenerator;

import pt.ulisboa.tecnico.hdsledger.utilities.*;

import javax.crypto.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class KeyGenerator {
    /*
     * Args: 
     *  - system configuration
     *  - output directory 
     */
    public static void main(String[] args) throws Exception {
        KeyGenerator.generateKeys(KeyGetter.getKeyDirectory());
    }

    /*
     * Generates Key pairs
     * Sets ProcessConfigBuilder and KeyGetter
     */
    public static void generateKeys(String configPath, String outputDir) throws Exception { // TODO : handle exceptions 
        ProcessConfigBuilder.setPath(configPath);
        KeyGenerator.generateKeys(outputDir);
    }

    public static void generateKey(ProcessConfig config, String outputDir) throws Exception {
        String folderName = config.getHostname() + ":" + config.getPort() + "/";
        Files.createDirectories(Paths.get(outputDir + "/" + folderName));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        PrivateKey privateKey = pair.getPrivate();
        PublicKey publicKey = pair.getPublic();

        FileOutputStream publicKeyStream = new FileOutputStream(outputDir + "/" + folderName + "public.key");
        publicKeyStream.write(Base64.getEncoder().encodeToString(publicKey.getEncoded()).getBytes());
        publicKeyStream.close();

        FileOutputStream privateKeyStream = new FileOutputStream(outputDir + "/" + folderName + "private.key");
        privateKeyStream.write(Base64.getEncoder().encodeToString(privateKey.getEncoded()).getBytes());
        privateKeyStream.close();
    }

    /*
     * Generates Key pairs
     * Sets KeyGetter and gets config path from ProcessConfigBuilder
     */
    public static void generateKeys(String outputDir) throws Exception { // TODO : handle exceptions
        KeyGetter.setKeyDirectory(outputDir); // This is for testing purposes, when using KeyGenerator as a library it is convenient to have everything set, as ProcessConfigBuilder also gets set
        Files.createDirectories(Paths.get(outputDir));

        SystemConfig systemConfig = ProcessConfigBuilder.getSystemConfig();

        for (ProcessConfig config : systemConfig.getNodes()) {
            generateKey(config, outputDir);
        }

        for (ProcessConfig config : systemConfig.getClients()) {
            generateKey(config, outputDir);
        }
    }
}
