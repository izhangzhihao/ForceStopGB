package me.piebridge.prevent.xposed;

import android.app.ActivityThread;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;

public class XposedMod implements IXposedHookZygoteInit {

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        XposedBridge.hookAllMethods(ActivityThread.class, "systemMain", new SystemServiceHook());
    }

}
