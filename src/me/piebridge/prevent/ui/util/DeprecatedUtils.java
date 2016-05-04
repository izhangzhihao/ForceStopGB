package me.piebridge.prevent.ui.util;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceActivity;

/**
 * Created by thom on 15/10/13.
 */
public class DeprecatedUtils {

    private DeprecatedUtils() {

    }

    @SuppressWarnings("deprecation")
    public static CharSequence getClipboard(Context context) {
        return ((android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).getText();
    }

    @SuppressWarnings("deprecation")
    public static void setClipboard(Context context, CharSequence content) {
        ((android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(content);
    }

    @SuppressWarnings("deprecation")
    public static void addPreferencesFromResource(PreferenceActivity activity, int preferencesResId) {
        activity.addPreferencesFromResource(preferencesResId);
    }

    @SuppressWarnings("deprecation")
    public static Preference findPreference(PreferenceActivity activity, CharSequence key) {
        return activity.findPreference(key);
    }

}
