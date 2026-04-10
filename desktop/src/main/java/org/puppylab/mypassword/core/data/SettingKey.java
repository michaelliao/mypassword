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
     * Delete items in trash after N days.
     * 
     * Example: 90 = 90 days, 0 = never.
     */
    String DELETE_AFTER = "delete_after";

    /**
     * Hot key to activate app.
     * 
     * Value: "alt+p", or "" = no hot key.
     */
    String HOT_KEY_ACTIVATE = "hot_key_activate";

    String HOT_KEY_LOCK = "hot_key_lock";

    /**
     * UI language. "" = system default.
     */
    String LANGUAGE = "language";

}
