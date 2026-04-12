package org.puppylab.mypassword.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import org.slf4j.LoggerFactory;

public class I18nUtils {

    // Initial bundle is loaded with the JVM default locale so i18n() works
    // before init() is called (e.g. during very early startup). MainWindow
    // calls init() once VaultManager is available to honour the user's
    // SettingKey.LANGUAGE setting.
    private static volatile ResourceBundle bundle = ResourceBundle.getBundle("messages");

    /**
     * Initialise the i18n bundle for the given language code.
     *
     * @param language IETF language tag from {@code SettingKey.LANGUAGE} —
     *                 {@code ""} or {@code null} for system default, otherwise e.g.
     *                 {@code "en"}, {@code "zh"}.
     */
    public static void init(String language) {
        Locale locale = (language == null || language.isEmpty()) ? Locale.getDefault()
                : Locale.forLanguageTag(language);
        LoggerFactory.getLogger(I18nUtils.class).info("init locale: {}", locale);
        bundle = ResourceBundle.getBundle("messages", locale);
    }

    public static String i18n(String key) {
        boolean isBtn = key.startsWith("btn.") || key.endsWith(".btn") || key.contains(".btn.");
        String value = bundle.getString(key);
        if (isBtn) {
            return " " + value + " ";
        }
        return value;
    }

    public static String i18n(String key, Object... args) {
        return MessageFormat.format(bundle.getString(key), args);
    }
}
