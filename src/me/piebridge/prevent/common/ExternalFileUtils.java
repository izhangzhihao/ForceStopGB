package me.piebridge.prevent.common;

import android.content.Context;
import android.os.Build;

import java.io.File;

/**
 * Created by thom on 16/2/11.
 */
public class ExternalFileUtils {

    private ExternalFileUtils() {

    }

    public static File[] getExternalFilesDirs(Context context) {
        File[] files;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            files = context.getExternalFilesDirs(null);
            if (files == null) {
                files = new File[0];
            }
        } else {
            files = new File[]{context.getExternalFilesDir(null)};
        }
        return files;
    }

}
