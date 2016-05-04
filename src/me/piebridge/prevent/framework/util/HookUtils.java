package me.piebridge.prevent.framework.util;

import android.app.ActivityManager;
import android.content.Context;

import java.util.Collections;
import java.util.List;

import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 15/8/2.
 */
public class HookUtils {

    private HookUtils() {

    }

    public static List<ActivityManager.RunningServiceInfo> getServices(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
        if (services != null) {
            PreventLog.v("services size: " + services.size());
            return services;
        } else {
            return Collections.emptyList();
        }
    }

}
