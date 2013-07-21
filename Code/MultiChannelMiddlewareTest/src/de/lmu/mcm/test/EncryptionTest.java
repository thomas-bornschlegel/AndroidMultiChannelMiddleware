package de.lmu.mcm.test;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.json.JSONException;

import android.test.AndroidTestCase;
import de.lmu.mcm.helper.ByteConverter;
import de.lmu.mcm.security.AesHelper;
import de.lmu.mcm.security.KeyHolder;
import de.lmu.mcm.security.RsaHelper;

public class EncryptionTest extends AndroidTestCase {

    public void testRsa() throws NoSuchAlgorithmException, JSONException, InterruptedException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException {

        KeyHolder keyHolder = KeyHolder.getInstance();
        KeyPair keysA = keyHolder.generateRandomKeyPair();

        int length = 500;
        String stringToEncrypt = getRandomString(length);
        // Just for the record:
        // The following commented check would fail.
        // The values are not the same as characters are encoded in Unicode and not ASCII!
        // assertEquals(length, msg.getBytes().length);
        System.out.println("To encrypt: " + stringToEncrypt);
        RsaHelper rsa = new RsaHelper();

        byte[] toEncrypt = stringToEncrypt.getBytes();
        byte[] encrypted = rsa.encrypt(toEncrypt, keysA.getPrivate());
        byte[] decrypted = rsa.decrypt(encrypted, keysA.getPublic());
        assertTrue(Arrays.equals(toEncrypt, decrypted));

        String stringAfterDecryption = new String(decrypted);
        System.out.println("After Decryption: " + stringAfterDecryption);
        assertEquals(stringToEncrypt, stringAfterDecryption);

    }

    public void testAes() throws Exception {
        String plainText = getRandomString(100);
        SecretKey key = KeyHolder.getInstance().generateRandomSymmetricKey();
        AesHelper aes = new AesHelper();
        System.out.println("Encoding...");
        byte[] encoded = aes.encrypt(plainText.getBytes(), key);
        System.out.println("encoded " + new String(encoded));
        byte[] encoded64 = ByteConverter.encodeBase64(encoded);

        // To check if the transformation from bytes to string generates any problems:
        String bytesToString = new String(encoded64, "UTF-8");
        byte[] encoded64B = bytesToString.getBytes("UTF-8");
        assertEquals(encoded64.length, encoded64B.length);
        for (int i = 0; i < encoded64.length; i++) {
            assertEquals(encoded64[i], encoded64B[i]);
        }

        byte[] encoded64Undo = ByteConverter.decodeBase64(encoded64);
        byte[] decrypted = aes.decrypt(encoded64Undo, key);
        String decryptedString = new String(decrypted);
        System.out.println("decrypted " + decryptedString);
        assertEquals(decryptedString, plainText);
    }

    public String getRandomString(int length) {

        char[] alphabet = { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r',
                's', 't', 'u', 'v', 'w', 'x', 'y', 'z',

                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',

                '!', '+', '-', '*', '?', '%', '&', '$', '§', '=',

                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
                'U', 'V', 'W', 'X', 'Y', 'Z' };

        int aLength = alphabet.length - 1;

        char[] data = new char[length];
        for (int i = 0; i < length; i++) {
            data[i] = alphabet[(int) (Math.round((Math.random() * aLength)))];

        }
        String str = new String(data);
        return str;
    }
}