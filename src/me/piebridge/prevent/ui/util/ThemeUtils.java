package me.piebridge.prevent.ui.util;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.preference.PreferenceManager;

import java.lang.reflect.Method;

import me.piebridge.forcestopgb.R;

/**
 * Created by thom on 15/10/6.
 */
public class ThemeUtils {

    private static final String THEME = "theme";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private ThemeUtils() {

    }

    private static boolean hasSmartBar() {
        try {
            Method method = Build.class.getMethod("hasSmartBar");
            return (Boolean) method.invoke(null);
        } catch (Exception e) { // NOSONAR
            // do nothing
        }
        return false;
    }

    public static void setTheme(Activity activity) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        activity.setTheme(THEME_LIGHT.equals(sp.getString(THEME, THEME_LIGHT)) ? R.style.light : R.style.dark);
        if (hasSmartBar()) {
            activity.getWindow().setUiOptions(ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW);
        }
    }

    public static void switchTheme(Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = sp.getString(ThemeUtils.THEME, ThemeUtils.THEME_LIGHT);
        if (ThemeUtils.THEME_LIGHT.equals(theme)) {
            sp.edit().putString(ThemeUtils.THEME, ThemeUtils.THEME_DARK).apply();
        } else {
            sp.edit().putString(ThemeUtils.THEME, ThemeUtils.THEME_LIGHT).apply();
        }
    }

    public static void fixSmartBar(Activity activity) {
        try {
            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.setActionBarViewCollapsable(true);
            }
        } catch (NoSuchMethodError e) { // NOSONAR
            // do nothing
        }
    }

}
