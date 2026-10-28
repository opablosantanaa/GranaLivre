package com.opablosantanaa.granalivre.ui.sheet;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.domain.SalaryPlanner;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.SalaryPriorities;
import com.opablosantanaa.granalivre.ui.widget.BarView;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SalarySheet extends BaseSheet {
    private YearMonth baseMonth;
    private YearMonth planMonth;
    private SalaryPlanner.Plan plan;
    private Map<Long, Long> spent = new HashMap<>();
    private long income;
    private long savings;

    public SalarySheet() {
        super(R.layout.sheet_salary);
    }

    public static SalarySheet create() {
        return new SalarySheet();
    }

    private String categoryName(Long categoryId) {
        Category category = categoryId == null ? null : MainViewModel.find(vm().categoryList.getValue(), categoryId);
        return category == null ? getString(R.string.salary_uncategorized) : category.getCategory_name();
    }

    private static String monthYear(YearMonth month) {
        return Dates.month(month) + " de " + month.getYear();
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%d%%", Math.round(ratio * 100));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(view, R.string.salary_title);
        YearMonth now = YearMonth.now();
        baseMonth = now.minusMonths(1);
        planMonth = now.plusMonths(1);
        ((TextView) view.findViewById(R.id.intro)).setText(
                getString(R.string.salary_intro, monthYear(baseMonth), monthYear(planMonth)));
        ((TextInputLayout) view.findViewById(R.id.income_layout)).setHint(
                getString(R.string.salary_income, Dates.month(planMonth)));
        TextView error = view.findViewById(R.id.error);
        view.findViewById(R.id.calculate).setOnClickListener(v -> calculate(view, error));
        MaterialButton apply = view.findViewById(R.id.apply);
        apply.setText(getString(R.string.salary_apply, Dates.month(planMonth)));
        apply.setOnClickListener(v -> confirmApply(error, apply));
    }

    private void calculate(View view, TextView error) {
        error.setVisibility(View.GONE);
        View focused = view.findFocus();
        if (focused != null) {
            focused.clearFocus();
            InputMethodManager keyboard = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        long income = Money.parse(text(view.findViewById(R.id.income)));
        long savings = Money.parse(text(view.findViewById(R.id.savings)));
        if (income <= 0) {
            fail(error, null, getString(R.string.salary_error_income));
            return;
        }
        if (savings < 0 || savings > income) {
            fail(error, null, getString(R.string.salary_error_savings));
            return;
        }
        spent = SalaryPlanner.spentByCategory(vm().history.getValue(), vm().categoryList.getValue(), baseMonth);
        this.income = income;
        this.savings = savings;
        replan();
        render(view);
    }

    private void replan() {
        plan = SalaryPlanner.plan(spent, income, savings,
                SalaryPriorities.load(requireContext(), userId(), spent.keySet()));
    }

    private SegmentedControl priorityControl(View view, SalaryPlanner.Line line, String name) {
        SegmentedControl control = new SegmentedControl(requireContext());
        control.setPalette(color(R.color.gl_line), color(R.color.gl_text_secondary), color(R.color.gl_primary),
                color(R.color.gl_on_primary));
        control.setOptions(getString(R.string.salary_priority_keep), getString(R.string.salary_priority_moderate),
                getString(R.string.salary_priority_drastic));
        control.select(line.priority.ordinal(), false);
        control.setContentDescription(getString(R.string.salary_priority_choice, name));
        control.setOnChange(index -> {
            SalaryPriorities.save(requireContext(), userId(), line.categoryId, SalaryPlanner.Priority.values()[index]);
            replan();
            render(view);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        control.setLayoutParams(params);
        return control;
    }

    private void render(View view) {
        TextView summary = view.findViewById(R.id.summary);
        LinearLayout lines = view.findViewById(R.id.lines);
        View apply = view.findViewById(R.id.apply);
        lines.removeAllViews();
        summary.setVisibility(View.VISIBLE);
        String base = Dates.month(baseMonth);
        if (plan.lines.isEmpty()) {
            summary.setText(getString(R.string.salary_no_history, base));
            apply.setVisibility(View.GONE);
            return;
        }
        String spentText = Money.plain(plan.totalSpentCents);
        String incomeText = Money.plain(plan.incomeCents);
        String savingsText = Money.plain(plan.savingsCents);
        String availableText = Money.plain(plan.availableCents);
        String text;
        if (plan.shortfallCents > 0) {
            text = getString(R.string.salary_shortfall, base, spentText, incomeText, savingsText, availableText,
                    Money.plain(plan.shortfallCents));
        } else if (plan.cutCents > 0) {
            text = getString(R.string.salary_cut, base, spentText, incomeText, savingsText, availableText,
                    Money.plain(plan.cutCents));
        } else {
            text = getString(R.string.salary_fits, base, spentText, incomeText, savingsText, availableText,
                    Money.plain(plan.leftoverCents));
        }
        summary.setText(text + "\n\n" + getString(R.string.salary_priority_hint));
        int secondary = color(R.color.gl_text_secondary);
        for (SalaryPlanner.Line line : plan.lines) {
            View row = getLayoutInflater().inflate(R.layout.item_progress, lines, false);
            String name = categoryName(line.categoryId);
            String value = Money.plain(line.suggestedCents);
            String caption = getString(R.string.salary_line_caption, Money.plain(line.spentCents),
                    percent(line.incomeShare));
            String note = line.cutCents > 0 ? getString(R.string.salary_line_cut, Money.plain(line.cutCents))
                    : getString(R.string.salary_line_keep);
            ((TextView) row.findViewById(R.id.title)).setText(name);
            ((TextView) row.findViewById(R.id.value)).setText(value);
            BarView bar = row.findViewById(R.id.bar);
            bar.setColors(color(R.color.gl_line), line.cutCents > 0 ? color(R.color.gl_accent) : color(R.color.gl_primary));
            bar.setProgress(line.spentCents == 0 ? 0f : (float) line.suggestedCents / line.spentCents);
            ((TextView) row.findViewById(R.id.caption)).setText(caption);
            TextView noteView = row.findViewById(R.id.note);
            noteView.setText(note);
            noteView.setTextColor(line.cutCents > 0 ? color(R.color.gl_expense) : secondary);
            row.setForeground(null);
            ((LinearLayout) row).addView(priorityControl(view, line, name));
            lines.addView(row);
        }
        boolean anyLimit = false;
        for (SalaryPlanner.Line line : plan.lines) {
            if (line.categoryId != null && line.suggestedCents > 0) anyLimit = true;
        }
        apply.setVisibility(anyLimit ? View.VISIBLE : View.GONE);
        apply.setEnabled(true);
    }

    private void confirmApply(TextView error, View apply) {
        if (plan == null) return;
        Map<Long, Long> limits = new HashMap<>();
        for (SalaryPlanner.Line line : plan.lines) {
            if (line.categoryId != null && line.suggestedCents > 0) limits.put(line.categoryId, line.suggestedCents);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.salary_apply, Dates.month(planMonth)))
                .setMessage(getString(R.string.salary_apply_confirm, Dates.month(planMonth)))
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.budget_save, (d, w) -> {
                    error.setVisibility(View.GONE);
                    apply.setEnabled(false);
                    vm().budgets.saveAll(userId(), planMonth, limits, new RepositoryCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer result) {
                            done(R.string.salary_applied);
                        }

                        @Override
                        public void onError(Exception e) {
                            fail(error, apply, e.getMessage());
                        }
                    });
                })
                .show();
    }
}
