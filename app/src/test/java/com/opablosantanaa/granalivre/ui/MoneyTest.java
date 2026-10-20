package com.opablosantanaa.granalivre.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyTest {
    private static final String NBSP = "\u00A0";
    private static final String MINUS = "\u2212";

    @Test
    public void groupsThousandsWithDots() {
        assertEquals("R$" + NBSP + "0,05", Money.plain(5));
        assertEquals("R$" + NBSP + "999,99", Money.plain(99_999));
        assertEquals("R$" + NBSP + "1.000,00", Money.plain(100_000));
        assertEquals("R$" + NBSP + "12.345,60", Money.plain(1_234_560));
        assertEquals("R$" + NBSP + "1.234.567,89", Money.plain(123_456_789));
    }

    @Test
    public void formatsSignsAndWholeValues() {
        assertEquals(MINUS + "R$" + NBSP + "1.500,00", Money.plain(-150_000));
        assertEquals("+R$" + NBSP + "300,00", Money.styled(30_000, 0, true).toString());
        assertEquals(MINUS + "R$" + NBSP + "2.265", Money.whole(-226_570));
    }
}
