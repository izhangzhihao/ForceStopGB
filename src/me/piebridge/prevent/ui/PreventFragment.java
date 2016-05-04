package me.piebridge.prevent.ui;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ListFragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.common.GmsUtils;
import me.piebridge.prevent.common.PackageUtils;
import me.piebridge.prevent.ui.util.LabelLoader;
import me.piebridge.prevent.ui.util.LicenseUtils;
import me.piebridge.prevent.ui.util.StatusUtils;

public abstract class PreventFragment extends ListFragment implements AbsListView.OnScrollListener {

    private Adapter mAdapter;
    private PreventActivity mActivity;
    private Set<String> prevNames = null;
    private View filter;
    private CheckBox check;
    private EditText search;
    private int headerIconWidth;
    private static final int HEADER_ICON_WIDTH = 48;
    private static Map<String, Position> positions = new HashMap<String, Position>();

    private boolean scrolling;
    private static boolean appNotification;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(getListView());
        mActivity = (PreventActivity) getActivity();
        if (mActivity != null) {
            appNotification = PreferenceManager.getDefaultSharedPreferences(mActivity).getBoolean("app_notification", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
            setNewAdapterIfNeeded(mActivity, true);
        }
    }

    @Override
    public void onDestroyView() {
        saveListPosition();
        super.onDestroyView();
        mActivity = null;
        setListAdapter(null);
    }

    private void selectAll(boolean checked) {
        if (mActivity != null && mAdapter != null) {
            Set<String> selections = mActivity.getSelection();
            if (checked) {
                selections.addAll(mAdapter.getAllPreventablePackages());
            } else {
                selections.clear();
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    public void setChecked(boolean checked) {
        if (check != null) {
            check.setChecked(checked);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list, container, false);
        filter = view.findViewById(R.id.filter);
        check = (CheckBox) filter.findViewById(R.id.filter_check);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll(check.isChecked());
            }
        });
        search = (EditText) filter.findViewById(R.id.filter_query);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
                // do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                if (mAdapter != null) {
                    mAdapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // do nothing
            }
        });
        search.setHint(getQueryHint());
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnScrollListener(this);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        scrolling = scrollState != SCROLL_STATE_IDLE;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        l.showContextMenuForChild(v);
    }

    @Override
    public void onPause() {
        saveListPosition();
        super.onPause();
    }

    private int getHeaderIconWidth() {
        if (headerIconWidth == 0) {
            headerIconWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HEADER_ICON_WIDTH, getResources().getDisplayMetrics());
        }
        return headerIconWidth;
    }

    private boolean canCreateContextMenu(ContextMenu menu, ContextMenuInfo menuInfo) {
        return mActivity != null && menu != null && menuInfo != null;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (!canCreateContextMenu(menu, menuInfo)) {
            return;
        }
        menu.clear();
        ViewHolder holder = (ViewHolder) ((AdapterContextMenuInfo) menuInfo).targetView.getTag();
        menu.setHeaderTitle(holder.nameView.getText());
        if (holder.icon != null) {
            setHeaderIcon(menu, holder.icon);
        }
        menu.add(Menu.NONE, R.string.app_info, Menu.NONE, R.string.app_info);
        if (holder.checkView.isEnabled() || canPreventAll()) {
            updatePreventMenu(menu, holder.packageName);
        }
        if (getMainIntent(holder.packageName) != null) {
            menu.add(Menu.NONE, R.string.open, Menu.NONE, R.string.open);
        }
        if (holder.canUninstall) {
            menu.add(Menu.NONE, R.string.uninstall, Menu.NONE, R.string.uninstall);
        }
        if (appNotification) {
            menu.add(Menu.NONE, R.string.app_notifications, Menu.NONE, R.string.app_notifications);
        }
    }

    private boolean canPreventAll() {
        if (BuildConfig.DONATE) {
            String licenseName = LicenseUtils.getRawLicenseName(mActivity);
            return licenseName != null && licenseName.startsWith("PA");
        } else {
            return true;
        }
    }

    private void updatePreventMenu(Menu menu, String packageName) {
        if (mActivity.getPreventPackages().containsKey(packageName)) {
            menu.add(Menu.NONE, R.string.remove, Menu.NONE, R.string.remove);
        } else {
            menu.add(Menu.NONE, R.string.prevent, Menu.NONE, R.string.prevent);
        }
    }

    private void setHeaderIcon(ContextMenu menu, Drawable icon) {
        int width = getHeaderIconWidth();
        if (icon.getMinimumWidth() <= width) {
            menu.setHeaderIcon(icon);
        } else if (icon instanceof BitmapDrawable) {
            Bitmap bitmap = Bitmap.createScaledBitmap(((BitmapDrawable) icon).getBitmap(), width, width, false);
            menu.setHeaderIcon(new BitmapDrawable(getResources(), bitmap));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mActivity == null || item == null) {
            return false;
        }
        ViewHolder holder = (ViewHolder) ((AdapterContextMenuInfo) item.getMenuInfo()).targetView.getTag();
        return onContextItemSelected(holder, holder.packageName, item.getItemId());
    }

    private boolean onContextItemSelected(ViewHolder holder, String packageName, int id) {
        if (id == R.string.app_info || id == R.string.uninstall) {
            startActivity(id, packageName);
        } else if (id == R.string.app_notifications) {
            startNotification(packageName);
        } else if (id == R.string.remove || id == R.string.prevent) {
            updatePrevent(id, holder, packageName);
        } else if (id == R.string.open) {
            startPackage(packageName);
        }
        return true;
    }

    private boolean startNotification(String packageName) {
        ApplicationInfo info;
        try {
            info = mActivity.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            UILog.d("cannot find package " + packageName, e);
            return false;
        }
        int uid = info.uid;
        Intent intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("app_package", packageName)
                .putExtra("app_uid", uid);
        try {
            mActivity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            appNotification = false;
            PreferenceManager.getDefaultSharedPreferences(mActivity).edit().putBoolean("app_notification", false).apply();
            UILog.d("cannot start notification for " + packageName, e);
            return false;
        }
    }

    private boolean startActivity(int id, String packageName) {
        String action;
        if (id == R.string.app_info) {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
        } else if (id == R.string.uninstall) {
            action = Intent.ACTION_DELETE;
        } else {
            return false;
        }
        mActivity.startActivity(new Intent(action, Uri.fromParts("package", packageName, null)));
        return true;
    }

    private boolean startPackage(String packageName) {
        Intent intent = getMainIntent(packageName);
        if (intent != null) {
            mActivity.startActivity(intent);
        }
        return true;
    }

    private boolean updatePrevent(int id, ViewHolder holder, String packageName) {
        if (id == R.string.prevent) {
            holder.preventView.setVisibility(View.VISIBLE);
            holder.preventView.setImageResource(StatusUtils.getDrawable(holder.running, false));
            mActivity.changePrevent(packageName, true);
        } else if (id == R.string.remove) {
            holder.preventView.setVisibility(View.GONE);
            mActivity.changePrevent(packageName, false);
        }
        return true;
    }

    private Intent getMainIntent(String packageName) {
        return mActivity.getPackageManager().getLaunchIntentForPackage(packageName);
    }

    public void refresh(boolean force) {
        if (mActivity != null) {
            setNewAdapterIfNeeded(mActivity, force);
            if (mActivity.getSelection().isEmpty()) {
                check.setChecked(false);
            }
        }
    }

    protected abstract Set<String> getPackageNames(PreventActivity activity);

    protected abstract int getQueryHint();

    protected abstract String getDefaultQuery();

    protected abstract boolean canSelectAll();

    protected boolean showRunning() {
        return false;
    }

    public void saveListPosition() {
        if (mAdapter != null) {
            ListView l = getListView();
            int position = l.getFirstVisiblePosition();
            View v = l.getChildAt(0);
            int top = (v == null) ? 0 : v.getTop();
            setListPosition(new Position(position, top));
        }
    }

    private void setListPosition(Position position) {
        positions.put(getClass().getName(), position);
    }

    private Position getListPosition() {
        return positions.get(getClass().getName());
    }

    private void setNewAdapterIfNeeded(PreventActivity activity, boolean force) {
        Set<String> names;
        if (force || prevNames == null) {
            names = getPackageNames(activity);
        } else {
            names = prevNames;
        }
        if (force || mAdapter == null || !names.equals(prevNames)) {
            if (mAdapter != null) {
                setListAdapter(null);
            }
            mAdapter = new Adapter(activity, names, filter, showRunning());
            setListAdapter(mAdapter);
            if (prevNames == null) {
                prevNames = new HashSet<String>();
            }
            prevNames.clear();
            prevNames.addAll(names);
        } else {
            mAdapter.notifyDataSetChanged();
            Position position = getListPosition();
            if (position != null) {
                getListView().setSelectionFromTop(position.pos, position.top);
            }
        }
    }

    public void startTaskIfNeeded() {
        mAdapter.startTaskIfNeeded();
    }

    public void updateTimeIfNeeded(String packageName) {
        if (scrolling || mAdapter == null) {
            return;
        }
        ListView l = getListView();
        int size = mAdapter.getCount();
        for (int i = 0; i < size; ++i) {
            View view = l.getChildAt(i);
            if (view == null || view.getTag() == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            ViewHolder holder = (ViewHolder) view.getTag();
            if (PackageUtils.equals(packageName, holder.packageName)) {
                holder.updatePreventView(mActivity);
                holder.running = mActivity.getRunningProcesses().get(packageName);
                holder.summaryView.setText(StatusUtils.formatRunning(mActivity, holder.running));
            } else if (holder.running != null) {
                holder.summaryView.setText(StatusUtils.formatRunning(mActivity, holder.running));
            }
        }
    }

    public void notifyDataSetChanged() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private static class Position {
        int pos;
        int top;

        public Position(int pos, int top) {
            this.pos = pos;
            this.top = top;
        }
    }

    private static class AppInfo implements Comparable<AppInfo> {
        int flags;
        String name = "";
        String packageName;
        Set<Long> running;

        public AppInfo(String packageName, String name, Set<Long> running) {
            super();
            this.packageName = packageName;
            if (name != null) {
                this.name = name;
            }
            this.running = running;
        }

        public AppInfo setFlags(int flags) {
            this.flags = flags;
            return this;
        }

        public boolean isSystem() {
            return PackageUtils.isSystemPackage(this.flags);
        }

        @Override
        public String toString() {
            return (running == null ? "1" : "0") + (isSystem() ? "1" : "0") + "/" + name + "/" + packageName;
        }

        @Override
        public int compareTo(AppInfo another) {
            return Collator.getInstance().compare(toString(), another.toString());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AppInfo && compareTo((AppInfo) obj) == 0;
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }

    private static class ViewHolder {
        String packageName;
        CheckBox checkView;
        ImageView iconView;
        TextView nameView;
        TextView summaryView;
        TextView loadingView;
        ImageView preventView;
        Drawable icon;
        Set<Long> running;
        RetrieveIconTask task;
        boolean canUninstall;

        public void updatePreventView(PreventActivity activity) {
            Boolean result = activity.getPreventPackages().get(packageName);
            if (result == null) {
                preventView.setVisibility(View.INVISIBLE);
            } else {
                preventView.setVisibility(View.VISIBLE);
                running = activity.getRunningProcesses().get(packageName);
                preventView.setImageResource(StatusUtils.getDrawable(running, result));
            }
        }
    }

    private class Adapter extends ArrayAdapter<AppInfo> {
        private final PackageManager mPm;
        private final LayoutInflater inflater;
        private final PreventActivity mActivity;
        private final CompoundButton.OnCheckedChangeListener mListener;

        private List<AppInfo> mAppInfos = new ArrayList<AppInfo>();
        private Set<String> mNames = new HashSet<String>();
        private Set<String> mCanPreventNames = new HashSet<String>();
        private Set<String> mFiltered;
        private Filter mFilter;
        private View mView;
        private RetrieveInfoTask mTask;

        public Adapter(PreventActivity activity) {
            super(activity, R.layout.item);
            mActivity = activity;
            mPm = mActivity.getPackageManager();
            inflater = LayoutInflater.from(activity);
            mListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    ViewHolder holder = (ViewHolder) buttonView.getTag();
                    Set<String> selections = mActivity.getSelection();
                    if (isChecked) {
                        selections.add(holder.packageName);
                    } else {
                        selections.remove(holder.packageName);
                    }
                    mActivity.checkSelection();
                }
            };
        }

        public Adapter(final PreventActivity activity, Set<String> names, View view, boolean showRunning) {
            this(activity);
            mView = view;
            mNames.addAll(names);
            mTask = new RetrieveInfoTask();
            mCanPreventNames.addAll(names);
            if (showRunning) {
                mNames.addAll(mActivity.getRunningProcesses().keySet());
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.item, parent, false);
                ViewHolder viewHolder = new ViewHolder();
                viewHolder.checkView = (CheckBox) view.findViewById(R.id.check);
                viewHolder.iconView = (ImageView) view.findViewById(R.id.icon);
                viewHolder.nameView = (TextView) view.findViewById(R.id.name);
                viewHolder.summaryView = (TextView) view.findViewById(R.id.summary);
                viewHolder.loadingView = (TextView) view.findViewById(R.id.loading);
                viewHolder.preventView = (ImageView) view.findViewById(R.id.prevent);
                viewHolder.checkView.setOnCheckedChangeListener(mListener);
                viewHolder.checkView.setTag(viewHolder);
                view.setTag(viewHolder);
            }

            ViewHolder holder = (ViewHolder) view.getTag();
            AppInfo appInfo = getItem(position);
            holder.nameView.setText(appInfo.name);
            if (!PackageUtils.equals(holder.packageName, appInfo.packageName)) {
                holder.summaryView.setVisibility(View.GONE);
                holder.iconView.setImageDrawable(mPm.getDefaultActivityIcon());
                holder.loadingView.setVisibility(View.VISIBLE);
            }
            holder.packageName = appInfo.packageName;
            holder.checkView.setEnabled(mCanPreventNames.contains(holder.packageName));
            holder.checkView.setChecked(mActivity.getSelection().contains(holder.packageName));
            if (appInfo.isSystem()) {
                view.setBackgroundColor(mActivity.getDangerousColor());
            } else {
                view.setBackgroundColor(mActivity.getTransparentColor());
            }
            holder.canUninstall = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) || ((appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
            holder.updatePreventView(mActivity);
            if (holder.task != null) {
                holder.task.cancel(true);
            }
            holder.task = new RetrieveIconTask();
            holder.task.execute(holder, appInfo);
            return view;
        }


        public Filter getFilter() {
            if (mFilter == null) {
                mFilter = new SimpleFilter();
            }
            return mFilter;
        }

        public Collection<String> getAllPreventablePackages() {
            Set<String> allPreventablePackages = new HashSet<String>();
            if (mFiltered == null) {
                allPreventablePackages.addAll(mNames);
            } else {
                allPreventablePackages.addAll(mFiltered);
            }
            if (canSelectAll()) {
                return allPreventablePackages;
            }
            Iterator<String> iterator = allPreventablePackages.iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                if (!mCanPreventNames.contains(name) || PackageUtils.isImportPackage(mActivity, name)) {
                    iterator.remove();
                }
            }
            return allPreventablePackages;
        }

        public void startTaskIfNeeded() {
            AsyncTask.Status status = mTask.getStatus();
            if (status == AsyncTask.Status.PENDING) {
                mTask.execute();
            } else if (mTask.dialog != null) {
                mTask.dialog.show();
            }
        }

        private class SimpleFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence prefix) {
                FilterResults results = new FilterResults();
                String query;
                boolean defaultQuery = false;
                if (TextUtils.isEmpty(prefix)) {
                    query = getDefaultQuery();
                    defaultQuery = true;
                } else {
                    query = prefix.toString().toLowerCase(Locale.US);
                }
                if (mFiltered == null) {
                    mFiltered = new HashSet<String>();
                }
                List<AppInfo> values = new ArrayList<AppInfo>();
                if (query == null) {
                    values.addAll(mAppInfos);
                    mFiltered.addAll(mNames);
                } else {
                    mFiltered.clear();
                    for (AppInfo appInfo : mAppInfos) {
                        if (defaultQuery && !mCanPreventNames.contains(appInfo.packageName)) {
                            continue;
                        }
                        if (match(query, appInfo)) {
                            values.add(appInfo);
                            mFiltered.add(appInfo.packageName);
                        }
                    }
                }
                results.values = values;
                results.count = values.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                setNotifyOnChange(false);
                clear();
                for (AppInfo appInfo : (List<AppInfo>) results.values) {
                    add(appInfo);
                }
                notifyDataSetChanged();
            }

            private boolean match(String query, AppInfo appInfo) {
                return contains(query, appInfo)
                        || queryForThirdParty(query, appInfo)
                        || queryExtra(query, appInfo)
                        || queryCombined(query, appInfo);
            }

            private boolean queryCombined(String query, AppInfo appInfo) {
                if ("-sg".equals(query)) {
                    return appInfo.isSystem() && !GmsUtils.isGapps(appInfo.packageName);
                } else if ("-3g".equals(query)) {
                    return !appInfo.isSystem() || GmsUtils.isGapps(appInfo.packageName);
                }
                return false;
            }

            private boolean queryExtra(String query, AppInfo appInfo) {
                return queryForSystem(query, appInfo)
                        || queryForGapps(query, appInfo)
                        || queryForRunning(query, appInfo)
                        || queryForEnabled(query, appInfo);
            }

            private boolean contains(String query, AppInfo appInfo) {
                return "-a".equals(query)
                    || appInfo.name.toLowerCase(Locale.US).contains(query)
                    || (query.length() >= 0x4 && appInfo.packageName.toLowerCase(Locale.US).contains(query));
            }

            private boolean queryForThirdParty(String query, AppInfo appInfo) {
                return "-3".equals(query) && !appInfo.isSystem();
            }

            private boolean queryForSystem(String query, AppInfo appInfo) {
                return "-s".equals(query) && appInfo.isSystem();
            }

            private boolean queryForGapps(String query, AppInfo appInfo) {
                return "-g".equals(query) && GmsUtils.isGapps(appInfo.packageName);
            }

            private boolean queryForRunning(String query, AppInfo appInfo) {
                return "-r".equals(query) && mActivity.getRunningProcesses().containsKey(appInfo.packageName);
            }

            private boolean queryForEnabled(String query, AppInfo appInfo) {
                return "-e".equals(query) && !mActivity.getPreventPackages().containsKey(appInfo.packageName);
            }
        }

        private class RetrieveInfoTask extends AsyncTask<Void, Integer, Set<AppInfo>> {
            ProgressDialog dialog;
            LabelLoader labelLoader;

            @Override
            protected void onPreExecute() {
                dialog = new ProgressDialog(mActivity);
                dialog.setTitle(R.string.app_name);
                dialog.setIcon(R.drawable.ic_launcher);
                dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                dialog.setCancelable(false);
                dialog.setMax(mNames.size());
                dialog.show();
                labelLoader = new LabelLoader(mActivity);
            }

            @Override
            protected Set<AppInfo> doInBackground(Void... params) {
                Map<String, Set<Long>> running = mActivity.getRunningProcesses();
                Set<AppInfo> applications = new TreeSet<AppInfo>();
                int i = 1;
                for (String name : mNames) {
                    publishProgress(++i);
                    ApplicationInfo info;
                    try {
                        info = mPm.getApplicationInfo(name, 0);
                    } catch (NameNotFoundException e) { // NOSONAR
                        info = null;
                    }
                    if (info == null || !info.enabled) {
                        continue;
                    }
                    String label = labelLoader.loadLabel(info);
                    applications.add(new AppInfo(name, label, running.get(name)).setFlags(info.flags));
                }
                return applications;
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
                if (dialog != null) {
                    dialog.setProgress(progress[0]);
                }
            }

            @Override
            protected void onPostExecute(Set<AppInfo> applications) {
                for (AppInfo application : applications) {
                    add(application);
                    mAppInfos.add(application);
                }
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
                if (mView != null) {
                    mView.setVisibility(View.VISIBLE);
                }
                String query = search.getText().toString();
                if (TextUtils.isEmpty(query)) {
                    query = getDefaultQuery();
                }
                if (!TextUtils.isEmpty(query)) {
                    getFilter().filter(query);
                }
            }
        }
    }

    private class RetrieveIconTask extends AsyncTask<Object, Void, ViewHolder> {

        WeakReference<PreventActivity> wr = new WeakReference<PreventActivity>(mActivity);

        @Override
        protected ViewHolder doInBackground(Object... params) {
            ViewHolder holder = (ViewHolder) params[0];
            PreventActivity pa = wr.get();
            if (pa == null) {
                return holder;
            }
            AppInfo appInfo = (AppInfo) params[1];
            try {
                holder.icon = pa.getPackageManager().getApplicationIcon(appInfo.packageName);
            } catch (NameNotFoundException e) { // NOSONAR
                // do nothing
            }
            holder.running = pa.getRunningProcesses().get(appInfo.packageName);
            return holder;
        }

        @Override
        protected void onPostExecute(ViewHolder holder) {
            holder.iconView.setImageDrawable(holder.icon);
            holder.summaryView.setText(StatusUtils.formatRunning(mActivity, holder.running));
            holder.loadingView.setVisibility(View.GONE);
            holder.summaryView.setVisibility(View.VISIBLE);
        }
    }

}