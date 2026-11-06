package com.brawldns.switcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;

/** Выдача разрешений самому себе через Shizuku: компьютер не нужен. */
final class ShizukuHelper {
    static final String PKG = "moe.shizuku.privileged.api";
    static final String SITE = "https://shizuku.rikka.app/download/";
    static final int REQ = 4242;

    private ShizukuHelper() {}

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Служба Shizuku запущена и отвечает. */
    static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Приложению разрешено пользоваться Shizuku. */
    static boolean permitted() {
        try {
            return running() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Можно ли ещё показать системный запрос (false, если выбрано «Больше не спрашивать»). */
    static boolean canAsk() {
        try {
            return !Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable t) {
            return true;
        }
    }

    static void request() {
        try {
            Shizuku.requestPermission(REQ);
        } catch (Throwable ignored) {
        }
    }

    private static String read(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(line);
                if (sb.length() > 300) break;
            }
        }
        return sb.toString();
    }

    /** Выполняет команду от имени shell через Shizuku. Возвращает вывод, пустую строку или "ERR: ...". */
    static String exec(String cmd) {
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess",
                    String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            String out = read(p.getInputStream());
            String err = read(p.getErrorStream());
            int code = p.waitFor();
            if (code != 0) return "ERR(" + code + "): " + (err.isEmpty() ? out : err);
            return out;
        } catch (Throwable t) {
            Throwable r = t.getCause() != null ? t.getCause() : t;
            return "ERR: " + r;
        }
    }

    /** Выдаёт себе все нужные разрешения. Результат каждой команды пишет в журнал. */
    static void grantAll(Context c) {
        String p = c.getPackageName();
        String[][] steps = new String[][]{
                {"Системные настройки", "pm grant " + p + " android.permission.WRITE_SECURE_SETTINGS"},
                {"Статистика использования", "appops set " + p + " GET_USAGE_STATS allow"},
                {"Статистика использования (доп.)", "pm grant " + p + " android.permission.PACKAGE_USAGE_STATS"},
                {"Работа в фоне", "dumpsys deviceidle whitelist +" + p},
        };
        for (String[] s : steps) {
            String r = exec(s[1]);
            EventLog.add(c, "Shizuku: " + s[0] + (r.startsWith("ERR") ? " не вышло: " + r : " — ок"));
        }
        if (Build.VERSION.SDK_INT >= 33) {
            String r = exec("pm grant " + p + " android.permission.POST_NOTIFICATIONS");
            EventLog.add(c, "Shizuku: Уведомления" + (r.startsWith("ERR") ? " не вышло: " + r : " — ок"));
        }
    }
}
