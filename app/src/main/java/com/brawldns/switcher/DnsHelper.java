package com.brawldns.switcher;

import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

final class DnsHelper {
    private static final String P = "dns_state";
    private static final String MODE = "private_dns_mode";
    private static final String SPEC = "private_dns_specifier";

    private DnsHelper() {}

    static boolean hasUsageAccess(Context c) {
        AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int m = ao.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                c.getPackageName());
        return m == AppOpsManager.MODE_ALLOWED;
    }

    static boolean hasSecure(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static Network wifiNetwork(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] all = cm.getAllNetworks();
        for (Network n : all) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null
                    && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return n;
            }
        }
        return null;
    }

    static boolean isOnWifi(Context c) {
        return wifiNetwork(c) != null;
    }

    /** Адрес шлюза текущей Wi-Fi сети (по нему различаем сети без доступа к геолокации). */
    static String wifiGateway(Context c) {
        Network n = wifiNetwork(c);
        if (n == null) return null;
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties lp = cm.getLinkProperties(n);
        if (lp == null) return null;
        String any = null;
        for (RouteInfo r : lp.getRoutes()) {
            if (!r.isDefaultRoute()) continue;
            InetAddress g = r.getGateway();
            if (g == null) continue;
            if (g instanceof Inet4Address) return g.getHostAddress();
            if (any == null) any = g.getHostAddress();
        }
        return any;
    }

    static boolean isApplied(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE).getBoolean("applied", false);
    }

    static String currentHost(Context c) {
        try {
            String h = Settings.Global.getString(c.getContentResolver(), SPEC);
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    static String currentMode(Context c) {
        try {
            String m = Settings.Global.getString(c.getContentResolver(), MODE);
            return m == null ? "opportunistic" : m;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Включает Частный DNS с указанным хостом. Запоминает прежние значения. */
    static boolean apply(Context c, String host) {
        SharedPreferences sp = c.getSharedPreferences(P, Context.MODE_PRIVATE);
        ContentResolver r = c.getContentResolver();
        try {
            if (!sp.getBoolean("applied", false)) {
                String prevMode = Settings.Global.getString(r, MODE);
                String prevSpec = Settings.Global.getString(r, SPEC);
                sp.edit()
                        .putString("prev_mode", prevMode)
                        .putString("prev_spec", prevSpec)
                        .commit();
            }
            Settings.Global.putString(r, SPEC, host);
            Settings.Global.putString(r, MODE, "hostname");
            sp.edit().putBoolean("applied", true).commit();
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Возвращает прежние настройки Частного DNS. */
    static void restore(Context c) {
        SharedPreferences sp = c.getSharedPreferences(P, Context.MODE_PRIVATE);
        if (!sp.getBoolean("applied", false)) return;
        ContentResolver r = c.getContentResolver();
        String prevMode = sp.getString("prev_mode", null);
        String prevSpec = sp.getString("prev_spec", null);
        try {
            if (prevSpec != null) {
                Settings.Global.putString(r, SPEC, prevSpec);
            }
            Settings.Global.putString(r, MODE, prevMode != null ? prevMode : "opportunistic");
            sp.edit().putBoolean("applied", false).commit();
        } catch (SecurityException ignored) {
        }
    }

    /** Ручное включение/выключение (плитка в шторке и кнопка в приложении). */
    static void toggleManual(final Context c, final Runnable done) {
        final Handler ui = new Handler(Looper.getMainLooper());
        if (State.manual(c)) {
            State.setManual(c, false);
            restore(c);
            EventLog.add(c, "Вручную: DNS выключен");
            if (done != null) done.run();
            return;
        }
        if (!hasSecure(c)) {
            EventLog.add(c, "Вручную: нет разрешения WRITE_SECURE_SETTINGS");
            if (done != null) done.run();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Config cfg = Config.load(c);
                String host = DnsProbe.pickReachable(c, cfg.candidates(null));
                if (host != null && apply(c, host)) {
                    State.setManual(c, true);
                    EventLog.add(c, "Вручную: DNS " + host + " включён");
                } else {
                    EventLog.add(c, "Вручную: не удалось включить DNS");
                }
                if (done != null) ui.post(done);
            }
        }).start();
    }
}
