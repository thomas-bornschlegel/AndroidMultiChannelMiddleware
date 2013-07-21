package de.lmu.mcm.security;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import android.content.Context;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.helper.PrefsHelper;

/**
 * Helper class that provides methods to create, store and access the asymmetric and symmetric keys.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class KeyHolder {

    private final String TAG = "Key Holder ";
    private static KeyHolder keyHolder;

    public static final int AES_KEY_LENGTH = 256;
    public static int RSA_KEY_LENGTH = 1024;
    private final String fileNamePrivateKey = "private.key";
    private final String fileNamePublicKey = "public.key";
    private Map<UUID, SymmetricKeyWrapper> symmetricKeys = new HashMap<UUID, SymmetricKeyWrapper>();

    private KeyHolder() {
    }

    /**
     * @return a singleton instance of the key KeyHolder
     */
    public static KeyHolder getInstance() {
        if (keyHolder == null) {
            keyHolder = new KeyHolder();
        }
        return keyHolder;
    }

    /**
     * Stores a symmetric key in the memory (only valid for the current session).
     * 
     * @param remoteUserId
     *            the UUID of the user that also has this key
     * @param secretKey
     *            the key
     * @param timestamp
     *            creation time of the key
     * 
     * */
    public void storeSymmetricKey(UUID remoteUserId, Key secretKey, long timestamp) {
        SymmetricKeyWrapper key = new SymmetricKeyWrapper(secretKey, timestamp);
        symmetricKeys.put(remoteUserId, key);
    }

    /**
     * Retrieves a key that was stored earlier.
     * 
     * @return the symmetric key or null if none exists for this user
     * */
    public SymmetricKeyWrapper getSymmetricKey(UUID remoteUserId) {
        return symmetricKeys.get(remoteUserId);
    }

    public boolean makeSureOwnKeyPairIsAvailable(Context context) {
        if (isPrivateKeyPairAvailable(context)) {
            LogHelper.getInstance().d(TAG, "Private/public keys were generated before.");
            LogHelper.getInstance().d(TAG, "Loading private/public keys from internal storage...");
            boolean success = getSavedKeyPair(context) != null;
            if (success) {
                LogHelper.getInstance().d(TAG, "Successfully loaded private/public keys from internal storage!");
                return true;
            }
            return false;
        } else {
            LogHelper.getInstance().d(TAG, "Generating private/public keys and saving them internally...");
            return generateAndStoreRandomKeyPair(context);
        }
    }

    /**
     * @return true if a private/public keypair was generated before and was saved in the app's internal storage.
     * */
    private boolean isPrivateKeyPairAvailable(Context context) {
        try {
            PrivateKey privateKey = readPrivateKey(context, fileNamePrivateKey);
            PublicKey publicKey = readPublicKey(context, fileNamePublicKey);
            return privateKey != null && publicKey != null;
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * @return true if the keypair could be generated and saved to the app's internal storage.
     * */
    private boolean generateAndStoreRandomKeyPair(Context context) {
        try {
            KeyPair keypair = generateRandomKeyPair();
            LogHelper.getInstance().d(TAG, "Generated new private/public keys.");
            savePrivateKey(context, fileNamePrivateKey, keypair.getPrivate());
            LogHelper.getInstance().d(TAG, "Saved private key.");
            savePublicKey(context, fileNamePublicKey, keypair.getPublic());
            LogHelper.getInstance().d(TAG, "Saved public key.");
            return true;
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error creating new key pair", e);
        }
        return false;
    }

    /**
     * @return the saved private/public key pair or null if no pair was generated.
     * */
    public KeyPair getSavedKeyPair(Context context) {
        try {
            PrivateKey privateKey = readPrivateKey(context, fileNamePrivateKey);
            PublicKey publicKey = readPublicKey(context, fileNamePublicKey);
            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error creating new key pair", e);
        }
        return null;
    }

    /**
     * Stores the key of the given user on the device. This key will also be available in future sessions.
     * 
     * @return true if the public key of the other user could be saved
     * */
    public boolean storePublicKeyOfOtherUser(Context context, UUID userid, PublicKey publicKey) {
        try {
            String randomFileName = UUID.randomUUID().toString();
            savePublicKey(context, randomFileName, publicKey);
            PrefsHelper.storeFilenamePublicKey(context, userid.toString(), randomFileName);
            return true;
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error saving public key of user: " + userid, e);
        }
        return false;
    }

    /**
     * Reads the public key of the given user from the device.
     * 
     * @return the public key of the given user if it could be retrieved.
     * */
    public PublicKey getPublicKeyOfOtherUser(Context context, UUID uuid) {
        String userid = uuid.toString();
        try {
            String fileName = PrefsHelper.getFilenamePublicKey(context, uuid);
            if (fileName != null) {
                PublicKey publicKey = readPublicKey(context, fileName);
                return publicKey;
            }
            LogHelper.getInstance().d(TAG, "No key found for user: " + userid);
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error reading public key of user: " + userid, e);
        }
        // XXX START: THIS IS JUST FOR TESTING! REMOVE IT WHEN THE TESTS ARE NOT USED ANY MORE
        String ownId = PrefsHelper.getOwnId(context);
        if (userid.equals(ownId)) {
            LogHelper.getInstance().e(TAG,
                    "RETRIEVED OWN PUBLIC KEY FOR ENCRYPTION! THIS SHOULD ONLY HAPPEN IN TEST CASES!!!!");
            LogHelper.getInstance().e(TAG,
                    "RETRIEVED OWN PUBLIC KEY FOR ENCRYPTION! THIS SHOULD ONLY HAPPEN IN TEST CASES!!!!");
            LogHelper.getInstance().e(TAG,
                    "RETRIEVED OWN PUBLIC KEY FOR ENCRYPTION! THIS SHOULD ONLY HAPPEN IN TEST CASES!!!!");
            return getSavedKeyPair(context).getPublic();
        }
        // XXX END: THIS IS JUST FOR TESTING! REMOVE IT WHEN THE TESTS ARE NOT USED ANY MORE

        return null;
    }

    /**
     * Generates a new random AES key.
     * */
    public SecretKey generateRandomSymmetricKey() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = new SecureRandom();
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_LENGTH, secureRandom);

        return keyGenerator.generateKey();
    }

    /**
     * Generates a new random RSA key pair.
     * */
    public KeyPair generateRandomKeyPair() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = new SecureRandom();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(RSA_KEY_LENGTH, secureRandom);
        return keyGen.genKeyPair();
    }

    /**
     * @param publicKeyEncoded
     *            the key in X509 encoding
     * @return a public key generated from the given bytes
     * */
    public PublicKey generatePublicKeyFromBytes(byte[] publicKeyEncoded) throws InvalidKeySpecException,
            NoSuchAlgorithmException {
        // For further infos on X509 see http://docs.oracle.com/javase/tutorial/security/apisign/vstep2.html
        // Maybe useful for the concept of the middleware to define the format of the transfered key.
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyEncoded));
        return publicKey;
    }

    /**
     * Saves a private key with the given fileName on the file system.
     * */
    private void savePrivateKey(Context context, String fileName, PrivateKey privateKey) throws Exception {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        RSAPrivateKeySpec priv = fact.getKeySpec(privateKey, RSAPrivateKeySpec.class);
        saveToFile(context, fileName, priv.getModulus(), priv.getPrivateExponent());
    }

    /**
     * Saves a public key with the given fileName on the file system.
     * */
    private void savePublicKey(Context context, String fileName, PublicKey publicKey) throws Exception {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        RSAPublicKeySpec pub = fact.getKeySpec(publicKey, RSAPublicKeySpec.class);
        saveToFile(context, fileName, pub.getModulus(), pub.getPublicExponent());
    }

    /**
     * Saves the two prime numbers identifying a public/private key to a file.
     * */
    private void saveToFile(Context context, String fileName, BigInteger mod, BigInteger exp) throws Exception {
        // Based on source taken from http://stackoverflow.com/a/9890863
        FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(outputStream));
        try {
            oout.writeObject(mod);
            oout.writeObject(exp);
        } catch (IOException e) {
            throw new Exception(e);
        } finally {
            oout.close();
        }
    }

    /**
     * Reads a public key from a file.
     * */
    private PublicKey readPublicKey(Context context, String fileName) throws Exception {
        // Based on source taken from http://stackoverflow.com/a/9890863
        InputStream in = context.openFileInput(fileName);
        ObjectInputStream oin = new ObjectInputStream(new BufferedInputStream(in));
        try {
            BigInteger m = (BigInteger) oin.readObject();
            BigInteger e = (BigInteger) oin.readObject();
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(m, e);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            PublicKey pubKey = fact.generatePublic(keySpec);
            return pubKey;
        } catch (Exception e) {
            throw new Exception(e);
        } finally {
            oin.close();
        }
    }

    /**
     * Reads a private key from a file.
     * */
    private PrivateKey readPrivateKey(Context context, String fileName) throws Exception {
        // Based on source taken from http://stackoverflow.com/a/9890863
        InputStream in = context.openFileInput(fileName);
        ObjectInputStream oin = new ObjectInputStream(new BufferedInputStream(in));
        try {
            BigInteger m = (BigInteger) oin.readObject();
            BigInteger e = (BigInteger) oin.readObject();
            RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(m, e);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = fact.generatePrivate(keySpec);
            return privateKey;
        } catch (Exception e) {
            throw new Exception(e);
        } finally {
            oin.close();
        }
    }

    /**
     * Wrapper class to link a symmetric key to a timestamp.
     * */
    public class SymmetricKeyWrapper {
        private Key key;
        private long timestamp;

        public SymmetricKeyWrapper(Key key, long timestamp) {
            super();
            this.key = key;
            this.timestamp = timestamp;
        }

        public Key getKey() {
            return key;
        }

        public long getTimestamp() {
            return timestamp;
        }

    }
}
