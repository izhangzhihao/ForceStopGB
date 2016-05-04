package me.piebridge.prevent.framework;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by thom on 15/7/25.
 */
public class PreventLog {

    public static final String TAG = "Prevent";

    private static boolean hasXposedString = false;

    private static boolean hasXposedThrowable = false;

    private static Method xposedLogString;

    private static Method xposedLogThrowable;

    static {
        initXposed();
    }

    private PreventLog() {

    }

    public static void v(String msg) {
        Log.v(TAG, msg);
        if (!SystemHook.isActivated()) {
            logToXposed("[V/" + TAG + "] " + msg);
        }
    }

    public static void v(String msg, Throwable t) {
        Log.v(TAG, msg, t);
        if (!SystemHook.isActivated()) {
            logToXposed("[V/" + TAG + "] " + msg);
            logToXposed(t);
        }
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
        if (!SystemHook.isActivated()) {
            logToXposed("[D/" + TAG + "] " + msg);
        }
    }

    public static void d(String msg, Throwable t) {
        Log.d(TAG, msg, t);
        if (!SystemHook.isActivated()) {
            logToXposed("[D/" + TAG + "] " + msg);
            logToXposed(t);
        }
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        logToXposed("[I/" + TAG + "] " + msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        logToXposed("[W/" + TAG + "] " + msg);
    }

    public static void w(String msg, Throwable t) {
        Log.w(TAG, msg, t);
        logToXposed("[W/" + TAG + "] " + msg);
        logToXposed(t);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
        logToXposed("[E/" + TAG + "] " + msg);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        logToXposed("[E/" + TAG + "] " + msg);
        logToXposed(t);
    }

    private static void logToXposed(String msg) {
        if (!hasXposedString) {
            return;
        }
        try {
            xposedLogString.invoke(null, msg);
        } catch (InvocationTargetException e) {
            hasXposedString = false;
            Log.e(TAG, "cannot invoke XposedBridge.log(String)", e);
        } catch (IllegalAccessException e) {
            hasXposedString = false;
            Log.e(TAG, "cannot access XposedBridge.log(String)", e);
        }
    }

    private static void logToXposed(Throwable t) {
        if (!hasXposedThrowable) {
            return;
        }
        try {
            xposedLogThrowable.invoke(null, t);
        } catch (InvocationTargetException e) {
            hasXposedThrowable = false;
            Log.e(TAG, "cannot invoke XposedBridge.log(Throwable)", e);
        } catch (IllegalAccessException e) {
            hasXposedThrowable = false;
            Log.e(TAG, "cannot access XposedBridge.log(Throwable)", e);
        }
    }

    private static void initXposed() {
        try {
            Class<?> clazz = Class.forName("de.robv.android.xposed.XposedBridge", false, ClassLoader.getSystemClassLoader());
            xposedLogString = clazz.getMethod("log", String.class);
            hasXposedString = true;
            xposedLogThrowable = clazz.getMethod("log", Throwable.class);
            hasXposedThrowable = true;
        } catch (ClassNotFoundException e) { // NOSONAR
            Log.d(TAG, "cannot find XposedBridge");
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "cannot find method", e);
        }
    }

}
