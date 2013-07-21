package de.lmu.mcm.security;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import de.lmu.mcm.helper.ByteConverter;

/**
 * A helper class to encrypt messages with RSA (public/private key).
 * 
 * @author Thomas Bornschlegel
 * */
public class RsaHelper {

    private Cipher cipher;
    public static final String DEFAULT_CYPHER_ALGO = "RSA/ECB/PKCS1Padding";

    // ECB/NoPadding";//

    public RsaHelper() throws NoSuchAlgorithmException, NoSuchPaddingException {
        cipher = Cipher.getInstance(DEFAULT_CYPHER_ALGO);
    }

    /**
     * Encrypts the given bytes with RSA/ECB/PKCS1Padding
     * 
     * @param bytes
     *            the bytes to encrypt
     * @param key
     *            the key to use
     * 
     * @return the encrypted bytes
     * 
     * */
    public byte[] encrypt(byte[] bytes, Key key) throws InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException {
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return blockCipher(bytes, Cipher.ENCRYPT_MODE);
    }

    /**
     * Decrypts the given bytes with RSA/ECB/PKCS1Padding
     * 
     * @param bytes
     *            the bytes to encrypt
     * @param key
     *            the key to use
     * 
     * @return the decrypted bytes
     * 
     * */
    public byte[] decrypt(byte[] encrypted, Key key) throws IllegalBlockSizeException, BadPaddingException,
            InvalidKeyException {
        this.cipher.init(Cipher.DECRYPT_MODE, key);

        return blockCipher(encrypted, Cipher.DECRYPT_MODE);
    }

    /**
     * Encodes/decodes an arbitrary length of bytes. The Bouncy Castle implementation of AES which is used in Android
     * only supports the encryption/decryption of at most 127 bytes (see question 5 at
     * http://www.bouncycastle.org/wiki/display/JA1/Frequently+Asked+Questions).
     * 
     * @param bytes
     *            the bytes to encrypt/decrypt
     * @param mode
     *            either Cipher.ENCRYPT_MODE or Cipher.DECRYPT_MODE
     * 
     * @return the encrypted/decrypted bytes
     * 
     * */
    private byte[] blockCipher(byte[] bytes, int mode) throws IllegalBlockSizeException, BadPaddingException {
        // The source of this method is taken from http://coding.westreicher.org/?p=23

        // string initialize 2 buffers.
        // scrambled will hold intermediate results
        byte[] scrambled = new byte[0];

        // toReturn will hold the total result
        byte[] toReturn = new byte[0];

        // TODO This value has to be adjusted if we use smaller key sizes than 1024.
        // For background info read this article:
        // http://www.obviex.com/articles/ciphertextsize.aspx
        // But as we only use 1024 Bit keys this should be fine:

        // if we encrypt we use 117 byte long blocks. Decryption requires 128 byte long blocks (because of RSA)
        int length = (mode == Cipher.ENCRYPT_MODE) ? 117 : 128;

        // another buffer. this one will hold the bytes that have to be modified in this step
        byte[] buffer = new byte[(bytes.length > length ? length : bytes.length)];
        // byte[] buffer = new byte[length];

        for (int i = 0; i < bytes.length; i++) {

            // if we filled our buffer array we have our block ready for de- or encryption
            if ((i > 0) && (i % length == 0)) {
                // execute the operation
                scrambled = cipher.doFinal(buffer);
                // add the result to our total result.
                toReturn = ByteConverter.combineMultipleByteArrays(toReturn, scrambled);
                // here we calculate the length of the next buffer required
                int newlength = length;

                // if newlength would be longer than remaining bytes in the bytes array we shorten it.
                if (i + length > bytes.length) {
                    newlength = bytes.length - i;
                }
                // clean the buffer array
                // buffer = new byte[(bytes.length > length ? length : bytes.length)];
                buffer = new byte[newlength];
            }
            // copy byte into our buffer.
            buffer[i % length] = bytes[i];
        }

        // this step is needed if we had a trailing buffer. should only happen when encrypting.
        // example: we encrypt 110 bytes. 100 bytes per run means we "forgot" the last 10 bytes. they are in the buffer
        // array
        scrambled = cipher.doFinal(buffer);

        // final step before we can return the modified data.
        toReturn = ByteConverter.combineMultipleByteArrays(toReturn, scrambled);

        return toReturn;
    }

}
