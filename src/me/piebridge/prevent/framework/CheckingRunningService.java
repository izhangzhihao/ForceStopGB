package me.piebridge.prevent.framework;

import android.app.ActivityManager;
import android.content.Context;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.prevent.common.Configuration;
import me.piebridge.prevent.framework.util.HookUtils;

/**
 * Created by thom on 15/7/25.
 */

abstract class CheckingRunningService implements Runnable {

    private final Context mContext;
    private Map<String, Boolean> mPreventPackages;

    CheckingRunningService(Context context, Map<String, Boolean> preventPackages) {
        mContext = context;
        mPreventPackages = preventPackages;
    }

    @Override
    public void run() {
        Collection<String> packageNames = preparePackageNames();
        Collection<String> whiteList = prepareWhiteList();
        if (!packageNames.isEmpty() && packageNames.equals(whiteList)) {
            return;
        }
        PreventLog.d("checking services, packages: " + packageNames + ", whitelist: " + whiteList);
        Set<String> shouldStopPackageNames = new TreeSet<String>();
        if (Configuration.getDefault().isDestroyProcesses()) {
            shouldStopPackageNames.addAll(packageNames);
            shouldStopPackageNames.removeAll(whiteList);
            packageNames = Collections.emptyList();
        }
        for (ActivityManager.RunningServiceInfo service : HookUtils.getServices(mContext)) {
            checkService(service, packageNames, whiteList, shouldStopPackageNames);
        }
        stopServiceIfNeeded(shouldStopPackageNames);
        PreventLog.v("complete checking running service");
    }

    private boolean checkService(ActivityManager.RunningServiceInfo service, Collection<String> packageNames, Collection<String> whiteList, Set<String> shouldStopPackageNames) {
        String name = service.service.getPackageName();
        boolean prevent = Boolean.TRUE.equals(mPreventPackages.get(name));
        logServiceIfNeeded(prevent, name, service);
        if (!prevent || whiteList.contains(name)) {
            return false;
        }
        if (packageNames.contains(name) || service.started) {
            shouldStopPackageNames.add(name);
        }
        return true;
    }

    protected abstract Collection<String> preparePackageNames();

    protected abstract Collection<String> prepareWhiteList();

    private void logServiceIfNeeded(boolean prevents, String name, ActivityManager.RunningServiceInfo service) {
        if (!service.started) {
            return;
        }
        if (BuildConfig.DEBUG || prevents || service.uid >= SystemHook.FIRST_APPLICATION_UID) {
            PreventLog.v("prevents: " + prevents + ", name: " + name + ", count: " + service.clientCount + ", label: " + service.clientLabel
                    + ", uid: " + service.uid + ", pid: " + service.pid + ", process: " + service.process + ", flags: " + service.flags);
        }
    }

    private void stopServiceIfNeeded(Set<String> shouldStopPackageNames) {
        for (String name : shouldStopPackageNames) {
            String forceStop = "force stop";
            if (SystemHook.isUseAppStandby()) {
                forceStop = "standby";
            }
            if (Configuration.getDefault().isDestroyProcesses()) {
                PreventLog.i(forceStop + " " + name);
            } else {
                PreventLog.i(name + " has running services, " + forceStop + " it");
            }
            SystemHook.forceStopPackageIfNeeded(name);
        }
    }

}