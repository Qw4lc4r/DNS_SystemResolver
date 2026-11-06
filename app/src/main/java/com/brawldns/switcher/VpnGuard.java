package com.brawldns.switcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Проверка VPN: при включённом VPN частный DNS не работает, поэтому запуск блокируется. */
final class VpnGuard {
    static final String MSG = "Выключите VPN: с ним частный DNS не работает";

    private VpnGuard() {}

    static boolean vpnActive(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }

    /** Если VPN включён: пишет в журнал, показывает уведомление-тост и возвращает true. */
    static boolean blocked(final Context c) {
        if (!vpnActive(c)) return false;
        EventLog.add(c, "Запуск отменён: включён VPN");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(c, MSG, Toast.LENGTH_LONG).show();
            }
        });
        return true;
    }
}
