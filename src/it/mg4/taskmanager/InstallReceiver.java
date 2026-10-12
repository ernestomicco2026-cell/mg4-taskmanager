package it.mg4.taskmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Riceve l'esito dell'installer di sistema e, se serve, mostra la schermata di conferma. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Lang.init(c);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (Exception e) {
                    Toast.makeText(c, Lang.tr("Impossibile aprire l'installer di Android", "Couldn't open the Android installer",
                "No se puede abrir el instalador de Android", "Não foi possível abrir o instalador do Android", "Impossible d'ouvrir le programme d'installation Android", "Android-Installer konnte nicht geöffnet werden"), Toast.LENGTH_LONG).show();
                }
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, Lang.tr("Aggiornamento non installato", "Update not installed",
                "Actualización no instalada", "Atualização não instalada", "Mise à jour non installée", "Update nicht installiert") + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
        }
    }
}
