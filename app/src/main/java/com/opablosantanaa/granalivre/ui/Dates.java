package com.opablosantanaa.granalivre.ui;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;

public final class Dates {
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Money.BR);
    private static final DateTimeFormatter WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Money.BR);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Money.BR);

    private Dates() {}

    private static String clean(String text) {
        return text.replace(".", "");
    }

    public static String dayMonth(LocalDate date) {
        return clean(date.format(DAY_MONTH));
    }

    public static String relativeDay(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Hoje";
        if (date.equals(today.minusDays(1))) return "Ontem";
        if (date.equals(today.plusDays(1))) return "Amanh\u00e3";
        String text = date.format(WEEKDAY_DAY);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static String todayLabel() {
        return "Hoje, " + dayMonth(LocalDate.now());
    }

    public static String month(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Money.BR);
    }

    public static String monthYear(YearMonth month) {
        String name = month(month);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " de " + month.getYear();
    }

    public static String monthShort(YearMonth month) {
        return clean(month.getMonth().getDisplayName(TextStyle.SHORT, Money.BR));
    }

    public static String weekdayShort(LocalDate date) {
        return clean(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Money.BR));
    }

    public static String shortDate(LocalDate date) {
        return date.format(SHORT);
    }
}
