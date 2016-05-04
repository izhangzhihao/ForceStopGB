package me.piebridge.prevent.framework.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 15/8/11.
 */
public class LogcatUtils {

    private static final String CACHE_PREFIX = "/data/system/me.piebridge.prevent.log.";
    private static final String COMMAND = "/system/bin/logcat -d -v time -f " + CACHE_PREFIX;

    public static final String BOOT = PreventIntent.LOGCAT_BOOT;
    public static final String PREVENT = "prevent";
    public static final String SYSTEM = "system";
    public static final String COMPLETED = "completed";

    private LogcatUtils() {

    }

    public static void logcat(String prefix, String log) {
        try {
            String command = COMMAND + prefix + " " + log;
            PreventLog.d("will execute: " + command);
            Runtime.getRuntime().exec(command).waitFor();
            PreventLog.d("execute complete: " + command);
        } catch (InterruptedException e) {
            PreventLog.e("execute interrupted", e);
        } catch (IOException e) {
            PreventLog.d("exec wrong", e);
        }
    }

    public static long logcat(Context context, String prefix) {
        File cache = new File(CACHE_PREFIX + prefix);
        if (cache.exists()) {
            long size = cache.length();
            PreventLog.d("send " + prefix + " log, size: " + cache.length());
            try {
                sendToUi(context, new BufferedInputStream(new FileInputStream(cache)), prefix);
                PreventLog.d("send to ui successfully");
            } catch (IOException e) {
                PreventLog.d("cannot send log to ui", e);
            }
            cache.delete();
            return size;
        } else if (!BOOT.equals(prefix)) {
            PreventLog.d("not exist: " + cache.getAbsolutePath());
        }
        return 0L;
    }

    private static void sendToUi(Context context, InputStream is, String prefix) throws IOException {
        int length;
        byte[] buffer = new byte[0x300];
        ContentResolver contentResolver = context.getContentResolver();
        String path = new SimpleDateFormat("yyyyMMdd.HH.mm.ss'.txt'", Locale.US).format(new Date());
        int offset = 0;
        while ((length = is.read(buffer)) != -1) {
            String line = Base64.encodeToString(buffer, 0, length, Base64.URL_SAFE | Base64.NO_WRAP);
            Uri uri = PreventIntent.CONTENT_URI.buildUpon().appendQueryParameter("path", prefix + "." + path)
                    .appendQueryParameter("offset", String.valueOf(offset))
                    .appendQueryParameter("log", line).build();
            contentResolver.query(uri, null, null, null, null);
            offset += length;
        }
        is.close();
    }

    public static void completed(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = PreventIntent.CONTENT_URI.buildUpon().appendQueryParameter("path", COMPLETED)
                .appendQueryParameter("offset", String.valueOf(0))
                .appendQueryParameter("log", "").build();
        contentResolver.query(uri, null, null, null, null);
    }

    public static void deleteBootLog() {
        File cache = new File(CACHE_PREFIX + BOOT);
        if (cache.exists()) {
            cache.delete();
        }
    }
}
