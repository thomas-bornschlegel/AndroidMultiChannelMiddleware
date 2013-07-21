package de.lmu.mcm.security;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

/**
 * A helper class to encrypt messages with AES (symmetric key).
 * 
 * @author Thomas Bornschlegel
 * */
public class AesHelper {

    private Cipher cipher;
    private final String DEFAULT_CYPHER_ALGO = "AES/CBC/PKCS5Padding";
    private byte[] iv = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    IvParameterSpec ivspec = new IvParameterSpec(iv);

    public AesHelper() throws NoSuchAlgorithmException, NoSuchPaddingException {
        cipher = Cipher.getInstance(DEFAULT_CYPHER_ALGO);
    }

    /**
     * Encrypts the given bytes with AES/CBC/PKCS5Padding
     * 
     * @param bytes
     *            the bytes to encrypt
     * @param symmetricKey
     *            the key to use
     * 
     * @return the encrypted bytes
     * 
     * */
    public byte[] encrypt(byte[] bytes, Key symmetricKey) throws InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException {
        cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, ivspec);
        return cipher.doFinal(bytes);
    }

    /**
     * Decrypts the given bytes with AES/CBC/PKCS5Padding
     * 
     * @param bytes
     *            the bytes to decrypt
     * @param symmetricKey
     *            the key to use
     * 
     * @return the decrypted bytes
     * 
     * */
    public byte[] decrypt(byte[] encrypted, Key symmetricKey) throws IllegalBlockSizeException, BadPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException {
        this.cipher.init(Cipher.DECRYPT_MODE, symmetricKey, ivspec);
        return cipher.doFinal(encrypted);
    }

}
