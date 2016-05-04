package me.piebridge.prevent.ui;

import java.util.Set;
import java.util.TreeSet;

import me.piebridge.forcestopgb.R;

/**
 * Created by thom on 16/2/17.
 */
public class PreventList extends PreventFragment {

    @Override
    protected Set<String> getPackageNames(PreventActivity activity) {
        return new TreeSet<String>(activity.getPreventPackages().keySet());
    }

    @Override
    protected int getQueryHint() {
        return R.string.query_hint;
    }

    @Override
    protected String getDefaultQuery() {
        return null;
    }

    @Override
    protected boolean canSelectAll() {
        return true;
    }

}