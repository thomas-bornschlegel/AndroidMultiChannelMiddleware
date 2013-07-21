package de.lmu.mcm.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import android.content.Context;
import android.test.AndroidTestCase;
import de.lmu.mcm.helper.PrefsHelper;
import de.lmu.mcm.security.MessageEncryptionHandler;
import de.lmu.mcm.security.KeyHolder;
import de.lmu.mcm.security.byteproto.BasicMessage;
import de.lmu.mcm.security.byteproto.HandshakeMessage1;
import de.lmu.mcm.security.byteproto.HandshakeMessage2;
import de.lmu.mcm.security.byteproto.HandshakeMessage3;
import de.lmu.mcm.security.byteproto.HandshakeMessage4;
import de.lmu.mcm.security.byteproto.PublicKeyExchangeMessage;

public class HandshakeTest extends AndroidTestCase {

    public void setUp() throws Exception {

    }

    public void testSendingOfHanshakeMsg1() throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            InvalidKeySpecException {
        UUID ownId = setupEncryptionParameters();

        // Create message
        byte messageType = 1;
        HandshakeMessage1 message = new HandshakeMessage1(System.currentTimeMillis(), ownId);
        assertTrue(message.equals(new HandshakeMessage1(message.getMessageContentAsBytes())));

        MessageEncryptionHandler encryptionHandler = new MessageEncryptionHandler();
        // Encrypt
        byte[] toSend = encryptionHandler.prepareMessageForSending(getContext(), ownId, message, messageType);
        // byte[] encryptedMessage = encryptionHandler.encryptMessage(getContext(), ownId, message, messageType);
        // Decrypt
        BasicMessage receivedMessage = encryptionHandler.extractReceivedMessage(getContext(), ownId, toSend);

        // Compare
        // HandshakeMessage1 messageRestored = new HandshakeMessage1(decryptedMessage);
        assertTrue(message.equals(receivedMessage));
    }

    public void testEncryptionAndDecryptionHanshakeMsg1() throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        UUID ownId = setupEncryptionParameters();

        // Create message
        byte messageType = 1;
        HandshakeMessage1 message = new HandshakeMessage1(System.currentTimeMillis(), ownId);
        assertTrue(message.equals(new HandshakeMessage1(message.getMessageContentAsBytes())));

        MessageEncryptionHandler encryptionHandler = new MessageEncryptionHandler();
        // Encrypt
        byte[] encryptedMessage = encryptMessage(encryptionHandler, getContext(), ownId, message, messageType);
        // Decrypt
        byte[] decryptedMessage = decryptMessage(encryptionHandler, getContext(), ownId, encryptedMessage, messageType);
        // Compare
        HandshakeMessage1 messageRestored = new HandshakeMessage1(decryptedMessage);
        assertTrue(message.equals(messageRestored));
    }

    public void testEncryptionAndDecryptionHanshakeMsg2() throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        UUID ownId = setupEncryptionParameters();

        // Create message
        byte messageType = 2;
        SecureRandom random = new SecureRandom();
        HandshakeMessage2 message = new HandshakeMessage2(random.nextLong(), random.nextLong());

        MessageEncryptionHandler encryptionHandler = new MessageEncryptionHandler();
        // Encrypt
        byte[] encryptedMessage = encryptMessage(encryptionHandler, getContext(), ownId, message, messageType);
        // Decrypt
        byte[] decryptedMessage = decryptMessage(encryptionHandler, getContext(), ownId, encryptedMessage, messageType);
        // Compare
        HandshakeMessage2 messageRestored = new HandshakeMessage2(decryptedMessage);
        assertTrue(message.equals(messageRestored));
    }

    public void testEncryptionAndDecryptionHanshakeMsg3() throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        UUID ownId = setupEncryptionParameters();

        // Create message
        byte messageType = 3;
        SecureRandom random = new SecureRandom();
        HandshakeMessage3 message = new HandshakeMessage3(random.nextLong());

        MessageEncryptionHandler encryptionHandler = new MessageEncryptionHandler();
        // Encrypt
        byte[] encryptedMessage = encryptMessage(encryptionHandler, getContext(), ownId, message, messageType);
        // Decrypt
        byte[] decryptedMessage = decryptMessage(encryptionHandler, getContext(), ownId, encryptedMessage, messageType);
        // Compare
        HandshakeMessage3 messageRestored = new HandshakeMessage3(decryptedMessage);
        assertTrue(message.equals(messageRestored));
    }

    public void testEncryptionAndDecryptionHanshakeMsg4() throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        UUID ownId = setupEncryptionParameters();

        // Create message
        byte messageType = 4;
        SecureRandom random = new SecureRandom();
        Key key = KeyHolder.getInstance().generateRandomSymmetricKey();
        HandshakeMessage4 message = new HandshakeMessage4(UUID.randomUUID(), UUID.randomUUID(), random.nextLong(), key);

        MessageEncryptionHandler encryptionHandler = new MessageEncryptionHandler();
        // Encrypt
        byte[] encryptedMessage = encryptMessage(encryptionHandler, getContext(), ownId, message, messageType);
        // Decrypt
        byte[] decryptedMessage = decryptMessage(encryptionHandler, getContext(), ownId, encryptedMessage, messageType);
        // Compare
        HandshakeMessage4 messageRestored = new HandshakeMessage4(decryptedMessage);
        assertTrue(message.equals(messageRestored));
    }

    private UUID setupEncryptionParameters() {
        // Setup new random keys
        // UUID uuidOfReceiver = UUID.randomUUID();
        KeyHolder keyHolder = KeyHolder.getInstance();
        // Own keys
        keyHolder.makeSureOwnKeyPairIsAvailable(getContext());
        // Keys of receiver
        // KeyPair keyPairReceiver = keyHolder.generateRandomKeyPair();
        // keyHolder.storePublicKeyOfOtherUser(getContext(), uuidOfReceiver.toString(), keyPairReceiver.getPublic());
        // Own ID
        PrefsHelper.generateOwnIdIfNotPresent(getContext());
        String ownIdString = PrefsHelper.getOwnId(getContext());
        UUID ownId = UUID.fromString(ownIdString);
        return ownId;
    }

    public void testHandshakeMessage1() {
        HandshakeMessage1 message = new HandshakeMessage1(System.currentTimeMillis(), UUID.randomUUID());
        byte[] msgInBytes = message.getMessageContentAsBytes();
        HandshakeMessage1 messageRestored = new HandshakeMessage1(msgInBytes);
        assertTrue(message.equals(messageRestored));
    }

    public void testHandshakeMessage2() {
        SecureRandom random = new SecureRandom();
        HandshakeMessage2 message = new HandshakeMessage2(random.nextLong(), random.nextLong());
        byte[] msgInBytes = message.getMessageContentAsBytes();
        HandshakeMessage2 messageRestored = new HandshakeMessage2(msgInBytes);
        assertTrue(message.equals(messageRestored));
    }

    public void testHandshakeMessage3() {
        SecureRandom random = new SecureRandom();
        HandshakeMessage3 message = new HandshakeMessage3(random.nextLong());
        byte[] msgInBytes = message.getMessageContentAsBytes();
        HandshakeMessage3 messageRestored = new HandshakeMessage3(msgInBytes);
        assertTrue(message.equals(messageRestored));
    }

    public void testHandshakeMessage4() throws NoSuchAlgorithmException, NoSuchPaddingException {
        SecureRandom random = new SecureRandom();
        Key key = KeyHolder.getInstance().generateRandomSymmetricKey();
        HandshakeMessage4 message = new HandshakeMessage4(UUID.randomUUID(), UUID.randomUUID(), random.nextLong(), key);
        byte[] msgInBytes = message.getMessageContentAsBytes();
        HandshakeMessage4 messageRestored = new HandshakeMessage4(msgInBytes);
        assertTrue(message.equals(messageRestored));
    }

    public void testPublicKeyExchange() throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeySpecException {
        // Generate random key
        KeyHolder keyHolder = KeyHolder.getInstance();
        KeyPair keyPair = keyHolder.generateRandomKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        // Generate random bluetooth device address
        byte[] bluetoothAddress = new byte[6];
        SecureRandom random = new SecureRandom();
        random.nextBytes(bluetoothAddress);

        // Test message
        PublicKeyExchangeMessage message = new PublicKeyExchangeMessage(UUID.randomUUID(), System.currentTimeMillis(),
                bluetoothAddress, publicKey);
        byte[] msgInBytes = message.getMessageContentAsBytes();
        PublicKeyExchangeMessage messageRestored = new PublicKeyExchangeMessage(msgInBytes);
        assertTrue(message.equals(messageRestored));
    }

    /**
     * This method is used to access a private method.
     * */
    public byte[] encryptMessage(MessageEncryptionHandler encryptionHandler, Context context, UUID uuid,
            BasicMessage message, byte messageType) throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        Class[] argClasses = { Context.class, UUID.class, BasicMessage.class, byte.class };
        Object[] argObjects = { context, uuid, message, messageType };
        Method method = MessageEncryptionHandler.class.getDeclaredMethod("encryptMessage", argClasses);
        method.setAccessible(true);
        Object result = method.invoke(encryptionHandler, argObjects);

        return (byte[]) result;
    }

    /**
     * This method is used to access a private method.
     * */
    public byte[] decryptMessage(MessageEncryptionHandler encryptionHandler, Context context, UUID uuid,
            byte[] message, byte messageType) throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        Class[] argClasses = { Context.class, UUID.class, byte[].class, byte.class };
        Object[] argObjects = { context, uuid, message, messageType };
        Method method = MessageEncryptionHandler.class.getDeclaredMethod("decryptMessage", argClasses);
        method.setAccessible(true);
        Object result = method.invoke(encryptionHandler, argObjects);

        return (byte[]) result;
    }

}
