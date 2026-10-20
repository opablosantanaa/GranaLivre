package com.opablosantanaa.granalivre.ui.screen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.sheet.BudgetSheet;
import com.opablosantanaa.granalivre.ui.sheet.GoalSheet;
import com.opablosantanaa.granalivre.ui.widget.BarView;

import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GoalsScreen extends Screen {
    public GoalsScreen() {
        super(R.layout.screen_goals);
    }

    @Override
    public int tab() {
        return App.TAB_GOALS;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        padTop(view.findViewById(R.id.header), 16);
        padBottomForTabs(view.findViewById(R.id.sheet), 8);
        view.findViewById(R.id.new_goal).setOnClickListener(v ->
                GoalSheet.create().show(getChildFragmentManager(), "goal"));

        MainViewModel vm = vm();
        vm.goalList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.budgetList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.history.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.categoryList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.cardList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.cardDebts.observe(getViewLifecycleOwner(), v -> renderLater(view));
    }

    private View section(LinearLayout sheet, String title, String action, View.OnClickListener onAction) {
        View header = getLayoutInflater().inflate(R.layout.item_section, sheet, false);
        ((TextView) header.findViewById(R.id.section_title)).setText(title);
        MaterialButton button = header.findViewById(R.id.section_action);
        if (action != null) {
            button.setText(action);
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(onAction);
        }
        sheet.addView(header);
        return header;
    }

    private View progress(LinearLayout sheet, String title, CharSequence value, float ratio, int barColor,
                          String caption, String note, int noteColor) {
        LayoutInflater inflater = getLayoutInflater();
        View row = inflater.inflate(R.layout.item_progress, sheet, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        BarView bar = row.findViewById(R.id.bar);
        bar.setColors(color(R.color.gl_line), barColor);
        bar.setProgress(ratio);
        ((TextView) row.findViewById(R.id.caption)).setText(caption);
        TextView noteView = row.findViewById(R.id.note);
        noteView.setText(note);
        noteView.setTextColor(noteColor);
        row.setContentDescription(title + ", " + value + ", " + caption + ", " + note);
        sheet.addView(row);
        return row;
    }

    private void empty(LinearLayout sheet, int text) {
        TextView view = new TextView(requireContext(), null, 0, R.style.Text_Body);
        view.setText(text);
        view.setTextColor(color(R.color.gl_text_secondary));
        view.setPadding(0, dp(4), 0, dp(12));
        sheet.addView(view);
    }

    private static String percent(float ratio) {
        return String.format(Locale.ROOT, "%d%%", Math.round(ratio * 100));
    }

    private void renderLater(View view) {
        later("render", () -> render(view));
    }

    private void render(View view) {
        MainViewModel vm = vm();
        LinearLayout sheet = view.findViewById(R.id.sheet);
        sheet.removeAllViews();
        int secondary = color(R.color.gl_text_secondary);

        List<Goal> goals = vm.goalList.getValue();
        long savedTotal = 0;
        section(sheet, getString(R.string.goals_section), null, null);
        if (goals == null || goals.isEmpty()) {
            empty(sheet, R.string.goals_empty);
        } else {
            for (Goal goal : goals) {
                savedTotal += goal.getGoal_saved_cents();
                long target = Math.max(1, goal.getGoal_target_cents());
                float ratio = (float) goal.getGoal_saved_cents() / target;
                String note = goal.getGoal_deadline() == null ? getString(R.string.goals_no_deadline)
                        : getString(R.string.goals_until, Dates.dayMonth(goal.getGoal_deadline())
                        + " " + goal.getGoal_deadline().getYear());
                View row = progress(sheet, goal.getGoal_name(), percent(Math.min(ratio, 1f)), ratio,
                        color(R.color.gl_accent),
                        getString(R.string.goals_progress, Money.whole(goal.getGoal_saved_cents()),
                                Money.whole(goal.getGoal_target_cents())),
                        ratio >= 1f ? getString(R.string.goal_done) : note,
                        ratio >= 1f ? color(R.color.gl_income) : secondary);
                row.setOnClickListener(v -> GoalSheet.edit(goal).show(getChildFragmentManager(), "goal"));
            }
        }
        ((TextView) view.findViewById(R.id.saved)).setText(
                Money.styled(savedTotal, color(R.color.gl_on_silk_secondary)));

        YearMonth month = YearMonth.now();
        section(sheet, getString(R.string.budgets_section, Dates.month(month)),
                getString(R.string.budgets_define),
                v -> BudgetSheet.create(0).show(getChildFragmentManager(), "budget"));
        List<Budget> budgets = vm.budgetList.getValue();
        List<FinancialTransaction> history = vm.history.getValue();
        List<Category> categories = vm.categoryList.getValue();
        if (budgets == null || budgets.isEmpty()) {
            empty(sheet, R.string.budgets_empty);
        } else {
            for (Budget budget : budgets) {
                long spent = 0;
                if (history != null) {
                    for (FinancialTransaction t : history) {
                        if (t.getTransaction_type() == TransactionType.DESPESA && t.getCategory_id() != null
                                && t.getCategory_id() == budget.getCategory_id()
                                && YearMonth.from(t.getTransaction_date()).equals(month)) {
                            spent += t.getTransaction_amount_cents();
                        }
                    }
                }
                long limit = Math.max(1, budget.getBudget_limit_cents());
                float ratio = (float) spent / limit;
                int barColor = ratio > 1f ? color(R.color.gl_expense)
                        : ratio >= 0.85f ? color(R.color.gl_accent) : color(R.color.gl_primary);
                Category category = MainViewModel.find(categories, budget.getCategory_id());
                String name = category == null ? "" : category.getCategory_name();
                String note = spent <= limit
                        ? getString(R.string.goal_remaining, Money.whole(limit - spent))
                        : getString(R.string.analytics_budget_over, Money.whole(spent - limit));
                View row = progress(sheet, name, percent(ratio), ratio, barColor,
                        getString(R.string.goals_progress, Money.whole(spent), Money.whole(limit)),
                        note, spent <= limit ? secondary : color(R.color.gl_expense));
                row.setOnClickListener(v ->
                        BudgetSheet.create(budget.getCategory_id()).show(getChildFragmentManager(), "budget"));
            }
        }

        List<Card> cards = vm.cardList.getValue();
        Map<Long, Long> debts = vm.cardDebts.getValue();
        if (cards != null && !cards.isEmpty()) {
            section(sheet, getString(R.string.invoices_section), null, null);
            for (Card card : cards) {
                Long debtValue = debts == null ? null : debts.get(card.getCard_id());
                long debt = debtValue == null ? 0 : debtValue;
                long limit = Math.max(1, card.getCard_limit_cents());
                progress(sheet, card.getCard_nickname(), Money.styled(-debt, secondary),
                        (float) debt / limit, color(R.color.gl_primary),
                        getString(R.string.invoice_line, card.getCard_closing_day(), card.getCard_due_day()),
                        getString(R.string.invoice_limit, Money.whole(card.getCard_limit_cents() - debt)),
                        secondary).setClickable(false);
            }
        }
    }
}
