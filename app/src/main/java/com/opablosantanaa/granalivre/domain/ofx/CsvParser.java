package com.opablosantanaa.granalivre.domain.ofx;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CsvParser {
    private static final Pattern BR_DATE = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{2,4})");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    private CsvParser() {}

    public static OfxStatement parse(byte[] bytes) throws OfxException {
        return parse(OfxParser.decode(bytes));
    }

    public static OfxStatement parse(String text) throws OfxException {
        String[] lines = text.replace("﻿", "").split("\\r?\\n");
        int headerLine = -1;
        int date = -1;
        int amount = -1;
        int description = -1;
        int id = -1;
        char separator = ',';
        boolean invoiceHeader = false;
        boolean statementHeader = false;
        for (int i = 0; i < lines.length && headerLine < 0; i++) {
            if (lines[i].trim().isEmpty()) continue;
            separator = count(lines[i], ';') > count(lines[i], ',') ? ';' : ',';
            List<String> cells = split(lines[i], separator);
            date = amount = description = id = -1;
            invoiceHeader = statementHeader = false;
            for (int c = 0; c < cells.size(); c++) {
                String name = normalize(cells.get(c));
                invoiceHeader |= name.startsWith("title") || name.startsWith("parcela")
                        || name.startsWith("estabelecimento") || name.contains("cartao");
                statementHeader |= name.startsWith("saldo") || name.startsWith("identificador");
                if (date < 0 && (name.startsWith("data") || name.startsWith("date"))) date = c;
                else if (amount < 0 && (name.startsWith("valor") || name.startsWith("amount") || name.equals("value"))) amount = c;
                else if (id < 0 && (name.startsWith("identificador") || name.equals("id"))) id = c;
                else if (description < 0 && (name.startsWith("descri") || name.startsWith("title")
                        || name.startsWith("histor") || name.startsWith("lancamento")
                        || name.startsWith("estabelecimento") || name.startsWith("memo"))) description = c;
            }
            if (date >= 0 && amount >= 0) headerLine = i;
        }
        if (headerLine < 0) throw new OfxException("O arquivo não parece ser um extrato OFX ou CSV.");

        List<OfxTransaction> transactions = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        LocalDate start = null;
        LocalDate end = null;
        int positive = 0;
        int negative = 0;
        for (int i = headerLine + 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) continue;
            List<String> cells = split(lines[i], separator);
            if (cells.size() <= Math.max(date, amount)) continue;
            String rawAmount = cells.get(amount).replace("R$", "").replace(' ', ' ').trim();
            if (rawAmount.isEmpty()) continue;
            LocalDate day = parseDate(cells.get(date));
            long cents = OfxParser.parseAmount(rawAmount);
            if (cents == 0) continue;
            if (cents > 0) positive++;
            else negative++;
            String memo = description >= 0 && description < cells.size() ? cells.get(description).trim() : "";
            String fitId = id >= 0 && id < cells.size() ? cells.get(id).trim() : "";
            String importId;
            if (!fitId.isEmpty()) {
                importId = "fit:" + fitId;
            } else {
                String key = day + "|" + cents + "|" + memo;
                int occurrence = seen.containsKey(key) ? seen.get(key) + 1 : 0;
                seen.put(key, occurrence);
                importId = "hash:" + OfxParser.hash(key + "|" + occurrence);
            }
            transactions.add(new OfxTransaction(importId, "", day, cents, memo));
            if (start == null || day.isBefore(start)) start = day;
            if (end == null || day.isAfter(end)) end = day;
        }
        boolean invoice = invoiceHeader || (!statementHeader && positive > negative);
        return new OfxStatement(null, null, "BRL", invoice, start, end, null, null, transactions);
    }

    static LocalDate parseDate(String raw) throws OfxException {
        String value = raw.trim();
        try {
            Matcher iso = ISO_DATE.matcher(value);
            if (iso.find()) {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                        Integer.parseInt(iso.group(3)));
            }
            Matcher br = BR_DATE.matcher(value);
            if (br.find()) {
                int year = Integer.parseInt(br.group(3));
                return LocalDate.of(year < 100 ? 2000 + year : year, Integer.parseInt(br.group(2)),
                        Integer.parseInt(br.group(1)));
            }
        } catch (DateTimeException ignored) {
        }
        throw new OfxException("Data inválida no extrato: " + raw);
    }

    static List<String> split(String line, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == separator && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private static int count(String text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == c) n++;
        return n;
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
