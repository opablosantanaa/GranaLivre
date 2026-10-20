package com.opablosantanaa.granalivre.ui;

import com.opablosantanaa.granalivre.domain.RecurrenceSchedule;
import com.opablosantanaa.granalivre.entity.Recurrence;

import java.time.LocalDate;

public final class RecurrenceMath {
    private RecurrenceMath() {}

    public static long monthlyCents(Recurrence r) {
        long amount = r.getRecurrence_amount_cents();
        switch (r.getRecurrence_frequency()) {
            case SEMANAL:
                return Math.round(amount * 52 / 12.0);
            case ANUAL:
                return Math.round(amount / 12.0);
            case MENSAL:
            default:
                return amount;
        }
    }

    public static LocalDate nextOccurrence(Recurrence r, LocalDate from) {
        LocalDate end = r.getRecurrence_end_date();
        for (int n = 0; n < 10_000; n++) {
            LocalDate date = RecurrenceSchedule.occurrence(r.getRecurrence_start_date(), r.getRecurrence_frequency(), n);
            if (end != null && date.isAfter(end)) return null;
            if (!date.isBefore(from)) return date;
        }
        return null;
    }
}
