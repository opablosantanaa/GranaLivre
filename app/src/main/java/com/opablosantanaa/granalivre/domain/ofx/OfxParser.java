package com.opablosantanaa.granalivre.domain.ofx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OfxParser {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private OfxParser() {}

    public static OfxStatement parse(byte[] bytes) throws OfxException {
        return parse(decode(bytes));
    }

    static String decode(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 600), StandardCharsets.ISO_8859_1)
                .toUpperCase(Locale.ROOT);
        boolean declaresLatin = head.contains("CHARSET:1252") || head.contains("CHARSET:ISO-8859-1")
                || head.contains("ENCODING=\"ISO-8859-1\"") || head.contains("ENCODING=\"WINDOWS-1252\"");
        if (!declaresLatin) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ignored) {
                // Arquivo sem declaração confiável: os bancos brasileiros usam windows-1252 nesse caso.
            }
        }
        return new String(bytes, WINDOWS_1252);
    }

    static long parseAmount(String raw) throws OfxException {
        String value = raw.trim().replace(" ", "").replace("+", "");
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) value = value.replace(".", "").replace(',', '.');
            else value = value.replace(",", "");
        } else if (comma >= 0) {
            value = value.replace(',', '.');
        } else if (dot >= 0 && value.indexOf('.') != dot) {
            value = value.replace(".", "");
        }
        try {
            return new BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new OfxException("Valor inválido no extrato: " + raw);
        }
    }

    static LocalDate parseDate(String raw) throws OfxException {
        String value = raw.trim();
        if (value.length() < 8) throw new OfxException("Data inválida no extrato: " + raw);
        try {
            int year = Integer.parseInt(value.substring(0, 4));
            int month = Integer.parseInt(value.substring(4, 6));
            int day = Integer.parseInt(value.substring(6, 8));
            return LocalDate.of(year, month, day);
        } catch (NumberFormatException | DateTimeException e) {
            throw new OfxException("Data inválida no extrato: " + raw);
        }
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&nbsp;", " ").replace("&amp;", "&");
    }

    private static String collapse(String text) {
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space) out.append(' ');
            out.append(c);
            space = false;
        }
        return out.toString();
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 10; i++) out.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static OfxStatement parse(String text) throws OfxException {
        int start = text.toUpperCase(Locale.ROOT).indexOf("<OFX>");
        if (start < 0) throw new OfxException("O arquivo não parece ser um extrato OFX.");

        Map<String, String> header = new HashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> current = null;
        boolean inLedger = false;
        boolean creditCard = false;
        Long ledger = null;
        LocalDate ledgerDate = null;

        int i = start;
        int length = text.length();
        while (i < length) {
            int open = text.indexOf('<', i);
            if (open < 0) break;
            int close = text.indexOf('>', open);
            if (close < 0) break;
            String tag = text.substring(open + 1, close).trim().toUpperCase(Locale.ROOT);
            int next = text.indexOf('<', close);
            String value = unescape(text.substring(close + 1, next < 0 ? length : next)).trim();
            i = close + 1;
            if (tag.isEmpty() || tag.startsWith("?") || tag.startsWith("!")) continue;
            int space = tag.indexOf(' ');
            if (space > 0) tag = tag.substring(0, space);

            if (tag.startsWith("/")) {
                String name = tag.substring(1);
                if (name.equals("STMTTRN") && current != null) {
                    rows.add(current);
                    current = null;
                } else if (name.equals("BANKTRANLIST") && current != null) {
                    rows.add(current);
                    current = null;
                } else if (name.equals("LEDGERBAL")) {
                    inLedger = false;
                }
                continue;
            }
            switch (tag) {
                case "STMTTRN":
                    if (current != null) rows.add(current);
                    current = new HashMap<>();
                    continue;
                case "LEDGERBAL":
                    inLedger = true;
                    continue;
                case "AVAILBAL":
                    inLedger = false;
                    continue;
                case "CCSTMTRS":
                    creditCard = true;
                    continue;
                default:
                    break;
            }
            if (value.isEmpty()) continue;
            if (current != null) {
                current.put(tag, value);
            } else if (inLedger && tag.equals("BALAMT")) {
                ledger = parseAmount(value);
            } else if (inLedger && tag.equals("DTASOF")) {
                ledgerDate = parseDate(value);
            } else if (!header.containsKey(tag)) {
                header.put(tag, value);
            }
        }
        if (current != null) rows.add(current);
        return build(header, rows, creditCard, ledger, ledgerDate);
    }

    private static OfxStatement build(Map<String, String> header, List<Map<String, String>> rows, boolean creditCard,
                                      Long ledger, LocalDate ledgerDate) throws OfxException {
        String account = header.get("ACCTID");
        List<OfxTransaction> transactions = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Map<String, String> row : rows) {
            String rawDate = row.get("DTPOSTED");
            String rawAmount = row.get("TRNAMT");
            if (rawDate == null || rawAmount == null) continue;
            LocalDate date = parseDate(rawDate);
            long amount = parseAmount(rawAmount);
            if (amount == 0) continue;
            String memo = row.get("MEMO");
            String name = row.get("NAME");
            String description = collapse(memo != null && !memo.isEmpty() ? memo : name == null ? "" : name);
            String fitId = row.get("FITID");
            String importId;
            if (fitId != null && !fitId.trim().isEmpty()) {
                importId = "fit:" + fitId.trim();
            } else {
                String key = date + "|" + amount + "|" + description;
                int occurrence = seen.containsKey(key) ? seen.get(key) + 1 : 0;
                seen.put(key, occurrence);
                importId = "hash:" + hash(key + "|" + occurrence);
            }
            String type = row.get("TRNTYPE");
            transactions.add(new OfxTransaction(importId, type == null ? "" : type.toUpperCase(Locale.ROOT),
                    date, amount, description));
        }
        LocalDate start = header.containsKey("DTSTART") ? parseDate(header.get("DTSTART")) : null;
        LocalDate end = header.containsKey("DTEND") ? parseDate(header.get("DTEND")) : null;
        return new OfxStatement(header.get("BANKID"), account, header.get("CURDEF"), creditCard, start, end,
                ledger, ledgerDate, transactions);
    }
}
