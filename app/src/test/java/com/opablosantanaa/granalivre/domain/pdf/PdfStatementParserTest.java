package com.opablosantanaa.granalivre.domain.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.domain.ofx.OfxException;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.domain.ofx.OfxTransaction;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Amostras no formato que {@link PdfLayout} produz (colunas separadas por " | "), reproduzindo a estrutura dos
 * extratos reais de cada banco com nomes e valores trocados.
 */
public class PdfStatementParserTest {
    private static OfxStatement parse(String... text) throws OfxException {
        List<List<String>> lines = new ArrayList<>();
        for (String line : text) lines.add(Arrays.asList(line.split(" \\| ")));
        return PdfStatementParser.parse(lines);
    }

    private static long net(OfxStatement s) {
        long sum = 0;
        for (OfxTransaction t : s.transactions) sum += t.amountCents;
        return sum;
    }

    private static void assertTx(OfxTransaction t, String date, long cents, String description) {
        assertEquals(LocalDate.parse(date), t.date);
        assertEquals(cents, t.amountCents);
        assertEquals(description, t.description);
    }

    @Test
    public void itauSignedValuesAndDailyBalance() throws OfxException {
        OfxStatement s = parse(
                "FULANO DE TAL 000.000.000-00 agência: 1234 conta: 012345-6",
                "saldo em conta | Limite da Conta utilizado | Limite da Conta disponível",
                "R$ 1.020,00 | R$ 0,00 | R$ 100,00",
                "extrato conta / lançamentos",
                "período de visualização: 20/09/2026 até 20/10/2026",
                "data | lançamentos | valor (R$) | saldo (R$)",
                "20/10/2026 PIX TRANSF JOAO PE17/10 | 20,00",
                "20/10/2026 PIX QRS MERCADO19/10 | -79,79",
                "20/10/2026 SALDO DO DIA | 1.020,00",
                "16/10/2026 | INT PRE-PAGOXXXXX 3027 | -20,00",
                "16/10/2026 | SALDO DO DIA | 1.079,79",
                "15/10/2026 SALDO DO DIA | 1.099,79",
                "Aviso!",
                "Consulte as condições no site www.itau.com.br");
        assertEquals("Itaú", s.bankId);
        assertEquals("012345-6", s.accountId);
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-10-16", -2000, "INT PRE-PAGOXXXXX 3027");
        assertTx(s.transactions.get(2), "2026-10-20", 2000, "PIX TRANSF JOAO PE");
        assertEquals(Long.valueOf(102000), s.ledgerBalanceCents);
        assertEquals(LocalDate.of(2026, 10, 20), s.ledgerBalanceDate);
        assertEquals(LocalDate.of(2026, 9, 20), s.start);
    }

    @Test
    public void bradescoUsesRunningBalanceForCreditOrDebit() throws OfxException {
        OfxStatement s = parse(
                "Bradesco Celular",
                "Extrato de: Agência: 1111 | Conta: 22222-3 | Movimentação entre: 11/10/2026 e 20/10/2026 | Folha: 1/2",
                "Data | Histórico | Docto. | Crédito (R$) | Débito (R$) | Saldo (R$)",
                "09/10/2026 | COD. LANC. 0 | 0,00 | 760,80",
                "PIX RECEBIDO",
                "13/10/2026 | 0859257 | 40,00 | 800,80",
                "REM: MARIA DE SOUZA 10/10",
                "PIX ENVIADO",
                "1245274 | 110,00 | 690,80",
                "DES: LOJA EXEMPLO 10/10",
                "Bradesco Celular",
                "Data: 20/10/2026 - 21h33",
                "Data | Histórico | Docto. | Crédito (R$) | Débito (R$) | Saldo (R$)",
                "15/10/2026 | COD. LANC. 0 | 690,80",
                "PIX RECEBIDO",
                "20/10/2026 | 1420343 | 50,00 | 740,80",
                "REM: JOAO DA SILVA 20/10",
                "Total | 90,00 | 110,00 | 740,80");
        assertEquals("Bradesco", s.bankId);
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-10-13", 4000, "PIX RECEBIDO MARIA DE SOUZA");
        assertTx(s.transactions.get(1), "2026-10-13", -11000, "PIX ENVIADO LOJA EXEMPLO");
        assertTx(s.transactions.get(2), "2026-10-20", 5000, "PIX RECEBIDO JOAO DA SILVA");
        assertEquals(Long.valueOf(74080), s.ledgerBalanceCents);
        assertEquals(LocalDate.of(2026, 10, 20), s.ledgerBalanceDate);
    }

    @Test
    public void caixaOcrWithCreditDebitSuffix() throws OfxException {
        OfxStatement s = parse(
                "Extrato por período",
                "Conta | 3248 / 1288.000801470539-2",
                "Período dos lançamentos | 15/10/2026 até 20/10/2026",
                "SALDO ANTERIOR | R$ 0,17 C",
                "Lançamentos | Nr. Doc | Histórico/Complemento | Favorecido | CPF/CNPJ | Valor | Saldo",
                "19/10/2026 - 00:00:00 | 000000 | SALDO DIA | 0,00 C | 22,39 C",
                "19/10/2026 - 22:22:30 | 192222 | DEB PIX QR COD DIN | Loja Exemplo | **464.032/0*** | 538,49 D | 22,39 C",
                "18/10/2026 - 14:49:25 | 181449 | CRED PIX CHAVE | Joao Silva | ***.738.164*** | 560,71 C | 560,88 C",
                "15/10/2026 - 10:13:07 | 151013 | CRED PIX CHAVE | Joao Silva | ***.397.594*** | 30,00 C | 30,17 C",
                "Alô CAIXA: 4004 0104 (Capitais e Regiões Metropolitanas)");
        assertEquals("Caixa", s.bankId);
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-10-15", 3000, "CRED PIX CHAVE Joao Silva");
        assertTx(s.transactions.get(2), "2026-10-19", -53849, "DEB PIX QR COD DIN Loja Exemplo");
        assertEquals(Long.valueOf(2239), s.ledgerBalanceCents);
        assertEquals(LocalDate.of(2026, 10, 15), s.start);
        assertEquals(LocalDate.of(2026, 10, 20), s.end);
    }

    /** Linha com posição vertical, como sai do PDF: "y:texto | texto". */
    private static OfxStatement parseAt(String... text) throws OfxException {
        List<PdfLayout.Line> rows = new ArrayList<>();
        for (String line : text) {
            int colon = line.indexOf(':');
            rows.add(new PdfLayout.Line(0, Float.parseFloat(line.substring(0, colon)),
                    Arrays.asList(line.substring(colon + 1).split(" \\| "))));
        }
        return PdfStatementParser.parseRows(rows);
    }

    @Test
    public void bancoDoBrasilParenthesizedSignsAndFutureEntries() throws OfxException {
        // o título do lançamento fica logo acima da linha do valor; o nome que não coube, logo abaixo
        OfxStatement s = parseAt(
                "10:Extrato de Conta Corrente",
                "20:Cliente: FULANO DE TAL",
                "30:Período: 01 a 20/10/2026 | Agência: 68-X Conta: 64941-4",
                "50:Lançamentos",
                "60:Dia | Lote | Documento | Histórico | Valor",
                "70:30/09/2026 | Saldo Anterior | 100,00 (+)",
                "80:Pix - Recebido",
                "86:05/10/2026 | 68 | 202234121449991 | 05/10 22:34 12345678900 FULANO | 1.250,15 (+)",
                "92:DE TAL",
                "104:Pix - Enviado",
                "110:06/10/2026 | 99 | 100618 | 06/10 08:10 MERCADO EXEMPLO | 80,00 (-)",
                "122:Pagamento de Boleto",
                "128:06/10/2026 | 13105 | 61001 | ENERGIA EXEMPLO | 150,00 (-)",
                "140:06/10/2026 | S A L D O | 1.120,15 (+)",
                "160:Informações Adicionais",
                "170:Saldo | 1.120,15 (+)",
                "180:Juros * | 0,00",
                "200:Lançamentos Futuros",
                "210:Dia | Histórico | Valor",
                "220:25/10/2026 | Pagamento agendado | 300,00 (-)");
        assertEquals("Banco do Brasil", s.bankId);
        assertEquals("64941-4", s.accountId);
        assertEquals(LocalDate.of(2026, 10, 1), s.start);
        assertEquals(LocalDate.of(2026, 10, 20), s.end);
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-10-05", 125015, "Pix - Recebido FULANO DE TAL");
        assertTx(s.transactions.get(1), "2026-10-06", -8000, "Pix - Enviado MERCADO EXEMPLO");
        assertTx(s.transactions.get(2), "2026-10-06", -15000, "Pagamento de Boleto ENERGIA EXEMPLO");
        assertEquals(Long.valueOf(112015), s.ledgerBalanceCents);
        assertEquals(LocalDate.of(2026, 10, 6), s.ledgerBalanceDate);
    }

    @Test
    public void footerFarBelowLastRowIsNotPartOfIt() throws OfxException {
        OfxStatement s = parseAt(
                "10:período de visualização: 01/10/2026 até 02/10/2026",
                "20:Data | Descrição | Valor | Saldo",
                "30:Pix enviado",
                "36:02/10/2026 | -R$ 9,00 | R$ 1,00",
                "42:Loja Exemplo",
                "300:Documento gerado eletronicamente");
        assertTx(s.transactions.get(0), "2026-10-02", -900, "Pix enviado Loja Exemplo");
    }

    @Test
    public void caixaToleratesOcrNoise() throws OfxException {
        OfxStatement s = parse(
                "CAIXA",
                "Período dos lançamentos | 15/10/2026 até 20/10/2026",
                "Lançamentos | Nr. Doc | Histórico/Complemento | Favorecido | CPF/CNPJ | Valor | Saldo",
                "19/10/2026 - 15:07:08 | 191507 | DEB PIX OR COD EST | Loja | **132.229/0*** | 50,05 D | 667.88C",
                "CAIXA",
                "18/10/2026 - 14:49:25 | 181449 | CRED PIX CHAVE | Fulano | *** 738 164*** | 1.012,00 c | 717,93 C",
                "17/10/2026-19:07:36 | 171907 | cOMPRA CARTAO DEBITO | Atacarejo | 27,62D | 1.064,17 Cc");
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-10-17", -2762, "cOMPRA CARTAO DEBITO Atacarejo");
        assertTx(s.transactions.get(1), "2026-10-18", 101200, "CRED PIX CHAVE Fulano");
        assertTx(s.transactions.get(2), "2026-10-19", -5005, "DEB PIX OR COD EST Loja");
        assertEquals(Long.valueOf(66788), s.ledgerBalanceCents);
    }

    @Test
    public void interDayHeadersAndQuotedDescriptions() throws OfxException {
        OfxStatement s = parse(
                "CPF/CNPJ: 000.000.000-00, Instituição: Banco Inter, Agência: 0001-9, Conta: 1234567-8",
                "Período: 23/01/2025 a 25/03/2025",
                "Saldo total | Saldo disponível: | Saldo bloqueado:",
                "R$ 0,76 | R$ 0,76 | R$ 0,00",
                "24 de Janeiro de 2025 Saldo do dia: R$ 3.147,23 | Valor | Saldo por transação",
                "Pix enviado: \"00019 373510802 FULANO DA SILVA\" | -R$ 0,30 | R$ 3.147,23",
                "25 de Janeiro de 2025 Saldo do dia: R$ 3.126,25",
                "Pix enviado: \"Cp :14796606-iFood\" | -R$ 20,98 | R$ 3.126,25",
                "26 de Fevereiro de 2025 Saldo do dia: R$ 4.238,75",
                "Pix enviado devolvido: \"Cp :10573521-PIX Marketplace\" | R$ 1.112,50 | R$ 4.238,75",
                "Fale com a gente");
        assertEquals("Inter", s.bankId);
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2025-01-24", -30, "Pix enviado: FULANO DA SILVA");
        assertTx(s.transactions.get(1), "2025-01-25", -2098, "Pix enviado: iFood");
        assertTx(s.transactions.get(2), "2025-02-26", 111250, "Pix enviado devolvido: PIX Marketplace");
        assertEquals(Long.valueOf(423875), s.ledgerBalanceCents);
        assertEquals(LocalDate.of(2025, 2, 26), s.ledgerBalanceDate);
    }

    @Test
    public void nubankSectionsDefineTheSign() throws OfxException {
        OfxStatement s = parse(
                "CPF •••.172.025-•• Agência 0001 Conta",
                "76882783-4",
                "01 DE JULHO DE 2026 a 31 DE JULHO DE 2026 | VALORES EM R$",
                "Saldo inicial | 3,12",
                "Rendimento líquido | +0,00",
                "Saldo final do período",
                "Total de entradas | +1.386,84",
                "R$ 0,60",
                "Total de saídas | -1.389,36",
                "Saldo final do período | 0,60",
                "Movimentações",
                "18 JUL 2026 | Total de entradas | + 1.386,84",
                "Transferência recebida pelo Pix | FULANO DE TAL - •••.172.025- | 1.386,84",
                "•• - BANCO EXEMPLO S.A. (0341) Agência: 7922",
                "Conta: 54073-2",
                "Total de saídas | - 1.389,36",
                "Resgate de empréstimo | 1.375,36",
                "Transferência enviada pelo Pix | FULANO DE TAL - •••.172.025- | 14,00",
                "Nu Pagamentos S.A. - Instituição de Pagamento");
        assertEquals("Nubank", s.bankId);
        assertEquals("76882783-4", s.accountId);
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-07-18", 138684, "Transferência recebida pelo Pix FULANO DE TAL");
        assertTx(s.transactions.get(1), "2026-07-18", -137536, "Resgate de empréstimo");
        assertEquals(-252, net(s));
        assertEquals(Long.valueOf(60), s.ledgerBalanceCents);
        assertEquals(LocalDate.of(2026, 7, 31), s.ledgerBalanceDate);
    }

    @Test
    public void mercadoPagoJoinsCenteredMultiLineDescriptions() throws OfxException {
        OfxStatement s = parse(
                "EXTRATO DE CONTA",
                "CPF/CNPJ: 00000000000 Agência: 1 Conta: 74842659354",
                "Periodo: De 01-04-2026 al 30-04-2026",
                "Entradas: R$ 203,42",
                "Saldo inicial: R$ 0,14 | Saldo final: R$ 0,14",
                "Saidas: R$ -203,42",
                "DETALHE DOS MOVIMENTOS",
                "Data | Descrição | ID da operação | Valor | Saldo",
                "Pix recebido FULANO",
                "07-04-2026 | DE TAL | 153617710564 | R$ 203,42 | R$ 203,56",
                "SOBRENOME",
                "07-04-2026 | Pagamento Cartão de crédito | 153617722628 | R$ -203,42 | R$ 0,14",
                "Mercado Pago Instituição de Pagamento Ltda.");
        assertEquals("Mercado Pago", s.bankId);
        assertEquals(2, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-04-07", 20342, "Pix recebido FULANO DE TAL SOBRENOME");
        assertTx(s.transactions.get(1), "2026-04-07", -20342, "Pagamento Cartão de crédito");
        assertEquals(LocalDate.of(2026, 4, 1), s.start);
        assertEquals(LocalDate.of(2026, 4, 30), s.end);
    }

    @Test
    public void picPayWrittenDatesAndTimes() throws OfxException {
        OfxStatement s = parse(
                "CPF: 000.000.000-00 Agência: 0001 Conta: 1234567-8",
                "Período | Saldo final do período",
                "20 de setembro de 2026 a | R$ 0,00",
                "19 de outubro de 2026",
                "25 de setembro 2026 | Saldo ao final do dia: R$ 0,00",
                "Hora | Tipo | Origem / Destino | Forma de pagamento | Valor",
                "PADARIA",
                "09:47 | Pix enviado | Com cartão | −R$ 1,95",
                "BOM PAO",
                "22 de setembro 2026 | Saldo ao final do dia: R$ 0,00",
                "Hora | Tipo | Origem / Destino | Forma de pagamento | Valor",
                "Fulano de Tal",
                "14:15 | Pix enviado | Com cartão | −R$ 52,49",
                "Sobrenome",
                "Documento emitido em: | PicPay Serviços S/A");
        assertEquals("PicPay", s.bankId);
        assertEquals(2, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-09-22", -5249, "Pix enviado Fulano de Tal Sobrenome");
        assertTx(s.transactions.get(1), "2026-09-25", -195, "Pix enviado PADARIA BOM PAO");
        assertEquals(LocalDate.of(2026, 9, 20), s.start);
        assertEquals(LocalDate.of(2026, 10, 19), s.end);
    }

    @Test
    public void unnamedBankWithDescriptionAroundTheValueRow() throws OfxException {
        OfxStatement s = parse(
                "EXTRATO DE CONTA",
                "Agência: 4046 | Conta: 01.042205.2",
                "Período: 14/10/2026 a 20/10/2026",
                "Saldo inicial | Entradas | Saídas | Saldo final",
                "R$ 25,99 | R$ 100,00 | R$ 119,00 | R$ 6,99",
                "Data | Descrição | Valor | Saldo",
                "Segunda, 19 de outubro de 2026",
                "Pix enviado",
                "19/10/2026 | -R$ 94,00 | R$ 6,99",
                "Fulano de Tal",
                "Pix recebido",
                "19/10/2026 | R$ 100,00 | R$ 100,99",
                "Ciclano de Tal",
                "Sexta, 16 de outubro de 2026",
                "Debito visa electron brasil",
                "16/10/2026 | -R$ 25,00 | R$ 0,99",
                "Atacado Exemplo",
                "1/1");
        assertNull(s.bankId);
        assertEquals(3, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-10-16", -2500, "Debito visa electron brasil Atacado Exemplo");
        assertTx(s.transactions.get(1), "2026-10-19", 10000, "Pix recebido Ciclano de Tal");
        assertTx(s.transactions.get(2), "2026-10-19", -9400, "Pix enviado Fulano de Tal");
        assertEquals(Long.valueOf(699), s.ledgerBalanceCents);
    }

    @Test
    public void pixReceiptBecomesOneTransaction() throws OfxException {
        OfxStatement s = parse(
                "Comprovante de transferência",
                "Pix enviado",
                "Valor | R$ 150,00",
                "Data | 18/10/2026 às 14:32",
                "Destino",
                "Nome | Maria de Souza",
                "Instituição | Banco Exemplo",
                "Origem",
                "Nome | Fulano de Tal",
                "ID da transação | E123456789");
        assertEquals(1, s.transactions.size());
        assertTx(s.transactions.get(0), "2026-10-18", -15000, "Pix enviado Maria de Souza");
    }

    @Test
    public void sameDayDuplicatesGetDistinctIds() throws OfxException {
        OfxStatement s = parse(
                "período de visualização: 01/10/2026 até 02/10/2026",
                "02/10/2026 PIX QRS UBER | -6,94",
                "02/10/2026 PIX QRS UBER | -6,94",
                "02/10/2026 PIX TRANSF ANA | 10,00");
        assertEquals(3, s.transactions.size());
        assertNotEquals(s.transactions.get(0).importId, s.transactions.get(1).importId);
        assertTrue(s.transactions.get(0).importId.startsWith("pdf:"));
    }

    @Test(expected = OfxException.class)
    public void documentWithoutMovementsIsRejected() throws OfxException {
        parse("Contrato de abertura de conta", "Cláusula 1 | Texto qualquer");
    }

    @Test
    public void layoutSplitsColumnsByGap() {
        List<PdfLayout.Glyph> glyphs = new ArrayList<>();
        float x = 10;
        for (char c : "PIX".toCharArray()) {
            glyphs.add(new PdfLayout.Glyph(x, 100, 5, 8, String.valueOf(c)));
            x += 5;
        }
        glyphs.add(new PdfLayout.Glyph(x + 2, 100.5f, 5, 8, "A"));
        glyphs.add(new PdfLayout.Glyph(200, 101, 20, 8, "-6,94"));
        glyphs.add(new PdfLayout.Glyph(10, 120, 20, 8, "Linha 2"));
        List<List<String>> lines = PdfLayout.lines(glyphs);
        assertEquals(2, lines.size());
        assertEquals(Arrays.asList("PIX A", "-6,94"), lines.get(0));
        assertEquals(Arrays.asList("Linha 2"), lines.get(1));
    }
}
