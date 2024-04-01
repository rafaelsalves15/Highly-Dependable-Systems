package pt.ulisboa.tecnico.hdsledger.utilities;

import javax.crypto.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

public class KeyGetter {
    public static String DEFAULT_DIR = "/tmp/keys/";
    private static KeyGetter instance = new KeyGetter();
    private String keyDirectory;

    private KeyGetter() {
        String key_dir_var = System.getenv("KEY_DIR");
        if (key_dir_var != null && !key_dir_var.equals("")) {
            this.keyDirectory = key_dir_var;
        } else {
            this.keyDirectory = KeyGetter.DEFAULT_DIR;
        }
    }

    public static void setKeyDirectory(String dir) {
        instance.keyDirectory = dir;
    }

    public static String getKeyDirectory() {
        return instance.keyDirectory;
    }

    /*
     * This method is simplified as in an ideal case getting a the private key for another process should be impossible. For simplification purposes both keys are exposed in the system but private keys are only used by their owners
     */
    public static KeyPair getPair(ProcessConfig conf)  { 
        // Read public and private keys
        String folder = "/" + conf.getHostname() + ":" + conf.getPort() + "/";
        while (true) {
            try {
                BufferedReader publicKeyStream  = new BufferedReader(new InputStreamReader(new FileInputStream (instance.keyDirectory + folder +"public.key"), StandardCharsets.UTF_8));
                BufferedReader privateKeyStream = new BufferedReader(new InputStreamReader(new FileInputStream (instance.keyDirectory + folder +"private.key"), StandardCharsets.UTF_8));

                PublicKey publicKey  = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyStream.readLine())));
                PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyStream.readLine())));

                publicKeyStream.close();
                privateKeyStream.close();

                return new KeyPair(publicKey, privateKey);
            } catch (Exception e) {
                // Keep trying until it goes through
                // TODO
            }
        }
    }

    public static KeyPair getPair(int id) throws Exception {
            return KeyGetter.getPair(ProcessConfigBuilder.fromId(id));
    }

    public static PublicKey getPublic(ProcessConfig conf) throws Exception {
        return KeyGetter.getPair(conf).getPublic();
    }

    public static PublicKey getPublic(int id) throws Exception {
        return KeyGetter.getPair(id).getPublic();
    }

    public static PublicKey fromString(String key) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
    }
}

