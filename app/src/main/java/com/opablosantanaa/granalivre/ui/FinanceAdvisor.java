package com.opablosantanaa.granalivre.ui;

import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assessor da moeda: transforma o histórico de lançamentos em dicas curtas, só com regras locais.
 * As dicas saem ordenadas da mais urgente para a menos urgente.
 */
public final class FinanceAdvisor {
    public enum Mood { ALERT, WARN, PRAISE, INFO }

    public static final class Tip {
        public final Mood mood;
        public final String text;

        Tip(Mood mood, String text) {
            this.mood = mood;
            this.text = text;
        }
    }

    /** Fatia dos gastos do mês a partir da qual uma categoria é considerada exagerada. */
    static final float DOMINANT_SHARE = 0.40f;
    /** Gasto acima da média dos 3 meses anteriores que gera alerta (50% a mais). */
    static final float SPIKE_RATIO = 1.5f;
    /** Queda em relação à média que vale um elogio (20% a menos). */
    static final float DROP_RATIO = 0.8f;
    /** Parte da receita guardada que vale um elogio. */
    static final float GOOD_SAVINGS = 0.20f;
    /** Diferença mínima, em centavos, para uma dica de categoria não ser ruído (R$ 100). */
    static final long MIN_RELEVANT_CENTS = 10_000;
    /** A partir deste dia o mês corrente já serve para medir a economia; antes disso usa o mês anterior. */
    static final int SAVINGS_CHECK_DAY = 20;

    private FinanceAdvisor() {}

    public static List<Tip> tips(List<FinancialTransaction> history, List<Category> categories,
                                 List<Budget> budgets, LocalDate today) {
        List<Tip> alerts = new ArrayList<>();
        List<Tip> warnings = new ArrayList<>();
        List<Tip> praises = new ArrayList<>();
        if (history == null) history = new ArrayList<>();
        YearMonth month = YearMonth.from(today);

        int overdue = 0;
        for (FinancialTransaction t : history) {
            if (!t.isTransaction_paid() && t.getRecurrence_id() != null && t.getTransaction_date().isBefore(today)) {
                overdue++;
            }
        }
        if (overdue > 0) {
            alerts.add(new Tip(Mood.ALERT, overdue == 1
                    ? "Tem uma conta recorrente atrasada. Quite ou registre um pagamento parcial na aba Recorrentes."
                    : "Tem " + overdue + " contas recorrentes atrasadas. Quite ou registre pagamentos parciais "
                    + "na aba Recorrentes."));
        }

        Totals current = totals(history, month, today);
        if (current.income > 0 && current.expense > current.income) {
            alerts.add(new Tip(Mood.ALERT, "Neste mês você já gastou " + Money.plain(current.expense - current.income)
                    + " a mais do que recebeu. Segure os gastos não essenciais até o mês virar."));
        }

        if (budgets != null) {
            for (Budget budget : budgets) {
                if (budget.getBudget_limit_cents() <= 0) continue;
                long spent = value(current.byCategory, budget.getCategory_id());
                if (spent <= budget.getBudget_limit_cents()) continue;
                alerts.add(new Tip(Mood.ALERT, "Você passou do orçamento de " + name(categories, budget.getCategory_id())
                        + " em " + Money.plain(spent - budget.getBudget_limit_cents())
                        + ". Avalie cortar um pouco nessa categoria até o fim do mês."));
            }
        }

        Map<Long, Long> average = average(history, month, 3, today);
        if (current.expense > 0) {
            for (Map.Entry<Long, Long> entry : current.byCategory.entrySet()) {
                long spent = entry.getValue();
                float share = (float) spent / current.expense;
                if (share < DOMINANT_SHARE || spent < MIN_RELEVANT_CENTS) continue;
                String category = name(categories, entry.getKey());
                long fixed = value(current.recurringByCategory, entry.getKey());
                if (fixed >= spent * 0.8f) {
                    warnings.add(new Tip(Mood.WARN, category + " representa " + percent(share)
                            + " dos seus gastos neste mês, quase tudo em contas fixas. Se estiver pesado, "
                            + "vale tentar renegociar ou trocar de plano."));
                } else {
                    warnings.add(new Tip(Mood.WARN, "Neste mês, " + category + " representa " + percent(share)
                            + " dos seus gastos. Você exagerou um pouco aqui: avalie gastar menos nessa categoria "
                            + "no próximo mês."));
                }
            }
            for (Map.Entry<Long, Long> entry : current.byCategory.entrySet()) {
                long avg = value(average, entry.getKey());
                long spent = entry.getValue();
                if (avg <= 0 || spent < avg * SPIKE_RATIO || spent - avg < MIN_RELEVANT_CENTS) continue;
                // sem porcentagens acima de 100%: a partir do dobro, fala em "vezes"
                float ratio = (float) spent / avg;
                String how = ratio >= 2f ? times(ratio) : percent(ratio - 1f) + " acima da sua média";
                warnings.add(new Tip(Mood.WARN, "Os gastos com " + name(categories, entry.getKey()) + " já estão "
                        + how + " dos últimos 3 meses (" + Money.plain(avg) + "). Fique de olho até o fim do mês."));
            }
        }

        boolean useCurrent = today.getDayOfMonth() >= SAVINGS_CHECK_DAY;
        YearMonth reference = useCurrent ? month : month.minusMonths(1);
        Totals measured = useCurrent ? current : totals(history, reference, today);
        String when = useCurrent ? "neste mês" : "em " + Dates.month(reference);
        if (measured.income > 0) {
            long saved = measured.income - measured.expense;
            float rate = (float) saved / measured.income;
            if (rate >= GOOD_SAVINGS) {
                praises.add(new Tip(Mood.PRAISE, "Mandou bem! " + capitalize(when) + " você guardou "
                        + percent(rate) + " do que recebeu (" + Money.plain(saved) + "). Que tal separar uma parte "
                        + "para uma meta?"));
            } else if (saved > 0) {
                praises.add(new Tip(Mood.INFO, capitalize(when) + " sobraram " + Money.plain(saved) + ", "
                        + percent(rate) + " do que entrou. Tente chegar a 20% cortando um pouco da maior categoria."));
            }
            Map<Long, Long> before = average(history, reference, 3, today);
            Long bestCategory = null;
            long bestDrop = 0;
            for (Map.Entry<Long, Long> entry : before.entrySet()) {
                long avg = entry.getValue();
                long spent = value(measured.byCategory, entry.getKey());
                if (avg < MIN_RELEVANT_CENTS || spent > avg * DROP_RATIO) continue;
                if (avg - spent > bestDrop) {
                    bestDrop = avg - spent;
                    bestCategory = entry.getKey();
                }
            }
            if (bestCategory != null) {
                long avg = value(before, bestCategory);
                praises.add(new Tip(Mood.PRAISE, capitalize(when) + " você gastou " + Money.plain(bestDrop)
                        + " a menos com " + name(categories, bestCategory) + " do que costuma ("
                        + percent((float) bestDrop / avg) + " abaixo da média). Continue assim!"));
            }
        }

        List<Tip> tips = new ArrayList<>(alerts);
        tips.addAll(warnings);
        tips.addAll(praises);
        if (tips.isEmpty()) {
            tips.add(new Tip(Mood.INFO, current.expense == 0 && current.income == 0
                    ? "Ainda não há lançamentos neste mês. Registre receitas e despesas para eu poder te ajudar."
                    : "Tudo em ordem por aqui! Nenhum gasto fora do normal neste mês."));
        }
        return tips;
    }

    private static final class Totals {
        long income;
        long expense;
        final Map<Long, Long> byCategory = new HashMap<>();
        final Map<Long, Long> recurringByCategory = new HashMap<>();
    }

    /** Soma o mês até hoje (lançamentos futuros ainda não aconteceram). */
    private static Totals totals(List<FinancialTransaction> history, YearMonth month, LocalDate today) {
        Totals totals = new Totals();
        for (FinancialTransaction t : history) {
            LocalDate date = t.getTransaction_date();
            if (date.isAfter(today) || !YearMonth.from(date).equals(month)) continue;
            long cents = t.getTransaction_amount_cents();
            if (t.getTransaction_type() == TransactionType.RECEITA) {
                totals.income += cents;
                continue;
            }
            totals.expense += cents;
            long key = t.getCategory_id() == null ? -1L : t.getCategory_id();
            totals.byCategory.merge(key, cents, Long::sum);
            if (t.getRecurrence_id() != null) totals.recurringByCategory.merge(key, cents, Long::sum);
        }
        return totals;
    }

    /** Média mensal de gastos por categoria nos {@code months} meses anteriores a {@code month}. */
    private static Map<Long, Long> average(List<FinancialTransaction> history, YearMonth month, int months,
                                           LocalDate today) {
        Map<Long, Long> sum = new HashMap<>();
        int counted = 0;
        for (int i = 1; i <= months; i++) {
            Totals totals = totals(history, month.minusMonths(i), today);
            if (totals.expense == 0 && totals.income == 0) continue;
            counted++;
            for (Map.Entry<Long, Long> entry : totals.byCategory.entrySet()) {
                sum.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        Map<Long, Long> average = new HashMap<>();
        if (counted == 0) return average;
        for (Map.Entry<Long, Long> entry : sum.entrySet()) average.put(entry.getKey(), entry.getValue() / counted);
        return average;
    }

    private static long value(Map<Long, Long> map, long key) {
        Long value = map.get(key);
        return value == null ? 0 : value;
    }

    private static String name(List<Category> categories, long id) {
        if (categories != null) {
            for (Category category : categories) {
                if (category.getCategory_id() == id) return category.getCategory_name();
            }
        }
        return "Outras despesas";
    }

    /** "mais que o dobro da sua média", "mais que o triplo..." ou "mais de 4 vezes a sua média". */
    private static String times(float ratio) {
        int n = (int) Math.floor(ratio);
        return n == 2 ? "mais que o dobro da sua média" : n == 3 ? "mais que o triplo da sua média"
                : "mais de " + n + " vezes a sua média";
    }

    private static String percent(float ratio) {
        return String.format(Locale.ROOT, "%d%%", Math.round(ratio * 100));
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
