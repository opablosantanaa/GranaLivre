package com.opablosantanaa.granalivre.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.util.List;

public final class CategoryFilters {
    private static final long ALL = 0L;

    private CategoryFilters() {}

    public static void populate(Context context, ChipGroup group, List<Category> categories,
                                 long selectedId, java.util.function.LongConsumer onSelected) {
        populate(context, group, categories, TransactionType.DESPESA, selectedId, onSelected);
    }

    public static void populate(Context context, ChipGroup group, List<Category> categories, TransactionType type,
                                 long selectedId, java.util.function.LongConsumer onSelected) {
        group.setOnCheckedStateChangeListener(null);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.removeAllViews();
        add(context, group, context.getString(R.string.category_filter_all), ALL, selectedId == ALL);
        if (categories != null) {
            for (Category category : categories) {
                if (category.getCategory_type() != type || category.getCategory_parent_id() != null) continue;
                add(context, group, category.getCategory_name(), category.getCategory_id(),
                        category.getCategory_id() == selectedId);
            }
        }
        add(context, group, context.getString(R.string.analytics_no_category), -1L, selectedId == -1L);
        group.setOnCheckedStateChangeListener((chips, checkedIds) -> {
            long id = ALL;
            if (!checkedIds.isEmpty()) {
                Object tag = chips.findViewById(checkedIds.get(0)).getTag();
                if (tag instanceof Long) id = (Long) tag;
            }
            onSelected.accept(id);
        });
    }

    private static void add(Context context, ChipGroup group, String label, long id, boolean checked) {
        Chip chip = (Chip) LayoutInflater.from(context).inflate(R.layout.item_chip, group, false);
        chip.setText(label);
        chip.setId(View.generateViewId());
        chip.setTag(id);
        group.addView(chip);
        chip.setChecked(checked);
    }
}
