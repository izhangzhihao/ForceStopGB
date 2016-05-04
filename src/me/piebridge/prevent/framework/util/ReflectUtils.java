package me.piebridge.prevent.framework.util;

import java.lang.reflect.Field;

import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 16/2/3.
 */
public class ReflectUtils {

    private ReflectUtils() {

    }

    public static Field getDeclaredField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Field field = null;
        Class clazz = target.getClass();
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(name);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                PreventLog.d("cannot find field " + name + " in " + clazz, e);
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            PreventLog.e("cannot find field " + name + " in " + target.getClass());
        }
        return field;
    }

}
