package com.opablosantanaa.granalivre.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;

public class InvoiceCalculatorTest {
    @Test
    public void purchaseBeforeClosingDayGoesToCurrentMonth() {
        assertEquals(YearMonth.of(2026, 10), InvoiceCalculator.referenceMonth(LocalDate.of(2026, 10, 24), 25));
    }

    @Test
    public void purchaseOnClosingDayGoesToNextMonth() {
        assertEquals(YearMonth.of(2026, 11), InvoiceCalculator.referenceMonth(LocalDate.of(2026, 10, 25), 25));
    }

    @Test
    public void purchaseInDecemberAfterClosingGoesToNextYear() {
        assertEquals(YearMonth.of(2027, 1), InvoiceCalculator.referenceMonth(LocalDate.of(2026, 12, 28), 25));
    }

    @Test
    public void closingDayIsClampedToEndOfShortMonth() {
        assertEquals(LocalDate.of(2027, 2, 28), InvoiceCalculator.closingDate(YearMonth.of(2027, 2), 31));
    }

    @Test
    public void dueDayBeforeClosingDayFallsInNextMonth() {
        assertEquals(LocalDate.of(2026, 11, 5), InvoiceCalculator.dueDate(YearMonth.of(2026, 10), 25, 5));
    }

    @Test
    public void dueDayAfterClosingDayFallsInSameMonth() {
        assertEquals(LocalDate.of(2026, 10, 15), InvoiceCalculator.dueDate(YearMonth.of(2026, 10), 5, 15));
    }
}
