package it.mg4.taskmanager;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Debug;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modalità sistema: disponibile solo quando l'APK è firmato con la chiave platform dell'auto.
 * In quel caso l'app ottiene i permessi di firma (FORCE_STOP_PACKAGES, REAL_GET_TASKS,
 * PACKAGE_USAGE_STATS) e può elencare e chiudere le app senza ADB, come la modalità completa.
 */
public final class SysPriv {

    private SysPriv() {
    }

    public static boolean available(Context c) {
        return c.checkSelfPermission("android.permission.FORCE_STOP_PACKAGES") == PackageManager.PERMISSION_GRANTED;
    }

    /** Elenco reale dei processi in esecuzione (REAL_GET_TASKS fa vedere tutte le app, non solo la nostra). */
    public static List<Tasks.Entry> list(Context ctx, boolean includeBackground) {
        PackageManager pm = ctx.getPackageManager();
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        Set<String> launchable = Tasks.launchablePackages(pm);
        Set<String> exclude = Tasks.excludedPackages(ctx, pm);
        Set<String> prot = Adb.protectedApps(ctx);
        String fg = foregroundPackage(am);

        Map<String, Tasks.Entry> byPkg = new HashMap<String, Tasks.Entry>();
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return new ArrayList<Tasks.Entry>();
        java.util.Map<Integer, Long> mem = memoryBatch(am, procs);
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.pkgList == null) continue;
            Long rssVal = mem.get(p.pid);
            long rssKb = rssVal == null ? 0 : rssVal;
            for (String pkg : p.pkgList) {
                if (exclude.contains(pkg)) continue;
                boolean canLaunch = launchable.contains(pkg);
                if (!canLaunch && !includeBackground) continue;
                ApplicationInfo ai;
                try {
                    ai = pm.getApplicationInfo(pkg, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }
                if (ai.uid < 10000 && !canLaunch) continue;
                Tasks.Entry en = byPkg.get(pkg);
                if (en == null) {
                    en = new Tasks.Entry();
                    en.pkg = pkg;
                    Tasks.fillMeta(pm, pkg, ai, en);
                    en.launchable = canLaunch;
                    en.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    en.isProtected = prot.contains(pkg);
                    en.foreground = pkg.equals(fg);
                    en.rssKb = 0;
                    byPkg.put(pkg, en);
                }
                en.rssKb += rssKb;
                en.processes++;
            }
        }
        return new ArrayList<Tasks.Entry>(byPkg.values());
    }

    /** Una sola chiamata binder per tutti i processi, invece di una per pid. */
    private static java.util.Map<Integer, Long> memoryBatch(ActivityManager am, List<ActivityManager.RunningAppProcessInfo> procs) {
        java.util.Map<Integer, Long> out = new HashMap<Integer, Long>();
        int[] pids = new int[procs.size()];
        for (int i = 0; i < procs.size(); i++) pids[i] = procs.get(i).pid;
        try {
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(pids);
            if (mi != null) for (int i = 0; i < mi.length && i < pids.length; i++) {
                out.put(pids[i], (long) mi[i].getTotalPss());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String foregroundPackage(ActivityManager am) {
        if (KeyService.lastForeground != null) return KeyService.lastForeground;
        try {
            List<ActivityManager.RunningAppProcessInfo> l = am.getRunningAppProcesses();
            if (l != null) for (ActivityManager.RunningAppProcessInfo p : l) {
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && p.pkgList != null && p.pkgList.length > 0) {
                    return p.pkgList[0];
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Chiusura istantanea con ActivityManager.forceStopPackage (richiede FORCE_STOP_PACKAGES). */
    public static boolean forceStop(Context ctx, List<String> pkgs) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        boolean all = true;
        Method m = null;
        try {
            m = ActivityManager.class.getMethod("forceStopPackage", String.class);
        } catch (NoSuchMethodException e) {
            return false;
        }
        for (String p : pkgs) {
            try {
                m.invoke(am, p);
            } catch (Exception e) {
                all = false;
            }
        }
        Tasks.markKilled(pkgs);
        return all;
    }
}
