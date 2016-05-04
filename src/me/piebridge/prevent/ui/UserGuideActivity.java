package me.piebridge.prevent.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import me.piebridge.billing.DonateActivity;
import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.common.PreventIntent;
import me.piebridge.prevent.ui.util.DeprecatedUtils;
import me.piebridge.prevent.ui.util.EmailUtils;
import me.piebridge.prevent.ui.util.FileUtils;
import me.piebridge.prevent.ui.util.LicenseUtils;
import me.piebridge.prevent.ui.util.RecreateUtils;
import me.piebridge.prevent.ui.util.ThemeUtils;

/**
 * Created by thom on 15/10/3.
 */
public class UserGuideActivity extends DonateActivity implements View.OnClickListener {

    private static final int VERSION = 20160406;

    private View donateView;

    private AlertDialog request;

    private ProgressDialog donateDialog;

    private BroadcastReceiver receiver;

    private boolean clickedDonate = false;

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0x1;

    private String name;
    private Integer version = getXposedVersion();
    private String method = "xposed";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        ThemeUtils.fixSmartBar(this);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        WebView webView = (WebView) findViewById(R.id.webview);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        if ("zh".equals(Locale.getDefault().getLanguage())) {
            webView.loadUrl("file:///android_asset/about.zh.html");
        } else {
            webView.loadUrl("file:///android_asset/about.en.html");
        }
        setView(R.id.alipay, "com.eg.android.AlipayGphone");
        if (hasPermission()) {
            setView(R.id.wechat, "com.tencent.mm");
        } else {
            findViewById(R.id.wechat).setVisibility(View.GONE);
        }
        if (!setView(R.id.paypal, "com.paypal.android.p2pmobile")) {
            TextView paypal = (TextView) findViewById(R.id.paypal);
            paypal.setClickable(true);
            paypal.setOnClickListener(this);
            paypal.setCompoundDrawablesWithIntrinsicBounds(null, cropDrawable(paypal.getCompoundDrawables()[1]), null, null);
        }
        if (setView(R.id.play, "com.android.vending")) {
            findViewById(R.id.play).setVisibility(View.GONE);
            checkDonate();
        }
        donateView = findViewById(R.id.donate);
        if (BuildConfig.DONATE && TextUtils.isEmpty(LicenseUtils.getLicense(this))) {
            donateView.setVisibility(View.VISIBLE);
        } else {
            donateView.setVisibility(View.GONE);
        }
        retrieveInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BuildConfig.DONATE) {
            checkLicense();
            hideDonateDialog();
        }
    }

    @Override
    protected void onDestroy() {
        deleteQrCodeIfNeeded();
        super.onDestroy();
    }

    private int getPixel(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private Drawable cropDrawable(Drawable icon) {
        int width = getPixel(0x20);
        if (icon.getMinimumWidth() > width && icon instanceof BitmapDrawable) {
            Bitmap bitmap = Bitmap.createScaledBitmap(((BitmapDrawable) icon).getBitmap(), width, width, false);
            return new BitmapDrawable(getResources(), bitmap);
        }
        return icon;
    }

    private boolean setView(int id, String packageName) {
        TextView donate = (TextView) findViewById(id);
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            if (!info.enabled) {
                return false;
            }
            CharSequence label = getLabel(pm, info);
            donate.setContentDescription(label);
            donate.setCompoundDrawablesWithIntrinsicBounds(null, cropDrawable(pm.getApplicationIcon(info)), null, null);
            donate.setText(label);
            donate.setClickable(true);
            donate.setOnClickListener(this);
            donate.setVisibility(View.VISIBLE);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            UILog.d("cannot find package " + packageName, e);
            return false;
        }
    }

    private CharSequence getLabel(PackageManager pm, ApplicationInfo info) throws PackageManager.NameNotFoundException {
        CharSequence label = null;
        if ("com.android.vending".equals(info.packageName)) {
            Resources resources = pm.getResourcesForApplication(info);
            int appName = resources.getIdentifier("app_name", "string", info.packageName);
            if (appName > 0) {
                label = resources.getText(appName);
            }
        }
        if (TextUtils.isEmpty(label)) {
            label = pm.getApplicationLabel(info);
        }
        return label;
    }

    private File getQrCode() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            return null;
        }
        if (!checkPermission()) {
            return null;
        }
        File screenshots = new File(dir, "Screenshots");
        if (!screenshots.exists()) {
            screenshots.mkdirs();
        }
        return new File(screenshots, "pr_donate.png");
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                || !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                donateViaWeChat();
            } else {
                findViewById(R.id.wechat).setVisibility(View.GONE);
            }
        }
    }

    private void refreshQrCode(File qrCode) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(qrCode));
        sendBroadcast(mediaScanIntent);
    }

    private void deleteQrCodeIfNeeded() {
        File qrCode = getQrCode();
        if (qrCode != null && qrCode.exists()) {
            qrCode.delete();
            refreshQrCode(qrCode);
        }
    }

    private boolean donateViaWeChat() {
        File qrCode = getQrCode();
        if (qrCode == null) {
            return false;
        }
        try {
            FileUtils.dumpFile(getAssets().open("wechat.png"), qrCode);
        } catch (IOException e) {
            UILog.d("cannot dump wechat", e);
            return false;
        }
        refreshQrCode(qrCode);
        showDonateDialog();
        Intent intent = new Intent("com.tencent.mm.action.BIZSHORTCUT");
        intent.setPackage("com.tencent.mm");
        intent.putExtra("LauncherUI.From.Scaner.Shortcut", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
            for (int i = 0; i < 0x3; ++i) {
                Toast.makeText(this, R.string.select_qr_code, Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) { // NOSONAR
            hideDonateDialog();
        }
        return true;
    }

    private boolean donateViaAlipay() {
        showDonateDialog();
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse(BuildConfig.DONATE_ALIPAY));
        try {
            startActivity(intent);
        } catch (Throwable t) { // NOSONAR
            hideDonateDialog();
        }
        return true;
    }

    private boolean donateViaPayPal() {
        showDonateDialog();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DONATE_PAYPAL)));
        } catch (Throwable t) { // NOSONAR
            // do nothing
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.wechat) {
            donateViaWeChat();
        } else if (id == R.id.alipay) {
            donateViaAlipay();
        } else if (id == R.id.paypal) {
            donateViaPayPal();
        } else if (id == R.id.play) {
            showDonateDialog();
            donateViaPlay();
        }
    }

    @Override
    public void onDonateFailed() {
        hideDonateDialog();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (BuildConfig.DONATE && donateView.getVisibility() == View.GONE) {
            menu.add(Menu.NONE, R.string.donate, Menu.NONE, R.string.donate);
        }
        menu.add(Menu.NONE, R.string.version, Menu.NONE, R.string.version);
        if (BuildConfig.DONATE) {
            menu.add(Menu.NONE, R.string.feedback, Menu.NONE, R.string.feedback);
            if (TextUtils.isEmpty(LicenseUtils.getLicense(this))) {
                menu.add(Menu.NONE, R.string.request_license, Menu.NONE, R.string.request_license);
            }
        }
        menu.add(Menu.NONE, R.string.advanced_settings, Menu.NONE, R.string.advanced_settings);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.string.donate) {
            clickedDonate = true;
            donateView.setVisibility(View.VISIBLE);
        } else if (id == R.string.feedback) {
            feedback();
        } else if (id == R.string.version) {
            showVersionInfo();
        } else if (id == R.string.advanced_settings) {
            startActivity(new Intent(this, AdvancedSettingsActivity.class));
        } else if (id == R.string.request_license) {
            requestLicense();
        }
        return true;
    }

    private void feedback() {
        EmailUtils.sendEmail(this, getString(R.string.feedback));
    }

    private void checkLicense() {
        if (LicenseUtils.importLicenseFromClipboard(this)) {
            LicenseUtils.validLicense(this, true, new Runnable() {
                @Override
                public void run() {
                    recreateIfNeeded();
                }
            });
        }
    }

    private void recreateIfNeeded() {
        if (request != null) {
            request.dismiss();
            request = null;
        }
        if (!TextUtils.isEmpty(LicenseUtils.getLicense(this))) {
            RecreateUtils.recreate(this);
        }
    }

    private void requestLicense() {
        if (TextUtils.isEmpty(LicenseUtils.getLicenseName(this))) {
            Intent intent = new Intent(PreventIntent.ACTION_CHECK_LICENSE, Uri.fromParts(PreventIntent.SCHEME, getPackageName(), null));
            intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            sendOrderedBroadcast(intent, PreventIntent.PERMISSION_SYSTEM, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (PreventIntent.ACTION_CHECK_LICENSE.equals(intent.getAction()) && getResultCode() != 1) {
                        request = LicenseUtils.requestLicense(UserGuideActivity.this, null, getResultData());
                    }
                }
            }, null, 0, null, null);
        }
    }

    private void showDonateDialog() {
        RelativeLayout layout = new RelativeLayout(this);
        int pixel = getPixel(0x30);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(pixel, pixel);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        layout.addView(new ProgressBar(this), params);
        donateDialog = ProgressDialog.show(this, null, null);
        donateDialog.setContentView(layout);
        donateDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, pixel * 0x4);
    }

    private void hideDonateDialog() {
        if (donateDialog != null) {
            donateDialog.dismiss();
            donateDialog = null;
        }
    }

    private void retrieveInfo() {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setAction(PreventIntent.ACTION_GET_INFO);
        intent.setData(Uri.fromParts(PreventIntent.SCHEME, getPackageName(), null));
        UILog.i("sending get info broadcast");
        if (receiver == null) {
            receiver = new HookReceiver();
        }
        sendOrderedBroadcast(intent, PreventIntent.PERMISSION_SYSTEM, receiver, null, 0, null, null);
    }

    @Override
    public void onBackPressed() {
        if (clickedDonate && donateView.getVisibility() == View.VISIBLE) {
            donateView.setVisibility(View.GONE);
            clickedDonate = false;
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onUnavailable() {
        findViewById(R.id.play).setVisibility(View.GONE);
    }

    @Override
    public void onAvailable() {
        findViewById(R.id.play).setVisibility(View.VISIBLE);
    }

    @Override
    public void onDonated() {
        LicenseUtils.setInAppLicensed();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            invalidateOptionsMenu();
        }
        donateView.setVisibility(View.GONE);
        findViewById(R.id.play).setVisibility(View.GONE);
    }

    private class HookReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (PreventIntent.ACTION_GET_INFO.equals(action)) {
                handleInfo();
            }
        }

        private void handleInfo() {
            String info = getResultData();
            if (TextUtils.isEmpty(info)) {
                return;
            }
            try {
                JSONObject json = new JSONObject(info);
                version = json.optInt("version");
                method = json.optString("method");
                name = json.optString("name");
            } catch (JSONException e) {
                UILog.d("cannot get version from " + info, e);
            }
        }
    }

    private static Integer getXposedVersion() {
        try {
            return (Integer) Class.forName("de.robv.android.xposed.XposedBridge", false, ClassLoader.getSystemClassLoader()).getField("XPOSED_BRIDGE_VERSION").get(null);
        } catch (Throwable t) { // NOSONAR
            return 0;
        }
    }

    private String getVersionInfo(boolean showAppVersion) {
        StringBuilder sb = new StringBuilder();
        String licenseName;
        if (!BuildConfig.DONATE) {
            licenseName = null;
        } else if (showAppVersion) {
            licenseName = LicenseUtils.getLicense(this);
        } else {
            licenseName = LicenseUtils.getLicenseName(this);
        }
        if (!TextUtils.isEmpty(licenseName)) {
            sb.append(licenseName);
            sb.append("\n");
        }
        showVersion(sb);
        sb.append("Android: ");
        sb.append(Locale.getDefault());
        sb.append("-");
        sb.append(Build.VERSION.RELEASE);
        sb.append("\n");
        if (showAppVersion) {
            sb.append(getString(R.string.app_name));
            sb.append(": ");
            sb.append(BuildConfig.VERSION_NAME);
            sb.append("\n");
        }
        sb.append(Build.FINGERPRINT);
        return sb.toString();
    }

    private void showVersion(StringBuilder sb) {
        if (name != null && !BuildConfig.VERSION_NAME.equalsIgnoreCase(name)) {
            sb.append("Active: ");
            sb.append(name);
            sb.append("\n");
        }
        if (version != null) {
            if (version == 0) {
                method = "native";
            }
            sb.append("Bridge: ");
            sb.append(method);
            sb.append(" v");
            sb.append(version);
            if ("native".equalsIgnoreCase(method) && version < VERSION) {
                sb.append(" -> v");
                sb.append(VERSION);
            }
            sb.append("\n");
        }
    }

    private void showVersionInfo() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name) + "(" + BuildConfig.VERSION_NAME + ")");
        builder.setMessage(getVersionInfo(false));
        builder.setIcon(R.drawable.ic_launcher);
        builder.setPositiveButton(getString(android.R.string.copy), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DeprecatedUtils.setClipboard(getBaseContext(), getVersionInfo(true));
            }
        });
        builder.create().show();
    }

}
