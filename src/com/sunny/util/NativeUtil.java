package com.sunny.util;

import android.content.Context;
import android.os.Build;
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
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NativeUtil {
    private final Context context;
    private final Callbacks callbacks;
    private boolean usePreferred = false;
    public NativeUtil(Context context,Callbacks callbacks){
        this.context = context;
        this.callbacks = callbacks;
    }

    public static String[] getSupportedABIs(){
        return Build.SUPPORTED_64_BIT_ABIS.length > 0 ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
    }
    public static String getPreferredABI(){
        return getSupportedABIs()[0];
    }

    private String getNameFromUrl(String url){
        return url.substring(url.lastIndexOf("/") + 1);
    }

    public void downloadAndLoad(final String zipUrl){
        AsynchUtil.runAsynchronously(new Runnable() {
            @Override
            public void run() {
                try{
                    File dFile = new File(context.getFilesDir(),"/zip/" + getNameFromUrl(zipUrl));
                    if (!(dFile.length() > 0)) {
                        if (!dFile.getParentFile().exists()){
                            boolean ignored = dFile.getParentFile().mkdirs();
                        }
                        FileOutputStream fos = new FileOutputStream(dFile);
                        URL url = new URL(zipUrl);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        if (connection.getResponseCode() == 200) {
                            long totalSize = connection.getContentLengthLong();
                            long downloadedSize = 0;
                            byte[] buffer = new byte[1024];
                            int bufferLength;
                            InputStream in = new BufferedInputStream(connection.getInputStream());
                            while ((bufferLength = in.read(buffer)) > 0) {
                                fos.write(buffer, 0, bufferLength);
                                downloadedSize += bufferLength;
                                final int progress = (int) (100 * downloadedSize / totalSize);
                                callbacks.onDownloadProgress(progress);
                            }
                            callbacks.onDownloadComplete(dFile);
                        } else {
                            callbacks.onError("Unable to download zip file");
                        }
                    }else {
                        callbacks.onDownloadComplete(dFile);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    callbacks.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        });
    }

    private String parseName(String name){
        if (name.contains(".")){
            return name.split(".")[0];
        }
        return name;
    }

    public void unzipAndLoad(final File zFile){
        try {
            final List<String> unzippedLibs = new ArrayList<>();
            File unzipFolder = new File(context.getFilesDir(),"lib/"+parseName(zFile.getName()));
            ZipFile zipFile = new ZipFile(zFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            if (zipFile.size() > 1){
                if (usePreferred){
                    while (entries.hasMoreElements()){
                        ZipEntry entry = entries.nextElement();
                        if (entry.getName().toLowerCase().contains(getPreferredABI().toLowerCase())){
                            File libFile = new File(unzipFolder,entry.getName());
                            if (!libFile.getParentFile().exists()) {
                                libFile.getParentFile().mkdirs();
                            }
                            unzippedLibs.add(libFile.getAbsolutePath());
                            copy(zipFile.getInputStream(entry),new FileOutputStream(libFile));
                        }
                    }
                }else {
                    final List<String> supported = new ArrayList<>();
                    for (String s:Build.SUPPORTED_ABIS
                         ) {
                        supported.add(s.toLowerCase());
                    }
                    while (entries.hasMoreElements()){
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        String archName = name.split("/")[0];
                        if (supported.contains(archName)) {
                            File libFile = new File(unzipFolder, name);
                            unzippedLibs.add(libFile.getAbsolutePath());
                            if (!libFile.getParentFile().exists()) {
                                libFile.getParentFile().mkdirs();
                            }
                            copy(zipFile.getInputStream(entry), new FileOutputStream(libFile));
                        }
                    }
                }
            }else{
                while (entries.hasMoreElements()){
                    ZipEntry entry = entries.nextElement();
                    File libFile = new File(unzipFolder,entry.getName());
                    if (!libFile.getParentFile().exists()) {
                        libFile.getParentFile().mkdirs();
                    }
                    unzippedLibs.add(libFile.getAbsolutePath());
                    copy(zipFile.getInputStream(entry),new FileOutputStream(libFile));
                }
            }
            zipFile.close();
            loadLibs(unzippedLibs);
        }catch (Exception e){
            callbacks.onError(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    public void loadLibs(final List<String> libs){
        context.getMainExecutor().execute(new Runnable() {
            @Override
            public void run() {
                for (String lib:libs
                ) {
                    try {
                        System.load(lib);
                    }catch (Exception e){
                        callbacks.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                    }
                }
                callbacks.onLibsLoaded(libs.toArray(new String[libs.size()]));
            }
        });
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
