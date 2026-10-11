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
import java.util.List;
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

    private static String tr(String it, String en) {
        return Lang.tr(it, en);
    }

    private final AdbClient.AuthListener auth = new AdbClient.AuthListener() {
        @Override
        public void onWaitingForApproval() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    setStatus(tr("Sullo schermo dell'auto conferma «Consentire il debug?» (spunta «Consenti sempre»)",
                            "On the car screen tap «Allow debugging?» (check «Always allow»)"));
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
        instance = this;
        getWindow().getDecorView().setBackgroundColor(BG);
        setContentView(buildUi());
        ensureKeyListeners();
        UpdateJobService.schedule(getApplicationContext());
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

        header.addView(button(tr("Aggiorna", "Refresh"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        }), lp(-2, -2, 0, dp(12)));
        header.addView(button(tr("Chiudi tutto", "Close all"), DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeAll();
            }
        }), lp(-2, -2, 0, dp(12)));
        header.addView(button(tr("Impostazioni", "Settings"), BTN, new View.OnClickListener() {
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

        TextView hint = text(tr("Tocca un'app per aprirla · tieni premuto per escluderla da «Chiudi tutto»",
                "Tap an app to open it · long-press to exclude it from «Close all»"), 14, MUTED, false);
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
        String st = apps + (apps == 1 ? tr(" app aperta", " app open") : tr(" app aperte", " apps open"));
        if (mode == Tasks.Result.SYSTEM) st += " · " + (kb / 1024) + tr(" MB · modalità sistema", " MB · system mode");
        else if (mode == Tasks.Result.ADB) st += " · " + (kb / 1024) + tr(" MB · modalità completa (ADB)", " MB · full mode (ADB)");
        else st += tr(" · modalità base", " · basic mode");
        int hidden = current.size() - vis.size();
        if (hidden > 0) st += " · " + hidden + tr(" di sistema nascoste", " system hidden");
        setStatus(st);
    }

    private void render() {
        list.removeAllViews();
        List<Tasks.Entry> vis = shown();
        updateStatus(vis);
        if (!adbMode) addSetupCard();
        if (vis.isEmpty()) {
            TextView t = text(tr("Nessuna app aperta", "No apps open"), 22, MUTED, false);
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
                TextView h = text(tr("Servizi in background (chiudili solo se sai cosa fanno)",
                        "Background services (close only if you know what they do)"), 16, MUTED, true);
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
        String tag = e.isProtected ? tr("  · esclusa", "  · excluded")
                : (e.system && skipSystem() ? tr("  · sistema", "  · system") : "");
        TextView name = text(e.label + tag, 20, TEXT, true);
        name.setSingleLine(true);
        texts.addView(name);
        StringBuilder sub = new StringBuilder();
        if (e.foreground) sub.append(tr("In primo piano · ", "Foreground · "));
        if (e.system) sub.append(tr("App di sistema · ", "System app · "));
        if (e.rssKb >= 0) sub.append(e.rssKb / 1024).append(" MB");
        else if (sub.length() == 0) sub.append(tr("Aperta da questa accensione", "Open since power-on"));
        if (e.processes > 1) sub.append(" · ").append(e.processes).append(tr(" processi", " processes"));
        if (sub.toString().endsWith(" · ")) sub.setLength(sub.length() - 3);
        sub.append("\n").append(e.pkg);
        TextView s = text(sub.toString(), 14, MUTED, false);
        texts.addView(s);
        c.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        c.addView(button(tr("Chiudi", "Close"), DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> one = new ArrayList<String>();
                one.add(e.pkg);
                kill(one, e.label + tr(" chiusa", " closed"));
            }
        }));

        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(e.pkg);
                if (i == null) {
                    toast(tr("Questa app non ha una schermata da aprire", "This app has no screen to open"));
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                    finish();
                } catch (Exception ex) {
                    toast(tr("Impossibile aprire l'app", "Couldn't open the app"));
                }
            }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean p = Adb.toggleProtected(MainActivity.this, e.pkg);
                toast(p ? e.label + tr(" esclusa: «Chiudi tutto» la salterà", " excluded: «Close all» will skip it")
                        : e.label + tr(" non è più esclusa", " is no longer excluded"));
                e.isProtected = p;
                render();
                return true;
            }
        });
        return c;
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
        box.addView(text(tr("Completa la configurazione", "Finish setup"), 22, TEXT, true));
        StringBuilder b = new StringBuilder();
        if (!a11y) b.append(tr("• Accessibilità: attiva «Task Manager» per vedere le app aperte e chiuderle davvero (l'app preme da sola «Forza interruzione»).\n",
                "• Accessibility: enable «Task Manager» to see open apps and really close them (the app taps «Force stop» by itself).\n"));
        if (!usage) b.append(tr("• Accesso ai dati di utilizzo: consenti a «Task Manager» di vedere quali app hai usato.\n",
                "• Usage access: allow «Task Manager» to see which apps you used.\n"));
        b.append(tr("\nSenza questi permessi può chiudere solo le app in background.",
                "\nWithout these permissions it can only close background apps."));
        TextView body = text(b.toString(), 16, MUTED, false);
        body.setPadding(0, dp(10), 0, dp(16));
        box.addView(body);
        LinearLayout btns = new LinearLayout(this);
        if (!a11y) btns.addView(button(tr("Attiva accessibilità", "Enable accessibility"), ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!KeyService.openAccessibilitySettings(MainActivity.this))
                    toast(tr("Cerca «Accessibilità» nelle impostazioni dell'auto e attiva «Task Manager»",
                            "Find «Accessibility» in the car settings and enable «Task Manager»"));
            }
        }));
        if (!usage) btns.addView(button(tr("Consenti utilizzo", "Allow usage access"), a11y ? ACCENT : BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!KeyService.openSettings(MainActivity.this, android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    toast(tr("Cerca «Accesso dati di utilizzo» nelle impostazioni dell'auto",
                            "Find «Usage data access» in the car settings"));
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
        setStatus(tr("Caricamento…", "Loading…"));
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
                        if (mode == Tasks.Result.ADB) grantViaAdbOnce();
                        render();
                    }
                });
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
            toast(tr("Niente da chiudere", "Nothing to close"));
            return;
        }
        kill(pkgs, pkgs.size() == 1 ? tr("1 app chiusa", "1 app closed")
                : pkgs.size() + tr(" app chiuse", " apps closed"));
    }

    private void kill(final List<String> pkgs, final String done) {
        if (KeyService.isBusy()) {
            toast(tr("Chiusura già in corso…", "Already closing…"));
            return;
        }
        setStatus(tr("Chiusura…", "Closing…"));
        bg.execute(new Runnable() {
            @Override
            public void run() {
                final boolean viaAdb = Tasks.killInstant(MainActivity.this, pkgs, auth);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (viaAdb) {
                            toast(done);
                            main.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    refresh();
                                }
                            }, 400);
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
                            toast(tr("Chiuse solo in background: attiva l'accessibilità per chiuderle del tutto",
                                    "Closed in background only: enable accessibility to close them fully"));
                            refresh();
                        }
                    }
                });
            }
        });
    }

    // ================================================================== impostazioni

    private void showSettings() {
        final SharedPreferences p = Adb.prefs(this);
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(24), dp(12), dp(24), dp(4));

        v.addView(text(tr("Modalità", "Mode"), 17, TEXT, true));
        String modeDesc;
        if (mode == Tasks.Result.SYSTEM)
            modeDesc = tr("Sistema: l'app è firmata con la chiave dell'auto. Chiusura istantanea senza ADB, nessun permesso da attivare a mano.",
                    "System: the app is signed with the car key. Instant close without ADB, no permission to enable by hand.");
        else if (mode == Tasks.Result.ADB)
            modeDesc = tr("Completa: ADB dell'auto raggiungibile, chiusura istantanea e aggiornamenti silenziosi.",
                    "Full: car ADB reachable, instant close and silent updates.");
        else
            modeDesc = tr("Base: né firma di sistema né ADB. Le app si chiudono tramite accessibilità.",
                    "Basic: neither system signature nor ADB. Apps are closed via accessibility.");
        v.addView(text(modeDesc, 15, MUTED, false));
        LinearLayout.LayoutParams a11yP = new LinearLayout.LayoutParams(-2, -2);
        a11yP.topMargin = dp(8);
        v.addView(button(KeyService.running ? tr("Accessibilità attiva", "Accessibility on")
                        : tr("Attiva accessibilità", "Enable accessibility"), BTN,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        KeyService.openAccessibilitySettings(MainActivity.this);
                    }
                }), a11yP);
        TextView adbT = text(tr("Indirizzo ADB (avanzato)", "ADB address (advanced)"), 15, MUTED, false);
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
        showBg.setText(tr("Mostra anche i servizi in background (avanzato)", "Also show background services (advanced)"));
        showBg.setChecked(p.getBoolean("show_bg", false));
        v.addView(showBg);

        TextView ct = text(tr("«Chiudi tutto»", "«Close all»"), 17, TEXT, true);
        ct.setPadding(0, dp(18), 0, 0);
        v.addView(ct);
        final CheckBox skipSys = new CheckBox(this);
        skipSys.setText(tr("Escludi e nascondi le app di sistema (radio, navigatore, app dell'auto…)",
                "Exclude and hide system apps (radio, navigation, car apps…)"));
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
        String exText = nEx == 0 ? tr("Nessuna app esclusa manualmente", "No apps excluded manually")
                : (nEx == 1 ? tr("1 app esclusa manualmente", "1 app excluded manually")
                : nEx + tr(" app escluse manualmente", " apps excluded manually"));
        v.addView(text(exText, 15, MUTED, false));
        LinearLayout.LayoutParams exp = new LinearLayout.LayoutParams(-2, -2);
        exp.topMargin = dp(8);
        v.addView(button(tr("Scegli le app da escludere…", "Choose apps to exclude…"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                chooseExcluded();
            }
        }), exp);

        TextView ut = text(tr("Aggiornamenti", "Updates"), 17, TEXT, true);
        ut.setPadding(0, dp(18), 0, 0);
        v.addView(ut);
        v.addView(text(tr("Versione installata: ", "Installed version: ") + Updater.currentName(this), 15, MUTED, false));
        final CheckBox autoUp = new CheckBox(this);
        autoUp.setText(tr("Controlla automaticamente se ci sono aggiornamenti", "Automatically check for updates"));
        autoUp.setChecked(Updater.autoEnabled(this));
        autoUp.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Adb.prefs(MainActivity.this).edit().putBoolean("auto_update", on).apply();
            }
        });
        v.addView(autoUp);
        final CheckBox autoInst = new CheckBox(this);
        autoInst.setText(tr("Installa gli aggiornamenti da solo (senza ADB serve un tocco su «Installa»)",
                "Install updates automatically (without ADB a tap on «Install» is needed)"));
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
        v.addView(button(tr("Controlla ora", "Check now"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                checkUpdates(true);
            }
        }), upp);

        TextView kt = text(tr("Tasto al volante", "Steering-wheel button"), 17, TEXT, true);
        kt.setPadding(0, dp(18), 0, 0);
        v.addView(kt);
        String desc = p.getString("key_desc", null);
        String keyLine = desc == null ? tr("Nessun tasto impostato", "No button set")
                : tr("Impostato: ", "Set: ") + desc + (KeyHub.SRC_A11Y.equals(p.getString("key_src", null))
                ? tr("  (via accessibilità)", "  (via accessibility)") : tr("  (via getevent)", "  (via getevent)"));
        v.addView(text(keyLine, 15, MUTED, false));

        final CheckBox longPress = new CheckBox(this);
        longPress.setText(tr("Solo con pressione lunga (la pressione breve mantiene la funzione originale)",
                "Long-press only (a short press keeps the original function)"));
        longPress.setChecked(p.getBoolean("key_long", false));
        v.addView(longPress);

        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout kb = new LinearLayout(this);
        kb.addView(button(tr("Imposta tasto…", "Set button…"), ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                saveSettings(host, port, showBg, longPress);
                dlg[0].dismiss();
                learnKey();
            }
        }));
        kb.addView(button(tr("Rimuovi", "Remove"), BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                KeyHub.clear(MainActivity.this);
                KeyMonitorService.stop(MainActivity.this);
                toast(tr("Tasto rimosso", "Button removed"));
                dlg[0].dismiss();
            }
        }), lp(-2, -2, 0, dp(12)));
        LinearLayout.LayoutParams kbp = new LinearLayout.LayoutParams(-2, -2);
        kbp.topMargin = dp(8);
        v.addView(kb, kbp);

        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        dlg[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Impostazioni", "Settings"))
                .setView(sv)
                .setPositiveButton(tr("Salva", "Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        saveSettings(host, port, showBg, longPress);
                        Adb.reset();
                        refresh();
                    }
                })
                .setNegativeButton(tr("Annulla", "Cancel"), null)
                .create();
        dlg[0].show();
    }

    // ================================================================== aggiornamenti

    private void checkUpdates(final boolean manual) {
        if (manual) toast(tr("Controllo aggiornamenti…", "Checking for updates…"));
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
                            else if (manual) toast(tr("Hai già l'ultima versione (", "You already have the latest version (")
                                    + Updater.currentName(MainActivity.this) + ")");
                        }
                    });
                } catch (final IOException e) {
                    if (manual) main.post(new Runnable() {
                        @Override
                        public void run() {
                            toast(tr("Impossibile controllare: ", "Couldn't check: ") + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void offerUpdate(final Updater.Release r) {
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.length() > 700) notes = notes.substring(0, 700) + "…";
        final String fnotes = notes;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Aggiornamento disponibile: ", "Update available: ") + r.tag)
                .setMessage(tr("Versione installata: ", "Installed version: ") + Updater.currentName(this)
                        + (fnotes.isEmpty() ? "" : tr("\n\nNovità:\n", "\n\nWhat's new:\n") + fnotes))
                .setPositiveButton(tr("Aggiorna", "Update"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (Updater.pending(MainActivity.this) != null) installDownloaded(r);
                        else runUpdate(r);
                    }
                })
                .setNegativeButton(tr("Più tardi", "Later"), null)
                .show();
    }

    /** Aggiornamento già scaricato in background: installa subito. */
    private void installDownloaded(final Updater.Release r) {
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Aggiornamento a ", "Updating to ") + r.tag)
                .setMessage(tr("Installazione in corso…", "Installing…"))
                .setNegativeButton(tr("Chiudi", "Close"), null)
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
                            ? tr("Installazione in corso…\nL'app si chiuderà e si riaprirà da sola.",
                            "Installing…\nThe app will close and reopen by itself.")
                            : tr("Conferma con «Installa» nella schermata di Android.\nAl termine riapri il Task Manager.",
                            "Confirm with «Install» on the Android screen.\nReopen Task Manager when done."));
                }
            });
            if (silent) main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    String log = Updater.readLog(MainActivity.this);
                    dlg.setMessage(tr("L'installazione non è riuscita.\n\n", "Installation failed.\n\n")
                            + (log == null || log.isEmpty() ? tr("Nessun dettaglio disponibile.", "No details available.") : log));
                }
            }, 40000);
        } catch (final IOException e) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    dlg.setMessage(tr("Aggiornamento non riuscito: ", "Update failed: ") + e.getMessage());
                }
            });
        }
    }

    private void runUpdate(final Updater.Release r) {
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Aggiornamento a ", "Updating to ") + r.tag)
                .setMessage(tr("Download in corso…", "Downloading…"))
                .setNegativeButton(tr("Chiudi", "Close"), null)
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
                                    dlg.setMessage(tr("Download in corso… ", "Downloading… ") + pct + "%");
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
                            dlg.setMessage(tr("Aggiornamento non riuscito: ", "Update failed: ") + e.getMessage());
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
            items[i] = labels.get(pkg) + (sys.contains(pkg) ? tr("   (sistema)", "   (system)") : "");
            checked[i] = prot.contains(pkg);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Escludi da «Chiudi tutto»", "Exclude from «Close all»"))
                .setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean on) {
                        checked[which] = on;
                    }
                })
                .setPositiveButton(tr("Salva", "Save"), new DialogInterface.OnClickListener() {
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
                        toast(tr("Esclusioni salvate", "Exclusions saved"));
                    }
                })
                .setNegativeButton(tr("Annulla", "Cancel"), null)
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
                .setTitle(tr("Imposta tasto al volante", "Set steering-wheel button"))
                .setMessage(tr("Preparazione…", "Preparing…"))
                .setNegativeButton(tr("Annulla", "Cancel"), new DialogInterface.OnClickListener() {
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
                wait.setMessage(tr("Nessun tasto rilevato.\n\nProva con un altro tasto: alcuni tasti del volante vengono gestiti direttamente dall'auto e non arrivano ad Android.",
                        "No button detected.\n\nTry another button: some steering-wheel buttons are handled directly by the car and never reach Android."));
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
                            wait.setMessage(tr("Per usare il tasto al volante attiva «Task Manager» in Impostazioni → Accessibilità, poi torna qui e riprova.",
                                    "To use the steering-wheel button, enable «Task Manager» in Settings → Accessibility, then come back and try again."));
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
                                wait.setMessage(tr("Tasto impostato: ", "Button set: ") + d + "\n\n"
                                        + (KeyHub.SRC_A11Y.equals(src)
                                        ? tr("Premendolo aprirai e chiuderai il Task Manager.",
                                        "Pressing it will open and close Task Manager.")
                                        : tr("Premendolo aprirai e chiuderai il Task Manager. Nota: questo tasto manterrà anche la sua funzione originale.",
                                        "Pressing it will open and close Task Manager. Note: this button also keeps its original function.")));
                                wait.getButton(DialogInterface.BUTTON_NEGATIVE).setText("OK");
                            }
                        });
                        KeyMonitorService.start(MainActivity.this);
                        wait.setMessage(tr("Premi ora sul volante il tasto che vuoi usare…\n(hai 20 secondi)",
                                "Now press the steering-wheel button you want to use…\n(you have 20 seconds)"));
                        main.postDelayed(timeout, 20000);
                    }
                });
            }
        });
    }
}
