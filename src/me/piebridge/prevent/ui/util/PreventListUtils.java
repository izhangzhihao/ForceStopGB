package me.piebridge.prevent.ui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.Set;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.prevent.common.ExternalFileUtils;
import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.common.FileUtils;
import me.piebridge.prevent.ui.UILog;

public final class PreventListUtils {

    private static boolean synced = false;

    private static PreventListUtils preventListUtils = new PreventListUtils();

    private PreventListUtils() {

    }

    protected String getPrevent(Context context) {
        String dataDir;
        try {
            dataDir = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).applicationInfo.dataDir;
        } catch (PackageManager.NameNotFoundException e) {
            UILog.d("cannot find package for context: " + context, e);
            dataDir = Environment.getDataDirectory() + "/data/" + BuildConfig.APPLICATION_ID;
        }
        return new File(new File(dataDir, "conf"), FileUtils.PREVENT_LIST).getAbsolutePath();
    }

    public synchronized void backupIfNeeded(Context context, Set<String> packages) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean backup = false;
        try {
            backup = sp.getBoolean(PreventIntent.KEY_BACKUP_PREVENT_LIST, false);
        } catch (ClassCastException e) {
            UILog.d("invalid value for " + PreventIntent.KEY_BACKUP_PREVENT_LIST, e);
            sp.edit().putBoolean(PreventIntent.KEY_BACKUP_PREVENT_LIST, false).apply();
        }
        for (File dir : ExternalFileUtils.getExternalFilesDirs(context)) {
            if (dir == null) {
                continue;
            }
            File file = new File(dir, FileUtils.PREVENT_LIST);
            if (backup) {
                FileUtils.save(file.getAbsolutePath(), packages);
            } else if (file.exists()) {
                FileUtils.eraseFiles(file);
            }
        }
        File file = new File(getPrevent(context));
        if (file.exists()) {
            FileUtils.eraseFiles(file);
        }
    }

    public boolean syncIfNeeded(Context context, Set<String> packages) {
        boolean updated = false;
        if (packages.isEmpty() && !synced) {
            synced = true;
            updated = PreventUtils.updateConfiguration(context, true);
        }
        backupIfNeeded(context, packages);
        return updated;
    }

    public Set<String> load(Context context) {
        return FileUtils.load(context, getPrevent(context));
    }

    public static PreventListUtils getInstance() {
        return preventListUtils;
    }

}
