package org.puppylab.mypassword.util;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class I18nUtils {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages");

    public static String i18n(String key) {
        boolean isBtn = key.startsWith("btn.") || key.endsWith(".btn") || key.contains(".btn.");
        String value = bundle.getString(key);
        if (isBtn) {
            return "  " + value + "  ";
        }
        return value;
    }

    public static String i18n(String key, Object... args) {
        return MessageFormat.format(bundle.getString(key), args);
    }
}
