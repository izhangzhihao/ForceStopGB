package me.piebridge.prevent.framework.util;

import android.content.Context;
import android.content.Intent;

import java.util.Set;

import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.framework.IntentFilterMatchResult;
import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 15/8/2.
 */
public class AlarmManagerServiceUtils {

    private static Object cachedFilter;

    private AlarmManagerServiceUtils() {

    }

    public static void releaseAlarm(Context context, String packageName) {
        final Intent intent = new Intent(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE, null);
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, new String[]{packageName});
        intent.addCategory(PreventIntent.CATEGORY_ALARM);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(intent);
    }

    public static boolean canHook(Set<String> categories) {
        return categories != null && categories.contains(PreventIntent.CATEGORY_ALARM);
    }

    public static IntentFilterMatchResult hook(Object filter) {
        if (isAlarm(filter)) {
            return IntentFilterMatchResult.NONE;
        } else {
            return IntentFilterMatchResult.NO_MATCH;
        }
    }

    private static boolean isAlarm(Object filter) {
        if (cachedFilter != null) {
            return cachedFilter.equals(filter);
        }
        String receiverName = BroadcastFilterUtils.getReceiverName(filter);
        if (receiverName != null && receiverName.endsWith("AlarmManagerService$UninstallReceiver")) {
            PreventLog.d("found " + receiverName + " in filter: " + filter);
            cachedFilter = filter;
            return true;
        } else {
            PreventLog.v("checking " + receiverName + " in filter: " + filter);
            return false;
        }
    }

}
