package org.puppylab.mypassword.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

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

    private static DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault());

    /**
     * Set locale when VaultManager initialized and language setting is read.
     */
    public static void initDateTimeLocale(String locale) {
        if (locale != null && !locale.isEmpty()) {
            DATE_TIME_FMT = DATE_TIME_FMT.withLocale(Locale.of(locale));
        }
    }

    public static String formatDateTime(long ts) {
        return DATE_TIME_FMT.format(Instant.ofEpochMilli(ts));
    }
}
