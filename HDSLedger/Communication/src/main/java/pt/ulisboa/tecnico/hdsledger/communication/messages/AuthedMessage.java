package pt.ulisboa.tecnico.hdsledger.communication.messages;

import javax.crypto.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.Arrays;

import com.google.gson.Gson;

public class AuthedMessage extends Message {
    private String data; // Json Message

    private String signature;
    private String key;
    private String hostname;
    private String timeStamp;
    private Integer port;

    private static String serializeToBase64(Serializable object) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(object);
      oos.close();
      return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public AuthedMessage(Message data) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidKeySpecException, IOException, IllegalBlockSizeException, BadPaddingException, Exception { // TODO : Exception
        super(data.getSenderId(), data.getType()); 
        
        // Transfer piggyback messages to this message;
        this.setPiggyback(data.getPiggyback());
        data.clearPiggyback();

        ProcessConfig author = ProcessConfigBuilder.fromId(data.getSenderId());
        KeyPair keyPair = KeyGetter.getPair(author);

        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, keyPair.getPrivate());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        this.key = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        this.data = new Gson().toJson(data);
        this.hostname = author.getHostname();
        this.port = author.getPort();
        this.timeStamp = new Date().toString();
        byte[] rawDigest = digest.digest((this.data + this.timeStamp).getBytes());
        this.signature = Base64.getEncoder().encodeToString(encryptCipher.doFinal(rawDigest));
    }

    public boolean isKeyCorrect() throws Exception {
            PublicKey senderPublicKey = KeyGetter.getPublic(this.getSenderId());
            String senderPublicKeyBase64 = Base64.getEncoder().encodeToString(senderPublicKey.getEncoded());
            return senderPublicKeyBase64.equals(this.key);
    }
    public boolean isSignatureCorrect() throws Exception { 
            PublicKey senderPublicKey = KeyGetter.getPublic(this.getSenderId());
            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, senderPublicKey);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] decryptedSignature = decryptCipher.doFinal(Base64.getDecoder().decode(this.signature));
            byte[] rawDigest = digest.digest((this.data + this.timeStamp).getBytes());

            return Arrays.toString(decryptedSignature).equals(Arrays.toString(rawDigest));
    }

    @Override
    public void verify() throws Exception {
        try {
            if (!this.isKeyCorrect())
                throw new Exception();
        } catch (Exception e) {
            throw new HDSSException(ErrorMessage.InvalidMessageWrongKey);
        }
        try {
            if (!this.isSignatureCorrect())
                throw new Exception();
        } catch (Exception e) {
            throw new HDSSException(ErrorMessage.InvalidMessageWrongSignature);
        }
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String getSignature() {
        return this.signature;
    }

    public String getKey() {
        return this.key;
    }

    public String getHostname() {
        return this.hostname;
    }

    public String getTimeStamp() {
        return this.timeStamp;
    }

    public int getPort() {
        return this.port;
    }

    public String getData() {
        return this.data;
    }

    public Message getDataAsMessage() {
        return this.getDataAs(Message.class);
    }

    public <T extends Message> T getDataAs(Class<T> messageClass) {
        T m = new Gson().fromJson(this.data, messageClass);
        // Append piggyback messages
        m.setPiggyback(this.getPiggyback());
        return m;
    }
}

