package com.sunny.nativeutil;

import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.YailList;
import com.sunny.util.NativeUtil;

import java.io.File;

public class NativeUtils extends AndroidNonvisibleComponent implements NativeUtil.Callbacks{

  public NativeUtils(ComponentContainer container) {
    super(container.$form());
  }

  @SimpleFunction()
  public YailList GetSupportedSupportedABIs(){
    return YailList.makeList(NativeUtil.getSupportedABIs());
  }
  @SimpleFunction()
  public String GetPreferredABI(){
    return NativeUtil.getPreferredABI();
  }

  @SimpleFunction()
  public void LoadFromFile(String zipFile){
    new NativeUtil(form,this).usePreferredABI().unzipAndLoad(new File(zipFile));
  }

  @SimpleFunction()
  public void LoadFromUrl(String zipUrl){
    new NativeUtil(form,this).usePreferredABI().downloadAndLoad(zipUrl);
  }
  @SimpleEvent()
  public void ErrorOccurred(String errorMsg){
    EventDispatcher.dispatchEvent(this,"ErrorOccurred",errorMsg);
  }
  @SimpleEvent()
  public void GotDownloadProgress(int progress){
    EventDispatcher.dispatchEvent(this,"GotDownloadProgress",progress);
  }
  @SimpleEvent()
  public void ZipDownloaded(String zipFile){
    EventDispatcher.dispatchEvent(this,"ZipDownloaded",zipFile);
  }
  @SimpleEvent()
  public void LibsLoaded(YailList libPaths){
    EventDispatcher.dispatchEvent(this,"LibsLoaded",libPaths);
  }

  @Override
  public void onError(final String errorMessage) {
    form.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        ErrorOccurred(errorMessage);
      }
    });
  }

  @Override
  public void onDownloadProgress(final int progress) {
    form.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        GotDownloadProgress(progress);
      }
    });
  }

  @Override
  public void onDownloadComplete(final File zipFile) {
    form.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        ZipDownloaded(zipFile.getAbsolutePath());
      }
    });
  }

  @Override
  public void onLibsLoaded(final String[] libPaths) {
    form.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        LibsLoaded(YailList.makeList(libPaths));
      }
    });
  }
}
