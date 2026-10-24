package it.mg4.taskmanager;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Disinstallazione delle app installate dall'utente (mai quelle di sistema).
 *  - Edizione di sistema: PackageInstaller.uninstall con DELETE_PACKAGES, senza altre conferme
 *    (la conferma la chiede già il Task Manager).
 *  - Con ADB: "pm uninstall".
 *  - Altrimenti: la schermata di disinstallazione di Android.
 */
public final class Uninstall {

    /** Disinstallazione avviata: l'esito arriva a UninstallReceiver. */
    public static final int STARTED = 0;
    /** Disinstallata (ADB). */
    public static final int DONE = 1;
    /** Serve la schermata di Android: chi chiama apre systemDialog(). */
    public static final int NEEDS_DIALOG = 2;
    /** Non riuscita: il motivo è in lastError. */
    public static final int FAILED = 3;

    static volatile String lastError;

    private Uninstall() {
    }

    /** True solo per le app installate dall'utente (non di sistema, non aggiornamenti di app di sistema, non noi). */
    public static boolean allowed(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            return isUserApp(ai);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean isUserApp(ApplicationInfo ai) {
        return (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
    }

    /** App installate dall'utente, per la scelta da Impostazioni. */
    public static List<ApplicationInfo> userApps(Context c) {
        List<ApplicationInfo> out = new ArrayList<ApplicationInfo>();
        for (ApplicationInfo ai : c.getPackageManager().getInstalledApplications(0)) {
            if (isUserApp(ai) && !ai.packageName.equals(c.getPackageName())) out.add(ai);
        }
        return out;
    }

    /** Da chiamare in background. */
    public static int start(Context c, String pkg, AdbClient.AuthListener l) {
        lastError = null;
        if (!allowed(c, pkg)) {
            lastError = Lang.tr("Le app di sistema non si possono disinstallare", "System apps can't be uninstalled",
                    "Las apps del sistema no se pueden desinstalar", "Os apps do sistema não podem ser desinstalados",
                    "Les apps système ne peuvent pas être désinstallées", "System-Apps können nicht deinstalliert werden");
            return FAILED;
        }
        // Edizione di sistema: permesso di firma DELETE_PACKAGES
        if (c.checkSelfPermission("android.permission.DELETE_PACKAGES") == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent i = new Intent(c, UninstallReceiver.class).putExtra(UninstallReceiver.EXTRA_PKG, pkg);
                PendingIntent pi = PendingIntent.getBroadcast(c, pkg.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT);
                c.getPackageManager().getPackageInstaller().uninstall(pkg, pi.getIntentSender());
                return STARTED;
            } catch (Exception e) {
                // si prova con ADB o con la schermata di Android
            }
        }
        // Con ADB
        try {
            String out = Adb.exec(c, "pm uninstall " + Adb.q(pkg), l).trim();
            if (out.contains("Success")) {
                done(pkg);
                return DONE;
            }
            lastError = out.isEmpty() ? null : out;
            return lastError == null ? NEEDS_DIALOG : FAILED;
        } catch (IOException noAdb) {
            return NEEDS_DIALOG;
        }
    }

    /** Intent per la schermata di disinstallazione di Android (modalità base). */
    public static Intent systemDialog(String pkg) {
        Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + pkg));
        i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        return i;
    }

    /** Dopo una disinstallazione riuscita: aggiorna cache ed esclusioni. */
    static void done(String pkg) {
        Tasks.invalidatePackages();
        List<String> one = new ArrayList<String>();
        one.add(pkg);
        Tasks.markKilled(one);
    }
}
