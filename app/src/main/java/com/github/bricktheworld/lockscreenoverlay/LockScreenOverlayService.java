package com.github.bricktheworld.lockscreenoverlay;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Switch;

import com.rvalerio.fgchecker.AppChecker;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;

public class LockScreenOverlayService extends AccessibilityService {
    private static final String TAG = "CustomAccessibility";
    private static RelativeLayout mainLayout;
    private static ConstraintLayout mainActivityLayout;
    private static WindowManager.LayoutParams mainWindow;
    private static WindowManager wm;
    private static WebView webView;
    private Button hideButton;
    private static Context context;
    private static AppChecker appChecker = new AppChecker();
    static final Point windowSize = new Point();
    private static String currentApp;
    private static Boolean _show = false;
    private static Switch enableDisableSwitch;
    public static int batteryLevel;
    private static String filePath;
    private static AlarmManager alarmManager;
    private static Long nextAlarmTime;
    private static boolean ableToLoadLockScreen = true;
    final static Handler handler = new Handler();
    private boolean onLockscreen = false;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private static SharedPreferences shre;

    public static NotificationListener service;
    public static boolean bound = false;



    public static boolean hideShowFunction(Boolean show) throws PackageManager.NameNotFoundException, JSONException, IOException {
        _show = show;

        Log.d("HideShow", "Running");
        Long currentTime = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());
        Log.d("alarm", "nextAlarm: " + String.valueOf(nextAlarmTime));
        Log.d("alarm", "currentTime: " + String.valueOf(currentTime));
        if(currentTime >= nextAlarmTime) {
            return false;
        }
        nextAlarmTime = TimeUnit.MILLISECONDS.toMinutes(alarmManager.getNextAlarmClock().getTriggerTime());
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onServiceConnected() {
        //accessibility stuff
        AccessibilityServiceInfo config = new AccessibilityServiceInfo();
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        if (Build.VERSION.SDK_INT >= 16)
            //Just in case this helps
            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        setServiceInfo(config);

        Intent intent = new Intent(this, NotificationListener.class);
        intent.setAction("notificationListenerBind");
        bindService(intent, connection, Context.BIND_AUTO_CREATE);




        context = this;
        shre = PreferenceManager.getDefaultSharedPreferences(context);

        //inflate lockscreenlayout as mainLayout.
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.activity_lock_screen, null);
        mainLayout = view.findViewById(R.id.lockscreenLayout);
        view = inflater.inflate(R.layout.activity_main, null);
        mainActivityLayout = (ConstraintLayout) view.findViewById(R.id.MainActivity);
        //setup the lock and unlock detector
        setupLockscreenReceiver();
        //setup the battery receiver for the api
        setupBatteryReceiver();
        //get the windowmanager and set the windowsize
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealSize(windowSize);
        //setup the main overlay window containing the webview
        setupMainWindow();
//        setMainWindowUntouchable();
        wm.removeView(mainLayout);
        setupEmergencyHideButton();
        setupeEnabeDisableSwitch();

        //get webview from mainlayout
        webView = (WebView) mainLayout.findViewById(R.id.MainWebView);
        webView.setWebViewClient(new WebViewClient());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebContentsDebuggingEnabled(true);
        webView.addJavascriptInterface(new JavascriptInterfaceAPI(this), "System");
//        webView.addJavascriptInterface(new NotificationListener(), "System");

        if(!shre.getString("currentLockScreenPath", "").equals("")) {
            try {
                webView.clearCache(false);
                loadNewLockScreen(shre.getString("currentLockScreenPath", ""), false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        alarmManager = (AlarmManager) getSystemService(context.ALARM_SERVICE);
        nextAlarmTime = TimeUnit.MILLISECONDS.toMinutes(alarmManager.getNextAlarmClock().getTriggerTime());

        //wakelock
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LockScreenOverlay::ReloadLS");





    }



    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
//        Log.d("CurrentActivity", "windowChanged");
        if(accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d("CurrentActivity", accessibilityEvent.toString());
            if(accessibilityEvent.getText().contains("Lock screen.") && onLockscreen) {
                setMainWindowTouchable();
                reloadLockScreen();
                mainLayout.setVisibility(View.VISIBLE);
                updateLockScreenNotifs();
                Log.d("CurrentActivity", "back to ls");
                onLockscreen = false;
                return;
            }
            if(accessibilityEvent.getPackageName() != null && accessibilityEvent.getClassName() != null) {
                ComponentName componentName = new ComponentName(accessibilityEvent.getPackageName().toString(), accessibilityEvent.getClassName().toString());
                ActivityInfo activityInfo = tryGetActivity(componentName);
                boolean isActivity = activityInfo != null;
                if(isActivity) {
                    if(componentName.flattenToShortString().contains("Camera")){
                        setMainWindowUntouchable();
                        onLockscreen = true;
                    }
                }
            }
        }
    }
    private ActivityInfo tryGetActivity(ComponentName componentName) {
        try{
            return getPackageManager().getActivityInfo(componentName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    public void onInterrupt() {
    }

    private int getNavBarHeight () {
        Resources resources = this.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId)/2;
        }
        return 0;
    }

    private void setupLockscreenReceiver () {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.setPriority(998);
        registerReceiver(DisplayStateReceiver, filter);
    }

    private void setupBatteryReceiver() {
        this.registerReceiver(this.batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void setupMainWindow () {
        mainWindow = new WindowManager.LayoutParams();
        mainWindow.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        mainWindow.format = PixelFormat.TRANSLUCENT;
        mainWindow.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mainWindow.width = windowSize.x;
        mainWindow.height = windowSize.y;
        mainWindow.gravity = Gravity.CENTER;
        mainWindow.y = getNavBarHeight();
        mainWindow.width = 0;
        wm.addView(mainLayout, mainWindow);
    }
    private void setupEmergencyHideButton () {
        hideButton = (Button) mainLayout.findViewById(R.id.hideButton);
        hideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _show = false;
                setMainWindowUntouchable();
            }
        });
    }
    private void setupeEnabeDisableSwitch () {
        enableDisableSwitch = (Switch) mainActivityLayout.findViewById(R.id.enableDisableSwitch);
    }
    public static void loadNewLockScreen (String path, Boolean putInSP) throws IOException {
        if(putInSP) {
            SharedPreferences.Editor edit = shre.edit();
            edit.putString("currentLockScreenPath", path);
            edit.commit();
        }
        filePath = "File://" + path + "/index.html";
        webView.loadUrl(filePath);
    }
    private static void reloadLockScreen () {
        webView.loadUrl(filePath);
    }

    public static void updateLockScreenNotifs() {
        String notificationsArray = null;
        try {
            if(getNotificationApps().toString().equals("[null]")) {
                notificationsArray = "[]";
            } else {
                notificationsArray = getNotificationApps().toString();
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        notificationsArray = notificationsArray.replace("null", "");
        Log.d("Notifications", "javascript: " + "notificationUpdate(" + notificationsArray + ")");
        webView.evaluateJavascript("javascript: " + "notificationUpdate(" + notificationsArray + ")", null);
    }



    private static void setMainWindowUntouchable () {
//        mainWindow.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
//        mainWindow.width = 0;
//        if(webView != null) {
//            webView.setVisibility(View.INVISIBLE);
//        }

//        wm.updateViewLayout(mainLayout, mainWindow);
        try {
            wm.removeView(mainLayout);
        } catch(Exception e) {
            e.printStackTrace();
        }
//        mainLayout.setVisibility(View.INVISIBLE);
//        mainWindow.width = 0;
//        wm.updateViewLayout(mainLayout, mainWindow);

    }
    private static void setMainWindowTouchable () {
        mainWindow.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mainWindow.width = windowSize.x;
        try {
            wm.addView(mainLayout, mainWindow);
            Log.d("displayTesting", "added Window");
        } catch (Exception e) {
            e.printStackTrace();
        }
//        mainLayout.setVisibility(View.INVISIBLE);

    }


    private BroadcastReceiver batteryReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        }
    };

    private static JSONArray getNotificationApps () throws PackageManager.NameNotFoundException, JSONException {
        StatusBarNotification[] activeNotifications = NotificationListener.getCurrentNotifications();
        PackageManager packageManager = context.getPackageManager();
        String[] notificationApps = new String[activeNotifications.length];
        int length = 0;
        for (int i = activeNotifications.length - 1; i >= 0; i--) {

            if(activeNotifications[i].isClearable()) {
                Notification notification = activeNotifications[i].getNotification();
                String Title = notification.extras.getString(notification.EXTRA_TITLE);
                if(Title == null) {
                    Log.d("notifications", String.valueOf(activeNotifications[i].getId()));
                } else {
                    Log.d("notifications", "OK: " + String.valueOf(activeNotifications[i].getId()));
                    length++;
                }
            }
        }
        Log.d("notifications", String.valueOf(length));
        String[][] notificiationApps = new String[length][4];

        for (int i = length - 1; i >=0; i--) {
            Notification notification = activeNotifications[i].getNotification();
            String Title = notification.extras.getString(notification.EXTRA_TITLE);
            if(activeNotifications[i].isClearable()) {
                Log.d("Json", "not empty");
                String packageName = activeNotifications[i].getPackageName();
                ApplicationInfo appInfo = null;
                appInfo = packageManager.getApplicationInfo(packageName, 0);
                String appName = (String) packageManager.getApplicationLabel(appInfo);
                notificiationApps[i][0] = appName;
                notificationApps[i] = appName;
                String Content = notification.extras.getString(notification.EXTRA_TEXT);
                notificiationApps[i][1] = Title;
                notificiationApps[i][2] = Content;
                notificiationApps[i][3] = String.valueOf(activeNotifications[i].getKey());

                Log.d("notification_key", activeNotifications[i].getKey());
            }
        }
        JSONArray jsonArray = new JSONArray(notificationApps);
        JSONArray jsonArray1 = new JSONArray(notificiationApps);
        Log.d("Notifications", "javascript: " + "notificationUpdate(" + jsonArray1 + ")");


        return jsonArray1;
    }

    public static void runShellCommand(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }



    private BroadcastReceiver DisplayStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    wakeLock.acquire(5000);
                    Log.d("displayTesting", "screenOff");
                    onLockscreen = false;
                    setMainWindowTouchable();
                    reloadLockScreen();

                    try {
                        LockScreenOverlayService.hideShowFunction(true);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case Intent.ACTION_SCREEN_ON:
                    if (keyguardManager.isKeyguardLocked()) {
                        onLockscreen = true;
                        mainLayout.setVisibility(View.VISIBLE);
                        updateLockScreenNotifs();
                        try {
                            LockScreenOverlayService.hideShowFunction(true);
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case Intent.ACTION_USER_PRESENT:
                    onLockscreen = false;
                    setMainWindowUntouchable();
                    reloadLockScreen();
                    try {
                        LockScreenOverlayService.hideShowFunction(false);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    };

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder _service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            NotificationListener.LocalBinder binder = (NotificationListener.LocalBinder) _service;
            service = binder.getService();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };


}
