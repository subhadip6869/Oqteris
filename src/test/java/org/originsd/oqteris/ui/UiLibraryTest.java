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

import java.io.File;

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
        RibbonTab tab = new RibbonTab("Document");
        RibbonGroup group = new RibbonGroup();
        group.setTitle("Navigation");
        tab.getRibbonGroups().add(group);
        ribbon.getTabs().add(tab);
        assertEquals(1, ribbon.getTabs().size());

        // Verify icon constants used by PDF viewer
        assertNotNull(FontIcon.of(MaterialDesignM.MAGNIFY, 16));
        assertNotNull(FontIcon.of(MaterialDesignF.FIND_REPLACE, 16));
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
    public void testPdfDocumentViewRibbon() {
        File pdfFile = new File("samples", "sample_signed.pdf");
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
