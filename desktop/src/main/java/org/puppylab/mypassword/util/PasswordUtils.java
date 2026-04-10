package org.puppylab.mypassword.util;

import java.security.SecureRandom;

public class PasswordUtils {

    public static final int STYLE_ALPHABET_NUMBER        = 0;
    public static final int STYLE_ALPHABET               = 1;
    public static final int STYLE_NUMBER                 = 2;
    public static final int STYLE_ALPHABET_NUMBER_SYMBOL = 3;

    static final String ALPHABET               = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String NUMBER                 = "0123456789";
    static final String SYMBOL                 = "?!@#$%&*()-_+=[]{}<>:;,.";
    static final String ALPHABET_NUMBER        = ALPHABET + NUMBER;
    static final String ALPHABET_NUMBER_SYMBOL = (ALPHABET + NUMBER).repeat(4) + SYMBOL;

    private static SecureRandom sr = new SecureRandom();

    public static String generatePassword(int len, int style) {
        if (len < 4 || len > 100) {
            throw new IllegalArgumentException("Invalid length.");
        }
        if (style < STYLE_ALPHABET_NUMBER || style > STYLE_ALPHABET_NUMBER_SYMBOL) {
            throw new IllegalArgumentException("Invalid style.");
        }
        return switch (style) {
        case STYLE_ALPHABET -> generate(ALPHABET, len);
        case STYLE_NUMBER -> generate(NUMBER, len);
        case STYLE_ALPHABET_NUMBER -> generate(ALPHABET_NUMBER, len, true, true, false);
        case STYLE_ALPHABET_NUMBER_SYMBOL -> generate(ALPHABET_NUMBER_SYMBOL, len, true, true, true);
        default -> throw new IllegalArgumentException("Invalid style.");
        };
    }

    private static String generate(String pool, int len) {
        char[] buffer = new char[len];
        generate(buffer, pool, false, false, false);
        return new String(buffer);
    }

    private static String generate(String pool, int len, boolean containsAlphabet, boolean containsNumber,
            boolean containsSymbol) {
        char[] buffer = new char[len];
        while (!generate(buffer, pool, containsAlphabet, containsNumber, containsSymbol))
            ;
        return new String(buffer);
    }

    private static boolean generate(char[] buffer, String pool, boolean containsAlphabet, boolean containsNumber,
            boolean containsSymbol) {
        boolean hasAlphabet = false;
        boolean hasNumber = false;
        boolean hasSymbol = false;
        int len = buffer.length;
        int range = pool.length();
        for (int i = 0; i < len; i++) {
            char ch = pool.charAt(sr.nextInt(range));
            buffer[i] = ch;
            if (ALPHABET.indexOf(ch) >= 0) {
                hasAlphabet = true;
            }
            if (NUMBER.indexOf(ch) >= 0) {
                hasNumber = true;
            }
            if (SYMBOL.indexOf(ch) >= 0) {
                hasSymbol = true;
            }
        }
        if (containsAlphabet && !hasAlphabet) {
            return false;
        }
        if (containsNumber && !hasNumber) {
            return false;
        }
        if (containsSymbol && !hasSymbol) {
            return false;
        }
        return true;
    }

}
