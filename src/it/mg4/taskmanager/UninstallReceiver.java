package it.mg4.taskmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Esito della disinstallazione avviata con PackageInstaller (edizione di sistema). */
public class UninstallReceiver extends BroadcastReceiver {

    static final String EXTRA_PKG = "uninstall_pkg";

    @Override
    public void onReceive(Context c, Intent intent) {
        Lang.init(c);
        String pkg = intent.getStringExtra(EXTRA_PKG);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        boolean ok = status == PackageInstaller.STATUS_SUCCESS;
        if (ok) Uninstall.done(pkg);
        MainActivity.onUninstallResult(pkg, ok, ok ? null : intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
}
