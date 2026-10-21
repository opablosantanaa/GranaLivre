package com.opablosantanaa.granalivre.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FinanceAdvisorTest {
    private static final long FOOD = 1;
    private static final long HOME = 2;
    private static final long LEISURE = 3;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final List<FinancialTransaction> history = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();

    public FinanceAdvisorTest() {
        categories.add(category(FOOD, "Alimentação"));
        categories.add(category(HOME, "Moradia"));
        categories.add(category(LEISURE, "Lazer"));
    }

    private static Category category(long id, String name) {
        Category c = new Category();
        c.setCategory_id(id);
        c.setCategory_name(name);
        c.setCategory_type(TransactionType.DESPESA);
        return c;
    }

    private FinancialTransaction add(TransactionType type, long cents, Long category, LocalDate date) {
        FinancialTransaction t = new FinancialTransaction();
        t.setTransaction_type(type);
        t.setTransaction_amount_cents(cents);
        t.setCategory_id(category);
        t.setTransaction_date(date);
        history.add(t);
        return t;
    }

    private List<FinanceAdvisor.Tip> tips() {
        return FinanceAdvisor.tips(history, categories, Collections.emptyList(), TODAY);
    }

    private static boolean has(List<FinanceAdvisor.Tip> tips, FinanceAdvisor.Mood mood, String fragment) {
        for (FinanceAdvisor.Tip tip : tips) {
            if (tip.mood == mood && tip.text.contains(fragment)) return true;
        }
        return false;
    }

    @Test
    public void dominantCategoryGetsWarning() {
        add(TransactionType.RECEITA, 1_000_000, null, TODAY.withDayOfMonth(5));
        add(TransactionType.DESPESA, 60_000, FOOD, TODAY.withDayOfMonth(10));
        add(TransactionType.DESPESA, 40_000, LEISURE, TODAY.withDayOfMonth(11));
        List<FinanceAdvisor.Tip> tips = tips();
        assertTrue(has(tips, FinanceAdvisor.Mood.WARN, "Alimentação representa 60%"));
        assertTrue(has(tips, FinanceAdvisor.Mood.WARN, "Lazer representa 40%"));
    }

    @Test
    public void mostlyFixedCategoryGetsRenegotiationTip() {
        add(TransactionType.DESPESA, 150_000, HOME, TODAY.withDayOfMonth(10)).setRecurrence_id(9L);
        add(TransactionType.DESPESA, 20_000, FOOD, TODAY.withDayOfMonth(12));
        assertTrue(has(tips(), FinanceAdvisor.Mood.WARN, "renegociar"));
    }

    @Test
    public void spendingMoreThanIncomeIsAlertFirst() {
        add(TransactionType.RECEITA, 100_000, null, TODAY.withDayOfMonth(5));
        add(TransactionType.DESPESA, 150_000, FOOD, TODAY.withDayOfMonth(6));
        List<FinanceAdvisor.Tip> tips = tips();
        assertEquals(FinanceAdvisor.Mood.ALERT, tips.get(0).mood);
        assertTrue(tips.get(0).text.contains("a mais do que recebeu"));
    }

    @Test
    public void goodSavingsArePraised() {
        add(TransactionType.RECEITA, 500_000, null, TODAY.withDayOfMonth(5));
        add(TransactionType.DESPESA, 100_000, FOOD, TODAY.withDayOfMonth(8));
        add(TransactionType.DESPESA, 100_000, LEISURE, TODAY.withDayOfMonth(9));
        assertTrue(has(tips(), FinanceAdvisor.Mood.PRAISE, "você guardou 60%"));
    }

    @Test
    public void earlyInMonthSavingsUsePreviousMonth() {
        LocalDate early = LocalDate.of(2026, 10, 6);
        add(TransactionType.RECEITA, 500_000, null, early.withDayOfMonth(5));
        add(TransactionType.RECEITA, 500_000, null, LocalDate.of(2026, 9, 5));
        add(TransactionType.DESPESA, 450_000, FOOD, LocalDate.of(2026, 9, 10));
        List<FinanceAdvisor.Tip> tips = FinanceAdvisor.tips(history, categories, null, early);
        assertFalse(has(tips, FinanceAdvisor.Mood.PRAISE, "guardou"));
        assertTrue(has(tips, FinanceAdvisor.Mood.INFO, "Em setembro sobraram"));
    }

    @Test
    public void spikeAboveAverageIsWarned() {
        for (int i = 1; i <= 3; i++) add(TransactionType.DESPESA, 20_000, LEISURE, TODAY.minusMonths(i));
        add(TransactionType.DESPESA, 50_000, LEISURE, TODAY.withDayOfMonth(3));
        add(TransactionType.DESPESA, 200_000, FOOD, TODAY.withDayOfMonth(3));
        assertTrue(has(tips(), FinanceAdvisor.Mood.WARN, "Lazer já estão mais que o dobro da sua média"));
    }

    @Test
    public void dropBelowAverageIsPraised() {
        add(TransactionType.RECEITA, 500_000, null, TODAY.withDayOfMonth(1));
        for (int i = 1; i <= 3; i++) add(TransactionType.DESPESA, 100_000, FOOD, TODAY.minusMonths(i));
        add(TransactionType.DESPESA, 40_000, FOOD, TODAY.withDayOfMonth(3));
        assertTrue(has(tips(), FinanceAdvisor.Mood.PRAISE, "R$ 600,00 a menos com Alimentação"));
    }

    @Test
    public void exceededBudgetIsAlert() {
        add(TransactionType.DESPESA, 70_000, FOOD, TODAY.withDayOfMonth(3));
        Budget budget = new Budget();
        budget.setCategory_id(FOOD);
        budget.setBudget_limit_cents(50_000);
        List<FinanceAdvisor.Tip> tips = FinanceAdvisor.tips(history, categories,
                Collections.singletonList(budget), TODAY);
        assertTrue(has(tips, FinanceAdvisor.Mood.ALERT, "orçamento de Alimentação em R$ 200,00"));
    }

    @Test
    public void overdueRecurrenceIsAlert() {
        FinancialTransaction t = add(TransactionType.DESPESA, 10_000, HOME, TODAY.minusDays(2));
        t.setTransaction_paid(false);
        t.setRecurrence_id(4L);
        assertTrue(has(tips(), FinanceAdvisor.Mood.ALERT, "atrasada"));
    }

    @Test
    public void futureEntriesAreIgnoredAndEmptyMonthGetsHint() {
        add(TransactionType.DESPESA, 90_000, FOOD, TODAY.plusDays(3));
        List<FinanceAdvisor.Tip> tips = tips();
        assertEquals(1, tips.size());
        assertTrue(tips.get(0).text.contains("Ainda não há lançamentos"));
    }
}
