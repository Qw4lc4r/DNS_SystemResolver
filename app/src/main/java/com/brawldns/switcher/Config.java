package com.brawldns.switcher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Настройки пользователя (правила, DNS, условия). Хранятся одним JSON. */
final class Config {
    static final class Rule {
        String pkg = "";
        String name = "";
        String dns = "";
        boolean on = true;
    }

    static final class Net {
        String gw = "";
        String label = "";
    }

    static final String DEF_PRIMARY = "dns.nullsproxy.com";
    static final String DEF_DOMAIN = "game.brawlstarsgame.com";

    final List<Rule> rules = new ArrayList<>();
    final List<String> fallbacks = new ArrayList<>();
    final List<Net> nets = new ArrayList<>();
    String primary = DEF_PRIMARY;
    boolean wifiOnly = true;
    boolean netOnly = false;
    boolean schedOn = false;
    int schedFrom = 18 * 60;
    int schedTo = 23 * 60;
    int offDelay = 5;
    String checkDomain = DEF_DOMAIN;
    String repo = "";
    String token = "";

    static Config defaults() {
        Config c = new Config();
        Rule r = new Rule();
        r.pkg = "com.supercell.brawlstars";
        r.name = "Brawl Stars";
        c.rules.add(r);
        return c;
    }

    static Config load(Context ctx) {
        String s = ctx.getSharedPreferences("config", Context.MODE_PRIVATE).getString("json", null);
        if (s == null) return defaults();
        try {
            return fromJson(new JSONObject(s));
        } catch (Exception e) {
            return defaults();
        }
    }

    void save(Context ctx) {
        try {
            ctx.getSharedPreferences("config", Context.MODE_PRIVATE)
                    .edit().putString("json", toJson(true).toString()).apply();
        } catch (Exception ignored) {
        }
    }

    JSONObject toJson(boolean withToken) throws Exception {
        JSONObject o = new JSONObject();
        JSONArray ra = new JSONArray();
        for (Rule r : rules) {
            JSONObject j = new JSONObject();
            j.put("pkg", r.pkg);
            j.put("name", r.name);
            j.put("dns", r.dns);
            j.put("on", r.on);
            ra.put(j);
        }
        o.put("rules", ra);
        o.put("primary", primary);
        o.put("fallbacks", new JSONArray(fallbacks));
        JSONArray na = new JSONArray();
        for (Net n : nets) {
            JSONObject j = new JSONObject();
            j.put("gw", n.gw);
            j.put("label", n.label);
            na.put(j);
        }
        o.put("nets", na);
        o.put("wifiOnly", wifiOnly);
        o.put("netOnly", netOnly);
        o.put("schedOn", schedOn);
        o.put("schedFrom", schedFrom);
        o.put("schedTo", schedTo);
        o.put("offDelay", offDelay);
        o.put("checkDomain", checkDomain);
        o.put("repo", repo);
        if (withToken) o.put("token", token);
        return o;
    }

    static Config fromJson(JSONObject o) throws Exception {
        Config c = new Config();
        JSONArray ra = o.optJSONArray("rules");
        if (ra != null) {
            for (int i = 0; i < ra.length(); i++) {
                JSONObject j = ra.getJSONObject(i);
                Rule r = new Rule();
                r.pkg = j.optString("pkg", "");
                r.name = j.optString("name", "");
                r.dns = j.optString("dns", "");
                r.on = j.optBoolean("on", true);
                if (!r.pkg.isEmpty()) c.rules.add(r);
            }
        }
        c.primary = o.optString("primary", DEF_PRIMARY);
        JSONArray fa = o.optJSONArray("fallbacks");
        if (fa != null) {
            for (int i = 0; i < fa.length(); i++) {
                String h = fa.optString(i, "").trim();
                if (!h.isEmpty()) c.fallbacks.add(h);
            }
        }
        JSONArray na = o.optJSONArray("nets");
        if (na != null) {
            for (int i = 0; i < na.length(); i++) {
                JSONObject j = na.getJSONObject(i);
                Net n = new Net();
                n.gw = j.optString("gw", "");
                n.label = j.optString("label", "");
                if (!n.gw.isEmpty()) c.nets.add(n);
            }
        }
        c.wifiOnly = o.optBoolean("wifiOnly", true);
        c.netOnly = o.optBoolean("netOnly", false);
        c.schedOn = o.optBoolean("schedOn", false);
        c.schedFrom = o.optInt("schedFrom", 18 * 60);
        c.schedTo = o.optInt("schedTo", 23 * 60);
        c.offDelay = Math.max(0, Math.min(60, o.optInt("offDelay", 5)));
        c.checkDomain = o.optString("checkDomain", DEF_DOMAIN);
        c.repo = o.optString("repo", "");
        c.token = o.optString("token", "");
        return c;
    }

    Rule ruleFor(String pkg) {
        if (pkg == null) return null;
        for (Rule r : rules) {
            if (r.on && pkg.equals(r.pkg)) return r;
        }
        return null;
    }

    /** Порядок проверки DNS: DNS правила (или основной), затем запасные. */
    List<String> candidates(Rule rule) {
        List<String> out = new ArrayList<>();
        String first = (rule != null && !rule.dns.trim().isEmpty()) ? rule.dns.trim() : primary.trim();
        if (!first.isEmpty()) out.add(first);
        if (!primary.trim().isEmpty() && !out.contains(primary.trim())) out.add(primary.trim());
        for (String f : fallbacks) {
            if (!f.isEmpty() && !out.contains(f)) out.add(f);
        }
        return out;
    }

    boolean inSchedule() {
        if (!schedOn || schedFrom == schedTo) return true;
        Calendar cal = Calendar.getInstance();
        int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        if (schedFrom < schedTo) return m >= schedFrom && m < schedTo;
        return m >= schedFrom || m < schedTo;
    }

    boolean knownNet(String gw) {
        if (gw == null) return false;
        for (Net n : nets) {
            if (gw.equals(n.gw)) return true;
        }
        return false;
    }
}
