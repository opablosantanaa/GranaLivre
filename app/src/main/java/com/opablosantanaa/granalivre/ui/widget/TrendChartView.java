package com.opablosantanaa.granalivre.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.opablosantanaa.granalivre.R;

public class TrendChartView extends View {
    public interface AxisFormatter {
        String format(long value);
    }

    public interface OnSelect {
        void onSelect(int index);
    }

    private long[] first = new long[0];
    private long[] second = new long[0];
    private String[] labels = new String[0];
    private String[] tips = new String[0];
    private boolean[] showLabel = new boolean[0];
    private int selected = -1;
    private AxisFormatter axis = String::valueOf;
    private OnSelect listener;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Path firstPath;
    private Path secondPath;
    private Path areaPath;
    private long cachedMax = 1;
    private boolean cacheDirty = true;
    private int dotShaderColor;
    private final Typeface regular;
    private final Typeface semibold;
    private final float d;

    private int firstColor = 0xFF4ADE80;
    private int secondColor = 0xFFF87171;
    private int textColor = 0xFFE6EEF0;
    private int mutedColor = 0xFF9AABAE;
    private int surfaceColor = 0xFF172124;

    public TrendChartView(Context context) {
        this(context, null);
    }

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        regular = ResourcesCompat.getFont(context, R.font.schibsted_regular);
        semibold = ResourcesCompat.getFont(context, R.font.schibsted_semibold);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.4f * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(7f * d);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeJoin(Paint.Join.ROUND);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(1.5f * d);
        dash.setPathEffect(new DashPathEffect(new float[]{3 * d, 3 * d}, 0));
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(d);
        text.setTextSize(12 * sp);
        text.setTypeface(regular);
        setFocusable(true);
    }

    public void setColors(int firstSeries, int secondSeries, int text, int muted, int surface, int pillColor) {
        firstColor = firstSeries;
        secondColor = secondSeries;
        textColor = text;
        mutedColor = muted;
        surfaceColor = surface;
        pill.setColor(pillColor);
        dots.setColor((text & 0x00FFFFFF) | 0x38000000);
        grid.setColor((text & 0x00FFFFFF) | 0x14000000);
        invalidate();
    }

    public void setAxisFormatter(AxisFormatter formatter) {
        axis = formatter;
    }

    public void setOnSelect(OnSelect listener) {
        this.listener = listener;
    }

    public void setData(long[] first, long[] second, String[] labels, String[] tips, boolean[] showLabel, int selected) {
        this.first = first;
        this.second = second;
        this.labels = labels;
        this.tips = tips;
        this.showLabel = showLabel;
        this.selected = selected;
        cacheDirty = true;
        invalidate();
    }

    private float left() { return 4 * d; }
    private float right() { return getWidth() - 46 * d; }
    private float top() { return 44 * d; }
    private float baseline() { return getHeight() - 28 * d; }

    private float xAt(int i) {
        if (labels.length <= 1) return (left() + right()) / 2f;
        return left() + (right() - left()) * i / (labels.length - 1f);
    }

    private float yAt(long value, long max) {
        float ratio = max <= 0 ? 0f : (float) value / max;
        return baseline() - (baseline() - top()) * ratio;
    }

    private static long niceMax(long max) {
        if (max <= 0) return 100;
        double magnitude = Math.pow(10, Math.floor(Math.log10(max)));
        double[] steps = {1, 2, 2.5, 5, 10};
        for (double step : steps) {
            if (step * magnitude >= max) return Math.round(step * magnitude);
        }
        return Math.round(10 * magnitude);
    }

    private Path curve(long[] values, long max, boolean closed) {
        int n = values.length;
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = xAt(i);
            ys[i] = yAt(values[i], max);
        }
        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            float x0 = i > 0 ? xs[i - 1] : xs[i];
            float y0 = i > 0 ? ys[i - 1] : ys[i];
            float x3 = i + 2 < n ? xs[i + 2] : xs[i + 1];
            float y3 = i + 2 < n ? ys[i + 2] : ys[i + 1];
            float c1y = Math.min(ys[i] + (ys[i + 1] - y0) / 6f, baseline());
            float c2y = Math.min(ys[i + 1] - (y3 - ys[i]) / 6f, baseline());
            path.cubicTo(xs[i] + (xs[i + 1] - x0) / 6f, c1y, xs[i + 1] - (x3 - xs[i]) / 6f, c2y, xs[i + 1], ys[i + 1]);
        }
        if (closed) {
            path.lineTo(xs[n - 1], baseline());
            path.lineTo(xs[0], baseline());
            path.close();
        }
        return path;
    }

    private void drawSeries(Canvas canvas, Path path, int color) {
        glow.setStrokeWidth(9f * d);
        glow.setColor((color & 0x00FFFFFF) | 0x1F000000);
        canvas.drawPath(path, glow);
        glow.setStrokeWidth(5.5f * d);
        glow.setColor((color & 0x00FFFFFF) | 0x38000000);
        canvas.drawPath(path, glow);
        line.setColor(color);
        canvas.drawPath(path, line);
    }

    private void rebuildPaths() {
        cacheDirty = false;
        int n = labels.length;
        long raw = 0;
        for (int i = 0; i < n; i++) raw = Math.max(raw, Math.max(first[i], second[i]));
        cachedMax = niceMax(raw);
        if (n < 2 || getWidth() == 0) {
            firstPath = null;
            secondPath = null;
            areaPath = null;
            return;
        }
        firstPath = curve(first, cachedMax, false);
        secondPath = curve(second, cachedMax, false);
        areaPath = curve(first, cachedMax, true);
        areaPath.op(curve(second, cachedMax, true), Path.Op.UNION);
    }

    private void ensureDotShader() {
        int color = dots.getColor();
        if (dots.getShader() != null && dotShaderColor == color) return;
        int step = Math.max(2, Math.round(9 * d));
        Bitmap tile = Bitmap.createBitmap(step, step, Bitmap.Config.ARGB_8888);
        Canvas tileCanvas = new Canvas(tile);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(color);
        tileCanvas.drawCircle(step / 2f, step / 2f, 0.9f * d, dot);
        dots.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        dotShaderColor = color;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cacheDirty = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = labels.length;
        if (n == 0) return;
        if (cacheDirty) rebuildPaths();
        long max = cachedMax;

        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(regular);
        text.setColor(mutedColor);
        Paint.FontMetrics fm = text.getFontMetrics();
        long[] ticks = {max, max / 2, 0};
        for (long tick : ticks) {
            float y = yAt(tick, max);
            canvas.drawLine(left(), y, right(), y, grid);
            canvas.drawText(axis.format(tick), right() + 8 * d, y - (fm.ascent + fm.descent) / 2f, text);
        }

        if (areaPath != null) {
            ensureDotShader();
            canvas.drawPath(areaPath, dots);
            drawSeries(canvas, secondPath, secondColor);
            drawSeries(canvas, firstPath, firstColor);
        }

        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            if (!showLabel[i]) continue;
            float half = text.measureText(labels[i]) / 2f;
            float x = Math.max(left() + half, Math.min(right() - half, xAt(i)));
            text.setColor(mutedColor);
            canvas.drawText(labels[i], x, getHeight() - 6 * d, text);
        }

        if (selected < 0 || selected >= n) return;
        float sx = xAt(selected);
        float yFirst = yAt(first[selected], max);
        float ySecond = yAt(second[selected], max);
        float topY = Math.min(yFirst, ySecond);
        dash.setColor((textColor & 0x00FFFFFF) | 0x99000000);
        canvas.drawLine(sx, topY, sx, baseline(), dash);
        drawPoint(canvas, sx, ySecond, secondColor);
        drawPoint(canvas, sx, yFirst, firstColor);

        String tip = tips[selected];
        text.setTypeface(semibold);
        text.setColor(textColor);
        float tw = text.measureText(tip) + 22 * d;
        float th = 30 * d;
        float tx = Math.max(left() + tw / 2f, Math.min(right() - tw / 2f, sx));
        float ty = Math.max(th / 2f, topY - th / 2f - 12 * d);
        rect.set(tx - tw / 2f, ty - th / 2f, tx + tw / 2f, ty + th / 2f);
        canvas.drawRoundRect(rect, 9 * d, 9 * d, pill);
        canvas.drawText(tip, tx, ty - (fm.ascent + fm.descent) / 2f, text);
        text.setTypeface(regular);
    }

    private void drawPoint(Canvas canvas, float x, float y, int color) {
        glow.setColor((color & 0x00FFFFFF) | 0x80000000);
        point.setColor(color);
        canvas.drawCircle(x, y, 7 * d, point);
        point.setColor(surfaceColor);
        canvas.drawCircle(x, y, 3.5f * d, point);
        point.setColor(color);
        canvas.drawCircle(x, y, 2.2f * d, point);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (labels.length == 0) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            int nearest = 0;
            float best = Float.MAX_VALUE;
            for (int i = 0; i < labels.length; i++) {
                float dist = Math.abs(xAt(i) - event.getX());
                if (dist < best) {
                    best = dist;
                    nearest = i;
                }
            }
            if (nearest != selected) {
                selected = nearest;
                invalidate();
                if (listener != null) listener.onSelect(selected);
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
