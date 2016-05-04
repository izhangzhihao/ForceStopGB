package me.piebridge.prevent.common;

import android.os.Bundle;

import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 16/2/21.
 */
public class Configuration {

    private final Bundle bundle;

    private static Configuration mConfiguration = new Configuration();

    private Configuration() {
        this.bundle = new Bundle();
    }

    public static Configuration getDefault() {
        return mConfiguration;
    }

    public long getForceStopTimeout() {
        return bundle.getLong(PreventIntent.KEY_FORCE_STOP_TIMEOUT, -1);
    }

    public boolean isAllowEmptySender() {
        return bundle.getBoolean(PreventIntent.KEY_ALLOW_EMPTY_SENDER, true);
    }

    public boolean isAutoPrevent() {
        return bundle.getBoolean(PreventIntent.KEY_AUTO_PREVENT, true);
    }

    public boolean isBackupPreventList() {
        return bundle.getBoolean(PreventIntent.KEY_BACKUP_PREVENT_LIST, false);
    }

    public boolean isDestroyProcesses() {
        return bundle.getBoolean(PreventIntent.KEY_DESTROY_PROCESSES, false);
    }

    public boolean isLockSyncSettings() {
        return bundle.getBoolean(PreventIntent.KEY_LOCK_SYNC_SETTINGS, false);
    }

    public boolean isStopSignatureApps() {
        return bundle.getBoolean(PreventIntent.KEY_STOP_SIGNATURE_APPS, true);
    }

    public boolean isUseAppStandby() {
        return bundle.getBoolean(PreventIntent.KEY_USE_APP_STANDBY, false);
    }

    public Bundle getBundle() {
        return new Bundle(bundle);
    }

    public void updateBundle(Bundle bundle) {
        for (String key : PreventIntent.KEYS_LONG) {
            if (bundle.containsKey(key)) {
                long value = bundle.getLong(key);
                PreventLog.d("update " + key + " to " + value);
                this.bundle.putLong(key, value);
            }
        }
        for (String key : PreventIntent.KEYS_BOOLEAN) {
            if (bundle.containsKey(key)) {
                boolean value = bundle.getBoolean(key);
                PreventLog.d("update " + key + " to " + value);
                this.bundle.putBoolean(key, value);
            }
        }
    }
}
