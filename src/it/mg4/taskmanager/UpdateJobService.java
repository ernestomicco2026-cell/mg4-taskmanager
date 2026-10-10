package it.mg4.taskmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.io.File;

/**
 * Controllo aggiornamenti in background (all'accensione dell'auto e ogni 6 ore).
 * Con ADB l'aggiornamento si installa da solo; senza ADB viene scaricato e una notifica
 * chiede un tocco per installarlo.
 */
public class UpdateJobService extends JobService {

    private static final int JOB_PERIODIC = 1;
    private static final int JOB_SOON = 2;
    private static final int NOTIF_ID = 2;

    /** Programma il controllo periodico (idempotente) e uno a breve, se è passato abbastanza tempo. */
    public static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        ComponentName cn = new ComponentName(c, UpdateJobService.class);
        if (js.getPendingJob(JOB_PERIODIC) == null) {
            js.schedule(new JobInfo.Builder(JOB_PERIODIC, cn)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(6L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build());
        }
        if (Updater.dueForAutoCheck(c) && js.getPendingJob(JOB_SOON) == null) {
            js.schedule(new JobInfo.Builder(JOB_SOON, cn)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(90 * 1000) // lascia il tempo all'auto di finire l'avvio
                    .setOverrideDeadline(30L * 60 * 1000)
                    .build());
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runCheck(getApplicationContext());
                } catch (Exception ignored) {
                }
                jobFinished(params, false);
            }
        }, "update-check").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    static void runCheck(Context c) throws Exception {
        if (!Updater.autoEnabled(c)) return;
        Updater.Release r = Updater.pending(c);
        if (r == null) {
            r = Updater.checkNewer(c);
            if (r == null) return;
            Updater.download(c, r, null);
            Updater.setPending(c, r);
        }
        File apk = Updater.downloadedFile(c);
        if (Updater.autoInstall(c)) {
            try {
                Adb.exec(c, "true", null);
                // Silenzioso; riapre l'app solo se era sullo schermo
                Updater.install(c, apk, null, MainActivity.visible);
                return;
            } catch (java.io.IOException noAdb) {
                // senza ADB serve un tocco: notifica qui sotto
            }
        }
        notifyReady(c, r);
    }

    private static void notifyReady(Context c, Updater.Release r) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("updates", "Aggiornamenti",
                NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_INSTALL_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "updates")
                .setSmallIcon(KeyMonitorService.ICON)
                .setContentTitle("Task Manager " + r.tag + " pronto")
                .setContentText("Tocca per installare l'aggiornamento")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_ID, n);
    }
}
