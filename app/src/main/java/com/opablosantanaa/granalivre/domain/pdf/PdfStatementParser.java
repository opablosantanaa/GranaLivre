package com.opablosantanaa.granalivre.domain.pdf;

import com.opablosantanaa.granalivre.domain.ofx.OfxException;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.domain.ofx.OfxTransaction;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê extratos e comprovantes em PDF já convertidos em linhas/colunas por {@link PdfLayout}. Em vez de um
 * leitor por banco, usa regras que valem para os layouts dos principais bancos (Itaú, Bradesco, Caixa, Santander,
 * Inter, Nubank, Mercado Pago, PicPay): uma linha com valor em dinheiro é um lançamento (âncora); a data vem da
 * própria linha ou do último cabeçalho de dia; o texto das linhas vizinhas sem valor completa a descrição.
 */
public final class PdfStatementParser {
    private static final String MINUS = "[-−–]";
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.,])(" + MINUS + "|\\+)?\\s*(R\\$)?\\s*(" + MINUS + "|\\+)?\\s*"
                    // "1.234,56" ou, quando o OCR troca a vírgula por ponto, "234.56"
                    + "(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d{1,3}\\.\\d{2})(?![\\d,])"
                    // sufixo de sinal: "C"/"D" (Caixa) ou "(+)"/"(-)" (Banco do Brasil)
                    + "(?:\\s*([CDcd])(?![a-bd-zA-Z])|\\s*\\(([+\\-])\\))?");
    private static final Pattern SHORT_NUMBER = Pattern.compile("^\\d{1,4}$");
    private static final Pattern DAY_RANGE = Pattern.compile("(\\d{2})\\s+a\\s+(\\d{2})/(\\d{2})/(\\d{4})");
    private static final Pattern NUMERIC_DATE = Pattern.compile("^(\\d{2})[/-](\\d{2})[/-](\\d{4})");
    private static final Pattern WRITTEN_DATE = Pattern.compile(
            "^(?:[a-zçá-ú]+-?(?:feira)?,\\s*)?(\\d{1,2})\\s+(?:de\\s+)?([a-zçá-ú]{3,9})\\.?\\s+(?:de\\s+)?(\\d{4})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_NUMERIC_DATE = Pattern.compile("(\\d{2})[/-](\\d{2})[/-](\\d{4})");
    private static final Pattern TIME = Pattern.compile("^\\d{1,2}:\\d{2}(:\\d{2})?$");
    private static final Pattern DOC_NUMBER = Pattern.compile("^\\d{5,}$");
    private static final Pattern MASKED = Pattern.compile(".*[*•].*\\d.*|.*\\d.*[*•].*");
    private static final Map<String, Integer> MONTHS = new HashMap<>();

    static {
        String[] names = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
        for (int i = 0; i < names.length; i++) MONTHS.put(names[i], i + 1);
    }

    /** Valor de dinheiro encontrado num segmento: centavos com o sinal explícito, se houver. */
    static final class Amount {
        final long cents;
        final int sign;

        Amount(long cents, int sign) {
            this.cents = cents;
            this.sign = sign;
        }

        long signed() {
            return sign < 0 ? -cents : cents;
        }
    }

    private static final class Anchor {
        LocalDate date;
        Amount value;
        Amount balance;
        final List<String> text = new ArrayList<>();
        final List<String> above = new ArrayList<>();
        final List<String> below = new ArrayList<>();
        int page;
        float y = Float.NaN;
        int mode;
        long signedCents;
    }

    private PdfStatementParser() {}

    /** Linha solta (sem valor) que ainda vai ser atribuída a um lançamento. */
    private static final class Orphan {
        final String text;
        final int page;
        final float y;

        Orphan(String text, PdfLayout.Line line) {
            this.text = text;
            this.page = line.page;
            this.y = line.y;
        }
    }

    /** Linhas sem posição (testes, texto já montado): usa a regra de simetria para as linhas soltas. */
    public static OfxStatement parse(List<List<String>> lines) throws OfxException {
        List<PdfLayout.Line> rows = new ArrayList<>();
        for (List<String> line : lines) rows.add(new PdfLayout.Line(0, Float.NaN, line));
        return parseRows(rows);
    }

    public static OfxStatement parseRows(List<PdfLayout.Line> rows) throws OfxException {
        List<List<String>> lines = new ArrayList<>();
        for (PdfLayout.Line row : rows) lines.add(row.segments);
        float reach = reach(rows);
        String all = normalize(joinAll(lines));
        String bank = bank(all);
        LocalDate periodStart = null;
        LocalDate periodEnd = null;

        List<Anchor> anchors = new ArrayList<>();
        List<Orphan> orphans = new ArrayList<>();
        Anchor last = null;
        LocalDate current = null;
        int mode = 0;
        List<Map.Entry<LocalDate, Long>> dayBalances = new ArrayList<>();
        Long summaryLedger = null;
        boolean awaitingPeriodEnd = false;

        for (PdfLayout.Line row : rows) {
            List<String> line = row.segments;
            String joined = String.join(" ", line);
            String lower = normalize(joined);
            if (awaitingPeriodEnd) {
                awaitingPeriodEnd = false;
                LocalDate end = dateAtStart(line.get(0), null);
                if (end != null && line.size() == 1 && line.get(0).trim().matches(".*\\d{4}$")) {
                    periodEnd = end;
                    continue;
                }
            }
            // agendamentos ainda não realizados (Banco do Brasil) ficam fora da importação
            if (lower.startsWith("lancamentos futuros")) break;
            if (lower.startsWith("saldo final") && summaryLedger == null) {
                Amount balance = firstAmount(joined);
                if (balance != null) summaryLedger = balance.signed();
            }
            if (lower.contains("total de entradas")) {
                mode = 1;
                current = dateAtStart(joined, current);
                barrier(orphans, last, row, reach);
                last = null;
                continue;
            }
            if (lower.contains("total de saidas")) {
                mode = -1;
                barrier(orphans, last, row, reach);
                last = null;
                continue;
            }
            LocalDate[] period = period(joined);
            if (period != null) {
                if (periodStart == null) {
                    periodStart = period[0];
                    periodEnd = period[1];
                    awaitingPeriodEnd = period[1] == null;
                }
                barrier(orphans, last, row, reach);
                last = null;
                continue;
            }
            LocalDate leading = dateAtStart(joined, null);
            List<Amount> amounts = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            split(line, amounts, texts);

            boolean writtenHeader = leading != null && !NUMERIC_DATE.matcher(joined.trim()).find();
            if (writtenHeader) {
                current = leading;
                Long balance = amounts.isEmpty() ? null : amounts.get(amounts.size() - 1).signed();
                if (balance != null && lower.contains("saldo")) {
                    dayBalances.add(new HashMap.SimpleEntry<>(leading, balance));
                }
                barrier(orphans, last, row, reach);
                last = null;
                continue;
            }
            if (amounts.isEmpty()) {
                if (isTableHeader(lower) || isNoise(lower)) {
                    barrier(orphans, last, row, reach);
                    last = null;
                } else {
                    orphans.add(new Orphan(joined.trim(), row));
                }
                continue;
            }
            if (lower.startsWith("total") || lower.startsWith("saldo") && leading == null) {
                barrier(orphans, last, row, reach);
                last = null;
                continue;
            }
            LocalDate date = leading != null ? leading : current;
            if (leading != null) current = leading;
            if (isBalanceLine(lower)) {
                if (date != null) dayBalances.add(new HashMap.SimpleEntry<>(date, amounts.get(amounts.size() - 1).signed()));
                barrier(orphans, last, row, reach);
                last = null;
                continue;
            }
            if (date == null) {
                orphans.clear();
                continue;
            }
            Anchor anchor = new Anchor();
            anchor.date = date;
            anchor.value = amounts.get(0);
            anchor.balance = amounts.size() > 1 ? amounts.get(amounts.size() - 1) : null;
            anchor.mode = mode;
            anchor.page = row.page;
            anchor.y = row.y;
            anchor.text.addAll(texts);
            attach(orphans, last, anchor, reach);
            anchors.add(anchor);
            last = anchor;
        }
        barrier(orphans, last, null, reach);

        List<Anchor> moves = new ArrayList<>();
        for (Anchor a : anchors) {
            if (a.value.cents != 0) moves.add(a);
        }
        if (moves.isEmpty()) {
            OfxStatement receipt = PdfReceiptParser.parse(lines, bank);
            if (receipt != null) return receipt;
            throw new OfxException("Não encontrei lançamentos neste PDF. Confira se é um extrato ou comprovante "
                    + "de conta e se o texto pode ser selecionado.");
        }
        boolean descending = moves.get(0).date.isAfter(moves.get(moves.size() - 1).date);
        sign(descending ? reversed(anchors) : anchors);
        List<OfxTransaction> transactions = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Anchor a : moves) {
            String description = description(a);
            String key = a.date + ":" + a.signedCents + ":" + normalize(description);
            int n = seen.merge(key, 1, Integer::sum);
            String importId = "pdf:" + Integer.toHexString((bank + ":" + key + ":" + n).hashCode());
            transactions.add(new OfxTransaction(importId, a.signedCents > 0 ? "CREDIT" : "DEBIT", a.date,
                    a.signedCents, description));
        }
        if (descending) java.util.Collections.reverse(transactions);

        LocalDate start = periodStart;
        LocalDate end = periodEnd;
        if (start == null || end == null || end.isBefore(start)) {
            start = transactions.get(0).date;
            end = transactions.get(transactions.size() - 1).date;
        }
        Long ledger = null;
        LocalDate ledgerDate = null;
        for (Map.Entry<LocalDate, Long> entry : dayBalances) {
            boolean later = ledgerDate == null || entry.getKey().isAfter(ledgerDate);
            boolean sameDayLaterInText = !descending && entry.getKey().equals(ledgerDate);
            if (later || sameDayLaterInText) {
                ledgerDate = entry.getKey();
                ledger = entry.getValue();
            }
        }
        if (ledger == null && summaryLedger != null && end != null) {
            ledger = summaryLedger;
            ledgerDate = end;
        }
        for (Anchor a : descending ? moves : reversed(moves)) {
            if (a.balance == null) continue;
            if (ledgerDate == null || a.date.isAfter(ledgerDate)) {
                ledgerDate = a.date;
                ledger = a.balance.signed();
            }
            break;
        }
        return new OfxStatement("PDF".equals(bank) ? null : bank, account(lines), "BRL", false, start, end,
                ledger, ledgerDate, transactions);
    }

    private static List<Anchor> reversed(List<Anchor> list) {
        List<Anchor> copy = new ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }

    /**
     * Distribui as linhas soltas entre o lançamento anterior e o próximo. Com posições, cada linha vai para o
     * lançamento mais próximo na vertical. Sem posições, vale a simetria das tabelas centralizadas: o texto de um
     * lançamento fica metade acima, metade abaixo da linha do valor, então o anterior recebe abaixo tantas linhas
     * quantas recebeu acima, e o resto vai para cima do próximo.
     */
    private static void attach(List<Orphan> orphans, Anchor previous, Anchor next, float reach) {
        if (positioned(orphans, previous) && !Float.isNaN(next.y)) {
            for (Orphan o : orphans) {
                float toPrevious = distance(o, previous == null ? -1 : previous.page, previous == null ? 0 : previous.y,
                        reach);
                float toNext = distance(o, next.page, next.y, reach);
                if (Math.min(toPrevious, toNext) == Float.MAX_VALUE) continue;
                if (toPrevious < toNext) previous.below.add(o.text);
                else next.above.add(o.text);
            }
            orphans.clear();
            return;
        }
        int take = previous == null ? 0 : Math.min(previous.above.size(), orphans.size());
        for (int i = 0; i < take; i++) previous.below.add(orphans.get(i).text);
        for (int i = take; i < orphans.size(); i++) next.above.add(orphans.get(i).text);
        orphans.clear();
    }

    /** Linhas soltas antes de um cabeçalho/fim: ficam com o lançamento anterior só se estiverem mais perto dele. */
    private static void barrier(List<Orphan> orphans, Anchor previous, PdfLayout.Line boundary, float reach) {
        if (previous != null) {
            if (positioned(orphans, previous)) {
                for (Orphan o : orphans) {
                    float toPrevious = distance(o, previous.page, previous.y, reach);
                    float toBoundary = boundary == null || Float.isNaN(boundary.y) ? Float.MAX_VALUE / 2
                            : distance(o, boundary.page, boundary.y, reach);
                    if (toPrevious < toBoundary && toPrevious < Float.MAX_VALUE) previous.below.add(o.text);
                }
            } else {
                int take = Math.min(previous.above.size(), orphans.size());
                for (int i = 0; i < take; i++) previous.below.add(orphans.get(i).text);
            }
        }
        orphans.clear();
    }

    private static boolean positioned(List<Orphan> orphans, Anchor previous) {
        if (previous != null && Float.isNaN(previous.y)) return false;
        for (Orphan o : orphans) {
            if (Float.isNaN(o.y)) return false;
        }
        return true;
    }

    /** Distância vertical; linhas longe demais (rodapé, outra página) contam como infinitas. */
    private static float distance(Orphan o, int page, float y, float reach) {
        if (o.page != page) return Float.MAX_VALUE;
        float d = Math.abs(o.y - y);
        return d > reach ? Float.MAX_VALUE : d;
    }

    /** Até onde uma linha solta ainda pertence a um lançamento: 2,5 vezes o espaçamento típico entre linhas. */
    private static float reach(List<PdfLayout.Line> rows) {
        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            PdfLayout.Line a = rows.get(i - 1);
            PdfLayout.Line b = rows.get(i);
            if (a.page == b.page && !Float.isNaN(a.y) && !Float.isNaN(b.y) && b.y > a.y) gaps.add(b.y - a.y);
        }
        if (gaps.isEmpty()) return Float.MAX_VALUE / 4;
        java.util.Collections.sort(gaps);
        return gaps.get(gaps.size() / 2) * 2.5f;
    }

    /** Define o sinal de cada valor: sinal explícito, C/D, seção de entradas/saídas, saldo ou palavras-chave. */
    private static void sign(List<Anchor> anchors) {
        boolean explicitNegatives = false;
        for (Anchor a : anchors) {
            if (a.value.sign < 0) explicitNegatives = true;
        }
        Long previousBalance = null;
        for (Anchor a : anchors) {
            long cents = a.value.cents;
            int sign = a.value.sign;
            if (sign == 0 && a.mode != 0) sign = a.mode;
            if (sign == 0 && !explicitNegatives && previousBalance != null && a.balance != null) {
                long delta = a.balance.signed() - previousBalance;
                if (delta == cents) sign = 1;
                else if (delta == -cents) sign = -1;
            }
            if (sign == 0 && explicitNegatives) sign = 1;
            if (sign == 0) sign = keywordSign(description(a));
            if (sign == 0) sign = -1;
            a.signedCents = sign * cents;
            if (a.balance != null) previousBalance = a.balance.signed();
        }
    }

    private static int keywordSign(String description) {
        String d = normalize(description);
        if (d.matches(".*\\b(recebid[oa]|cred|credito|deposito|rem:|estorno|dev pix|devolucao|rendimento|rend pago|salario)\\b.*")) {
            return 1;
        }
        if (d.matches(".*\\b(enviad[oa]|deb|debito|pagamento|pag|compra|des:|saque|tarifa|boleto|recarga)\\b.*")) {
            return -1;
        }
        return 0;
    }

    static String description(Anchor a) {
        List<String> parts = new ArrayList<>();
        if (a.text.size() >= 2) {
            parts.addAll(a.text);
            parts.addAll(a.above);
        } else {
            parts.addAll(a.above);
            parts.addAll(a.text);
        }
        parts.addAll(a.below);
        String text = String.join(" ", parts);
        text = text.replaceAll("\"", " ")
                .replaceAll("(?i)\\bCp\\s*:\\s*\\d+-", " ")
                .replaceAll("^\\s*\\d{5}\\s+\\d{6,}\\s+", " ")
                .replaceAll(":\\s+\\d{5}\\s+\\d{6,}\\s+", ": ")
                .replaceAll("(?i)\\b(com cart[aã]o|com saldo|no estabelecimento)\\b", " ")
                .replaceAll("(?i)\\b(REM|DES):\\s*", "")
                .replaceAll("\\s*-?\\s*[•*]{2,}[^ ]*", " ")
                .replaceAll("\\b\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}\\b", " ")
                .replaceAll("\\b\\d{8,}\\b", " ")
                .replaceAll("(?i)\\b(ag[eê]ncia|conta):?\\s*[\\d.\\-]+", " ")
                .replaceAll("(\\D)(\\d{2}/\\d{2})$", "$1")
                .replaceAll("\\s+\\d{2}/\\d{2}$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.endsWith(":")) text = text.substring(0, text.length() - 1);
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }

    private static void split(List<String> line, List<Amount> amounts, List<String> texts) {
        for (int i = 0; i < line.size(); i++) {
            String segment = line.get(i).trim();
            if (i == 0) segment = stripLeadingDate(segment);
            if (segment.isEmpty()) continue;
            Matcher m = AMOUNT.matcher(segment);
            if (m.find()) {
                String before = segment.substring(0, m.start()).trim();
                String after = segment.substring(m.end()).trim();
                // uma letra sobrando depois do valor é ruído de OCR ("1.064,17 Cc")
                boolean whole = before.isEmpty() && after.length() <= 1;
                boolean labelled = !before.isEmpty() && before.endsWith(":") && after.isEmpty();
                if (whole || labelled || segment.contains("R$")) {
                    amounts.add(amount(m));
                    if (!before.isEmpty() && !labelled) texts.add(before);
                    continue;
                }
            }
            if (TIME.matcher(segment).matches() || DOC_NUMBER.matcher(segment).matches()
                    || SHORT_NUMBER.matcher(segment).matches()) continue;
            boolean startsMasked = segment.startsWith("*") || segment.startsWith("•");
            if (MASKED.matcher(segment).matches() && (startsMasked || !segment.contains(" "))) continue;
            texts.add(segment);
        }
    }

    private static Amount amount(Matcher m) {
        String digits = m.group(4).replace(".", "").replace(",", "");
        long cents = Long.parseLong(digits);
        int sign = 0;
        if (m.group(1) != null || m.group(3) != null) {
            String s = m.group(1) != null ? m.group(1) : m.group(3);
            sign = s.equals("+") ? 1 : -1;
        }
        if ("C".equalsIgnoreCase(m.group(5))) sign = 1;
        if ("D".equalsIgnoreCase(m.group(5))) sign = -1;
        if (m.group(6) != null) sign = m.group(6).equals("+") ? 1 : -1;
        return new Amount(cents, sign);
    }

    private static String stripLeadingDate(String segment) {
        Matcher numeric = NUMERIC_DATE.matcher(segment);
        if (numeric.find()) {
            String rest = segment.substring(numeric.end()).trim();
            return rest.replaceFirst("^-?\\s*\\d{1,2}:\\d{2}(:\\d{2})?", "").trim();
        }
        return segment;
    }

    static LocalDate dateAtStart(String text, LocalDate fallback) {
        String trimmed = text.trim();
        Matcher numeric = NUMERIC_DATE.matcher(trimmed);
        if (numeric.find()) return date(numeric.group(1), numeric.group(2), numeric.group(3), fallback);
        Matcher written = WRITTEN_DATE.matcher(trimmed);
        if (written.find()) {
            Integer month = MONTHS.get(normalize(written.group(2)).substring(0, 3));
            if (month == null) return fallback;
            return date(written.group(1), String.valueOf(month), written.group(3), fallback);
        }
        return fallback;
    }

    private static LocalDate date(String day, String month, String year, LocalDate fallback) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Linha de período ("01/04/2026 a 30/04/2026", "Período: ... até ..."): não é cabeçalho de dia. */
    private static LocalDate[] period(String text) {
        String lower = normalize(text);
        Matcher m = ANY_NUMERIC_DATE.matcher(text);
        List<LocalDate> dates = new ArrayList<>();
        while (m.find()) {
            LocalDate d = date(m.group(1), m.group(2), m.group(3), null);
            if (d != null) dates.add(d);
        }
        boolean labelled = lower.contains("periodo") || lower.contains("entre") || lower.contains("ate ");
        if (dates.size() >= 2 && (labelled || lower.matches(".*\\d{4}\\s+(a|al|ate)\\s+\\d.*"))) {
            return new LocalDate[]{dates.get(0), dates.get(1)};
        }
        Matcher range = DAY_RANGE.matcher(text);
        if (labelled && range.find()) {
            LocalDate end = date(range.group(2), range.group(3), range.group(4), null);
            LocalDate start = end == null ? null : date(range.group(1), range.group(3), range.group(4), null);
            if (start != null) return new LocalDate[]{start, end};
        }
        Matcher written = WRITTEN_DATE.matcher(text.trim());
        if (written.find() && lower.matches(".*\\d{4}\\s+a(\\s.*|$)")) {
            LocalDate first = dateAtStart(text, null);
            if (first == null) return null;
            String rest = text.trim().substring(written.end()).replaceFirst("^\\s*a\\s*", "");
            LocalDate second = dateAtStart(rest.replaceFirst("^\\|\\s*", ""), null);
            return new LocalDate[]{first, second};
        }
        return null;
    }

    private static boolean isBalanceLine(String lower) {
        return lower.matches(".*\\bsaldo (do dia|dia|anterior|inicial|final|em conta|disponivel|total).*")
                || lower.matches("^saldo\\b.*") || lower.contains("cod. lanc") || lower.contains("s a l d o");
    }

    private static boolean isTableHeader(String lower) {
        int hits = 0;
        for (String word : new String[]{"data", "hora", "descricao", "historico", "valor", "saldo", "lancamentos",
                "tipo", "docto", "credito", "debito"}) {
            if (lower.matches(".*\\b" + word + "\\b.*")) hits++;
        }
        return hits >= 2;
    }

    private static boolean isNoise(String lower) {
        return lower.startsWith("data:") || lower.startsWith("folha") || lower.matches("^\\d+\\s*(/|de)\\s*\\d+$")
                // rodapés de atendimento
                || lower.matches("^(fale com a gente|sac\\b|ouvidoria|central de atendimento|duvidas\\?|alo ).*")
                // logotipo/cabeçalho repetido no topo de cada página
                || lower.matches("^#?(caixa|bradesco( celular)?|itau|nubank|inter|santander|banco do brasil|picpay"
                + "|mercado pago|c6 bank|pessoal)$");
    }

    private static String account(List<List<String>> lines) {
        Pattern p = Pattern.compile("(?i)conta:?\\s*([\\d.\\-/]{4,})");
        for (int i = 0; i < lines.size(); i++) {
            String joined = String.join(" ", lines.get(i)).trim();
            Matcher m = p.matcher(joined);
            if (m.find()) return m.group(1);
            boolean labelAtEnd = normalize(joined).endsWith(" conta") || normalize(joined).endsWith(" conta:");
            if (labelAtEnd && i + 1 < lines.size()) {
                String next = String.join(" ", lines.get(i + 1)).trim();
                if (next.matches("[\\d.\\-/]{4,}")) return next;
            }
        }
        return null;
    }

    static String bank(String all) {
        if (all.contains("nubank") || all.contains("nu pagamentos")) return "Nubank";
        if (all.contains("banco inter")) return "Inter";
        if (all.contains("mercado pago") || all.contains("mercadopago")) return "Mercado Pago";
        if (all.contains("picpay")) return "PicPay";
        if (all.contains("bradesco")) return "Bradesco";
        if (all.contains("caixa")) return "Caixa";
        if (all.contains("itau.com.br") || all.contains("extrato conta / lancamentos")) return "Itaú";
        if (all.contains("santander")) return "Santander";
        if (all.contains("banco do brasil") || all.contains("bb.com.br") || all.contains("s a l d o")) {
            return "Banco do Brasil";
        }
        if (all.contains("c6 bank") || all.contains("banco c6")) return "C6 Bank";
        return "PDF";
    }

    private static String joinAll(List<List<String>> lines) {
        StringBuilder sb = new StringBuilder();
        for (List<String> line : lines) sb.append(String.join(" ", line)).append('\n');
        return sb.toString();
    }

    static String normalize(String text) {
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    static Amount firstAmount(String text) {
        Matcher m = AMOUNT.matcher(text);
        return m.find() ? amount(m) : null;
    }
}
