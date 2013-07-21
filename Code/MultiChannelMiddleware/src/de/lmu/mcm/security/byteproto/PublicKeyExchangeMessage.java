package de.lmu.mcm.security.byteproto;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.UUID;

import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.security.KeyHolder;

/**
 * 
 * Encapsulates the message to exchange the public key, the uuid and (optionally) the telephone number and the bluetooth
 * address.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class PublicKeyExchangeMessage implements BasicMessage {

    private UUID uuidOfKeyOwner;
    private long telephoneNumber = 0;
    private PublicKey asymmetricKey;
    private byte[] bluetoothDeviceAddress;

    private byte[] emptyAddress = new byte[6];

    /**
     * @param uuidOfKeyOwner
     *            the uuid of the key owner
     * @param telephoneNumber
     *            for example the number "+4903829194852" will be encode as "4903829194852". If no telephone number is
     *            available pass "0" instead.
     * @param bluetoothDeviceAddress
     *            The address of a bluetooth device. This address has to be 6 bytes long and in network byte order (MSB
     *            first). Pass null if no address is available
     * @param asymmetricKey
     *            a 256 bits AES key
     * */
    public PublicKeyExchangeMessage(UUID uuidOfKeyOwner, long telephoneNumber, byte[] bluetoothDeviceAddress,
            PublicKey asymmetricKey) {
        super();
        this.uuidOfKeyOwner = uuidOfKeyOwner;
        this.telephoneNumber = telephoneNumber;
        if (bluetoothDeviceAddress == null) {
            this.bluetoothDeviceAddress = emptyAddress;
        } else {
            this.bluetoothDeviceAddress = bluetoothDeviceAddress;
        }
        this.asymmetricKey = asymmetricKey;
    }

    public PublicKeyExchangeMessage(byte[] rawMessage) throws InvalidKeySpecException, NoSuchAlgorithmException {
        ByteBuffer buffer = ByteBuffer.wrap(rawMessage);

        // Retrieve the UUID
        int uuidLength = 16;
        byte[] bytesOfUuid = new byte[uuidLength];
        buffer.get(bytesOfUuid, 0, uuidLength);
        uuidOfKeyOwner = ByteConverter.deserializeUUID(bytesOfUuid);

        // Retrieve the telephone number (is 0 if none is given)
        telephoneNumber = buffer.getLong();

        // Retrieve the bluetooth device address (just zeros if none is given)
        bluetoothDeviceAddress = new byte[6];
        buffer.get(bluetoothDeviceAddress, 0, 6);
        // Check if the device was null
        if (bluetoothDeviceAddress == null || bluetoothDeviceAddress.length != 6) {
            bluetoothDeviceAddress = null;
        } else {
            boolean foundDifferentByte = false;
            for (int i = 0; i < emptyAddress.length; i++) {
                if (emptyAddress[i] != bluetoothDeviceAddress[i]) {
                    foundDifferentByte = true;
                    break;
                }
            }
            if (!foundDifferentByte) {
                bluetoothDeviceAddress = emptyAddress;
            }
        }

        // Retrieve key
        // TODO key is larger than 128 Bytes because we use X.509 Encoding. Is it possible to send the raw key instead?
        // int lengthKey = 162;
        int lengthLeft = buffer.remaining();
        int lengthKey = lengthLeft;
        byte[] keyInBytes = new byte[lengthKey];
        buffer.get(keyInBytes, 0, lengthKey);
        asymmetricKey = KeyHolder.getInstance().generatePublicKeyFromBytes(keyInBytes);
    }

    @Override
    public byte[] getMessageContentAsBytes() {
        byte[] bytesOfUuidOfKeyOwner = ByteConverter.serializeUUID(uuidOfKeyOwner);
        byte[] bytesOfKey = asymmetricKey.getEncoded();
        ByteBuffer buffer = ByteBuffer.allocate(bytesOfUuidOfKeyOwner.length + Long.SIZE / 8 + 6 + bytesOfKey.length);
        buffer.put(bytesOfUuidOfKeyOwner);
        buffer.putLong(telephoneNumber);
        buffer.put(bluetoothDeviceAddress);
        buffer.put(bytesOfKey);
        return buffer.array();
    }

    public UUID getUuidOfKeyOwner() {
        return uuidOfKeyOwner;
    }

    /**
     * @return the telephone number of null if no number is present. For example the number "+4903829194852" will be
     *         encoded as "4903829194852".
     * */
    public long getTelephoneNumber() {
        return telephoneNumber;
    }

    /**
     * @return The address of a bluetooth device, or null if no address is given. If an address is given it is in
     *         network byte order (MSB first) and 6 bytes long.
     * */
    public byte[] getBluetoothDeviceAddress() {
        if (Arrays.equals(emptyAddress, bluetoothDeviceAddress)) {
            return null;
        }
        return bluetoothDeviceAddress;
    }

    public PublicKey getAsymmetricKey() {
        return asymmetricKey;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PublicKeyExchangeMessage) {
            PublicKeyExchangeMessage otherMsg = (PublicKeyExchangeMessage) o;
            boolean basicMatch = uuidOfKeyOwner.equals(otherMsg.getUuidOfKeyOwner())
                    && telephoneNumber == otherMsg.getTelephoneNumber();

            if (asymmetricKey == null && otherMsg.getAsymmetricKey() != null || asymmetricKey != null
                    && otherMsg.getAsymmetricKey() == null) {
                return false;
            }

            if (asymmetricKey != null && otherMsg.getAsymmetricKey() != null) {
                byte[] keyRaw = asymmetricKey.getEncoded();
                byte[] otherKeyRaw = otherMsg.getAsymmetricKey().getEncoded();

                basicMatch = basicMatch && Arrays.equals(keyRaw, otherKeyRaw);
            }

            basicMatch = basicMatch && Arrays.equals(bluetoothDeviceAddress, otherMsg.getBluetoothDeviceAddress());

            return basicMatch;

        }
        return false;
    }

}
