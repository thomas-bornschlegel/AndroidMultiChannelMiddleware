package de.lmu.mcm.security.byteproto;

/**
 * @author Thomas Bornschlegel
 * 
 */
public interface BasicMessage {

    /**
     * @return the content of the message (without message type and length indicator)
     * */
    public abstract byte[] getMessageContentAsBytes();

}
