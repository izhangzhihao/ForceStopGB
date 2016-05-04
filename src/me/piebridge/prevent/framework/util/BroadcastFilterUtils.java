package me.piebridge.prevent.framework.util;

import android.content.pm.ApplicationInfo;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import me.piebridge.prevent.framework.PreventLog;
import me.piebridge.prevent.framework.SystemHook;

/**
 * Created by thom on 15/7/14.
 */
public class BroadcastFilterUtils {

    private static Class<?> BroadcastFilter;
    private static Field BroadcastFilter$receiverList;
    private static Field ReceiverList$app;
    private static Field ReceiverList$receiver;

    private BroadcastFilterUtils() {

    }

    static {
        initReflections();
    }

    private static void initReflections() {
        PreventLog.d("init BroadcastFilterUtils");
        ClassLoader classLoader = SystemHook.getClassLoader();
        try {
            BroadcastFilter = Class.forName("com.android.server.am.BroadcastFilter", false, classLoader);
            BroadcastFilter$receiverList = BroadcastFilter.getDeclaredField("receiverList");
            BroadcastFilter$receiverList.setAccessible(true);

            Class<?> receiverList = Class.forName("com.android.server.am.ReceiverList", false, classLoader);
            ReceiverList$app = receiverList.getDeclaredField("app");
            ReceiverList$app.setAccessible(true);

            ReceiverList$receiver = receiverList.getDeclaredField("receiver");
            ReceiverList$receiver.setAccessible(true);
        } catch (ClassNotFoundException e) {
            PreventLog.e("cannot find classes for BroadcastFilterUtils", e);
            SystemHook.setNotSupported();
        } catch (NoSuchFieldException e) {
            PreventLog.e("cannot find fields for BroadcastFilterUtils", e);
            SystemHook.setNotSupported();
        }
    }

    public static boolean isBroadcastFilter(Object filter) {
        return ReceiverList$receiver != null && BroadcastFilter.isAssignableFrom(filter.getClass());
    }

    public static String getPackageName(Object filter) {
        if (!isBroadcastFilter(filter)) {
            return null;
        }
        try {
            Object receiverList = BroadcastFilter$receiverList.get(filter);
            Object app = ReceiverList$app.get(receiverList);
            ApplicationInfo info = ProcessRecordUtils.getInfo(app);
            if (info != null) {
                return info.packageName;
            }
        } catch (IllegalAccessException e) {
            PreventLog.e("cannot get package name from " + filter, e);
        }
        return null;
    }

    public static String getReceiverName(Object filter) {
        if (!isBroadcastFilter(filter)) {
            return null;
        }
        try {
            Object receiverList = BroadcastFilter$receiverList.get(filter);
            Object receiver = ReceiverList$receiver.get(receiverList);
            Field field = receiver.getClass().getDeclaredField("mDispatcher");
            field.setAccessible(true);
            WeakReference mDispatcher = (WeakReference) field.get(receiver);
            Object rd = mDispatcher.get();
            field = rd.getClass().getDeclaredField("mReceiver");
            field.setAccessible(true);
            return field.get(rd).getClass().getName();
        } catch (NoSuchFieldException e) {
            PreventLog.v("cannot find field for filter: " + filter, e);
        } catch (IllegalAccessException e) {
            PreventLog.d("cannot access field for filter: " + filter, e);
        } catch (NullPointerException e) {
            PreventLog.v("cannot get field for filter: " + filter, e);
        }
        return null;
    }

}
