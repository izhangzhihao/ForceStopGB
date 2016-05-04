package android.app;

import android.content.res.Configuration;
import android.os.IBinder;
import android.os.RemoteException;

/**
  * @hide
  */
public class ActivityThread {

    public static Application currentApplication() {
        throw new UnsupportedOperationException();
    }

    public ApplicationThread getApplicationThread() {
        throw new UnsupportedOperationException();
    }

    private abstract class ApplicationThread implements IApplicationThread {
    }

}
