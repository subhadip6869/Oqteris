package org.originsd.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.Paragraph;
import org.originsd.oqteris.writer.model.TextRun;
import org.originsd.oqteris.writer.model.WriterDocument;

import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.rtf.RTFEditorKit;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Importer for Rich Text Format (.rtf) documents.
 */
public class RtfImporter implements DocumentImporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "RTF",
            "Rich Text Format (*.rtf)",
            DocumentType.WRITER,
            List.of("rtf"),
            "application/rtf",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new RtfImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".rtf");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        RTFEditorKit rtfKit = new RTFEditorKit();
        DefaultStyledDocument doc = new DefaultStyledDocument();

        try (InputStream in = new FileInputStream(file)) {
            rtfKit.read(in, doc, 0);
        } catch (Exception e) {
            throw new IOException("Failed to parse RTF document: " + e.getMessage(), e);
        }

        Element root = doc.getDefaultRootElement();
        int paraCount = root.getElementCount();

        for (int i = 0; i < paraCount; i++) {
            Element paraElem = root.getElement(i);
            Paragraph paragraph = new Paragraph();

            // Paragraph alignment
            AttributeSet paraAttrs = paraElem.getAttributes();
            int align = StyleConstants.getAlignment(paraAttrs);
            switch (align) {
                case StyleConstants.ALIGN_CENTER -> paragraph.setAlignment(Paragraph.Alignment.CENTER);
                case StyleConstants.ALIGN_RIGHT -> paragraph.setAlignment(Paragraph.Alignment.RIGHT);
                case StyleConstants.ALIGN_JUSTIFIED -> paragraph.setAlignment(Paragraph.Alignment.JUSTIFY);
                default -> paragraph.setAlignment(Paragraph.Alignment.LEFT);
            }

            int runCount = paraElem.getElementCount();
            for (int j = 0; j < runCount; j++) {
                Element runElem = paraElem.getElement(j);
                int start = runElem.getStartOffset();
                int end = runElem.getEndOffset();
                try {
                    String text = doc.getText(start, end - start);
                    // Filter out trailing newline from swing paragraph elements
                    if (j == runCount - 1 && text.endsWith("\n")) {
                        text = text.substring(0, text.length() - 1);
                    }
                    if (text.isEmpty()) continue;

                    AttributeSet attrs = runElem.getAttributes();
                    String family = StyleConstants.getFontFamily(attrs);
                    if (family == null || family.isBlank()) family = "Segoe UI";
                    int size = StyleConstants.getFontSize(attrs);
                    if (size <= 0) size = 11;
                    boolean bold = StyleConstants.isBold(attrs);
                    boolean italic = StyleConstants.isItalic(attrs);
                    boolean underline = StyleConstants.isUnderline(attrs);
                    boolean strike = StyleConstants.isStrikeThrough(attrs);

                    java.awt.Color awtColor = StyleConstants.getForeground(attrs);
                    Color textColor = Color.BLACK;
                    if (awtColor != null) {
                        textColor = Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), awtColor.getAlpha() / 255.0);
                    }

                    TextRun run = new TextRun(text, family, (double) size, bold, italic, underline, strike, textColor, null);
                    paragraph.addRun(run);
                } catch (Exception ignored) {
                }
            }

            document.addElement(paragraph);
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }
}
