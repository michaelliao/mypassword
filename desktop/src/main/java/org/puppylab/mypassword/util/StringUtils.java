package org.puppylab.mypassword.util;

import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.VaultException;

public class StringUtils {

    public static String normalize(String str) {
        return str == null ? "" : str.strip();
    }

    public static String checkNotEmpty(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new VaultException(ErrorCode.BAD_FIELD, "Invalid " + name);
        }
        return value.strip();
    }

    public static String checkPattern(String name, String pattern, String value) {
        if (value == null || !value.matches(pattern)) {
            throw new VaultException(ErrorCode.BAD_FIELD, "Invalid " + name);
        }
        return value;
    }
}
