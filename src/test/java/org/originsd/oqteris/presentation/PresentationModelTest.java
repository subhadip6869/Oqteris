package org.originsd.oqteris.presentation;

import org.junit.jupiter.api.Test;
import org.originsd.oqteris.presentation.io.PptxExporter;
import org.originsd.oqteris.presentation.io.PptxImporter;
import org.originsd.oqteris.presentation.model.*;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class PresentationModelTest {

    @Test
    public void testPresentationSlideManagement() {
        Presentation pres = new Presentation("TestDeck");
        int initialCount = pres.getSlideCount();
        assertTrue(initialCount >= 2);

        // Add new slide
        Slide newSlide = pres.createSlide("New Strategy Slide");
        assertEquals(initialCount + 1, pres.getSlideCount());

        // Duplicate slide
        pres.duplicateSlide(0);
        assertEquals(initialCount + 2, pres.getSlideCount());

        // Reorder slides
        pres.reorderSlide(0, 1);
        assertEquals(initialCount + 2, pres.getSlideCount());

        // Delete slide
        pres.removeSlide(0);
        assertEquals(initialCount + 1, pres.getSlideCount());
    }

    @Test
    public void testShapeZOrderingAndHitTest() {
        Slide slide = new Slide("CanvasTest");
        GeometricShape rect = new GeometricShape(GeometricShape.Geometry.RECTANGLE, 50, 50, 200, 100);
        TextBoxShape text = new TextBoxShape(100, 80, 150, 50, "Sample Text", 16.0, false);

        slide.addShape(rect);
        slide.addShape(text);

        // Point (120, 90) overlaps both shapes; top-most (text) should be hit
        SlideShape hit = slide.hitTest(120, 90);
        assertEquals(text, hit);

        // Bring rect to front
        slide.bringToFront(rect);
        hit = slide.hitTest(120, 90);
        assertEquals(rect, hit);
    }

    @Test
    public void testPptxRoundTrip() throws IOException {
        Presentation pres = new Presentation("PptxExportTest");
        Slide slide = pres.getSlide(0);
        slide.addShape(new GeometricShape(GeometricShape.Geometry.ROUNDED_RECTANGLE, 50, 50, 180, 90));

        File tempPptx = File.createTempFile("oqteris_presentation_", ".pptx");
        tempPptx.deleteOnExit();

        PptxExporter exporter = new PptxExporter();
        exporter.exportDocument(pres, tempPptx);

        assertTrue(tempPptx.exists());
        assertTrue(tempPptx.length() > 0);

        PptxImporter importer = new PptxImporter();
        Presentation reloaded = importer.importDocument(tempPptx);

        assertNotNull(reloaded);
        assertTrue(reloaded.getSlideCount() >= 1);
    }
}
