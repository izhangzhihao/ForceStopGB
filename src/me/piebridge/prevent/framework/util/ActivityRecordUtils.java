package me.piebridge.prevent.framework.util;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import me.piebridge.prevent.framework.PreventLog;
import me.piebridge.prevent.framework.SystemHook;

/**
 * Created by thom on 15/9/18.
 */
public class ActivityRecordUtils {

    private static Field weakActivity;
    private static Class weakActivityClass;

    private static Map<String, Field> fields = new HashMap<String, Field>();
    private static Class fieldsClass;

    private ActivityRecordUtils() {

    }

    public static boolean isActivityRecord(Object object) {
        return object != null && object.getClass().getSimpleName().endsWith("ActivityRecord");
    }

    private static Object getField(Object target, String name) {
        Object activityRecord = getActivityRecord(target);
        if (activityRecord == null) {
            PreventLog.e("cannot find activity record from " + target);
            return null;
        }
        Field field = getCacheField(target, name);
        if (field != null) {
            try {
                return field.get(activityRecord);
            } catch (IllegalAccessException e) {
                PreventLog.e("cannot access " + name + " in " + activityRecord, e);
            }
        } else {
            PreventLog.e("cannot get " + name + " in " + activityRecord);
        }
        return null;
    }

    private static Field getCacheField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Field field;
        if (fieldsClass == target.getClass() && fields.containsKey(name)) {
            field = fields.get(name);
        } else {
            fieldsClass = target.getClass();
            field = ReflectUtils.getDeclaredField(target, name);
            if (field == null) {
                SystemHook.setNotSupported();
                PreventLog.e("cannot find " + name + " in " + fieldsClass);
            } else {
                PreventLog.d("find " + name + " " + field + " in " + fieldsClass);
            }
            fields.put(name, field);
        }
        return field;
    }

    public static Object getActivityRecord(Object target) {
        if (isActivityRecord(target)) {
            return target;
        }
        Field field = getCacheField(target);
        if (field != null) {
            try {
                return ((WeakReference<?>) field.get(target)).get();
            } catch (IllegalAccessException e) {
                PreventLog.e("cannot access weakActivity in " + target, e);
            }
        } else {
            PreventLog.e("cannot get weakActivity in " + target);
        }
        return null;
    }

    private static Field getCacheField(Object target) {
        if (target == null) {
            return null;
        }
        if (weakActivityClass != target.getClass()) {
            weakActivityClass = target.getClass();
            weakActivity = ReflectUtils.getDeclaredField(target, "weakActivity");
            if (weakActivity == null) {
                PreventLog.e("cannot find weakActivity in " + weakActivityClass);
                SystemHook.setNotSupported();
            } else {
                PreventLog.d("find weakActivity " + weakActivity + " in " + weakActivityClass);
            }
        }
        return weakActivity;
    }

    public static Object getTask(Object target) {
        return getField(target, "task");
    }

    public static String getPackageName(Object target) {
        return (String) getField(target, "packageName");
    }

    public static ActivityInfo getInfo(Object target) {
        return (ActivityInfo) getField(target, "info");
    }

    public static int getPid(Object target) {
        Object processRecord = getField(target, "app");
        return ProcessRecordUtils.getPid(processRecord);
    }

    public static int getUid(Object target) {
        Object processRecord = getField(target, "app");
        ApplicationInfo info = ProcessRecordUtils.getInfo(processRecord);
        if (info == null) {
            return 0;
        } else {
            return info.uid;
        }
    }

}
