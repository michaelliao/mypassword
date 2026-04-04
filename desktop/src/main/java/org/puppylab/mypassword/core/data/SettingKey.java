package org.puppylab.mypassword.core.data;

public interface SettingKey {

    /**
     * Auto lock when device is idle in minutes.
     * 
     * Example: 30 = 30 minutes, 0 = never.
     */
    String AUTO_LOCK = "auto_lock";

    /**
     * Clear password from clipboard in seconds.
     * 
     * Example: 60 = 60 seconds, 0 = never.
     */
    String CLEAR_CLIPBOARD = "clear_clipboard";

    /**
     * Keep tray icon rather than exit when close window.
     * 
     * Value: 0/1, default to 1.
     */
    String KEEP_TRAY_ICON = "keep_tray_icon";

    /**
     * UI language. "" = system default.
     */
    String LANGUAGE = "language";
}
