package de.lmu.mcm.security.byteproto;

import java.nio.ByteBuffer;
import java.security.Key;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.security.KeyHolder;

/**
 * 
 * Encapsulates the fourth message from A to B that contains "B => pubA(privB(symmetricKey, A, B, timestamp))"
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class HandshakeMessage4 implements BasicMessage {

    private UUID idA;
    private UUID idB;
    private long timestamp;
    private Key symmetricKey;

    public HandshakeMessage4(UUID idA, UUID idB, long timestamp, Key symmetricKey) {
        super();
        this.idA = idA;
        this.idB = idB;
        this.timestamp = timestamp;
        this.symmetricKey = symmetricKey;
    }

    public HandshakeMessage4(byte[] rawMessage) {
        ByteBuffer buffer = ByteBuffer.wrap(rawMessage);

        // Retrieve UUIDs
        int uuidLength = 16;
        byte[] bytesOfUuid = new byte[uuidLength];
        buffer.get(bytesOfUuid, 0, uuidLength);
        idA = ByteConverter.deserializeUUID(bytesOfUuid);
        buffer.get(bytesOfUuid, 0, uuidLength);
        idB = ByteConverter.deserializeUUID(bytesOfUuid);

        // Retrieve timestamp
        timestamp = buffer.getLong();

        // Retrieve key
        int keyLength = KeyHolder.AES_KEY_LENGTH / 8;
        byte[] bytesOfKey = new byte[keyLength];
        buffer.get(bytesOfKey, 0, keyLength);
        symmetricKey = new SecretKeySpec(bytesOfKey, "AES");
    }

    @Override
    public byte[] getMessageContentAsBytes() {
        byte[] bytesOfIdA = ByteConverter.serializeUUID(idA);
        byte[] bytesOfIdB = ByteConverter.serializeUUID(idB);
        byte[] bytesOfKey = symmetricKey.getEncoded();
        ByteBuffer buffer = ByteBuffer.allocate(bytesOfIdA.length + bytesOfIdB.length + Long.SIZE / 8
                + bytesOfKey.length);
        buffer.put(bytesOfIdA);
        buffer.put(bytesOfIdB);
        buffer.putLong(timestamp);
        buffer.put(bytesOfKey);
        return buffer.array();
    }

    public UUID getIdA() {
        return idA;
    }

    public UUID getIdB() {
        return idB;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Key getSymmetricKey() {
        return symmetricKey;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HandshakeMessage4) {
            HandshakeMessage4 otherMsg = (HandshakeMessage4) o;
            boolean basicMatch = idA.equals(otherMsg.getIdA()) && idB.equals(otherMsg.getIdB())
                    && timestamp == otherMsg.getTimestamp();
            byte[] keyRaw = symmetricKey.getEncoded();
            byte[] otherKeyRaw = otherMsg.getSymmetricKey().getEncoded();

            basicMatch = basicMatch && keyRaw.length == otherKeyRaw.length;

            for (int i = 0; basicMatch && i < keyRaw.length; i++) {
                basicMatch = basicMatch && keyRaw[i] == otherKeyRaw[i];
                if (!basicMatch) {
                    break;
                }
            }
            return basicMatch;

        }
        return false;
    }
}
