package de.lmu.mcm.security.byteproto;

/**
 * 
 * This class is a container for custom messages that do not encode handshake messages. It can be used to create
 * messages without encryption, symmetrically encrypted messages or asymmetrically encrypted messages.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class CustomMessage implements BasicMessage {

    private byte[] messageInBytes;

    public CustomMessage(byte[] unencryptedMessageContentInBytes) {
        this.messageInBytes = unencryptedMessageContentInBytes;
    }

    @Override
    public byte[] getMessageContentAsBytes() {
        return messageInBytes;
    }

}
