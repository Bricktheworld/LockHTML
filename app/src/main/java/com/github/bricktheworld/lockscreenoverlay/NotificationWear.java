package com.github.bricktheworld.lockscreenoverlay;


import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.UUID;

public class NotificationWear {
    public String id = UUID.randomUUID().toString();
    public String packageName = "";
    public PendingIntent pendingIntent;
    public ArrayList<android.support.v4.app.RemoteInput> remoteInputs = new ArrayList<>();
    public ArrayList<Notification> pages = new ArrayList<>();
    public Bundle bundle;
    public String tag;
}