package it.mg4.taskmanager;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servizio di accessibilità:
 *  - intercetta il tasto scelto sul volante;
 *  - ricorda quali app sono state aperte (serve alla modalità senza ADB);
 *  - senza ADB chiude le app aprendo "Info app" e premendo "Forza interruzione".
 */
public class KeyService extends AccessibilityService {

    public static volatile String lastForeground;
    public static volatile boolean running;
    private static KeyService instance;

    private static final Map<String, Long> seen = new LinkedHashMap<String, Long>();

    // Testi del pulsante "Forza interruzione" e della conferma, in più lingue (minuscolo).
    private static final String[] FORCE = {
            "forza interruzione", "forza arresto", "arresto forzato", "termina forzatamente", "forza chiusura",
            "force stop", "forcer l'arrêt", "forcer l’arrêt", "beenden erzwingen", "stopp erzwingen",
            "forzar detención", "forzar cierre", "forçar parada", "强行停止", "強制停止",
    };
    private static final String[] CONFIRM = {"ok", "sì", "si", "conferma", "yes", "确定"};
    private static final String[] FORCE_IDS = {
            "com.android.settings:id/force_stop_button", "com.android.settings:id/right_button",
            "com.android.car.settings:id/force_stop_button",
    };

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue = new ArrayDeque<String>();
    private Runnable onDone;
    private String current;
    private int stage;
    private long stageStart;
    private int pageWindowId = -1;

    // ================================================================== ciclo di vita

    @Override
    protected void onServiceConnected() {
        running = true;
        instance = this;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            return KeyHub.onA11yKey(this, event);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui") || pkg.equals("android")) return;
        if (current != null) return; // durante la chiusura automatica ignoriamo le Impostazioni
        lastForeground = pkg;
        synchronized (seen) {
            seen.remove(pkg);
            seen.put(pkg, System.currentTimeMillis());
        }
    }

    @Override
    public void onInterrupt() {
    }

    static Map<String, Long> seenSnapshot() {
        synchronized (seen) {
            return new HashMap<String, Long>(seen);
        }
    }

    static void forget(List<String> pkgs) {
        synchronized (seen) {
            for (String p : pkgs) seen.remove(p);
        }
        if (pkgs.contains(lastForeground)) lastForeground = null;
    }

    // ================================================================== chiusura senza ADB

    public static boolean canForceStop() {
        return instance != null;
    }

    public static boolean isBusy() {
        return instance != null && instance.current != null;
    }

    /** Chiude le app una alla volta premendo "Forza interruzione". onDone gira sul thread principale. */
    public static boolean forceStop(List<String> pkgs, Runnable onDone) {
        KeyService s = instance;
        if (s == null || s.current != null) return false;
        s.queue.clear();
        s.queue.addAll(pkgs);
        s.onDone = onDone;
        s.next();
        return true;
    }

    private void next() {
        h.removeCallbacks(tick);
        current = queue.poll();
        if (current == null) {
            finishAll();
            return;
        }
        stage = 0;
        pageWindowId = -1;
        stageStart = SystemClock.uptimeMillis();
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        } catch (Exception e) {
            next();
            return;
        }
        h.postDelayed(tick, 500);
    }

    private void finishAll() {
        current = null;
        Runnable done = onDone;
        onDone = null;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
        if (done != null) done.run();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (current == null) return;
            long elapsed = SystemClock.uptimeMillis() - stageStart;
            if (elapsed > 6000) { // pagina diversa dal previsto: passiamo alla prossima app
                next();
                return;
            }
            AccessibilityNodeInfo root = null;
            try {
                root = getRootInActiveWindow();
            } catch (Exception ignored) {
            }
            if (root != null) {
                if (stage == 0) {
                    AccessibilityNodeInfo btn = findForceButton(root);
                    if (btn != null) {
                        if (!btn.isEnabled()) { // già chiusa
                            next();
                            return;
                        }
                        pageWindowId = root.getWindowId();
                        click(btn);
                        stage = 1;
                        stageStart = SystemClock.uptimeMillis();
                    }
                } else if (stage == 1) {
                    if (root.getWindowId() != pageWindowId) {
                        AccessibilityNodeInfo ok = findConfirmButton(root);
                        if (ok != null) {
                            click(ok);
                            stage = 2;
                            stageStart = SystemClock.uptimeMillis();
                        }
                    } else {
                        AccessibilityNodeInfo btn = findForceButton(root);
                        if (btn != null && !btn.isEnabled()) { // chiusa senza conferma
                            next();
                            return;
                        }
                    }
                } else if (elapsed > 400) { // stage 2: conferma premuta
                    next();
                    return;
                }
            }
            h.postDelayed(this, 250);
        }
    };

    private static AccessibilityNodeInfo findForceButton(AccessibilityNodeInfo root) {
        for (String id : FORCE_IDS) {
            List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(id);
            if (l != null) for (AccessibilityNodeInfo n : l) if (matches(n, FORCE)) return n;
        }
        return findByText(root, FORCE);
    }

    private static AccessibilityNodeInfo findConfirmButton(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId("android:id/button1");
        if (l != null && !l.isEmpty()) return l.get(0);
        AccessibilityNodeInfo n = findByText(root, CONFIRM);
        return n != null ? n : findByText(root, FORCE);
    }

    private static AccessibilityNodeInfo findByText(AccessibilityNodeInfo root, String[] texts) {
        for (String t : texts) {
            List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByText(t);
            if (l == null) continue;
            for (AccessibilityNodeInfo n : l) if (matches(n, texts)) return n;
        }
        return null;
    }

    private static boolean matches(AccessibilityNodeInfo n, String[] texts) {
        CharSequence t = n.getText();
        if (t == null) t = n.getContentDescription();
        if (t == null) return false;
        String s = t.toString().trim().toLowerCase(Locale.ROOT);
        for (String x : texts) if (s.equals(x)) return true;
        return false;
    }

    private static void click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo c = n;
        while (c != null && !c.isClickable()) c = c.getParent();
        (c != null ? c : n).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    /** Apre le impostazioni di accessibilità (per attivare il servizio senza ADB). */
    public static boolean openAccessibilitySettings(Context c) {
        return openSettings(c, Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    static boolean openSettings(Context c, String action) {
        Intent[] tries = {new Intent(action), new Intent(Settings.ACTION_SETTINGS)};
        for (Intent i : tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) == null) continue;
            try {
                c.startActivity(i);
                return i.getAction().equals(action);
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
