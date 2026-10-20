package com.opablosantanaa.granalivre.domain;

import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class RecurrenceSchedule {
    private RecurrenceSchedule() {}

    public static List<LocalDate> pendingOccurrences(LocalDate start, LocalDate end, RecurrenceFrequency frequency,
                                                     LocalDate lastGenerated, LocalDate until) {
        LocalDate limit = end != null && end.isBefore(until) ? end : until;
        List<LocalDate> dates = new ArrayList<>();
        for (int n = 0; ; n++) {
            LocalDate date = occurrence(start, frequency, n);
            if (date.isAfter(limit)) {
                return dates;
            }
            if (lastGenerated == null || date.isAfter(lastGenerated)) {
                dates.add(date);
            }
        }
    }

    public static LocalDate occurrence(LocalDate start, RecurrenceFrequency frequency, int index) {
        switch (frequency) {
            case SEMANAL:
                return start.plusWeeks(index);
            case ANUAL:
                return start.plusYears(index);
            case MENSAL:
            default:
                return start.plusMonths(index);
        }
    }
}
