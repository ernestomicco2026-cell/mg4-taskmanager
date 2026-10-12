package it.mg4.taskmanager;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Debug;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Elenco reale dei processi in esecuzione (REAL_GET_TASKS fa vedere tutte le app, non solo la nostra).
     * Veloce: la RAM non viene letta qui ma dopo, con fillMemory(), solo per le app mostrate.
     */
    public static List<Tasks.Entry> list(Context ctx, boolean includeBackground) {
        PackageManager pm = ctx.getPackageManager();
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return new ArrayList<Tasks.Entry>();
        Set<String> launchable = Tasks.launchablePackages(pm);
        Set<String> exclude = Tasks.excludedPackages(ctx, pm);
        Set<String> prot = Adb.protectedApps(ctx);
        String fg = foregroundPackage(procs);

        Map<String, Tasks.Entry> byPkg = new LinkedHashMap<String, Tasks.Entry>();
        Map<String, List<Integer>> pids = new HashMap<String, List<Integer>>();
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.pkgList == null) continue;
            for (String pkg : p.pkgList) {
                if (exclude.contains(pkg)) continue;
                boolean canLaunch = launchable.contains(pkg);
                if (!canLaunch && !includeBackground) continue;
                Tasks.Entry en = byPkg.get(pkg);
                if (en == null) {
                    Tasks.Meta m = Tasks.meta(pm, pkg);
                    if (m == null || (m.uid < 10000 && !canLaunch)) continue;
                    en = Tasks.entry(pkg, m, canLaunch, prot, fg);
                    byPkg.put(pkg, en);
                    pids.put(pkg, new ArrayList<Integer>());
                }
                en.processes++;
                pids.get(pkg).add(p.pid);
            }
        }
        for (Tasks.Entry en : byPkg.values()) {
            List<Integer> l = pids.get(en.pkg);
            en.pids = new int[l.size()];
            for (int i = 0; i < l.size(); i++) en.pids[i] = l.get(i);
        }
        return new ArrayList<Tasks.Entry>(byPkg.values());
    }

    /** RAM (PSS) dei processi delle voci indicate, con una sola chiamata binder. */
    static void fillMemory(Context ctx, List<Tasks.Entry> entries) {
        int n = 0;
        for (Tasks.Entry e : entries) if (e.pids != null) n += e.pids.length;
        if (n == 0) return;
        int[] all = new int[n];
        int k = 0;
        for (Tasks.Entry e : entries) if (e.pids != null) for (int pid : e.pids) all[k++] = pid;
        Map<Integer, Long> mem = new HashMap<Integer, Long>();
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(all);
            if (mi != null) for (int i = 0; i < mi.length && i < all.length; i++) {
                mem.put(all[i], (long) mi[i].getTotalPss());
            }
        } catch (Exception ignored) {
            return;
        }
        for (Tasks.Entry e : entries) {
            if (e.pids == null) continue;
            long kb = 0;
            for (int pid : e.pids) {
                Long v = mem.get(pid);
                if (v != null) kb += v;
            }
            e.rssKb = kb;
        }
    }

    private static String foregroundPackage(List<ActivityManager.RunningAppProcessInfo> procs) {
        if (KeyService.lastForeground != null) return KeyService.lastForeground;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && p.pkgList != null && p.pkgList.length > 0) {
                return p.pkgList[0];
            }
        }
        return null;
    }

    /** Chiusura istantanea con ActivityManager.forceStopPackage (richiede FORCE_STOP_PACKAGES). */
    private static Method forceStopMethod;

    public static boolean forceStop(Context ctx, List<String> pkgs) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        boolean all = true;
        Method m = forceStopMethod;
        if (m == null) {
            try {
                m = forceStopMethod = ActivityManager.class.getMethod("forceStopPackage", String.class);
            } catch (NoSuchMethodException e) {
                return false;
            }
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
