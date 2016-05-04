package android.app;

import java.util.List;

import android.content.ComponentName;

public class ActivityManager {

    /**
     * @hide
     */
    public void forceStopPackage(String packageName) {
    }

    public List<RunningServiceInfo> getRunningServices(int maxNum) throws SecurityException {
        throw new UnsupportedOperationException();
    }

    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        throw new UnsupportedOperationException();
    }

    public static class RunningAppProcessInfo {
    }

    public static class RunningServiceInfo {
    }
}
