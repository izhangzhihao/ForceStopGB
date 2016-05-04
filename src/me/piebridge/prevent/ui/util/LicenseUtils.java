package me.piebridge.prevent.ui.util;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

import me.piebridge.billing.DonateUtils;
import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.common.ExternalFileUtils;
import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.ui.UILog;

/**
 * Created by thom on 15/10/5.
 */
public class LicenseUtils {

    private static final String LICENSE = "license.key";

    private static boolean inAppLicensed = false;

    // @formatter:off
    private static final byte[] MODULUS = {
             -93, -117,  -85,   56,  -65,   -8,  -86,   59,   52,   43,  -50,  -47,   64,   51,   89, -116,
              95,  120,  -85,  -82,  -60,  -78,   65,   80,   18,   78, -109,   61,  106,  -28,  112,   76,
             124,   32,   94,   -4,  103,  -31,  -81,   17,  -15,   58,   -1, -120,  103,   49,  -64,   29,
              25,  107,  -16,   85, -126,   92,  -85,    0,  -54,  -45, -120,  -50,   -9,   69,  -81, -113,
            -108,    7, -101,   69,  -57,   31,  105, -114,   42,   55,  -26, -103,   48,   23,  -51,   16,
            -102,  102,  -52,   78,  123,   74,   64,  -88,   18,  111,   71,   66,   -1,    6,  109,   29,
             -17,   -2,  -30,   94,   -5,    0,   61,   21,   31,   47,  -97,  -12,   76,  -37,  -40,  -40,
              23,  -41,  -89,  -21,   48,   52,  -76,  -34,  106,   41, -119,  119, -113,   -9,  -44, -103,
              31,  -38,    9,   34,  125, -121,   61, -111,   25, -113,  120,  -74, -105,   76,  -86,  114,
              38,  -38,  100,  117,    1,   37,   97,   11,  -34,   79,  -87,  106,  -56,  -75,  -72,   12,
             -50, -116,  100,   83, -123,  104,   50, -109,   55,   25,  125,   49,   49,  -99,   94,  -37,
              95,   -3,  -62,   33,  -82,  125,  -29,  -60,   60,   70,  -30,  127,   91,   76,   -3,  120,
             -84,  -76,  -69,    1,  100,    0,   84,  -43,  110,  -21,  113, -115,   81,  -43,  -21,  -26,
            -124,   76,  -87,   59,  -82,   38,   46, -126,   33,    0,   96,  -20, -101,   17,   43, -121,
               6,  -37,  -13,  -99,  123,  -41,   69,  120,  111, -106,   31, -124,   91,   51,   89,  -96,
             126,   20,  -75,  108, -107,   16,  127,   56,  -36,  -17,  -24,  -92,  -34,  -48,   65,   73,
    };
    // @formatter:on

    private LicenseUtils() {

    }

    private static byte[] readKey(Context context) {
        byte[] key;
        for (File file : ExternalFileUtils.getExternalFilesDirs(context)) {
            if (file == null) {
                continue;
            }
            key = readKey(file);
            if (key.length > 0) {
                return key;
            }
        }
        key = readKey(context.getFilesDir());
        if (key.length > 0) {
            return key;
        }
        return new byte[0];
    }

    private static byte[] readKey(File file) {
        byte[] key = new byte[0x100];
        File path = new File(file, LICENSE);
        if (path.isFile() && path.canRead()) {
            try {
                InputStream is = new FileInputStream(path);
                is.read(key);
                is.close();
                return key;
            } catch (IOException e) {
                UILog.d("cannot get license", e);
            }
        }
        return new byte[0];
    }

    public static String getLicense(final Context context) {
        if (inAppLicensed) {
            return DonateUtils.ITEM_ID;
        }
        String license = getLicense(readKey(context));
        if (TextUtils.isEmpty(license)) {
            return null;
        } else {
            return license.split(",")[0];
        }
    }

    public static void validLicense(Context context, final boolean show, final Runnable runnable) {
        String license = getLicense(readKey(context));
        if (inAppLicensed || context == null || TextUtils.isEmpty(license)) {
            return;
        }
        final String account = license.split(",")[0];
        Intent intent = new Intent(PreventIntent.ACTION_CHECK_LICENSE, Uri.fromParts(PreventIntent.SCHEME, BuildConfig.APPLICATION_ID, null));
        intent.putExtra(Intent.EXTRA_USER, account);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendOrderedBroadcast(intent, PreventIntent.PERMISSION_SYSTEM, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (getResultCode() != 1) {
                    String message = context.getString(R.string.no_valid_license, account);
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                    deleteLicenseIfNeeded(context);
                } else if (show) {
                    Toast.makeText(context, LicenseUtils.getRawLicenseName(context), Toast.LENGTH_LONG).show();
                }
                if (runnable != null) {
                    runnable.run();
                }
            }
        }, null, 0, null, null);
    }

    public static String getLicenseName(final Context context) {
        if (inAppLicensed) {
            return DonateUtils.ITEM_ID;
        }
        return getRawLicenseName(context);
    }

    public static String getRawLicenseName(final Context context) {
        String license = getLicense(readKey(context));
        if (TextUtils.isEmpty(license)) {
            return null;
        } else if (license.contains(",")) {
            return license.split(",", 0x2)[1];
        } else {
            return context.getString(R.string.licensed);
        }
    }

    private static String getLicense(byte[] key) {
        if (inAppLicensed) {
            return DonateUtils.ITEM_ID;
        }
        if (key == null || key.length == 0) {
            return null;
        }
        BigInteger exponent = BigInteger.valueOf(0x10001);
        BigInteger modulus = new BigInteger(1, MODULUS);
        byte[] signature = new BigInteger(1, key).modPow(exponent, modulus).toByteArray();
        return decodeLicense(signature);
    }

    private static String decodeLicense(byte[] signature) {
        int size = signature.length;
        if (signature[0] != 1) {
            return null;
        }
        for (int i = 0x1; i < size; ++i) {
            if (signature[i] == 0 && i > 0x8) {
                int offset = i + 1;
                return new String(signature, offset, size - offset);
            } else if (signature[i] != -1) {
                return null;
            }
        }
        return null;
    }

    private static void saveLicense(Context context, byte[] key) {
        try {
            File path = new File(context.getFilesDir(), LICENSE);
            FileOutputStream fos = new FileOutputStream(path);
            fos.write(key);
            fos.close();
        } catch (IOException e) {
            UILog.d("cannot save license", e);
        }
    }

    public static boolean importLicenseFromClipboard(Context context) {
        byte[] key = readKeyFromClipboard(context);
        String license = getLicense(key);
        if (!TextUtils.isEmpty(license)) {
            DeprecatedUtils.setClipboard(context, null);
            saveLicense(context, key);
            return true;
        }
        return false;
    }

    private static byte[] readKeyFromClipboard(Context context) {
        CharSequence plain = DeprecatedUtils.getClipboard(context);
        if (TextUtils.isEmpty(plain)) {
            return new byte[0];
        }
        try {
            return Base64.decode(plain.toString(), Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            UILog.d("cannot decode as base64: " + plain, e);
            return new byte[0];
        }
    }

    public static AlertDialog requestLicense(final Context context, String license, String accounts) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final String content = "license: " + license + ", accounts: " + accounts;
        builder.setTitle(context.getString(R.string.app_name) + "(" + BuildConfig.VERSION_NAME + ")");
        if (TextUtils.isEmpty(license)) {
            builder.setMessage(R.string.no_license);
        } else {
            builder.setMessage(context.getString(R.string.no_valid_license, license));
        }
        builder.setIcon(R.drawable.ic_launcher);
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
            }
        });
        builder.setPositiveButton(R.string.email_request, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteLicenseIfNeeded(context);
                EmailUtils.sendEmail(context, content);
            }
        });
        builder.setNeutralButton(R.string.copy_request, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteLicenseIfNeeded(context);
                DeprecatedUtils.setClipboard(context, content);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    private static void deleteLicenseIfNeeded(Context context) {
        deleteFileIfNeeded(context.getFilesDir());
        for (File dir : ExternalFileUtils.getExternalFilesDirs(context)) {
            deleteFileIfNeeded(dir);
        }
    }

    private static boolean deleteFileIfNeeded(File dir) {
        if (dir != null) {
            File file = new File(dir, LICENSE);
            if (file.isFile()) {
                return file.delete();
            }
        }
        return false;
    }

    public static void setInAppLicensed() {
        inAppLicensed = true;
    }

    public static boolean isNotInAppLicensed() {
        return !inAppLicensed;
    }

}
