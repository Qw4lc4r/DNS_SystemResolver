package com.brawldns.switcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.quicksettings.TileService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MonitorService extends Service {
    static final String ACTION_PAUSE = "com.brawldns.switcher.PAUSE";
    static final String ACTION_RESUME = "com.brawldns.switcher.RESUME";

    private static final String CHANNEL = "monitor";
    private static final int NOTIF_ID = 1;
    private static final long POLL_MS = 1500L;
    private static final long PAUSE_MS = 3600_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private ExecutorService worker;
    private UsageStatsManager usm;
    private NotificationManager nm;

    private String fgPkg = null;
    private long lastQuery = 0L;
    private long lastActive = 0L;
    private volatile long retryAt = 0L;
    private int lastKind = -1;
    private String lastText = "";
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                poll();
            } catch (Exception e) {
                publish(State.PROBLEM, "Ошибка: " + e.getClass().getSimpleName());
            }
            if (running) handler.postDelayed(this, POLL_MS);
        }
    };

    /** Включить или выключить службу (кнопка, виджет). */
    static void toggle(Context c) {
        if (State.enabled(c)) {
            State.setEnabled(c, false);
            c.stopService(new Intent(c, MonitorService.class));
            State.setStatus(c, State.OFF, "Служба остановлена");
            EventLog.add(c, "Служба остановлена");
        } else {
            State.setEnabled(c, true);
            State.setStatus(c, State.WAIT, "Жду запуска игры");
            try {
                c.startForegroundService(new Intent(c, MonitorService.class));
                EventLog.add(c, "Служба запущена");
            } catch (Exception e) {
                State.setEnabled(c, false);
                State.setStatus(c, State.OFF, "Не удалось запустить службу");
            }
        }
        StatusWidget.refresh(c);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Слежение за играми", NotificationManager.IMPORTANCE_LOW));
        worker = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification("Жду запуска игры", false);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        if (!State.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String a = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(a)) {
            State.setPausedUntil(this, System.currentTimeMillis() + PAUSE_MS);
            EventLog.add(this, "Пауза на 1 час");
        } else if (ACTION_RESUME.equals(a)) {
            State.setPausedUntil(this, 0L);
            EventLog.add(this, "Пауза снята");
        }
        if (!running) {
            running = true;
            handler.post(tick);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        if (worker != null) worker.shutdownNow();
        if (!State.manual(this)) DnsHelper.restore(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static String clock(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private void poll() {
        final long now = System.currentTimeMillis();
        Config cfg = Config.load(this);

        if (!DnsHelper.hasUsageAccess(this)) {
            publish(State.PROBLEM, "Нужен доступ к статистике использования");
            return;
        }
        if (!DnsHelper.hasSecure(this)) {
            publish(State.PROBLEM, "Нет разрешения WRITE_SECURE_SETTINGS");
            return;
        }

        long from = (lastQuery == 0L) ? now - 3600000L : lastQuery - 2000L;
        UsageEvents events = usm.queryEvents(from, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int t = e.getEventType();
            String p = e.getPackageName();
            if (t == UsageEvents.Event.ACTIVITY_RESUMED) {
                fgPkg = p;
            } else if ((t == UsageEvents.Event.ACTIVITY_PAUSED
                    || t == UsageEvents.Event.ACTIVITY_STOPPED)
                    && p != null && p.equals(fgPkg)) {
                fgPkg = null;
            }
        }
        lastQuery = now;

        if (State.manual(this)) {
            publish(State.ACTIVE, "Вручную: DNS " + DnsHelper.currentHost(this));
            return;
        }

        long pausedUntil = State.pausedUntil(this);
        boolean paused = now < pausedUntil;
        Config.Rule rule = cfg.ruleFor(fgPkg);

        String blocked = null;
        if (rule != null) {
            if (paused) {
                blocked = "пауза";
            } else if (!cfg.inSchedule()) {
                blocked = "вне расписания";
            } else if (cfg.wifiOnly && !DnsHelper.isOnWifi(this)) {
                blocked = "нет Wi-Fi";
            } else if (cfg.netOnly && !cfg.knownNet(DnsHelper.wifiGateway(this))) {
                blocked = "сеть не в списке";
            }
        }
        boolean active = rule != null && blocked == null;
        boolean applied = DnsHelper.isApplied(this);

        if (active) {
            lastActive = now;
            if (applied) {
                publish(State.ACTIVE, rule.name + " · " + DnsHelper.currentHost(this));
            } else if (now >= retryAt) {
                startApply(cfg, rule);
            }
        } else {
            if (applied && now > State.graceUntil(this)
                    && now - lastActive >= cfg.offDelay * 1000L) {
                DnsHelper.restore(this);
                EventLog.add(this, "DNS возвращён");
                applied = false;
            }
            if (paused) {
                publish(State.PAUSED, "Пауза до " + clock(pausedUntil));
            } else if (blocked != null) {
                publish(State.WAIT, rule.name + ": " + blocked);
            } else {
                publish(State.WAIT, applied ? "Выхожу из игры…" : "Жду запуска игры");
            }
        }
    }

    private void startApply(final Config cfg, final Config.Rule rule) {
        if (!busy.compareAndSet(false, true)) return;
        publish(State.WAIT, "Проверяю DNS…");
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<String> cands = new ArrayList<>(cfg.candidates(rule));
                        String host = DnsProbe.pickReachable(MonitorService.this, cands);
                        if (host == null) {
                            retryAt = System.currentTimeMillis() + 30_000L;
                            EventLog.add(MonitorService.this, "Ни один DNS не отвечает, оставляю как есть");
                            publish(State.PROBLEM, "DNS-серверы не отвечают");
                        } else if (DnsHelper.apply(MonitorService.this, host)) {
                            EventLog.add(MonitorService.this, "Включён DNS " + host + " для " + rule.name);
                            publish(State.ACTIVE, rule.name + " · " + host);
                        } else {
                            retryAt = System.currentTimeMillis() + 30_000L;
                            publish(State.PROBLEM, "Не удалось изменить DNS");
                        }
                    } finally {
                        busy.set(false);
                    }
                }
            });
        } catch (Exception ex) {
            busy.set(false);
        }
    }

    private synchronized void publish(int kind, String text) {
        if (kind == lastKind && text.equals(lastText)) return;
        lastKind = kind;
        lastText = text;
        State.setStatus(this, kind, text);
        nm.notify(NOTIF_ID, buildNotification(text, kind == State.PAUSED));
        StatusWidget.refresh(this);
        try {
            TileService.requestListeningState(this, new ComponentName(this, DnsTile.class));
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(String text, boolean paused) {
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent ai = new Intent(this, MonitorService.class)
                .setAction(paused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent api = PendingIntent.getService(
                this, paused ? 2 : 1, ai,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action act = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat),
                paused ? "Продолжить" : "Пауза 1 ч", api).build();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Brawl DNS")
                .setContentText(text)
                .setContentIntent(open)
                .addAction(act)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }
}
