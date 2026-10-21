package com.opablosantanaa.granalivre.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.opablosantanaa.granalivre.R;

public class PairBarsView extends View {
    public interface OnSelect {
        void onSelect(int index);
    }

    private long[] income = new long[0];
    private long[] expense = new long[0];
    private String[] labels = new String[0];
    private int selected = -1;
    private OnSelect listener;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface regular;
    private final Typeface semibold;
    private int textColor = 0xFF1A2B2E;
    private int mutedColor = 0xFF3F5558;
    private final float d;

    public PairBarsView(Context context) {
        this(context, null);
    }

    public PairBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        regular = ResourcesCompat.getFont(context, R.font.jakarta_regular);
        semibold = ResourcesCompat.getFont(context, R.font.jakarta_semibold);
        label.setTextSize(12 * sp);
        label.setTextAlign(Paint.Align.CENTER);
        setFocusable(true);
    }

    public void setColors(int trackColor, int incomeColor, int expenseColor, int text, int muted) {
        track.setColor(trackColor);
        incomePaint.setColor(incomeColor);
        expensePaint.setColor(expenseColor);
        textColor = text;
        mutedColor = muted;
        invalidate();
    }

    public void setOnSelect(OnSelect listener) {
        this.listener = listener;
    }

    public void setData(long[] income, long[] expense, String[] labels, int selected) {
        this.income = income;
        this.expense = expense;
        this.labels = labels;
        this.selected = selected;
        invalidate();
    }

    private float columnWidth() {
        return labels.length == 0 ? 0 : (float) getWidth() / labels.length;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (labels.length == 0) return;
        long max = 1;
        for (int i = 0; i < labels.length; i++) max = Math.max(max, Math.max(income[i], expense[i]));
        float labelArea = 24 * d;
        float bottom = getHeight() - labelArea;
        float topY = 4 * d;
        float barWidth = Math.min(14 * d, columnWidth() / 3.2f);
        float pairGap = 3 * d;
        float trackWidth = barWidth * 2 + pairGap + 8 * d;
        float radius = barWidth / 2f;

        for (int i = 0; i < labels.length; i++) {
            float cx = columnWidth() * (i + 0.5f);
            boolean active = i == selected;
            if (active) {
                rect.set(cx - trackWidth / 2f, topY, cx + trackWidth / 2f, bottom);
                canvas.drawRoundRect(rect, trackWidth / 2f, trackWidth / 2f, track);
            }
            float usable = bottom - topY - 8 * d;
            float hIn = Math.max(barWidth, usable * income[i] / max);
            float hOut = Math.max(barWidth, usable * expense[i] / max);
            float base = bottom - 4 * d;
            rect.set(cx - pairGap / 2f - barWidth, base - hIn, cx - pairGap / 2f, base);
            incomePaint.setAlpha(income[i] == 0 ? 70 : 255);
            canvas.drawRoundRect(rect, radius, radius, incomePaint);
            rect.set(cx + pairGap / 2f, base - hOut, cx + pairGap / 2f + barWidth, base);
            expensePaint.setAlpha(expense[i] == 0 ? 70 : 255);
            canvas.drawRoundRect(rect, radius, radius, expensePaint);

            label.setTypeface(active ? semibold : regular);
            label.setColor(active ? textColor : mutedColor);
            canvas.drawText(labels[i], cx, getHeight() - 6 * d, label);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (labels.length == 0) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            int index = Math.max(0, Math.min(labels.length - 1, (int) (event.getX() / columnWidth())));
            if (index != selected) {
                selected = index;
                invalidate();
                if (listener != null) listener.onSelect(index);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
