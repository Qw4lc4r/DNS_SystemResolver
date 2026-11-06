package com.brawldns.switcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Быстро меняющееся состояние: работает ли служба, пауза, статус для экрана/виджета. */
final class State {
    static final String PREFS = "state";
    static final int OFF = 0;
    static final int WAIT = 1;
    static final int ACTIVE = 2;
    static final int PAUSED = 3;
    static final int PROBLEM = 4;

    private State() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    static void setEnabled(Context c, boolean v) {
        prefs(c).edit().putBoolean("enabled", v).apply();
    }

    static boolean manual(Context c) {
        return prefs(c).getBoolean("manual", false);
    }

    static void setManual(Context c, boolean v) {
        prefs(c).edit().putBoolean("manual", v).apply();
    }

    static long pausedUntil(Context c) {
        return prefs(c).getLong("paused_until", 0L);
    }

    static void setPausedUntil(Context c, long t) {
        prefs(c).edit().putLong("paused_until", t).apply();
    }

    static long graceUntil(Context c) {
        return prefs(c).getLong("grace_until", 0L);
    }

    static void setGraceUntil(Context c, long t) {
        prefs(c).edit().putLong("grace_until", t).apply();
    }

    static int kind(Context c) {
        return prefs(c).getInt("kind", OFF);
    }

    static String text(Context c) {
        return prefs(c).getString("text", "");
    }

    static void setStatus(Context c, int kind, String text) {
        prefs(c).edit().putInt("kind", kind).putString("text", text).apply();
    }
}
