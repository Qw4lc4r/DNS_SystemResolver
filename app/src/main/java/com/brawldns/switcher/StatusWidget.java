package com.brawldns.switcher;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Виджет рабочего стола: статус и включение/остановка службы одним нажатием. */
public class StatusWidget extends AppWidgetProvider {
    static final String ACTION_TOGGLE = "com.brawldns.switcher.WIDGET_TOGGLE";

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, StatusWidget.class));
            if (ids.length == 0) return;
            m.updateAppWidget(ids, build(c));
        } catch (Exception ignored) {
        }
    }

    static RemoteViews build(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_status);
        boolean on = State.enabled(c);
        String st = on ? State.text(c) : "Остановлено";
        rv.setTextViewText(R.id.widget_status, st.isEmpty() ? "Жду запуска игры" : st);
        rv.setTextViewText(R.id.widget_action, on ? "НАЖМИТЕ, ЧТОБЫ ОСТАНОВИТЬ" : "НАЖМИТЕ, ЧТОБЫ ЗАПУСТИТЬ");
        Intent i = new Intent(c, StatusWidget.class).setAction(ACTION_TOGGLE);
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(
                c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return rv;
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        m.updateAppWidget(ids, build(c));
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            MonitorService.toggle(c);
            refresh(c);
        } else {
            super.onReceive(c, intent);
        }
    }
}
