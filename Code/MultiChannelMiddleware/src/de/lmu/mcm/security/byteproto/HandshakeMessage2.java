package de.lmu.mcm.security.byteproto;

import java.nio.ByteBuffer;

/**
 * 
 * Encapsulates the second handshake message from A to B that contains "B => pubA(nonceA, nonceB) => A"
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class HandshakeMessage2 implements BasicMessage {

    private long nonceA;
    private long nonceB;

    public HandshakeMessage2(long nonceA, long nonceB) {
        super();
        this.nonceA = nonceA;
        this.nonceB = nonceB;
    }

    public HandshakeMessage2(byte[] rawMessage) {
        ByteBuffer buffer = ByteBuffer.wrap(rawMessage);
        nonceA = buffer.getLong();
        nonceB = buffer.getLong();
    }

    @Override
    public byte[] getMessageContentAsBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(2 * Long.SIZE / 8);
        buffer.putLong(nonceA);
        buffer.putLong(nonceB);
        return buffer.array();
    }

    public long getNonceA() {
        return nonceA;
    }

    public long getNonceB() {
        return nonceB;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HandshakeMessage2) {
            HandshakeMessage2 otherMsg = (HandshakeMessage2) o;
            return nonceA == otherMsg.getNonceA() && nonceB == otherMsg.getNonceB();
        }
        return false;
    }

}
