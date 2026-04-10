package org.puppylab.mypassword.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PasswordUtilsTest {

    @Test
    void testGeneratePassword0() {
        for (int n = 0; n < 10; n++) {
            String pwd = PasswordUtils.generatePassword(20, PasswordUtils.STYLE_ALPHABET_NUMBER);
            System.out.println(pwd);
            boolean hasAlpha = false;
            boolean hasNumber = false;
            for (int i = 0; i < pwd.length(); i++) {
                char c = pwd.charAt(i);
                boolean isAlpha = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
                boolean isNumber = c >= '0' && c <= '9';
                assertTrue(isAlpha || isNumber);
                hasAlpha = hasAlpha || isAlpha;
                hasNumber = hasNumber || isNumber;
            }
            assertTrue(hasAlpha);
            assertTrue(hasNumber);
        }
    }

    @Test
    void testGeneratePassword1() {
        for (int n = 0; n < 10; n++) {
            String pwd = PasswordUtils.generatePassword(10, PasswordUtils.STYLE_ALPHABET);
            System.out.println(pwd);
            for (int i = 0; i < pwd.length(); i++) {
                char c = pwd.charAt(i);
                assertTrue(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
            }
        }
    }

    @Test
    void testGeneratePassword2() {
        for (int n = 0; n < 10; n++) {
            String pwd = PasswordUtils.generatePassword(15, PasswordUtils.STYLE_NUMBER);
            System.out.println(pwd);
            for (int i = 0; i < pwd.length(); i++) {
                char c = pwd.charAt(i);
                assertTrue(c >= '0' && c <= '9');
            }
        }
    }

    @Test
    void testGeneratePassword3() {
        for (int n = 0; n < 10; n++) {
            String pwd = PasswordUtils.generatePassword(20, PasswordUtils.STYLE_ALPHABET_NUMBER_SYMBOL);
            System.out.println(pwd);
            boolean hasAlpha = false;
            boolean hasNumber = false;
            boolean hasSymbol = false;
            for (int i = 0; i < pwd.length(); i++) {
                char c = pwd.charAt(i);
                boolean isAlpha = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
                boolean isNumber = c >= '0' && c <= '9';
                boolean isSymbol = PasswordUtils.SYMBOL.indexOf(c) >= 0;
                assertTrue(isAlpha || isNumber || isSymbol);
                hasAlpha = hasAlpha || isAlpha;
                hasNumber = hasNumber || isNumber;
                hasSymbol = hasSymbol || isSymbol;
            }
            assertTrue(hasAlpha);
            assertTrue(hasNumber);
            assertTrue(hasSymbol);
        }
    }

    @Test
    void testGeneratePassword4() {
        for (int n = 0; n < 10; n++) {
            String pwd = PasswordUtils.generatePassword(4, PasswordUtils.STYLE_ALPHABET_NUMBER_SYMBOL);
            System.out.println(pwd);
        }
    }
}
