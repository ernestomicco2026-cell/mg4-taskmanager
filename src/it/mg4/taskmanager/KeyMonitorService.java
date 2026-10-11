package it.mg4.taskmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servizio in primo piano che legge "getevent -l" via ADB: serve per i tasti del volante
 * che non arrivano al servizio di accessibilità (tasti gestiti direttamente dall'hardware).
 */
public class KeyMonitorService extends Service {

    private static final Pattern EV = Pattern.compile("^(/dev/input/\\S+):\\s+EV_KEY\\s+(\\S+)\\s+(DOWN|UP)");
    static final int ICON = 0x7f010000; // @drawable/ic

    private volatile boolean stop;
    private volatile AdbClient client;
    private Thread worker;

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, KeyMonitorService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, KeyMonitorService.class));
    }

    /** Serve il monitor getevent? (tasto grezzo configurato oppure apprendimento in corso) */
    public static boolean needed(Context c) {
        return KeyHub.SRC_RAW.equals(Adb.prefs(c).getString("key_src", null)) || KeyHub.isLearning();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("keys", Lang.tr("Tasto al volante", "Steering-wheel button"), NotificationManager.IMPORTANCE_MIN));
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        Notification n = new Notification.Builder(this, "keys")
                .setSmallIcon(ICON)
                .setContentTitle("Task Manager")
                .setContentText(Lang.tr("In ascolto del tasto al volante", "Listening for the steering-wheel button"))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, n);
        if (worker == null) {
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "getevent");
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        final Context ctx = getApplicationContext();
        while (!stop) {
            try {
                client = Adb.open(ctx, null);
                client.shellStream("getevent -l", new AdbClient.LineListener() {
                    @Override
                    public void onLine(String line) {
                        Matcher m = EV.matcher(line.trim());
                        if (!m.find()) return;
                        String key = m.group(2);
                        if (key.equals("BTN_TOUCH") || key.startsWith("BTN_TOOL")) return;
                        KeyHub.onRawKey(ctx, m.group(1), key, m.group(3).equals("DOWN"));
                    }
                });
            } catch (IOException ignored) {
            } finally {
                if (client != null) client.close();
                client = null;
            }
            if (!stop && !needed(ctx)) break;
            for (int i = 0; i < 100 && !stop; i++) {
                try {
                    Thread.sleep(100); // riprova tra 10 s (es. ADB non ancora pronto dopo l'avvio)
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        stop = true;
        AdbClient c = client;
        if (c != null) c.close();
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
