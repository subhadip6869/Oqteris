package org.oqteris.presentation.io;

import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.*;
import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.presentation.model.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Exporter for Office Open XML Presentations (.pptx).
 */
public class PptxExporter implements DocumentExporter<Presentation> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "PPTX",
            "PowerPoint Presentation (*.pptx)",
            DocumentType.IMPRESS,
            List.of("pptx"),
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerExporter(new PptxExporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canExport(File file) {
        if (file == null) return false;
        return file.getName().toLowerCase().endsWith(".pptx");
    }

    @Override
    public void exportDocument(Presentation document, File file) throws IOException {
        try (XMLSlideShow poiShow = new XMLSlideShow()) {
            poiShow.setPageSize(new Dimension((int) document.getSlideWidth(), (int) document.getSlideHeight()));

            for (Slide slide : document.getSlides()) {
                XSLFSlide poiSlide = poiShow.createSlide();

                for (SlideShape shape : slide.getShapes()) {
                    Rectangle2D anchor = new Rectangle2D.Double(shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight());

                    if (shape instanceof TextBoxShape tb) {
                        XSLFTextBox poiText = poiSlide.createTextBox();
                        poiText.setAnchor(anchor);
                        String[] lines = tb.getText().split("\n");
                        for (String line : lines) {
                            XSLFTextParagraph p = poiText.addNewTextParagraph();
                            XSLFTextRun r = p.addNewTextRun();
                            r.setText(line);
                            r.setFontFamily(tb.getFontFamily());
                            r.setFontSize(tb.getFontSize());
                            r.setBold(tb.isBold());
                            r.setItalic(tb.isItalic());

                            if (tb.getTextColor() != null) {
                                r.setFontColor(new Color(
                                        (float) tb.getTextColor().getRed(),
                                        (float) tb.getTextColor().getGreen(),
                                        (float) tb.getTextColor().getBlue()
                                ));
                            }
                        }

                    } else if (shape instanceof GeometricShape gs) {
                        XSLFAutoShape poiShape = poiSlide.createAutoShape();
                        poiShape.setAnchor(anchor);
                        ShapeType st = switch (gs.getGeometry()) {
                            case ROUNDED_RECTANGLE -> ShapeType.ROUND_RECT;
                            case ELLIPSE -> ShapeType.ELLIPSE;
                            case TRIANGLE -> ShapeType.TRIANGLE;
                            default -> ShapeType.RECT;
                        };
                        poiShape.setShapeType(st);

                        if (gs.getFillColor() != null) {
                            poiShape.setFillColor(new Color(
                                    (float) gs.getFillColor().getRed(),
                                    (float) gs.getFillColor().getGreen(),
                                    (float) gs.getFillColor().getBlue()
                            ));
                        }
                    }
                }
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                poiShow.write(out);
            }
        }
    }
}
