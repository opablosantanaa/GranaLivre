package com.opablosantanaa.granalivre.domain.ofx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public class CsvParserTest {
    @Test
    public void readsCommaSeparatedCardInvoiceWithIsoDates() throws OfxException {
        OfxStatement s = CsvParser.parse("date,title,amount\n2026-10-03,Padaria,12.50\n"
                + "2026-10-05,Pagamento recebido,-500.00\n");
        assertEquals(2, s.transactions.size());
        assertEquals(1_250, s.transactions.get(0).amountCents);
        assertEquals(-50_000, s.transactions.get(1).amountCents);
        assertEquals("Padaria", s.transactions.get(0).description);
        assertEquals(LocalDate.of(2026, 10, 3), s.start);
        assertEquals(LocalDate.of(2026, 10, 5), s.end);
    }

    @Test
    public void readsSemicolonSeparatedBrazilianFormatWithQuotes() throws OfxException {
        OfxStatement s = CsvParser.parse("﻿Data;Descrição;Valor\n03/10/2026;\"Mercado; Centro\";\"-R$ 1.234,56\"\n");
        assertEquals(1, s.transactions.size());
        assertEquals(-123_456, s.transactions.get(0).amountCents);
        assertEquals("Mercado; Centro", s.transactions.get(0).description);
        assertEquals(LocalDate.of(2026, 10, 3), s.transactions.get(0).date);
    }

    @Test
    public void usesIdentifierColumnAndHashesRepeatedLinesApart() throws OfxException {
        OfxStatement withId = CsvParser.parse("Data,Valor,Identificador,Descrição\n01/10/2026,-10.00,abc,Café\n");
        assertEquals("fit:abc", withId.transactions.get(0).importId);
        OfxStatement repeated = CsvParser.parse("data,valor,descricao\n01/10/2026,-5,Café\n01/10/2026,-5,Café\n");
        assertTrue(repeated.transactions.get(0).importId.startsWith("hash:"));
        assertNotEquals(repeated.transactions.get(0).importId, repeated.transactions.get(1).importId);
    }

    @Test
    public void tellsInvoiceFromStatement() throws OfxException {
        assertTrue(CsvParser.parse("date,title,amount\n2026-10-03,Padaria,12.50\n").creditCard);
        assertTrue(CsvParser.parse("Data;Lançamento;Valor\n03/10/2026;Loja;50,00\n03/10/2026;Bar;20,00\n"
                + "04/10/2026;Pagamento;-70,00\n").creditCard);
        assertEquals(false, CsvParser.parse("Data,Valor,Identificador,Descrição\n01/10/2026,100.00,a,Pix\n").creditCard);
        assertEquals(false, CsvParser.parse("Data;Descrição;Valor;Saldo\n01/10/2026;Salário;3000,00;3000,00\n").creditCard);
        assertEquals(false, CsvParser.parse("data,valor,descricao\n01/10/2026,-5,Café\n02/10/2026,10,Pix\n"
                + "03/10/2026,-8,Pão\n").creditCard);
    }

    @Test
    public void rejectsFileWithoutDateAndAmountColumns() {
        assertThrows(OfxException.class, () -> CsvParser.parse("foo,bar\n1,2\n"));
    }

    @Test
    public void detectsOfxByContent() {
        assertTrue(OfxParser.isOfx("OFXHEADER:100\n<OFX>".getBytes()));
        assertEquals(false, OfxParser.isOfx("date,title,amount".getBytes()));
    }
}
