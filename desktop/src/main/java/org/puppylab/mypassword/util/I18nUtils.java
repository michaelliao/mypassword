package org.puppylab.mypassword.util;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class I18nUtils {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages");

    public static String i18n(String key) {
        return bundle.getString(key);
    }

    public static String i18n(String key, Object... args) {
        return MessageFormat.format(bundle.getString(key), args);
    }
}
