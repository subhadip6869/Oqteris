package org.originsd.oqteris.ui;

import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.textfield.TextFields;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.RibbonTab;
import com.pixelduke.control.ribbon.RibbonGroup;
import org.originsd.oqteris.writer.model.WriterDocument;
import org.originsd.oqteris.writer.ui.WriterDocumentView;
import org.originsd.oqteris.calc.model.Workbook;
import org.originsd.oqteris.calc.ui.CalcDocumentView;
import org.originsd.oqteris.presentation.model.Presentation;
import org.originsd.oqteris.presentation.model.Slide;
import org.originsd.oqteris.presentation.ui.PresentationDocumentView;

import static org.junit.jupiter.api.Assertions.*;

public class UiLibraryTest {

    @org.junit.jupiter.api.BeforeAll
    public static void initJfx() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void testLibrariesPresent() {
        assertNotNull(SegmentedButton.class);
        assertNotNull(TextFields.class);
        assertNotNull(JMetro.class);
        assertNotNull(Style.LIGHT);
        assertNotNull(Style.DARK);
        assertNotNull(Ribbon.class);
        assertNotNull(RibbonTab.class);
        assertNotNull(RibbonGroup.class);

        Ribbon ribbon = new Ribbon();
        RibbonTab tab = new RibbonTab("Home");
        RibbonGroup group = new RibbonGroup();
        group.setTitle("Font");
        tab.getRibbonGroups().add(group);
        ribbon.getTabs().add(tab);
        assertEquals(1, ribbon.getTabs().size());

        // Verify icon constants
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_BOLD, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_ITALIC, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_UNDERLINE, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_STRIKETHROUGH, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_ALIGN_LEFT, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_ALIGN_CENTER, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_ALIGN_RIGHT, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_ALIGN_JUSTIFY, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_LIST_BULLETED, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FORMAT_LIST_NUMBERED, 16));
        assertNotNull(FontIcon.of(MaterialDesignT.TABLE, 16));
        assertNotNull(FontIcon.of(MaterialDesignI.IMAGE, 16));
        assertNotNull(FontIcon.of(MaterialDesignL.LINK, 16));
        assertNotNull(FontIcon.of(MaterialDesignC.CONTENT_CUT, 16));
        assertNotNull(FontIcon.of(MaterialDesignC.CONTENT_COPY, 16));
        assertNotNull(FontIcon.of(MaterialDesignC.CONTENT_PASTE, 16));
        assertNotNull(FontIcon.of(MaterialDesignU.UNDO, 16));
        assertNotNull(FontIcon.of(MaterialDesignR.REDO, 16));
        assertNotNull(FontIcon.of(MaterialDesignM.MAGNIFY, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FIND_REPLACE, 16));
        assertNotNull(FontIcon.of(MaterialDesignC.CHART_BAR, 16));
        assertNotNull(FontIcon.of(MaterialDesignS.SIGMA, 16));
        assertNotNull(FontIcon.of(MaterialDesignP.PRINTER, 16));
        assertNotNull(FontIcon.of(MaterialDesignS.SHIELD_CHECK, 16));
        assertNotNull(FontIcon.of(MaterialDesignS.SHIELD_ALERT, 16));
        assertNotNull(FontIcon.of(MaterialDesignC.CHEVRON_LEFT, 16));
        assertNotNull(FontIcon.of(MaterialDesignC.CHEVRON_RIGHT, 16));
        assertNotNull(FontIcon.of(MaterialDesignP.PAGE_FIRST, 16));
        assertNotNull(FontIcon.of(MaterialDesignP.PAGE_LAST, 16));
        assertNotNull(FontIcon.of(MaterialDesignR.ROTATE_RIGHT, 16));
        assertNotNull(FontIcon.of(MaterialDesignV.VIEW_SPLIT_VERTICAL, 16));
    }

    @Test
    public void testDocumentViewRibbons() {
        WriterDocument doc = new WriterDocument();
        WriterDocumentView writerView = new WriterDocumentView(doc);
        assertNotNull(writerView.getRibbonToolbar());
        assertInstanceOf(Ribbon.class, writerView.getRibbonToolbar());
        Ribbon writerRibbon = (Ribbon) writerView.getRibbonToolbar();
        assertEquals(4, writerRibbon.getTabs().size());
        assertEquals("Home", writerRibbon.getTabs().get(0).getText());
        assertEquals("Insert", writerRibbon.getTabs().get(1).getText());
        assertEquals("Layout", writerRibbon.getTabs().get(2).getText());
        assertEquals("Review & View", writerRibbon.getTabs().get(3).getText());

        Workbook wb = new Workbook();
        CalcDocumentView calcView = new CalcDocumentView(wb);
        assertNotNull(calcView.getRibbonToolbar());
        assertInstanceOf(Ribbon.class, calcView.getRibbonToolbar());
        Ribbon calcRibbon = (Ribbon) calcView.getRibbonToolbar();
        assertEquals(4, calcRibbon.getTabs().size());
        assertEquals("Home", calcRibbon.getTabs().get(0).getText());
        assertEquals("Insert", calcRibbon.getTabs().get(1).getText());
        assertEquals("Formulas & Data", calcRibbon.getTabs().get(2).getText());
        assertEquals("View", calcRibbon.getTabs().get(3).getText());

        Presentation pres = new Presentation();
        pres.addSlide(new Slide());
        PresentationDocumentView presView = new PresentationDocumentView(pres);
        assertNotNull(presView.getRibbonToolbar());
        assertInstanceOf(Ribbon.class, presView.getRibbonToolbar());
        Ribbon presRibbon = (Ribbon) presView.getRibbonToolbar();
        assertEquals(4, presRibbon.getTabs().size());
        assertEquals("Home", presRibbon.getTabs().get(0).getText());
        assertEquals("Insert", presRibbon.getTabs().get(1).getText());
        assertEquals("Design", presRibbon.getTabs().get(2).getText());
        assertEquals("Slide Show", presRibbon.getTabs().get(3).getText());

        java.io.File pdfFile = new java.io.File("samples", "sample_signed.pdf");
        if (pdfFile.exists()) {
            try {
                org.originsd.oqteris.pdf.ui.PdfDocumentView pdfView = new org.originsd.oqteris.pdf.ui.PdfDocumentView(pdfFile);
                assertNotNull(pdfView.getRibbonToolbar());
                assertInstanceOf(Ribbon.class, pdfView.getRibbonToolbar());
                Ribbon pdfRibbon = (Ribbon) pdfView.getRibbonToolbar();
                assertEquals(1, pdfRibbon.getTabs().size());
                assertEquals("Document", pdfRibbon.getTabs().get(0).getText());
            } catch (Exception e) {
                fail("Failed to open PdfDocumentView: " + e.getMessage());
            }
        }
    }
}
