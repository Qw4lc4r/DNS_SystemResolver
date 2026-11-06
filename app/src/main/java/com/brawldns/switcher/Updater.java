package com.brawldns.switcher;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Проверка новой сборки по последнему релизу на GitHub. */
final class Updater {
    static final class Result {
        boolean ok;
        String error = "";
        long remote;
        long local;
        String tag = "";
        String url = "";
        String apk = "";
    }

    /** Публичный репозиторий, где GitHub Actions публикует сборки. */
    static final String REPO = "Qw4lc4r/DNS_SystemResolver";
    static final String APK_NAME = "BrawlDns.apk";

    private Updater() {}

    static Result check(Context c) {
        final String repo = REPO;
        Result r = new Result();
        try {
            r.local = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
            if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                r.error = "Укажите репозиторий в формате владелец/имя";
                return r;
            }
            HttpURLConnection h = (HttpURLConnection)
                    new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("User-Agent", "BrawlDns");
            int code = h.getResponseCode();
            if (code != 200) {
                r.error = code == 404
                        ? "Релизов нет, либо репозиторий приватный и нужен токен (ответ 404)"
                        : "Ответ сервера: " + code;
                return r;
            }
            InputStream is = h.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject o = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            r.tag = o.optString("tag_name");
            r.url = o.optString("html_url");
            r.apk = "https://github.com/" + repo + "/releases/latest/download/" + APK_NAME;
            Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(r.tag);
            if (m.find()) r.remote = Long.parseLong(m.group(1));
            r.ok = true;
        } catch (Exception e) {
            r.error = "Нет связи: " + e.getClass().getSimpleName();
        }
        return r;
    }
}
