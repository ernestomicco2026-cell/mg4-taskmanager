package it.mg4.taskmanager;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;

/**
 * Edizione di sistema: si concede da sola i permessi che altrimenti richiederebbero i menu di Android
 * (bloccati sulle SWI68 R67/R71/R73). Nessun ADB, nessuna richiesta all'utente.
 *  - Accessibilità: scrive enabled_accessibility_services / accessibility_enabled (WRITE_SECURE_SETTINGS),
 *    aggiungendo il Task Manager senza mai togliere i servizi di altre app.
 *  - App ops GET_USAGE_STATS e REQUEST_INSTALL_PACKAGES: "consenti" (MANAGE_APP_OPS_MODES).
 */
public final class SelfSetup {

    private SelfSetup() {
    }

    /** True se l'app può scrivere le impostazioni di sistema protette (solo edizione di sistema). */
    public static boolean canWriteSecure(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    static ComponentName a11yComponent(Context c) {
        return new ComponentName(c.getPackageName(), KeyService.class.getName());
    }

    /** Il nostro servizio è nell'elenco dei servizi di accessibilità attivati? */
    static boolean inA11yList(String list, ComponentName me) {
        if (list == null || list.isEmpty() || list.equals("null")) return false;
        for (String s : list.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s.trim());
            if (me.equals(cn)) return true;
        }
        return false;
    }

    /**
     * Attiva il proprio servizio di accessibilità scrivendo direttamente le impostazioni.
     * Restituisce false se l'app non ha il permesso (edizione normale) o se la scrittura fallisce.
     * Non toglie mai dall'elenco i servizi di altre app.
     */
    public static boolean enableAccessibility(Context c) {
        if (!canWriteSecure(c)) return false;
        try {
            ContentResolver cr = c.getContentResolver();
            ComponentName me = a11yComponent(c);
            String cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (!inA11yList(cur, me)) {
                String comp = me.flattenToString();
                String nv = (cur == null || cur.trim().isEmpty() || cur.equals("null")) ? comp : cur + ":" + comp;
                if (!Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nv)) return false;
            }
            return Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception e) { // SecurityException se il permesso non c'è davvero
            return false;
        }
    }

    /** Attende che il servizio di accessibilità sia partito (massimo ms millisecondi). */
    public static boolean waitA11y(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (!KeyService.running && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        return KeyService.running;
    }

    /** Consente da sola l'accesso ai dati di utilizzo e l'installazione degli aggiornamenti. */
    public static boolean grantAppOps(Context c) {
        boolean usage = setAppOp(c, AppOpsManager.OPSTR_GET_USAGE_STATS, "OP_GET_USAGE_STATS");
        boolean install = setAppOp(c, "android:request_install_packages", "OP_REQUEST_INSTALL_PACKAGES");
        return usage && install;
    }

    /** AppOpsManager.setMode è un'API di sistema: la chiamiamo per riflessione, senza errori se manca. */
    private static boolean setAppOp(Context c, String opStr, String opField) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int uid = Process.myUid();
            if (ops.checkOpNoThrow(opStr, uid, c.getPackageName()) == AppOpsManager.MODE_ALLOWED) return true;
            int code;
            try {
                code = (Integer) AppOpsManager.class.getMethod("strOpToOp", String.class).invoke(null, opStr);
            } catch (Exception e) {
                code = AppOpsManager.class.getField(opField).getInt(null);
            }
            Method set = AppOpsManager.class.getMethod("setMode", int.class, int.class, String.class, int.class);
            set.invoke(ops, code, uid, c.getPackageName(), AppOpsManager.MODE_ALLOWED);
            return ops.checkOpNoThrow(opStr, uid, c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * All'avvio (edizione di sistema): attiva l'accessibilità se non è attiva e concede gli app ops.
     * Da chiamare in background. Restituisce: null se non c'era niente da fare,
     * true/false = esito dell'attivazione dell'accessibilità.
     */
    public static Boolean runAtStartup(Context c) {
        if (!canWriteSecure(c)) return null;
        grantAppOps(c);
        if (KeyService.running) return null;
        if (!enableAccessibility(c)) return false;
        return waitA11y(3000);
    }
}
