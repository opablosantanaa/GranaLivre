package com.opablosantanaa.granalivre.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class RecurrenceScheduleTest {
    @Test
    public void monthlyOccurrencesUntilTodayInclusive() {
        List<LocalDate> dates = RecurrenceSchedule.pendingOccurrences(
                LocalDate.of(2026, 7, 10), null, RecurrenceFrequency.MENSAL, null, LocalDate.of(2026, 10, 10));
        assertEquals(Arrays.asList(
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10)), dates);
    }

    @Test
    public void alreadyGeneratedDatesAreSkipped() {
        List<LocalDate> dates = RecurrenceSchedule.pendingOccurrences(
                LocalDate.of(2026, 7, 10), null, RecurrenceFrequency.MENSAL,
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 20));
        assertEquals(Arrays.asList(LocalDate.of(2026, 10, 10)), dates);
    }

    @Test
    public void endDateLimitsOccurrences() {
        List<LocalDate> dates = RecurrenceSchedule.pendingOccurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), RecurrenceFrequency.SEMANAL,
                null, LocalDate.of(2026, 12, 31));
        assertEquals(Arrays.asList(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 15)), dates);
    }

    @Test
    public void futureStartProducesNothing() {
        assertTrue(RecurrenceSchedule.pendingOccurrences(
                LocalDate.of(2026, 11, 1), null, RecurrenceFrequency.ANUAL, null, LocalDate.of(2026, 10, 1)).isEmpty());
    }

    @Test
    public void monthlyFromDay31KeepsAnchorDay() {
        assertEquals(LocalDate.of(2027, 2, 28),
                RecurrenceSchedule.occurrence(LocalDate.of(2027, 1, 31), RecurrenceFrequency.MENSAL, 1));
        assertEquals(LocalDate.of(2027, 3, 31),
                RecurrenceSchedule.occurrence(LocalDate.of(2027, 1, 31), RecurrenceFrequency.MENSAL, 2));
    }
}
