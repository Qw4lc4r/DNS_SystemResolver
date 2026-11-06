package com.brawldns.switcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Циферблат состояния: ободок из тонких делений, подсвечиваемых латунью. */
final class DialView extends View {
    private static final int TICKS = 90;

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private int kind = State.OFF;
    private String bigText = "";
    private String smallText = "";
    private float sweep = 0f;
    private ValueAnimator anim;

    DialView(Context c) {
        super(c);
        tick.setStrokeCap(Paint.Cap.BUTT);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Ui.LINE);
        ring.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
        big.setColor(Ui.TEXT);
        big.setTypeface(Ui.SERIF);
        big.setTextSize(sp(30));
        big.setTextAlign(Paint.Align.CENTER);
        small.setColor(Ui.DIM);
        small.setTypeface(Ui.MEDIUM);
        small.setTextSize(sp(10.5f));
        small.setLetterSpacing(0.12f);
        small.setTextAlign(Paint.Align.CENTER);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    private float dp(float v) {
        return Ui.dp(getContext(), v);
    }

    void set(int newKind, String bigT, String smallT) {
        kind = newKind;
        bigText = bigT;
        smallText = smallT == null ? "" : smallT;
        float target;
        switch (newKind) {
            case State.ACTIVE:
                target = 1f;
                break;
            case State.PAUSED:
                target = 0.5f;
                break;
            case State.PROBLEM:
                target = 0.25f;
                break;
            case State.WAIT:
                target = 1f / TICKS;
                break;
            default:
                target = 0f;
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(sweep, target);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                sweep = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), Ui.dp(getContext(), 270));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outer = Math.min(cx, cy) - dp(4);
        int lit = Math.round(sweep * TICKS);
        int on = kind == State.PROBLEM ? Ui.WARN : (kind == State.PAUSED ? Ui.DIM : Ui.ACCENT);

        for (int i = 0; i < TICKS; i++) {
            double a = Math.toRadians(-90.0 + i * 360.0 / TICKS);
            boolean major = i % 15 == 0;
            float len = major ? dp(15) : dp(8);
            tick.setStrokeWidth(major ? dp(1.6f) : dp(1f));
            tick.setColor(i < lit ? on : (major ? Ui.LINE_HI : Ui.LINE));
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            canvas.drawLine(cx + c * outer, cy + s * outer,
                    cx + c * (outer - len), cy + s * (outer - len), tick);
        }
        canvas.drawCircle(cx, cy, outer - dp(28), ring);

        float maxW = (outer - dp(28)) * 2f * 0.84f;
        fit(big, bigText, sp(30), sp(17), maxW);
        String st = smallText.toUpperCase();
        fit(small, st, sp(10.5f), sp(8), maxW);
        CharSequence b = TextUtils.ellipsize(bigText, big, maxW, TextUtils.TruncateAt.END);
        Paint.FontMetrics fb = big.getFontMetrics();
        if (st.isEmpty()) {
            canvas.drawText(b.toString(), cx, cy - (fb.ascent + fb.descent) / 2f, big);
            return;
        }
        CharSequence s2 = TextUtils.ellipsize(st, small, maxW, TextUtils.TruncateAt.END);
        Paint.FontMetrics fs = small.getFontMetrics();
        float bigH = fb.descent - fb.ascent;
        float smallH = fs.descent - fs.ascent;
        float gap = dp(6);
        float top = cy - (bigH + gap + smallH) / 2f;
        canvas.drawText(b.toString(), cx, top - fb.ascent, big);
        canvas.drawText(s2.toString(), cx, top + bigH + gap - fs.ascent, small);
    }

    /** Уменьшает шрифт, пока текст не влезет в круг. */
    private void fit(TextPaint p, String text, float start, float min, float maxW) {
        float size = start;
        p.setTextSize(size);
        while (size > min && p.measureText(text) > maxW) {
            size -= sp(0.5f);
            p.setTextSize(size);
        }
    }
}
