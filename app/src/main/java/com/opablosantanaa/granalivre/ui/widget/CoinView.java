package com.opablosantanaa.granalivre.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.opablosantanaa.granalivre.ui.FinanceAdvisor;

/** Mascote do assessor: uma moedinha em pixel art que pisca, pula ao toque e muda de cara conforme a dica. */
public class CoinView extends View {
    private static final String[] BODY = {
            "....oooooo....",
            "..oohhhhhhoo..",
            ".ohhhgggggsso.",
            ".ohggggggggso.",
            "ohggggggggggso",
            "ohggggggggggso",
            "ohggggggggggso",
            "ohggggggggggso",
            "ohggggggggggso",
            "ohggggggggggso",
            ".osgggggggsso.",
            ".ossggggsssso.",
            "..oossssssoo..",
            "....oooooo....",
            "...oo....oo...",
            "..ooo....ooo..",
    };
    private static final int COLS = 14;
    private static final int ROWS = BODY.length;
    private static final long BLINK_EVERY_MS = 3800;
    private static final long BLINK_FOR_MS = 140;

    private final Paint paint = new Paint();
    private FinanceAdvisor.Mood mood = FinanceAdvisor.Mood.INFO;
    private boolean blinking;
    private boolean alertDot;
    private float hop;

    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            blinking = !blinking;
            invalidate();
            postDelayed(this, blinking ? BLINK_FOR_MS : BLINK_EVERY_MS);
        }
    };

    public CoinView(Context context) {
        this(context, null);
    }

    public CoinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(false);
    }

    public void setMood(FinanceAdvisor.Mood mood, boolean alertDot) {
        if (this.mood == mood && this.alertDot == alertDot) return;
        this.mood = mood;
        this.alertDot = alertDot;
        invalidate();
    }

    public void hop() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f, 0f);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            hop = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(blink, BLINK_EVERY_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(blink);
        super.onDetachedFromWindow();
    }

    private static int color(char c) {
        switch (c) {
            case 'o': return 0xFF6B4A00;
            case 'h': return 0xFFFFE69A;
            case 'g': return 0xFFFFC83D;
            case 's': return 0xFFE09A1B;
            case 'e': return 0xFF1A2B2E;
            case 'r': return 0xFFFF8A80;
            default: return 0;
        }
    }

    private void pixel(Canvas canvas, float left, float top, float size, int col, int row, int color) {
        paint.setColor(color);
        canvas.drawRect(left + col * size, top + row * size, left + (col + 1) * size, top + (row + 1) * size, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min((getWidth() - getPaddingLeft() - getPaddingRight()) / (float) COLS,
                (getHeight() - getPaddingTop() - getPaddingBottom()) / (float) (ROWS + 2));
        size = (float) Math.floor(size);
        if (size <= 0) return;
        float left = (getWidth() - size * COLS) / 2f;
        float top = (getHeight() - size * ROWS) / 2f + size - hop * size * 2;
        for (int row = 0; row < ROWS; row++) {
            String line = BODY[row];
            for (int col = 0; col < COLS; col++) {
                int c = color(line.charAt(col));
                if (c != 0) pixel(canvas, left, top, size, col, row, c);
            }
        }
        int eye = color('e');
        if (blinking) {
            for (int col : new int[]{4, 5, 8, 9}) pixel(canvas, left, top, size, col, 6, eye);
        } else {
            for (int row = 5; row <= 6; row++) {
                for (int col : new int[]{4, 5, 8, 9}) pixel(canvas, left, top, size, col, row, eye);
            }
            pixel(canvas, left, top, size, 4, 5, color('h'));
            pixel(canvas, left, top, size, 8, 5, color('h'));
        }
        switch (mood) {
            case PRAISE:
                pixel(canvas, left, top, size, 4, 8, eye);
                pixel(canvas, left, top, size, 9, 8, eye);
                for (int col = 5; col <= 8; col++) pixel(canvas, left, top, size, col, 9, eye);
                pixel(canvas, left, top, size, 2, 7, color('r'));
                pixel(canvas, left, top, size, 11, 7, color('r'));
                break;
            case ALERT:
            case WARN:
                for (int col = 5; col <= 8; col++) pixel(canvas, left, top, size, col, 8, eye);
                pixel(canvas, left, top, size, 4, 9, eye);
                pixel(canvas, left, top, size, 9, 9, eye);
                break;
            default:
                for (int col = 6; col <= 7; col++) pixel(canvas, left, top, size, col, 9, eye);
                pixel(canvas, left, top, size, 5, 8, eye);
                pixel(canvas, left, top, size, 8, 8, eye);
                break;
        }
        if (alertDot) {
            paint.setAntiAlias(true);
            paint.setColor(0xFFE5484D);
            float r = size * 1.6f;
            canvas.drawCircle(left + size * COLS - r * 0.4f, top + r * 0.6f, r, paint);
            paint.setAntiAlias(false);
        }
    }
}
