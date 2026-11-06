package com.brawldns.switcher;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONObject;

import rikka.shizuku.Shizuku;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 11;
    private static final int REQ_IMPORT = 12;
    private static final int REQ_NOTIF = 13;
    /** Пользователь отказался от автонастройки: до перезапуска приложения не пристаём. */
    private static boolean setupSkipped = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Config cfg;

    private LinearLayout root;
    private LinearLayout rulesBox;
    private LinearLayout netsBox;
    private LinearLayout accessBox;
    private LinearLayout logBox;
    private DialView dial;
    private TextView statusLine;
    private TextView toggleBtn;
    private TextView pauseBtn;
    private TextView manualBtn;
    private EditText primaryField;
    private SharedPreferences.OnSharedPreferenceChangeListener stateListener;
    private Dialog setupDialog;
    private boolean autoGranting;
    private boolean waitingPerm;

    private final Shizuku.OnBinderReceivedListener binderListener = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    runSetup(false);
                }
            });
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, final int grantResult) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            waitingPerm = false;
                            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                autoGrant();
                            } else {
                                setupSkipped = true;
                                toast("Доступ к Shizuku не разрешён");
                                renderAccess();
                            }
                        }
                    });
                }
            };

    private interface Picked {
        void on(String pkg, String label);
    }

    // ------------------------------------------------------------------ жизненный цикл

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = Config.load(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int ph = Ui.dp(this, 22);
        root.setPadding(ph, Ui.dp(this, 20), ph, Ui.dp(this, 48));
        sv.addView(root);
        setContentView(sv);

        buildHeader();
        buildDial();
        buildRules();
        buildConditions();
        buildDns();
        buildAccess();
        buildLog();
        buildData();
        buildUpdates();

        stateListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshDial();
                        renderLog();
                    }
                });
            }
        };

        Shizuku.addBinderReceivedListener(binderListener);
        Shizuku.addRequestPermissionResultListener(permListener);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener);
        Shizuku.removeRequestPermissionResultListener(permListener);
        dismissSetup();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        State.prefs(this).registerOnSharedPreferenceChangeListener(stateListener);
        renderAccess();
        renderLog();
        refreshDial();
        if (!setupSkipped) runSetup(false);
    }

    @Override
    protected void onPause() {
        State.prefs(this).unregisterOnSharedPreferenceChangeListener(stateListener);
        super.onPause();
    }

    // ------------------------------------------------------------------ помощники

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void put(LinearLayout parent, View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        lp.topMargin = Ui.dp(this, topDp);
        parent.addView(v, lp);
    }

    private void watch(EditText e, final Consumer<String> cb) {
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                cb.accept(s.toString());
            }
        });
    }

    private static String hhmm(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private String versionLabel() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }

    // ------------------------------------------------------------------ верх

    private void buildHeader() {
        put(root, Ui.label(this, "Brawl DNS  ·  " + versionLabel()), 0);
        put(root, Ui.title(this, "DNS по требованию", 32), 10);
        put(root, Ui.dim(this, "Частный DNS включается только на время выбранных приложений "
                + "и возвращается, когда вы из них выходите."), 10);
    }

    private void buildDial() {
        dial = new DialView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 26);
        root.addView(dial, lp);

        statusLine = Ui.body(this, "");
        statusLine.setGravity(Gravity.CENTER);
        put(root, statusLine, 6);

        LinearLayout row = Ui.row(this);
        toggleBtn = Ui.button(this, "Запустить", 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MonitorService.toggle(MainActivity.this);
                refreshDial();
            }
        });
        pauseBtn = Ui.button(this, "Пауза 1 ч", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!State.enabled(MainActivity.this)) return;
                long now = System.currentTimeMillis();
                boolean paused = State.pausedUntil(MainActivity.this) > now;
                State.setPausedUntil(MainActivity.this, paused ? 0L : now + 3600_000L);
                EventLog.add(MainActivity.this, paused ? "Пауза снята" : "Пауза на 1 час");
                refreshDial();
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        l1.rightMargin = Ui.dp(this, 5);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        l2.leftMargin = Ui.dp(this, 5);
        row.addView(toggleBtn, l1);
        row.addView(pauseBtn, l2);
        put(root, row, 18);

        manualBtn = Ui.button(this, "Включить DNS вручную", 2, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DnsHelper.toggleManual(MainActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        refreshDial();
                        renderLog();
                    }
                });
            }
        });
        put(root, manualBtn, 4);
    }

    private void refreshDial() {
        boolean on = State.enabled(this);
        boolean manual = State.manual(this);
        int kind = on ? State.kind(this) : State.OFF;
        String txt = on ? State.text(this) : "Служба остановлена";
        if (manual) {
            kind = State.ACTIVE;
            txt = "Вручную: DNS " + DnsHelper.currentHost(this);
        }
        String[] names = {"Выкл", "Ожидание", "Активно", "Пауза", "Внимание"};
        dial.set(kind, names[kind], on || manual ? DnsHelper.currentMode(this).equals("hostname")
                ? DnsHelper.currentHost(this) : "частный DNS не задан" : "");
        statusLine.setText(txt);
        toggleBtn.setText(on ? "Остановить" : "Запустить");
        boolean paused = State.pausedUntil(this) > System.currentTimeMillis();
        pauseBtn.setText(paused ? "Продолжить" : "Пауза 1 ч");
        pauseBtn.setAlpha(on ? 1f : 0.4f);
        manualBtn.setText(manual ? "Выключить DNS вручную" : "Включить DNS вручную");
    }

    // ------------------------------------------------------------------ правила

    private void buildRules() {
        put(root, Ui.section(this, "Правила"), 0);
        rulesBox = new LinearLayout(this);
        rulesBox.setOrientation(LinearLayout.VERTICAL);
        put(root, rulesBox, 0);
        put(root, Ui.button(this, "Добавить приложение", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editRule(null);
            }
        }), 4);
        renderRules();
    }

    private void renderRules() {
        rulesBox.removeAllViews();
        if (cfg.rules.isEmpty()) {
            put(rulesBox, Ui.dim(this, "Правил нет. Добавьте приложение, для которого нужен DNS."), 0);
        }
        for (final Config.Rule r : cfg.rules) {
            LinearLayout card = Ui.card(this);
            LinearLayout top = Ui.row(this);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(appIcon(r.pkg));
            top.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            names.addView(Ui.title(this, r.name.isEmpty() ? r.pkg : r.name, 19));
            TextView pk = Ui.mono(this, r.pkg);
            pk.setPadding(0, Ui.dp(this, 4), 0, 0);
            names.addView(pk);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
            nl.leftMargin = Ui.dp(this, 14);
            nl.rightMargin = Ui.dp(this, 10);
            top.addView(names, nl);
            top.addView(Ui.sw(this, r.on, new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                    r.on = v;
                    cfg.save(MainActivity.this);
                }
            }));
            card.addView(top);

            TextView d = Ui.dim(this, "DNS: " + (r.dns.trim().isEmpty()
                    ? cfg.primary + "  (основной)" : r.dns));
            d.setPadding(0, Ui.dp(this, 12), 0, 0);
            card.addView(d);

            LinearLayout act = Ui.row(this);
            act.addView(Ui.button(this, "Играть", 0, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    play(r);
                }
            }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            act.addView(Ui.hgap(this, 8));
            act.addView(Ui.button(this, "Изменить", 1, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editRule(r);
                }
            }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            put(card, act, 14);
            put(rulesBox, card, 10);
        }
    }

    /** Подготовить DNS и сразу открыть игру. */
    private void play(final Config.Rule r) {
        final Intent launch = getPackageManager().getLaunchIntentForPackage(r.pkg);
        if (launch == null) {
            toast("Приложение не найдено");
            return;
        }
        if (!DnsHelper.hasSecure(this)) {
            toast("Нет разрешения WRITE_SECURE_SETTINGS, открываю без DNS");
            startActivity(launch);
            return;
        }
        if (!State.enabled(this)) MonitorService.toggle(this);
        State.setGraceUntil(this, System.currentTimeMillis() + 25_000L);
        toast("Готовлю DNS…");
        final Context app = getApplicationContext();
        final Config c = cfg;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String host = DnsProbe.pickReachable(app, c.candidates(r));
                if (host != null && DnsHelper.apply(app, host)) {
                    EventLog.add(app, "Включён DNS " + host + " перед запуском " + r.name);
                } else {
                    EventLog.add(app, "Запуск " + r.name + " без DNS: серверы не отвечают");
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(launch);
                    }
                });
            }
        }).start();
    }

    private void editRule(final Config.Rule existing) {
        final boolean isNew = existing == null;
        final Config.Rule r = isNew ? new Config.Rule() : existing;

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.title(this, isNew ? "Новое правило" : "Правило", 24));

        put(v, Ui.label(this, "Приложение"), 18);
        final EditText nameF = Ui.field(this, r.name, "Название", false);
        put(v, nameF, 8);
        final EditText pkgF = Ui.field(this, r.pkg, "com.пример.пакет", true);
        put(v, pkgF, 8);
        put(v, Ui.button(this, "Выбрать из установленных", 1, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                pickApp(new Picked() {
                    @Override
                    public void on(String pkg, String label) {
                        pkgF.setText(pkg);
                        nameF.setText(label);
                    }
                });
            }
        }), 8);

        put(v, Ui.label(this, "DNS только для этого приложения"), 20);
        final EditText dnsF = Ui.field(this, r.dns, "пусто = основной DNS", true);
        put(v, dnsF, 8);

        final Dialog d = Ui.dialog(this, v, true);
        LinearLayout acts = Ui.row(this);
        if (!isNew) {
            acts.addView(Ui.button(this, "Удалить", 2, new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    cfg.rules.remove(existing);
                    cfg.save(MainActivity.this);
                    renderRules();
                    d.dismiss();
                }
            }));
        }
        View spacer = new View(this);
        acts.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        acts.addView(Ui.button(this, "Отмена", 2, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                d.dismiss();
            }
        }));
        acts.addView(Ui.button(this, "Сохранить", 0, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                String pkg = pkgF.getText().toString().trim();
                if (pkg.isEmpty()) {
                    toast("Укажите пакет приложения");
                    return;
                }
                r.pkg = pkg;
                r.name = nameF.getText().toString().trim();
                if (r.name.isEmpty()) r.name = pkg;
                r.dns = dnsF.getText().toString().trim();
                if (isNew) cfg.rules.add(r);
                cfg.save(MainActivity.this);
                renderRules();
                d.dismiss();
            }
        }));
        put(v, acts, 22);
        Ui.show(d, this);
    }

    private void pickApp(final Picked cb) {
        final PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
        final List<String[]> all = new ArrayList<>();
        final Map<String, Drawable> icons = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : ris) {
            String p = ri.activityInfo.packageName;
            if (!seen.add(p)) continue;
            all.add(new String[]{ri.loadLabel(pm).toString(), p});
            icons.put(p, ri.loadIcon(pm));
        }
        Collections.sort(all, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        final List<String[]> shown = new ArrayList<>(all);

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.title(this, "Установленные приложения", 22));
        EditText search = Ui.field(this, "", "Поиск", false);
        put(v, search, 12);
        final ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        final Dialog d = Ui.dialog(this, v, false);

        final BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int i) {
                return shown.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View cv, ViewGroup parent) {
                String[] it = shown.get(i);
                LinearLayout row = Ui.row(MainActivity.this);
                int pv = Ui.dp(MainActivity.this, 9);
                row.setPadding(0, pv, 0, pv);
                ImageView ic = new ImageView(MainActivity.this);
                ic.setImageDrawable(icons.get(it[1]));
                row.addView(ic, new LinearLayout.LayoutParams(
                        Ui.dp(MainActivity.this, 34), Ui.dp(MainActivity.this, 34)));
                LinearLayout t = new LinearLayout(MainActivity.this);
                t.setOrientation(LinearLayout.VERTICAL);
                t.addView(Ui.body(MainActivity.this, it[0]));
                t.addView(Ui.mono(MainActivity.this, it[1]));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                tl.leftMargin = Ui.dp(MainActivity.this, 12);
                row.addView(t, tl);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, view, pos, id) -> {
            String[] it = shown.get(pos);
            cb.on(it[1], it[0]);
            d.dismiss();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String f = s.toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                for (String[] a : all) {
                    if (f.isEmpty() || a[0].toLowerCase(Locale.ROOT).contains(f)
                            || a[1].toLowerCase(Locale.ROOT).contains(f)) {
                        shown.add(a);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });
        v.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 380)));
        Ui.show(d, this);
    }

    // ------------------------------------------------------------------ условия

    private void buildConditions() {
        put(root, Ui.section(this, "Условия"), 0);
        LinearLayout card = Ui.card(this);

        card.addView(Ui.switchRow(this, "Только по Wi-Fi",
                "На мобильной сети DNS не включается",
                Ui.sw(this, cfg.wifiOnly, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                        cfg.wifiOnly = v;
                        cfg.save(MainActivity.this);
                    }
                })));
        card.addView(Ui.line(this));

        card.addView(Ui.switchRow(this, "Только в сохранённых сетях",
                "Сеть определяется по адресу шлюза роутера, геолокация не нужна",
                Ui.sw(this, cfg.netOnly, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                        cfg.netOnly = v;
                        cfg.save(MainActivity.this);
                    }
                })));
        netsBox = new LinearLayout(this);
        netsBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(netsBox);
        put(card, Ui.button(this, "Запомнить текущую сеть", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String gw = DnsHelper.wifiGateway(MainActivity.this);
                if (gw == null) {
                    toast("Нет подключения к Wi-Fi");
                    return;
                }
                if (cfg.knownNet(gw)) {
                    toast("Эта сеть уже в списке");
                    return;
                }
                Config.Net n = new Config.Net();
                n.gw = gw;
                n.label = "Сеть " + gw;
                cfg.nets.add(n);
                cfg.save(MainActivity.this);
                renderNets();
            }
        }), 6);
        renderNets();

        put(card, Ui.line(this), 14);

        final TextView from = Ui.button(this, "С " + hhmm(cfg.schedFrom), 1, null);
        final TextView to = Ui.button(this, "До " + hhmm(cfg.schedTo), 1, null);
        from.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(cfg.schedFrom, new Consumer<Integer>() {
                    @Override
                    public void accept(Integer m) {
                        cfg.schedFrom = m;
                        cfg.save(MainActivity.this);
                        from.setText("С " + hhmm(m));
                    }
                });
            }
        });
        to.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(cfg.schedTo, new Consumer<Integer>() {
                    @Override
                    public void accept(Integer m) {
                        cfg.schedTo = m;
                        cfg.save(MainActivity.this);
                        to.setText("До " + hhmm(m));
                    }
                });
            }
        });
        card.addView(Ui.switchRow(this, "По расписанию",
                "Включать DNS только в выбранные часы",
                Ui.sw(this, cfg.schedOn, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                        cfg.schedOn = v;
                        cfg.save(MainActivity.this);
                    }
                })));
        LinearLayout times = Ui.row(this);
        times.addView(from, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        times.addView(Ui.hgap(this, 8));
        times.addView(to, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        put(card, times, 4);

        put(card, Ui.line(this), 18);

        final TextView delayLabel = Ui.body(this, "Задержка выключения: " + cfg.offDelay + " с");
        put(card, delayLabel, 16);
        put(card, Ui.dim(this, "Сколько ждать после выхода из игры, прежде чем вернуть прежний DNS"), 4);
        SeekBar sb = new SeekBar(this);
        sb.setMax(60);
        sb.setProgress(cfg.offDelay);
        sb.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE_HI));
        sb.setThumbTintList(ColorStateList.valueOf(Ui.TEXT));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                cfg.offDelay = p;
                delayLabel.setText("Задержка выключения: " + p + " с");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                cfg.save(MainActivity.this);
            }
        });
        put(card, sb, 8);
        put(root, card, 0);
    }

    private void renderNets() {
        netsBox.removeAllViews();
        for (final Config.Net n : cfg.nets) {
            LinearLayout row = Ui.row(this);
            row.addView(Ui.mono(this, n.label), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            row.addView(Ui.button(this, "Убрать", 2, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cfg.nets.remove(n);
                    cfg.save(MainActivity.this);
                    renderNets();
                }
            }));
            put(netsBox, row, 2);
        }
    }

    private void pickTime(int minutes, final Consumer<Integer> cb) {
        new TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int h, int m) {
                        cb.accept(h * 60 + m);
                    }
                }, minutes / 60, minutes % 60, true).show();
    }

    // ------------------------------------------------------------------ DNS

    private void buildDns() {
        put(root, Ui.section(this, "DNS"), 0);
        LinearLayout card = Ui.card(this);

        card.addView(Ui.label(this, "Основной (DNS over TLS)"));
        primaryField = Ui.field(this, cfg.primary, Config.DEF_PRIMARY, true);
        put(card, primaryField, 8);
        watch(primaryField, s -> {
            cfg.primary = s.trim();
            cfg.save(this);
        });

        put(card, Ui.label(this, "Запасные, по одному в строке"), 18);
        final EditText fb = Ui.field(this, String.join("\n", cfg.fallbacks), "dns.example.com", true);
        fb.setSingleLine(false);
        fb.setMinLines(2);
        fb.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        put(card, fb, 8);
        watch(fb, s -> {
            cfg.fallbacks.clear();
            for (String line : s.split("\n")) {
                String h = line.trim();
                if (!h.isEmpty()) cfg.fallbacks.add(h);
            }
            cfg.save(this);
        });
        put(card, Ui.dim(this, "Перед включением приложение проверяет, что сервер отвечает. "
                + "Если нет, берёт следующий из списка. Если не отвечает ни один, DNS остаётся прежним."), 8);

        put(card, Ui.label(this, "Проверочный домен"), 18);
        EditText dom = Ui.field(this, cfg.checkDomain, Config.DEF_DOMAIN, true);
        put(card, dom, 8);
        watch(dom, s -> {
            cfg.checkDomain = s.trim();
            cfg.save(this);
        });

        LinearLayout acts = Ui.row(this);
        acts.addView(Ui.button(this, "Скорость", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speedTest();
            }
        }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        acts.addView(Ui.hgap(this, 8));
        acts.addView(Ui.button(this, "Проверить имя", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkName();
            }
        }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        put(card, acts, 18);
        put(root, card, 0);
    }

    private void speedTest() {
        final List<String> hosts = cfg.candidates(null);
        if (hosts.isEmpty()) {
            toast("Список DNS пуст");
            return;
        }
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.title(this, "Скорость DNS", 24));
        put(v, Ui.dim(this, "Время TLS-соединения с портом 853, лучший из двух замеров. "
                + "Нажмите на строку, чтобы сделать сервер основным."), 8);

        final TextView[] res = new TextView[hosts.size()];
        final long[] ms = new long[hosts.size()];
        final AtomicInteger left = new AtomicInteger(hosts.size());
        for (int i = 0; i < hosts.size(); i++) {
            final int idx = i;
            final String h = hosts.get(i);
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            TextView name = Ui.text(this, h, 13, Ui.TEXT, Ui.MONO);
            row.addView(name, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            res[i] = Ui.text(this, "…", 13, Ui.DIM, Ui.SANS);
            row.addView(res[i]);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if (ms[idx] <= 0) return;
                    cfg.primary = h;
                    cfg.save(MainActivity.this);
                    primaryField.setText(h);
                    toast("Основной DNS: " + h);
                }
            });
            put(v, Ui.line(this), i == 0 ? 14 : 0);
            put(v, row, 0);
        }
        final Dialog d = Ui.dialog(this, v, true);
        put(v, Ui.button(this, "Закрыть", 1, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                d.dismiss();
            }
        }), 14);
        Ui.show(d, this);

        for (int i = 0; i < hosts.size(); i++) {
            final int idx = i;
            final String h = hosts.get(i);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final long t = DnsProbe.best(h, 3000);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            ms[idx] = t;
                            res[idx].setText(t < 0 ? "нет ответа" : t + " мс");
                            res[idx].setTextColor(t < 0 ? Ui.WARN : Ui.TEXT);
                            if (left.decrementAndGet() == 0) {
                                int best = -1;
                                for (int k = 0; k < ms.length; k++) {
                                    if (ms[k] > 0 && (best < 0 || ms[k] < ms[best])) best = k;
                                }
                                if (best >= 0 && ms.length > 1) {
                                    res[best].setText(ms[best] + " мс · быстрее всех");
                                    res[best].setTextColor(Ui.ACCENT);
                                }
                            }
                        }
                    });
                }
            }).start();
        }
    }

    private void checkName() {
        final String domain = cfg.checkDomain.trim();
        if (domain.isEmpty()) {
            toast("Укажите проверочный домен");
            return;
        }
        toast("Проверяю…");
        final String mode = DnsHelper.currentMode(this);
        final String host = DnsHelper.currentHost(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out;
                try {
                    long t0 = System.nanoTime();
                    InetAddress[] a = InetAddress.getAllByName(domain);
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    StringBuilder sb = new StringBuilder();
                    sb.append(domain).append(" разрешается за ").append(ms).append(" мс:\n");
                    for (InetAddress x : a) sb.append("  ").append(x.getHostAddress()).append('\n');
                    out = sb.toString();
                } catch (Exception e) {
                    out = "Имя " + domain + " не разрешилось (" + e.getClass().getSimpleName() + ").";
                }
                out += "\nЧастный DNS сейчас: "
                        + ("hostname".equals(mode) ? host : "не задан (" + mode + ")")
                        + "\n\nЕсли DNS игры ещё не включён, результат относится к обычному DNS сети.";
                final String text = out;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Ui.message(MainActivity.this, "Проверка имени", text);
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ доступ

    // ------------------------------------------------------------------ автонастройка через Shizuku

    private boolean needsSetup() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return !DnsHelper.hasSecure(this)
                || !DnsHelper.hasUsageAccess(this)
                || !nm.areNotificationsEnabled()
                || !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void dismissSetup() {
        if (setupDialog != null) {
            try {
                setupDialog.dismiss();
            } catch (Exception ignored) {
            }
            setupDialog = null;
        }
    }

    private void openShizuku() {
        Intent i = getPackageManager().getLaunchIntentForPackage(ShizukuHelper.PKG);
        try {
            if (i != null) startActivity(i);
            else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuHelper.SITE)));
        } catch (Exception e) {
            toast("Не удалось открыть");
        }
    }

    private void showSetup(String title, String body, String btn, final Runnable action) {
        dismissSetup();
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.title(this, title, 22));
        v.addView(Ui.gap(this, 12));
        v.addView(Ui.body(this, body));
        v.addView(Ui.gap(this, 18));
        final Dialog d = Ui.dialog(this, v, true);
        v.addView(Ui.button(this, btn, 0, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                d.dismiss();
                action.run();
            }
        }));
        v.addView(Ui.gap(this, 8));
        v.addView(Ui.button(this, "Позже", 2, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                setupSkipped = true;
                d.dismiss();
            }
        }));
        d.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(android.content.DialogInterface di) {
                setupSkipped = true;
            }
        });
        setupDialog = d;
        Ui.show(d, this);
    }

    /** Ведёт по шагам: установить Shizuku, запустить, разрешить доступ, выдать права. */
    private void runSetup(boolean manual) {
        if (manual) {
            setupSkipped = false;
            waitingPerm = false;
        }
        if (autoGranting || waitingPerm || isFinishing()) return;
        dismissSetup();
        if (!needsSetup()) return;

        if (!ShizukuHelper.installed(this)) {
            showSetup("Нужен Shizuku",
                    "Чтобы приложение само выдало себе все разрешения без компьютера, "
                            + "установите Shizuku и вернитесь сюда.",
                    "Скачать Shizuku", new Runnable() {
                        @Override
                        public void run() {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuHelper.SITE)));
                            } catch (Exception e) {
                                toast("Не удалось открыть ссылку");
                            }
                        }
                    });
            return;
        }
        if (!ShizukuHelper.running()) {
            String how = Build.VERSION.SDK_INT >= 30
                    ? "В Shizuku выберите запуск через «Беспроводную отладку» и следуйте подсказкам, "
                    + "компьютер не нужен."
                    : "На Android 10 Shizuku можно запустить только с компьютера (один раз) или через root.";
            showSetup("Включите Shizuku",
                    "Shizuku сейчас не запущен. " + how + " Потом вернитесь в это приложение.",
                    "Открыть Shizuku", new Runnable() {
                        @Override
                        public void run() {
                            openShizuku();
                        }
                    });
            return;
        }
        if (!ShizukuHelper.permitted()) {
            if (ShizukuHelper.canAsk()) {
                waitingPerm = true;
                ShizukuHelper.request();
            } else {
                showSetup("Разрешите доступ в Shizuku",
                        "Откройте Shizuku, найдите в списке приложений Brawl DNS и разрешите доступ.",
                        "Открыть Shizuku", new Runnable() {
                            @Override
                            public void run() {
                                openShizuku();
                            }
                        });
            }
            return;
        }
        autoGrant();
    }

    private void autoGrant() {
        if (autoGranting) return;
        autoGranting = true;
        setupSkipped = true; // одна попытка за запуск, чтобы не зациклиться
        toast("Выдаю разрешения…");
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                ShizukuHelper.grantAll(app);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        autoGranting = false;
                        renderAccess();
                        refreshDial();
                        renderLog();
                        toast(needsSetup() ? "Выдано не всё, см. журнал" : "Все разрешения выданы");
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ доступ

    private void buildAccess() {
        put(root, Ui.section(this, "Доступ"), 0);
        accessBox = Ui.card(this);
        put(root, accessBox, 0);
    }

    private void accessRow(String title, String sub, boolean ok, String btn, View.OnClickListener l) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.body(this, title));
        TextView s = Ui.text(this, sub, 12.5f, ok ? Ui.ACCENT : Ui.WARN, Ui.SANS);
        s.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(s);
        row.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        if (!ok && btn != null) row.addView(Ui.button(this, btn, 1, l));
        accessBox.addView(row);
    }

    private void renderAccess() {
        accessBox.removeAllViews();
        if (needsSetup()) {
            accessRow("Автонастройка", "Выдать все разрешения одним нажатием через Shizuku", false,
                    "Настроить", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            runSetup(true);
                        }
                    });
            accessBox.addView(Ui.line(this));
        }
        accessRow("Статистика использования", DnsHelper.hasUsageAccess(this) ? "Есть" : "Не выдан",
                DnsHelper.hasUsageAccess(this), "Открыть", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    }
                });
        accessBox.addView(Ui.line(this));
        accessRow("Системные настройки", DnsHelper.hasSecure(this)
                        ? "Есть" : "Не выдано. Нажмите «Настроить» выше (нужен Shizuku)",
                DnsHelper.hasSecure(this), "Настроить", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        runSetup(true);
                    }
                });
        accessBox.addView(Ui.line(this));
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean notif = nm.areNotificationsEnabled();
        accessRow("Уведомления", notif ? "Есть" : "Выключены", notif, "Разрешить", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            }
        });
        accessBox.addView(Ui.line(this));
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean bat = pm.isIgnoringBatteryOptimizations(getPackageName());
        accessRow("Работа в фоне", bat ? "Без ограничений" : "Система может останавливать службу. "
                        + "На Xiaomi включите ещё автозапуск в настройках приложения",
                bat, "Открыть", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    }
                });
    }

    // ------------------------------------------------------------------ журнал

    private void buildLog() {
        put(root, Ui.section(this, "Журнал"), 0);
        LinearLayout card = Ui.card(this);
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(logBox);
        LinearLayout acts = Ui.row(this);
        acts.addView(Ui.button(this, "Весь журнал", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> all = EventLog.tail(MainActivity.this, 200);
                Collections.reverse(all);
                Ui.message(MainActivity.this, "Журнал",
                        all.isEmpty() ? "Пока пусто." : String.join("\n", all));
            }
        }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        acts.addView(Ui.hgap(this, 8));
        acts.addView(Ui.button(this, "Очистить", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EventLog.clear(MainActivity.this);
                renderLog();
            }
        }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        put(card, acts, 14);
        put(root, card, 0);
    }

    private void renderLog() {
        if (logBox == null) return;
        logBox.removeAllViews();
        List<String> lines = EventLog.tail(this, 6);
        if (lines.isEmpty()) {
            logBox.addView(Ui.dim(this, "Событий пока нет."));
            return;
        }
        Collections.reverse(lines);
        for (String l : lines) {
            TextView t = Ui.mono(this, l);
            t.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
            logBox.addView(t);
        }
    }

    // ------------------------------------------------------------------ данные

    private void buildData() {
        put(root, Ui.section(this, "Данные"), 0);
        LinearLayout card = Ui.card(this);
        card.addView(Ui.dim(this, "Правила и настройки можно сохранить в файл и вернуть на другом "
                + "телефоне. Токен обновлений в файл не попадает."));
        LinearLayout acts = Ui.row(this);
        acts.addView(Ui.button(this, "Экспорт", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "brawl-dns-settings.json");
                startActivityForResult(i, REQ_EXPORT);
            }
        }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        acts.addView(Ui.hgap(this, 8));
        acts.addView(Ui.button(this, "Импорт", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
        }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        put(card, acts, 14);
        put(root, card, 0);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            if (req == REQ_EXPORT) {
                OutputStream os = getContentResolver().openOutputStream(u, "wt");
                os.write(cfg.toJson(false).toString(2).getBytes(StandardCharsets.UTF_8));
                os.close();
                toast("Сохранено");
            } else if (req == REQ_IMPORT) {
                InputStream is = getContentResolver().openInputStream(u);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                is.close();
                Config imported = Config.fromJson(new JSONObject(
                        new String(bo.toByteArray(), StandardCharsets.UTF_8)));
                imported.token = cfg.token;
                cfg = imported;
                cfg.save(this);
                EventLog.add(this, "Настройки импортированы");
                recreate();
            }
        } catch (Exception e) {
            toast("Ошибка: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ обновления

    private void buildUpdates() {
        put(root, Ui.section(this, "Обновления"), 0);
        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, "Репозиторий GitHub"));
        EditText repo = Ui.field(this, cfg.repo, "владелец/имя", true);
        put(card, repo, 8);
        watch(repo, s -> {
            cfg.repo = s.trim();
            cfg.save(this);
        });
        put(card, Ui.label(this, "Токен (для приватного репозитория)"), 16);
        EditText tok = Ui.field(this, cfg.token, "необязательно", true);
        tok.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        put(card, tok, 8);
        watch(tok, s -> {
            cfg.token = s.trim();
            cfg.save(this);
        });
        put(card, Ui.dim(this, "Токен только для чтения (Contents: Read-only). Хранится на телефоне и "
                + "уходит только на api.github.com."), 8);
        put(card, Ui.button(this, "Проверить обновления", 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdates();
            }
        }), 16);
        put(root, card, 0);
    }

    private void checkUpdates() {
        toast("Проверяю…");
        final String repo = cfg.repo;
        final String token = cfg.token;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Updater.Result r = Updater.check(getApplicationContext(), repo, token);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        showUpdateResult(r);
                    }
                });
            }
        }).start();
    }

    private void showUpdateResult(final Updater.Result r) {
        if (!r.ok) {
            Ui.message(this, "Обновления", r.error);
            return;
        }
        if (r.remote <= r.local) {
            Ui.message(this, "Обновления", "Установлена актуальная сборка (" + r.local + ").");
            return;
        }
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.title(this, "Доступна новая сборка", 22));
        put(v, Ui.body(this, "У вас " + r.local + ", в репозитории " + r.remote + " (" + r.tag + ")."), 12);
        final Dialog d = Ui.dialog(this, v, true);
        LinearLayout acts = Ui.row(this);
        acts.addView(Ui.button(this, "Позже", 2, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                d.dismiss();
            }
        }));
        acts.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        acts.addView(Ui.button(this, "Открыть релиз", 0, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url)));
                d.dismiss();
            }
        }));
        put(v, acts, 20);
        Ui.show(d, this);
    }
}
