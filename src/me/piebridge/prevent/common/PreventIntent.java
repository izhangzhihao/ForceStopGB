package me.piebridge.prevent.common;

import android.net.Uri;

import me.piebridge.forcestopgb.Manifest;

/**
 * Created by thom on 15/7/12.
 */
public final class PreventIntent {

    public static final String NAMESPACE = "me.piebridge.prevent.";

    // for ui - manager
    public static final String ACTION_GET_PACKAGES = NAMESPACE + "GET_PACKAGES";
    public static final String ACTION_GET_PROCESSES = NAMESPACE + "GET_PROCESSES";
    public static final String ACTION_GET_INFO = NAMESPACE + "GET_INFO";
    public static final String ACTION_UPDATE_PREVENT = NAMESPACE + "UPDATE_PREVENT";
    public static final String ACTION_SYSTEM_LOG = NAMESPACE + "SYSTEM_LOG";
    public static final String ACTION_CHECK_LICENSE = NAMESPACE + "CHECK_LICENSE";
    public static final String ACTION_UPDATE_CONFIGURATION = NAMESPACE + "UPDATE_CONFIGURATION";
    public static final String ACTION_SOFT_REBOOT = NAMESPACE + "SOFT_REBOOT";
    public static final String ACTION_REBOOT = NAMESPACE + "REBOOT";
    public static final String ACTION_NOT_SUPPORTED = NAMESPACE + "NOT_SUPPORTED";

    public static final String EXTRA_PACKAGES = NAMESPACE + "PACKAGES";
    public static final String EXTRA_PREVENT = NAMESPACE + "PREVENT";
    public static final String EXTRA_CONFIGURATION = NAMESPACE + "CONFIGURATION";

    public static final String CATEGORY_ALARM = NAMESPACE + ".CATEGORY_ALARM";

    public static final String SCHEME = "prevent";
    public static final String PERMISSION_MANAGER = Manifest.permission.MANAGER;
    public static final String PERMISSION_SYSTEM = "android.permission.SHUTDOWN";

    public static final String KEY_FORCE_STOP_TIMEOUT = "force_stop_timeout";
    public static final String KEY_DESTROY_PROCESSES = "destroy_processes";
    public static final String KEY_BACKUP_PREVENT_LIST = "backup_prevent_list";
    public static final String KEY_LOCK_SYNC_SETTINGS = "lock_sync_settings";
    public static final String KEY_AUTO_PREVENT = "auto_prevent";
    public static final String KEY_STOP_SIGNATURE_APPS = "stop_signature_apps";
    public static final String KEY_USE_APP_STANDBY = "use_app_standby";
    public static final String KEY_ALLOW_EMPTY_SENDER = "allow_empty_sender";
    public static final String KEY_PREVENT_LIST = "prevent_list";

    public static final String[] KEYS_LONG = new String[] {
            KEY_FORCE_STOP_TIMEOUT
    };

    public static final String[] KEYS_BOOLEAN = new String[] {
            KEY_ALLOW_EMPTY_SENDER,
            KEY_DESTROY_PROCESSES,
            KEY_BACKUP_PREVENT_LIST,
            KEY_LOCK_SYNC_SETTINGS,
            KEY_AUTO_PREVENT,
            KEY_STOP_SIGNATURE_APPS,
            KEY_USE_APP_STANDBY
    };

    public static final Uri CONTENT_URI = Uri.parse("content://me.piebridge.prevent.provider");
    public static final String LOGCAT_BOOT = "boot";
    public static final String LOGCAT_COMPLETED = "completed";

    private PreventIntent() {

    }

    private static boolean isKey(String key, String[] keys) {
        for (String k : keys) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBoolean(String key) {
        return isKey(key, KEYS_BOOLEAN);
    }

    public static boolean isLong(String key) {
        return isKey(key, KEYS_LONG);
    }
}
