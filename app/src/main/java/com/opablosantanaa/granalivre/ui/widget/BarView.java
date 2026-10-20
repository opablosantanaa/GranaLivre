package com.opablosantanaa.granalivre.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class BarView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float progress;

    public BarView(Context context) {
        this(context, null);
    }

    public BarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setColor(0x1F1A2B2E);
        fill.setColor(0xFF0B6E69);
    }

    public void setColors(int trackColor, int fillColor) {
        track.setColor(trackColor);
        fill.setColor(fillColor);
        invalidate();
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float radius = h / 2f;
        rect.set(0, 0, getWidth(), h);
        canvas.drawRoundRect(rect, radius, radius, track);
        if (progress <= 0f) return;
        rect.set(0, 0, Math.max(h, getWidth() * progress), h);
        canvas.drawRoundRect(rect, radius, radius, fill);
    }
}
