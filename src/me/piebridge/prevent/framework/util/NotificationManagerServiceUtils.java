package me.piebridge.prevent.framework.util;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.os.ServiceManager;
import android.os.UserHandle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import me.piebridge.prevent.framework.IntentFilterMatchResult;
import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 15/7/31.
 */
public class NotificationManagerServiceUtils {

    private static Object nmsFilter;
    private static final String NMS = "com.android.server.NotificationManagerService$";
    private static final String NMS_21 = "com.android.server.notification.NotificationManagerService$";

    private static Object notificationManagerService;

    private static Method cancelAllNotificationsInt;

    private static final int REASON_PACKAGE_CHANGED = 5;

    private static final int[] REMOVE_FLAGS = new int[] {
            Notification.FLAG_FOREGROUND_SERVICE,
            Notification.FLAG_NO_CLEAR,
            Notification.FLAG_ONGOING_EVENT};

    private static Set<String> mKeepNotificationPackages = new HashSet<String>();
    private static Boolean success;
    private static ScheduledThreadPoolExecutor clearExecutor = new ScheduledThreadPoolExecutor(0x1);

    static {
        initMethod();
    }

    private NotificationManagerServiceUtils() {

    }

    private static boolean initMethod() {
        PreventLog.d("init NotificationManagerServiceUtils");
        Object object = ServiceManager.getService(Context.NOTIFICATION_SERVICE);
        if (object == null) {
            PreventLog.d("cannot find service: " + Context.NOTIFICATION_SERVICE);
            return false;
        }
        if (!object.getClass().getName().contains("$")) {
            return initMethod(object);
        }
        return initMethod(HideApiUtils.getThis0(object));
    }

    private static boolean initMethod(Object nms) {
        if (nms == null) {
            return false;
        }
        Class<?> clazz = nms.getClass();
        PreventLog.d("notificationManagerService: " + clazz.getName());
        while (clazz != null) {
            for (Method method : clazz.getDeclaredMethods()) {
                if ("cancelAllNotificationsInt".equals(method.getName())) {
                    method.setAccessible(true);
                    cancelAllNotificationsInt = method;
                    notificationManagerService = nms;
                    PreventLog.d("find cancelAllNotificationsInt in " + clazz.getName() + ": " + cancelAllNotificationsInt);
                    return true;
                }
            }
            clazz = clazz.getSuperclass();
        }
        PreventLog.d("cannot find cancelAllNotificationsInt in " + nms);
        return false;
    }

    private static boolean cancelStickyNotification(final String packageName) {
        if (Boolean.FALSE.equals(success)) {
            return false;
        }
        Future<Boolean> future = clearExecutor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return cancelStickyNotificationBlock(packageName);
            }
        });
        if (Boolean.TRUE.equals(success)) {
            return true;
        }
        try {
            success = future.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) { // NOSONAR
            PreventLog.d("cannot cancelStickyNotification (interrupt)", e);
        } catch (ExecutionException e) { // NOSONAR
            PreventLog.d("cannot cancelStickyNotification (execution)", e);
        } catch (TimeoutException e) {
            PreventLog.d("timeout when cancelStickyNotification", e);
        }
        return Boolean.TRUE.equals(success);
    }

    private static boolean cancelStickyNotificationBlock(String packageName) {
        int uid = Process.myUid();
        int pid = Process.myPid();
        int length = cancelAllNotificationsInt.getParameterTypes().length;
        PreventLog.v("cancel sticky notification: " + packageName);
        try {
            for (int flag : REMOVE_FLAGS) {
                if (length == 0x4) {
                    cancelAllNotificationsInt.invoke(notificationManagerService, packageName, flag, 0, true);
                } else if (length == 0x5) {
                    cancelAllNotificationsInt.invoke(notificationManagerService, packageName, flag, 0, true, UserHandle.USER_ALL);
                } else if (length == 0x9) {
                    cancelAllNotificationsInt.invoke(notificationManagerService, uid, pid, packageName, flag, 0, true,
                            UserHandle.USER_ALL, REASON_PACKAGE_CHANGED, null);
                }
            }
            return true;
        } catch (IllegalAccessException e) {
            PreventLog.d("cannot access cancelAllNotificationsInt", e);
        } catch (InvocationTargetException e) {
            PreventLog.d("cannot invoke cancelAllNotificationsInt", e);
        }
        return false;
    }

    public static boolean canHook(Object filter, Uri data, String action) {
        return canHook(filter, action) && mKeepNotificationPackages.remove(data.getSchemeSpecificPart());
    }

    private static boolean canHook(Object filter, String action) {
        if (!Intent.ACTION_PACKAGE_RESTARTED.equals(action)) {
            return false;
        }
        if (nmsFilter != null) {
            return nmsFilter.equals(filter);
        }
        String name = BroadcastFilterUtils.getReceiverName(filter);
        if (name != null && (name.startsWith(NMS) || name.startsWith(NMS_21))) {
            nmsFilter = filter;
            return true;
        } else {
            return false;
        }
    }

    public static IntentFilterMatchResult hook(Uri data, Map<String, Boolean> preventPackages) {
        String packageName = data.getSchemeSpecificPart();
        if (cancelAllNotificationsInt != null && packageName != null && preventPackages.containsKey(packageName) && cancelStickyNotification(packageName)) {
            return IntentFilterMatchResult.NO_MATCH;
        } else {
            return IntentFilterMatchResult.NONE;
        }
    }

    public static void keepNotification(String packageName) {
        mKeepNotificationPackages.add(packageName);
    }

}