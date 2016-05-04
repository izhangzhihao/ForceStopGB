package android.content.pm;

import android.content.ComponentName;

public interface IPackageManager {

    boolean isProtectedBroadcast(String actionName);

    ServiceInfo getServiceInfo(ComponentName className, int flags);

    ServiceInfo getServiceInfo(ComponentName className, int flags, int userId);

}
