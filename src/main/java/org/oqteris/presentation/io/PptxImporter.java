package org.oqteris.presentation.io;

import org.apache.poi.xslf.usermodel.*;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.presentation.model.*;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Importer for Office Open XML Presentations (.pptx).
 */
public class PptxImporter implements DocumentImporter<Presentation> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "PPTX",
            "PowerPoint Presentation (*.pptx)",
            DocumentType.IMPRESS,
            List.of("pptx", "pptm"),
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new PptxImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        if (file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".pptx") || name.endsWith(".pptm");
    }

    @Override
    public Presentation importDocument(File file) throws IOException {
        Presentation presentation = new Presentation(file.getName());
        presentation.setFile(file);

        try (FileInputStream in = new FileInputStream(file);
             XMLSlideShow poiShow = new XMLSlideShow(in)) {

            // Slide dimensions
            java.awt.Dimension pgSize = poiShow.getPageSize();
            if (pgSize != null) {
                presentation.setSlideWidth(pgSize.getWidth());
                presentation.setSlideHeight(pgSize.getHeight());
            }

            // Clear default starter slides before importing from file
            presentation.clearSlides();

            for (XSLFSlide poiSlide : poiShow.getSlides()) {
                String slideTitle = poiSlide.getTitle() != null ? poiSlide.getTitle() : "Slide " + (presentation.getSlideCount() + 1);
                Slide slide = new Slide(slideTitle);

                for (XSLFShape poiShape : poiSlide.getShapes()) {
                    Rectangle2D anchor = poiShape.getAnchor();
                    double x = anchor != null ? anchor.getX() : 50;
                    double y = anchor != null ? anchor.getY() : 50;
                    double w = anchor != null ? anchor.getWidth() : 200;
                    double h = anchor != null ? anchor.getHeight() : 100;

                    if (poiShape instanceof XSLFTextShape ts) {
                        String text = ts.getText();
                        TextBoxShape tbs = new TextBoxShape(x, y, w, h, text, 18.0, false);
                        slide.addShape(tbs);
                    } else if (poiShape instanceof XSLFAutoShape as) {
                        GeometricShape gs = new GeometricShape(GeometricShape.Geometry.RECTANGLE, x, y, w, h);
                        slide.addShape(gs);
                    } else if (poiShape instanceof XSLFTable table) {
                        TableShape tbl = new TableShape(table.getNumberOfRows(), table.getNumberOfColumns(), x, y, w, h);
                        slide.addShape(tbl);
                    }
                }

                presentation.addSlide(slide);
            }
        }

        if (presentation.getSlideCount() == 0) {
            presentation.createSlide("Slide 1");
        }

        presentation.setModified(false);
        return presentation;
    }
}
