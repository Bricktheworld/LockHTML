package com.github.bricktheworld.lockscreenoverlay;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.widget.Button;
import android.widget.Toast;

import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.regex.Pattern;

import static com.rvalerio.fgchecker.Utils.hasUsageStatsPermission;

public class MainActivity extends Activity {

    private final static int AccessibilityRequestCode = 1;
    final Handler handler = new Handler();
    private Button chooseFilesButton;
    private Button chooseImageButton;

    public static final int PERMISSIONS_REQUEST_CODE = 00000;
    public static final int FILE_PICKER_REQUEST_CODE = 00001;
    public static String currentLockScreenPath;

    NotificationListener mService;
    boolean mBound = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestUsageStatsPermission();
        requestAccessibilityPermission();
        requestNotificationAccessPermission();
        setupFilePopup();
        Intent intent = new Intent(this, NotificationListener.class);
//        bindService(intent, connection, Context.BIND_AUTO_CREATE);




        SharedPreferences shre = PreferenceManager.getDefaultSharedPreferences(this);
        String _currentLockScreenPath = shre.getString("currentLockScreenPath", "");
        if(shre.getString("currentLockScreenPath", "").equals("")) {
            Toast.makeText(MainActivity.this, "LockScreen html file not found, please select and index.html file", Toast.LENGTH_LONG).show();
        } else {
            currentLockScreenPath = _currentLockScreenPath;
//            try {
//                LockScreenOverlayService.loadNewLockScreen();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
        }
    }

//    private ServiceConnection connection = new ServiceConnection() {
//
//        @Override
//        public void onServiceConnected(ComponentName className,
//                                       IBinder service) {
//            // We've bound to LocalService, cast the IBinder and get LocalService instance
//            NotificationListener.LocalBinder binder = (NotificationListener.LocalBinder) service;
//            mService = binder.getService();
//            mBound = true;
//            JavascriptInterfaceAPI.notificationListener = mService;
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName arg0) {
//            mBound = false;
//            JavascriptInterfaceAPI.notificationListener = null;
//        }
//    };


    void requestUsageStatsPermission() {
        if(!hasUsageStatsPermission(this)) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }
    void requestAccessibilityPermission() {
        if(PermissionChecker.checkSelfPermission(this, Manifest.permission.BIND_ACCESSIBILITY_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant LockScreenOverlay Accessibility Serivce Permission", Toast.LENGTH_LONG).show();
            startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), AccessibilityRequestCode);
        }
    }

    void requestNotificationAccessPermission () {

    }
    void setupFilePopup () {
        chooseFilesButton = findViewById(R.id.FileBrowser);
//        n = getApplicationContext().getSystemService(NotificationManager.class);
        chooseFilesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndOpenFilePicker();
                Toast.makeText(MainActivity.this, "Please select an index.html file", Toast.LENGTH_LONG).show();
            }
        });
        chooseImageButton = findViewById(R.id.wallpaperSetter);
        chooseImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 200 && resultCode == RESULT_OK) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            filePath = filePath.replace("index.html", "");
            String applicationDataPath = getFilesDir().getAbsolutePath();
            currentLockScreenPath = copyFileOrDirectory(filePath, applicationDataPath);
            Log.d("Dir", currentLockScreenPath);
            SharedPreferences shre = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor edit=shre.edit();
            edit.putString("currentLockScreenPath",currentLockScreenPath);
            edit.commit();
            // Do anything with file
            currentLockScreenPath = shre.getString("currentLockScreenPath", "");

            try {
                LockScreenOverlayService.loadNewLockScreen(currentLockScreenPath, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void checkPermissionsAndOpenFilePicker() {
        String permission = Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showError();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSIONS_REQUEST_CODE);
            }
        } else {
            openFilePicker();
        }
    }

    private void showError() {
        Toast.makeText(this, "Allow external storage reading", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openFilePicker();
                } else {
                    showError();
                }
            }
        }
    }

    private void openFilePicker () {
        new MaterialFilePicker()
                .withActivity(MainActivity.this)
                .withRequestCode(200)
                .withFilter(Pattern.compile(".*\\.html$")) // Filtering files and directories by file name using regexp
                .withFilterDirectories(false) // Set directories filterable (false by default)
                .withHiddenFiles(true) // Show hidden files and folders
                .start();
    }

    private void setImageBackground() {
        new MaterialFilePicker()
                .withActivity(MainActivity.this)
                .withRequestCode(201)
                .withFilter(Pattern.compile(".*\\.png$")) // Filtering files and directories by file name using regexp
                .withFilterDirectories(false) // Set directories filterable (false by default)
                .withHiddenFiles(true) // Show hidden files and folders
                .start();
    }
    public String copyFileOrDirectory(String srcDir, String dstDir) {

        try {
            File src = new File(srcDir);
            File dst = new File(dstDir, src.getName());

            if (src.isDirectory()) {
                Toast.makeText(MainActivity.this, "CopyingDir", Toast.LENGTH_SHORT).show();

                String files[] = src.list();
                int filesLength = files.length;
                for (int i = 0; i < filesLength; i++) {
                    String src1 = (new File(src, files[i]).getPath());
                    String dst1 = dst.getPath();
                    copyFileOrDirectory(src1, dst1);

                }
                return dst.getPath();
            } else {
                copyFile(src, dst);
                return dst.getPath();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();

        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }
}
