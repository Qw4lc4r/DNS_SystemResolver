package com.brawldns.switcher;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** Плитка в шторке: ручное включение и выключение DNS. */
public class DnsTile extends TileService {
    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        if (!DnsHelper.hasSecure(this) && !State.manual(this)) {
            Toast.makeText(this, "Нет разрешения WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show();
            return;
        }
        DnsHelper.toggleManual(this, new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = State.manual(this);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Brawl DNS");
        t.setSubtitle(on ? "Включён" : "Выключен");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_stat));
        t.updateTile();
    }
}
