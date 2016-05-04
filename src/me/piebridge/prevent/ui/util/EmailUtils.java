package me.piebridge.prevent.ui.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.util.Locale;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.ui.UILog;

/**
 * Created by thom on 15/10/5.
 */
public class EmailUtils {

    private EmailUtils() {

    }

    public static String getSubject(Context context) {
        StringBuilder subject = new StringBuilder();
        subject.append(context.getString(R.string.app_name));
        subject.append(" ");
        subject.append(BuildConfig.VERSION_NAME);
        subject.append("(Android ");
        subject.append(Locale.getDefault().toString());
        subject.append("-");
        subject.append(Build.VERSION.RELEASE);
        subject.append(")");
        return subject.toString();
    }

    public static boolean sendEmail(Context context, String content) {
        if (TextUtils.isEmpty(BuildConfig.EMAIL)) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + BuildConfig.EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT, getSubject(context));
        if (content != null) {
            intent.putExtra(Intent.EXTRA_TEXT, content);
        }
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.choose_email)));
            return true;
        } catch (ActivityNotFoundException e) {
            UILog.d("cannot send email", e);
            return false;
        }
    }

    public static void sendZip(Context context, File path, String content) {
        if (TextUtils.isEmpty(BuildConfig.EMAIL)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setType("vnd.android.cursor.dir/email");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(path));
        intent.putExtra(Intent.EXTRA_SUBJECT, EmailUtils.getSubject(context));
        intent.putExtra(Intent.EXTRA_TEXT, content);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{BuildConfig.EMAIL});
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.choose_email)));
    }

}
