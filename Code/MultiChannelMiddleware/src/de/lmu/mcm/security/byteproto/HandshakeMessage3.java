package de.lmu.mcm.security.byteproto;

import java.nio.ByteBuffer;

/**
 * 
 * Encapsulates the third handshake message from B to A that contains "A => pubB(nonceB) => B"
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class HandshakeMessage3 implements BasicMessage {

    private long nonceB;

    public HandshakeMessage3(long nonceB) {
        super();
        this.nonceB = nonceB;
    }

    public HandshakeMessage3(byte[] rawMessage) {
        ByteBuffer buffer = ByteBuffer.wrap(rawMessage);
        nonceB = buffer.getLong();
    }

    @Override
    public byte[] getMessageContentAsBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / 8);
        buffer.putLong(nonceB);
        return buffer.array();
    }

    public long getNonceB() {
        return nonceB;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HandshakeMessage3) {
            HandshakeMessage3 otherMsg = (HandshakeMessage3) o;
            return nonceB == otherMsg.getNonceB();
        }
        return false;
    }

}
