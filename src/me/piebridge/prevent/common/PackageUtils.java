package me.piebridge.prevent.common;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.prevent.framework.SystemHook;

/**
 * Created by thom on 15/7/23.
 */
public class PackageUtils {

    private static Set<String> launchers;

    private static Set<String> inputMethodPackages = new HashSet<String>();

    private static String smsDefaultApplication;

    private static final Collection<String> IMPORT_PACKAGES = Arrays.asList(
            "de.robv.android.xposed.installer",
            "eu.chainfire.supersu",
            BuildConfig.APPLICATION_ID
    );

    private PackageUtils() {

    }

    public static boolean isSystemPackage(int flags) {
        return (flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    public static boolean isSystemSignaturePackage(PackageManager pm, String packageName) {
        return pm.checkSignatures("android", packageName) != PackageManager.SIGNATURE_NO_MATCH;
    }

    private static synchronized void initLauncher(PackageManager pm) {
        if (launchers == null) {
            launchers = new HashSet<String>();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo resolveInfo : pm.queryIntentActivities(intent, 0)) {
                launchers.add(resolveInfo.activityInfo.packageName);
            }
        }
    }

    public static boolean isLauncher(PackageManager pm, String packageName) {
        if (launchers == null) {
            initLauncher(pm);
        }
        return launchers.contains(packageName);
    }

    public static boolean canPrevent(PackageManager pm, ApplicationInfo appInfo) {
        return appInfo.uid >= SystemHook.FIRST_APPLICATION_UID && (!isSystemPackage(appInfo.flags) || canPreventSystemPackage(pm, appInfo));
    }

    private static boolean canPreventSystemPackage(PackageManager pm, ApplicationInfo appInfo) {
        // cannot prevent launcher
        if (isLauncher(pm, appInfo.packageName)) {
            return false;
        }
        // can prevent system packages with launcher
        if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
            return true;
        }
        if (isSystemSignaturePackage(pm, BuildConfig.APPLICATION_ID)) {
            // shouldn't happen, but for some abnormal rom
            return GmsUtils.isGapps(appInfo.packageName);
        } else {
            return !isSystemSignaturePackage(pm, appInfo.packageName);
        }
    }

    public static String getPackageName(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            return data.getSchemeSpecificPart();
        } else {
            return null;
        }
    }

    public static boolean equals(Object a, Object b) { // NOSONAR
        return (a == b) || (a != null && a.equals(b));
    }

    private static boolean isInputMethod(String name) {
        if (GmsUtils.isGapps(name) && !GmsUtils.isInputMethod(name)) {
            return false;
        }
        return inputMethodPackages.contains(name);
    }

    private static void initInputMethods(Context context) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> inputMethods = inputMethodManager.getEnabledInputMethodList();
        final int count = inputMethods == null ? 0 : inputMethods.size();
        for (int i = 0; i < count; ++i) {
            inputMethodPackages.add(inputMethods.get(i).getPackageName());
        }
    }

    public static void clearInputMethodPackages() {
        inputMethodPackages.clear();
    }

    public static boolean isImportPackage(Context context, String name) {
        if (name == null) {
            return false;
        }
        if (inputMethodPackages.isEmpty()) {
            initInputMethods(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                smsDefaultApplication = Settings.Secure.getString(context.getContentResolver(), "sms_default_application");
            }
        }
        return IMPORT_PACKAGES.contains(name)
                || isInputMethod(name)
                || name.equals(smsDefaultApplication);
    }

}
