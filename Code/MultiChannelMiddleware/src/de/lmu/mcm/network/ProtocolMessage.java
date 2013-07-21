package de.lmu.mcm.network;

import de.lmu.mcm.network.Enums.MessageOrigin;

/**
 * 
 * Encapsulates received bytes with the time of arrival and the address of the sender.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class ProtocolMessage {

    private byte[] rawMessageInBytes;
    private MultiNetworkAddress address;
    private long timeOfArrival;
    private MessageOrigin origin;

    private ProtocolMessage(MessageOrigin origin) {
        this.origin = origin;
        this.timeOfArrival = System.currentTimeMillis();
    }

    /**
     * This constructor is used by the interfaces NFC and Barcodes, because we get no address of the sender. On these
     * interfaces we get no information from the transport layer where the message is coming from.
     * */
    public ProtocolMessage(MessageOrigin origin, byte[] rawMessage) {
        this(origin);
        this.rawMessageInBytes = rawMessage;
    }

    /**
     * This constructor is used for the interfaces Bluetooth, Internet and SMS because we always get the address of the
     * sender when we receive a message.
     * */
    public ProtocolMessage(MessageOrigin origin, MultiNetworkAddress address, byte[] rawMessage) {
        this(origin);
        this.rawMessageInBytes = rawMessage;
        this.address = address;
    }

    /**
     * @return the raw message in bytes
     * */
    public byte[] getRawMessageInBytes() {
        return rawMessageInBytes;
    }

    /**
     * @return the address of the sender or null if no sender was specified.
     * */
    public MultiNetworkAddress getAddress() {
        return address;
    }

    /**
     * The time in ms when this message arrived.
     * */
    public long getTimeOfArrival() {
        return timeOfArrival;
    }

    /**
     * @return the origin of the message (self or remote)
     * */
    public MessageOrigin getOrigin() {
        return origin;
    }

}