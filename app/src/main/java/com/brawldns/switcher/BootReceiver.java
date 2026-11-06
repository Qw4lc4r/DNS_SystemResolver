package com.brawldns.switcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        if (State.enabled(c)) {
            try {
                c.startForegroundService(new Intent(c, MonitorService.class));
            } catch (Exception ignored) {
            }
        }
    }
}
