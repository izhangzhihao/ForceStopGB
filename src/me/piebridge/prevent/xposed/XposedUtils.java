package me.piebridge.prevent.xposed;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Field;
import java.util.Map;

import me.piebridge.forcestopgb.BuildConfig;

/**
 * Created by thom on 15/10/13.
 */
public class XposedUtils {

    private XposedUtils() {

    }

    public static boolean canDisableXposed() {
        return !("samsung".equals(Build.BRAND) && System.getProperty("java.vm.version", "1").startsWith("2"));
    }

    public static void disableXposed(Class<?> clazz) {
        try {
            Field field = clazz.getDeclaredField("sHookedMethodCallbacks");
            field.setAccessible(true);
            Map sHookedMethodCallbacks = (Map) field.get(null);
            Object doNothing = Class.forName("de.robv.android.xposed.XC_MethodReplacement", false, clazz.getClassLoader()).getField("DO_NOTHING").get(null);
            for (Object callbacks : sHookedMethodCallbacks.values()) {
                field = callbacks.getClass().getDeclaredField("elements");
                field.setAccessible(true);
                Object[] elements = (Object[]) field.get(callbacks);
                for (int i = 0; i < elements.length; ++i) {
                    elements[i] = doNothing;
                }
            }
        } catch (Throwable t) { // NOSONAR
            // do nothing
        }
    }

    public static boolean hasXposed(Context context) {
        try {
            context.getPackageManager().getPackageInfo("de.robv.android.xposed.installer", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) { // NOSONAR
            return false;
        }
    }

    public static void startXposed(Activity activity) {
        Intent intent = new Intent("de.robv.android.xposed.installer.OPEN_SECTION");
        intent.setPackage("de.robv.android.xposed.installer");
        intent.putExtra("section", "modules");
        intent.putExtra("module", BuildConfig.APPLICATION_ID);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) { // NOSONAR
            activity.finish();
        }
    }

}
