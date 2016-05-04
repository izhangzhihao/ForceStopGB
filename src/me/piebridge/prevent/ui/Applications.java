package me.piebridge.prevent.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.HashSet;
import java.util.Set;

import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.common.PackageUtils;
import me.piebridge.prevent.ui.util.LicenseUtils;

/**
 * Created by thom on 16/2/17.
 */
public class Applications extends PreventFragment {

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LicenseUtils.validLicense(getActivity(), false, null);
    }

    @Override
    protected Set<String> getPackageNames(PreventActivity activity) {
        Set<String> names = new HashSet<String>();
        PackageManager pm = activity.getPackageManager();
        for (PackageInfo pkgInfo : pm.getInstalledPackages(0)) {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
            if (PackageUtils.canPrevent(pm, appInfo)) {
                names.add(appInfo.packageName);
            }
        }
        return names;
    }

    @Override
    protected int getQueryHint() {
        return R.string.query_hint_system;
    }

    @Override
    protected String getDefaultQuery() {
        return "-3g";
    }

    @Override
    protected boolean canSelectAll() {
        return false;
    }

    @Override
    protected boolean showRunning() {
        return true;
    }

}