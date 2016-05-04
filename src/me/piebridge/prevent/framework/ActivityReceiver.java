package me.piebridge.prevent.framework;

import android.app.INotificationManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.piebridge.prevent.common.Configuration;
import me.piebridge.prevent.framework.util.ActivityRecordUtils;
import me.piebridge.prevent.framework.util.HideApiUtils;
import me.piebridge.prevent.framework.util.LogUtils;
import me.piebridge.prevent.framework.util.ProcessRecordUtils;

abstract class ActivityReceiver extends BroadcastReceiver {

    protected Context mContext;
    protected Map<String, Boolean> mPreventPackages;

    private boolean screen = false;
    private Map<String, Integer> packageUids = new HashMap<String, Integer>();
    private Map<String, Set<String>> abnormalProcesses = new ConcurrentHashMap<String, Set<String>>();
    private Map<String, Map<Integer, AtomicInteger>> packageCounters = new ConcurrentHashMap<String, Map<Integer, AtomicInteger>>();
    private Map<String, Long> leavingPackages = new ConcurrentHashMap<String, Long>();
    private Set<String> checkLeavingNext = new TreeSet<String>();
    private ScheduledFuture<?> leavingFuture;
    private ScheduledThreadPoolExecutor singleExecutor = new ScheduledThreadPoolExecutor(0x2);

    public ActivityReceiver(Context context, Map<String, Boolean> preventPackages) {
        mContext = context;
        mPreventPackages = preventPackages;
    }

    protected int countCounter(String packageName) {
        return countCounter(-1, packageName);
    }

    private int countCounter(int currentPid, String packageName) {
        int count = 0;
        Map<Integer, AtomicInteger> values = packageCounters.get(packageName);
        if (values == null) {
            return count;
        }
        Iterator<Map.Entry<Integer, AtomicInteger>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, AtomicInteger> entry = iterator.next();
            int pid = entry.getKey();
            if (pid == currentPid || checkPid(pid, packageName)) {
                count += entry.getValue().get();
            } else {
                LogUtils.logIgnore(entry.getKey(), packageName);
                iterator.remove();
            }
        }
        return count;
    }


    private boolean checkPid(int pid, String packageName) {
        Integer uid = packageUids.get(packageName);
        if (uid == null) {
            return false;
        }
        try {
            if (HideApiUtils.getUidForPid(pid) != uid) {
                return false;
            }
        } catch (Throwable t) { // NOSONAR
            PreventLog.e("cannot get uid for " + pid, t);
        }
        String processName = getProcessName(uid, pid, packageName);
        if (isNormalProcessName(processName, packageName)) {
            return true;
        }
        PreventLog.v("pid: " + pid + ", package: " + packageName + ", process: " + processName);
        Set<String> abnormalPackages = abnormalProcesses.get(processName);
        return abnormalPackages != null && abnormalPackages.contains(packageName);
    }

    private String getProcessName(int uid, int pid, String packageName) {
        String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
        if (packages != null && packages.length == 1) {
            return packageName;
        } else {
            return SystemHook.getProcessName(pid);
        }
    }

    private boolean isNormalProcessName(String processName, String packageName) {
        return (processName != null) && (processName.equals(packageName)
                || processName.startsWith(packageName + ":")
                || "<pre-initialized>".equals(processName));
    }

    private void setAbnormalProcessIfNeeded(String processName, String packageName) {
        if (!isNormalProcessName(processName, packageName)) {
            Set<String> abnormalProcess = abnormalProcesses.get(processName);
            if (abnormalProcess == null) {
                abnormalProcess = new HashSet<String>();
                abnormalProcesses.put(processName, abnormalProcess);
            }
            if (abnormalProcess.add(packageName)) {
                PreventLog.d("package " + packageName + " has abnormal process: " + processName);
            }
        }
    }

    public void onLaunchActivity(Object activityRecord) {
        String packageName = ActivityRecordUtils.getPackageName(activityRecord);
        SystemHook.cancelCheck(packageName);
        SystemHook.updateRunningGapps(packageName, true);
        if (mPreventPackages.containsKey(packageName)) {
            mPreventPackages.put(packageName, false);
        }
        int pid = ActivityRecordUtils.getPid(activityRecord);
        int uid = ActivityRecordUtils.getUid(activityRecord);
        String processName = ActivityRecordUtils.getInfo(activityRecord).processName;
        setAbnormalProcessIfNeeded(processName, packageName);
        if (uid > 0) {
            packageUids.put(packageName, uid);
        }
        Map<Integer, AtomicInteger> packageCounter = packageCounters.get(packageName);
        if (packageCounter == null) {
            packageCounter = new HashMap<Integer, AtomicInteger>();
            packageCounters.put(packageName, packageCounter);
        }
        AtomicInteger pidCounter = packageCounter.get(pid);
        if (pidCounter == null) {
            pidCounter = new AtomicInteger();
            packageCounter.put(pid, pidCounter);
        }
        pidCounter.incrementAndGet();
        int count = countCounter(pid, packageName);
        if (count == 1) {
            SystemHook.checkSync(packageName);
        }
        LogUtils.logActivity("start activity", packageName, count);
    }

    public void onDestroyActivity(Object activityRecord) {
        String packageName = ActivityRecordUtils.getPackageName(activityRecord);
        Map<Integer, AtomicInteger> packageCounter = packageCounters.get(packageName);
        if (packageCounter != null) {
            int pid = ActivityRecordUtils.getPid(activityRecord);
            AtomicInteger pidCounter = packageCounter.get(pid);
            if (pidCounter != null) {
                pidCounter.decrementAndGet();
            }
        }
        int count = countCounter(packageName);
        LogUtils.logActivity("destroy activity", packageName, count);
        if (count > 0) {
            return;
        }
        SystemHook.updateRunningGapps(packageName, false);
        if (mPreventPackages.containsKey(packageName)) {
            mPreventPackages.put(packageName, true);
            LogUtils.logForceStop("destroy activity", packageName, "if needed in " + SystemHook.TIME_DESTROY + "s");
            SystemHook.checkRunningServices(packageName, SystemHook.TIME_DESTROY);
        } else {
            SystemHook.checkRunningServices(null, SystemHook.TIME_DESTROY);
        }
        SystemHook.killNoFather();
    }

    public void onDestroyActivity(String reason, String packageName) {
        SystemHook.updateRunningGapps(packageName, false);
        if (mPreventPackages.containsKey(packageName)) {
            mPreventPackages.put(packageName, true);
            LogUtils.logForceStop(reason, packageName, "destroy in " + SystemHook.TIME_SUICIDE + "s");
            SystemHook.checkRunningServices(packageName, SystemHook.TIME_SUICIDE);
        } else {
            SystemHook.checkRunningServices(null, SystemHook.TIME_SUICIDE < SystemHook.TIME_DESTROY ? SystemHook.TIME_DESTROY : SystemHook.TIME_SUICIDE);
        }
        SystemHook.killNoFather();
    }

    public void onResumeActivity(Object activityRecord) {
        String packageName = ActivityRecordUtils.getPackageName(activityRecord);
        SystemHook.cancelCheck(packageName);
        SystemHook.updateRunningGapps(packageName, true);
        if (Boolean.TRUE.equals(mPreventPackages.get(packageName))) {
            mPreventPackages.put(packageName, false);
        }
        int count = countCounter(packageName);
        LogUtils.logActivity("resume activity", packageName, count);
    }

    public void onUserLeavingActivity(Object activityRecord) {
        String packageName = ActivityRecordUtils.getPackageName(activityRecord);
        int count = countCounter(packageName);
        leavingPackages.put(packageName, TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime()));
        LogUtils.logActivity("user leaving activity", packageName, count);
    }

    protected void onPackageRemoved(String packageName) {
        leavingPackages.remove(packageName);
    }

    private void cancelCheckingIfNeeded() {
        if (leavingFuture != null && leavingFuture.getDelay(TimeUnit.SECONDS) > 0) {
            leavingFuture.cancel(false);
        }
    }

    protected void onScreenOn() {
        PreventLog.d("screen on");
        screen = true;
        cancelCheckingIfNeeded();
    }

    protected void onScreenOff() {
        PreventLog.d("screen off");
        screen = false;
        cancelCheckingIfNeeded();
        checkLeavingNext.clear();
        checkLeavingPackages();
    }

    public void cancelCheckLeaving(String packageName) {
        checkLeavingNext.remove(packageName);
    }

    private long getElapsed(long now, String packageName) {
        long elapsed;
        Long lastRunning = leavingPackages.get(packageName);
        if (lastRunning != null) {
            elapsed = now - lastRunning;
        } else {
            if (SystemHook.getCurrentPackageNames().contains(packageName)) {
                PreventLog.v(packageName + " is running, set elapsed to 0");
                elapsed = 0;
            } else if (checkLeavingNext.contains(packageName)) {
                elapsed = Configuration.getDefault().getForceStopTimeout();
            } else {
                PreventLog.v("cannot figure elapsed for " + packageName + ", wait for next checking");
                checkLeavingNext.add(packageName);
                elapsed = Configuration.getDefault().getForceStopTimeout() - SystemHook.TIME_CHECK_USER_LEAVING;
            }
        }
        return elapsed;
    }

    private void checkLeavingPackages() {
        if (Configuration.getDefault().getForceStopTimeout() <= 0) {
            return;
        }
        PreventLog.d("checking leaving packages");
        long now = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime());
        Iterator<Map.Entry<String, Boolean>> iterator = mPreventPackages.entrySet().iterator();
        Set<String> stopPackages = new HashSet<String>();
        boolean needCheckMore = false;
        while (iterator.hasNext()) {
            Map.Entry<String, Boolean> entry = iterator.next();
            String packageName = entry.getKey();
            Boolean prevent = entry.getValue();
            if (Boolean.TRUE.equals(prevent)) {
                leavingPackages.remove(packageName);
            }
            if (!Boolean.FALSE.equals(prevent)) {
                continue;
            }
            long elapsed = getElapsed(now, packageName);
            if (elapsed >= Configuration.getDefault().getForceStopTimeout()) {
                PreventLog.i("leaving package " + packageName + " for " + elapsed + " seconds");
                stopPackages.add(packageName);
            } else {
                needCheckMore = true;
            }
        }
        forceStopPackages(stopPackages);
        if (needCheckMore) {
            checkLeavingPackagesIfNeeded();
        }
    }

    private void forceStopPackages(Set<String> stopPackages) {
        for (String packageName : stopPackages) {
            if (screen) {
                break;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !hasHighPriority(packageName)) {
                SystemHook.forceStopPackage(packageName, false);
                leavingPackages.remove(packageName);
                checkLeavingNext.remove(packageName);
            }
        }
    }

    protected boolean hasHighPriority(String packageName) {
        INotificationManager sINM = INotificationManager.Stub.asInterface(ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        try {
            ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(packageName, 0);
            int priority = sINM.getPackagePriority(packageName, info.uid);
            if (priority == Notification.PRIORITY_MAX) {
                PreventLog.d(packageName + " has high priority " + priority + ", cannot stop");
                return true;
            }
            PreventLog.v("package " + packageName + ", priority: " + priority);
        } catch (RemoteException e) {
            PreventLog.d("cannot get package priority for " + packageName, e);
        } catch (PackageManager.NameNotFoundException e) {
            PreventLog.d("cannot find package " + packageName, e);
        }
        return false;
    }

    private void checkLeavingPackagesIfNeeded() {
        if (!screen) {
            leavingFuture = singleExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    if (!screen) {
                        checkLeavingPackages();
                    }
                }
            }, SystemHook.TIME_CHECK_USER_LEAVING, TimeUnit.SECONDS);
        }
    }

    public void onAppDied(Object processRecord) {
        String packageName = ProcessRecordUtils.getInfo(processRecord).packageName;
        if (leavingPackages.containsKey(packageName)) {
            LogUtils.logActivity("app died when user leaving", packageName, -1);
            return;
        }
        int count = countCounter(packageName);
        LogUtils.logActivity("app died", packageName, count);
        int pid = ProcessRecordUtils.getPid(processRecord);
        if (!shouldStop(packageName, pid)) {
            return;
        }
        SystemHook.updateRunningGapps(packageName, false);
        if (mPreventPackages.containsKey(packageName)) {
            mPreventPackages.put(packageName, true);
            SystemHook.checkRunningServices(packageName, SystemHook.TIME_IMMEDIATE < SystemHook.TIME_DESTROY ? SystemHook.TIME_DESTROY : SystemHook.TIME_IMMEDIATE);
        }
    }

    private boolean shouldStop(String packageName, int pid) {
        countCounter(packageName);
        Map<Integer, AtomicInteger> values = packageCounters.get(packageName);
        if (values == null) {
            return true;
        }
        Set<Integer> pids = new HashSet<Integer>(values.keySet());
        pids.remove(pid);
        return pids.isEmpty();
    }

    protected void removePackageCounters(String packageName) {
        packageCounters.remove(packageName);
    }

    public Map<String, Long> getLeavingPackages() {
        return new HashMap<String, Long>(leavingPackages);
    }

    public void removeLeavingPackage(String packageName) {
        leavingPackages.remove(packageName);
    }

}
