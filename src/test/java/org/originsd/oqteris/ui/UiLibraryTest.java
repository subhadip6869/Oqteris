package org.originsd.oqteris.ui;

import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.textfield.TextFields;
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
        assertNotNull(org.originsd.oqteris.ui.ThemeManager.class);
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

                // Professional desktop ribbon structure: 9 core tabs + contextual signature tab
                assertTrue(pdfRibbon.getTabs().size() >= 9, "Should have at least 9 core ribbon tabs");

                assertEquals("Home", pdfRibbon.getTabs().get(0).getText());
                assertEquals("View", pdfRibbon.getTabs().get(1).getText());
                assertEquals("Edit", pdfRibbon.getTabs().get(2).getText());
                assertEquals("Comment", pdfRibbon.getTabs().get(3).getText());
                assertEquals("Organize", pdfRibbon.getTabs().get(4).getText());
                assertEquals("Fill & Sign", pdfRibbon.getTabs().get(5).getText());
                assertEquals("Protect", pdfRibbon.getTabs().get(6).getText());
                assertEquals("Share", pdfRibbon.getTabs().get(7).getText());
                assertEquals("Help", pdfRibbon.getTabs().get(8).getText());

                // Home tab contains groups for File, Clipboard, Tools, Navigation, Zoom, Page Tools, Find, Security
                RibbonTab homeTab = pdfRibbon.getTabs().get(0);
                assertFalse(homeTab.getRibbonGroups().isEmpty());
                assertTrue(homeTab.getRibbonGroups().stream().anyMatch(g -> "Navigation".equals(g.getTitle())));
                assertTrue(homeTab.getRibbonGroups().stream().anyMatch(g -> "Zoom".equals(g.getTitle())));
                assertTrue(homeTab.getRibbonGroups().stream().anyMatch(g -> "File".equals(g.getTitle())));

                // For signed PDF, contextual signatures tab must be present
                boolean hasContextualSigTab = pdfRibbon.getTabs().stream().anyMatch(t -> t.getText().startsWith("Signatures"));
                assertTrue(hasContextualSigTab, "Signed PDF should display contextual signatures tab");

                // Verify View tab has Theme group with Dark Theme toggle
                RibbonTab viewTab = pdfRibbon.getTabs().get(1);
                assertEquals("View", viewTab.getText());
                assertTrue(viewTab.getRibbonGroups().stream().anyMatch(g -> "Theme".equals(g.getTitle())),
                        "View tab should contain Theme ribbon group");
            } catch (Exception e) {
                fail("Failed to open PdfDocumentView: " + e.getMessage());
            }
        }

        // Test unsigned document (sample.pdf)
        File unsignedPdf = new File("samples", "sample.pdf");
        if (unsignedPdf.exists()) {
            try {
                org.originsd.oqteris.pdf.ui.PdfDocumentView pdfView = new org.originsd.oqteris.pdf.ui.PdfDocumentView(unsignedPdf);
                Ribbon pdfRibbon = (Ribbon) pdfView.getRibbonToolbar();
                assertEquals(9, pdfRibbon.getTabs().size(), "Unsigned PDF should have exactly 9 core tabs (no contextual signature tab)");
            } catch (Exception e) {
                fail("Failed to open unsigned PdfDocumentView: " + e.getMessage());
            }
        }
    }

    @Test
    public void testRibbonControlFactory() throws Exception {
        javafx.scene.control.Button largeBtn = org.originsd.oqteris.ui.ribbon.RibbonControlFactory.createLargeButton(
                "Test", MaterialDesignM.MAGNIFY, "Tooltip", null
        );
        assertNotNull(largeBtn);
        assertTrue(largeBtn.getStyleClass().contains("ribbon-large-btn"));
        assertEquals("Test", largeBtn.getText());
        assertNotNull(largeBtn.getTooltip());

        javafx.scene.control.Button smallBtn = org.originsd.oqteris.ui.ribbon.RibbonControlFactory.createSmallButton(
                "Save", MaterialDesignC.CONTENT_SAVE_OUTLINE, "Save Tooltip", null
        );
        assertNotNull(smallBtn);
        assertTrue(smallBtn.getStyleClass().contains("ribbon-btn"));

        com.pixelduke.control.ribbon.Column col = org.originsd.oqteris.ui.ribbon.RibbonControlFactory.createColumn(largeBtn, smallBtn);
        assertNotNull(col);
        assertEquals(2, col.getChildren().size());
        File pdfFile = new File("samples", "sample_signed.pdf");
        org.originsd.oqteris.pdf.ui.PdfDocumentView pdfView = new org.originsd.oqteris.pdf.ui.PdfDocumentView(pdfFile);
        Ribbon r = (Ribbon) pdfView.getRibbonToolbar();
        javafx.scene.Scene scene = new javafx.scene.Scene(r, 1200, 200);
        org.originsd.oqteris.ui.ThemeManager.getInstance().applyTheme(scene);
        r.applyCss();
        r.layout();
        for (RibbonTab tabObj : r.getTabs()) {
            for (RibbonGroup g : tabObj.getRibbonGroups()) {
                for (javafx.scene.Node n : g.getNodes()) {
                    if (n instanceof javafx.scene.control.ButtonBase b) {
                        assertTrue(b.getWidth() >= Math.floor(b.prefWidth(-1)),
                                "Button '" + b.getText() + "' width (" + b.getWidth() + ") should be >= prefWidth (" + b.prefWidth(-1) + ")");
                    } else if (n instanceof com.pixelduke.control.ribbon.Column c) {
                        for (javafx.scene.Node cn : c.getChildren()) {
                            if (cn instanceof javafx.scene.control.ButtonBase b) {
                                assertTrue(b.getWidth() >= Math.floor(b.prefWidth(-1)),
                                        "Col Button '" + b.getText() + "' width (" + b.getWidth() + ") should be >= prefWidth (" + b.prefWidth(-1) + ")");
                            }
                        }
                    }
                }
            }
        }
    }
}