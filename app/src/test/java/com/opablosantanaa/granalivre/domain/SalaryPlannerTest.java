package com.opablosantanaa.granalivre.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalaryPlannerTest {
    private static Map<Long, Long> spent(long... pairs) {
        Map<Long, Long> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    public void spendingThatFitsKeepsValuesAndReportsLeftover() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 100_000, 2L, 50_000), 300_000, 100_000);
        assertEquals(150_000, plan.totalSpentCents);
        assertEquals(200_000, plan.availableCents);
        assertEquals(0, plan.cutCents);
        assertEquals(50_000, plan.leftoverCents);
        assertEquals(Long.valueOf(1), plan.lines.get(0).categoryId);
        assertEquals(100_000, plan.lines.get(0).suggestedCents);
        assertEquals(0, plan.lines.get(0).cutCents);
        assertEquals(1.0 / 3, plan.lines.get(0).incomeShare, 1e-9);
    }

    @Test
    public void overspendingIsCutProportionallyAndSumsToAvailable() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 200_000, 2L, 100_000), 300_000, 60_000);
        assertEquals(60_000, plan.cutCents);
        assertEquals(0, plan.leftoverCents);
        assertEquals(160_000, plan.lines.get(0).suggestedCents);
        assertEquals(40_000, plan.lines.get(0).cutCents);
        assertEquals(80_000, plan.lines.get(1).suggestedCents);
        assertEquals(20_000, plan.lines.get(1).cutCents);
    }

    @Test
    public void roundingDistributesRemainingCentsSoTotalMatchesExactly() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 100, 2L, 100, 3L, 100), 200, 0);
        long sum = 0;
        for (SalaryPlanner.Line line : plan.lines) sum += line.suggestedCents;
        assertEquals(200, sum);
    }

    @Test
    public void uncategorizedAndEmptyEntriesAreHandled() {
        Map<Long, Long> map = spent(1L, 0);
        map.put(null, 5_000L);
        SalaryPlanner.Plan plan = SalaryPlanner.plan(map, 10_000, 10_000);
        assertEquals(1, plan.lines.size());
        assertEquals(null, plan.lines.get(0).categoryId);
        assertEquals(0, plan.lines.get(0).suggestedCents);
        assertEquals(5_000, plan.cutCents);
    }

    private static FinancialTransaction expense(Long categoryId, long cents, LocalDate date, String importId) {
        FinancialTransaction t = new FinancialTransaction();
        t.setTransaction_type(TransactionType.DESPESA);
        t.setCategory_id(categoryId);
        t.setTransaction_amount_cents(cents);
        t.setTransaction_date(date);
        t.setTransaction_import_id(importId);
        return t;
    }

    @Test
    public void spendingGroupsSubcategoriesSkipsOtherMonthsIncomeAndAdjustments() {
        Category food = new Category();
        food.setCategory_id(1);
        Category market = new Category();
        market.setCategory_id(2);
        market.setCategory_parent_id(1L);
        FinancialTransaction income = expense(1L, 99_000, LocalDate.of(2026, 9, 5), null);
        income.setTransaction_type(TransactionType.RECEITA);
        List<FinancialTransaction> history = Arrays.asList(
                expense(1L, 1_000, LocalDate.of(2026, 9, 1), null),
                expense(2L, 2_000, LocalDate.of(2026, 9, 30), "fit:A"),
                expense(null, 500, LocalDate.of(2026, 9, 10), null),
                expense(1L, 7_000, LocalDate.of(2026, 10, 1), null),
                expense(null, 9_000, LocalDate.of(2026, 9, 20), "adjust:2026-09-20:1"),
                income);

        Map<Long, Long> spent = SalaryPlanner.spentByCategory(history, Arrays.asList(food, market),
                YearMonth.of(2026, 9));
        assertEquals(2, spent.size());
        assertEquals(Long.valueOf(3_000), spent.get(1L));
        assertEquals(Long.valueOf(500), spent.get(null));
    }

    private static Map<Long, SalaryPlanner.Priority> priorities(Object... pairs) {
        Map<Long, SalaryPlanner.Priority> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((Long) pairs[i], (SalaryPlanner.Priority) pairs[i + 1]);
        return map;
    }

    private static SalaryPlanner.Line line(SalaryPlanner.Plan plan, long categoryId) {
        for (SalaryPlanner.Line line : plan.lines) {
            if (line.categoryId != null && line.categoryId == categoryId) return line;
        }
        throw new AssertionError(categoryId);
    }

    @Test
    public void keptCategoryIsNotCutAndOthersCoverTheDifference() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 100_000, 2L, 50_000), 120_000, 0,
                priorities(1L, SalaryPlanner.Priority.KEEP));
        assertEquals(0, line(plan, 1).cutCents);
        assertEquals(SalaryPlanner.Priority.KEEP, line(plan, 1).priority);
        assertEquals(30_000, line(plan, 2).cutCents);
        assertEquals(SalaryPlanner.Priority.MODERATE, line(plan, 2).priority);
        assertEquals(0, plan.shortfallCents);
    }

    @Test
    public void drasticCategoryCutsTwiceAsMuchPerReal() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 40_000, 2L, 80_000), 90_000, 0,
                priorities(1L, SalaryPlanner.Priority.DRASTIC));
        assertEquals(15_000, line(plan, 1).cutCents);
        assertEquals(15_000, line(plan, 2).cutCents);
    }

    @Test
    public void drasticCutCappedAtZeroIsRedistributed() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 10_000, 2L, 20_000), 5_000, 0,
                priorities(1L, SalaryPlanner.Priority.DRASTIC));
        assertEquals(10_000, line(plan, 1).cutCents);
        assertEquals(0, line(plan, 1).suggestedCents);
        assertEquals(15_000, line(plan, 2).cutCents);
        assertEquals(25_000, plan.cutCents);
        assertEquals(0, plan.shortfallCents);
    }

    @Test
    public void protectedSpendingAboveAvailableReportsShortfall() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 100_000, 2L, 20_000), 90_000, 0,
                priorities(1L, SalaryPlanner.Priority.KEEP));
        assertEquals(20_000, line(plan, 2).cutCents);
        assertEquals(0, line(plan, 1).cutCents);
        assertEquals(10_000, plan.shortfallCents);
        assertEquals(20_000, plan.cutCents);
    }

    @Test
    public void everythingKeptMeansNoCutAndFullShortfall() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 50_000), 40_000, 0,
                priorities(1L, SalaryPlanner.Priority.KEEP));
        assertEquals(0, plan.cutCents);
        assertEquals(10_000, plan.shortfallCents);
        assertEquals(50_000, plan.lines.get(0).suggestedCents);
    }

    @Test
    public void weightedRoundingStillSumsToAvailable() {
        SalaryPlanner.Plan plan = SalaryPlanner.plan(spent(1L, 333, 2L, 333, 3L, 334), 700, 0,
                priorities(1L, SalaryPlanner.Priority.DRASTIC));
        long sum = 0;
        for (SalaryPlanner.Line l : plan.lines) sum += l.suggestedCents;
        assertEquals(700, sum);
        assertEquals(0, plan.shortfallCents);
    }

    @Test
    public void rejectsInvalidIncomeOrSavings() {
        assertThrows(IllegalArgumentException.class, () -> SalaryPlanner.plan(spent(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SalaryPlanner.plan(spent(), 100, 101));
        assertThrows(IllegalArgumentException.class, () -> SalaryPlanner.plan(spent(), 100, -1));
    }
}
