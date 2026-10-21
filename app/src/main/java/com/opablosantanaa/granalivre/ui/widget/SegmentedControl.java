package com.opablosantanaa.granalivre.ui.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.opablosantanaa.granalivre.R;

public class SegmentedControl extends LinearLayout {
    public interface OnChange {
        void onChange(int index);
    }

    private int selected;
    private int textColor = 0xFFFFFFFF;
    private int selectedTextColor = 0xFF1A2B2E;
    private int thumbColor = 0xFFFFFFFF;
    private OnChange listener;

    public SegmentedControl(Context context) {
        this(context, null);
    }

    public SegmentedControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        int pad = dp(3);
        setPadding(pad, pad, pad, pad);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void setPalette(int trackStroke, int text, int thumb, int selectedText) {
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(999));
        track.setStroke(dp(1), trackStroke);
        setBackground(track);
        textColor = text;
        thumbColor = thumb;
        selectedTextColor = selectedText;
        refresh();
    }

    public void setOptions(String... options) {
        removeAllViews();
        for (int i = 0; i < options.length; i++) {
            TextView item = new TextView(getContext());
            item.setText(options[i]);
            item.setGravity(Gravity.CENTER);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, options.length > 3 ? 12 : 13);
            item.setMaxLines(1);
            item.setPadding(dp(4), 0, dp(4), 0);
            if (options.length > 3) {
                item.setAutoSizeTextTypeUniformWithConfiguration(10, 12, 1, TypedValue.COMPLEX_UNIT_SP);
            }
            item.setTypeface(ResourcesCompat.getFont(getContext(), R.font.jakarta_medium));
            item.setIncludeFontPadding(false);
            item.setMinHeight(dp(34));
            int index = i;
            item.setOnClickListener(v -> select(index, true));
            addView(item, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        refresh();
    }

    public void setOnChange(OnChange listener) {
        this.listener = listener;
    }

    public int getSelected() {
        return selected;
    }

    public void select(int index, boolean notify) {
        selected = index;
        refresh();
        if (notify && listener != null) listener.onChange(index);
    }

    private void refresh() {
        for (int i = 0; i < getChildCount(); i++) {
            TextView item = (TextView) getChildAt(i);
            boolean active = i == selected;
            item.setSelected(active);
            item.setTextColor(active ? selectedTextColor : textColor);
            if (active) {
                GradientDrawable thumb = new GradientDrawable();
                thumb.setCornerRadius(dp(999));
                thumb.setColor(thumbColor);
                item.setBackground(thumb);
            } else {
                item.setBackground(null);
            }
        }
    }
}
