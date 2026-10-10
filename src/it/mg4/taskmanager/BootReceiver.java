package it.mg4.taskmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * All'accensione dell'auto (e dopo un aggiornamento dell'app):
 * riattiva l'ascolto del tasto al volante e programma il controllo aggiornamenti.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (KeyMonitorService.needed(c)) {
            try {
                KeyMonitorService.start(c);
            } catch (Exception ignored) {
            }
        }
        UpdateJobService.schedule(c);
    }
}
