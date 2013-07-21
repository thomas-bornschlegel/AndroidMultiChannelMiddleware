package de.lmu.mcm.security;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.json.JSONException;

import android.content.Context;
import de.lmu.mcm.helper.LogHelper;
import de.lmu.mcm.network.Enums.HandshakeNextAction;
import de.lmu.mcm.network.Enums.HandshakeRole;
import de.lmu.mcm.security.byteproto.BasicMessage;
import de.lmu.mcm.security.byteproto.HandshakeMessage1;
import de.lmu.mcm.security.byteproto.HandshakeMessage2;
import de.lmu.mcm.security.byteproto.HandshakeMessage3;
import de.lmu.mcm.security.byteproto.HandshakeMessage4;

/**
 * This class helps to initiate a handshake and symmetric key exchange between to parties A and B. In short the
 * following messages are exchanged:
 * 
 * <pre>
 * 1. A => pubB(idA, nonceA) => B
 * 2. B => pubA(nonceA, nonceB) => A
 * 3. A => pubB(nonceB) => B
 * 4. B => pubA(privB(symmetricKey, A, B, timestamp))
 * </pre>
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class HandshakeStateHolder {

    private String TAG = HandshakeStateHolder.class.getSimpleName() + " ";

    private UUID idA;
    private UUID idB;
    private byte step;
    private HandshakeRole role;
    private MessageEncryptionHandler encryptionHandler = new MessageEncryptionHandler();

    private long nonceFromFirstMessageA;
    private long nonceFromSecondMessageB;
    private BasicMessage message1FromAToB;
    private BasicMessage message2FromBToA;
    private BasicMessage message3FromAToB;
    private BasicMessage message4FromBToA;

    private SecureRandom random = new SecureRandom();

    /**
     * Constructs a fresh handshake holder
     * 
     * @param idA
     * @param idB
     * @param role
     *            the party that sends the first message has role "A", the other party has role "B"
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     */
    public HandshakeStateHolder(UUID idA, UUID idB, HandshakeRole role) throws NoSuchAlgorithmException,
            NoSuchPaddingException {
        this.idA = idA;
        this.idB = idB;
        this.role = role;
        this.step = 1;

        LogHelper.getInstance().d(
                TAG,
                "Initialized HandshakeHelper as " + role + " with UUID-A: " + idA.toString() + " and UUID-B: "
                        + idB.toString());
    }

    /**
     * Call this method to check if your next step is sending or receiving
     * 
     * @return if this instance is currently the receiver or sender
     * */
    public HandshakeNextAction getNextAction() {
        if (step == 1 && role == HandshakeRole.A || step == 3 && role == HandshakeRole.A || step == 2
                && role == HandshakeRole.B || step == 4 && role == HandshakeRole.B) {
            return HandshakeNextAction.SEND;
        } else {
            return HandshakeNextAction.RECEIVE;
        }
    }

    /**
     * Call this method to get the next message to send. Do not call it multiple times when you did not receive a new
     * message from the other party and did not process this message with
     * {@link #processReceivedMessage(Context, ParsedProtocolMessage)}! If you need to send the message multiple times,
     * cache the returned value instead.
     * 
     * Only call this method if {@link #getNextAction()} returns HandshakeNextAction.SEND.
     * 
     * @param context
     *            the context
     * 
     * @return the next message to send or null if this message was called incorrectly.
     * */
    public BasicMessage getNextMessageToSend(Context context) {
        if (getNextAction() != HandshakeNextAction.SEND) {
            LogHelper.getInstance().e(TAG, "Called getNextMessage while in receiving state");
            return null;
        }
        try {
            BasicMessage msg = null;
            if (step == 1 && role == HandshakeRole.A) {
                step = 2;
                msg = getMessage1FromAtoB();
            } else if (step == 2 && role == HandshakeRole.B) {
                step = 3;
                msg = getMessage2FromBtoA();
            } else if (step == 3 && role == HandshakeRole.A) {
                step = 4;
                msg = getMessage3FromAtoB();
            } else if (step == 4 && role == HandshakeRole.B) {
                step = 5;
                msg = getMessage4FromBtoA();
            }
            if (msg != null) {
                return msg;
            }
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error while trying to get next message to send in step " + step, e);
            e.printStackTrace();
        }
        return null;
    }

    public byte getMessageTypeForMessageToSend() {
        return (byte) (step - 1);
    }

    /**
     * Call this method to process a received message.
     * 
     * Only call this method if {@link #getNextAction()} returns HandshakeNextAction.RECEIVE.
     * 
     * @param context
     *            the context
     * @param rawMsg
     *            the received message
     * 
     * @return true if the message was correct, false otherwise.
     * */
    public boolean processReceivedMessage(Context context, BasicMessage msg) throws JSONException {
        if (msg == null) {
            LogHelper.getInstance().e(TAG, "Message was null");
            return false;
        }
        if (getNextAction() != HandshakeNextAction.RECEIVE) {
            LogHelper.getInstance().e(TAG, "processReceivedMessage was called while in send mode");
            return false;
        }
        try {
            if (step == 1 && role == HandshakeRole.B) {
                if (!(msg instanceof HandshakeMessage1)) {
                    LogHelper.getInstance().e(TAG, "Message 1 was of wrong type: " + msg.getClass().getSimpleName());
                    return false;
                }
                HandshakeMessage1 msg1 = (HandshakeMessage1) msg;
                if (!idA.equals(msg1.getIdA())) {
                    LogHelper.getInstance().e(TAG, "ID of A in first message was wrong!");
                    return false;
                }
                this.nonceFromFirstMessageA = msg1.getNonceA();
                step = 2;
                return true;
            } else if (step == 2 && role == HandshakeRole.A) {
                if (!(msg instanceof HandshakeMessage2)) {
                    LogHelper.getInstance().e(TAG, "Message 2 was of wrong type: " + msg.getClass().getSimpleName());
                    return false;
                }
                HandshakeMessage2 msg2 = (HandshakeMessage2) msg;
                if (msg2.getNonceA() != nonceFromFirstMessageA) {
                    LogHelper.getInstance().e(TAG, "Received nonce in message 2 was wrong!");
                    return false;
                }
                this.nonceFromSecondMessageB = msg2.getNonceB();
                step = 3;
                return true;
            } else if (step == 3 && role == HandshakeRole.B) {
                if (!(msg instanceof HandshakeMessage3)) {
                    LogHelper.getInstance().e(TAG, "Message 3 was of wrong type: " + msg.getClass().getSimpleName());
                    return false;
                }
                HandshakeMessage3 msg3 = (HandshakeMessage3) msg;
                if (msg3.getNonceB() != nonceFromSecondMessageB) {
                    LogHelper.getInstance().e(TAG, "Received nonce in message 3 was wrong!");
                    return false;
                }
                step = 4;
                return true;
            } else if (step == 4 && role == HandshakeRole.A) {
                if (!(msg instanceof HandshakeMessage4)) {
                    LogHelper.getInstance().e(TAG, "Message 4 was of wrong type: " + msg.getClass().getSimpleName());
                    return false;
                }
                HandshakeMessage4 msg4 = (HandshakeMessage4) msg;

                if (!idA.equals(msg4.getIdA())) {
                    LogHelper.getInstance().e(TAG, "ID of A in forth message was wrong!");
                    return false;
                }
                if (!idB.equals(msg4.getIdB())) {
                    LogHelper.getInstance().e(TAG, "ID of B in forth message was wrong!");
                    return false;
                }
                KeyHolder.getInstance().storeSymmetricKey(idB, msg4.getSymmetricKey(), msg4.getTimestamp());
                step = 5;
                return true;
            }
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG, "Error while trying to get next message to receive in step " + step, e);
        }
        LogHelper.getInstance().e(TAG, "Received message was not correct!");
        return false;
    }

    /**
     * @return the first message from A to B. Contains "A => pubB(idA, nonceA) => B"
     * */
    private BasicMessage getMessage1FromAtoB() throws Exception {

        if (message1FromAToB == null) {
            this.nonceFromFirstMessageA = generateNonce();
            message1FromAToB = new HandshakeMessage1(nonceFromFirstMessageA, idA);
        }

        return message1FromAToB;
    }

    /**
     * @return the first message from B to A. Contains "B => pubA(nonceA, nonceB) => A"
     * */
    private BasicMessage getMessage2FromBtoA() throws Exception {

        if (message2FromBToA == null) {
            this.nonceFromSecondMessageB = generateNonce();
            message2FromBToA = new HandshakeMessage2(nonceFromFirstMessageA, nonceFromSecondMessageB);
        }

        return message2FromBToA;
    }

    /**
     * @return the third message from A to B. Contains "A => pubB(nonceB) => B"
     * */
    private BasicMessage getMessage3FromAtoB() throws Exception {

        if (message3FromAToB == null) {
            message3FromAToB = new HandshakeMessage3(nonceFromSecondMessageB);

        }
        return message3FromAToB;
    }

    /**
     * @return the fourth message from B to A. Contains "B => pubA(privB(symmetricKey, A, B, timestamp))"
     * */
    private BasicMessage getMessage4FromBtoA() throws Exception {

        if (message4FromBToA == null) {
            // Generate key
            SecretKey symmetricKey = KeyHolder.getInstance().generateRandomSymmetricKey();
            long timeStamp = System.currentTimeMillis();
            KeyHolder.getInstance().storeSymmetricKey(idA, symmetricKey, timeStamp);

            // Generate message
            message4FromBToA = new HandshakeMessage4(idA, idB, timeStamp, symmetricKey);
        }
        return message4FromBToA;
    }

    private Long generateNonce() {
        return random.nextLong();
    }

    private UUID getUuidOfReceiver() {
        if (role == HandshakeRole.A) {
            return idB;
        } else {
            return idA;
        }
    }

    private UUID getUuidOfSender() {
        if (role == HandshakeRole.A) {
            return idB;
        } else {
            return idA;
        }
    }

    /**
     * @return the current step of the handshake. Returns 5 if the handshake is completed
     */
    public int getCurrentStep() {
        return step;
    }

    /**
     * @return true if the handshake is completed
     */
    public boolean isHandshakeComplete() {
        return step == 5;
    }
}
