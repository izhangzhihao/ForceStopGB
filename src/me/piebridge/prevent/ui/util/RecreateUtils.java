package me.piebridge.prevent.ui.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityThread;
import android.app.IApplicationThread;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.Field;

import me.piebridge.prevent.ui.UILog;

public class RecreateUtils {


    private static Field mToken;

    private static Field mMainThread;

    private RecreateUtils() {

    }

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            initReflections();
        }
    }

    public static void recreate(Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            recreateHC(activity);
        } else {
            try {
                recreateGB(activity);
            } catch (IllegalAccessException e) {
                UILog.e("cannot call recreate (illegal access)", e);
            } catch (RemoteException e) {
                UILog.e("cannot call recreate (remote exception)", e);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void recreateHC(Activity activity) {
        activity.recreate();
    }

    private static void initReflections() {
        try {
            mToken = Activity.class.getDeclaredField("mToken");
            mToken.setAccessible(true);

            mMainThread = Activity.class.getDeclaredField("mMainThread");
            mMainThread.setAccessible(true);
        } catch (NoSuchFieldException e) {
            UILog.e("cannot find fields in Activity", e);
        }
    }

    private static void recreateGB(Activity activity) throws IllegalAccessException, RemoteException {
        if (mMainThread != null) {
            IBinder token = (IBinder) mToken.get(activity);
            ActivityThread mainThread = (ActivityThread) mMainThread.get(activity);
            IApplicationThread applicationThread = mainThread.getApplicationThread();
            applicationThread.scheduleRelaunchActivity(token, null, null, 0, false, null);
        }
    }

}
