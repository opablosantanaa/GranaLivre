package com.opablosantanaa.granalivre.domain;

import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SalaryPlanner {
    public static final String ADJUSTMENT_PREFIX = "adjust:";

    public enum Priority {
        KEEP(0), MODERATE(1), DRASTIC(2);

        public final int weight;

        Priority(int weight) {
            this.weight = weight;
        }
    }

    public static final class Line {
        public final Long categoryId;
        public final long spentCents;
        public final long suggestedCents;
        public final long cutCents;
        public final double incomeShare;
        public final Priority priority;

        Line(Long categoryId, long spentCents, long suggestedCents, double incomeShare, Priority priority) {
            this.categoryId = categoryId;
            this.spentCents = spentCents;
            this.suggestedCents = suggestedCents;
            this.cutCents = spentCents - suggestedCents;
            this.incomeShare = incomeShare;
            this.priority = priority;
        }
    }

    public static final class Plan {
        public final List<Line> lines;
        public final long incomeCents;
        public final long savingsCents;
        public final long totalSpentCents;
        public final long availableCents;
        public final long cutCents;
        public final long leftoverCents;
        public final long shortfallCents;

        Plan(List<Line> lines, long incomeCents, long savingsCents, long totalSpentCents, long shortfallCents) {
            this.lines = Collections.unmodifiableList(lines);
            this.incomeCents = incomeCents;
            this.savingsCents = savingsCents;
            this.totalSpentCents = totalSpentCents;
            this.availableCents = incomeCents - savingsCents;
            this.shortfallCents = shortfallCents;
            this.cutCents = Math.max(0, totalSpentCents - availableCents) - shortfallCents;
            this.leftoverCents = Math.max(0, availableCents - totalSpentCents);
        }
    }

    private SalaryPlanner() {
    }

    public static Map<Long, Long> spentByCategory(List<FinancialTransaction> transactions, List<Category> categories,
                                                  YearMonth month) {
        Map<Long, Long> parents = new HashMap<>();
        if (categories != null) {
            for (Category c : categories) parents.put(c.getCategory_id(), c.getCategory_parent_id());
        }
        Map<Long, Long> spent = new HashMap<>();
        if (transactions == null) return spent;
        for (FinancialTransaction t : transactions) {
            if (t.getTransaction_type() != TransactionType.DESPESA) continue;
            if (!YearMonth.from(t.getTransaction_date()).equals(month)) continue;
            String importId = t.getTransaction_import_id();
            if (importId != null && importId.startsWith(ADJUSTMENT_PREFIX)) continue;
            Long categoryId = t.getCategory_id();
            Long parent = categoryId == null ? null : parents.get(categoryId);
            if (parent != null) categoryId = parent;
            Long current = spent.get(categoryId);
            spent.put(categoryId, (current == null ? 0 : current) + t.getTransaction_amount_cents());
        }
        return spent;
    }

    public static Plan plan(Map<Long, Long> spentByCategory, long incomeCents, long savingsCents) {
        return plan(spentByCategory, incomeCents, savingsCents, Collections.emptyMap());
    }

    public static Plan plan(Map<Long, Long> spentByCategory, long incomeCents, long savingsCents,
                            Map<Long, Priority> priorities) {
        if (incomeCents <= 0) throw new IllegalArgumentException("income");
        if (savingsCents < 0 || savingsCents > incomeCents) throw new IllegalArgumentException("savings");
        List<Map.Entry<Long, Long>> entries = new ArrayList<>();
        long total = 0;
        for (Map.Entry<Long, Long> e : spentByCategory.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            entries.add(e);
            total += e.getValue();
        }
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        int size = entries.size();
        long[] spent = new long[size];
        Priority[] priority = new Priority[size];
        for (int i = 0; i < size; i++) {
            spent[i] = entries.get(i).getValue();
            Priority chosen = priorities == null ? null : priorities.get(entries.get(i).getKey());
            priority[i] = chosen == null ? Priority.MODERATE : chosen;
        }
        long[] cut = new long[size];
        long shortfall = cut(spent, priority, Math.max(0, total - (incomeCents - savingsCents)), cut);
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            lines.add(new Line(entries.get(i).getKey(), spent[i], spent[i] - cut[i], (double) spent[i] / incomeCents,
                    priority[i]));
        }
        return new Plan(lines, incomeCents, savingsCents, total, shortfall);
    }

    private static long cut(long[] spent, Priority[] priority, long needed, long[] cut) {
        boolean[] active = new boolean[spent.length];
        for (int i = 0; i < spent.length; i++) active[i] = priority[i].weight > 0;
        long remaining = needed;
        while (remaining > 0) {
            long weightTotal = 0;
            for (int i = 0; i < spent.length; i++) {
                if (active[i]) weightTotal = Math.addExact(weightTotal, spent[i] * priority[i].weight);
            }
            if (weightTotal == 0) return remaining;
            boolean capped = false;
            for (int i = 0; i < spent.length; i++) {
                if (!active[i] || Math.multiplyExact(remaining, priority[i].weight) < weightTotal) continue;
                cut[i] = spent[i];
                active[i] = false;
                capped = true;
            }
            if (capped) {
                remaining = needed;
                for (long c : cut) remaining -= c;
                continue;
            }
            long[] remainders = new long[spent.length];
            long assigned = 0;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < spent.length; i++) {
                if (!active[i]) continue;
                long scaled = Math.multiplyExact(remaining, spent[i] * priority[i].weight);
                cut[i] = scaled / weightTotal;
                remainders[i] = scaled % weightTotal;
                assigned += cut[i];
                order.add(i);
            }
            order.sort((a, b) -> remainders[a] != remainders[b] ? Long.compare(remainders[b], remainders[a])
                    : Integer.compare(a, b));
            for (int k = 0; k < remaining - assigned; k++) cut[order.get(k)]++;
            return 0;
        }
        return 0;
    }
}
