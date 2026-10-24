package it.mg4.taskmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Colori (pensati per lo schermo dell'auto, tema scuro)
    private static final int BG = 0xFF0E1216;
    private static final int CARD = 0xFF1A2027;
    private static final int CARD_FG = 0xFF223A33;
    private static final int TEXT = 0xFFF2F4F6;
    private static final int MUTED = 0xFF8E99A4;
    private static final int ACCENT = 0xFF2BB673;
    private static final int DANGER = 0xFFE5484D;
    private static final int BTN = 0xFF2A323B;

    static final String EXTRA_INSTALL_UPDATE = "install_update";

    static volatile boolean visible;
    private static MainActivity instance;
    private boolean adbMode; // true in modalità sistema o ADB (chiusura istantanea)
    private int mode = Tasks.Result.BASE;
    private boolean grantedViaAdb;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private TextView status;
    private LinearLayout list;
    private List<Tasks.Entry> current = new ArrayList<Tasks.Entry>();
    private boolean loading;
    /** Sottotitolo di ogni scheda, per aggiornare la RAM senza ridisegnare la lista. */
    private final Map<String, TextView> subtitles = new HashMap<String, TextView>();

    /** Ultima lista caricata: alla riapertura la mostriamo subito, poi la aggiorniamo. */
    private static List<Tasks.Entry> lastEntries;
    private static int lastMode = Tasks.Result.BASE;

    private static String tr(String it, String en, String es, String pt, String fr, String de) {
        return Lang.tr(it, en, es, pt, fr, de);
    }

    private final AdbClient.AuthListener auth = new AdbClient.AuthListener() {
        @Override
        public void onWaitingForApproval() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    setStatus(tr("Sullo schermo dell'auto conferma «Consentire il debug?» (spunta «Consenti sempre»)", "On the car screen tap «Allow debugging?» (check «Always allow»)",
                "En la pantalla del coche confirma «¿Permitir la depuración?» (marca «Permitir siempre»)", "Na tela do carro confirme «Permitir depuração?» (marque «Permitir sempre»)", "Sur l'écran de la voiture, confirmez « Autoriser le débogage ? » (cochez « Toujours autoriser »)", "Bestätige auf dem Fahrzeugbildschirm „Debugging zulassen?“ (aktiviere „Immer zulassen“)"));
                }
            });
        }
    };

    /** Chiamato dal tasto al volante: apre il Task Manager o, se è già aperto, lo chiude. */
    static void toggle(Context c) {
        if (visible && instance != null) {
            instance.finish();
            return;
        }
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        super.onCreate(b);
        Lang.init(this);
        instance = this;
        getWindow().getDecorView().setBackgroundColor(BG);
        setContentView(buildUi());
        if (lastEntries != null) { // lista della volta precedente, subito a schermo
            current = lastEntries;
            mode = lastMode;
            adbMode = mode != Tasks.Result.BASE;
            render();
        }
        ensureKeyListeners();
        final Context app = getApplicationContext();
        bg.execute(new Runnable() {
            @Override
            public void run() {
                UpdateJobService.schedule(app); // chiamate a JobScheduler: fuori dal thread principale
            }
        });
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        if (i != null && i.getBooleanExtra(EXTRA_INSTALL_UPDATE, false)) {
            i.removeExtra(EXTRA_INSTALL_UPDATE);
            final Updater.Release r = Updater.pending(this);
            if (r != null) main.post(new Runnable() {
                @Override
                public void run() {
                    installDownloaded(r);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        refresh();
        Updater.Release pend = Updater.pending(this);
        if (pend != null && !Adb.prefs(this).getString("pending_offered", "").equals(pend.tag)) {
            Adb.prefs(this).edit().putString("pending_offered", pend.tag).apply();
            offerUpdate(pend);
        } else if (Updater.dueForAutoCheck(this)) {
            checkUpdates(false);
        }
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        bg.shutdown();
        super.onDestroy();
    }

    // ================================================================== UI

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String s, int color, View.OnClickListener l) {
        TextView t = text(s, 19, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(color));
        t.setPadding(dp(22), dp(14), dp(22), dp(14));
        t.setMinWidth(dp(64));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight, int marginLeft) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.leftMargin = marginLeft;
        return p;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Task Manager", 28, TEXT, true));
        status = text("", 15, MUTED, false);
        titles.addView(status);
        header.addView(titles, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, 0));

        header.addView(button(tr("Aggiorna", "Refresh",
                "Actualizar", "Atualizar", "Actualiser", "Aktualisieren"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        }), lp(-2, -2, 0, dp(12)));
        header.addView(button(tr("Chiudi tutto", "Close all",
                "Cerrar todo", "Fechar tudo", "Tout fermer", "Alle schließen"), DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeAll();
            }
        }), lp(-2, -2, 0, dp(12)));
        header.addView(button(tr("Impostazioni", "Settings",
                "Ajustes", "Configurações", "Paramètres", "Einstellungen"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        }), lp(-2, -2, 0, dp(12)));
        header.addView(button("×", BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }), lp(-2, -2, 0, dp(12)));
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(16), 0, dp(16));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = text(tr("Tocca un'app per aprirla · tieni premuto per escluderla da «Chiudi tutto»", "Tap an app to open it · long-press to exclude it from «Close all»",
                "Toca una app para abrirla · mantenla pulsada para excluirla de «Cerrar todo»", "Toque num app para abri-lo · mantenha pressionado para excluí-lo de «Fechar tudo»", "Touchez une app pour l'ouvrir · appui long pour l'exclure de « Tout fermer »", "Tippe auf eine App, um sie zu öffnen · lange drücken, um sie von „Alle schließen“ auszunehmen"), 14, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);
        return root;
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    /** App da mostrare: con «Escludi app di sistema» attivo quelle di sistema spariscono dalla lista. */
    private List<Tasks.Entry> shown() {
        if (!skipSystem()) return current;
        List<Tasks.Entry> r = new ArrayList<Tasks.Entry>();
        for (Tasks.Entry e : current) if (!e.system) r.add(e);
        return r;
    }

    private void updateStatus(List<Tasks.Entry> vis) {
        int apps = 0;
        long kb = 0;
        for (Tasks.Entry e : vis) {
            if (e.launchable) apps++;
            if (e.rssKb > 0) kb += e.rssKb;
        }
        String st = apps + (apps == 1 ? tr(" app aperta", " app open",
                " app abierta", " app aberto", " app ouverte", " App geöffnet") : tr(" app aperte", " apps open",
                " apps abiertas", " apps abertos", " apps ouvertes", " Apps geöffnet"));
        if (mode == Tasks.Result.SYSTEM) st += " · " + (kb / 1024) + tr(" MB · modalità sistema", " MB · system mode",
                " MB · modo sistema", " MB · modo sistema", " Mo · mode système", " MB · Systemmodus");
        else if (mode == Tasks.Result.ADB) st += " · " + (kb / 1024) + tr(" MB · modalità completa (ADB)", " MB · full mode (ADB)",
                " MB · modo completo (ADB)", " MB · modo completo (ADB)", " Mo · mode complet (ADB)", " MB · Vollmodus (ADB)");
        else st += tr(" · modalità base", " · basic mode",
                " · modo básico", " · modo básico", " · mode de base", " · Basismodus");
        int hidden = current.size() - vis.size();
        if (hidden > 0) st += " · " + hidden + tr(" di sistema nascoste", " system hidden",
                " del sistema ocultas", " do sistema ocultos", " système masquées", " System-Apps ausgeblendet");
        setStatus(st);
    }

    private void render() {
        list.removeAllViews();
        subtitles.clear();
        List<Tasks.Entry> vis = shown();
        updateStatus(vis);
        if (!adbMode) addSetupCard();
        if (vis.isEmpty()) {
            TextView t = text(tr("Nessuna app aperta", "No apps open",
                "Ninguna app abierta", "Nenhum app aberto", "Aucune app ouverte", "Keine Apps geöffnet"), 22, MUTED, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(60), 0, 0);
            list.addView(t, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        int cols = Math.max(1, Math.min(3, width / dp(560)));
        LinearLayout row = null;
        boolean bgHeaderDone = false;
        int col = 0;
        for (Tasks.Entry e : vis) {
            if (!e.launchable && !bgHeaderDone) {
                bgHeaderDone = true;
                col = 0;
                TextView h = text(tr("Servizi in background (chiudili solo se sai cosa fanno)", "Background services (close only if you know what they do)",
                "Servicios en segundo plano (ciérralos solo si sabes lo que hacen)", "Serviços em segundo plano (feche-os só se souber o que fazem)", "Services en arrière-plan (fermez-les seulement si vous savez ce qu'ils font)", "Hintergrunddienste (nur schließen, wenn du weißt, was sie tun)"), 16, MUTED, true);
                h.setPadding(dp(4), dp(18), 0, dp(8));
                list.addView(h);
            }
            if (col == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            View card = card(e);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.leftMargin = col == 0 ? 0 : dp(12);
            p.bottomMargin = dp(12);
            row.addView(card, p);
            col = (col + 1) % cols;
            if (col == 0) row = null;
        }
        // riempi l'ultima riga per mantenere le colonne allineate
        while (row != null && col != 0) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1);
            p.leftMargin = dp(12);
            row.addView(new View(this), p);
            col = (col + 1) % cols;
        }
    }

    private View card(final Tasks.Entry e) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(round(e.foreground ? CARD_FG : CARD));
        c.setPadding(dp(16), dp(14), dp(14), dp(14));

        ImageView icon = new ImageView(this);
        if (e.icon != null) icon.setImageDrawable(e.icon);
        c.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(16), 0, dp(8), 0);
        String tag = e.isProtected ? tr("  · esclusa", "  · excluded",
                "  · excluida", "  · excluído", "  · exclue", "  · ausgenommen")
                : (e.system && skipSystem() ? tr("  · sistema", "  · system",
                "  · sistema", "  · sistema", "  · système", "  · System") : "");
        TextView name = text(e.label + tag, 20, TEXT, true);
        name.setSingleLine(true);
        texts.addView(name);
        TextView s = text(subtitle(e), 14, MUTED, false);
        subtitles.put(e.pkg, s);
        texts.addView(s);
        c.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        if (!e.system) { // solo app installate da te: quelle di sistema non si disinstallano
            c.addView(button("🗑", BTN, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askUninstall(e.pkg, e.label);
                }
            }), lp(-2, -2, 0, 0));
            View gap = new View(this);
            c.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        }
        c.addView(button(tr("Chiudi", "Close",
                "Cerrar", "Fechar", "Fermer", "Schließen"), DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> one = new ArrayList<String>();
                one.add(e.pkg);
                kill(one, e.label + tr(" chiusa", " closed",
                " cerrada", " fechado", " fermée", " geschlossen"));
            }
        }));

        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(e.pkg);
                if (i == null) {
                    toast(tr("Questa app non ha una schermata da aprire", "This app has no screen to open",
                "Esta app no tiene una pantalla que abrir", "Este app não tem uma tela para abrir", "Cette app n'a pas d'écran à ouvrir", "Diese App hat keinen Bildschirm zum Öffnen"));
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                    finish();
                } catch (Exception ex) {
                    toast(tr("Impossibile aprire l'app", "Couldn't open the app",
                "No se puede abrir la app", "Não foi possível abrir o app", "Impossible d'ouvrir l'app", "App konnte nicht geöffnet werden"));
                }
            }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean p = Adb.toggleProtected(MainActivity.this, e.pkg);
                toast(p ? e.label + tr(" esclusa: «Chiudi tutto» la salterà", " excluded: «Close all» will skip it",
                " excluida: «Cerrar todo» la omitirá", " excluído: «Fechar tudo» vai ignorá-lo", " exclue : « Tout fermer » l'ignorera", " ausgenommen: „Alle schließen“ überspringt sie")
                        : e.label + tr(" non è più esclusa", " is no longer excluded",
                " ya no está excluida", " não está mais excluído", " n'est plus exclue", " ist nicht mehr ausgenommen"));
                e.isProtected = p;
                render();
                return true;
            }
        });
        return c;
    }

    private String subtitle(Tasks.Entry e) {
        StringBuilder sub = new StringBuilder();
        if (e.foreground) sub.append(tr("In primo piano · ", "Foreground · ",
                "En primer plano · ", "Em primeiro plano · ", "Au premier plan · ", "Im Vordergrund · "));
        if (e.system) sub.append(tr("App di sistema · ", "System app · ",
                "App del sistema · ", "App do sistema · ", "App système · ", "System-App · "));
        String mb = "fr".equals(Lang.code()) ? " Mo" : " MB";
        if (e.rssKb >= 0) sub.append(e.rssKb / 1024).append(mb);
        else if (mode == Tasks.Result.SYSTEM) sub.append("…").append(mb);
        else if (sub.length() == 0) sub.append(tr("Aperta da questa accensione", "Open since power-on",
                "Abierta desde el encendido", "Aberto desde a ligação", "Ouverte depuis le démarrage", "Seit dem Einschalten geöffnet"));
        if (e.processes > 1) sub.append(" · ").append(e.processes).append(tr(" processi", " processes",
                " procesos", " processos", " processus", " Prozesse"));
        if (sub.toString().endsWith(" · ")) sub.setLength(sub.length() - 3);
        sub.append("\n").append(e.pkg);
        return sub.toString();
    }

    /** Dopo la lettura della RAM: aggiorna solo i testi, senza ricostruire la lista. */
    private void updateMemoryViews() {
        for (Tasks.Entry e : current) {
            TextView t = subtitles.get(e.pkg);
            if (t != null) t.setText(subtitle(e));
        }
        updateStatus(shown());
    }

    private void toast(CharSequence s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * Modalità base: spiega cosa manca e porta alle pagine giuste delle Impostazioni.
     * Sparisce quando accessibilità e accesso ai dati di utilizzo sono attivi.
     */
    private void addSetupCard() {
        if (mode == Tasks.Result.SYSTEM) return; // in modalità sistema non serve alcun permesso manuale
        boolean a11y = KeyService.running;
        boolean usage = Tasks.hasUsageAccess(this);
        if (a11y && usage) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(CARD));
        box.setPadding(dp(24), dp(20), dp(24), dp(20));
        box.addView(text(tr("Completa la configurazione", "Finish setup",
                "Completa la configuración", "Conclua a configuração", "Terminer la configuration", "Einrichtung abschließen"), 22, TEXT, true));
        StringBuilder b = new StringBuilder();
        if (!a11y) b.append(tr("• Accessibilità: attiva «Task Manager» per vedere le app aperte e chiuderle davvero (l'app preme da sola «Forza interruzione»).\n", "• Accessibility: enable «Task Manager» to see open apps and really close them (the app taps «Force stop» by itself).\n",
                "• Accesibilidad: activa «Task Manager» para ver las apps abiertas y cerrarlas de verdad (la app pulsa sola «Forzar detención»).\n", "• Acessibilidade: ative «Task Manager» para ver os apps abertos e fechá-los de verdade (o app toca sozinho em «Forçar parada»).\n", "• Accessibilité : activez « Task Manager » pour voir les apps ouvertes et les fermer vraiment (l'app appuie elle-même sur « Forcer l'arrêt »).\n", "• Bedienungshilfen: Aktiviere „Task Manager“, um geöffnete Apps zu sehen und wirklich zu schließen (die App tippt selbst auf „Beenden erzwingen“).\n"));
        if (!usage) b.append(tr("• Accesso ai dati di utilizzo: consenti a «Task Manager» di vedere quali app hai usato.\n", "• Usage access: allow «Task Manager» to see which apps you used.\n",
                "• Acceso a datos de uso: permite a «Task Manager» ver qué apps has usado.\n", "• Acesso aos dados de uso: permita que o «Task Manager» veja quais apps você usou.\n", "• Accès aux données d'utilisation : autorisez « Task Manager » à voir les apps utilisées.\n", "• Zugriff auf Nutzungsdaten: Erlaube „Task Manager“ zu sehen, welche Apps du benutzt hast.\n"));
        b.append(tr("\nSenza questi permessi può chiudere solo le app in background.", "\nWithout these permissions it can only close background apps.",
                "\nSin estos permisos solo puede cerrar las apps en segundo plano.", "\nSem estas permissões só consegue fechar os apps em segundo plano.", "\nSans ces autorisations, seules les apps en arrière-plan peuvent être fermées.", "\nOhne diese Berechtigungen können nur Hintergrund-Apps geschlossen werden."));
        TextView body = text(b.toString(), 16, MUTED, false);
        body.setPadding(0, dp(10), 0, dp(16));
        box.addView(body);
        LinearLayout btns = new LinearLayout(this);
        if (!a11y) btns.addView(button(tr("Attiva accessibilità", "Enable accessibility",
                "Activar accesibilidad", "Ativar acessibilidade", "Activer l'accessibilité", "Bedienungshilfe aktivieren"), ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!KeyService.openAccessibilitySettings(MainActivity.this))
                    toast(tr("Cerca «Accessibilità» nelle impostazioni dell'auto e attiva «Task Manager»", "Find «Accessibility» in the car settings and enable «Task Manager»",
                "Busca «Accesibilidad» en los ajustes del coche y activa «Task Manager»", "Procure «Acessibilidade» nas configurações do carro e ative «Task Manager»", "Cherchez « Accessibilité » dans les paramètres de la voiture et activez « Task Manager »", "Suche „Bedienungshilfen“ in den Fahrzeugeinstellungen und aktiviere „Task Manager“"));
            }
        }));
        if (!usage) btns.addView(button(tr("Consenti utilizzo", "Allow usage access",
                "Permitir acceso de uso", "Permitir acesso de uso", "Autoriser l'accès", "Nutzungszugriff erlauben"), a11y ? ACCENT : BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!KeyService.openSettings(MainActivity.this, android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    toast(tr("Cerca «Accesso dati di utilizzo» nelle impostazioni dell'auto", "Find «Usage data access» in the car settings",
                "Busca «Acceso a datos de uso» en los ajustes del coche", "Procure «Acesso aos dados de uso» nas configurações do carro", "Cherchez « Accès aux données d'utilisation » dans les paramètres de la voiture", "Suche „Zugriff auf Nutzungsdaten“ in den Fahrzeugeinstellungen"));
            }
        }), lp(-2, -2, 0, a11y ? 0 : dp(12)));
        box.addView(btns);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(14);
        list.addView(box, p);
    }

    /** Con ADB disponibile concede da sola i permessi che altrimenti l'utente dovrebbe dare a mano. */
    private void grantViaAdbOnce() {
        if (grantedViaAdb) return;
        grantedViaAdb = true;
        final Context c = getApplicationContext();
        bg.execute(new Runnable() {
            @Override
            public void run() {
                String pkg = c.getPackageName();
                try {
                    Adb.exec(c, "appops set " + pkg + " GET_USAGE_STATS allow ; appops set " + pkg
                            + " REQUEST_INSTALL_PACKAGES allow", null);
                    enableAccessibility(c, null);
                } catch (IOException ignored) {
                }
            }
        });
    }

    // ================================================================== azioni

    private void refresh() {
        if (loading) return;
        loading = true;
        setStatus(tr("Caricamento…", "Loading…",
                "Cargando…", "Carregando…", "Chargement…", "Wird geladen…"));
        final boolean showBg = Adb.prefs(this).getBoolean("show_bg", false);
        bg.execute(new Runnable() {
            @Override
            public void run() {
                final Tasks.Result r = Tasks.list(MainActivity.this, showBg, auth);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        current = r.entries;
                        adbMode = r.adb;
                        mode = r.mode;
                        lastEntries = r.entries;
                        lastMode = r.mode;
                        if (mode == Tasks.Result.ADB) grantViaAdbOnce();
                        render();
                    }
                });
                if (r.memoryPending) { // modalità sistema: la RAM arriva subito dopo la lista
                    Tasks.fillMemory(MainActivity.this, r.entries);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (current == r.entries && !isFinishing()) updateMemoryViews();
                        }
                    });
                }
            }
        });
    }

    private boolean skipSystem() {
        return Adb.prefs(this).getBoolean("skip_system", true);
    }

    /** True se «Chiudi tutto» salterà questa app. */
    private boolean isExcluded(Tasks.Entry e) {
        return e.isProtected || (e.system && skipSystem());
    }

    private void closeAll() {
        List<String> pkgs = new ArrayList<String>();
        for (Tasks.Entry e : current) {
            if (e.launchable && !isExcluded(e)) pkgs.add(e.pkg);
        }
        if (pkgs.isEmpty()) {
            toast(tr("Niente da chiudere", "Nothing to close",
                "Nada que cerrar", "Nada para fechar", "Rien à fermer", "Nichts zu schließen"));
            return;
        }
        kill(pkgs, pkgs.size() == 1 ? tr("1 app chiusa", "1 app closed",
                "1 app cerrada", "1 app fechado", "1 app fermée", "1 App geschlossen")
                : pkgs.size() + tr(" app chiuse", " apps closed",
                " apps cerradas", " apps fechados", " apps fermées", " Apps geschlossen"));
    }

    private void kill(final List<String> pkgs, final String done) {
        if (KeyService.isBusy()) {
            toast(tr("Chiusura già in corso…", "Already closing…",
                "Ya se están cerrando…", "Já fechando…", "Fermeture déjà en cours…", "Wird bereits geschlossen…"));
            return;
        }
        setStatus(tr("Chiusura…", "Closing…",
                "Cerrando…", "Fechando…", "Fermeture…", "Wird geschlossen…"));
        bg.execute(new Runnable() {
            @Override
            public void run() {
                final boolean viaAdb = Tasks.killInstant(MainActivity.this, pkgs, auth);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (viaAdb) {
                            toast(done);
                            removeFromList(pkgs); // subito a schermo, poi conferma con un aggiornamento
                            main.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    refresh();
                                }
                            }, 300);
                            return;
                        }
                        // Senza ADB: prima i processi in background, poi "Forza interruzione" via accessibilità
                        Tasks.killBackground(MainActivity.this, pkgs);
                        boolean started = KeyService.forceStop(pkgs, new Runnable() {
                            @Override
                            public void run() {
                                toast(done);
                                refresh();
                            }
                        });
                        if (!started) {
                            toast(tr("Chiuse solo in background: attiva l'accessibilità per chiuderle del tutto", "Closed in background only: enable accessibility to close them fully",
                "Cerradas solo en segundo plano: activa la accesibilidad para cerrarlas del todo", "Fechados só em segundo plano: ative a acessibilidade para fechá-los por completo", "Fermées seulement en arrière-plan : activez l'accessibilité pour les fermer complètement", "Nur im Hintergrund geschlossen: Aktiviere die Bedienungshilfe, um sie ganz zu schließen"));
                            refresh();
                        }
                    }
                });
            }
        });
    }

    // ================================================================== disinstallazione

    private static final int REQ_UNINSTALL = 7;
    private String uninstallPkg;
    private CharSequence uninstallLabel;

    /** Elenco di tutte le app installate da te (anche chiuse): tocca per disinstallarla. */
    private void chooseUninstall() {
        final android.content.pm.PackageManager pm = getPackageManager();
        final List<android.content.pm.ApplicationInfo> apps = Uninstall.userApps(this);
        if (apps.isEmpty()) {
            toast(tr("Nessuna app installata da te", "No apps installed by you",
                    "Ninguna app instalada por ti", "Nenhum app instalado por você",
                    "Aucune app installée par vous", "Keine selbst installierten Apps"));
            return;
        }
        final java.util.Map<String, CharSequence> labels = new HashMap<String, CharSequence>();
        for (android.content.pm.ApplicationInfo ai : apps) labels.put(ai.packageName, ai.loadLabel(pm));
        java.util.Collections.sort(apps, new java.util.Comparator<android.content.pm.ApplicationInfo>() {
            @Override
            public int compare(android.content.pm.ApplicationInfo a, android.content.pm.ApplicationInfo b) {
                return labels.get(a.packageName).toString().compareToIgnoreCase(labels.get(b.packageName).toString());
            }
        });
        CharSequence[] items = new CharSequence[apps.size()];
        for (int i = 0; i < apps.size(); i++) items[i] = labels.get(apps.get(i).packageName);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Disinstalla un'app", "Uninstall an app",
                        "Desinstalar una app", "Desinstalar um app", "Désinstaller une app", "Eine App deinstallieren"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String pkg = apps.get(which).packageName;
                        askUninstall(pkg, labels.get(pkg));
                    }
                })
                .setNegativeButton(tr("Annulla", "Cancel",
                        "Cancelar", "Cancelar", "Annuler", "Abbrechen"), null)
                .show();
    }

    private void askUninstall(final String pkg, final CharSequence label) {
        if (!Uninstall.allowed(this, pkg)) {
            toast(tr("Le app di sistema non si possono disinstallare", "System apps can't be uninstalled",
                    "Las apps del sistema no se pueden desinstalar", "Os apps do sistema não podem ser desinstalados",
                    "Les apps système ne peuvent pas être désinstallées", "System-Apps können nicht deinstalliert werden"));
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(String.format(tr("Disinstallare «%s»?", "Uninstall «%s»?",
                        "¿Desinstalar «%s»?", "Desinstalar «%s»?", "Désinstaller « %s » ?", "„%s“ deinstallieren?"), label))
                .setMessage(tr("L'app e i suoi dati verranno rimossi dall'auto.",
                        "The app and its data will be removed from the car.",
                        "La app y sus datos se eliminarán del coche.",
                        "O app e os seus dados serão removidos do carro.",
                        "L'app et ses données seront supprimées de la voiture.",
                        "Die App und ihre Daten werden aus dem Fahrzeug entfernt."))
                .setPositiveButton(tr("Disinstalla", "Uninstall",
                        "Desinstalar", "Desinstalar", "Désinstaller", "Deinstallieren"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        uninstallNow(pkg, label);
                    }
                })
                .setNegativeButton(tr("Annulla", "Cancel",
                        "Cancelar", "Cancelar", "Annuler", "Abbrechen"), null)
                .show();
    }

    private void uninstallNow(final String pkg, final CharSequence label) {
        uninstallPkg = pkg;
        uninstallLabel = label;
        setStatus(tr("Disinstallazione…", "Uninstalling…",
                "Desinstalando…", "Desinstalando…", "Désinstallation…", "Wird deinstalliert…"));
        bg.execute(new Runnable() {
            @Override
            public void run() {
                final int r = Uninstall.start(getApplicationContext(), pkg, auth);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (r == Uninstall.DONE) {
                            afterUninstall(pkg, true, null);
                        } else if (r == Uninstall.NEEDS_DIALOG) {
                            try {
                                startActivityForResult(Uninstall.systemDialog(pkg), REQ_UNINSTALL);
                            } catch (Exception e) {
                                afterUninstall(pkg, false, null);
                            }
                        } else if (r == Uninstall.FAILED) {
                            afterUninstall(pkg, false, Uninstall.lastError);
                        }
                        // STARTED: l'esito arriva da UninstallReceiver
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_UNINSTALL || uninstallPkg == null) return;
        boolean gone;
        try {
            getPackageManager().getApplicationInfo(uninstallPkg, 0);
            gone = false;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            gone = true;
        }
        if (gone) {
            Uninstall.done(uninstallPkg);
            afterUninstall(uninstallPkg, true, null);
        } else {
            uninstallPkg = null;
            refresh(); // annullata dall'utente
        }
    }

    /** Dal ricevitore dell'edizione di sistema (qualsiasi thread). */
    static void onUninstallResult(final String pkg, final boolean ok, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                MainActivity a = instance;
                if (a != null) a.afterUninstall(pkg, ok, msg);
            }
        });
    }

    private void afterUninstall(String pkg, boolean ok, String msg) {
        CharSequence label = pkg != null && pkg.equals(uninstallPkg) && uninstallLabel != null ? uninstallLabel : pkg;
        uninstallPkg = null;
        if (ok) {
            toast(String.format(tr("%s disinstallata", "%s uninstalled",
                    "%s desinstalada", "%s desinstalado", "%s désinstallée", "%s deinstalliert"), label));
            java.util.Set<String> prot = Adb.protectedApps(this);
            if (prot.remove(pkg)) Adb.prefs(this).edit().putStringSet("protected", prot).apply();
            List<String> one = new ArrayList<String>();
            one.add(pkg);
            removeFromList(one);
        } else {
            toast(String.format(tr("%s non è stata disinstallata", "%s was not uninstalled",
                    "%s no se ha desinstalado", "%s não foi desinstalado", "%s n'a pas été désinstallée", "%s wurde nicht deinstalliert"), label)
                    + (msg == null || msg.isEmpty() ? "" : ": " + msg));
        }
        refresh();
    }

    private void removeFromList(List<String> pkgs) {
        Set<String> gone = new HashSet<String>(pkgs);
        List<Tasks.Entry> keep = new ArrayList<Tasks.Entry>();
        for (Tasks.Entry e : current) if (!gone.contains(e.pkg)) keep.add(e);
        current = keep;
        lastEntries = keep;
        render();
    }

    // ================================================================== impostazioni

    private void showSettings() {
        final SharedPreferences p = Adb.prefs(this);
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(24), dp(12), dp(24), dp(4));
        final AlertDialog[] dlg = new AlertDialog[1];

        v.addView(text(tr("Lingua", "Language",
                "Idioma", "Idioma", "Langue", "Sprache"), 17, TEXT, true));
        String chosen = Lang.chosen(this);
        String langLine = chosen.isEmpty()
                ? tr("Automatica: ", "Automatic: ",
                "Automático: ", "Automático: ", "Automatique : ", "Automatisch: ") + Lang.name(Lang.code()) + tr(" (lingua di sistema)", " (system language)",
                " (idioma del sistema)", " (idioma do sistema)", " (langue du système)", " (Systemsprache)")
                : Lang.name(chosen);
        LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(-2, -2);
        lgp.topMargin = dp(8);
        lgp.bottomMargin = dp(18);
        v.addView(button(langLine, BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                chooseLanguage(dlg[0]);
            }
        }), lgp);

        v.addView(text(tr("Modalità", "Mode",
                "Modo", "Modo", "Mode", "Modus"), 17, TEXT, true));
        String modeDesc;
        if (mode == Tasks.Result.SYSTEM)
            modeDesc = tr("Sistema: l'app è firmata con la chiave dell'auto. Chiusura istantanea senza ADB, nessun permesso da attivare a mano.", "System: the app is signed with the car key. Instant close without ADB, no permission to enable by hand.",
                "Sistema: la app está firmada con la clave del coche. Cierre instantáneo sin ADB, ningún permiso que activar a mano.", "Sistema: o app é assinado com a chave do carro. Fechamento instantâneo sem ADB, nenhuma permissão para ativar à mão.", "Système : l'app est signée avec la clé de la voiture. Fermeture instantanée sans ADB, aucune autorisation à activer à la main.", "System: Die App ist mit dem Fahrzeugschlüssel signiert. Sofortiges Schließen ohne ADB, keine Berechtigung manuell zu aktivieren.");
        else if (mode == Tasks.Result.ADB)
            modeDesc = tr("Completa: ADB dell'auto raggiungibile, chiusura istantanea e aggiornamenti silenziosi.", "Full: car ADB reachable, instant close and silent updates.",
                "Completo: ADB del coche accesible, cierre instantáneo y actualizaciones silenciosas.", "Completo: ADB do carro acessível, fechamento instantâneo e atualizações silenciosas.", "Complet : ADB de la voiture accessible, fermeture instantanée et mises à jour silencieuses.", "Voll: ADB des Fahrzeugs erreichbar, sofortiges Schließen und stille Updates.");
        else
            modeDesc = tr("Base: né firma di sistema né ADB. Le app si chiudono tramite accessibilità.", "Basic: neither system signature nor ADB. Apps are closed via accessibility.",
                "Básico: ni firma de sistema ni ADB. Las apps se cierran mediante accesibilidad.", "Básico: nem assinatura de sistema nem ADB. Os apps são fechados pela acessibilidade.", "De base : ni signature système ni ADB. Les apps sont fermées via l'accessibilité.", "Basis: weder Systemsignatur noch ADB. Apps werden über die Bedienungshilfe geschlossen.");
        v.addView(text(modeDesc, 15, MUTED, false));
        LinearLayout.LayoutParams a11yP = new LinearLayout.LayoutParams(-2, -2);
        a11yP.topMargin = dp(8);
        v.addView(button(KeyService.running ? tr("Accessibilità attiva", "Accessibility on",
                "Accesibilidad activa", "Acessibilidade ativa", "Accessibilité activée", "Bedienungshilfe aktiv")
                        : tr("Attiva accessibilità", "Enable accessibility",
                "Activar accesibilidad", "Ativar acessibilidade", "Activer l'accessibilité", "Bedienungshilfe aktivieren"), BTN,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        KeyService.openAccessibilitySettings(MainActivity.this);
                    }
                }), a11yP);
        TextView adbT = text(tr("Indirizzo ADB (avanzato)", "ADB address (advanced)",
                "Dirección ADB (avanzado)", "Endereço ADB (avançado)", "Adresse ADB (avancé)", "ADB-Adresse (erweitert)"), 15, MUTED, false);
        adbT.setPadding(0, dp(12), 0, 0);
        v.addView(adbT);
        LinearLayout hp = new LinearLayout(this);
        final EditText host = new EditText(this);
        host.setText(Adb.host(this));
        host.setSingleLine(true);
        hp.addView(host, new LinearLayout.LayoutParams(0, -2, 3));
        final EditText port = new EditText(this);
        port.setText(String.valueOf(Adb.port(this)));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        hp.addView(port, new LinearLayout.LayoutParams(0, -2, 1));
        v.addView(hp);

        final CheckBox showBg = new CheckBox(this);
        showBg.setText(tr("Mostra anche i servizi in background (avanzato)", "Also show background services (advanced)",
                "Mostrar también los servicios en segundo plano (avanzado)", "Mostrar também os serviços em segundo plano (avançado)", "Afficher aussi les services en arrière-plan (avancé)", "Auch Hintergrunddienste anzeigen (erweitert)"));
        showBg.setChecked(p.getBoolean("show_bg", false));
        v.addView(showBg);

        TextView ct = text(tr("«Chiudi tutto»", "«Close all»",
                "«Cerrar todo»", "«Fechar tudo»", "« Tout fermer »", "„Alle schließen“"), 17, TEXT, true);
        ct.setPadding(0, dp(18), 0, 0);
        v.addView(ct);
        final CheckBox skipSys = new CheckBox(this);
        skipSys.setText(tr("Escludi e nascondi le app di sistema (radio, navigatore, app dell'auto…)", "Exclude and hide system apps (radio, navigation, car apps…)",
                "Excluir y ocultar las apps del sistema (radio, navegador, apps del coche…)", "Excluir e ocultar os apps do sistema (rádio, navegador, apps do carro…)", "Exclure et masquer les apps système (radio, navigation, apps de la voiture…)", "System-Apps ausnehmen und ausblenden (Radio, Navigation, Fahrzeug-Apps…)"));
        skipSys.setChecked(skipSystem());
        skipSys.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Adb.prefs(MainActivity.this).edit().putBoolean("skip_system", on).apply();
                render();
            }
        });
        v.addView(skipSys);
        int nEx = Adb.protectedApps(this).size();
        String exText = nEx == 0 ? tr("Nessuna app esclusa manualmente", "No apps excluded manually",
                "Ninguna app excluida manualmente", "Nenhum app excluído manualmente", "Aucune app exclue manuellement", "Keine App manuell ausgenommen")
                : (nEx == 1 ? tr("1 app esclusa manualmente", "1 app excluded manually",
                "1 app excluida manualmente", "1 app excluído manualmente", "1 app exclue manuellement", "1 App manuell ausgenommen")
                : nEx + tr(" app escluse manualmente", " apps excluded manually",
                " apps excluidas manualmente", " apps excluídos manualmente", " apps exclues manuellement", " Apps manuell ausgenommen"));
        v.addView(text(exText, 15, MUTED, false));
        LinearLayout.LayoutParams exp = new LinearLayout.LayoutParams(-2, -2);
        exp.topMargin = dp(8);
        v.addView(button(tr("Scegli le app da escludere…", "Choose apps to exclude…",
                "Elegir las apps que excluir…", "Escolher os apps a excluir…", "Choisir les apps à exclure…", "Auszunehmende Apps wählen…"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                chooseExcluded();
            }
        }), exp);

        TextView unt = text(tr("Disinstalla app", "Uninstall apps",
                "Desinstalar apps", "Desinstalar apps", "Désinstaller des apps", "Apps deinstallieren"), 17, TEXT, true);
        unt.setPadding(0, dp(18), 0, 0);
        v.addView(unt);
        v.addView(text(tr("Rimuovi dall'auto le app che hai installato tu. Le app di sistema non si possono disinstallare.",
                "Remove from the car the apps you installed. System apps can't be uninstalled.",
                "Quita del coche las apps que has instalado tú. Las apps del sistema no se pueden desinstalar.",
                "Remova do carro os apps que você instalou. Os apps do sistema não podem ser desinstalados.",
                "Supprimez de la voiture les apps que vous avez installées. Les apps système ne peuvent pas être désinstallées.",
                "Entferne die Apps, die du selbst installiert hast. System-Apps können nicht deinstalliert werden."), 15, MUTED, false));
        LinearLayout.LayoutParams unp = new LinearLayout.LayoutParams(-2, -2);
        unp.topMargin = dp(8);
        v.addView(button(tr("Scegli l'app da disinstallare…", "Choose the app to uninstall…",
                "Elegir la app que desinstalar…", "Escolher o app a desinstalar…",
                "Choisir l'app à désinstaller…", "Zu deinstallierende App wählen…"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                dlg[0].dismiss();
                chooseUninstall();
            }
        }), unp);

        TextView ut = text(tr("Aggiornamenti", "Updates",
                "Actualizaciones", "Atualizações", "Mises à jour", "Updates"), 17, TEXT, true);
        ut.setPadding(0, dp(18), 0, 0);
        v.addView(ut);
        v.addView(text(tr("Versione installata: ", "Installed version: ",
                "Versión instalada: ", "Versão instalada: ", "Version installée : ", "Installierte Version: ") + Updater.currentName(this), 15, MUTED, false));
        final CheckBox autoUp = new CheckBox(this);
        autoUp.setText(tr("Controlla automaticamente se ci sono aggiornamenti", "Automatically check for updates",
                "Buscar actualizaciones automáticamente", "Procurar atualizações automaticamente", "Rechercher automatiquement les mises à jour", "Automatisch nach Updates suchen"));
        autoUp.setChecked(Updater.autoEnabled(this));
        autoUp.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Adb.prefs(MainActivity.this).edit().putBoolean("auto_update", on).apply();
            }
        });
        v.addView(autoUp);
        final CheckBox autoInst = new CheckBox(this);
        autoInst.setText(tr("Installa gli aggiornamenti da solo (senza ADB serve un tocco su «Installa»)", "Install updates automatically (without ADB a tap on «Install» is needed)",
                "Instalar las actualizaciones solo (sin ADB hace falta tocar «Instalar»)", "Instalar as atualizações sozinho (sem ADB é preciso tocar em «Instalar»)", "Installer les mises à jour automatiquement (sans ADB, il faut toucher « Installer »)", "Updates selbst installieren (ohne ADB ist ein Tippen auf „Installieren“ nötig)"));
        autoInst.setChecked(Updater.autoInstall(this));
        autoInst.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Adb.prefs(MainActivity.this).edit().putBoolean("auto_install", on).apply();
            }
        });
        v.addView(autoInst);
        LinearLayout.LayoutParams upp = new LinearLayout.LayoutParams(-2, -2);
        upp.topMargin = dp(8);
        v.addView(button(tr("Controlla ora", "Check now",
                "Buscar ahora", "Procurar agora", "Vérifier maintenant", "Jetzt prüfen"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                checkUpdates(true);
            }
        }), upp);

        TextView kt = text(tr("Tasto al volante", "Steering-wheel button",
                "Botón del volante", "Botão do volante", "Bouton au volant", "Lenkradtaste"), 17, TEXT, true);
        kt.setPadding(0, dp(18), 0, 0);
        v.addView(kt);
        String desc = p.getString("key_desc", null);
        String keyLine = desc == null ? tr("Nessun tasto impostato", "No button set",
                "Ningún botón configurado", "Nenhum botão configurado", "Aucun bouton configuré", "Keine Taste festgelegt")
                : tr("Impostato: ", "Set: ",
                "Configurado: ", "Configurado: ", "Configuré : ", "Festgelegt: ") + desc + (KeyHub.SRC_A11Y.equals(p.getString("key_src", null))
                ? tr("  (via accessibilità)", "  (via accessibility)",
                "  (mediante accesibilidad)", "  (pela acessibilidade)", "  (via l'accessibilité)", "  (über Bedienungshilfe)") : tr("  (via getevent)", "  (via getevent)",
                "  (mediante getevent)", "  (via getevent)", "  (via getevent)", "  (über getevent)"));
        v.addView(text(keyLine, 15, MUTED, false));

        final CheckBox longPress = new CheckBox(this);
        longPress.setText(tr("Solo con pressione lunga (la pressione breve mantiene la funzione originale)", "Long-press only (a short press keeps the original function)",
                "Solo con pulsación larga (la pulsación corta mantiene la función original)", "Só com pressão longa (a pressão curta mantém a função original)", "Appui long uniquement (l'appui court garde la fonction d'origine)", "Nur bei langem Drücken (kurzes Drücken behält die ursprüngliche Funktion)"));
        longPress.setChecked(p.getBoolean("key_long", false));
        v.addView(longPress);

        LinearLayout kb = new LinearLayout(this);
        kb.addView(button(tr("Imposta tasto…", "Set button…",
                "Configurar botón…", "Configurar botão…", "Configurer le bouton…", "Taste festlegen…"), ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                saveSettings(host, port, showBg, longPress);
                dlg[0].dismiss();
                learnKey();
            }
        }));
        kb.addView(button(tr("Rimuovi", "Remove",
                "Quitar", "Remover", "Supprimer", "Entfernen"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                KeyHub.clear(MainActivity.this);
                KeyMonitorService.stop(MainActivity.this);
                toast(tr("Tasto rimosso", "Button removed",
                "Botón eliminado", "Botão removido", "Bouton supprimé", "Taste entfernt"));
                dlg[0].dismiss();
            }
        }), lp(-2, -2, 0, dp(12)));
        LinearLayout.LayoutParams kbp = new LinearLayout.LayoutParams(-2, -2);
        kbp.topMargin = dp(8);
        v.addView(kb, kbp);

        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        dlg[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Impostazioni", "Settings",
                "Ajustes", "Configurações", "Paramètres", "Einstellungen"))
                .setView(sv)
                .setPositiveButton(tr("Salva", "Save",
                "Guardar", "Salvar", "Enregistrer", "Speichern"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        saveSettings(host, port, showBg, longPress);
                        Adb.reset();
                        refresh();
                    }
                })
                .setNegativeButton(tr("Annulla", "Cancel",
                "Cancelar", "Cancelar", "Annuler", "Abbrechen"), null)
                .create();
        dlg[0].show();
    }

    /** Scelta della lingua: automatica (di sistema) oppure una delle sei. */
    private void chooseLanguage(final AlertDialog settings) {
        final String[] codes = new String[Lang.CODES.length + 1];
        CharSequence[] items = new CharSequence[codes.length];
        codes[0] = "";
        items[0] = tr("Automatica (lingua di sistema)", "Automatic (system language)",
                "Automático (idioma del sistema)", "Automático (idioma do sistema)", "Automatique (langue du système)", "Automatisch (Systemsprache)");
        for (int i = 0; i < Lang.CODES.length; i++) {
            codes[i + 1] = Lang.CODES[i];
            items[i + 1] = Lang.NAMES[i];
        }
        String cur = Lang.chosen(this);
        int sel = 0;
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(cur)) sel = i;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Lingua", "Language",
                "Idioma", "Idioma", "Langue", "Sprache"))
                .setSingleChoiceItems(items, sel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (codes[which].equals(Lang.chosen(MainActivity.this))) return;
                        Lang.save(MainActivity.this, codes[which]);
                        if (settings != null) settings.dismiss();
                        recreate(); // ridisegna tutta l'interfaccia nella nuova lingua
                    }
                })
                .setNegativeButton(tr("Annulla", "Cancel",
                "Cancelar", "Cancelar", "Annuler", "Abbrechen"), null)
                .show();
    }

    // ================================================================== aggiornamenti

    private void checkUpdates(final boolean manual) {
        if (manual) toast(tr("Controllo aggiornamenti…", "Checking for updates…",
                "Buscando actualizaciones…", "Procurando atualizações…", "Recherche de mises à jour…", "Suche nach Updates…"));
        bg.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Updater.Release r = Updater.checkNewer(getApplicationContext());
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            if (r != null) offerUpdate(r);
                            else if (manual) toast(tr("Hai già l'ultima versione (", "You already have the latest version (",
                "Ya tienes la última versión (", "Você já tem a versão mais recente (", "Vous avez déjà la dernière version (", "Du hast bereits die neueste Version (")
                                    + Updater.currentName(MainActivity.this) + ")");
                        }
                    });
                } catch (final IOException e) {
                    if (manual) main.post(new Runnable() {
                        @Override
                        public void run() {
                            toast(tr("Impossibile controllare: ", "Couldn't check: ",
                "No se pudo comprobar: ", "Não foi possível verificar: ", "Vérification impossible : ", "Prüfung nicht möglich: ") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void offerUpdate(final Updater.Release r) {
        String notes = r.notes == null ? "" : Lang.pickSection(r.notes).trim();
        if (notes.length() > 700) notes = notes.substring(0, 700) + "…";
        final String fnotes = notes;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Aggiornamento disponibile: ", "Update available: ",
                "Actualización disponible: ", "Atualização disponível: ", "Mise à jour disponible : ", "Update verfügbar: ") + r.tag)
                .setMessage(tr("Versione installata: ", "Installed version: ",
                "Versión instalada: ", "Versão instalada: ", "Version installée : ", "Installierte Version: ") + Updater.currentName(this)
                        + (fnotes.isEmpty() ? "" : tr("\n\nNovità:\n", "\n\nWhat's new:\n",
                "\n\nNovedades:\n", "\n\nNovidades:\n", "\n\nNouveautés :\n", "\n\nNeuigkeiten:\n") + fnotes))
                .setPositiveButton(tr("Aggiorna", "Update",
                "Actualizar", "Atualizar", "Mettre à jour", "Aktualisieren"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (Updater.pending(MainActivity.this) != null) installDownloaded(r);
                        else runUpdate(r);
                    }
                })
                .setNegativeButton(tr("Più tardi", "Later",
                "Más tarde", "Mais tarde", "Plus tard", "Später"), null)
                .show();
    }

    /** Aggiornamento già scaricato in background: installa subito. */
    private void installDownloaded(final Updater.Release r) {
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Aggiornamento a ", "Updating to ",
                "Actualizando a ", "Atualizando para ", "Mise à jour vers ", "Update auf ") + r.tag)
                .setMessage(tr("Installazione in corso…", "Installing…",
                "Instalando…", "Instalando…", "Installation…", "Wird installiert…"))
                .setNegativeButton(tr("Chiudi", "Close",
                "Cerrar", "Fechar", "Fermer", "Schließen"), null)
                .create();
        dlg.show();
        bg.execute(new Runnable() {
            @Override
            public void run() {
                installStep(r, Updater.downloadedFile(getApplicationContext()), dlg);
            }
        });
    }

    /** Da chiamare in background: ADB silenzioso se c'è, altrimenti installer di Android. */
    private void installStep(final Updater.Release r, java.io.File apk, final AlertDialog dlg) {
        try {
            final boolean silent = Updater.installAny(getApplicationContext(), apk, auth, true);
            main.post(new Runnable() {
                @Override
                public void run() {
                    dlg.setMessage(silent
                            ? tr("Installazione in corso…\nL'app si chiuderà e si riaprirà da sola.", "Installing…\nThe app will close and reopen by itself.",
                "Instalando…\nLa app se cerrará y se volverá a abrir sola.", "Instalando…\nO app vai fechar e reabrir sozinho.", "Installation…\nL'app va se fermer et se rouvrir toute seule.", "Wird installiert…\nDie App schließt sich und öffnet sich von selbst wieder.")
                            : tr("Conferma con «Installa» nella schermata di Android.\nAl termine riapri il Task Manager.", "Confirm with «Install» on the Android screen.\nReopen Task Manager when done.",
                "Confirma con «Instalar» en la pantalla de Android.\nAl terminar, vuelve a abrir Task Manager.", "Confirme com «Instalar» na tela do Android.\nNo final, abra de novo o Task Manager.", "Confirmez avec « Installer » sur l'écran Android.\nRouvrez ensuite Task Manager.", "Bestätige mit „Installieren“ auf dem Android-Bildschirm.\nÖffne danach Task Manager erneut."));
                }
            });
            if (silent) main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    String log = Updater.readLog(MainActivity.this);
                    dlg.setMessage(tr("L'installazione non è riuscita.\n\n", "Installation failed.\n\n",
                "La instalación no se ha completado.\n\n", "A instalação não foi concluída.\n\n", "L'installation a échoué.\n\n", "Die Installation ist fehlgeschlagen.\n\n")
                            + (log == null || log.isEmpty() ? tr("Nessun dettaglio disponibile.", "No details available.",
                "No hay detalles disponibles.", "Nenhum detalhe disponível.", "Aucun détail disponible.", "Keine Details verfügbar.") : log));
                }
            }, 40000);
        } catch (final IOException e) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    dlg.setMessage(tr("Aggiornamento non riuscito: ", "Update failed: ",
                "Actualización fallida: ", "Falha na atualização: ", "Échec de la mise à jour : ", "Update fehlgeschlagen: ") + e.getMessage());
                }
            });
        }
    }

    private void runUpdate(final Updater.Release r) {
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Aggiornamento a ", "Updating to ",
                "Actualizando a ", "Atualizando para ", "Mise à jour vers ", "Update auf ") + r.tag)
                .setMessage(tr("Download in corso…", "Downloading…",
                "Descargando…", "Baixando…", "Téléchargement…", "Wird heruntergeladen…"))
                .setNegativeButton(tr("Chiudi", "Close",
                "Cerrar", "Fechar", "Fermer", "Schließen"), null)
                .setCancelable(false)
                .create();
        dlg.show();
        bg.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.File apk = Updater.download(getApplicationContext(), r, new Updater.Progress() {
                        @Override
                        public void onProgress(final int pct) {
                            main.post(new Runnable() {
                                @Override
                                public void run() {
                                    dlg.setMessage(tr("Download in corso… ", "Downloading… ",
                "Descargando… ", "Baixando… ", "Téléchargement… ", "Wird heruntergeladen… ") + pct + "%");
                                }
                            });
                        }
                    });
                    Updater.setPending(getApplicationContext(), r);
                    installStep(r, apk, dlg);
                } catch (final IOException e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            dlg.setMessage(tr("Aggiornamento non riuscito: ", "Update failed: ",
                "Actualización fallida: ", "Falha na atualização: ", "Échec de la mise à jour : ", "Update fehlgeschlagen: ") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /** Elenco di tutte le app con icona nel launcher: spunta quelle da non chiudere mai con «Chiudi tutto». */
    private void chooseExcluded() {
        android.content.pm.PackageManager pm = getPackageManager();
        final java.util.Set<String> prot = Adb.protectedApps(this);
        Tasks.invalidatePackages(); // elenco aggiornato, anche se hai appena installato un'app
        final List<String> pkgs = new ArrayList<String>(Tasks.launchablePackages(pm));
        pkgs.remove(getPackageName());
        final java.util.Map<String, String> labels = new java.util.HashMap<String, String>();
        final java.util.Set<String> sys = new java.util.HashSet<String>();
        for (String pkg : pkgs) {
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                labels.put(pkg, ai.loadLabel(pm).toString());
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) sys.add(pkg);
            } catch (Exception ex) {
                labels.put(pkg, pkg);
            }
        }
        java.util.Collections.sort(pkgs, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                boolean sa = sys.contains(a), sb = sys.contains(b);
                if (sa != sb) return sa ? 1 : -1; // prima le app installate da te
                return labels.get(a).compareToIgnoreCase(labels.get(b));
            }
        });
        CharSequence[] items = new CharSequence[pkgs.size()];
        final boolean[] checked = new boolean[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) {
            String pkg = pkgs.get(i);
            items[i] = labels.get(pkg) + (sys.contains(pkg) ? tr("   (sistema)", "   (system)",
                "   (sistema)", "   (sistema)", "   (système)", "   (System)") : "");
            checked[i] = prot.contains(pkg);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Escludi da «Chiudi tutto»", "Exclude from «Close all»",
                "Excluir de «Cerrar todo»", "Excluir de «Fechar tudo»", "Exclure de « Tout fermer »", "Von „Alle schließen“ ausnehmen"))
                .setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean on) {
                        checked[which] = on;
                    }
                })
                .setPositiveButton(tr("Salva", "Save",
                "Guardar", "Salvar", "Enregistrer", "Speichern"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        java.util.Set<String> s = new java.util.HashSet<String>(prot);
                        for (int i = 0; i < pkgs.size(); i++) {
                            if (checked[i]) s.add(pkgs.get(i));
                            else s.remove(pkgs.get(i));
                        }
                        Adb.prefs(MainActivity.this).edit().putStringSet("protected", s).apply();
                        for (Tasks.Entry e : current) e.isProtected = s.contains(e.pkg);
                        render();
                        toast(tr("Esclusioni salvate", "Exclusions saved",
                "Exclusiones guardadas", "Exclusões salvas", "Exclusions enregistrées", "Ausnahmen gespeichert"));
                    }
                })
                .setNegativeButton(tr("Annulla", "Cancel",
                "Cancelar", "Cancelar", "Annuler", "Abbrechen"), null)
                .show();
    }

    private void saveSettings(EditText host, EditText port, CheckBox showBg, CheckBox longPress) {
        int pt = 5555;
        try {
            pt = Integer.parseInt(port.getText().toString().trim());
        } catch (NumberFormatException ignored) {
        }
        String h = host.getText().toString().trim();
        Adb.prefs(this).edit()
                .putString("host", h.isEmpty() ? "127.0.0.1" : h)
                .putInt("port", pt)
                .putBoolean("show_bg", showBg.isChecked())
                .putBoolean("key_long", longPress.isChecked())
                .apply();
    }

    /** Attiva (via ADB) il servizio di accessibilità del Task Manager, se non è già attivo. */
    private static void enableAccessibility(Context c, AdbClient.AuthListener l) throws IOException {
        if (KeyService.running) return;
        // senza ADB: l'utente deve attivarla a mano (gestito da chi chiama)
        String comp = c.getPackageName() + "/" + KeyService.class.getName();
        String cur = Adb.exec(c, "settings get secure enabled_accessibility_services", l).trim();
        if (!cur.contains(comp)) {
            String nv = (cur.isEmpty() || cur.equals("null")) ? comp : cur + ":" + comp;
            Adb.exec(c, "settings put secure enabled_accessibility_services " + Adb.q(nv), l);
        }
        Adb.exec(c, "settings put secure accessibility_enabled 1", l);
        for (int i = 0; i < 30 && !KeyService.running; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** All'apertura: se il tasto è configurato, si assicura che l'ascolto sia attivo. */
    private void ensureKeyListeners() {
        final Context c = getApplicationContext();
        String src = Adb.prefs(c).getString("key_src", null);
        if (KeyHub.SRC_RAW.equals(src)) {
            KeyMonitorService.start(c);
        } else if (KeyHub.SRC_A11Y.equals(src) && !KeyService.running) {
            bg.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        enableAccessibility(c, null);
                    } catch (IOException ignored) {
                    }
                }
            });
        }
    }

    private void learnKey() {
        final AlertDialog wait = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Imposta tasto al volante", "Set steering-wheel button",
                "Configurar botón del volante", "Configurar botão do volante", "Configurer le bouton au volant", "Lenkradtaste festlegen"))
                .setMessage(tr("Preparazione…", "Preparing…",
                "Preparando…", "Preparando…", "Préparation…", "Vorbereitung…"))
                .setNegativeButton(tr("Annulla", "Cancel",
                "Cancelar", "Cancelar", "Annuler", "Abbrechen"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        KeyHub.cancelLearning();
                        if (!KeyMonitorService.needed(MainActivity.this)) KeyMonitorService.stop(MainActivity.this);
                    }
                })
                .setCancelable(false)
                .create();
        wait.show();
        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                if (!KeyHub.isLearning()) return;
                KeyHub.cancelLearning();
                if (!KeyMonitorService.needed(MainActivity.this)) KeyMonitorService.stop(MainActivity.this);
                wait.setMessage(tr("Nessun tasto rilevato.\n\nProva con un altro tasto: alcuni tasti del volante vengono gestiti direttamente dall'auto e non arrivano ad Android.", "No button detected.\n\nTry another button: some steering-wheel buttons are handled directly by the car and never reach Android.",
                "No se ha detectado ningún botón.\n\nPrueba con otro botón: algunos botones del volante los gestiona directamente el coche y no llegan a Android.", "Nenhum botão detectado.\n\nTente outro botão: alguns botões do volante são geridos diretamente pelo carro e não chegam ao Android.", "Aucun bouton détecté.\n\nEssayez un autre bouton : certains boutons au volant sont gérés directement par la voiture et n'arrivent pas à Android.", "Keine Taste erkannt.\n\nVersuche eine andere Taste: Manche Lenkradtasten werden direkt vom Fahrzeug verarbeitet und erreichen Android nicht."));
            }
        };
        bg.execute(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    enableAccessibility(getApplicationContext(), auth);
                } catch (IOException e) {
                    err = e.getMessage();
                }
                final String error = err;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (error != null && !KeyService.running) {
                            wait.setMessage(tr("Per usare il tasto al volante attiva «Task Manager» in Impostazioni → Accessibilità, poi torna qui e riprova.", "To use the steering-wheel button, enable «Task Manager» in Settings → Accessibility, then come back and try again.",
                "Para usar el botón del volante activa «Task Manager» en Ajustes → Accesibilidad, luego vuelve aquí e inténtalo de nuevo.", "Para usar o botão do volante ative «Task Manager» em Configurações → Acessibilidade, depois volte aqui e tente de novo.", "Pour utiliser le bouton au volant, activez « Task Manager » dans Paramètres → Accessibilité, puis revenez ici et réessayez.", "Um die Lenkradtaste zu nutzen, aktiviere „Task Manager“ unter Einstellungen → Bedienungshilfen, komm dann hierher zurück und versuche es erneut."));
                            KeyService.openAccessibilitySettings(MainActivity.this);
                            return;
                        }
                        KeyHub.startLearning(new KeyHub.LearnCallback() {
                            @Override
                            public void onLearned(String src, String id, String d) {
                                main.removeCallbacks(timeout);
                                KeyHub.save(MainActivity.this, src, id, d);
                                if (KeyHub.SRC_RAW.equals(src)) KeyMonitorService.start(MainActivity.this);
                                else KeyMonitorService.stop(MainActivity.this);
                                wait.setMessage(tr("Tasto impostato: ", "Button set: ",
                "Botón configurado: ", "Botão configurado: ", "Bouton configuré : ", "Taste festgelegt: ") + d + "\n\n"
                                        + (KeyHub.SRC_A11Y.equals(src)
                                        ? tr("Premendolo aprirai e chiuderai il Task Manager.", "Pressing it will open and close Task Manager.",
                "Al pulsarlo abrirás y cerrarás Task Manager.", "Ao pressioná-lo você abre e fecha o Task Manager.", "En appuyant dessus, vous ouvrirez et fermerez Task Manager.", "Damit öffnest und schließt du Task Manager.")
                                        : tr("Premendolo aprirai e chiuderai il Task Manager. Nota: questo tasto manterrà anche la sua funzione originale.", "Pressing it will open and close Task Manager. Note: this button also keeps its original function.",
                "Al pulsarlo abrirás y cerrarás Task Manager. Nota: este botón mantendrá también su función original.", "Ao pressioná-lo você abre e fecha o Task Manager. Nota: este botão também manterá a sua função original.", "En appuyant dessus, vous ouvrirez et fermerez Task Manager. Remarque : ce bouton garde aussi sa fonction d'origine.", "Damit öffnest und schließt du Task Manager. Hinweis: Die Taste behält auch ihre ursprüngliche Funktion.")));
                                wait.getButton(DialogInterface.BUTTON_NEGATIVE).setText("OK");
                            }
                        });
                        KeyMonitorService.start(MainActivity.this);
                        wait.setMessage(tr("Premi ora sul volante il tasto che vuoi usare…\n(hai 20 secondi)", "Now press the steering-wheel button you want to use…\n(you have 20 seconds)",
                "Pulsa ahora en el volante el botón que quieres usar…\n(tienes 20 segundos)", "Pressione agora no volante o botão que quer usar…\n(você tem 20 segundos)", "Appuyez maintenant sur le bouton au volant que vous voulez utiliser…\n(vous avez 20 secondes)", "Drücke jetzt die Lenkradtaste, die du verwenden möchtest…\n(du hast 20 Sekunden)"));
                        main.postDelayed(timeout, 20000);
                    }
                });
            }
        });
    }
}
