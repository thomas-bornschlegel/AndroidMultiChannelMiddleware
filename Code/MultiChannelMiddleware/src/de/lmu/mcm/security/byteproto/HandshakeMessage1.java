package de.lmu.mcm.security.byteproto;

import java.nio.ByteBuffer;
import java.util.UUID;

import de.lmu.mcm.helper.ByteConverter;

/**
 * 
 * Encapsulates the first handshake message from A to B that contains "A => pubB(idA, nonceA) => B"
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class HandshakeMessage1 implements BasicMessage {

    private long nonceA;
    private UUID idA;

    public HandshakeMessage1(long nonceA, UUID idA) {
        super();
        this.nonceA = nonceA;
        this.idA = idA;
    }

    public HandshakeMessage1(byte[] rawMessage) {
        ByteBuffer buffer = ByteBuffer.wrap(rawMessage);
        nonceA = buffer.getLong();
        int uuidLength = 16;
        byte[] uuidBytes = new byte[uuidLength];
        buffer.get(uuidBytes, 0, uuidLength);
        idA = ByteConverter.deserializeUUID(uuidBytes);
    }

    @Override
    public byte[] getMessageContentAsBytes() {
        byte[] bytesOfA = ByteConverter.serializeUUID(idA);
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / 8 + bytesOfA.length);
        buffer.putLong(nonceA);
        buffer.put(bytesOfA);
        return buffer.array();
    }

    public long getNonceA() {
        return nonceA;
    }

    public UUID getIdA() {
        return idA;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HandshakeMessage1) {
            HandshakeMessage1 otherMsg = (HandshakeMessage1) o;
            return idA.equals(otherMsg.getIdA()) && nonceA == otherMsg.getNonceA();
        }
        return false;
    }

}
