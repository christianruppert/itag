package solutions.s4y.itag.ble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

import solutions.s4y.itag.BuildConfig;
import solutions.s4y.itag.MainActivity;
import solutions.s4y.itag.R;

public class ITagsService extends Service {
    private static final int FOREGROUND_ID = 1;
    private static final String CHANNEL_ID = "itag0";
    private boolean mChannelCreated;

    private static final String T = ITagsService.class.getName();

    private HashMap<String, ITagGatt> mGatts = new HashMap<>(4);

    public class GattBinder extends Binder {
        public ITagsService getService() {
            return ITagsService.this;
        }
    }

    private IBinder mBinder = new GattBinder();

    public ITagsService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        connect();
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (BuildConfig.DEBUG) {
            Log.d(T, "onDestroy");
        }

        for (ITagGatt gatt : mGatts.values()) {
            gatt.disconnect();
            gatt.close();
        }

        super.onDestroy();
    }

    @NotNull
    public ITagGatt getGatt(@NotNull final String addr, boolean connect) {
        ITagGatt gatt = mGatts.get(addr);
        if (gatt == null) {
            gatt = new ITagGatt(addr);
            mGatts.put(addr, gatt);
            if (connect) gatt.connect(this);
        }
        return gatt;
    }

    public void connect() {
        for (ITagDevice device : ITagsDb.getDevices(this)) {
            getGatt(device.addr, true);
        }
    }

    public void alert(@NotNull final String addr) {
        final ITagGatt gatt = mGatts.get(addr);
        if (gatt.isAlert()) {
            gatt.stopAlert();
        } else {
            gatt.alert();
        }
    }

    public void addToForeground() {
        Notification.Builder builder = new Notification.Builder(this);
        builder
                .setSmallIcon(R.drawable.app)
                .setContentTitle(getString(R.string.service_in_background));
        Intent intent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        builder.setContentIntent(pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            }
            builder.setChannelId(CHANNEL_ID);
        }
        Notification notification = builder.build();
        startForeground(FOREGROUND_ID, notification);
    }

    public void removeFromForeground() {
        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (!mChannelCreated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
//            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            //          channel.setDescription(description);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                mChannelCreated=true;
            }
        }
    }

    public static void start(Context context){
        if (ITagsDb.getDevices(context).size()>0){
            Intent intent = new Intent(context, ITagsService.class);
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        if (ITagsDb.getDevices(context).size() == 0) {
            context.stopService(new Intent(context, ITagsService.class));
        }
    }
}