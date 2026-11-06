package com.brawldns.switcher;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Небольшой набор элементов интерфейса: тёмная тёплая гамма, сдержанная латунь, тонкие линии. */
final class Ui {
    static final int BG = 0xFF14120F;
    static final int SURFACE = 0xFF1B1815;
    static final int LINE = 0xFF332E28;
    static final int LINE_HI = 0xFF4A443B;
    static final int TEXT = 0xFFECE6DA;
    static final int DIM = 0xFF9B9487;
    static final int FAINT = 0xFF6A645A;
    static final int ACCENT = 0xFFB59A6A;
    static final int WARN = 0xFFC27A62;

    static final Typeface SERIF = Typeface.create("serif", Typeface.NORMAL);
    static final Typeface SANS = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    static Drawable ripple(Context c, Drawable content, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), content, mask);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, s, 11, DIM, MEDIUM);
        t.setAllCaps(true);
        t.setLetterSpacing(0.16f);
        return t;
    }

    static TextView title(Context c, String s, float sp) {
        TextView t = text(c, s, sp, TEXT, SERIF);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    static TextView body(Context c, String s) {
        TextView t = text(c, s, 14, TEXT, SANS);
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    static TextView dim(Context c, String s) {
        TextView t = text(c, s, 12.5f, DIM, SANS);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    static TextView mono(Context c, String s) {
        return text(c, s, 12, DIM, MONO);
    }

    static View gap(Context c, int dpH) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpH)));
        return v;
    }

    static View hgap(Context c, int dpW) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dpW), 1));
        return v;
    }

    static View line(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(c, 1))));
        return v;
    }

    static LinearLayout section(Context c, String name) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 34), 0, dp(c, 12));
        row.addView(label(c, name));
        View rule = new View(c);
        rule.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.max(1, dp(c, 1)), 1f);
        lp.leftMargin = dp(c, 14);
        row.addView(rule, lp);
        return row;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(box(c, SURFACE, LINE, 8));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    /** style: 0 основная, 1 контурная, 2 текстовая. */
    static TextView button(Context c, String s, int style, View.OnClickListener l) {
        TextView b = text(c, s, 12, TEXT, MEDIUM);
        b.setAllCaps(true);
        b.setLetterSpacing(0.09f);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 46));
        int ph = dp(c, 18);
        b.setPadding(ph, 0, ph, 0);
        Drawable bg;
        if (style == 0) {
            b.setTextColor(BG);
            bg = box(c, TEXT, 0, 6);
        } else if (style == 1) {
            bg = box(c, Color.TRANSPARENT, LINE_HI, 6);
        } else {
            b.setTextColor(ACCENT);
            bg = box(c, Color.TRANSPARENT, 0, 6);
        }
        b.setBackground(ripple(c, bg, 6));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    static Switch sw(Context c, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setChecked(checked);
        int[][] st = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        s.setThumbTintList(new ColorStateList(st, new int[]{TEXT, DIM}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0xFF7A6745, LINE}));
        s.setOnCheckedChangeListener(l);
        return s;
    }

    static LinearLayout switchRow(Context c, String title, String sub, Switch s) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 8), 0, dp(c, 8));
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(body(c, title));
        if (sub != null) {
            TextView d = dim(c, sub);
            d.setPadding(0, dp(c, 3), 0, 0);
            txt.addView(d);
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.rightMargin = dp(c, 12);
        row.addView(txt, tl);
        row.addView(s);
        return row;
    }

    static EditText field(Context c, String value, String hint, boolean mono) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(FAINT);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTypeface(mono ? MONO : SANS);
        e.setIncludeFontPadding(false);
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        e.setBackground(box(c, BG, LINE, 6));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    static Dialog dialog(Context c, View content, boolean scroll) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout holder = new LinearLayout(c);
        holder.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 20);
        holder.setPadding(p, p, p, p);
        holder.setBackground(box(c, SURFACE, LINE_HI, 10));
        holder.addView(content, new LinearLayout.LayoutParams(MATCH, WRAP));
        if (scroll) {
            ScrollView sv = new ScrollView(c);
            sv.addView(holder);
            d.setContentView(sv);
        } else {
            d.setContentView(holder);
        }
        return d;
    }

    static void show(Dialog d, Context c) {
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.92f), WRAP);
            w.setDimAmount(0.65f);
        }
    }

    static void message(Context c, String title, String body) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(title(c, title, 22));
        v.addView(gap(c, 12));
        v.addView(body(c, body));
        v.addView(gap(c, 18));
        final Dialog d = dialog(c, v, true);
        v.addView(button(c, "Закрыть", 1, new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                d.dismiss();
            }
        }));
        show(d, c);
    }
}
