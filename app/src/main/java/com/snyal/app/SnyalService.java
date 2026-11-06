package com.snyal.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** Фоновый сервис: держит Python и веб-дашборд Snyal живыми, пока висит уведомление. */
public class SnyalService extends Service {
    static final String ACTION_STOP = "com.snyal.app.STOP";
    private static final String CHANNEL = "snyal";
    private static final int NOTIF_ID = 1;
    private static final String TAG = "Snyal";

    /** Адрес дашборда (с токеном). null, пока Python запускается. */
    static volatile String url;
    /** Текст ошибки, если Python не поднялся. */
    static volatile String error;

    private static boolean started;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAll();
            return START_NOT_STICKY;
        }
        showNotification();
        synchronized (SnyalService.class) {
            if (!started) {
                started = true;
                new Thread(this::runPython, "snyal-python").start();
            }
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void showNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel(CHANNEL, "Snyal", NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, SnyalService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Snyal работает")
                .setContentText("Дашборд запущен. Нажми, чтобы открыть.")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(0, "Остановить", stop)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14/15: тип фонового сервиса обязателен
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void runPython() {
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(getApplicationContext()));
            }
            error = null;
            url = Python.getInstance()
                    .getModule("snyal_android")
                    .callAttr("start", getFilesDir().getAbsolutePath(), 8777)
                    .toString();
            Log.i(TAG, "dashboard is up");
        } catch (Throwable t) {
            error = String.valueOf(t);
            Log.e(TAG, "python start failed", t);
        }
    }

    private void stopAll() {
        url = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        new Thread(() -> {
            try {
                if (Python.isStarted()) {
                    Python.getInstance().getModule("snyal_android").callAttr("stop");
                }
            } catch (Throwable t) {
                Log.w(TAG, "stop failed", t);
            }
            // Python-потоки и root-shell живут в процессе — гасим процесс целиком
            Process.killProcess(Process.myPid());
        }, "snyal-stop").start();
    }
}
