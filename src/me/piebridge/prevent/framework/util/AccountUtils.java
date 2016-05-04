package me.piebridge.prevent.framework.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 15/8/7.
 */
public class AccountUtils {

    private static final String NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String SYNC_ADAPTER = "android.content.SyncAdapter";
    private static Collection<Account> ENABLED_ACCOUNTS = new ArrayList<Account>();

    private AccountUtils() {

    }

    private static SyncAdapterType getSyncAdapter(Context context, ComponentName cn) {
        XmlResourceParser parser = null;
        try {
            PackageManager pm = context.getPackageManager();
            ServiceInfo si = pm.getServiceInfo(cn, PackageManager.GET_META_DATA);
            Resources resources = pm.getResourcesForApplication(si.packageName);
            parser = si.loadXmlMetaData(pm, SYNC_ADAPTER);
            while (true) {
                int type = parser.next();
                if (type == XmlPullParser.START_TAG && "sync-adapter".equals(parser.getName())) {
                    return parseSyncAdapter(resources, cn, parser);
                } else if (type == XmlPullParser.END_DOCUMENT) {
                    break;
                }
            }
        } catch (XmlPullParserException e) {
            PreventLog.d("cannot parse " + cn.flattenToShortString(), e);
        } catch (IOException e) {
            PreventLog.d("cannot parse/io " + cn.flattenToShortString(), e);
        } catch (PackageManager.NameNotFoundException e) {
            PreventLog.d("cannot find " + cn.flattenToShortString(), e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
        return null;
    }

    private static SyncAdapterType parseSyncAdapter(Resources resources, ComponentName cn, XmlResourceParser parser) {
        String contentAuthority = getValue(resources, parser.getAttributeValue(NAMESPACE_ANDROID, "contentAuthority"), "");
        String accountType = getValue(resources, parser.getAttributeValue(NAMESPACE_ANDROID, "accountType"), "");
        boolean userVisible = Boolean.valueOf(getValue(resources, parser.getAttributeValue(NAMESPACE_ANDROID, "userVisible"), "true"));
        boolean supportsUploading = Boolean.valueOf(getValue(resources, parser.getAttributeValue(NAMESPACE_ANDROID, "supportsUploading"), "true"));
        PreventLog.v(cn.flattenToShortString() + ", accountType: " + accountType + ", contentAuthority: " + contentAuthority
                + ", userVisible: " + userVisible + ", supportsUploading: " + supportsUploading);
        if (TextUtils.isEmpty(contentAuthority) || TextUtils.isEmpty(accountType)) {
            return null;
        }
        return new SyncAdapterType(contentAuthority, accountType, userVisible, supportsUploading);
    }

    private static String getValue(Resources resources, String value, String defaultValue) {
        if (value != null && value.startsWith("@")) {
            String number = value.substring(1);
            if (TextUtils.isDigitsOnly(number)) {
                try {
                    return resources.getString(Integer.parseInt(number));
                } catch (Resources.NotFoundException e) {
                    PreventLog.d("cannot find " + value, e);
                }
            }
        }
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }
    }

    public static boolean isComponentSyncable(Context context, ComponentName component) {
        PreventLog.d("check sync for " + component.flattenToShortString());
        SyncAdapterType type = getSyncAdapter(context, component);
        if (type == null) {
            PreventLog.w("cannot find sync adapter for " + component.flattenToShortString());
            return false;
        } else if (type.isUserVisible() && isSyncable(type)) {
            PreventLog.d(component.flattenToShortString() + " is syncable, account type: " + type.accountType + ", authority: " + type.authority);
            return true;
        } else {
            PreventLog.d(component.flattenToShortString() + " isn't syncable");
            return false;
        }
    }

    @Nullable
    public static Boolean isPackageSyncable(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(SYNC_ADAPTER);
        intent.setPackage(packageName);
        Boolean result = null;
        for (ResolveInfo info : pm.queryIntentServices(intent, 0)) {
            ServiceInfo si = info.serviceInfo;
            if (packageName.equals(si.packageName)) {
                result = false;
                if (isComponentSyncable(context, new ComponentName(si.packageName, si.name))) {
                    return true;
                }
            }
        }
        return result;
    }

    public static void setSyncable(Context context, String packageName, boolean syncable) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(SYNC_ADAPTER);
        intent.setPackage(packageName);
        for (ResolveInfo info : pm.queryIntentServices(intent, 0)) {
            ServiceInfo si = info.serviceInfo;
            if (packageName.equals(si.packageName)) {
                setSyncable(context, new ComponentName(si.packageName, si.name), syncable);
            }
        }
    }

    private static void setSyncable(Context context, ComponentName component, boolean syncable) {
        PreventLog.d("set sync to " + syncable + " for " + component.flattenToShortString());
        SyncAdapterType type = getSyncAdapter(context, component);
        if (type != null && type.isUserVisible()) {
            for (Account account : getEnabledAccounts(null)) {
                if (account.type.equals(type.accountType)) {
                    ContentResolver.setSyncAutomatically(account, type.authority, syncable);
                }
            }
        }
    }

    private static boolean isSyncable(SyncAdapterType type) {
        for (Account account : getEnabledAccounts(null)) {
            if (account.type.equals(type.accountType)) {
                PreventLog.v("check account " + account.type + " for " + type.authority);
                if (ContentResolver.getSyncAutomatically(account, type.authority)
                        && ContentResolver.getIsSyncable(account, type.authority) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Collection<Account> getEnabledAccounts(Context context) {
        if (context != null) {
            fetchAccounts(context);
        }
        return ENABLED_ACCOUNTS;
    }

    public static synchronized void fetchAccounts(Context context) {
        try {
            ENABLED_ACCOUNTS.clear();
            Collections.addAll(ENABLED_ACCOUNTS, AccountManager.get(context).getAccounts());
            PreventLog.i("found accounts: " + ENABLED_ACCOUNTS.size());
        } catch (RuntimeException e) {
            PreventLog.e("cannot find system's account", e);
        }
    }

}
