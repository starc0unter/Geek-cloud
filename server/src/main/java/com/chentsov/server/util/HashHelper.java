package com.chentsov.server.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * @author Evgenii Chentsov
 */
public class HashHelper {

    /**
     * Generates salt for the hashing algorithm
     *
     * @return a String representative of the salt
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] byteSalt = new byte[16];
        random.nextBytes(byteSalt);
        return new String(byteSalt, StandardCharsets.UTF_8);
    }

    /**
     * Converts String password to hash that is stored in the DB
     *
     * @param password a plain text that represents password
     * @param salt     a salt to be mixed to the hash
     * @return a text that represent password hash
     */
    public static String getPasswordHash(String password, String salt) {
        try {
            byte[] byteSalt = salt.getBytes(StandardCharsets.UTF_8);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), byteSalt, 65536, 128);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return convertBytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            throw new RuntimeException("Mentioned algorithm was not found or the key spec is invalid");
        }
    }

    /**
     * Represents byte[] hash as a String hex value
     *
     * @param hash a byte array that represents hash
     * @return a String hex value
     */
    private static String convertBytesToHex(byte[] hash) {
        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(String.format("%02x", b));
        }
        System.out.println(result.toString());
        return result.toString();
    }

}
