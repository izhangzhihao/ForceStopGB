package me.piebridge.prevent.framework;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.common.Configuration;
import me.piebridge.prevent.common.GmsUtils;
import me.piebridge.prevent.common.PackageUtils;
import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.framework.util.AccountUtils;
import me.piebridge.prevent.framework.util.HookUtils;
import me.piebridge.prevent.framework.util.LogUtils;
import me.piebridge.prevent.framework.util.LogcatUtils;
import me.piebridge.prevent.framework.util.PreventListUtils;
import me.piebridge.prevent.framework.util.SafeActionUtils;

/**
 * Created by thom on 15/7/25.
 */
public class SystemReceiver extends ActivityReceiver {

    private Future<?> logFuture;

    private ScheduledThreadPoolExecutor logExecutor = new ScheduledThreadPoolExecutor(0x2);

    private static final int RADIX = 10;

    public static final Collection<String> MANAGER_ACTIONS = Arrays.asList(
            PreventIntent.ACTION_GET_PACKAGES,
            PreventIntent.ACTION_GET_PROCESSES,
            PreventIntent.ACTION_GET_INFO,
            PreventIntent.ACTION_UPDATE_PREVENT,
            PreventIntent.ACTION_SYSTEM_LOG,
            PreventIntent.ACTION_UPDATE_CONFIGURATION,
            PreventIntent.ACTION_CHECK_LICENSE,
            PreventIntent.ACTION_SOFT_REBOOT,
            PreventIntent.ACTION_REBOOT
    );

    public static final Collection<String> PACKAGE_ACTIONS = Arrays.asList(
            Intent.ACTION_PACKAGE_RESTARTED,
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED
    );

    public static final Collection<String> NON_SCHEME_ACTIONS = Arrays.asList(
            AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_SCREEN_ON
    );

    public SystemReceiver(Context context, Map<String, Boolean> preventPackages) {
        super(context, preventPackages);
        if (PreventListUtils.getInstance().canLoadConfiguration(mContext)) {
            PreventListUtils.getInstance().loadConfiguration(mContext);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (MANAGER_ACTIONS.contains(action)) {
            handleManager(context, intent, action);
        } else if (PACKAGE_ACTIONS.contains(action)) {
            handlePackage(intent, action);
        } else if (NON_SCHEME_ACTIONS.contains(action)) {
            handleNonScheme(action);
        }
    }

    private void handleManager(Context context, Intent intent, String action) {
        if (PreventIntent.ACTION_GET_PACKAGES.equals(action)) {
            handleGetPackages(action);
        } else if (PreventIntent.ACTION_GET_PROCESSES.equals(action)) {
            handleGetProcesses(context, action);
        } else if (PreventIntent.ACTION_GET_INFO.equals(action)) {
            handleGetInfo();
        } else if (PreventIntent.ACTION_UPDATE_PREVENT.equals(action)) {
            handleUpdatePrevent(action, intent);
        } else if (PreventIntent.ACTION_SYSTEM_LOG.equals(action)) {
            sendLogAsync();
        } else if (PreventIntent.ACTION_UPDATE_CONFIGURATION.equals(action)) {
            handleConfiguration(intent.getBundleExtra(PreventIntent.EXTRA_CONFIGURATION));
        } else if (PreventIntent.ACTION_CHECK_LICENSE.equals(action)) {
            handleCheckLicense(context, intent);
        } else if (PreventIntent.ACTION_SOFT_REBOOT.equals(action)) {
            softReboot();
        } else if (PreventIntent.ACTION_REBOOT.equals(action)) {
            reboot();
        }
    }

    private void reboot() {
        SystemProperties.set("sys.powerctl", "reboot");
    }

    private void softReboot() {
        SystemProperties.set("ctl.restart", "surfaceflinger");
        SystemProperties.set("ctl.restart", "zygote");
    }

    private void handleGetInfo() {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("method", SystemHook.getMethod());
        info.put("version", SystemHook.getVersion());
        info.put("name", BuildConfig.VERSION_NAME);
        info.put("code", BuildConfig.VERSION_CODE);
        Bundle configuration = Configuration.getDefault().getBundle();
        for (String key : configuration.keySet()) {
            info.put(key, configuration.get(key));
        }
        setResultData(new JSONObject(info).toString());
    }

    private void handleConfiguration(Bundle bundle) {
        Configuration configuration = Configuration.getDefault();
        configuration.updateBundle(bundle);
        if (bundle.containsKey(PreventIntent.KEY_PREVENT_LIST)) {
            updatePreventList(bundle.getStringArrayList(PreventIntent.KEY_PREVENT_LIST));
        }
        saveConfiguration(configuration, true);
    }

    private void saveConfiguration(Configuration configuration, boolean force) {
        PreventListUtils.getInstance().save(mContext, configuration, force);
    }

    private void updatePreventList(Collection<String> preventList) {
        if (preventList == null) {
            return;
        }
        PreventLog.i("update prevent: " + preventList.size());
        for (String prevent : preventList) {
            if (!mPreventPackages.containsKey(prevent)) {
                mPreventPackages.put(prevent, true);
            }
        }
        PreventListUtils.getInstance().save(mContext, mPreventPackages.keySet(), true);
    }

    private boolean handleCheckLicense(Context context, Intent intent) {
        String user = intent.getStringExtra(Intent.EXTRA_USER);
        Map<String, Set<String>> users = new LinkedHashMap<String, Set<String>>();
        boolean isEmail = !TextUtils.isEmpty(user) && Patterns.EMAIL_ADDRESS.matcher(user).matches();
        for (Account account : AccountUtils.getEnabledAccounts(context)) {
            Set<String> accounts = users.get(account.type);
            if (accounts == null) {
                accounts = new LinkedHashSet<String>();
                users.put(account.type, accounts);
            }
            accounts.add(account.name);
            if (isValid(account.name, user, isEmail)) {
                setResultCode(0x1);
                return true;
            }
        }
        String number = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
        if (!TextUtils.isEmpty(number) && Patterns.PHONE.matcher(number).matches()) {
            Set<String> numbers = new LinkedHashSet<String>();
            numbers.add(normalizeNumber(number));
            users.put("", numbers);
            if (PackageUtils.equals(number, user)) {
                setResultCode(0x1);
                return true;
            }
        }
        setResultCode(0x0);
        setResultData(users.toString());
        return false;
    }

    private String normalizeNumber(String number) {
        StringBuilder sb = new StringBuilder();
        int len = number.length();
        for (int i = 0; i < len; i++) {
            char c = number.charAt(i);
            int digit = Character.digit(c, RADIX);
            if (digit != -1) {
                sb.append(digit);
            } else if (sb.length() == 0 && c == '+') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isValid(String name, String user, boolean isEmail) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(user)) {
            return false;
        }
        if (name.equalsIgnoreCase(user)) {
            return true;
        }
        // for outlook and other email app
        if (isEmail
                && !Patterns.EMAIL_ADDRESS.matcher(name).matches()
                && name.toLowerCase(Locale.US).startsWith(user.toLowerCase(Locale.US))) {
            return true;
        }
        return false;
    }

    private void handlePackage(Intent intent, String action) {
        String packageName = PackageUtils.getPackageName(intent);
        if (Intent.ACTION_PACKAGE_RESTARTED.equals(action)) {
            handlePackageRestarted("PACKAGE_RESTARTED", packageName);
        } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            onPackageAdded(intent);
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            onPackageRemoved(intent);
        }
    }

    protected void onPackageAdded(Intent intent) {
        String packageName = PackageUtils.getPackageName(intent);
        SafeActionUtils.onPackageChanged(packageName);
        if (BuildConfig.APPLICATION_ID.equals(packageName) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            SystemHook.setSupported(true);
            PreventListUtils.getInstance().save(mContext, mPreventPackages.keySet(), false);
            PreventListUtils.getInstance().save(mContext, Configuration.getDefault(), false);
        } else if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && Configuration.getDefault().isAutoPrevent()) {
            mPreventPackages.put(packageName, true);
            showUpdated(packageName, mPreventPackages.size());
            PreventListUtils.getInstance().save(mContext, mPreventPackages.keySet(), true);
        }
    }

    private String getLabel(PackageManager pm, String packageName) {
        try {
            return pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            PreventLog.d("cannot find application " + packageName, e);
            return packageName;
        }
    }

    private void showUpdated(String packageName, int size) {
        try {
            PackageManager pm = mContext.getPackageManager();
            Resources resources = pm.getResourcesForApplication(BuildConfig.APPLICATION_ID);
            String message = resources.getString(R.string.updated_prevents, size) + "(" + getLabel(pm, packageName) + ")";
            Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
        } catch (PackageManager.NameNotFoundException e) {
            PreventLog.d("cannot find application " + BuildConfig.APPLICATION_ID, e);
        } catch (RuntimeException e) {
            PreventLog.d("cannot show toast", e);
        }
    }

    protected void onPackageRemoved(Intent intent) {
        String packageName = PackageUtils.getPackageName(intent);
        SafeActionUtils.onPackageChanged(packageName);
        onPackageRemoved(packageName);
        if (BuildConfig.APPLICATION_ID.equals(packageName) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            SystemHook.setSupported(false);
            PreventListUtils.getInstance().onRemoved(mContext);
        } else if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            mPreventPackages.remove(packageName);
            PreventListUtils.getInstance().save(mContext, mPreventPackages.keySet(), true);
        }
    }

    private void handleNonScheme(String action) {
        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            onScreenOff();
        } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            onScreenOn();
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            PreventLog.d("boot completed");
            AccountUtils.fetchAccounts(mContext);
            if (!SystemHook.isSupported()) {
                PreventListUtils.notifyNotSupported(mContext);
            } else if (!PreventListUtils.getInstance().canLoad(mContext)) {
                PreventListUtils.notifyNoPrevents(mContext);
            }
        } else if (AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION.equals(action)) {
            PreventLog.d("login accounts changed");
            AccountUtils.fetchAccounts(mContext);
        }
    }

    private void handleGetProcesses(Context context, String action) {
        Map<String, Set<Long>> running = getRunningAppProcesses(context);
        Map<String, Long> leaving = getLeavingPackages();
        for (Map.Entry<String, Long> entry : leaving.entrySet()) {
            String packageName = entry.getKey();
            Set<Long> status = running.get(packageName);
            if (status != null) {
                long elapsed = entry.getValue();
                if (elapsed % 0xa == 0) {
                    elapsed += 1;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hasHighPriority(packageName)) {
                    status.add(-elapsed);
                } else {
                    status.add(elapsed);
                }
            }
        }
        LogUtils.logRequestInfo(action, null, running.size());
        setResultData(toJSON(running));
        abortBroadcast();
    }

    private void handleGetPackages(String action) {
        Map<String, Boolean> preventPackages = new TreeMap<String, Boolean>(mPreventPackages);
        if (!GmsUtils.canStopGms()) {
            for (String packageName : GmsUtils.getGmsPackages()) {
                if (Boolean.TRUE.equals(preventPackages.get(packageName))) {
                    preventPackages.put(packageName, false);
                }
            }
        }
        int size = preventPackages.size();
        setResultCode(size);
        setResultData(new JSONObject(preventPackages).toString());
        LogUtils.logRequestInfo(action, null, size);
        abortBroadcast();
    }

    private void handleUpdatePrevent(String action, Intent intent) {
        String[] packages = intent.getStringArrayExtra(PreventIntent.EXTRA_PACKAGES);
        boolean prevent = intent.getBooleanExtra(PreventIntent.EXTRA_PREVENT, true);
        Map<String, Boolean> prevents = mPreventPackages;
        for (String name : packages) {
            if (prevent) {
                int count = countCounter(name);
                prevents.put(name, count == 0);
            } else {
                prevents.remove(name);
            }
        }
        PreventListUtils.getInstance().save(mContext, mPreventPackages.keySet(), true);
        setResultCode(prevents.size());
        setResultData(new JSONObject(prevents).toString());
        LogUtils.logRequestInfo(action, null, prevents.size());
        abortBroadcast();
    }

    private String toJSON(Map<String, Set<Long>> processes) {
        Map<String, String> results = new HashMap<String, String>();
        for (Map.Entry<String, Set<Long>> entry : processes.entrySet()) {
            results.put(entry.getKey(), convertSet(entry.getValue()));
        }
        return new JSONObject(results).toString();
    }

    private String convertSet(Set<Long> value) {
        StringBuilder sb = new StringBuilder();
        Iterator<Long> it = value.iterator();
        while (it.hasNext()) {
            Long v = it.next();
            if (v != null) {
                sb.append(v);
                if (it.hasNext()) {
                    sb.append(",");
                }
            }
        }
        return sb.toString();
    }

    private Map<String, Set<Long>> getRunningAppProcesses(Context context) {
        Map<String, Set<Long>> running = new HashMap<String, Set<Long>>();
        Set<String> services = getRunningServices(context);
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
        if (processes == null) {
            PreventLog.w("cannot get running processes");
            return running;
        }
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            for (String pkg : process.pkgList) {
                Set<Long> importance = running.get(pkg);
                if (importance == null) {
                    importance = new LinkedHashSet<Long>();
                    running.put(pkg, importance);
                }
                if (process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    importance.add((long) process.importance);
                } else if (services.contains(pkg)) {
                    importance.add((long) process.importance);
                } else {
                    importance.add((long) -process.importance);
                }
            }
        }
        return running;
    }

    private Set<String> getRunningServices(Context context) {
        Set<String> services = new HashSet<String>();
        for (ActivityManager.RunningServiceInfo service : HookUtils.getServices(context)) {
            if (service.started) {
                services.add(service.service.getPackageName());
            }
        }
        return services;
    }

    private void handlePackageRestarted(String action, String packageName) {
        LogUtils.logRequestInfo(action, packageName, -1);
        removePackageCounters(packageName);
        SystemHook.updateRunningGapps(packageName, false);
        if (mPreventPackages.containsKey(packageName)) {
            mPreventPackages.put(packageName, true);
        }
        SystemHook.killNoFather();
    }

    private void sendLogAsync() {
        if (logFuture != null) {
            logFuture.cancel(true);
        }
        logFuture = logExecutor.submit(new Runnable() {
            @Override
            public void run() {
                LogcatUtils.logcat(LogcatUtils.PREVENT, "-s Prevent:v PreventUI:v");
                LogcatUtils.logcat(LogcatUtils.SYSTEM, "ContentResolver:s *:v");
                LogcatUtils.logcat(mContext, LogcatUtils.BOOT);
                LogcatUtils.logcat(mContext, LogcatUtils.PREVENT);
                LogcatUtils.logcat(mContext, LogcatUtils.SYSTEM);
                LogcatUtils.completed(mContext);
            }
        });
    }
}
