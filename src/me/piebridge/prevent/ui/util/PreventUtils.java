package me.piebridge.prevent.ui.util;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.ui.UILog;

/**
 * Created by thom on 15/7/13.
 */
public class PreventUtils {

    private static final String INVALID_VALUE = "invalid value for ";

    private PreventUtils() {

    }

    public static void update(Context context, String[] packages, boolean add) {
        if (packages == null || packages.length == 0) {
            return;
        }
        Intent intent = new Intent(PreventIntent.ACTION_UPDATE_PREVENT, Uri.fromParts(PreventIntent.SCHEME, context.getPackageName(), null));
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        intent.putExtra(PreventIntent.EXTRA_PACKAGES, packages);
        intent.putExtra(PreventIntent.EXTRA_PREVENT, add);
        context.sendOrderedBroadcast(intent, PreventIntent.PERMISSION_SYSTEM, new PreventListReceiver(), null, 0, null, null);
    }

    public static void updateConfiguration(Context context, Bundle bundle) {
        Intent intent = new Intent(PreventIntent.ACTION_UPDATE_CONFIGURATION, Uri.fromParts(PreventIntent.SCHEME, context.getPackageName(), null));
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        intent.putExtra(PreventIntent.EXTRA_CONFIGURATION, bundle);
        context.sendBroadcast(intent, PreventIntent.PERMISSION_SYSTEM);
    }

    public static void softReboot(Context context) {
        Intent intent = new Intent(PreventIntent.ACTION_SOFT_REBOOT, Uri.fromParts(PreventIntent.SCHEME, context.getPackageName(), null));
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(intent, PreventIntent.PERMISSION_SYSTEM);
    }

    public static void reboot(Context context) {
        Intent intent = new Intent(PreventIntent.ACTION_REBOOT, Uri.fromParts(PreventIntent.SCHEME, context.getPackageName(), null));
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(intent, PreventIntent.PERMISSION_SYSTEM);
    }

    public static void confirmReboot(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.reboot);
        builder.setMessage(R.string.are_you_sure);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                reboot(context);
            }
        });
        builder.create().show();
    }

    public static void showUpdated(Context context, int size) {
        String message = context.getString(R.string.updated_prevents, size);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private static class PreventListReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String result = getResultData();
            if (PreventIntent.ACTION_UPDATE_PREVENT.equals(action) && result != null) {
                handlePackages(context, result);
            }
        }

        private void handlePackages(Context context, String result) {
            try {
                JSONObject json = new JSONObject(result);
                Set<String> prevents = new TreeSet<String>();
                Iterator<String> it = json.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    prevents.add(key);
                }
                int size = getResultCode();
                if (prevents.size() == size) {
                    showUpdated(context, size);
                    PreventListUtils.getInstance().backupIfNeeded(context, prevents);
                } else {
                    UILog.e("update prevents: " + prevents.size() + " != " + size);
                }
            } catch (JSONException e) {
                UILog.e("cannot convert to json", e);
            }
        }
    }

    public static void updateConfiguration(Context context) {
        updateConfiguration(context, false);
    }

    public static boolean updateConfiguration(Context context, boolean updatePreventList) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Bundle bundle = new Bundle();
        getPreference(sp, bundle, PreventIntent.KEY_FORCE_STOP_TIMEOUT, -1);
        getPreference(sp, bundle, PreventIntent.KEY_DESTROY_PROCESSES, false);
        getPreference(sp, bundle, PreventIntent.KEY_LOCK_SYNC_SETTINGS, false);
        getPreference(sp, bundle, PreventIntent.KEY_AUTO_PREVENT, true);
        getPreference(sp, bundle, PreventIntent.KEY_USE_APP_STANDBY, false);
        getPreference(sp, bundle, PreventIntent.KEY_ALLOW_EMPTY_SENDER, true);
        getPreference(sp, bundle, PreventIntent.KEY_STOP_SIGNATURE_APPS, true);
        Set<String> prevents = null;
        if (updatePreventList) {
            prevents = PreventListUtils.getInstance().load(context);
            if (!prevents.isEmpty()) {
                PreventUtils.showUpdated(context, prevents.size());
                bundle.putStringArrayList(PreventIntent.KEY_PREVENT_LIST, new ArrayList<String>(prevents));
            }
        }
        PreventUtils.updateConfiguration(context, bundle);
        return prevents != null && !prevents.isEmpty();
    }

    private static boolean getPreference(SharedPreferences sp, Bundle bundle, String key, boolean defaultValue) {
        boolean value = defaultValue;
        try {
            value = sp.getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            UILog.d(INVALID_VALUE + key, e);
            sp.edit().putBoolean(key, defaultValue).apply();
        }
        bundle.putBoolean(key, value);
        UILog.d(key + ": " + value);
        return value;
    }

    private static long getPreference(SharedPreferences sp, Bundle bundle, String key, long defaultValue) {
        long value = defaultValue;
        try {
            value = Long.parseLong(sp.getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            UILog.d(INVALID_VALUE + key, e);
        } catch (ClassCastException e) {
            UILog.d(INVALID_VALUE + key, e);
        }
        bundle.putLong(key, value);
        UILog.d(key + ": " + value);
        return value;
    }

    private static void updateBooleanPreference(SharedPreferences sp, JSONObject json, String key) {
        boolean defaultValue = json.optBoolean(key);
        boolean value = !defaultValue;
        try {
            value = sp.getBoolean(key, value);
        } catch (ClassCastException e) {
            UILog.d(INVALID_VALUE + key, e);
        }
        if (value != defaultValue) {
            sp.edit().putBoolean(key, defaultValue).apply();
        }
    }

    private static void updateLongPreference(SharedPreferences sp, JSONObject json, String key) {
        long defaultValue = json.optLong(key);
        long value = defaultValue + 1;
        try {
            value = Long.parseLong(sp.getString(key, String.valueOf(value)));
        } catch (NumberFormatException e) {
            UILog.d(INVALID_VALUE + key, e);
        } catch (ClassCastException e) {
            UILog.d(INVALID_VALUE + key, e);
        }
        if (value != defaultValue) {
            sp.edit().putString(key, String.valueOf(defaultValue)).apply();
        }
    }

    public static void updateConfiguration(Context context, JSONObject json) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean updated = false;
        for (String key : PreventIntent.KEYS_LONG) {
            if (json.has(key)) {
                updated = true;
                updateLongPreference(sp, json, key);
            }
        }
        for (String key : PreventIntent.KEYS_BOOLEAN) {
            if (json.has(key)) {
                updated = true;
                updateBooleanPreference(sp, json, key);
            }
        }
        if (!updated) {
            PreventUtils.updateConfiguration(context);
        }
    }

}
