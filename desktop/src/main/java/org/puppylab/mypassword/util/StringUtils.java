package org.puppylab.mypassword.util;

public class StringUtils {

    public static String normalize(String str) {
        return str == null ? "" : str.strip();
    }
}
