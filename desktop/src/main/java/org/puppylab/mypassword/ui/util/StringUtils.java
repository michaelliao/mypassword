package org.puppylab.mypassword.ui.util;

public class StringUtils {

    public static String normalize(String str) {
        return str == null ? "" : str.strip();
    }
}
