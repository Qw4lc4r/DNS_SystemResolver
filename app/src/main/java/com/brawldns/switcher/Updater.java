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
    }

    private Updater() {}

    static Result check(Context c, String repo, String token) {
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
            if (!token.isEmpty()) h.setRequestProperty("Authorization", "Bearer " + token);
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
            Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(r.tag);
            if (m.find()) r.remote = Long.parseLong(m.group(1));
            r.ok = true;
        } catch (Exception e) {
            r.error = "Нет связи: " + e.getClass().getSimpleName();
        }
        return r;
    }
}
