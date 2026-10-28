package com.opablosantanaa.granalivre.ui.sheet;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BudgetSheet extends BaseSheet {
    private static final String ARG_CATEGORY = "category_id";
    private static final String ARG_MONTH = "month";

    private YearMonth month;
    private List<Budget> monthBudgets = new ArrayList<>();

    public BudgetSheet() {
        super(R.layout.sheet_budget);
    }

    public static BudgetSheet create(long categoryId) {
        return create(categoryId, YearMonth.now());
    }

    public static BudgetSheet create(long categoryId, YearMonth month) {
        BudgetSheet sheet = new BudgetSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_CATEGORY, categoryId);
        args.putString(ARG_MONTH, month.toString());
        sheet.setArguments(args);
        return sheet;
    }

    private Budget existing(long categoryId) {
        for (Budget b : monthBudgets) if (b.getCategory_id() == categoryId) return b;
        return null;
    }

    private String categoryName(long categoryId) {
        Category category = MainViewModel.find(vm().categoryList.getValue(), categoryId);
        return category == null ? "" : category.getCategory_name();
    }

    private void prefill(View view, long categoryId) {
        TextInputLayout layout = view.findViewById(R.id.limit_layout);
        String name = categoryName(categoryId);
        layout.setHint(name.isEmpty() ? getString(R.string.card_limit) : getString(R.string.budget_limit, name));
        Budget budget = existing(categoryId);
        TextInputEditText field = view.findViewById(R.id.limit);
        if (budget != null) {
            long cents = budget.getBudget_limit_cents();
            field.setText(String.format(Locale.ROOT, "%d,%02d", cents / 100, cents % 100));
        } else {
            field.setText("");
        }
        view.findViewById(R.id.delete).setVisibility(budget == null ? View.GONE : View.VISIBLE);
    }

    private void renderMonthLimits(View view) {
        TextView summary = view.findViewById(R.id.month_limits);
        String monthName = Dates.month(month);
        if (monthBudgets.isEmpty()) {
            summary.setText(getString(R.string.budget_month_none, monthName));
            return;
        }
        StringBuilder list = new StringBuilder();
        for (Budget b : monthBudgets) {
            if (list.length() > 0) list.append(", ");
            list.append(categoryName(b.getCategory_id())).append(' ').append(Money.whole(b.getBudget_limit_cents()));
        }
        summary.setText(getString(R.string.budget_month_limits, monthName, list));
    }

    private void loadMonth(View view, ChipGroup group) {
        ((TextView) view.findViewById(R.id.month)).setText(Dates.monthYear(month));
        YearMonth requested = month;
        vm().budgets.findByMonth(userId(), requested, new RepositoryCallback<List<Budget>>() {
            @Override
            public void onSuccess(List<Budget> result) {
                if (!isAdded() || !requested.equals(month)) return;
                monthBudgets = result;
                renderMonthLimits(view);
                prefill(view, checkedTag(group));
            }

            @Override
            public void onError(Exception e) {
                if (isAdded()) fail(view.findViewById(R.id.error), null, e.getMessage());
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(view, getString(R.string.budget_title_monthly));
        long preselected = requireArguments().getLong(ARG_CATEGORY, 0);
        String monthArg = requireArguments().getString(ARG_MONTH);
        month = monthArg == null ? YearMonth.now() : YearMonth.parse(monthArg);
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
        view.findViewById(R.id.prev).setOnClickListener(v -> {
            month = month.minusMonths(1);
            loadMonth(view, group);
        });
        view.findViewById(R.id.next).setOnClickListener(v -> {
            month = month.plusMonths(1);
            loadMonth(view, group);
        });
        loadMonth(view, group);

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
            budget.setBudget_month(month);
            budget.setBudget_limit_cents(limit);
            save.setEnabled(false);
            vm().budgets.save(budget, callback(error, save, R.string.budget_saved));
        });

        View delete = view.findViewById(R.id.delete);
        delete.setOnClickListener(v -> {
            Budget budget = existing(checkedTag(group));
            if (budget == null) return;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.budget_delete)
                    .setMessage(getString(R.string.budget_delete_confirm, categoryName(budget.getCategory_id()),
                            Dates.month(budget.getBudget_month())))
                    .setNegativeButton(R.string.back, null)
                    .setPositiveButton(R.string.budget_delete, (d, w) -> {
                        error.setVisibility(View.GONE);
                        delete.setEnabled(false);
                        vm().budgets.delete(budget, callback(error, delete, R.string.budget_deleted));
                    })
                    .show();
        });
    }

    private <T> RepositoryCallback<T> callback(TextView error, View button, int message) {
        return new RepositoryCallback<T>() {
            @Override
            public void onSuccess(T result) {
                done(message);
            }

            @Override
            public void onError(Exception e) {
                fail(error, button, e.getMessage());
            }
        };
    }
}
