package com.opablosantanaa.granalivre.domain.pdf;

import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.domain.ofx.OfxTransaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comprovante de uma única operação (Pix, TED, boleto, pagamento): vira um extrato com um lançamento.
 * Procura o valor rotulado ("Valor"), a data e o nome da outra parte ("Para", "Recebedor", "De", "Pagador"...).
 */
final class PdfReceiptParser {
    private static final String[] TO = {"destino", "para", "recebedor", "favorecido", "beneficiario",
            "nome do recebedor", "quem recebeu", "pago a"};
    private static final String[] FROM = {"origem", "de", "pagador", "remetente", "quem pagou", "nome do pagador"};

    private PdfReceiptParser() {}

    static OfxStatement parse(List<List<String>> lines, String bank) {
        String all = PdfStatementParser.normalize(join(lines));
        if (!all.contains("comprovante") && !all.contains("autenticacao") && !all.contains("id da transacao")
                && !all.contains("transacao efetuada") && !all.contains("pagamento efetuado")) {
            return null;
        }
        PdfStatementParser.Amount amount = null;
        LocalDate date = null;
        String to = null;
        String from = null;
        for (int i = 0; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            String joined = String.join(" ", line);
            String lower = PdfStatementParser.normalize(joined);
            if (amount == null && lower.matches("^(valor|valor pago|valor da transferencia|valor do pagamento|total)\\b.*")) {
                amount = PdfStatementParser.firstAmount(joined);
                if (amount == null && i + 1 < lines.size()) {
                    amount = PdfStatementParser.firstAmount(String.join(" ", lines.get(i + 1)));
                }
            }
            if (date == null) date = findDate(line);
            if (to == null) to = labelled(lines, i, TO);
            if (from == null) from = labelled(lines, i, FROM);
        }
        if (amount == null) {
            for (List<String> line : lines) {
                String joined = String.join(" ", line);
                if (joined.contains("R$")) {
                    amount = PdfStatementParser.firstAmount(joined);
                    if (amount != null) break;
                }
            }
        }
        if (amount == null || amount.cents == 0 || date == null) return null;
        boolean income = all.contains("pix recebido") || all.contains("transferencia recebida")
                || all.contains("comprovante de recebimento") || all.contains("voce recebeu");
        String kind = all.contains("pix") ? "Pix" : all.contains("boleto") ? "Boleto"
                : all.contains("ted") || all.contains("transferencia") ? "Transferência" : "Pagamento";
        String other = income ? from : to;
        String description = kind + (income ? " recebido" : " enviado") + (other == null ? "" : " " + other);
        long cents = income ? amount.cents : -amount.cents;
        String importId = "pdf:" + Integer.toHexString((bank + ":" + date + ":" + cents + ":"
                + PdfStatementParser.normalize(description)).hashCode());
        List<OfxTransaction> list = new ArrayList<>(Collections.singletonList(
                new OfxTransaction(importId, income ? "CREDIT" : "DEBIT", date, cents, description)));
        return new OfxStatement(bank, null, "BRL", false, date, date, null, null, list);
    }

    private static LocalDate findDate(List<String> line) {
        for (String segment : line) {
            LocalDate d = PdfStatementParser.dateAtStart(segment, null);
            if (d != null) return d;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})").matcher(segment);
            if (m.find()) {
                try {
                    return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(1)));
                } catch (RuntimeException ignored) {
                    // data inválida, continua procurando
                }
            }
        }
        return null;
    }

    /** Nome logo após um rótulo: na mesma linha ("Para: Fulano" / "Para | Fulano") ou na linha seguinte. */
    private static String labelled(List<List<String>> lines, int index, String[] labels) {
        List<String> line = lines.get(index);
        String first = PdfStatementParser.normalize(line.get(0)).replaceAll("[:\\s]+$", "");
        for (String label : labels) {
            if (first.equals(label)) {
                if (line.size() > 1) return clean(line.get(1));
                if (index + 1 < lines.size()) {
                    List<String> next = lines.get(index + 1);
                    String nextLabel = PdfStatementParser.normalize(next.get(0)).replaceAll("[:\\s]+$", "");
                    if (next.size() > 1 && nextLabel.startsWith("nome")) return clean(next.get(1));
                    return clean(next.get(0));
                }
            }
            if (first.startsWith(label + ":")) return clean(line.get(0).substring(line.get(0).indexOf(':') + 1));
        }
        return null;
    }

    private static String clean(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty() || t.matches(".*\\d{3}.*")) return null;
        return t.length() > 60 ? t.substring(0, 60).trim() : t;
    }

    private static String join(List<List<String>> lines) {
        StringBuilder sb = new StringBuilder();
        for (List<String> line : lines) sb.append(String.join(" ", line)).append('\n');
        return sb.toString();
    }
}
