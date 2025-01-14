package com.samourai.wallet.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;


public class NotificationsFactory {

    public static NotificationManager mNotificationManager;
    private static Context context = null;
    private static String ChannelID = "SAM_PAYMENTS";
    private static NotificationsFactory instance = null;

    private NotificationsFactory() {
        ;
    }

    public static NotificationsFactory getInstance(Context ctx) {

        context = ctx;
        if (instance == null) {
            instance = new NotificationsFactory();
        }

        return instance;
    }

    public void clearNotification(int id) {
        mNotificationManager.cancel(id);
    }

    public void setNotification(String title, String marquee, String text, int drawable, Class cls, int id) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannel(context);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, ChannelID)
                .setSmallIcon(drawable)
                .setContentTitle(title)
                .setContentText(text)
                .setTicker(marquee)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        Intent notifyIntent = new Intent(context, cls);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mBuilder.setContentIntent(intent);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationManager.notify(id, mBuilder.build());

    }

    private static void createNotificationChannel(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Payments";
            String description = "Alerts for new payments";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(ChannelID, name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
 