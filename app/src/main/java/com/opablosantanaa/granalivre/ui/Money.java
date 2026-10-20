package com.opablosantanaa.granalivre.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class Money {
    public static final Locale BR = new Locale("pt", "BR");
    private static final String MINUS = "\u2212";

    private Money() {}

    private static String integerPart(long cents) {
        String digits = Long.toString(Math.abs(cents) / 100);
        int length = digits.length();
        if (length <= 3) return digits;
        StringBuilder out = new StringBuilder(length + length / 3);
        int head = length % 3 == 0 ? 3 : length % 3;
        out.append(digits, 0, head);
        for (int i = head; i < length; i += 3) out.append('.').append(digits, i, i + 3);
        return out.toString();
    }

    private static String decimalPart(long cents) {
        int rest = (int) (Math.abs(cents) % 100);
        return rest < 10 ? ",0" + rest : "," + rest;
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
