package de.lmu.mcm.helper;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.util.Base64;

/**
 * Provides methods to modify bytes, to encode/decode bytes and to (de)serialize objects.
 * 
 * @author Thomas Bornschlegel
 * */
public class ByteConverter {

    private static String TAG = "ByteConverter";

    /**
     * Combines multiple byte arrays into a single byte array.
     * */
    public static byte[] combineMultipleByteArrays(byte[]... multipleArrays) {
        int finalLength = 0;
        for (byte[] array : multipleArrays) {
            finalLength += array.length;
        }
        byte[] result = new byte[finalLength];
        int currentPosition = 0;
        for (byte[] array : multipleArrays) {
            System.arraycopy(array, 0, result, currentPosition, array.length);
            currentPosition += array.length;
        }
        return result;
    }

    /**
     * Used to encode key value pairs for DNS-SD TXT Records.
     * 
     * @param key
     *            the key in ASCII encoding
     * @param value
     *            the value in ASCII encoding
     * */
    public static byte[] encodeKeyValuePair(String key, String value) {
        String text = key + "=" + value;
        return encodeTextEntry(text);
    }

    /**
     * Uses ASCII input to be converted to bytes. This method converts a string to bytes, in the format that is
     * described in Chapter 4.6.2 of the book "Zero Configuration Networking: The Definitive Guide".
     * */
    private static byte[] encodeTextEntry(String text) {
        // Add 1 characters for the byte describing the length
        byte[] result = new byte[text.length() + 1];
        // Cache the text
        byte[] keyAsBytes = text.getBytes();
        // Write the length indicator
        Integer lengthIndicator = keyAsBytes.length;
        result[0] = lengthIndicator.byteValue();
        // Append the text to the length indicator
        System.arraycopy(keyAsBytes, 0, result, 1, keyAsBytes.length);

        return result;
    }

    /**
     * Parses a byte array which contains entries of the form <NumberOfCharacters, Text> for example:
     * "10FirstEntry11SecondEntry". This method is used to decode service data from DNS-SD TXT Records.
     * 
     * @return a list of text elements, for example {"FirstEntry", "SecondEntry"}.
     * */
    public static List<String> decodeTextEntries(byte[] inputBytes) {
        List<String> result = new ArrayList<String>();

        try {
            int currentPosition = 0;
            int bytesToRead = inputBytes[currentPosition];
            while (bytesToRead != 0 && currentPosition + bytesToRead < inputBytes.length) {
                byte[] subArray = new byte[bytesToRead];
                // Text is after the indicator for bytes to read, so advance position:
                currentPosition++;
                System.arraycopy(inputBytes, currentPosition, subArray, 0, bytesToRead);
                String text = new String(subArray);
                result.add(text);
                LogHelper.getInstance().d(TAG, "Decoded text: " + text);
                // The next indicator of bytes to read is directly after the currently read byte:
                currentPosition += bytesToRead;
                if (currentPosition < inputBytes.length) {
                    bytesToRead = inputBytes[currentPosition];
                }
            }
        } catch (Exception e) {
            LogHelper.getInstance().e(TAG,
                    "Error while trying to decode byte array. Number of parsed entries so far: " + result.size(), e);
        }
        return result;
    }

    /**
     * This method is used to decode service data from DNS-SD TXT Records.
     * 
     * @return the value for the given key, or null if it could not be found
     * */
    public static String getValueForKey(String key, List<String> keyValuePairs) {
        for (String keyAndValue : keyValuePairs) {
            String[] keyValue = getKeyAndValue(keyAndValue);
            if (keyValue != null && keyValue[0].equals(key)) {
                return keyValue[1];
            }
        }
        return null;
    }

    /**
     * This method is used to decode service data from DNS-SD TXT Records.
     * 
     * @param keyValuePair
     *            of the form "mykey=myvalue"
     * @return a String array that contains the key as the first and the value as the second entry.
     * */
    private static String[] getKeyAndValue(String keyValuePair) {
        if (keyValuePair == null || keyValuePair.length() == 0) {
            return null;
        }
        // The following only accepts well formed keyValuePairs. It would return a wrong result for the following
        // special case: If the input is "key=value=2", then the method returns a String array of the form {"key",
        // "value", "2"}. This way "value" would be considered as the value even though "value=2" would be correct. But
        // as we do only expect well formed Strings this special case does not matter here. To fix it we would have to
        // regard everything past the first "=" as the value.
        String[] splitted = keyValuePair.split("=");
        if (splitted.length > 1) {
            return splitted;
        } else {
            // Empty values are allowed, so we fill in an empty string in the place of the value
            return new String[] { splitted[0], "" };
        }
    }

    /**
     * Encodes the given bytes in base 64
     * */
    public static byte[] encodeBase64(byte[] bytes) {
        return Base64.encode(bytes, Base64.URL_SAFE);
    }

    /**
     * Decodes the given bytes from base 64.
     * */
    public static byte[] decodeBase64(byte[] bytes) {
        return Base64.decode(bytes, Base64.URL_SAFE);
    }

    /**
     * Encodes the given bytes in base 64 and returns it as a String.
     * */
    public static String encodeAsBase64String(byte[] bytesInUtf8) {
        // To support other charsets as UTF-8 it could help using new String(bytes, charset). In Android UTF-8 is used
        // per default, so we do not specify the charset here:
        return new String(Base64.encode(bytesInUtf8, Base64.URL_SAFE));
    }

    /**
     * 
     * Decodes the given base64-String and returns it as a raw byte array.
     * 
     * @param base64String
     *            a String representation of a base64-byte-array
     * @return a byte array of UTF8-bytes
     * */
    public static byte[] decodeBase64String(String base64String) {
        // To support charsets as UTF-8 it could help using String.getBytes(charset). In Android UTF-8 is used per
        // default, so we do not specify the charset here:
        return Base64.decode(base64String.getBytes(), Base64.URL_SAFE);
    }

    /**
     * Method to deserialize a UUID from bytes.
     * 
     * @param uuidAsBytes
     *            a byte array of 16 bytes. The first 8 bytes contain a long encoding the most significant bits. The
     *            last 8 bytes contain a long encoding the least significant bits
     * 
     * @return uuid a UUID
     * */
    public static UUID deserializeUUID(byte[] uuidAsBytes) {
        // Restore the longs from the byte array:
        ByteBuffer bufferRestored = ByteBuffer.wrap(uuidAsBytes);
        long most = bufferRestored.getLong();
        long least = bufferRestored.getLong();
        // Restore a new UUID from the bits from the byte buffer:
        return new UUID(most, least);
    }

    /**
     * Method to serialize a UUID to bytes.
     * 
     * @param uuid
     *            a UUID
     * 
     * @return a byte array of 16 bytes. The first 8 bytes contain a long encoding the most significant bits. The last 8
     *         bytes contain a long encoding the least significant bits
     * 
     * */
    public static byte[] serializeUUID(UUID uuid) {
        // Extract the least and the most significant bits:
        Long least = uuid.getLeastSignificantBits();
        Long most = uuid.getMostSignificantBits();
        // Store the bits in a byte array:
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(most);
        buffer.putLong(least);
        return buffer.array();
    }

    /**
     * Converts a string of the form "00A0BF" into a byte array with the corresponding values.
     * 
     * @param s
     *            a string that consists of hex characters e.g. "00A0BF"
     * 
     * @return a byte array that contains the bytes of the given hex string
     * 
     * */
    public static byte[] hexStringToByteArray(String s) {
        // the code of this method is taken from http://stackoverflow.com/a/140861
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Method to help debugging which prints a byte array via System.out.println(). An example output for a byte array
     * of size four and an id of "MyCustomId" would look like this: "Bytes MyCustomId (4) [43,23,2,0,]"
     * 
     * @param id
     *            an identifier that is printed before the actual bytes
     * @param bytes
     *            the byte array that should be printed
     * 
     * */
    public static void printBytes(String id, byte[] bytes) {
        if (bytes == null) {
            System.out.println("Bytes " + id + " were null!");
        }
        String msg = "Bytes " + id + " (" + bytes.length + ") [";
        for (int i = 0; i < bytes.length; i++) {
            msg += bytes[i] + ",";
        }
        System.out.println(msg + "]");
    }

}