package com.opablosantanaa.granalivre.ui.screen;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.ui.CategoryStyle;
import com.opablosantanaa.granalivre.ui.CategoryFilters;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.widget.BarView;
import com.opablosantanaa.granalivre.ui.widget.TrendChartView;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsScreen extends Screen {
    private static final int WEEK = 0;
    private static final int MONTH = 1;
    private static final int YEAR = 2;

    private int period = MONTH;
    private int chartSelected = -1;
    private long categoryFilter;
    private static final int CHART_INCOME = 0xFF4ADE80;
    private static final int CHART_EXPENSE = 0xFFF87171;
    private long[] chartIncome = new long[0];
    private long[] chartExpense = new long[0];

    public AnalyticsScreen() {
        super(R.layout.screen_analytics);
    }

    @Override
    public int tab() {
        return App.TAB_ANALYTICS;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        padTop(view.findViewById(R.id.header), 8);
        padBottomForTabs(view.findViewById(R.id.sheet), 8);

        SegmentedControl segmented = view.findViewById(R.id.period);
        segmented.setOptions(getString(R.string.analytics_week_short), getString(R.string.analytics_month_short),
                getString(R.string.analytics_year_short));
        segmented.setPalette(color(R.color.gl_ink_line), color(R.color.gl_on_ink_secondary),
                0xFF2E3D41, color(R.color.gl_on_ink));
        segmented.select(period, false);
        segmented.setOnChange(index -> {
            period = index;
            chartSelected = -1;
            render(view);
        });

        TrendChartView chart = view.findViewById(R.id.chart);
        chart.setColors(CHART_INCOME, CHART_EXPENSE, color(R.color.gl_on_ink), color(R.color.gl_on_ink_secondary),
                color(R.color.gl_ink), 0xFF2E3D41);
        chart.setAxisFormatter(Money::compact);
        chart.setOnSelect(index -> {
            chartSelected = index;
            renderLegend(view);
        });
        view.findViewById(R.id.legend_income_line).setBackgroundTintList(ColorStateList.valueOf(CHART_INCOME));
        view.findViewById(R.id.legend_expense_line).setBackgroundTintList(ColorStateList.valueOf(CHART_EXPENSE));

        MainViewModel vm = vm();
        com.google.android.material.chip.ChipGroup categoryChips = view.findViewById(R.id.category_filter);
        vm.history.observe(getViewLifecycleOwner(), v -> {
            chartSelected = -1;
            render(view);
        });
        vm.categoryList.observe(getViewLifecycleOwner(), categories -> {
            CategoryFilters.populate(requireContext(), categoryChips, categories, categoryFilter, id -> {
                categoryFilter = id;
                chartSelected = -1;
                render(view);
            });
            render(view);
        });
        vm.budgetList.observe(getViewLifecycleOwner(), v -> render(view));
    }

    private void renderLegend(View view) {
        if (chartSelected < 0 || chartSelected >= chartIncome.length) return;
        ((TextView) view.findViewById(R.id.legend_income)).setText(
                getString(R.string.analytics_legend_income, Money.whole(chartIncome[chartSelected])));
        ((TextView) view.findViewById(R.id.legend_expense)).setText(
                getString(R.string.analytics_legend_expense, Money.whole(chartExpense[chartSelected])));
    }

    private static boolean inRange(FinancialTransaction t, LocalDate from, LocalDate to) {
        LocalDate d = t.getTransaction_date();
        return !d.isBefore(from) && !d.isAfter(to);
    }

    private void add(FinancialTransaction t, int index, long[] expenses, long[] incomes) {
        if (index < 0 || index >= expenses.length) return;
        if (categoryFilter != 0) {
            if (t.getTransaction_type() != TransactionType.DESPESA
                    || !matchesCategory(t.getCategory_id(), categoryFilter)) return;
            expenses[index] += t.getTransaction_amount_cents();
        } else if (t.getTransaction_type() == TransactionType.RECEITA) {
            incomes[index] += t.getTransaction_amount_cents();
        } else {
            expenses[index] += t.getTransaction_amount_cents();
        }
    }

    private static boolean matchesCategory(Long transactionCategory, long selectedCategory) {
        return selectedCategory == -1L ? transactionCategory == null
                : transactionCategory != null && transactionCategory == selectedCategory;
    }

    private static boolean isExpense(FinancialTransaction t, LocalDate from, LocalDate to) {
        LocalDate d = t.getTransaction_date();
        return t.getTransaction_type() == TransactionType.DESPESA && !d.isBefore(from) && !d.isAfter(to);
    }

    private void render(View view) {
        MainViewModel vm = vm();
        List<FinancialTransaction> history = vm.history.getValue();
        if (history == null) history = new ArrayList<>();
        LocalDate today = LocalDate.now();

        long[] values;
        long[] incomes;
        String[] labels;
        boolean[] show;
        LocalDate from;
        if (period == WEEK) {
            from = today.minusDays(6);
            values = new long[7];
            incomes = new long[7];
            labels = new String[7];
            show = new boolean[7];
            for (int i = 0; i < 7; i++) {
                LocalDate day = from.plusDays(i);
                labels[i] = i == 6 ? getString(R.string.analytics_today) : Dates.weekdayShort(day);
                show[i] = true;
            }
            for (FinancialTransaction t : history) {
                if (inRange(t, from, today)) {
                    int index = (int) (t.getTransaction_date().toEpochDay() - from.toEpochDay());
                    add(t, index, values, incomes);
                }
            }
        } else if (period == MONTH) {
            from = today.withDayOfMonth(1);
            int days = today.getDayOfMonth();
            values = new long[days];
            incomes = new long[days];
            labels = new String[days];
            show = new boolean[days];
            String month = Dates.monthShort(YearMonth.from(today));
            for (int i = 0; i < days; i++) {
                int day = i + 1;
                boolean last = i == days - 1;
                labels[i] = last ? getString(R.string.analytics_today) : day + " " + month;
                show[i] = last || ((day == 1 || day % 5 == 0) && days - day >= 3);
            }
            for (FinancialTransaction t : history) {
                if (inRange(t, from, today)) {
                    add(t, t.getTransaction_date().getDayOfMonth() - 1, values, incomes);
                }
            }
        } else {
            YearMonth first = YearMonth.from(today).minusMonths(11);
            from = first.atDay(1);
            values = new long[12];
            incomes = new long[12];
            labels = new String[12];
            show = new boolean[12];
            for (int i = 0; i < 12; i++) {
                labels[i] = Dates.monthShort(first.plusMonths(i));
                show[i] = i % 2 == 1;
            }
            for (FinancialTransaction t : history) {
                if (inRange(t, from, today)) {
                    int index = (int) (YearMonth.from(t.getTransaction_date()).getMonthValue()
                            - first.getMonthValue() + 12 * (t.getTransaction_date().getYear() - first.getYear()));
                    if (index >= 0 && index < 12) add(t, index, values, incomes);
                }
            }
        }
        chartIncome = incomes;
        chartExpense = values;
        if (chartSelected < 0 || chartSelected >= values.length) chartSelected = values.length - 1;
        String[] tips = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            if (period == YEAR) tips[i] = Dates.monthShort(YearMonth.from(today).minusMonths(11 - i))
                    + " " + YearMonth.from(today).minusMonths(11 - i).getYear();
            else tips[i] = Dates.dayMonth(from.plusDays(i));
        }
        ((TrendChartView) view.findViewById(R.id.chart)).setData(incomes, values, labels, tips, show, chartSelected);
        long totalIncome = 0;
        long totalExpense = 0;
        for (int i = 0; i < values.length; i++) {
            totalIncome += incomes[i];
            totalExpense += values[i];
        }
        ((TextView) view.findViewById(R.id.net)).setText(
                Money.styled(totalIncome - totalExpense, color(R.color.gl_on_ink_secondary), true));
        TextView resultLabel = view.findViewById(R.id.result_label);
        TextView resultCaption = view.findViewById(R.id.net_caption);
        if (categoryFilter == 0) {
            resultLabel.setText(R.string.analytics_result);
            resultCaption.setText(getString(R.string.analytics_result_caption,
                    Money.whole(totalIncome), Money.whole(totalExpense)));
        } else {
            resultLabel.setText(R.string.analytics_category_result);
            resultCaption.setText(getString(R.string.analytics_category_result_caption,
                    categoryName(vm.categoryList.getValue(), categoryFilter), Money.whole(totalExpense)));
        }
        renderLegend(view);

        renderBudget(view, history, today);
        renderCategories(view, history, from, today);
    }

    private void renderBudget(View view, List<FinancialTransaction> history, LocalDate today) {
        List<Budget> budgets = vm().budgetList.getValue();
        YearMonth month = YearMonth.from(today);
        String name = Dates.month(month);
        ((TextView) view.findViewById(R.id.budget_month)).setText(
                Character.toUpperCase(name.charAt(0)) + name.substring(1));
        Map<Long, Long> limits = new HashMap<>();
        long limit = 0;
        if (budgets != null) {
            for (Budget b : budgets) {
                if (categoryFilter != 0 && (categoryFilter < 0 || b.getCategory_id() != categoryFilter)) continue;
                limits.put(b.getCategory_id(), b.getBudget_limit_cents());
                limit += b.getBudget_limit_cents();
            }
        }
        long spent = 0;
        for (FinancialTransaction t : history) {
            if (!isExpense(t, month.atDay(1), month.atEndOfMonth())) continue;
            if (categoryFilter != 0 && !matchesCategory(t.getCategory_id(), categoryFilter)) continue;
            if (limits.isEmpty() || (t.getCategory_id() != null && limits.containsKey(t.getCategory_id()))) {
                spent += t.getTransaction_amount_cents();
            }
        }
        TextView spentView = view.findViewById(R.id.budget_spent);
        spentView.setText(Money.styled(-spent, color(R.color.gl_text_secondary)));
        TextView left = view.findViewById(R.id.budget_left);
        if (limit == 0) {
            left.setText(R.string.analytics_budget_none);
            left.setTextColor(color(R.color.gl_text_secondary));
        } else if (spent <= limit) {
            left.setText(getString(R.string.analytics_budget_free, Money.whole(limit - spent)));
            left.setTextColor(color(R.color.gl_text_secondary));
        } else {
            left.setText(getString(R.string.analytics_budget_over, Money.whole(spent - limit)));
            left.setTextColor(color(R.color.gl_expense));
        }
    }

    private void renderCategories(View view, List<FinancialTransaction> history, LocalDate from, LocalDate to) {
        List<Category> categories = vm().categoryList.getValue();
        Map<Long, Long> totals = new HashMap<>();
        long sum = 0;
        for (FinancialTransaction t : history) {
            if (!isExpense(t, from, to)) continue;
            if (categoryFilter != 0 && !matchesCategory(t.getCategory_id(), categoryFilter)) continue;
            long key = t.getCategory_id() == null ? -1L : t.getCategory_id();
            Long current = totals.get(key);
            totals.put(key, (current == null ? 0 : current) + t.getTransaction_amount_cents());
            sum += t.getTransaction_amount_cents();
        }
        List<Map.Entry<Long, Long>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        LinearLayout list = view.findViewById(R.id.categories);
        list.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        long max = sorted.isEmpty() ? 0 : sorted.get(0).getValue();
        int muted = color(R.color.gl_text_secondary);
        for (Map.Entry<Long, Long> entry : sorted) {
            Category category = MainViewModel.find(categories, entry.getKey() < 0 ? null : entry.getKey());
            int tint = CategoryStyle.color(category);
            View row = inflater.inflate(R.layout.item_category_row, list, false);
            ImageView icon = row.findViewById(R.id.icon);
            icon.setImageResource(CategoryStyle.icon(category, TransactionType.DESPESA));
            icon.setImageTintList(ColorStateList.valueOf(tint));
            row.findViewById(R.id.icon_bg).setBackgroundTintList(ColorStateList.valueOf(CategoryStyle.tint(tint, 0x33)));
            String name = category == null ? getString(R.string.analytics_no_category) : category.getCategory_name();
            ((TextView) row.findViewById(R.id.name)).setText(name);
            BarView bar = row.findViewById(R.id.bar);
            bar.setColors(0xFFE1EAE8, tint);
            bar.setProgress(max == 0 ? 0f : (float) entry.getValue() / max);
            ((TextView) row.findViewById(R.id.amount)).setText(Money.styled(-entry.getValue(), muted));
            int percent = sum == 0 ? 0 : Math.round(100f * entry.getValue() / sum);
            ((TextView) row.findViewById(R.id.share)).setText(String.format(Locale.ROOT, "%d%%", percent));
            row.setContentDescription(name + ", " + Money.plain(-entry.getValue()) + ", " + percent + "%");
            list.addView(row);
        }
        view.findViewById(R.id.empty).setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String categoryName(List<Category> categories, long id) {
        if (id == -1L) return getString(R.string.analytics_no_category);
        Category category = MainViewModel.find(categories, id);
        return category == null ? getString(R.string.analytics_no_category) : category.getCategory_name();
    }
}
