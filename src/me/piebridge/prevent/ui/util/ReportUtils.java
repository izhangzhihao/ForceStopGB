package me.piebridge.prevent.ui.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.ui.UILog;

/**
 * Created by thom on 15/12/14.
 */
public class ReportUtils {

    private ReportUtils() {

    }

    public static void reportBug(Context context) {
        File dir = context.getExternalFilesDir(null);
        File cacheDir = context.getExternalCacheDir();
        if (dir == null || cacheDir == null) {
            return;
        }
        try {
            File path = new File(dir, "logs-v" + BuildConfig.VERSION_NAME + ".zip");
            final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));

            File[] caches = cacheDir.listFiles();
            if (caches == null) {
                caches = new File[0];
            }
            boolean empty = true;
            for (File file : caches) {
                empty = false;
                zos.putNextEntry(new ZipEntry(file.getName()));
                FileUtils.copyFile(zos, file);
                zos.closeEntry();
            }

            File xposedLog = new File(Environment.getDataDirectory() + "/data/de.robv.android.xposed.installer/log/error.log");
            if (xposedLog.isFile() && xposedLog.canRead()) {
                empty = false;
                zos.putNextEntry(new ZipEntry("xposed.log"));
                FileUtils.copyFile(zos, xposedLog);
                zos.closeEntry();
            }

            if (empty) {
                zos.putNextEntry(new ZipEntry("empty"));
                zos.closeEntry();
            }

            zos.close();
            EmailUtils.sendZip(context, path, Build.FINGERPRINT);
        } catch (IOException e) {
            UILog.e("cannot report bug", e);
        }
    }

    public static void clearReport(Context context) {
        File dir = context.getExternalCacheDir();
        if (dir == null) {
            return;
        }
        for (File file : dir.listFiles()) {
            String path = file.getName();
            if (!path.startsWith(PreventIntent.LOGCAT_BOOT)) {
                file.delete();
            }
        }
    }

    public static void waitForCompleted(Context context) {
        File dir = context.getExternalCacheDir();
        if (dir == null) {
            return;
        }
        while (true) {
            for (File file : dir.listFiles()) {
                String path = file.getName();
                if (PreventIntent.LOGCAT_COMPLETED.equals(path)) {
                    return;
                }
            }
            try {
                Thread.sleep(0x400);
            } catch (InterruptedException e) {
                UILog.d("cannot sleep", e);
            }
        }
    }
}
