package it.mg4.taskmanager;

import android.content.Context;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Lingue dell'app: italiano, inglese, spagnolo, portoghese, francese, tedesco.
 * Di default segue la lingua di sistema (inglese se non è tra queste); si può forzare
 * dalle Impostazioni. Ogni testo visibile si scrive inline con
 * Lang.tr(italiano, english, español, português, français, deutsch).
 */
public final class Lang {

    /** Codici supportati, nello stesso ordine degli argomenti di tr(). */
    public static final String[] CODES = {"it", "en", "es", "pt", "fr", "de"};
    /** Nome di ogni lingua scritto nella lingua stessa. */
    public static final String[] NAMES = {"Italiano", "English", "Español", "Português", "Français", "Deutsch"};

    static final String PREF = "lang"; // "" = automatica (lingua di sistema)
    private static final int EN = 1;

    private static volatile int idx = index(systemLanguage());

    private Lang() {
    }

    private static int index(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i;
        return EN;
    }

    /** Lingua attuale del sistema (non quella forzata nell'app). */
    public static String systemLanguage() {
        try {
            return Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        } catch (Exception e) {
            return Locale.getDefault().getLanguage();
        }
    }

    /** Applica la lingua salvata nelle preferenze. Da chiamare in ogni punto d'ingresso dell'app. */
    public static void init(Context c) {
        apply(chosen(c));
    }

    /** "" = automatica. */
    public static String chosen(Context c) {
        return Adb.prefs(c).getString(PREF, "");
    }

    public static void apply(String code) {
        idx = index(code == null || code.isEmpty() ? systemLanguage() : code);
    }

    public static void save(Context c, String code) {
        Adb.prefs(c).edit().putString(PREF, code == null ? "" : code).apply();
        apply(code);
    }

    /** Codice della lingua in uso ("it", "en", ...). */
    public static String code() {
        return CODES[idx];
    }

    public static String name(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return NAMES[i];
        return NAMES[EN];
    }

    public static String tr(String it, String en, String es, String pt, String fr, String de) {
        switch (idx) {
            case 0:
                return it;
            case 2:
                return es;
            case 3:
                return pt;
            case 4:
                return fr;
            case 5:
                return de;
            default:
                return en;
        }
    }

    /**
     * Note di rilascio multilingua: le sezioni iniziano con un commento "<!-- lang:xx -->"
     * (invisibile su GitHub). Restituisce la sezione nella lingua in uso, poi l'inglese,
     * altrimenti il testo intero.
     */
    public static String pickSection(String text) {
        if (text == null || text.indexOf("<!-- lang:") < 0) return text;
        String mine = section(text, code());
        if (mine == null) mine = section(text, "en");
        return mine != null ? mine : text;
    }

    private static String section(String text, String code) {
        String tag = "<!-- lang:" + code + " -->";
        int a = text.indexOf(tag);
        if (a < 0) return null;
        a += tag.length();
        int b = text.indexOf("<!-- lang:", a);
        String s = (b < 0 ? text.substring(a) : text.substring(a, b)).trim();
        if (s.endsWith("---")) s = s.substring(0, s.length() - 3).trim();
        return s;
    }
}
