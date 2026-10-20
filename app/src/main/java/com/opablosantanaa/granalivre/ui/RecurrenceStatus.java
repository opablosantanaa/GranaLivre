package com.opablosantanaa.granalivre.ui;

import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.repository.RecurrenceRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class RecurrenceStatus {
    public static final int SOON_DAYS = 5;

    public enum Kind { PAUSED, ENDED, OVERDUE, DUE_TODAY, PAID, SOON, UPCOMING }

    public final Kind kind;
    public final LocalDate date;
    public final int overdueCount;
    public final long daysUntil;

    private RecurrenceStatus(Kind kind, LocalDate date, int overdueCount, long daysUntil) {
        this.kind = kind;
        this.date = date;
        this.overdueCount = overdueCount;
        this.daysUntil = daysUntil;
    }

    public boolean needsAttention() {
        return kind == Kind.OVERDUE || kind == Kind.DUE_TODAY || kind == Kind.SOON;
    }

    public boolean canSettle() {
        return kind != Kind.PAUSED && kind != Kind.ENDED && kind != Kind.PAID;
    }

    private static boolean samePeriod(Recurrence r, LocalDate paid, LocalDate today) {
        switch (r.getRecurrence_frequency()) {
            case SEMANAL:
                return ChronoUnit.DAYS.between(paid, today) < 7;
            case ANUAL:
                return paid.getYear() == today.getYear();
            case MENSAL:
            default:
                return YearMonth.from(paid).equals(YearMonth.from(today));
        }
    }

    public static RecurrenceStatus of(Recurrence r, List<FinancialTransaction> pending,
                                      List<FinancialTransaction> history, LocalDate today) {
        if (!r.isRecurrence_active()) return new RecurrenceStatus(Kind.PAUSED, null, 0, 0);
        int overdue = 0;
        LocalDate oldest = null;
        boolean dueToday = false;
        if (pending != null) {
            for (FinancialTransaction t : pending) {
                if (t.getRecurrence_id() == null || t.getRecurrence_id() != r.getRecurrence_id()) continue;
                LocalDate date = t.getTransaction_date();
                if (date.isBefore(today)) {
                    overdue++;
                    if (oldest == null || date.isBefore(oldest)) oldest = date;
                } else if (date.equals(today)) {
                    dueToday = true;
                }
            }
        }
        if (overdue > 0) return new RecurrenceStatus(Kind.OVERDUE, oldest, overdue, 0);
        if (dueToday) return new RecurrenceStatus(Kind.DUE_TODAY, today, 0, 0);

        LocalDate lastPaid = null;
        if (history != null) {
            for (FinancialTransaction t : history) {
                if (!t.isTransaction_paid() || t.getRecurrence_id() == null
                        || t.getRecurrence_id() != r.getRecurrence_id()) continue;
                if (t.getTransaction_date().isAfter(today)) continue;
                if (lastPaid == null || t.getTransaction_date().isAfter(lastPaid)) lastPaid = t.getTransaction_date();
            }
        }
        LocalDate next = RecurrenceRepository.nextUnsettled(r);
        if (next == null) return new RecurrenceStatus(Kind.ENDED, lastPaid, 0, 0);
        long days = ChronoUnit.DAYS.between(today, next);
        if (days < 0) return new RecurrenceStatus(Kind.OVERDUE, next, 1, 0);
        if (days == 0) return new RecurrenceStatus(Kind.DUE_TODAY, next, 0, 0);
        if (lastPaid != null && samePeriod(r, lastPaid, today)) return new RecurrenceStatus(Kind.PAID, lastPaid, 0, days);
        if (days <= SOON_DAYS) return new RecurrenceStatus(Kind.SOON, next, 0, days);
        return new RecurrenceStatus(Kind.UPCOMING, next, 0, days);
    }
}
