package me.piebridge.prevent.ui;

import android.util.Log;

/**
 * Created by thom on 15/7/25.
 */
public class UILog {

    public static final String TAG = "PreventUI";

    private UILog() {

    }

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void d(String msg, Throwable t) { // NOSONAR
        Log.d(TAG, msg);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
    }

}
