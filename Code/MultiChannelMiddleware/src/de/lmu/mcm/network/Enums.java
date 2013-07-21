package de.lmu.mcm.network;

/**
 * Contains enums used in this project.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class Enums {

    public static enum InterfaceIdentifier {
        NFC, BLUETOOTH, BARCODES, WIFI, MOBILE_INTERNET, SMS, ARBITRARY;
    }

    public static enum Role {
        CLIENT, SERVER
    }

    public static enum CommunicationModes {
        READ, WRITE, READ_AND_WRITE, NOT_AVAILABLE
    }

    public static enum MessageOrigin {
        SELF, REMOTE
    }

    public static enum HandshakeRole {
        A, B
    }

    public static enum HandshakeNextAction {
        SEND, RECEIVE
    }

}
