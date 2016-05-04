package me.piebridge.prevent.common;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import me.piebridge.prevent.framework.PreventLog;

/**
 * Created by thom on 16/2/11.
 */
public class FileUtils {

    public static final String PREVENT_LIST = "prevent.list";

    private static final int MAX_WAIT = 3000;
    private static final int SINGLE_WAIT = 100;

    private FileUtils() {

    }

    private static void makeSure(File lock) {
        File parent = lock.getParentFile();
        if (parent.isFile()) {
            parent.delete();
        }
        if (!parent.isDirectory()) {
            parent.mkdirs();
        }
        while (lock.exists() && System.currentTimeMillis() - lock.lastModified() < MAX_WAIT) {
            try {
                Thread.sleep(SINGLE_WAIT);
            } catch (InterruptedException e) { // NOSONAR
                // do nothing
            }
        }
    }

    public static void save(String path, Set<String> packages) {
        File lock = new File(path + ".lock");
        makeSure(lock);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(lock));
            for (String key : packages) {
                writer.write(key);
                writer.write("\n");
            }
            writer.close();
            lock.renameTo(new File(path));
        } catch (IOException e) {
            PreventLog.e("cannot save " + path, e);
        }
    }

    public static void save(String path, Bundle bundle) {
        File lock = new File(path + ".lock");
        makeSure(lock);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(lock));
            for (String key : new TreeSet<String>(bundle.keySet())) {
                writer.write(key);
                writer.write("=");
                writer.write(String.valueOf(bundle.get(key)));
                writer.write("\n");
            }
            writer.close();
            lock.renameTo(new File(path));
        } catch (IOException e) {
            PreventLog.e("cannot save " + path, e);
        }
    }

    public static Set<String> load(File file) {
        Set<String> packages = new TreeSet<String>();
        if (!file.exists()) {
            return packages;
        }
        try {
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                int index = line.indexOf('=');
                if (index != -1) {
                    line = line.substring(0, index);
                }
                line = line.trim();
                packages.add(line);
            }
            reader.close();
            PreventLog.i("load " + file.getAbsolutePath() + ", size: " + packages.size());
        } catch (IOException e) {
            PreventLog.e("cannot load " + file.getAbsolutePath(), e);
        }
        return packages;
    }

    private static void loadExternal(Set<String> packages, Context context) {
        for (File file : ExternalFileUtils.getExternalFilesDirs(context)) {
            if (file != null) {
                packages.addAll(load(new File(file, PREVENT_LIST)));
                if (!packages.isEmpty()) {
                    return;
                }
            }
        }
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File sdcard = Environment.getExternalStorageDirectory();
            if (sdcard != null) {
                packages.addAll(load(new File(sdcard, PREVENT_LIST)));
            }
        }
    }

    public static Set<String> load(Context context, String prevent) {
        Set<String> packages = load(new File(prevent));
        if (context != null && packages.isEmpty()) {
            loadExternal(packages, context);
        }
        return packages;
    }

    public static boolean eraseFiles(File path) {
        if (path == null) {
            return false;
        }
        if (path.isDirectory()) {
            String[] files = path.list();
            if (files != null) {
                for (String file : files) {
                    eraseFiles(new File(path, file));
                }
            }
        }
        return path.delete();
    }

}