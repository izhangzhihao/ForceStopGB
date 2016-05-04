package me.piebridge.prevent.framework;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.prevent.common.Configuration;
import me.piebridge.prevent.common.GmsUtils;
import me.piebridge.prevent.common.PackageUtils;
import me.piebridge.prevent.framework.util.AccountUtils;
import me.piebridge.prevent.framework.util.LogUtils;
import me.piebridge.prevent.framework.util.SafeActionUtils;

/**
 * Created by thom on 15/8/11.
 */
public class ActivityManagerServiceHook {

    private static Context mContext;
    private static Map<String, Boolean> mPreventPackages;
    // normally, there is only one
    private static Collection<String> settingsPackages = new HashSet<String>();
    private static Collection<String> importantSystemPackages = new HashSet<String>();

    private static ScheduledThreadPoolExecutor cleanUpExecutor = new ScheduledThreadPoolExecutor(0x1);

    private ActivityManagerServiceHook() {

    }

    public static void setContext(Context context, Map<String, Boolean> preventPackages) {
        mContext = context;
        mPreventPackages = preventPackages;
    }

    public static boolean hookStartProcessLocked(Context context, ApplicationInfo info, String hostingType, ComponentName hostingName, String sender) {
        String packageName = info.packageName;

        if (mContext == null && context != null) {
            SystemHook.retrievePreventsIfNeeded(context);
        }

        if (BuildConfig.DEBUG) {
            if (hostingName != null) {
                PreventLog.v("startProcessLocked, hostingName: " + hostingName.flattenToShortString() + ", hostingType: " + hostingType + ", sender: " + sender);
            } else {
                PreventLog.v("startProcessLocked, packageName: " + packageName + ", hostingType: " + hostingType + ", sender: " + sender);
            }
        }

        if (mPreventPackages == null) {
            PreventLog.e("prevent list shouldn't be null");
            return true;
        }

        boolean prevents = Boolean.TRUE.equals(mPreventPackages.get(packageName));
        if ("activity".equals(hostingType)) {
            SystemHook.cancelCheck(packageName);
            SystemHook.updateRunningGapps(packageName, true);
            if (prevents) {
                // never block activity
                mPreventPackages.put(packageName, false);
                prevents = false;
            }
            LogUtils.logStartProcess(packageName, hostingType, hostingName, sender);
        }

        return !prevents || hookDependency(hostingName, hostingType, packageName, sender);
    }

    private static boolean hookDependency(ComponentName hostingName, String hostingType, String packageName, String sender) {
        if (packageName.equals(sender)) {
            LogUtils.logStartProcess(packageName, hostingType + "(self)", hostingName, sender);
            return true;
        }
        if ("broadcast".equals(hostingType)) {
            // always block broadcast
            return hookBroadcast(hostingName, hostingType, packageName, sender);
        } else if ("service".equals(hostingType)) {
            return hookService(hostingName, hostingType, packageName, sender);
        } else if ("content provider".equals(hostingType) && !SystemHook.isSystemPackage(packageName)) {
            LogUtils.logStartProcess(true, packageName, hostingType, hostingName, sender);
            return false;
        }

        SystemHook.checkRunningServices(packageName, false);
        LogUtils.logStartProcess(packageName, hostingType + "(should safe)", hostingName, sender);
        return true;
    }

    private static boolean hookBroadcast(ComponentName hostingName, String hostingType, String packageName, String sender) {
        if (SafeActionUtils.isSafeBroadcast(hostingName)) {
            SystemHook.checkRunningServices(packageName, false);
            LogUtils.logStartProcess(packageName, hostingType + "(safe)", hostingName, sender);
            return true;
        } else {
            LogUtils.logStartProcess(true, packageName, hostingType, hostingName, sender);
            return false;
        }
    }

    private static boolean hookService(ComponentName hostingName, String hostingType, String packageName, String sender) {
        if (SafeActionUtils.isSyncService(mContext, hostingName, sender)) {
            return hookSyncService(hostingName, hostingType, packageName, sender);
        }
        if (SafeActionUtils.isAccountService(mContext, hostingName, sender)) {
            return hookAccountService(hostingName, hostingType, packageName, sender);
        }
        if (GmsUtils.isGms(packageName)) {
            return hookGmsService(hostingName, hostingType, packageName, sender);
        }
        if (cannotPrevent(sender, packageName, hostingName)) {
            SystemHook.checkRunningServices(packageName, true);
            LogUtils.logStartProcess(packageName, hostingType, hostingName, sender);
            return true;
        } else {
            LogUtils.logStartProcess(true, packageName, hostingType, hostingName, sender);
            return false;
        }
    }

    private static boolean hookAccountService(ComponentName hostingName, String hostingType, String packageName, String sender) {
        Set<String> currentPackageNames = SystemHook.getCurrentPackageNames();
        PreventLog.d("account authenticator, current package: " + currentPackageNames + ", " + hostingName.flattenToShortString());
        if (settingsPackages.isEmpty()) {
            retrieveSettingsPackage(mContext.getPackageManager(), settingsPackages);
        }
        if (isSettingPackageName(currentPackageNames) || (GmsUtils.isGapps(packageName) && GmsUtils.isGapps(currentPackageNames))) {
            handleSafeService(packageName);
            SystemHook.checkRunningServices(packageName, true);
            LogUtils.logStartProcess(packageName, hostingType + "(account)", hostingName, sender);
            return true;
        } else {
            LogUtils.logStartProcess(true, packageName, hostingType + "(account)", hostingName, sender);
            return false;
        }
    }

    private static boolean isSettingPackageName(Set<String> currentPackageNames) {
        for (String currentPackageName : currentPackageNames) {
            if (settingsPackages.contains(currentPackageName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cannotPrevent(String sender, String packageName, ComponentName hostingName) {
        if (SafeActionUtils.isUnsafeService(hostingName)) {
            return false;
        } else if (SafeActionUtils.isSafeService(hostingName) || SafeActionUtils.cannotPrevent(mContext, hostingName)) {
            return true;
        } else if (sender == null) {
            return Configuration.getDefault().isAllowEmptySender();
        } else {
            return cannotPrevent(sender, packageName);
        }
    }

    private static boolean hookGmsService(ComponentName hostingName, String hostingType, String packageName, String sender) {
        if (cannotPreventGms(hostingName, sender)) {
            // only allow gapps to use gms
            SystemHook.checkRunningServices(packageName, true);
            LogUtils.logStartProcess(packageName, hostingType, hostingName, sender);
            return true;
        } else {
            LogUtils.logStartProcess(true, packageName, hostingType, hostingName, sender);
            return false;
        }
    }

    private static void retrieveSettingsPackage(PackageManager pm, Collection<String> packages) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", BuildConfig.APPLICATION_ID, null));
        for (ResolveInfo resolveInfo : pm.queryIntentActivities(intent, 0)) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (SystemHook.isSystemPackage(packageName)) {
                PreventLog.d("add " + packageName + " as settings");
                packages.add(packageName);
            }
        }
    }

    private static boolean cannotPreventGms(ComponentName component, String sender) {
        if (GmsUtils.isGmsRegister(mContext, component)) {
            return true;
        }
        PackageManager pm = mContext.getPackageManager();
        if (settingsPackages.isEmpty()) {
            retrieveSettingsPackage(pm, settingsPackages);
        }
        if (settingsPackages.contains(sender)) {
            return true;
        }
        if (!GmsUtils.isGapps(sender)) {
            return false;
        }
        return pm.getLaunchIntentForPackage(sender) == null || SystemHook.hasRunningActivity(sender);
    }

    private static boolean cannotPrevent(String sender, String packageName) {
        if (SystemHook.isFramework(packageName)) {
            return true;
        } else if (TextUtils.isDigitsOnly(sender)) {
            return Integer.parseInt(sender) < SystemHook.FIRST_APPLICATION_UID;
        } else if (cannotPrevent(sender)
                || mContext.getPackageManager().getLaunchIntentForPackage(sender) == null
                || (SystemHook.isSystemPackage(packageName) && SystemHook.hasRunningActivity(sender))) {
            // the sender cannot be prevent
            // the sender has no launcher
            // running sender call system package
            return true;
        }
        return false;
    }

    public static boolean cannotPrevent(String packageName) {
        if (importantSystemPackages.contains(packageName)) {
            return true;
        }
        try {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            boolean result = !PackageUtils.canPrevent(pm, info);
            if (result && PackageUtils.isSystemPackage(info.flags)) {
                importantSystemPackages.add(packageName);
            }
            return result;
        } catch (PackageManager.NameNotFoundException e) {
            PreventLog.d("cannot find package: " + packageName, e);
            return false;
        }
    }

    private static boolean hookSyncService(ComponentName hostingName, String hostingType, String packageName, String sender) {
        if (ContentResolver.getMasterSyncAutomatically() && AccountUtils.isComponentSyncable(mContext, hostingName)) {
            handleSafeService(packageName);
            SystemHook.checkRunningServices(packageName, true);
            LogUtils.logStartProcess(packageName, hostingType + "(sync)", hostingName, sender);
            return true;
        } else {
            LogUtils.logStartProcess(true, packageName, hostingType + "(sync)", hostingName, sender);
            return false;
        }
    }

    private static void handleSafeService(String packageName) {
        if (Boolean.TRUE.equals(mPreventPackages.get(packageName))) {
            PreventLog.i("allow " + packageName + " for next service/broadcast");
            mPreventPackages.put(packageName, false);
            SystemHook.restoreLater(packageName);
        }
    }

    public static boolean onCleanUpRemovedTask(final String packageName) {
        SystemHook.updateRunningGapps(packageName, false);
        if (packageName != null && mPreventPackages != null && mPreventPackages.containsKey(packageName)) {
            mPreventPackages.put(packageName, true);
            cleanUpExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (Boolean.TRUE.equals(mPreventPackages.get(packageName))) {
                        LogUtils.logForceStop("removeTask", packageName, "");
                        SystemHook.forceStopPackage(packageName, true);
                    }
                }
            });
        }
        return true;
    }

}
