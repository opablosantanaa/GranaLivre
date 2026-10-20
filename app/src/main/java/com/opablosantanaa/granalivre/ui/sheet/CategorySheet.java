package com.opablosantanaa.granalivre.ui.sheet;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

public class CategorySheet extends BaseSheet {
    public static final String RESULT = "category_created";
    public static final String RESULT_ID = "id";
    private static final String ARG_TYPE = "type";
    private static final int[] COLORS = {0xFFE57373, 0xFFF06292, 0xFFFFB74D, 0xFF81C784,
            0xFF4DB6AC, 0xFF64B5F6, 0xFF9575CD, 0xFFA1887F};

    private TransactionType type = TransactionType.DESPESA;
    private int selectedColor;

    public CategorySheet() {
        super(R.layout.sheet_category);
    }

    public static CategorySheet create(TransactionType type) {
        CategorySheet sheet = new CategorySheet();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
        sheet.setArguments(args);
        return sheet;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void paintSwatches(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View swatch = row.getChildAt(i);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(COLORS[i]);
            boolean active = selectedColor == i;
            if (active) dot.setStroke(dp(3), color(R.color.gl_text));
            ((FrameLayout) swatch).getChildAt(0).setBackground(dot);
            swatch.setSelected(active);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(view, R.string.category_title);
        if (getArguments() != null && getArguments().getString(ARG_TYPE) != null) {
            type = TransactionType.valueOf(getArguments().getString(ARG_TYPE));
        }
        SegmentedControl segmented = view.findViewById(R.id.type);
        segmented.setOptions(getString(R.string.entry_type_expense), getString(R.string.entry_type_income));
        segmented.setPalette(color(R.color.gl_line), color(R.color.gl_text_secondary),
                color(R.color.gl_text), color(R.color.gl_bg));
        segmented.select(type == TransactionType.RECEITA ? 1 : 0, false);
        segmented.setOnChange(index -> type = index == 1 ? TransactionType.RECEITA : TransactionType.DESPESA);

        LinearLayout row = view.findViewById(R.id.colors);
        for (int i = 0; i < COLORS.length; i++) {
            FrameLayout swatch = new FrameLayout(requireContext());
            swatch.setForeground(null);
            View dot = new View(requireContext());
            swatch.addView(dot, new FrameLayout.LayoutParams(dp(30), dp(30), android.view.Gravity.CENTER));
            swatch.setContentDescription(getString(R.string.category_color_item, i + 1, COLORS.length));
            int index = i;
            swatch.setOnClickListener(v -> {
                selectedColor = index;
                paintSwatches(row);
            });
            row.addView(swatch, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        paintSwatches(row);

        TextView error = view.findViewById(R.id.error);
        View save = view.findViewById(R.id.save);
        save.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            Category category = new Category();
            category.setUser_id(userId());
            category.setCategory_name(text(view.findViewById(R.id.name)));
            category.setCategory_type(type);
            category.setCategory_color(COLORS[selectedColor]);
            save.setEnabled(false);
            vm().categories.insert(category, new RepositoryCallback<Long>() {
                @Override
                public void onSuccess(Long id) {
                    Bundle result = new Bundle();
                    result.putLong(RESULT_ID, id);
                    getParentFragmentManager().setFragmentResult(RESULT, result);
                    done(R.string.category_saved);
                }

                @Override
                public void onError(Exception e) {
                    fail(error, save, e.getMessage());
                }
            });
        });
    }
}
