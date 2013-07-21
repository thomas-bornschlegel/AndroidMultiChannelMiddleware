package de.lmu.mcm.test;

import java.nio.ByteBuffer;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import android.bluetooth.BluetoothAdapter;
import android.test.AndroidTestCase;
import android.util.Log;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.helper.PrefsHelper;
import de.lmu.mcm.security.KeyHolder;

public class ByteEncodingTests extends AndroidTestCase {

    private static final String TAG = ByteEncodingTests.class.getSimpleName();

    public void testAesKeySerialization() throws NoSuchAlgorithmException, NoSuchPaddingException {
        // Create new symmetric key
        Key key = KeyHolder.getInstance().generateRandomSymmetricKey();

        // Get the bytes of the key
        byte[] keyInBytes = key.getEncoded();
        Log.d(TAG, "Encoding format of AES key: " + key.getFormat());

        // Check if the key has the correct length
        int expectedKeyLength = KeyHolder.AES_KEY_LENGTH / 8;
        int keyLength = keyInBytes.length;
        assertEquals("The key does not have the expected length of " + expectedKeyLength + " bytes! Key length: "
                + keyLength, expectedKeyLength, keyLength);

        // Generate a new key from the bytes
        Key key2 = new SecretKeySpec(keyInBytes, "AES");

        // Compare both keys
        assertEquals("Keys were not the same!", key, key2);

        Log.d(TAG, "Symmetric key successfully (de)serialized");
    }

    public void testRsaKeySerialization() throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeySpecException {
        // Create asymmetric key
        KeyHolder keyHolder = KeyHolder.getInstance();
        KeyPair keyPair = keyHolder.generateRandomKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        // Get the bytes of the key
        byte[] keyInBytes = publicKey.getEncoded();
        Log.d(TAG, "Encoding format: " + publicKey.getFormat());

        // THE FOLLOWING DOES NOT MATCH! The reason for this is, that the key is not encoded in raw format but in X.509
        // format, which adds some length to the key.

        // Check if the key has the correct length
        // int expectedKeyLength = KeyHolder.RSA_KEY_LENGTH / 8;
        // int keyLength = keyInBytes.length;
        // assertEquals("The key does not have the expected length of " + expectedKeyLength + " bytes! Key length: "
        // + keyLength, expectedKeyLength, keyLength);

        // Generate a new key from the bytes
        PublicKey publicKey2 = keyHolder.generatePublicKeyFromBytes(keyInBytes);

        // Compare both keys
        assertEquals("Keys were not the same!", publicKey, publicKey2);

        Log.d(TAG, "Asymmetric key successfully (de)serialized");
    }

    public void testArrayCopying() {
        byte[] startBytes = { 1, 2, 3, 4 };
        byte[] result = Arrays.copyOf(startBytes, 10);
        // We expect an array of size 10 with the elements 1,2,3,4,0,0,0,0,0,0
        for (int i = 0; i < result.length; i++) {
            if (i < startBytes.length) {
                assertEquals(startBytes[i], result[i]);
            } else {
                assertEquals(0, result[i]);
            }
        }
    }

    public void testByteBuffer() {
        // Add a long and a string to the ByteBuffer
        long l = Long.MAX_VALUE;
        byte[] byteArray = new String("Dies ist ein Test " + System.currentTimeMillis()).getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(byteArray.length + 8);
        buffer.putLong(l);
        buffer.put(byteArray);
        buffer.rewind();

        // Check if the Long can be retrieved
        long l2 = buffer.getLong();
        assertEquals(l, l2);

        // ... and if the String can be retrieved
        int lengthLeft = buffer.remaining();
        byte[] byteArrayRestored = new byte[lengthLeft];
        buffer.get(byteArrayRestored, 0, lengthLeft);
        assertEquals(byteArray.length, byteArrayRestored.length);

        for (int i = 0; i < byteArray.length; i++) {
            assertEquals(byteArray[i], byteArrayRestored[i]);
        }
    }

    public void testByteBufferAppending() {
        SecureRandom random = new SecureRandom();
        byte[] firstArray = new byte[1024];
        random.nextBytes(firstArray);
        int noOfBytesToAdd = 4;
        ByteBuffer buffer = ByteBuffer.allocate(noOfBytesToAdd + firstArray.length);
        int intToAdd = 4343;
        buffer.putInt(intToAdd);
        buffer.put(firstArray);

        buffer.rewind();
        int retrievedInt = buffer.getInt();
        assertEquals(intToAdd, retrievedInt);
        byte[] restoredArray = new byte[1024];
        buffer.get(restoredArray);
        assertTrue(Arrays.equals(firstArray, restoredArray));
    }

    public void testUuidSerialization() {
        // Create a new random UUID:
        UUID uuid = UUID.randomUUID();

        byte[] uuidInBytes = ByteConverter.serializeUUID(uuid);
        UUID uuidRestored = ByteConverter.deserializeUUID(uuidInBytes);
        assertEquals("UUID could not be restored from bytes.", uuid, uuidRestored);
    }

    public void testUuidSerializationWithOwnId() {
        PrefsHelper.generateOwnIdIfNotPresent(getContext());
        String ownIdString = PrefsHelper.getOwnId(getContext());
        UUID ownId = UUID.fromString(ownIdString);

        byte[] uuidInBytes = ByteConverter.serializeUUID(ownId);
        UUID uuidRestored = ByteConverter.deserializeUUID(uuidInBytes);
        assertEquals("UUID could not be restored from bytes.", ownId, uuidRestored);
    }

    public void testBase64() {
        String orig = "original String before base64 encoding in Java";
        byte[] input = orig.getBytes();

        // encoding byte array into base 64
        byte[] encoded = ByteConverter.encodeBase64(input);

        // decoding byte array into base64
        byte[] decoded = ByteConverter.decodeBase64(encoded);

        // Compare
        assertEquals(decoded.length, input.length);
        for (int i = 0; i < input.length; i++) {
            assertEquals(input[i], decoded[i]);
        }
    }

    public void testZeroConfTxtConverter() {
        String key1 = "mykey";
        String value1 = "myvalue";
        String key2 = "mykey2";
        String value2 = "myvalue2";

        // Encode entries
        byte[] encoded = ByteConverter.encodeKeyValuePair(key1, value1);
        byte[] encoded2 = ByteConverter.encodeKeyValuePair(key2, value2);
        // Combine both into a single array
        byte[] combined = ByteConverter.combineMultipleByteArrays(encoded, encoded2);
        // Decode entries again
        List<String> decodedEntries = ByteConverter.decodeTextEntries(combined);

        // Check for correctness
        String parsedValue1 = ByteConverter.getValueForKey(key1, decodedEntries);
        String parsedValue2 = ByteConverter.getValueForKey(key2, decodedEntries);
        assertEquals(value1, parsedValue1);
        assertEquals(value2, parsedValue2);
    }

    public void testBluetoothDeviceAddressStoring() {
        // // Generate random address
        // SecureRandom random = new SecureRandom();
        // byte[] originalAddress = new byte[6];
        // random.nextBytes(originalAddress);
        // // Transfer into

        // Receive own address
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        byte[] bluetoothAddress = null;
        if (adapter != null) {
            // Retrieve own ID
            String bluetoothAddressString = adapter.getAddress();
            if (bluetoothAddressString == null) {
                System.out.println("Please enable the bluetooth adapter for this test.");
            } else {
                // Convert it to hex...
                bluetoothAddressString = bluetoothAddressString.replace(":", "");
                // .. and to bytes
                bluetoothAddress = ByteConverter.hexStringToByteArray(bluetoothAddressString);

                // Now the bytes would be sent over the network.
                // And parsed by the other side again.
                // We do not have to do this here and therefore just "simulate" it:
                byte[] retrievedBytes = bluetoothAddress;

                // Store address
                UUID userId = UUID.randomUUID();
                PrefsHelper.storeBluetoothAddressForUser(getContext(), retrievedBytes, userId);
                // And retrieve it again
                byte[] bytesFromStorage = PrefsHelper.getBluetoothAddressForUser(getContext(), userId.toString());
                assertTrue(Arrays.equals(bluetoothAddress, bytesFromStorage));
            }

        } else {
            System.out.println("Please enable the bluetooth adapter for this test.");
        }

    }

    public void testMobilePhoneNumber() {

        String[] rawNumbers = { "004901234567", "+4912345667" };
        String[] expectedNormalizedNumbers = { "00491234567", "004912345667" };
        for (int i = 0; i < rawNumbers.length; i++) {
            String normalized = PrefsHelper.normalizeMobilePhoneNumer(rawNumbers[i]);
            assertEquals(expectedNormalizedNumbers[i], normalized);
        }

    }
}
