package it.mg4.taskmanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Connessione ADB condivisa + preferenze dell'app. */
public final class Adb {

    public static final String PREFS = "tm";

    private static AdbClient conn;

    private Adb() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String host(Context c) {
        return prefs(c).getString("host", "127.0.0.1");
    }

    public static int port(Context c) {
        return prefs(c).getInt("port", 5555);
    }

    public static AdbClient open(Context c, AdbClient.AuthListener l) throws IOException {
        return AdbClient.connect(c.getApplicationContext(), host(c), port(c), l);
    }

    /** Esegue un comando shell riusando la connessione; riprova una volta se è caduta. */
    public static synchronized String exec(Context c, String cmd, AdbClient.AuthListener l) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                if (conn == null) conn = open(c, l);
                return conn.shell(cmd);
            } catch (IOException e) {
                if (conn != null) conn.close();
                conn = null;
                if (attempt >= 1) throw e;
            }
        }
    }

    public static synchronized void reset() {
        if (conn != null) conn.close();
        conn = null;
    }

    // ------------------------------------------------------------ app protette

    public static Set<String> protectedApps(Context c) {
        return new HashSet<String>(prefs(c).getStringSet("protected", new HashSet<String>()));
    }

    public static boolean toggleProtected(Context c, String pkg) {
        Set<String> s = protectedApps(c);
        boolean now;
        if (s.contains(pkg)) {
            s.remove(pkg);
            now = false;
        } else {
            s.add(pkg);
            now = true;
        }
        prefs(c).edit().putStringSet("protected", s).apply();
        return now;
    }

    /** Racchiude un argomento tra apici singoli per la shell. */
    public static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
