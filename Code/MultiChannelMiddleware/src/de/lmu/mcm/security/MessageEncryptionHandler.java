package de.lmu.mcm.security;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import android.content.Context;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.security.KeyHolder.SymmetricKeyWrapper;
import de.lmu.mcm.security.byteproto.BasicMessage;
import de.lmu.mcm.security.byteproto.CustomMessage;
import de.lmu.mcm.security.byteproto.HandshakeMessage1;
import de.lmu.mcm.security.byteproto.HandshakeMessage2;
import de.lmu.mcm.security.byteproto.HandshakeMessage3;
import de.lmu.mcm.security.byteproto.HandshakeMessage4;
import de.lmu.mcm.security.byteproto.PublicKeyExchangeMessage;

/**
 * Used to encode and decode messages.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class MessageEncryptionHandler {
    private final String TAG = MessageEncryptionHandler.class.getSimpleName();

    /**
     * Inserts the length indicator and the message type to the front of the bytes of the given message. Also performs
     * encryption according to the message type.
     * 
     * @param uuidOfReceiver
     *            can be null if the receiver is not specified (this is only possible for messages without encryption)
     * 
     * */
    public byte[] prepareMessageForSending(Context context, UUID uuidOfReceiver, BasicMessage message, byte messageType)
            throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException {
        byte[] encryptedMessage = encryptMessage(context, uuidOfReceiver, message, messageType);
        ByteBuffer buffer = ByteBuffer.allocate(encryptedMessage.length + Integer.SIZE / 8 + Byte.SIZE / 8);
        buffer.putInt(encryptedMessage.length);
        buffer.put(messageType);
        buffer.put(encryptedMessage);
        return buffer.array();
    }

    /**
     * Extracts the message as it was received from the other device. Including the length indicator, the message type
     * and the message content. If the content was encrypted it is returned unencrypted. If the message could not be
     * restored null is returned.
     * 
     * @param uuidOfSender
     *            can be null if the sender is not specified (this is only possible for messages without encryption)
     * @return the parsed message or null if an error occured
     * 
     * */
    public BasicMessage extractReceivedMessage(Context context, UUID uuidOfSender, byte[] rawMessageInBytes)
            throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        if (rawMessageInBytes != null) {
            if (rawMessageInBytes.length > 5) {
                // The first 4 bytes contain the length indicator (int), the fifth byte (index=4) contains the message
                // type, everything afterwards is the message content
                ByteBuffer buffer = ByteBuffer.wrap(rawMessageInBytes);
                int messageLength = buffer.getInt();
                byte messageType = buffer.get();
                if (messageType < 0 || messageType > 8) {
                    LogHelper.getInstance().e(TAG, "Invalid message type: " + messageType + "!");
                    return null;
                }
                // The length indicator and the message type are not counted
                int lengthOfMessageTypeAndContent = rawMessageInBytes.length - 4 - 1;
                if (lengthOfMessageTypeAndContent != messageLength) {
                    LogHelper.getInstance().e(
                            TAG,
                            "Length indicator and message content had different length: indicator=" + messageLength
                                    + " content=" + lengthOfMessageTypeAndContent);
                    return null;
                } else {
                    byte[] messageContent = new byte[messageLength];
                    buffer.get(messageContent);

                    byte[] unencryptedContent = decryptMessage(context, uuidOfSender, messageContent, messageType);

                    if (unencryptedContent == null) {
                        return null;
                    }

                    buffer = ByteBuffer.wrap(unencryptedContent);
                    int lengthOfHash = 20;
                    byte[] messageContentWithoutHash = new byte[unencryptedContent.length - lengthOfHash];
                    byte[] receivedHash = new byte[lengthOfHash];

                    buffer.get(messageContentWithoutHash);
                    buffer.get(receivedHash);

                    byte[] calculatedHash = getSha1HashOfMessage(messageContentWithoutHash);

                    boolean hashesMatch = Arrays.equals(receivedHash, calculatedHash);

                    if (!hashesMatch) {
                        LogHelper.getInstance().e(TAG, "Hashes did not match!");
                        ByteConverter.printBytes("Received hash", receivedHash);
                        ByteConverter.printBytes("Calculated hash", calculatedHash);
                        return null;
                    } else {
                        LogHelper.getInstance().d(TAG, "Hashes matched");
                    }

                    switch (messageType) {
                    case 0:
                        return new PublicKeyExchangeMessage(messageContentWithoutHash);
                    case 1:
                        return new HandshakeMessage1(messageContentWithoutHash);
                    case 2:
                        return new HandshakeMessage2(messageContentWithoutHash);
                    case 3:
                        return new HandshakeMessage3(messageContentWithoutHash);
                    case 4:
                        return new HandshakeMessage4(messageContentWithoutHash);
                    default:
                        return new CustomMessage(messageContentWithoutHash);
                    }
                }
            } else {
                LogHelper.getInstance().e(TAG,
                        "Message was too short (" + rawMessageInBytes.length + "), can not return content!");
            }
        } else {
            LogHelper.getInstance().e(TAG, "Message was null, can not return content!");
        }
        return null;

    }

    /**
     * 
     * @param uuidOfReceiver
     *            can be null if the receiver is not specified (this is only possible for messages without encryption)
     * 
     * @return null if the message could not be encrypted. Otherwise the encrypted message in bytes
     * 
     * */
    private byte[] encryptMessage(Context context, UUID uuidOfReceiver, BasicMessage message, byte messageType)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException {

        KeyHolder keyHolder = KeyHolder.getInstance();
        RsaHelper rsa = new RsaHelper();

        // Append the SHA-1-Hash of the message:
        byte[] messageBytes = message.getMessageContentAsBytes();
        byte[] messageHash = getSha1HashOfMessage(messageBytes);
        messageBytes = ByteConverter.combineMultipleByteArrays(messageBytes, messageHash);

        if (messageType == 0) {
            LogHelper.getInstance().i(TAG, "No encryption neccessary for public key exchange.");
            return messageBytes;
        } else if (messageType == 1 || messageType == 2 || messageType == 3) {
            // Asymmetric encryption with public key of receiver
            return encryptMessageWithPublicKeyOfReceiver(context, uuidOfReceiver, messageBytes, rsa, keyHolder);
        } else if (messageType == 4) {
            // Asymmetric encryption with own private key and afterwards with the public key of the receiver
            PrivateKey ownPrivateKey = getOwnPrivateKey(context, keyHolder);
            if (ownPrivateKey == null) {
                LogHelper.getInstance().e(TAG, "Could not retrieve own private key!");
                return null;
            }
            byte[] encrypted = rsa.encrypt(messageBytes, ownPrivateKey);
            LogHelper.getInstance().d(TAG, "Encrypted message with own private key");
            if (encrypted != null) {
                byte[] secondEncryption = encryptMessageWithPublicKeyOfReceiver(context, uuidOfReceiver, encrypted,
                        rsa, keyHolder);
                return secondEncryption;
            }
            LogHelper.getInstance().e(TAG, "Encrypted message with own private key was null!");
            return null;
        } else if (messageType == 5) {
            return encryptMessageWithPublicKeyOfReceiver(context, uuidOfReceiver, messageBytes, rsa, keyHolder);
        } else if (messageType == 6) {
            PrivateKey ownPrivateKey = getOwnPrivateKey(context, keyHolder);
            if (ownPrivateKey != null) {
                byte[] encrypted = rsa.encrypt(messageBytes, ownPrivateKey);
                if (encrypted != null) {
                    return encrypted;
                } else {
                    LogHelper.getInstance().e(TAG, "Could not encrypt message with own private key!");
                }
            } else {
                LogHelper.getInstance().e(TAG, "Own private key was null!");
            }
            return null;
        } else if (messageType == 7) {
            AesHelper aes = new AesHelper();
            SymmetricKeyWrapper keyWrapper = keyHolder.getSymmetricKey(uuidOfReceiver);
            if (keyWrapper == null) {
                LogHelper.getInstance()
                        .e(TAG, "Could not retrieve symmetric key of user: " + uuidOfReceiver.toString());
                return null;
            }
            byte[] encrypted = aes.encrypt(messageBytes, keyWrapper.getKey());
            LogHelper.getInstance()
                    .d(TAG, "Encrypted message with symmetric key of user: " + uuidOfReceiver.toString());
            return encrypted;
        } else if (messageType == 8) {
            LogHelper.getInstance().i(TAG, "No encryption neccessary for message without encryption");
            return messageBytes;
        }
        LogHelper.getInstance().e(TAG, "Wrong message type! Returning raw message without encryption.");
        return messageBytes;
    }

    /**
     * @param uuidOfSender
     *            can be null if the sender is not specified (this is only possible for messages without encryption)
     * @return null if the message could not be decrypted. Otherwise the decrypted message in bytes
     * 
     * */
    private byte[] decryptMessage(Context context, UUID uuidOfSender, byte[] message, byte messageType)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException {

        KeyHolder keyHolder = KeyHolder.getInstance();
        RsaHelper rsa = new RsaHelper();

        if (messageType == 1 || messageType == 2 || messageType == 3) {
            // Asymmetric decryption with own private key
            return decryptMesssageWithOwnPrivateKey(context, message, rsa, keyHolder);
        } else if (messageType == 4) {
            // Asymmetric decryption with own private key
            byte[] decrypted = decryptMesssageWithOwnPrivateKey(context, message, rsa, keyHolder);
            if (decrypted == null) {
                return null;
            }
            // Asymmetric decryption with public key of sender
            decrypted = decryptMessageWithPublicKeyOfSender(context, uuidOfSender, decrypted, rsa, keyHolder);
            return decrypted;
        } else if (messageType == 0) {
            LogHelper.getInstance().i(TAG, "No decryption neccessary for public key exchange.");
            return message;
        } else if (messageType == 5) {
            // Asymmetric decryption with own private key
            return decryptMesssageWithOwnPrivateKey(context, message, rsa, keyHolder);
        } else if (messageType == 6) {
            // Asymmetric decryption with public key of sender
            return decryptMessageWithPublicKeyOfSender(context, uuidOfSender, message, rsa, keyHolder);
        } else if (messageType == 7) {
            AesHelper aes = new AesHelper();
            SymmetricKeyWrapper keyWrapper = keyHolder.getSymmetricKey(uuidOfSender);
            if (keyWrapper == null) {
                LogHelper.getInstance().e(TAG, "Could not retrieve symmetric key of user: " + uuidOfSender.toString());
                return null;
            }
            byte[] decrypted = aes.decrypt(message, keyWrapper.getKey());
            LogHelper.getInstance().d(TAG, "Decrypted message with symmetric key of user: " + uuidOfSender.toString());
            return decrypted;
        } else if (messageType == 8) {
            LogHelper.getInstance().i(TAG, "No encryption neccessary for message without encryption");
            return message;
        }

        LogHelper.getInstance().e(TAG, "Could not decrypt message of type " + messageType + "!");
        return null;
    }

    private byte[] decryptMesssageWithOwnPrivateKey(Context context, byte[] message, RsaHelper rsa, KeyHolder keyHolder)
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        PrivateKey key = getOwnPrivateKey(context, keyHolder);
        if (key == null) {
            LogHelper.getInstance().e(TAG, "Could not retrieve own private key!");
            return null;
        }
        byte[] decrypted = rsa.decrypt(message, key);
        LogHelper.getInstance().d(TAG, "Decrypted message with own private key");
        return decrypted;
    }

    private byte[] decryptMessageWithPublicKeyOfSender(Context context, UUID sender, byte[] message, RsaHelper rsa,
            KeyHolder keyHolder) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        PublicKey key = keyHolder.getPublicKeyOfOtherUser(context, sender);
        if (key == null) {
            LogHelper.getInstance().e(TAG, "Could not retrieve public key of user: " + sender.toString());
            return null;
        }
        byte[] decrypted = rsa.decrypt(message, key);
        LogHelper.getInstance().d(TAG, "Decrypted message with public key of user: " + sender.toString());
        return decrypted;
    }

    private byte[] encryptMessageWithPublicKeyOfReceiver(Context context, UUID receiver, byte[] message, RsaHelper rsa,
            KeyHolder keyHolder) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        PublicKey key = keyHolder.getPublicKeyOfOtherUser(context, receiver);
        if (key == null) {
            LogHelper.getInstance().e(TAG, "Could not retrieve public key of user: " + receiver.toString());
            return null;
        }
        byte[] encrypted = rsa.encrypt(message, key);
        LogHelper.getInstance().d(TAG, "Encrypted message with public key of user: " + receiver.toString());
        return encrypted;
    }

    private PrivateKey getOwnPrivateKey(Context context, KeyHolder keyHolder) {
        KeyPair keyPair = keyHolder.getSavedKeyPair(context);
        if (keyPair == null) {
            return null;
        }
        return keyPair.getPrivate();
    }

    private byte[] getSha1HashOfMessage(byte[] message) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(message, 0, message.length);
        return md.digest();
    }

    public String getMessageTypeAsString(byte messageType) {
        switch (messageType) {
        case 0:
            return "Public Key Exchange";
        case 1:
            return "Handshake Message 1";
        case 2:
            return "Handshake Message 2";
        case 3:
            return "Handshake Message 3";
        case 4:
            return "Handshake Message 4";
        case 5:
            return "Content encrypted with public key of receiver.";
        case 6:
            return "Content encrypted with symmetric key.";
        case 7:
            return "Unencrypted content.";
        }

        return "Unknown type";
    }

}
