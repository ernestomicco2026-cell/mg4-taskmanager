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
    private boolean adbMode;
    private boolean grantedViaAdb;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private TextView status;
    private LinearLayout list;
    private List<Tasks.Entry> current = new ArrayList<Tasks.Entry>();
    private boolean loading;

    private final AdbClient.AuthListener auth = new AdbClient.AuthListener() {
        @Override
        public void onWaitingForApproval() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    setStatus("Sullo schermo dell'auto conferma «Consentire il debug?» (spunta «Consenti sempre»)");
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

        header.addView(button("Aggiorna", BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        }), lp(-2, -2, 0, dp(12)));
        header.addView(button("Chiudi tutto", DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeAll();
            }
        }), lp(-2, -2, 0, dp(12)));
        header.addView(button("Impostazioni", BTN, new View.OnClickListener() {
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

        TextView hint = text("Tocca un'app per aprirla · tieni premuto per escluderla da «Chiudi tutto»", 14, MUTED, false);
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
        String st = apps + (apps == 1 ? " app aperta" : " app aperte");
        if (adbMode) st += " · " + (kb / 1024) + " MB · modalità completa";
        else st += " · modalità base";
        int hidden = current.size() - vis.size();
        if (hidden > 0) st += " · " + hidden + " di sistema nascoste";
        setStatus(st);
    }

    private void render() {
        list.removeAllViews();
        List<Tasks.Entry> vis = shown();
        updateStatus(vis);
        if (!adbMode) addSetupCard();
        if (vis.isEmpty()) {
            TextView t = text("Nessuna app aperta", 22, MUTED, false);
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
                TextView h = text("Servizi in background (chiudili solo se sai cosa fanno)", 16, MUTED, true);
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
        String tag = e.isProtected ? "  · esclusa" : (e.system && skipSystem() ? "  · sistema" : "");
        TextView name = text(e.label + tag, 20, TEXT, true);
        name.setSingleLine(true);
        texts.addView(name);
        StringBuilder sub = new StringBuilder();
        if (e.foreground) sub.append("In primo piano · ");
        if (e.system) sub.append("App di sistema · ");
        if (e.rssKb >= 0) sub.append(e.rssKb / 1024).append(" MB");
        else if (sub.length() == 0) sub.append("Aperta da questa accensione");
        if (e.processes > 1) sub.append(" · ").append(e.processes).append(" processi");
        if (sub.toString().endsWith(" · ")) sub.setLength(sub.length() - 3);
        sub.append("\n").append(e.pkg);
        TextView s = text(sub.toString(), 14, MUTED, false);
        texts.addView(s);
        c.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        c.addView(button("Chiudi", DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> one = new ArrayList<String>();
                one.add(e.pkg);
                kill(one, e.label + " chiusa");
            }
        }));

        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(e.pkg);
                if (i == null) {
                    toast("Questa app non ha una schermata da aprire");
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                    finish();
                } catch (Exception ex) {
                    toast("Impossibile aprire l'app");
                }
            }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean p = Adb.toggleProtected(MainActivity.this, e.pkg);
                toast(p ? e.label + " esclusa: «Chiudi tutto» la salterà" : e.label + " non è più esclusa");
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
        boolean a11y = KeyService.running;
        boolean usage = Tasks.hasUsageAccess(this);
        if (a11y && usage) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(CARD));
        box.setPadding(dp(24), dp(20), dp(24), dp(20));
        box.addView(text("Completa la configurazione", 22, TEXT, true));
        StringBuilder b = new StringBuilder();
        if (!a11y) b.append("• Accessibilità: attiva «Task Manager» per vedere le app aperte e chiuderle davvero ")
                .append("(l'app preme da sola «Forza interruzione»).\n");
        if (!usage) b.append("• Accesso ai dati di utilizzo: consenti a «Task Manager» di vedere quali app hai usato.\n");
        b.append("\nSenza questi permessi può chiudere solo le app in background.");
        TextView body = text(b.toString(), 16, MUTED, false);
        body.setPadding(0, dp(10), 0, dp(16));
        box.addView(body);
        LinearLayout btns = new LinearLayout(this);
        if (!a11y) btns.addView(button("Attiva accessibilità", ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!KeyService.openAccessibilitySettings(MainActivity.this))
                    toast("Cerca «Accessibilità» nelle impostazioni dell'auto e attiva «Task Manager»");
            }
        }));
        if (!usage) btns.addView(button("Consenti utilizzo", a11y ? ACCENT : BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!KeyService.openSettings(MainActivity.this, android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    toast("Cerca «Accesso dati di utilizzo» nelle impostazioni dell'auto");
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
        setStatus("Caricamento…");
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
                        if (adbMode) grantViaAdbOnce();
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
            toast("Niente da chiudere");
            return;
        }
        kill(pkgs, pkgs.size() == 1 ? "1 app chiusa" : pkgs.size() + " app chiuse");
    }

    private void kill(final List<String> pkgs, final String done) {
        if (KeyService.isBusy()) {
            toast("Chiusura già in corso…");
            return;
        }
        setStatus("Chiusura…");
        bg.execute(new Runnable() {
            @Override
            public void run() {
                final boolean viaAdb = Tasks.killAdb(MainActivity.this, pkgs, auth);
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
                            toast("Chiuse solo in background: attiva l'accessibilità per chiuderle del tutto");
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

        v.addView(text("Modalità", 17, TEXT, true));
        v.addView(text(adbMode
                ? "Completa: ADB dell'auto raggiungibile, chiusura istantanea e aggiornamenti silenziosi."
                : "Base: ADB non raggiungibile. Le app si chiudono tramite accessibilità.", 15, MUTED, false));
        LinearLayout.LayoutParams a11yP = new LinearLayout.LayoutParams(-2, -2);
        a11yP.topMargin = dp(8);
        v.addView(button(KeyService.running ? "Accessibilità attiva" : "Attiva accessibilità", BTN,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        KeyService.openAccessibilitySettings(MainActivity.this);
                    }
                }), a11yP);
        TextView adbT = text("Indirizzo ADB (avanzato)", 15, MUTED, false);
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
        showBg.setText("Mostra anche i servizi in background (avanzato)");
        showBg.setChecked(p.getBoolean("show_bg", false));
        v.addView(showBg);

        TextView ct = text("«Chiudi tutto»", 17, TEXT, true);
        ct.setPadding(0, dp(18), 0, 0);
        v.addView(ct);
        final CheckBox skipSys = new CheckBox(this);
        skipSys.setText("Escludi e nascondi le app di sistema (radio, navigatore, app dell'auto…)");
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
        TextView exInfo = text(nEx == 0 ? "Nessuna app esclusa manualmente"
                : (nEx == 1 ? "1 app esclusa manualmente" : nEx + " app escluse manualmente"), 15, MUTED, false);
        v.addView(exInfo);
        LinearLayout.LayoutParams exp = new LinearLayout.LayoutParams(-2, -2);
        exp.topMargin = dp(8);
        v.addView(button("Scegli le app da escludere…", BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                chooseExcluded();
            }
        }), exp);

        TextView ut = text("Aggiornamenti", 17, TEXT, true);
        ut.setPadding(0, dp(18), 0, 0);
        v.addView(ut);
        v.addView(text("Versione installata: " + Updater.currentName(this), 15, MUTED, false));
        final CheckBox autoUp = new CheckBox(this);
        autoUp.setText("Controlla automaticamente se ci sono aggiornamenti");
        autoUp.setChecked(Updater.autoEnabled(this));
        autoUp.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Adb.prefs(MainActivity.this).edit().putBoolean("auto_update", on).apply();
            }
        });
        v.addView(autoUp);
        final CheckBox autoInst = new CheckBox(this);
        autoInst.setText("Installa gli aggiornamenti da solo (senza ADB serve un tocco su «Installa»)");
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
        v.addView(button("Controlla ora", BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                checkUpdates(true);
            }
        }), upp);

        TextView kt = text("Tasto al volante", 17, TEXT, true);
        kt.setPadding(0, dp(18), 0, 0);
        v.addView(kt);
        String desc = p.getString("key_desc", null);
        v.addView(text(desc == null ? "Nessun tasto impostato" : "Impostato: " + desc
                + (KeyHub.SRC_A11Y.equals(p.getString("key_src", null)) ? "  (via accessibilità)" : "  (via getevent)"), 15, MUTED, false));

        final CheckBox longPress = new CheckBox(this);
        longPress.setText("Solo con pressione lunga (la pressione breve mantiene la funzione originale)");
        longPress.setChecked(p.getBoolean("key_long", false));
        v.addView(longPress);

        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout kb = new LinearLayout(this);
        kb.addView(button("Imposta tasto…", ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                saveSettings(host, port, showBg, longPress);
                dlg[0].dismiss();
                learnKey();
            }
        }));
        kb.addView(button("Rimuovi", BTN, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                KeyHub.clear(MainActivity.this);
                KeyMonitorService.stop(MainActivity.this);
                toast("Tasto rimosso");
                dlg[0].dismiss();
            }
        }), lp(-2, -2, 0, dp(12)));
        LinearLayout.LayoutParams kbp = new LinearLayout.LayoutParams(-2, -2);
        kbp.topMargin = dp(8);
        v.addView(kb, kbp);

        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        dlg[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Impostazioni")
                .setView(sv)
                .setPositiveButton("Salva", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        saveSettings(host, port, showBg, longPress);
                        Adb.reset();
                        refresh();
                    }
                })
                .setNegativeButton("Annulla", null)
                .create();
        dlg[0].show();
    }

    // ================================================================== aggiornamenti

    private void checkUpdates(final boolean manual) {
        if (manual) toast("Controllo aggiornamenti…");
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
                            else if (manual) toast("Hai già l'ultima versione (" + Updater.currentName(MainActivity.this) + ")");
                        }
                    });
                } catch (final IOException e) {
                    if (manual) main.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Impossibile controllare: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void offerUpdate(final Updater.Release r) {
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.length() > 700) notes = notes.substring(0, 700) + "…";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Aggiornamento disponibile: " + r.tag)
                .setMessage("Versione installata: " + Updater.currentName(this)
                        + (notes.isEmpty() ? "" : "\n\nNovità:\n" + notes))
                .setPositiveButton("Aggiorna", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (Updater.pending(MainActivity.this) != null) installDownloaded(r);
                        else runUpdate(r);
                    }
                })
                .setNegativeButton("Più tardi", null)
                .show();
    }

    /** Aggiornamento già scaricato in background: installa subito. */
    private void installDownloaded(final Updater.Release r) {
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Aggiornamento a " + r.tag)
                .setMessage("Installazione in corso…")
                .setNegativeButton("Chiudi", null)
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
                            ? "Installazione in corso…\nL'app si chiuderà e si riaprirà da sola."
                            : "Conferma con «Installa» nella schermata di Android.\nAl termine riapri il Task Manager.");
                }
            });
            if (silent) main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    String log = Updater.readLog(MainActivity.this);
                    dlg.setMessage("L'installazione non è riuscita.\n\n"
                            + (log == null || log.isEmpty() ? "Nessun dettaglio disponibile." : log));
                }
            }, 40000);
        } catch (final IOException e) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    dlg.setMessage("Aggiornamento non riuscito: " + e.getMessage());
                }
            });
        }
    }

    private void runUpdate(final Updater.Release r) {
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Aggiornamento a " + r.tag)
                .setMessage("Download in corso…")
                .setNegativeButton("Chiudi", null)
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
                                    dlg.setMessage("Download in corso… " + pct + "%");
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
                            dlg.setMessage("Aggiornamento non riuscito: " + e.getMessage());
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
            items[i] = labels.get(pkg) + (sys.contains(pkg) ? "   (sistema)" : "");
            checked[i] = prot.contains(pkg);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Escludi da «Chiudi tutto»")
                .setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean on) {
                        checked[which] = on;
                    }
                })
                .setPositiveButton("Salva", new DialogInterface.OnClickListener() {
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
                        toast("Esclusioni salvate");
                    }
                })
                .setNegativeButton("Annulla", null)
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
                .setTitle("Imposta tasto al volante")
                .setMessage("Preparazione…")
                .setNegativeButton("Annulla", new DialogInterface.OnClickListener() {
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
                wait.setMessage("Nessun tasto rilevato.\n\nProva con un altro tasto: alcuni tasti del volante "
                        + "vengono gestiti direttamente dall'auto e non arrivano ad Android.");
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
                            wait.setMessage("Per usare il tasto al volante attiva «Task Manager» in "
                                    + "Impostazioni → Accessibilità, poi torna qui e riprova.");
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
                                wait.setMessage("Tasto impostato: " + d + "\n\n"
                                        + (KeyHub.SRC_A11Y.equals(src)
                                        ? "Premendolo aprirai e chiuderai il Task Manager."
                                        : "Premendolo aprirai e chiuderai il Task Manager. Nota: questo tasto "
                                        + "manterrà anche la sua funzione originale."));
                                wait.getButton(DialogInterface.BUTTON_NEGATIVE).setText("OK");
                            }
                        });
                        KeyMonitorService.start(MainActivity.this);
                        wait.setMessage("Premi ora sul volante il tasto che vuoi usare…\n(hai 20 secondi)");
                        main.postDelayed(timeout, 20000);
                    }
                });
            }
        });
    }
}
