package me.piebridge.prevent.ui.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.SparseIntArray;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.piebridge.forcestopgb.R;

/**
 * Created by thom on 15/10/17.
 */
public class StatusUtils {

    private static SparseIntArray statusMap = new SparseIntArray();

    static {
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND, R.string.importance_background);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY, R.string.importance_empty);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, R.string.importance_gone);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND, R.string.importance_foreground);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE, R.string.importance_foreground_service);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING, R.string.importance_top_sleeping);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE, R.string.importance_perceptible);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE, R.string.importance_service);
        statusMap.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE, R.string.importance_visible);
        statusMap.put(-ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE, R.string.importance_service_not_started);
    }

    private StatusUtils() {

    }

    public static CharSequence formatRunning(Context context, Set<Long> running) {
        if (running == null) {
            return context.getString(R.string.not_running);
        } else {
            if (running.contains((long) ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) && running.contains((long) -ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE)) {
                running.remove((long) -ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE);
            }
            return doFormatRunning(context, running);
        }
    }

    private static CharSequence doFormatRunning(Context context, Set<Long> running) {
        Set<String> sets = new LinkedHashSet<String>();
        for (Long i : running) {
            int v = statusMap.get(i.intValue());
            if (v == 0) {
                long elapsed = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime()) - Math.abs(i);
                sets.add(DateUtils.formatElapsedTime(elapsed));
            } else {
                sets.add(context.getString(v));
            }
        }
        return toString(sets);
    }

    private static CharSequence toString(Set<String> sets) {
        StringBuilder buffer = new StringBuilder();
        Iterator<?> it = sets.iterator();
        while (it.hasNext()) {
            buffer.append(it.next());
            if (it.hasNext()) {
                buffer.append(", ");
            } else {
                break;
            }
        }
        return buffer.toString();
    }


    private static boolean isPriority(Set<Long> running) {
        for (Long i : running) {
            int v = statusMap.get(i.intValue());
            if (v == 0) {
                return i < 0;
            }
        }
        return false;
    }

    public static int getDrawable(Set<Long> running, boolean prevent) {
        if (running == null) {
            return R.drawable.ic_menu_block;
        }
        if (isPriority(running)) {
            return R.drawable.ic_menu_star;
        } else if (prevent) {
            return R.drawable.ic_menu_block;
        } else {
            return R.drawable.ic_menu_stop;
        }
    }

}
