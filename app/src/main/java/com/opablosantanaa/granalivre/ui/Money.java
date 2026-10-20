package com.opablosantanaa.granalivre.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class Money {
    public static final Locale BR = new Locale("pt", "BR");
    private static final String MINUS = "\u2212";

    private Money() {}

    private static String integerPart(long cents) {
        NumberFormat format = NumberFormat.getIntegerInstance(BR);
        format.setGroupingUsed(true);
        return format.format(Math.abs(cents) / 100);
    }

    private static String decimalPart(long cents) {
        return String.format(BR, ",%02d", Math.abs(cents) % 100);
    }

    public static String plain(long cents) {
        String sign = cents < 0 ? MINUS : "";
        return sign + "R$\u00A0" + integerPart(cents) + decimalPart(cents);
    }

    public static String whole(long cents) {
        String sign = cents < 0 ? MINUS : "";
        return sign + "R$\u00A0" + integerPart(cents);
    }

    public static String compact(long cents) {
        long reais = Math.abs(cents) / 100;
        String sign = cents < 0 ? MINUS : "";
        if (reais >= 1_000_000) return sign + String.format(BR, "%.1f mi", reais / 1_000_000.0).replace(",0 ", " ");
        if (reais >= 1_000) return sign + String.format(BR, "%.1f mil", reais / 1_000.0).replace(",0 ", " ");
        return sign + reais;
    }

    public static CharSequence styled(long cents, int decimalColor) {
        return styled(cents, decimalColor, false);
    }

    public static CharSequence styled(long cents, int decimalColor, boolean explicitSign) {
        String sign = cents < 0 ? MINUS : (explicitSign && cents > 0 ? "+" : "");
        return sign + "R$\u00A0" + integerPart(cents) + decimalPart(cents);
    }

    public static long parse(String input) {
        if (input == null) return 0;
        String clean = input.replace("R$", "").replace("\u00A0", "").replace(" ", "").trim();
        if (clean.isEmpty()) return 0;
        if (clean.contains(",")) {
            clean = clean.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(clean).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }
}
