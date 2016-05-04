package me.piebridge.prevent.ui.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by thom on 15/10/21.
 */
public class FileUtils {

    private FileUtils() {

    }

    public static void dumpFile(InputStream is, File file) throws IOException {
        byte[] buffer = new byte[0x1000];
        OutputStream os = new FileOutputStream(file);
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
        }
        os.flush();
        is.close();
    }

    public static void copyFile(OutputStream os, File file) throws IOException {
        byte[] buffer = new byte[0x1000];
        InputStream is = new FileInputStream(file);
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
        }
        os.flush();
        is.close();
    }

}
