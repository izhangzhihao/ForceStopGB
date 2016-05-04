package me.piebridge.prevent.framework.util;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.lang.reflect.Field;

import me.piebridge.prevent.framework.PreventLog;
import me.piebridge.prevent.framework.SystemHook;

/**
 * Created by thom on 15/7/14.
 */
public class ProcessRecordUtils {

    private static Class<?> ProcessRecord;

    private static Field ProcessRecord$info;

    private static Field ProcessRecord$pid;

    private static Field ProcessRecord$killedByAm;

    private ProcessRecordUtils() {

    }

    static {
        initReflection();
    }

    public static void initReflection() {
        PreventLog.d("init ProcessRecordUtils");
        ClassLoader classLoader = SystemHook.getClassLoader();
        try {
            ProcessRecord = Class.forName("com.android.server.am.ProcessRecord", false, classLoader);
            ProcessRecord$info = ProcessRecord.getDeclaredField("info");
            ProcessRecord$info.setAccessible(true);

            ProcessRecord$pid = ProcessRecord.getDeclaredField("pid");
            ProcessRecord$pid.setAccessible(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ProcessRecord$killedByAm = ProcessRecord.getDeclaredField("killedByAm");
            } else {
                ProcessRecord$killedByAm = ProcessRecord.getDeclaredField("killedBackground");
            }
            ProcessRecord$killedByAm.setAccessible(true);
        } catch (ClassNotFoundException e) {
            PreventLog.e("cannot find class for ProcessRecordUtils", e);
            SystemHook.setNotSupported();
        } catch (NoSuchFieldException e) {
            PreventLog.e("cannot find fields for ProcessRecordUtils", e);
            SystemHook.setNotSupported();
        }
    }

    public static ApplicationInfo getInfo(Object pr) {
        if (pr == null || ProcessRecord$info == null || !ProcessRecord.isAssignableFrom(pr.getClass())) {
            return null;
        }
        try {
            return (ApplicationInfo) ProcessRecord$info.get(pr);
        } catch (IllegalAccessException e) {
            PreventLog.e("cannot get info", e);
            return null;
        }
    }

    public static int getPid(Object pr) {
        if (pr == null || ProcessRecord$pid == null || !ProcessRecord.isAssignableFrom(pr.getClass())) {
            return 0;
        }
        try {
            return (Integer) ProcessRecord$pid.get(pr);
        } catch (IllegalAccessException e) {
            PreventLog.e("cannot get pid", e);
            return 0;
        }
    }

    public static boolean isKilledByAm(Object pr) {
        if (pr == null || ProcessRecord$killedByAm == null || !ProcessRecord.isAssignableFrom(pr.getClass())) {
            return true;
        }
        try {
            return (Boolean) ProcessRecord$killedByAm.get(pr);
        } catch (IllegalAccessException e) {
            PreventLog.e("cannot get killedByAm", e);
            return true;
        }
    }

}
