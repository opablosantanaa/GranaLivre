package com.opablosantanaa.granalivre.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.opablosantanaa.granalivre.R;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HeatmapView extends View {
    public interface OnDay {
        void onDay(LocalDate day, long net);
    }

    private static final int LEVELS = 4;
    private static final Locale BR = new Locale("pt", "BR");
    private static final String[] ROW_LABELS = {"", "seg", "", "qua", "", "sex", ""};

    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;

    private Map<LocalDate, Long> net = new HashMap<>();
    private long[] positiveSteps = new long[0];
    private long[] negativeSteps = new long[0];
    private int emptyColor = 0x1F1A2B2E;
    private int incomeColor = 0xFF22A06B;
    private int expenseColor = 0xFFE5484D;
    private LocalDate selected;
    private OnDay listener;

    private int columns;
    private float size;
    private float gap;
    private float left;
    private float top;
    private LocalDate firstDay;

    public HeatmapView(Context context) {
        this(context, null);
    }

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        label.setTextSize(10 * sp);
        label.setTypeface(ResourcesCompat.getFont(context, R.font.jakarta_regular));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * d);
        setFocusable(true);
    }

    public void setColors(int empty, int income, int expense, int text) {
        emptyColor = empty;
        incomeColor = income;
        expenseColor = expense;
        label.setColor(text);
        ring.setColor(text);
        invalidate();
    }

    public void setOnDay(OnDay listener) {
        this.listener = listener;
    }

    public void setData(Map<LocalDate, Long> values) {
        net = values;
        List<Long> positives = new ArrayList<>();
        List<Long> negatives = new ArrayList<>();
        for (long v : values.values()) {
            if (v > 0) positives.add(v);
            else if (v < 0) negatives.add(-v);
        }
        positiveSteps = steps(positives);
        negativeSteps = steps(negatives);
        invalidate();
    }

    private static long[] steps(List<Long> values) {
        long[] out = new long[LEVELS - 1];
        if (values.isEmpty()) return out;
        Collections.sort(values);
        for (int i = 1; i < LEVELS; i++) {
            out[i - 1] = values.get(Math.min(values.size() - 1, values.size() * i / LEVELS));
        }
        return out;
    }

    private static int level(long value, long[] steps) {
        int level = 1;
        for (long step : steps) if (value > step) level++;
        return Math.min(level, LEVELS);
    }

    public int colorFor(long value) {
        if (value == 0) return emptyColor;
        boolean income = value > 0;
        int lvl = level(Math.abs(value), income ? positiveSteps : negativeSteps);
        int base = income ? incomeColor : expenseColor;
        int alpha = Math.round(255 * (0.3f + 0.7f * (lvl - 1) / (LEVELS - 1f)));
        return (base & 0x00FFFFFF) | (alpha << 24);
    }

    public static int legendColor(int base, int level) {
        int alpha = Math.round(255 * (0.3f + 0.7f * (level - 1) / (LEVELS - 1f)));
        return (base & 0x00FFFFFF) | (alpha << 24);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        float gridGap = 3 * d;
        float gridLeft = 26 * d;
        int gridColumns = Math.max(1, (int) ((width - gridLeft + gridGap) / (16 * d + gridGap)));
        float gridSize = (width - gridLeft - gridGap * (gridColumns - 1)) / gridColumns;
        gridSize = Math.min(gridSize, 16 * d);
        int height = Math.round(18 * d + 7 * gridSize + 6 * gridGap + 4 * d);
        setMeasuredDimension(width, height);
    }

    private void layoutGrid() {
        left = 26 * d;
        top = 18 * d;
        gap = 3 * d;
        size = 13 * d;
        columns = Math.max(1, (int) ((getWidth() - left + gap) / (size + gap)));
        size = (getWidth() - left - gap * (columns - 1)) / columns;
        size = Math.min(size, 16 * d);
        LocalDate today = LocalDate.now();
        int todayRow = today.getDayOfWeek().getValue() % 7;
        firstDay = today.minusDays(todayRow).minusWeeks(columns - 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        layoutGrid();
        LocalDate today = LocalDate.now();
        float radius = 3 * d;
        Paint.FontMetrics fm = label.getFontMetrics();

        label.setTextAlign(Paint.Align.LEFT);
        for (int row = 0; row < 7; row++) {
            if (ROW_LABELS[row].isEmpty()) continue;
            float cy = top + row * (size + gap) + size / 2f;
            canvas.drawText(ROW_LABELS[row], 0, cy - (fm.ascent + fm.descent) / 2f, label);
        }

        int lastMonth = -1;
        float lastLabelEnd = -Float.MAX_VALUE;
        for (int col = 0; col < columns; col++) {
            LocalDate weekStart = firstDay.plusWeeks(col);
            float x = left + col * (size + gap);
            LocalDate monthMarker = null;
            for (int row = 0; row < 7; row++) {
                LocalDate day = weekStart.plusDays(row);
                if (day.getDayOfMonth() == 1) monthMarker = day;
            }
            if (col == 0) monthMarker = weekStart.plusDays(6);
            if (monthMarker != null && monthMarker.getMonthValue() != lastMonth && x > lastLabelEnd) {
                String name = monthMarker.getMonth().getDisplayName(TextStyle.SHORT, BR).replace(".", "");
                canvas.drawText(name, x, top - 6 * d, label);
                lastMonth = monthMarker.getMonthValue();
                lastLabelEnd = x + label.measureText(name) + 4 * d;
            }
            for (int row = 0; row < 7; row++) {
                LocalDate day = weekStart.plusDays(row);
                if (day.isAfter(today)) continue;
                Long value = net.get(day);
                cell.setColor(colorFor(value == null ? 0 : value));
                float y = top + row * (size + gap);
                rect.set(x, y, x + size, y + size);
                canvas.drawRoundRect(rect, radius, radius, cell);
                if (day.equals(selected)) {
                    rect.inset(-1.5f * d, -1.5f * d);
                    canvas.drawRoundRect(rect, radius + 1.5f * d, radius + 1.5f * d, ring);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (firstDay == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
        int col = (int) ((event.getX() - left) / (size + gap));
        int row = (int) ((event.getY() - top) / (size + gap));
        if (col < 0 || col >= columns || row < 0 || row > 6) return true;
        LocalDate day = firstDay.plusWeeks(col).plusDays(row);
        if (day.isAfter(LocalDate.now())) return true;
        selected = day;
        invalidate();
        Long value = net.get(day);
        if (listener != null) listener.onDay(day, value == null ? 0 : value);
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
