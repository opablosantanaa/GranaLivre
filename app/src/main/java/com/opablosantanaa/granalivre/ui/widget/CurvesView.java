package com.opablosantanaa.granalivre.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class CurvesView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public CurvesView(Context context) {
        this(context, null);
    }

    public CurvesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density);
        paint.setColor(0x8CFFFFFF);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        path.reset();
        path.moveTo(w * 0.55f, 0);
        path.cubicTo(w * 0.62f, h * 0.18f, w * 0.18f, h * 0.22f, w * 0.12f, h * 0.38f);
        path.cubicTo(w * 0.08f, h * 0.48f, w * 0.02f, h * 0.52f, -w * 0.02f, h * 0.56f);
        canvas.drawPath(path, paint);

        path.reset();
        path.moveTo(w * 1.02f, h * 0.6f);
        path.cubicTo(w * 0.7f, h * 0.62f, w * 0.3f, h * 0.6f, w * 0.34f, h * 0.53f);
        path.cubicTo(w * 0.38f, h * 0.46f, w * 0.62f, h * 0.5f, w * 0.66f, h * 0.58f);
        path.cubicTo(w * 0.72f, h * 0.68f, w * 0.9f, h * 0.7f, w * 1.02f, h * 0.68f);
        canvas.drawPath(path, paint);
    }
}
