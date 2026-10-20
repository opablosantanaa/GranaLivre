package com.opablosantanaa.granalivre.ui.sheet;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;

import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

public class BudgetSheet extends BaseSheet {
    private static final String ARG_CATEGORY = "category_id";

    public BudgetSheet() {
        super(R.layout.sheet_budget);
    }

    public static BudgetSheet create(long categoryId) {
        BudgetSheet sheet = new BudgetSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_CATEGORY, categoryId);
        sheet.setArguments(args);
        return sheet;
    }

    private Budget existing(long categoryId) {
        List<Budget> budgets = vm().budgetList.getValue();
        if (budgets == null) return null;
        for (Budget b : budgets) if (b.getCategory_id() == categoryId) return b;
        return null;
    }

    private void prefill(View view, long categoryId) {
        Category category = MainViewModel.find(vm().categoryList.getValue(), categoryId);
        TextInputLayout layout = view.findViewById(R.id.limit_layout);
        layout.setHint(category == null ? getString(R.string.card_limit)
                : getString(R.string.budget_limit, category.getCategory_name()));
        Budget budget = existing(categoryId);
        TextInputEditText field = view.findViewById(R.id.limit);
        if (budget != null) {
            long cents = budget.getBudget_limit_cents();
            field.setText(String.format(Locale.ROOT, "%d,%02d", cents / 100, cents % 100));
        } else {
            field.setText("");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(view, getString(R.string.budget_title, Dates.month(YearMonth.now())));
        long preselected = requireArguments().getLong(ARG_CATEGORY, 0);
        ChipGroup group = view.findViewById(R.id.categories);
        List<Category> categories = vm().categoryList.getValue();
        if (categories != null) {
            for (Category c : categories) {
                if (c.getCategory_type() != TransactionType.DESPESA || c.getCategory_parent_id() != null) continue;
                Chip chip = chip(group, c.getCategory_name(), c.getCategory_id());
                if (c.getCategory_id() == preselected) chip.setChecked(true);
            }
        }
        group.setOnCheckedStateChangeListener((g, ids) -> prefill(view, checkedTag(group)));
        if (preselected > 0) prefill(view, preselected);

        TextView error = view.findViewById(R.id.error);
        View save = view.findViewById(R.id.save);
        save.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            long categoryId = checkedTag(group);
            if (categoryId <= 0) {
                fail(error, null, getString(R.string.error_category));
                return;
            }
            long limit = Money.parse(text(view.findViewById(R.id.limit)));
            if (limit <= 0) {
                fail(error, null, getString(R.string.error_amount));
                return;
            }
            Budget budget = new Budget();
            budget.setUser_id(userId());
            budget.setCategory_id(categoryId);
            budget.setBudget_month(YearMonth.now());
            budget.setBudget_limit_cents(limit);
            save.setEnabled(false);
            vm().budgets.save(budget, new RepositoryCallback<Long>() {
                @Override
                public void onSuccess(Long result) {
                    done(R.string.budget_saved);
                }

                @Override
                public void onError(Exception e) {
                    fail(error, save, e.getMessage());
                }
            });
        });
    }
}
