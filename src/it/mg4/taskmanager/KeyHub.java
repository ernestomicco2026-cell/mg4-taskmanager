package it.mg4.taskmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Smista i tasti ricevuti da due fonti:
 *  - "a11y": servizio di accessibilità (vede i KeyEvent di Android e può "consumarli")
 *  - "raw":  flusso "getevent" via ADB (vede i tasti grezzi dei dispositivi di input, non può bloccarli)
 * Gestisce anche la modalità "impara tasto".
 */
public final class KeyHub {

    public static final String SRC_A11Y = "a11y";
    public static final String SRC_RAW = "raw";

    public interface LearnCallback {
        void onLearned(String source, String id, String description);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long LONG_MS = 700;

    private static LearnCallback learnCb;
    private static String candSrc, candId, candDesc;
    private static long downAt;
    private static long lastTrigger;

    private KeyHub() {
    }

    // ------------------------------------------------------------- apprendimento

    public static synchronized void startLearning(LearnCallback cb) {
        learnCb = cb;
        candSrc = null;
    }

    public static synchronized void cancelLearning() {
        learnCb = null;
        candSrc = null;
    }

    public static synchronized boolean isLearning() {
        return learnCb != null;
    }

    private static synchronized void offerCandidate(String src, String id, String desc) {
        if (learnCb == null) return;
        boolean first = candSrc == null;
        // Preferiamo la fonte "a11y": può impedire che il tasto faccia anche la sua funzione originale.
        if (first || (SRC_A11Y.equals(src) && !SRC_A11Y.equals(candSrc))) {
            candSrc = src;
            candId = id;
            candDesc = desc;
        }
        if (first) {
            MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finishLearning();
                }
            }, 500);
        }
    }

    private static synchronized void finishLearning() {
        if (learnCb == null || candSrc == null) return;
        LearnCallback cb = learnCb;
        learnCb = null;
        cb.onLearned(candSrc, candId, candDesc);
        candSrc = null;
    }

    public static void save(Context c, String src, String id, String desc) {
        Adb.prefs(c).edit()
                .putString("key_src", src)
                .putString("key_id", id)
                .putString("key_desc", desc)
                .apply();
    }

    public static void clear(Context c) {
        Adb.prefs(c).edit().remove("key_src").remove("key_id").remove("key_desc").apply();
    }

    // ------------------------------------------------------------- eventi

    /** Dal servizio di accessibilità. Ritorna true se il tasto va consumato. */
    public static boolean onA11yKey(Context c, KeyEvent e) {
        String id = String.valueOf(e.getKeyCode());
        if (isLearning()) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                offerCandidate(SRC_A11Y, id, KeyEvent.keyCodeToString(e.getKeyCode()));
            }
            return true; // durante l'apprendimento il tasto non fa altro
        }
        SharedPreferences p = Adb.prefs(c);
        if (!SRC_A11Y.equals(p.getString("key_src", null)) || !id.equals(p.getString("key_id", null))) {
            return false;
        }
        boolean longOnly = p.getBoolean("key_long", false);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) downAt = e.getEventTime();
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            long held = e.getEventTime() - downAt;
            if (!longOnly || held >= LONG_MS) trigger(c);
        }
        return !longOnly; // con "pressione lunga" la pressione breve mantiene la funzione originale
    }

    /** Dal flusso getevent. */
    public static void onRawKey(Context c, String device, String key, boolean down) {
        String id = device + " " + key;
        if (isLearning()) {
            if (down) offerCandidate(SRC_RAW, id, key + " (" + device + ")");
            return;
        }
        SharedPreferences p = Adb.prefs(c);
        if (!SRC_RAW.equals(p.getString("key_src", null)) || !id.equals(p.getString("key_id", null))) return;
        if (down) {
            downAt = SystemClock.uptimeMillis();
        } else {
            long held = SystemClock.uptimeMillis() - downAt;
            if (!p.getBoolean("key_long", false) || held >= LONG_MS) trigger(c);
        }
    }

    private static void trigger(final Context c) {
        long now = SystemClock.uptimeMillis();
        if (now - lastTrigger < 800) return;
        lastTrigger = now;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.toggle(c.getApplicationContext());
            }
        });
    }
}
