package com.opablosantanaa.granalivre.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DueReminderTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    public void nextSlotIsLunchBeforeLunch() {
        assertEquals(LocalDateTime.of(2026, 10, 23, 11, 30),
                DueReminder.nextSlot(LocalDateTime.of(2026, 10, 23, 8, 0)));
    }

    @Test
    public void nextSlotIsAfternoonBetweenSlots() {
        assertEquals(LocalDateTime.of(2026, 10, 23, 17, 30),
                DueReminder.nextSlot(LocalDateTime.of(2026, 10, 23, 11, 30)));
    }

    @Test
    public void nextSlotIsTomorrowAfterAfternoon() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 11, 30),
                DueReminder.nextSlot(LocalDateTime.of(2026, 10, 23, 17, 30)));
    }

    @Test
    public void windowGoesFromYesterdayToTwoDaysAhead() {
        assertFalse(DueReminder.inWindow(TODAY.minusDays(2), TODAY));
        assertTrue(DueReminder.inWindow(TODAY.minusDays(1), TODAY));
        assertTrue(DueReminder.inWindow(TODAY, TODAY));
        assertTrue(DueReminder.inWindow(TODAY.plusDays(2), TODAY));
        assertFalse(DueReminder.inWindow(TODAY.plusDays(3), TODAY));
    }

    @Test
    public void daysUntilDueIsNegativeWhenOverdue() {
        DueReminder reminder = new DueReminder(DueReminder.Kind.TRANSACTION, 1, "Luz", 100, TODAY.minusDays(1));
        assertEquals(-1, reminder.daysUntilDue(TODAY));
    }

    @Test
    public void earlyRunOfLunchSlotSchedulesAfternoonNotLunchAgain() {
        LocalDateTime planned = LocalDateTime.of(2026, 10, 23, 11, 30);
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 6, 45);
        assertEquals(LocalDateTime.of(2026, 10, 23, 17, 30), DueReminder.nextAfter(planned, now));
    }

    @Test
    public void lateRunCountsFromNow() {
        LocalDateTime planned = LocalDateTime.of(2026, 10, 23, 11, 30);
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 18, 5);
        assertEquals(LocalDateTime.of(2026, 10, 24, 11, 30), DueReminder.nextAfter(planned, now));
    }

    @Test
    public void runWithoutPlannedSlotCountsFromNow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
        assertEquals(LocalDateTime.of(2026, 10, 23, 17, 30), DueReminder.nextAfter(null, now));
    }
}
