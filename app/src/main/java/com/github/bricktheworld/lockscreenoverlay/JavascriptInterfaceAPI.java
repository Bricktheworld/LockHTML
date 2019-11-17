package com.github.bricktheworld.lockscreenoverlay;

import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class JavascriptInterfaceAPI {

    Context context;
    /** Instantiate the interface and set the context */
    JavascriptInterfaceAPI(Context _Context) {
        context = _Context;
    }

    public static NotificationListener notificationListener = null;

    /** Show a toast from the web page */
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public int getBattery() {
        return LockScreenOverlayService.batteryLevel;
    }

    @JavascriptInterface
    public void enterPasswordNum(int num) throws Exception {
        LockScreenOverlayService.runShellCommand("adb shell input text " + num);
    }

    @JavascriptInterface
    public void unlockPassword() throws  Exception{
        LockScreenOverlayService.runShellCommand("adb shell input keyevent 66");

    }
    @JavascriptInterface
    public void cancelNotification(String key) {
        if(!LockScreenOverlayService.bound || LockScreenOverlayService.service == null)
            return;
        Log.d("Notification_id", key);
        LockScreenOverlayService.service.removeNotification(key);
    }
    @JavascriptInterface
    public void deleteNumPassword() throws Exception {
        LockScreenOverlayService.runShellCommand("adb shell input keyevent 67");
    }
}
