package it.mg4.taskmanager;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elenco e chiusura delle app.
 * Modalità completa: via ADB locale (ps + am force-stop).
 * Modalità base (senza ADB): app viste dall'accessibilità / dati di utilizzo,
 * chiusura con killBackgroundProcesses + "Forza interruzione" premuto dal servizio di accessibilità.
 */
public final class Tasks {

    public static final class Entry {
        public String pkg;
        public CharSequence label;
        public Drawable icon;
        public long rssKb = -1; // -1 = sconosciuta (modalità base)
        public int processes;
        public boolean launchable;
        public boolean system;
        public boolean isProtected;
        public boolean foreground;
        int[] pids; // modalità sistema: processi di cui leggere la RAM nel secondo passaggio
    }

    public static final class Result {
        public static final int BASE = 0, ADB = 1, SYSTEM = 2;
        public List<Entry> entries;
        public int mode = BASE;
        public boolean adb; // true se chiusura istantanea disponibile (sistema o ADB)
        public boolean memoryPending; // RAM da leggere con fillMemory() (modalità sistema)
        public String adbError;
    }

    /** Pacchetti che non vanno mai chiusi: chiuderli può bloccare l'infotainment. */
    private static final Set<String> NEVER = new HashSet<String>();

    /** Quando abbiamo chiuso un'app (per non mostrarla più in modalità base finché non viene riaperta). */
    private static final Map<String, Long> killedAt = new HashMap<String, Long>();

    static {
        String[] core = {
                "android", "system", "com.android.systemui", "com.android.phone", "com.android.car",
                "com.android.settings.intelligence", "com.android.providers.settings", "com.android.bluetooth",
                "com.android.shell", "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
                "com.android.car.carlauncher", "com.android.se", "com.android.nfc",
                "com.android.settings", "com.android.car.settings", "com.android.packageinstaller",
                "com.google.android.packageinstaller",
        };
        Collections.addAll(NEVER, core);
    }

    private Tasks() {
    }

    // ================================================================== cache

    /** Dati di un pacchetto che non cambiano tra un aggiornamento e l'altro della lista. */
    static final class Meta {
        CharSequence label;
        Drawable icon;
        boolean system;
        int uid;
    }

    /** Etichetta, icona e flag per pacchetto: loadIcon e getApplicationInfo sono lenti, li facciamo una volta sola. */
    private static final Map<String, Meta> META = new HashMap<String, Meta>();
    /** Nomi di processo che non sono app installate (processi nativi): non li richiediamo di nuovo. */
    private static final Set<String> NOT_APPS = new HashSet<String>();

    /** null se il pacchetto non è installato. */
    static Meta meta(PackageManager pm, String pkg) {
        synchronized (META) {
            Meta m = META.get(pkg);
            if (m != null || NOT_APPS.contains(pkg)) return m;
        }
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            synchronized (META) {
                NOT_APPS.add(pkg);
            }
            return null;
        }
        Meta m = new Meta();
        m.label = ai.loadLabel(pm);
        try {
            m.icon = ai.loadIcon(pm);
        } catch (Exception ignored) {
        }
        m.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        m.uid = ai.uid;
        synchronized (META) {
            META.put(pkg, m);
        }
        return m;
    }

    private static final long PKG_TTL_MS = 60 * 1000;
    private static Set<String> launchableCache;
    private static String homeCache;
    private static long pkgCacheAt;

    /** Elenco delle app con icona nel launcher, ricalcolato al massimo una volta al minuto. */
    static synchronized Set<String> launchablePackages(PackageManager pm) {
        long now = SystemClock.elapsedRealtime();
        if (launchableCache == null || now - pkgCacheAt > PKG_TTL_MS) {
            Set<String> fresh = queryLaunchable(pm);
            if (launchableCache != null && !fresh.equals(launchableCache)) {
                synchronized (META) { // app installate o rimosse: rileggi tutto
                    META.clear();
                    NOT_APPS.clear();
                }
            }
            launchableCache = fresh;
            homeCache = queryHome(pm);
            pkgCacheAt = now;
        }
        return launchableCache;
    }

    /** Svuota le cache dei pacchetti (es. dopo aver installato o rimosso app). */
    static synchronized void invalidatePackages() {
        pkgCacheAt = 0;
    }

    /** Ultima RAM letta per pacchetto: la mostriamo subito, poi la aggiorniamo. */
    private static final Map<String, Long> LAST_RSS = new HashMap<String, Long>();

    // ================================================================== elenco

    public static Result list(Context ctx, boolean includeBackground, AdbClient.AuthListener l) {
        Result res = new Result();
        if (SysPriv.available(ctx)) {
            res.entries = SysPriv.list(ctx, includeBackground);
            res.mode = Result.SYSTEM;
            res.adb = true;
            res.memoryPending = !res.entries.isEmpty();
            synchronized (LAST_RSS) {
                for (Entry e : res.entries) {
                    Long v = LAST_RSS.get(e.pkg);
                    if (v != null) e.rssKb = v;
                }
            }
            sort(res.entries);
            return res;
        }
        try {
            res.entries = listAdb(ctx, includeBackground, l);
            res.mode = Result.ADB;
            res.adb = true;
        } catch (IOException e) {
            res.adbError = e.getMessage();
            res.entries = listBasic(ctx);
            res.mode = Result.BASE;
            res.adb = false;
        }
        sort(res.entries);
        return res;
    }

    /**
     * Secondo passaggio della modalità sistema: legge la RAM (lenta) solo dei processi mostrati.
     * Aggiorna le voci in place.
     */
    public static void fillMemory(Context ctx, List<Entry> entries) {
        SysPriv.fillMemory(ctx, entries);
        synchronized (LAST_RSS) {
            for (Entry e : entries) if (e.rssKb >= 0) LAST_RSS.put(e.pkg, e.rssKb);
        }
    }

    static Set<String> excludedPackages(Context ctx, PackageManager pm) {
        return excluded(ctx, pm);
    }

    private static Set<String> excluded(Context ctx, PackageManager pm) {
        Set<String> exclude = new HashSet<String>(NEVER);
        exclude.add(ctx.getPackageName());
        String home = homePackage(pm);
        if (home != null) exclude.add(home);
        return exclude;
    }

    static Entry entry(String pkg, Meta m, boolean launchable, Set<String> prot, String fg) {
        Entry en = new Entry();
        en.pkg = pkg;
        en.label = m.label;
        en.icon = m.icon;
        en.launchable = launchable;
        en.system = m.system;
        en.isProtected = prot.contains(pkg);
        en.foreground = pkg.equals(fg);
        return en;
    }

    static List<Entry> listAdb(Context ctx, boolean includeBackground, AdbClient.AuthListener l) throws IOException {
        String out = Adb.exec(ctx, "ps -A -o RSS,NAME 2>/dev/null || ps -A", l);
        Map<String, long[]> byPkg = parsePs(out);
        PackageManager pm = ctx.getPackageManager();
        Set<String> launchable = launchablePackages(pm);
        Set<String> exclude = excluded(ctx, pm);
        Set<String> prot = Adb.protectedApps(ctx);
        String fg = KeyService.lastForeground;
        List<Entry> res = new ArrayList<Entry>();
        for (Map.Entry<String, long[]> e : byPkg.entrySet()) {
            String pkg = e.getKey();
            if (exclude.contains(pkg)) continue;
            boolean canLaunch = launchable.contains(pkg);
            if (!canLaunch && !includeBackground) continue; // prima del PackageManager: evita chiamate inutili
            Meta m = meta(pm, pkg);
            if (m == null) continue; // processo nativo, non un'app
            if (m.uid < 10000 && !canLaunch) continue;
            Entry en = entry(pkg, m, canLaunch, prot, fg);
            en.rssKb = e.getValue()[0];
            en.processes = (int) e.getValue()[1];
            res.add(en);
        }
        return res;
    }

    /** Modalità base: app aperte dall'accensione, viste dall'accessibilità e/o dai dati di utilizzo. */
    static List<Entry> listBasic(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Set<String> launchable = launchablePackages(pm);
        Set<String> exclude = excluded(ctx, pm);
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();

        Map<String, Long> seen = new LinkedHashMap<String, Long>(KeyService.seenSnapshot());
        if (hasUsageAccess(ctx)) {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, boot, System.currentTimeMillis());
            if (stats != null) {
                for (UsageStats s : stats) {
                    if (s.getLastTimeUsed() <= boot || s.getTotalTimeInForeground() <= 0) continue;
                    Long old = seen.get(s.getPackageName());
                    if (old == null || old < s.getLastTimeUsed()) seen.put(s.getPackageName(), s.getLastTimeUsed());
                }
            }
        }

        // Le app attive con un processo nostro "vicino" non sono visibili senza ADB: mostriamo quelle usate.
        Set<String> alive = runningFromActivityManager(ctx);
        Set<String> prot = Adb.protectedApps(ctx);
        String fg = KeyService.lastForeground;
        List<Entry> res = new ArrayList<Entry>();
        for (Map.Entry<String, Long> e : seen.entrySet()) {
            String pkg = e.getKey();
            if (exclude.contains(pkg) || !launchable.contains(pkg)) continue;
            Long k;
            synchronized (killedAt) {
                k = killedAt.get(pkg);
            }
            if (k != null && k >= e.getValue() && !alive.contains(pkg)) continue;
            Meta m = meta(pm, pkg);
            if (m != null) res.add(entry(pkg, m, true, prot, fg));
        }
        return res;
    }

    private static Set<String> runningFromActivityManager(Context ctx) {
        Set<String> s = new HashSet<String>();
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> l = am.getRunningAppProcesses();
            if (l != null) for (ActivityManager.RunningAppProcessInfo p : l) {
                if (p.pkgList != null) Collections.addAll(s, p.pkgList);
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    private static void sort(List<Entry> res) {
        Collections.sort(res, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.launchable != b.launchable) return a.launchable ? -1 : 1;
                if (a.foreground != b.foreground) return a.foreground ? -1 : 1;
                if (a.rssKb != b.rssKb) return Long.compare(b.rssKb, a.rssKb);
                return a.label.toString().compareToIgnoreCase(b.label.toString());
            }
        });
    }

    public static boolean hasUsageAccess(Context ctx) {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /** pkg -> [RSS totale in kB, numero processi] */
    static Map<String, long[]> parsePs(String out) {
        Map<String, long[]> map = new HashMap<String, long[]>();
        String[] lines = out.split("\n");
        int rssIdx = -1;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            if (rssIdx < 0) {
                for (int i = 0; i < t.length; i++) if (t[i].equals("RSS")) rssIdx = i;
                if (rssIdx < 0) rssIdx = Math.max(0, t.length - 1 - 4);
                continue; // intestazione
            }
            String name = t[t.length - 1];
            if (name.indexOf('.') < 0 || name.startsWith("/") || name.startsWith("[")) continue;
            int colon = name.indexOf(':');
            String pkg = colon > 0 ? name.substring(0, colon) : name;
            long rss = 0;
            if (rssIdx < t.length) {
                try {
                    rss = Long.parseLong(t[rssIdx]);
                } catch (NumberFormatException ignored) {
                }
            }
            long[] v = map.get(pkg);
            if (v == null) map.put(pkg, v = new long[2]);
            v[0] += rss;
            v[1]++;
        }
        return map;
    }

    private static Set<String> queryLaunchable(PackageManager pm) {
        Set<String> s = new HashSet<String>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) s.add(ri.activityInfo.packageName);
        return s;
    }

    static String homePackage(PackageManager pm) {
        launchablePackages(pm); // aggiorna la cache se scaduta
        synchronized (Tasks.class) {
            return homeCache;
        }
    }

    private static String queryHome(PackageManager pm) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return null;
        String p = ri.activityInfo.packageName;
        return "android".equals(p) ? null : p;
    }

    // ================================================================== chiusura

    /** Chiusura istantanea: modalità sistema se disponibile, altrimenti ADB. False se nessuna delle due. */
    public static boolean killInstant(Context ctx, List<String> pkgs, AdbClient.AuthListener l) {
        if (pkgs.isEmpty()) return true;
        if (SysPriv.available(ctx)) return SysPriv.forceStop(ctx, pkgs);
        return killAdb(ctx, pkgs, l);
    }

    public static boolean killAdb(Context ctx, List<String> pkgs, AdbClient.AuthListener l) {
        if (pkgs.isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) {
            if (sb.length() > 0) sb.append(" ; ");
            sb.append("am force-stop ").append(Adb.q(p));
        }
        try {
            Adb.exec(ctx, sb.toString(), l);
            markKilled(pkgs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Modalità base: libera i processi in background (le app in uso potrebbero restare attive). */
    public static void killBackground(Context ctx, List<String> pkgs) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        for (String p : pkgs) {
            try {
                am.killBackgroundProcesses(p);
            } catch (Exception ignored) {
            }
        }
        markKilled(pkgs);
    }

    static void markKilled(List<String> pkgs) {
        long now = System.currentTimeMillis();
        synchronized (killedAt) {
            for (String p : pkgs) killedAt.put(p, now);
        }
        KeyService.forget(pkgs);
    }
}
