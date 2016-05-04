package me.piebridge.prevent.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import me.piebridge.prevent.framework.PreventLog;
import me.piebridge.prevent.framework.SystemHook;

/**
 * Created by thom on 15/7/28.
 */
public class GmsUtils {

    private static final String GMS = "com.google.android.gms";
    private static final String GSF = "com.google.android.gsf";
    private static final String GSF_LOGIN = "com.google.android.gsf.login";
    private static final String GAPPS_PREFIX = "com.google.android.";
    private static final String GAPPS_INPUTMETHOD_PREFIX = "com.google.android.inputmethod";
    private static final AtomicInteger GMS_COUNTER = new AtomicInteger();
    // https://developers.google.com/cloud-messaging/android/client
    private static Collection<String> GCM_ACTIONS = Arrays.asList(
            "com.google.android.c2dm.intent.RECEIVE",
            "com.google.android.c2dm.intent.REGISTRATION");
    private static final String GCM_ACTION_REGISTER = "com.google.android.c2dm.intent.REGISTER";
    private static final String GCM_ACTION_UNREGISTER = "com.google.android.c2dm.intent.UNREGISTER";
    private static Collection<String> GMS_PACKAGES = Arrays.asList(
            GMS, GSF, GSF_LOGIN
    );
    private static Collection<String> GAPPS = Arrays.asList(
            "com.android.chrome", "com.android.facelock", "com.android.vending"
    );
    private static Set<ComponentName> GCM_REGISTERS = new HashSet<ComponentName>();

    private GmsUtils() {

    }

    public static boolean isGapps(String packageName) {
        return packageName != null && (packageName.startsWith(GAPPS_PREFIX) || GAPPS.contains(packageName));
    }

    public static void increaseGmsCount(Context context, String packageName) {
        if (!GMS.equals(packageName) && isGapps(packageName) && !PackageUtils.isLauncher(context.getPackageManager(), packageName)) {
            int gmsCount = GMS_COUNTER.incrementAndGet();
            PreventLog.d("increase gms reference: " + gmsCount + ", package: " + packageName);
        }
    }

    public static void decreaseGmsCount(Context context, String packageName) {
        if (!GMS.equals(packageName) && isGapps(packageName) && !PackageUtils.isLauncher(context.getPackageManager(), packageName)) {
            int gmsCount = GMS_COUNTER.decrementAndGet();
            PreventLog.d("decrease gms reference: " + gmsCount + ", package: " + packageName);
        }
    }

    public static boolean isGcmAction(String sender, boolean isSystem, String action) {
        return (isSystem || isGms(sender)) && GCM_ACTIONS.contains(action);
    }

    public static boolean isGcmRegisterAction(String action) {
        return GCM_ACTION_REGISTER.equals(action) || GCM_ACTION_UNREGISTER.equals(action);
    }

    public static boolean isGmsRegister(Context context, ComponentName component) {
        if (GCM_REGISTERS.isEmpty()) {
            initGmsRegisters(context, GCM_ACTION_REGISTER);
            initGmsRegisters(context, GCM_ACTION_UNREGISTER);
        }
        return GCM_REGISTERS.contains(component);
    }

    private static void initGmsRegisters(Context context, String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        intent.setPackage(GMS);
        List<ResolveInfo> intentServices = context.getPackageManager().queryIntentServices(intent, 0);
        final int size = intentServices == null ? 0 : intentServices.size();
        for (int i = 0; i < size; ++i) {
            ServiceInfo si = intentServices.get(i).serviceInfo;
            if (GMS.equals(si.packageName) && GCM_REGISTERS.add(new ComponentName(si.packageName, si.name))) {
                PreventLog.d("add gcm register/unregister: " + si.name);
            }
        }
    }

    public static boolean isGms(String packageName) {
        return packageName != null && GMS_PACKAGES.contains(packageName);
    }

    public static Collection<String> getGmsPackages() {
        return GMS_PACKAGES;
    }

    public static boolean isGappsCaller(Context context) {
        try {
            int callingUid = Binder.getCallingUid();
            if (callingUid < SystemHook.FIRST_APPLICATION_UID) {
                return false;
            }
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationInfo(GMS, 0).uid == callingUid || isGapps(pm, callingUid);
        } catch (PackageManager.NameNotFoundException e) {
            PreventLog.v("cannot find gms", e);
        }
        return false;
    }

    private static boolean isGapps(PackageManager pm, int callingUid) {
        String[] packageNames = pm.getPackagesForUid(callingUid);
        if (packageNames == null) {
            return false;
        }
        for (String packageName : packageNames) {
            if (isGapps(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canStopGms() {
        int gmsCount = GMS_COUNTER.get();
        if (gmsCount != 0) {
            PreventLog.d("cannot stop gms now, gms reference: " + gmsCount);
            return false;
        } else {
            // I think it's logged already
            return !SystemHook.hasRunningGapps();
        }
    }

    public static boolean isInputMethod(String name) {
        return name != null && name.startsWith(GAPPS_INPUTMETHOD_PREFIX);
    }

    public static boolean isGapps(Set<String> currentPackageNames) {
        for (String packageName : currentPackageNames) {
            if (isGapps(packageName)) {
                return true;
            }
        }
        return false;
    }

}
