package com.opablosantanaa.granalivre.domain.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstrói linhas e colunas a partir das posições dos caracteres de um PDF. Cada linha vira uma lista de
 * segmentos: trechos de texto separados por um espaço horizontal grande (colunas da tabela do extrato).
 */
public final class PdfLayout {
    public static final class Glyph {
        final float x;
        final float y;
        final float width;
        final float height;
        final String text;

        public Glyph(float x, float y, float width, float height, String text) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
        }

        public String text() {
            return text == null ? "" : text;
        }
    }

    /** Uma linha do documento: página, altura (base do texto) e colunas. */
    public static final class Line {
        public final int page;
        public final float y;
        public final List<String> segments;

        public Line(int page, float y, List<String> segments) {
            this.page = page;
            this.y = y;
            this.segments = segments;
        }
    }

    private PdfLayout() {}

    public static List<List<String>> lines(List<Glyph> glyphs) {
        List<List<String>> lines = new ArrayList<>();
        for (Line line : rows(glyphs, 0)) lines.add(line.segments);
        return lines;
    }

    public static List<Line> rows(List<Glyph> glyphs, int page) {
        List<Glyph> sorted = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.text != null && !g.text.isEmpty()) sorted.add(g);
        }
        sorted.sort((a, b) -> a.y != b.y ? Float.compare(a.y, b.y) : Float.compare(a.x, b.x));
        List<List<Glyph>> rows = new ArrayList<>();
        List<Glyph> row = null;
        float rowY = 0;
        float rowHeight = 0;
        for (Glyph g : sorted) {
            float tolerance = Math.max(1.5f, Math.min(rowHeight, g.height) * 0.45f);
            if (row == null || Math.abs(g.y - rowY) > tolerance) {
                row = new ArrayList<>();
                rows.add(row);
                rowY = g.y;
                rowHeight = g.height;
            }
            row.add(g);
        }
        List<Line> lines = new ArrayList<>();
        for (List<Glyph> line : rows) {
            float baseline = line.get(0).y;
            line.sort((a, b) -> Float.compare(a.x, b.x));
            List<String> segments = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            float end = Float.NaN;
            for (Glyph g : line) {
                float size = Math.max(g.height, 4f);
                if (!Float.isNaN(end)) {
                    float gap = g.x - end;
                    if (gap > size * 1.2f) {
                        flush(segments, current);
                    } else if (gap > size * 0.18f && current.length() > 0
                            && current.charAt(current.length() - 1) != ' ' && !g.text.startsWith(" ")) {
                        current.append(' ');
                    }
                }
                current.append(g.text);
                end = Math.max(Float.isNaN(end) ? g.x + g.width : end, g.x + g.width);
                if (g.text.isEmpty() || g.text.trim().isEmpty()) end = g.x + g.width;
            }
            flush(segments, current);
            if (!segments.isEmpty()) lines.add(new Line(page, baseline, segments));
        }
        return lines;
    }

    private static void flush(List<String> segments, StringBuilder current) {
        String text = current.toString().replaceAll("\\s+", " ").trim();
        if (!text.isEmpty()) segments.add(text);
        current.setLength(0);
    }

    /** Formato de depuração e dos testes: segmentos separados por " | ". */
    public static String format(List<String> line) {
        return String.join(" | ", line);
    }
}
