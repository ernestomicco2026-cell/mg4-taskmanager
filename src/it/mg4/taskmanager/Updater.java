package it.mg4.taskmanager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Aggiornamento automatico: legge l'ultima release pubblicata su GitHub, scarica l'APK
 * e lo installa tramite ADB (pm install), poi riapre l'app.
 */
public final class Updater {

    public static final String REPO = "ernestomicco2026-cell/MG4-TaskManager";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long AUTO_INTERVAL_MS = 6L * 60 * 60 * 1000;

    public static final class Release {
        public String tag;
        public long code;
        public String notes;
        public String apkUrl;
        public long size;
    }

    public interface Progress {
        void onProgress(int percent);
    }

    private Updater() {
    }

    /** "v1.2.3" -> 10203 (stessa formula usata dalla build per il versionCode). */
    public static long codeFromTag(String tag) {
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        String[] p = t.split("[.\\-+]");
        long code = 0;
        for (int i = 0; i < 3; i++) {
            long n = 0;
            if (i < p.length) {
                try {
                    n = Long.parseLong(p[i].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {
                }
            }
            code = code * 100 + Math.min(n, 99);
        }
        return code;
    }

    public static long currentCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static boolean autoEnabled(Context c) {
        return Adb.prefs(c).getBoolean("auto_update", true);
    }

    /** True se è il momento di un controllo automatico (al massimo ogni 6 ore). */
    public static boolean dueForAutoCheck(Context c) {
        if (!autoEnabled(c)) return false;
        long last = Adb.prefs(c).getLong("update_last_check", 0);
        return System.currentTimeMillis() - last > AUTO_INTERVAL_MS;
    }

    /** Ultima release, oppure null se non ce n'è una più nuova di quella installata. */
    public static Release checkNewer(Context c) throws IOException {
        Adb.prefs(c).edit().putLong("update_last_check", System.currentTimeMillis()).apply();
        Release r = fetchLatest(isSystemEdition(c));
        if (r == null || r.apkUrl == null || r.code <= currentCode(c)) return null;
        return r;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(20000);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "MG4-TaskManager");
        return h;
    }

    /**
     * True se questa copia è l'edizione di sistema (firmata con la chiave platform, la stessa del
     * pacchetto "android"). Serve a scaricare l'APK giusto: con una firma diversa l'aggiornamento
     * non si installerebbe.
     */
    public static boolean isSystemEdition(Context c) {
        try {
            return c.getPackageManager().checkSignatures("android", c.getPackageName())
                    == android.content.pm.PackageManager.SIGNATURE_MATCH;
        } catch (Exception e) {
            return SysPriv.available(c);
        }
    }

    /** L'APK della Release per l'edizione indicata ("...-system.apk" oppure l'altro). */
    static boolean assetMatches(String name, boolean system) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".apk") && n.contains("system") == system;
    }

    static Release fetchLatest(boolean systemEdition) throws IOException {
        HttpURLConnection h = open(API);
        h.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int code = h.getResponseCode();
            if (code == 404) return null; // nessuna release pubblicata
            if (code != 200) throw new IOException(Lang.tr("GitHub ha risposto ", "GitHub responded ",
                "GitHub ha respondido ", "O GitHub respondeu ", "GitHub a répondu ", "GitHub hat geantwortet: ") + code);
            String body = readAll(h.getInputStream());
            JSONObject o = new JSONObject(body);
            Release r = new Release();
            r.tag = o.optString("tag_name", "");
            r.code = codeFromTag(r.tag);
            r.notes = o.optString("body", "");
            JSONArray assets = o.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (assetMatches(a.optString("name", ""), systemEdition)) {
                        r.apkUrl = a.optString("browser_download_url", null);
                        r.size = a.optLong("size", 0);
                        break;
                    }
                }
            }
            return r;
        } catch (org.json.JSONException e) {
            throw new IOException(Lang.tr("Risposta di GitHub non valida", "Invalid response from GitHub",
                "Respuesta de GitHub no válida", "Resposta do GitHub inválida", "Réponse de GitHub non valide", "Ungültige Antwort von GitHub"));
        } finally {
            h.disconnect();
        }
    }

    public static File download(Context c, Release r, Progress p) throws IOException {
        File dir = c.getExternalCacheDir();
        if (dir == null) throw new IOException(Lang.tr("Memoria non disponibile", "Storage not available",
                "Almacenamiento no disponible", "Armazenamento não disponível", "Stockage non disponible", "Speicher nicht verfügbar"));
        File out = new File(dir, "update.apk");
        HttpURLConnection h = open(r.apkUrl);
        try {
            if (h.getResponseCode() != 200) throw new IOException(Lang.tr("Download fallito (", "Download failed (",
                "Descarga fallida (", "Falha no download (", "Échec du téléchargement (", "Download fehlgeschlagen (") + h.getResponseCode() + ")");
            long total = r.size > 0 ? r.size : h.getContentLength();
            InputStream in = h.getInputStream();
            OutputStream os = new FileOutputStream(out);
            try {
                byte[] buf = new byte[16384];
                long done = 0;
                int n, lastPct = -1;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    done += n;
                    if (total > 0 && p != null) {
                        int pct = (int) (done * 100 / total);
                        if (pct != lastPct) p.onProgress(lastPct = pct);
                    }
                }
                if (r.size > 0 && done != r.size) throw new IOException(Lang.tr("Download incompleto", "Incomplete download",
                "Descarga incompleta", "Download incompleto", "Téléchargement incomplet", "Download unvollständig"));
            } finally {
                os.close();
                in.close();
            }
        } finally {
            h.disconnect();
        }
        return out;
    }

    public static File logFile(Context c) {
        File dir = c.getExternalCacheDir();
        return dir == null ? null : new File(dir, "update.log");
    }

    /**
     * Installa l'APK scaricato con "pm install -r" via ADB. Il comando gira in background nella
     * shell dell'auto, così prosegue anche quando Android chiude l'app per sostituirla, e alla fine
     * la riapre. L'esito finisce in update.log.
     */
    public static void install(Context c, File apk, AdbClient.AuthListener l) throws IOException {
        install(c, apk, l, true);
    }

    /** restart=false per gli aggiornamenti in background: l'app non si riapre da sola sullo schermo. */
    public static void install(Context c, File apk, AdbClient.AuthListener l, boolean restart) throws IOException {
        File log = logFile(c);
        if (log != null) log.delete();
        String tmp = "/data/local/tmp/mg4tm-update.apk";
        String act = c.getPackageName() + "/" + MainActivity.class.getName();
        String script = "sleep 1; cp " + apk.getAbsolutePath() + " " + tmp
                + " && pm install -r " + tmp + " > " + log.getAbsolutePath() + " 2>&1; "
                + "rm -f " + tmp + (restart ? "; am start -n " + act : "");
        Adb.exec(c, "nohup sh -c " + Adb.q(script) + " > /dev/null 2>&1 &", l);
    }

    public static boolean autoInstall(Context c) {
        return Adb.prefs(c).getBoolean("auto_install", true);
    }

    // ------------------------------------------------------------ aggiornamento scaricato in attesa

    public static void setPending(Context c, Release r) {
        Adb.prefs(c).edit().putString("pending_tag", r.tag).putLong("pending_code", r.code)
                .putString("pending_notes", r.notes == null ? "" : r.notes).apply();
    }

    /** Aggiornamento già scaricato e non ancora installato (null se nessuno). */
    public static Release pending(Context c) {
        android.content.SharedPreferences p = Adb.prefs(c);
        long code = p.getLong("pending_code", 0);
        File f = downloadedFile(c);
        if (code <= currentCode(c) || f == null || !f.exists()) return null;
        Release r = new Release();
        r.tag = p.getString("pending_tag", "");
        r.code = code;
        r.notes = p.getString("pending_notes", "");
        return r;
    }

    public static File downloadedFile(Context c) {
        File dir = c.getExternalCacheDir();
        return dir == null ? null : new File(dir, "update.apk");
    }

    /**
     * Installazione senza ADB: usa l'installer di Android. Comparirà la schermata
     * "Vuoi installare questo aggiornamento?" con il pulsante Installa.
     */
    public static void installWithSystem(Context c, File apk) throws IOException {
        android.content.pm.PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        android.content.pm.PackageInstaller.SessionParams params = new android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        android.content.pm.PackageInstaller.Session session = pi.openSession(id);
        try {
            OutputStream out = session.openWrite("update.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            } finally {
                in.close();
                out.close();
            }
            Intent i = new Intent(c, InstallReceiver.class);
            android.app.PendingIntent sender = android.app.PendingIntent.getBroadcast(c, id, i,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(sender.getIntentSender());
        } catch (IOException e) {
            session.abandon();
            throw e;
        } catch (RuntimeException e) {
            session.abandon();
            throw new IOException(e.getMessage());
        } finally {
            session.close();
        }
    }

    /** Prova l'installazione silenziosa via ADB; se ADB non c'è usa l'installer di sistema. */
    public static boolean installAny(Context c, File apk, AdbClient.AuthListener l, boolean restart) throws IOException {
        try {
            Adb.exec(c, "true", l); // ADB raggiungibile?
            install(c, apk, l, restart);
            return true;
        } catch (IOException adbMissing) {
            installWithSystem(c, apk);
            return false;
        }
    }

    /** Legge l'esito dell'ultima installazione (null se non ancora disponibile). */
    public static String readLog(Context c) {
        File f = logFile(c);
        if (f == null || !f.exists()) return null;
        try {
            return readAll(new FileInputStream(f)).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
