package com.opablosantanaa.granalivre.domain;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public final class DueReminder {
    public enum Kind { INVOICE, TRANSACTION, RECURRENCE }

    public static final int FIRST_DAY_OFFSET = 2;
    public static final int LAST_DAY_OFFSET = -1;

    private static final LocalTime[] SLOTS = {LocalTime.of(11, 30), LocalTime.of(17, 30)};

    @NonNull public final Kind kind;
    public final long id;
    @NonNull public final String title;
    public final long amountCents;
    @NonNull public final LocalDate dueDate;

    public DueReminder(@NonNull Kind kind, long id, @NonNull String title, long amountCents,
                       @NonNull LocalDate dueDate) {
        this.kind = kind;
        this.id = id;
        this.title = title;
        this.amountCents = amountCents;
        this.dueDate = dueDate;
    }

    public int daysUntilDue(LocalDate today) {
        return (int) ChronoUnit.DAYS.between(today, dueDate);
    }

    public static LocalDate windowStart(LocalDate today) {
        return today.plusDays(LAST_DAY_OFFSET);
    }

    public static LocalDate windowEnd(LocalDate today) {
        return today.plusDays(FIRST_DAY_OFFSET);
    }

    public static boolean inWindow(LocalDate dueDate, LocalDate today) {
        return !dueDate.isBefore(windowStart(today)) && !dueDate.isAfter(windowEnd(today));
    }

    public static LocalDateTime nextAfter(LocalDateTime planned, LocalDateTime now) {
        LocalDateTime base = planned != null && planned.isAfter(now) ? planned : now;
        return nextSlot(base);
    }

    public static LocalDateTime nextSlot(LocalDateTime now) {
        for (LocalTime slot : SLOTS) {
            LocalDateTime candidate = now.toLocalDate().atTime(slot);
            if (candidate.isAfter(now)) return candidate;
        }
        return now.toLocalDate().plusDays(1).atTime(SLOTS[0]);
    }
}
