package com.brawldns.switcher;

import android.content.Context;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Проверка доступности DoT-сервера (порт 853) и измерение времени TLS-рукопожатия. */
final class DnsProbe {
    private static final long OK_TTL_MS = 120_000L;
    private static final Map<String, Long> okAt = new HashMap<>();

    private DnsProbe() {}

    /** Возвращает миллисекунды или -1, если сервер не отвечает. */
    static long probe(String host, int timeoutMs) {
        long t0 = System.nanoTime();
        Socket raw = null;
        SSLSocket ssl = null;
        try {
            raw = new Socket();
            raw.connect(new InetSocketAddress(host, 853), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            ssl = (SSLSocket) f.createSocket(raw, host, 853, true);
            SSLParameters p = ssl.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(p);
            ssl.startHandshake();
            return (System.nanoTime() - t0) / 1_000_000L;
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                if (ssl != null) ssl.close();
            } catch (Exception ignored) {
            }
            try {
                if (raw != null) raw.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Лучший из двух замеров. */
    static long best(String host, int timeoutMs) {
        long a = probe(host, timeoutMs);
        long b = probe(host, timeoutMs);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /** Первый отвечающий DNS из списка, либо null. Вызывать не из главного потока. */
    static String pickReachable(Context c, List<String> candidates) {
        long now = System.currentTimeMillis();
        for (String h : candidates) {
            Long t;
            synchronized (okAt) {
                t = okAt.get(h);
            }
            if (t != null && now - t < OK_TTL_MS) return h;
            long ms = probe(h, 2500);
            if (ms >= 0) {
                synchronized (okAt) {
                    okAt.put(h, System.currentTimeMillis());
                }
                EventLog.add(c, "DNS " + h + " отвечает (" + ms + " мс)");
                return h;
            }
            EventLog.add(c, "DNS " + h + " не отвечает");
        }
        return null;
    }
}
