package com.opablosantanaa.granalivre.domain;

import java.time.LocalDate;
import java.time.YearMonth;

public final class InvoiceCalculator {
    private InvoiceCalculator() {}

    public static YearMonth referenceMonth(LocalDate purchaseDate, int closingDay) {
        YearMonth month = YearMonth.from(purchaseDate);
        return purchaseDate.isBefore(closingDate(month, closingDay)) ? month : month.plusMonths(1);
    }

    public static LocalDate closingDate(YearMonth month, int closingDay) {
        return dayInMonth(month, closingDay);
    }

    public static LocalDate dueDate(YearMonth month, int closingDay, int dueDay) {
        YearMonth dueMonth = dueDay > closingDay ? month : month.plusMonths(1);
        return dayInMonth(dueMonth, dueDay);
    }

    private static LocalDate dayInMonth(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
