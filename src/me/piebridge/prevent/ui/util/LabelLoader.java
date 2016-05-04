package me.piebridge.prevent.ui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Created by thom on 16/1/28.
 */
public class LabelLoader {

    private static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(0x2);

    private final SharedPreferences mSp;

    private final PackageManager mPm;

    public LabelLoader(Context context) {
        mSp = context.getSharedPreferences("label-" + Locale.getDefault(), Context.MODE_PRIVATE);
        mPm = context.getPackageManager();
    }

    public String loadLabel(ApplicationInfo info) {
        String packageName = info.packageName;
        if (mSp.contains(packageName)) {
            loadLabelIfNeeded(info);
            return mSp.getString(packageName, packageName);
        } else {
            String label = StringUtils.trim(info.loadLabel(mPm)).toString();
            mSp.edit().putString(packageName, label).apply();
            return label;
        }
    }

    private void loadLabelIfNeeded(final ApplicationInfo info) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                String packageName = info.packageName;
                String label = StringUtils.trim(info.loadLabel(mPm)).toString();
                if (!label.equals(mSp.getString(packageName, packageName))) {
                    mSp.edit().putString(packageName, label).apply();
                }
            }
        });
    }

}
