package org.puppylab.mypassword.core;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.puppylab.mypassword.core.exception.EncryptException;

public class EncryptUtils {

    static final String AES_ALG      = "AES/GCM/NoPadding";
    static final int    AES_KEY_SIZE = 256;
    static final int    AES_TAG_SIZE = 128;
    static final int    AES_IV_SIZE  = 96;

    static final String PBE_ALG        = "PBKDF2WithHmacSHA256";
    static final int    PBE_KEY_SIZE   = 256;
    static final int    PBE_ITERATIONS = 100_000;

    static final SecureRandom srandom = new SecureRandom();

    /**
     * Derive a PBE key.
     */
    public static byte[] derivePbeKey(char[] password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, PBE_KEY_SIZE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_ALG);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new EncryptException(e);
        }
    }

    /**
     * Generate random 12 bytes IV.
     */
    public static byte[] generateIV() {
        return generateSecureRandomBytes(AES_IV_SIZE / 8);
    }

    /**
     * Generate random 32 bytes salt.
     */
    public static byte[] generateSalt() {
        return generateSecureRandomBytes(32);
    }

    /**
     * Generate random 32 bytes key.
     */
    public static byte[] generateKey() {
        return generateSecureRandomBytes(32);
    }

    public static byte[] generateSecureRandomBytes(int size) {
        byte[] buffer = new byte[size];
        srandom.nextBytes(buffer);
        return buffer;
    }

    public static SecretKey bytesToAesKey(byte[] key) {
        return new SecretKeySpec(key, "AES");
    }

    /**
     * Encrypt data by AES-GCM.
     */
    public static byte[] encrypt(byte[] data, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALG);
            GCMParameterSpec spec = new GCMParameterSpec(AES_TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new EncryptException(e);
        }
    }

    public static byte[] decrypt(byte[] cdata, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALG);
            GCMParameterSpec spec = new GCMParameterSpec(AES_TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(cdata);
        } catch (GeneralSecurityException e) {
            throw new EncryptException(e);
        }
    }

}
