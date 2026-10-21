package com.opablosantanaa.granalivre.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.opablosantanaa.granalivre.domain.ofx.OfxException;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.domain.pdf.PdfLayout;
import com.opablosantanaa.granalivre.domain.pdf.PdfStatementParser;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte um PDF de extrato ou comprovante em {@link OfxStatement}. Usa o texto do PDF com as posições de cada
 * caractere; se o PDF for só imagem (sem texto), renderiza as páginas e reconhece o texto com OCR no aparelho.
 * Deve rodar fora da thread principal.
 */
public final class PdfStatementReader {
    private static final int MAX_PAGES = 30;
    /** Abaixo disso o PDF é tratado como imagem. */
    private static final int MIN_TEXT_CHARS = 40;
    private static final int OCR_WIDTH = 2000;

    private PdfStatementReader() {}

    public static boolean isPdf(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    public static OfxStatement read(Context context, byte[] bytes) throws OfxException, IOException {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        List<PdfLayout.Line> lines = new ArrayList<>();
        int chars = 0;
        try (PDDocument document = PDDocument.load(bytes)) {
            int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
            for (int page = 1; page <= pages; page++) {
                GlyphCollector collector = new GlyphCollector();
                collector.setStartPage(page);
                collector.setEndPage(page);
                collector.getText(document);
                for (PdfLayout.Glyph glyph : collector.glyphs) chars += glyph.text().trim().length();
                lines.addAll(PdfLayout.rows(collector.glyphs, page));
            }
        } catch (InvalidPasswordException e) {
            throw new OfxException("Este PDF tem senha. Exporte o extrato sem senha no app do banco.");
        }
        if (chars < MIN_TEXT_CHARS) lines = recognize(context, bytes);
        return PdfStatementParser.parseRows(lines);
    }

    private static final class GlyphCollector extends PDFTextStripper {
        final List<PdfLayout.Glyph> glyphs = new ArrayList<>();

        GlyphCollector() throws IOException {
            super();
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            float height = Math.max(t.getHeightDir(), t.getFontSizeInPt() * 0.7f);
            glyphs.add(new PdfLayout.Glyph(t.getXDirAdj(), t.getYDirAdj(), t.getWidthDirAdj(), height, t.getUnicode()));
        }
    }

    /** OCR das páginas: cada palavra reconhecida vira um "caractere" com a sua caixa, e o layout é refeito igual. */
    private static List<PdfLayout.Line> recognize(Context context, byte[] bytes) throws IOException, OfxException {
        File file = File.createTempFile("extrato", ".pdf", context.getCacheDir());
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        List<PdfLayout.Line> lines = new ArrayList<>();
        try {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(fd)) {
                int pages = Math.min(renderer.getPageCount(), MAX_PAGES);
                for (int i = 0; i < pages; i++) {
                    Bitmap bitmap;
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        float scale = (float) OCR_WIDTH / page.getWidth();
                        bitmap = Bitmap.createBitmap(OCR_WIDTH, Math.round(page.getHeight() * scale),
                                Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                    Text text;
                    try {
                        text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)));
                    } catch (Exception e) {
                        throw new OfxException("Não foi possível ler o texto deste PDF.");
                    } finally {
                        bitmap.recycle();
                    }
                    List<PdfLayout.Glyph> glyphs = new ArrayList<>();
                    for (Text.TextBlock block : text.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            for (Text.Element element : line.getElements()) {
                                Rect box = element.getBoundingBox();
                                if (box == null) continue;
                                glyphs.add(new PdfLayout.Glyph(box.left, box.bottom, box.width(), box.height(),
                                        element.getText()));
                            }
                        }
                    }
                    lines.addAll(PdfLayout.rows(glyphs, i));
                }
            }
        } finally {
            recognizer.close();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        return lines;
    }
}
