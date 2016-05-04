package me.piebridge.prevent.xposed;

import android.app.ActivityThread;
import android.app.IApplicationThread;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.framework.PreventLog;
import me.piebridge.prevent.framework.PreventRunning;
import me.piebridge.prevent.framework.util.ActivityRecordUtils;
import me.piebridge.prevent.framework.util.ProcessRecordUtils;
import me.piebridge.prevent.framework.util.ReflectUtils;
import me.piebridge.prevent.framework.util.TaskRecordUtils;

/**
 * Created by thom on 15/9/19.
 */
public class SystemServiceHook extends XC_MethodHook {

    private static Context mContext;

    private static PreventRunning preventRunning;

    private static boolean systemHooked;

    private static Method getRecordForAppLocked;

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        if (systemHooked) {
            return;
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Class.forName("me.piebridge.PreventRunning", false, classLoader);
            PreventLog.d("find PreventRunning in current thread class loader, disable xposed way");
            systemHooked = true;
        } catch (ClassNotFoundException e) { // NOSONAR
            // do nothing
        }
        if (!systemHooked) {
            PreventLog.d("start prevent hook (system)");
            preventRunning = new PreventRunning();
            preventRunning.setVersion(XposedBridge.XPOSED_BRIDGE_VERSION);
            preventRunning.setMethod("xposed");
            hookActivityManagerService(classLoader);
            hookActivity(classLoader);
            hookIntentFilter(classLoader);
            hookIntentIfNeeded(classLoader);
            PreventLog.d("finish prevent hook (system)");
            systemHooked = true;
            hookIntentResolver(classLoader);
        }
    }

    private void hookIntentResolver(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.android.server.IntentResolver", classLoader, "sortResults", List.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.hasThrowable()) {
                    PreventLog.e("ignore order in sortResults", param.getThrowable());
                    param.setThrowable(null);
                }
            }
        });
        PreventLog.d("hooked com.android.server.IntentResolver.sortResults");
    }

    private void hookIntentIfNeeded(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            XposedHelpers.findAndHookMethod("android.content.Intent", classLoader,
                    "isExcludingStopped",
                    new IntentExcludingStoppedHook());
        }
    }

    private void hookIntentFilter(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("android.content.IntentFilter", classLoader, "match",
                String.class, String.class, String.class, Uri.class, Set.class, String.class,
                new IntentFilterMatchHook());
    }

    private void hookActivityManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> activityManagerService = Class.forName("com.android.server.am.ActivityManagerService", false, classLoader);

        hookActivityManagerServiceStartProcessLocked(activityManagerService);

        hookActivityManagerServiceBroadcastIntent(activityManagerService, classLoader);

        hookActivityManagerServiceStartService(activityManagerService);

        hookActivityManagerServiceBindService(activityManagerService, classLoader);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            hookActivityManagerServiceCleanUpRemovedTaskLocked(activityManagerService, classLoader);
        }

        hookActivityManagerServiceStartActivity(activityManagerService, classLoader);

        hookActivityManagerServiceMoveActivityTaskToBack(activityManagerService);

        hookActivityManagerServiceHandleAppDiedLocked(activityManagerService, classLoader);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            XposedHelpers.findAndHookMethod(activityManagerService, "addPackageDependency", String.class, XC_MethodReplacement.DO_NOTHING);
        }

        getRecordForAppLocked = activityManagerService.getDeclaredMethod("getRecordForAppLocked", IApplicationThread.class);
        getRecordForAppLocked.setAccessible(true);
    }

    private static void logLinkageError(String method, LinkageError e) {
        PreventLog.d("cannot hook " + method, e);
    }

    private void hookActivityManagerServiceHandleAppDiedLocked(Class<?> activityManagerService, ClassLoader classLoader) throws ClassNotFoundException {
        int sdk = Build.VERSION.SDK_INT;
        String method = "handleAppDiedLocked";
        XC_MethodHook hook = new AppDiedHook();
        try {
            hookMethods(activityManagerService, method, hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            Class<?> processRecord = Class.forName("com.android.server.am.ProcessRecord", false, classLoader);
            if (sdk >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                // sdk 14, sdk 15, sdk 16, sdk 17, sdk 18, sdk 19, sdk 21, sdk 22, sdk 23
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        processRecord, boolean.class, boolean.class,
                        hook);
            } else {
                // sdk 10
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        processRecord, boolean.class,
                        hook);
            }
        }

    }

    private void hookActivityManagerServiceMoveActivityTaskToBack(Class<?> activityManagerService) {
        // for move activity to back
        String method = "moveActivityTaskToBack";
        XC_MethodHook hook = new BackActivityHook();
        try {
            hookMethods(activityManagerService, method, hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            // sdk 10, sdk 14, sdk 15, sdk 16, sdk 17, sdk 18, sdk 19, sdk 21, sdk 22, sdk 23
            XposedHelpers.findAndHookMethod(activityManagerService, method,
                    IBinder.class, boolean.class,
                    hook);
        }
    }

    private void hookActivityManagerServiceStartActivity(Class<?> activityManagerService, ClassLoader classLoader) throws ClassNotFoundException {
        // for start home activity
        int sdk = Build.VERSION.SDK_INT;
        String method = "startActivity";
        XC_MethodHook hook = new HomeActivityHook();
        try {
            hookMethods(activityManagerService, method, hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            if (sdk >= Build.VERSION_CODES.LOLLIPOP) {
                // sdk 21, sdk 22, sdk 23
                Class<?> profilerInfo = Class.forName("android.app.ProfilerInfo", false, classLoader);
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, String.class, Intent.class, String.class, IBinder.class, String.class, int.class, int.class, profilerInfo, Bundle.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // sdk 18, sdk 19
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, String.class, Intent.class, String.class, IBinder.class, String.class, int.class, int.class, String.class, ParcelFileDescriptor.class, Bundle.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN) {
                // sdk 16, sdk 17
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, IBinder.class, String.class, int.class, int.class, String.class, ParcelFileDescriptor.class, Bundle.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                // sdk 14, sdk 15
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, Uri[].class, int.class, IBinder.class, String.class, int.class, boolean.class, boolean.class, String.class, ParcelFileDescriptor.class, boolean.class,
                        hook);
            } else {
                // sdk 10
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, Uri[].class, int.class, IBinder.class, String.class, int.class, boolean.class, boolean.class,
                        hook);
            }
        }
    }

    private void hookActivityManagerServiceCleanUpRemovedTaskLocked(Class<?> activityManagerService, ClassLoader classLoader) throws ClassNotFoundException {
        int sdk = Build.VERSION.SDK_INT;
        String method = "cleanUpRemovedTaskLocked";
        XC_MethodHook hook = new CleanUpRemovedHook();
        try {
            hookMethods(activityManagerService, method, hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            if (sdk >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // sdk 22, sdk 23
                Class<?> taskRecord = Class.forName("com.android.server.am.TaskRecord", false, classLoader);
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        taskRecord, boolean.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN) {
                // sdk 16, sdk 17, sdk 18, sdk 19, sdk 21
                Class<?> taskRecord = Class.forName("com.android.server.am.TaskRecord", false, classLoader);
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        taskRecord, int.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                // sdk 14, sdk 15
                Class<?> activityRecord = Class.forName("com.android.server.am.ActivityRecord", false, classLoader);
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        activityRecord, boolean.class,
                        hook);
            }
        }
    }

    private void hookActivityManagerServiceBindService(Class<?> activityManagerService, ClassLoader classLoader) throws ClassNotFoundException {
        int sdk = Build.VERSION.SDK_INT;
        String method = "bindService";
        XC_MethodHook hook = new ContextHook();
        try {
            hookMethods(activityManagerService, method, hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            Class<?> iServiceConnection = Class.forName("android.app.IServiceConnection", false, classLoader);
            if (sdk >= Build.VERSION_CODES.M) {
                // sdk 23
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, IBinder.class, Intent.class, String.class, iServiceConnection, int.class, String.class, int.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN) {
                // sdk 16, sdk 17, sdk 18, sdk 19, sdk 21, sdk 22
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, IBinder.class, Intent.class, String.class, iServiceConnection, int.class, int.class,
                        hook);
            } else {
                // sdk 10, sdk 14, sdk 15
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, IBinder.class, Intent.class, String.class, iServiceConnection, int.class,
                        hook);
            }
        }
    }

    private void hookActivityManagerServiceStartService(Class<?> activityManagerService) {
        int sdk = Build.VERSION.SDK_INT;
        String method = "startService";
        XC_MethodHook hook = new StartServiceContextHook();
        try {
            hookMethods(activityManagerService, method, hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            if (sdk >= Build.VERSION_CODES.M) {
                // sdk 23
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, String.class, int.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // sdk 18, sdk 19, sdk 21, sdk 22
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, int.class,
                        hook);
            } else {
                // sdk 10, sdk 14, sdk 15, sdk 16, sdk 17
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class,
                        hook);
            }
        }
    }

    private void hookActivityManagerServiceBroadcastIntent(Class<?> activityManagerService, ClassLoader classLoader) throws ClassNotFoundException {
        int sdk = Build.VERSION.SDK_INT;
        String method = "broadcastIntent";
        XC_MethodHook hook = new BroadcastIntentContextHook();
        try {
            hookMethods(activityManagerService, method, hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            Class<?> iIntentReceiver = Class.forName("android.content.IIntentReceiver", false, classLoader);
            if (sdk >= Build.VERSION_CODES.M) {
                // sdk 23
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, iIntentReceiver, int.class, String.class, Bundle.class, String[].class, int.class, Bundle.class, boolean.class, boolean.class, int.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // sdk 18, sdk 19, sdk 21, sdk 22
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, iIntentReceiver, int.class, String.class, Bundle.class, String.class, int.class, boolean.class, boolean.class, int.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN) {
                // sdk 16, sdk 17
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, iIntentReceiver, int.class, String.class, Bundle.class, String.class, boolean.class, boolean.class, int.class,
                        hook);
            } else {
                // sdk 10, sdk 14, sdk 15
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        IApplicationThread.class, Intent.class, String.class, iIntentReceiver, int.class, String.class, Bundle.class, String.class, boolean.class, boolean.class,
                        hook);
            }
        }
    }

    private void hookActivityManagerServiceStartProcessLocked(Class<?> activityManagerService) {
        int sdk = Build.VERSION.SDK_INT;
        String method = "startProcessLocked";
        XC_MethodHook hook = new ProcessHook();
        try {
            hookMethods(activityManagerService, method, "ProcessRecord", hook);
        } catch (LinkageError e) {
            logLinkageError(method, e);
            if (sdk >= Build.VERSION_CODES.LOLLIPOP) {
                // sdk 21, sdk 22, sdk 23
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        String.class, ApplicationInfo.class, boolean.class, int.class, String.class, ComponentName.class, boolean.class, boolean.class, int.class, boolean.class, String.class, String.class, String[].class, Runnable.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.KITKAT) {
                // sdk 19, sdk 20
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        String.class, ApplicationInfo.class, boolean.class, int.class, String.class, ComponentName.class, boolean.class, boolean.class, boolean.class,
                        hook);
            } else if (sdk >= Build.VERSION_CODES.JELLY_BEAN) {
                // sdk 16, sdk 17, sdk 18
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        String.class, ApplicationInfo.class, boolean.class, int.class, String.class, ComponentName.class, boolean.class, boolean.class,
                        hook);
            } else {
                // sdk 10, 14, 15
                XposedHelpers.findAndHookMethod(activityManagerService, method,
                        String.class, ApplicationInfo.class, boolean.class, int.class, String.class, ComponentName.class, boolean.class,
                        hook);
            }
        }
    }

    private static Collection<Method> findMethods(Class<?> hookClass, String methodName, String returnName) {
        Collection<Method> methods = new ArrayList<Method>();
        for (Method method : hookClass.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            String returnType = method.getReturnType().getSimpleName();
            if (returnName == null || returnName.equals(returnType)) {
                PreventLog.v("found " + hookClass.getSimpleName() + "." + methodName + ": " + method);
                methods.add(method);
            }
        }
        return methods;
    }

    private static void hookMethods(Class<?> hookClass, String methodName, XC_MethodHook hook) {
        hookMethods(hookClass, methodName, null, hook);
    }

    private static void hookMethods(Class<?> hookClass, String methodName, String returnName, XC_MethodHook hook) {
        Collection<Method> methods = findMethods(hookClass, methodName, returnName);
        if (methods.isEmpty()) {
            PreventLog.e("cannot find " + hookClass.getSimpleName() + "." + methodName);
        }
        for (Method method : methods) {
            XposedBridge.hookMethod(method, hook);
            PreventLog.d("hooked " + hookClass.getSimpleName() + "." + methodName);
        }
    }

    private static Object getRecordForAppLocked(Object activityManagerService, Object thread) {
        if (getRecordForAppLocked == null) {
            return null;
        }
        try {
            return getRecordForAppLocked.invoke(activityManagerService, thread);
        } catch (IllegalAccessException e) {
            PreventLog.d("cannot access getRecordForAppLocked", e);
        } catch (InvocationTargetException e) {
            PreventLog.d("cannot invoke getRecordForAppLocked", e);
        }
        return null;
    }

    private static void hookActivity(ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> applicationThread = Class.forName("android.app.ApplicationThreadProxy", false, classLoader);
        hookMethods(applicationThread, "scheduleLaunchActivity", new LaunchActivityHook());

        hookMethods(applicationThread, "scheduleResumeActivity", new ResumeActivityHook());

        hookMethods(applicationThread, "schedulePauseActivity", new PauseActivityHook());

        hookMethods(applicationThread, "scheduleDestroyActivity", new DestroyActivityHook());
    }

    public static class AppDiedHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            Boolean restarting = (Boolean) param.args[0x1];
            Object processRecord = param.args[0];
            if (!restarting && !ProcessRecordUtils.isKilledByAm(processRecord)) {
                preventRunning.onAppDied(processRecord);
            }
        }
    }

    public static class BackActivityHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Boolean result = (Boolean) param.getResult();
            if (result != null && result) {
                Object activityRecord = ActivityRecordUtils.getActivityRecord(param.args[0x0]);
                preventRunning.onMoveActivityTaskToBack(ActivityRecordUtils.getPackageName(activityRecord));
            }
        }
    }

    public static class HomeActivityHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Integer result = (Integer) param.getResult();
            if (result == null || result < 0) {
                return;
            }
            Intent intent = null;
            for (Object arg : param.args) {
                if (arg instanceof Intent) {
                    intent = (Intent) arg;
                    break;
                }
            }
            Object processRecord = getRecordForAppLocked(param.thisObject, param.args[0]);
            ApplicationInfo info = ProcessRecordUtils.getInfo(processRecord);
            if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME) && info != null) {
                preventRunning.onStartHomeActivity(info.packageName);
            }
        }
    }

    public static class ContextHook extends XC_MethodHook {

        @Override
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            Object processRecord = getRecordForAppLocked(param.thisObject, param.args[0]);
            ApplicationInfo info = ProcessRecordUtils.getInfo(processRecord);
            String sender = info == null ? "" : info.packageName;
            if (sender == null) {
                sender = String.valueOf(Binder.getCallingUid());
            }
            preventRunning.setSender(sender);
        }

        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            preventRunning.setSender(null);
        }
    }

    public static class StartServiceContextHook extends ContextHook {

        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            ComponentName cn = (ComponentName) param.getResult();
            if (cn != null && cn.getPackageName().startsWith("!")) {
                param.setResult(null);
            }
        }

    }

    public static class BroadcastIntentContextHook extends ContextHook {

        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            Intent intent = (Intent) param.args[0x1];
            Integer result = (Integer) param.getResult();
            if (result != null && result >= 0 && intent != null) {
                preventRunning.onBroadcastIntent(intent);
            }
        }
    }

    public static class ProcessHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            ApplicationInfo info = (ApplicationInfo) param.args[0x1];
            String hostingType = (String) param.args[0x4];
            ComponentName hostingName = (ComponentName) param.args[0x5];
            if (mContext == null) {
                mContext = getRegisterContext(param.thisObject);
            }
            if (!preventRunning.hookStartProcessLocked(mContext, info, hostingType, hostingName)) {
                param.setResult(null);
            }
        }

        private Context getRegisterContext(Object activityManagerService) {
            Context context = getContext(activityManagerService);
            if (context != null && checkRegisterContext(context)) {
                return context;
            }
            context = ActivityThread.currentApplication();
            if (context != null && checkRegisterContext(context)) {
                return context;
            }
            return null;
        }

        private boolean checkRegisterContext(Context context) {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // do nothing
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(PreventIntent.ACTION_GET_PACKAGES);
            filter.addDataScheme(PreventIntent.SCHEME);
            try {
                context.registerReceiver(receiver, filter);
                context.unregisterReceiver(receiver);
                return true;
            } catch (SecurityException e) { // NOSONAR
                PreventLog.d("cannot register: " + e.getMessage());
                return false;
            }
        }

        private Context getContext(Object activityManagerService) {
            Field field = ReflectUtils.getDeclaredField(activityManagerService, "mContext");
            if (field != null) {
                try {
                    return (Context) field.get(activityManagerService);
                } catch (IllegalAccessException e) {
                    PreventLog.d("cannot visit mContext in " + activityManagerService.getClass().getName(), e);
                }
            }
            return null;
        }
    }

    public static class IntentExcludingStoppedHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            Boolean result = (Boolean) param.getResult();
            String action = ((Intent) param.thisObject).getAction();
            if (result != null && result && !preventRunning.isExcludingStopped(action)) {
                param.setResult(false);
            }
        }
    }

    public static class IntentFilterMatchHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Integer result = (Integer) param.getResult();
            if (result == null || result < 0) {
                return;
            }
            String action = (String) param.args[0x0];
            String type = (String) param.args[0x1];
            String scheme = (String) param.args[0x2];
            Uri data = (Uri) param.args[0x3];
            @SuppressWarnings("unchecked")
            Set<String> categories = (Set<String>) param.args[0x4];
            int match = preventRunning.match(result, param.thisObject, action, type, scheme, data, categories);
            if (match != result) {
                param.setResult(match);
            }
        }
    }

    public static class CleanUpRemovedHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (shouldKillProcess(param.args[1])) {
                String packageName = TaskRecordUtils.getPackageName(param.args[0]);
                preventRunning.onCleanUpRemovedTask(packageName);
            }
        }

        private boolean shouldKillProcess(Object killProcess) {
            if (killProcess == null) {
                return false;
            }
            if (killProcess instanceof Boolean) {
                return (Boolean) killProcess;
            } else if (killProcess instanceof Integer) {
                Integer flags = (Integer) killProcess;
                return (flags & 0x1) != 0;
            }
            return false;
        }
    }

    public static class LaunchActivityHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object activityRecord = ActivityRecordUtils.getActivityRecord(param.args[0x1]);
            preventRunning.onLaunchActivity(activityRecord);
        }
    }

    public static class ResumeActivityHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object activityRecord = ActivityRecordUtils.getActivityRecord(param.args[0x0]);
            preventRunning.onResumeActivity(activityRecord);
        }
    }

    public static class PauseActivityHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object activityRecord = ActivityRecordUtils.getActivityRecord(param.args[0x0]);
            Boolean userLeaving = (Boolean) param.args[0x2];
            if (userLeaving) {
                preventRunning.onUserLeavingActivity(activityRecord);
            }
        }
    }

    public static class DestroyActivityHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object activityRecord = ActivityRecordUtils.getActivityRecord(param.args[0x0]);
            preventRunning.onDestroyActivity(activityRecord);
        }
    }

}
