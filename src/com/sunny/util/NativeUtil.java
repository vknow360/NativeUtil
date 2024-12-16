package com.sunny.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.google.appinventor.components.runtime.util.AsynchUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class NativeUtil {

    private final static String TAG = "NativeUtil";
    private final Context context;
    private final Callbacks callbacks;
    private boolean usePreferred = false;

    public NativeUtil(Context context, Callbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        //log("Supported ABIs: " + TextUtils.join(",", getSupportedABIs()));
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    public static String[] getSupportedABIs() {
        return Build.SUPPORTED_64_BIT_ABIS.length > 0 ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
    }

    public static String getPreferredABI() {
        return getSupportedABIs()[0];
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        try {
            byte[] bytes = new byte[2048];
            int read;
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        }
    }

    private String getNameFromUrl(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    public void downloadAndLoad(final String zipUrl) {
        AsynchUtil.runAsynchronously(new Runnable() {
            @Override
            public void run() {
                try {
                    File dFile = new File(context.getFilesDir(), "/zip/" + getNameFromUrl(zipUrl));
                    log(dFile.getAbsolutePath());
                    if (!(dFile.length() > 0)) {
                        if (!dFile.getParentFile().exists()) {
                            boolean ignored = dFile.getParentFile().mkdirs();
                        }
                        FileOutputStream fos = new FileOutputStream(dFile);
                        URL url = new URL(zipUrl);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        if (connection.getResponseCode() == 200) {
                            long totalSize = connection.getContentLengthLong();
                            long downloadedSize = 0;
                            byte[] buffer = new byte[2048];
                            int bufferLength;
                            InputStream in = new BufferedInputStream(connection.getInputStream());
                            while ((bufferLength = in.read(buffer)) > 0) {
                                fos.write(buffer, 0, bufferLength);
                                downloadedSize += bufferLength;
                                final int progress = (int) (100 * downloadedSize / totalSize);
                                //log("Progress: " + progress);
                                callbacks.onDownloadProgress(progress);
                            }
                            callbacks.onDownloadComplete(dFile);
                            unzipAndLoad(dFile);
                        } else {
                            callbacks.onError("Unable to download zip file");
                        }
                    } else {
                        callbacks.onDownloadComplete(dFile);
                        unzipAndLoad(dFile);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    //log(e.getMessage() != null ? e.getMessage() : e.toString());
                    callbacks.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        });
    }

    private String parseName(String name) {
        if (name.contains(".")) {
            return name.split("\\.")[0];
        }
        return name;
    }

    public void unzipAndLoad(final File zFile) {
        try {
            final List<String> unzippedLibs = new ArrayList<>();
            File unzipFolder = new File(context.getFilesDir(), "lib/" + parseName(zFile.getName()));
            log(unzipFolder.getAbsolutePath());
            ZipFile zipFile = new ZipFile(zFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    File libFolder = new File(unzipFolder, name);
                    if (!libFolder.exists()) {
                        libFolder.mkdirs();
                    }
                }else if (usePreferred){
                    if (name.toLowerCase().contains(getPreferredABI().toLowerCase())) {
                        File libFile = new File(unzipFolder, name);
                        if (!libFile.getParentFile().exists()) {
                            libFile.getParentFile().mkdirs();
                        }
                        unzippedLibs.add(libFile.getAbsolutePath());
                        copy(zipFile.getInputStream(entry), new FileOutputStream(libFile));
                    }
                }else {
                    final List<String> supported = Arrays.asList(getSupportedABIs());
                    String archName = name.split("/")[0];
                    if (supported.contains(archName)) {
                        File libFile = new File(unzipFolder, name);
                        if (!libFile.getParentFile().exists()) {
                            libFile.getParentFile().mkdirs();
                        }
                        copy(zipFile.getInputStream(entry), new FileOutputStream(libFile));
                        unzippedLibs.add(libFile.getAbsolutePath());
                    }
                }
            }
            zipFile.close();
            //log("Loaded libs: " + TextUtils.join(",", unzippedLibs));
            loadLibs(unzippedLibs);
        } catch (Exception e) {
            callbacks.onError(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    public void loadLibs(final List<String> libs) {
        context.getMainExecutor().execute(new Runnable() {
            @Override
            public void run() {
                for (String lib : libs
                ) {
                    try {
                        System.load(lib);
                    } catch (Exception e) {
                        callbacks.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                    }
                }
                callbacks.onLibsLoaded(libs.toArray(new String[libs.size()]));
            }
        });
    }

    public NativeUtil usePreferredABI() {
        this.usePreferred = true;
        return this;
    }

    public interface Callbacks {
        void onError(String errorMessage);

        void onDownloadProgress(int progress);

        void onDownloadComplete(File zipFile);

        void onLibsLoaded(String[] libPaths);
    }
}
