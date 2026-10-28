package org.oqteris.presentation.io;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.oqteris.presentation.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Direct PDF export engine for presentation slide decks using Apache PDFBox.
 */
public class PresentationPdfExporter {

    public static void exportToPdf(Presentation presentation, File targetPdf) throws IOException {
        if (presentation == null || targetPdf == null) return;

        try (PDDocument pdfDoc = new PDDocument()) {
            float slideW = (float) presentation.getSlideWidth();
            float slideH = (float) presentation.getSlideHeight();
            PDRectangle mediaBox = new PDRectangle(slideW, slideH);

            for (Slide slide : presentation.getSlides()) {
                PDPage page = new PDPage(mediaBox);
                pdfDoc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(pdfDoc, page)) {
                    // Draw slide background
                    if (slide.getBackgroundColor() != null) {
                        cs.setNonStrokingColor(
                                (float) slide.getBackgroundColor().getRed(),
                                (float) slide.getBackgroundColor().getGreen(),
                                (float) slide.getBackgroundColor().getBlue()
                        );
                        cs.addRect(0, 0, slideW, slideH);
                        cs.fill();
                    }

                    // Render shapes
                    for (SlideShape shape : slide.getShapes()) {
                        float x = (float) shape.getX();
                        // In PDF, Y=0 is at bottom
                        float y = slideH - (float) (shape.getY() + shape.getHeight());
                        float w = (float) shape.getWidth();
                        float h = (float) shape.getHeight();

                        if (shape instanceof GeometricShape gs) {
                            if (gs.getFillColor() != null) {
                                cs.setNonStrokingColor(
                                        (float) gs.getFillColor().getRed(),
                                        (float) gs.getFillColor().getGreen(),
                                        (float) gs.getFillColor().getBlue()
                                );
                                cs.addRect(x, y, w, h);
                                cs.fill();
                            }
                        } else if (shape instanceof TextBoxShape tb) {
                            cs.beginText();
                            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), (float) Math.min(24.0, tb.getFontSize()));
                            if (tb.getTextColor() != null) {
                                cs.setNonStrokingColor(
                                        (float) tb.getTextColor().getRed(),
                                        (float) tb.getTextColor().getGreen(),
                                        (float) tb.getTextColor().getBlue()
                                );
                            }
                            cs.newLineAtOffset(x, y + h - (float) tb.getFontSize());

                            String[] lines = tb.getText().split("\n");
                            for (int i = 0; i < lines.length; i++) {
                                // Strip unprintable chars for standard Type 1 font
                                String clean = lines[i].replaceAll("[^\\x20-\\x7E]", " ");
                                cs.showText(clean);
                                if (i < lines.length - 1) {
                                    cs.newLineAtOffset(0, (float) -tb.getFontSize() * 1.3f);
                                }
                            }
                            cs.endText();
                        }
                    }
                }
            }

            pdfDoc.save(targetPdf);
        }
    }
}
