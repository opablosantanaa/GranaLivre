package com.opablosantanaa.granalivre.domain.ofx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public class OfxParserTest {
    private static final String SGML_HEADER = "OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nSECURITY:NONE\n"
            + "ENCODING:USASCII\nCHARSET:1252\nCOMPRESSION:NONE\nOLDFILEUID:NONE\nNEWFILEUID:NONE\n\n";

    private static final String ITAU = SGML_HEADER
            + "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS>"
            + "<DTSERVER>20261020120000[-03:EST]<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>"
            + "<BANKMSGSRSV1><STMTTRNRS><TRNUID>1001<STATUS><CODE>0<SEVERITY>INFO</STATUS>"
            + "<STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>0341<ACCTID>12345-6<ACCTTYPE>CHECKING</BANKACCTFROM>"
            + "<BANKTRANLIST><DTSTART>20261001100000[-03:EST]<DTEND>20261020100000[-03:EST]\n"
            + "<STMTTRN>\n<TRNTYPE>DEBIT\n<DTPOSTED>20261005100000[-03:EST]\n<TRNAMT>-150.00\n"
            + "<FITID>20261005001\n<MEMO>PAGTO CONTA ÁGUA SABESP\n</STMTTRN>\n"
            + "<STMTTRN>\n<TRNTYPE>CREDIT\n<DTPOSTED>20261006100000[-03:EST]\n<TRNAMT>3500.00\n"
            + "<FITID>20261006001\n<MEMO>SALARIO EMPRESA X\n</STMTTRN>\n"
            + "</BANKTRANLIST><LEDGERBAL><BALAMT>4350.00<DTASOF>20261020100000[-03:EST]</LEDGERBAL>"
            + "<AVAILBAL><BALAMT>4000.00<DTASOF>20261020</AVAILBAL></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";

    private static final String BRADESCO = SGML_HEADER
            + "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL"
            + "<BANKACCTFROM><BANKID>237<ACCTID>98765</BANKACCTFROM><BANKTRANLIST>"
            + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261010<TRNAMT>-1.234,56<NAME>COMPRA CARTAO MERCADO</STMTTRN>"
            + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261010<TRNAMT>-8,50<NAME>CAFE</STMTTRN>"
            + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261010<TRNAMT>-8,50<NAME>CAFE</STMTTRN>"
            + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";

    private static final String BANCO_DO_BRASIL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<?OFX OFXHEADER=\"200\" VERSION=\"211\"?>\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>"
            + "<CURDEF>BRL</CURDEF><BANKACCTFROM><BANKID>001</BANKID><ACCTID>4321-0</ACCTID></BANKACCTFROM>"
            + "<BANKTRANLIST><DTSTART>20261001</DTSTART><DTEND>20261020</DTEND>"
            + "<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20261012000000[-3:BRT]</DTPOSTED>"
            + "<TRNAMT>-89.90</TRNAMT><FITID>BB-77</FITID><MEMO>FARMÁCIA &amp; DROGARIA</MEMO></STMTTRN>"
            + "</BANKTRANLIST><LEDGERBAL><BALAMT>-12.30</BALAMT><DTASOF>20261020</DTASOF></LEDGERBAL>"
            + "</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";

    private static final String CARD = "<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL</CURDEF>"
            + "<CCACCTFROM><ACCTID>nubank-cc</ACCTID></CCACCTFROM><BANKTRANLIST>"
            + "<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20261003</DTPOSTED><TRNAMT>-42.00</TRNAMT>"
            + "<FITID>cc1</FITID><MEMO>Uber *Trip</MEMO></STMTTRN></BANKTRANLIST></CCSTMTRS>"
            + "</CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>";

    @Test
    public void parsesSgmlWithLatinCharsetLedgerAndTimezones() throws OfxException {
        OfxStatement s = OfxParser.parse(ITAU.getBytes(Charset.forName("windows-1252")));
        assertEquals("0341", s.bankId);
        assertEquals("12345-6", s.accountId);
        assertEquals("BRL", s.currency);
        assertFalse(s.creditCard);
        assertEquals(LocalDate.of(2026, 10, 1), s.start);
        assertEquals(LocalDate.of(2026, 10, 20), s.end);
        assertEquals(Long.valueOf(435_000), s.ledgerBalanceCents);
        assertEquals(LocalDate.of(2026, 10, 20), s.ledgerBalanceDate);
        assertEquals(2, s.transactions.size());
        OfxTransaction water = s.transactions.get(0);
        assertEquals(-15_000, water.amountCents);
        assertEquals(LocalDate.of(2026, 10, 5), water.date);
        assertEquals("PAGTO CONTA ÁGUA SABESP", water.description);
        assertEquals("fit:20261005001", water.importId);
        assertTrue(s.transactions.get(1).isIncome());
    }

    @Test
    public void parsesCommaDecimalsAndHashesRowsWithoutFitId() throws OfxException {
        OfxStatement s = OfxParser.parse(BRADESCO.getBytes(StandardCharsets.UTF_8));
        assertEquals("237", s.bankId);
        assertEquals(3, s.transactions.size());
        assertEquals(-123_456, s.transactions.get(0).amountCents);
        assertEquals("COMPRA CARTAO MERCADO", s.transactions.get(0).description);
        assertEquals(-850, s.transactions.get(1).amountCents);
        assertTrue(s.transactions.get(1).importId.startsWith("hash:"));
        assertNotEquals(s.transactions.get(1).importId, s.transactions.get(2).importId);
        assertNull(s.ledgerBalanceCents);
    }

    @Test
    public void hashesAreStableAcrossParses() throws OfxException {
        OfxStatement first = OfxParser.parse(BRADESCO.getBytes(StandardCharsets.UTF_8));
        OfxStatement second = OfxParser.parse(BRADESCO.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < first.transactions.size(); i++) {
            assertEquals(first.transactions.get(i).importId, second.transactions.get(i).importId);
        }
    }

    @Test
    public void parsesXmlVersionWithEntitiesAndNegativeBalance() throws OfxException {
        OfxStatement s = OfxParser.parse(BANCO_DO_BRASIL.getBytes(StandardCharsets.UTF_8));
        assertEquals("001", s.bankId);
        assertEquals(1, s.transactions.size());
        assertEquals("FARMÁCIA & DROGARIA", s.transactions.get(0).description);
        assertEquals(LocalDate.of(2026, 10, 12), s.transactions.get(0).date);
        assertEquals(Long.valueOf(-1_230), s.ledgerBalanceCents);
    }

    @Test
    public void detectsCreditCardStatements() throws OfxException {
        OfxStatement s = OfxParser.parse(CARD.getBytes(StandardCharsets.UTF_8));
        assertTrue(s.creditCard);
        assertEquals("nubank-cc", s.accountId);
        assertEquals(-4_200, s.transactions.get(0).amountCents);
    }

    @Test
    public void rejectsFilesThatAreNotOfx() {
        OfxException error = assertThrows(OfxException.class,
                () -> OfxParser.parse("data;valor\n01/10/2026;10,00".getBytes(StandardCharsets.UTF_8)));
        assertEquals("O arquivo não parece ser um extrato OFX.", error.getMessage());
    }

    @Test
    public void rejectsInvalidDates() {
        String broken = "<OFX><STMTTRN><DTPOSTED>2026<TRNAMT>-1.00</STMTTRN></OFX>";
        assertThrows(OfxException.class, () -> OfxParser.parse(broken.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parsesAmountFormats() throws OfxException {
        assertEquals(-123_456, OfxParser.parseAmount("-1.234,56"));
        assertEquals(-123_456, OfxParser.parseAmount("-1,234.56"));
        assertEquals(123_456_700, OfxParser.parseAmount("1.234.567"));
        assertEquals(1_005, OfxParser.parseAmount("+10.05"));
        assertEquals(1_000, OfxParser.parseAmount("10"));
    }

    @Test
    public void decodesUtf8WhenNoLatinCharsetIsDeclared() {
        String text = "<OFX><MEMO>ÇÃO</MEMO></OFX>";
        assertEquals(text, OfxParser.decode(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(text, OfxParser.decode(text.getBytes(Charset.forName("windows-1252"))));
    }
}
