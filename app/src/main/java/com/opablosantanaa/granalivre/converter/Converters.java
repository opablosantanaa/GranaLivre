package com.opablosantanaa.granalivre.converter;

import androidx.room.TypeConverter;

import java.time.LocalDate;
import java.time.YearMonth;

public class Converters {
    @TypeConverter
    public static Long fromLocalDate(LocalDate date) {
        return date == null ? null : date.toEpochDay();
    }

    @TypeConverter
    public static LocalDate toLocalDate(Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay);
    }

    @TypeConverter
    public static String fromYearMonth(YearMonth month) {
        return month == null ? null : month.toString();
    }

    @TypeConverter
    public static YearMonth toYearMonth(String month) {
        return month == null ? null : YearMonth.parse(month);
    }
}
