package com.brawldns.switcher;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Простой текстовый журнал событий. */
final class EventLog {
    private static final int KEEP = 300;

    private EventLog() {}

    private static File file(Context c) {
        return new File(c.getFilesDir(), "events.log");
    }

    static synchronized void add(Context c, String msg) {
        try {
            String line = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(new Date())
                    + "  " + msg + "\n";
            File f = file(c);
            try (FileOutputStream os = new FileOutputStream(f, true)) {
                os.write(line.getBytes(StandardCharsets.UTF_8));
            }
            if (f.length() > 60_000) {
                List<String> all = tail(c, KEEP);
                StringBuilder sb = new StringBuilder();
                for (String s : all) sb.append(s).append('\n');
                try (FileOutputStream os = new FileOutputStream(f, false)) {
                    os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
        }
    }

    static synchronized List<String> tail(Context c, int n) {
        List<String> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try (FileInputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) > 0) bo.write(buf, 0, r);
            String[] lines = new String(bo.toByteArray(), StandardCharsets.UTF_8).split("\n");
            int from = Math.max(0, lines.length - n);
            for (int i = from; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) out.add(lines[i]);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static synchronized void clear(Context c) {
        file(c).delete();
    }
}
