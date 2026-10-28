package org.oqteris.writer.io;

import javafx.embed.swing.SwingFXUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.oqteris.core.io.DocumentExporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.writer.model.*;
import org.oqteris.writer.model.Paragraph;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Exporter for Office Open XML Word Documents (.docx).
 */
public class DocxExporter implements DocumentExporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "DOCX",
            "Word Document (*.docx)",
            DocumentType.WRITER,
            List.of("docx"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerExporter(new DocxExporter());
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
        return file.getName().toLowerCase().endsWith(".docx");
    }

    @Override
    public void exportDocument(WriterDocument document, File file) throws IOException {
        try (XWPFDocument docx = new XWPFDocument()) {

            for (DocumentElement element : document.getElements()) {
                if (element instanceof Paragraph p) {
                    XWPFParagraph poiP = docx.createParagraph();
                    writeParagraph(p, poiP);
                } else if (element instanceof DocumentTable table) {
                    writeTable(table, docx);
                } else if (element instanceof DocumentImage img) {
                    writeImage(img, docx);
                } else if (element instanceof PageBreakElement) {
                    XWPFParagraph p = docx.createParagraph();
                    p.setPageBreak(true);
                }
            }

            // Export Page Setup (Dimensions and Margins)
            try {
                PageSetup setup = document.getPageSetup();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody body = docx.getDocument().getBody();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
                pgSz.setW(java.math.BigInteger.valueOf((long) Math.round(setup.getPageWidth() * 20.0)));
                pgSz.setH(java.math.BigInteger.valueOf((long) Math.round(setup.getPageHeight() * 20.0)));
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
                pgMar.setTop(java.math.BigInteger.valueOf((long) Math.round(setup.getMarginTop() * 20.0)));
                pgMar.setBottom(java.math.BigInteger.valueOf((long) Math.round(setup.getMarginBottom() * 20.0)));
                pgMar.setLeft(java.math.BigInteger.valueOf((long) Math.round(setup.getMarginLeft() * 20.0)));
                pgMar.setRight(java.math.BigInteger.valueOf((long) Math.round(setup.getMarginRight() * 20.0)));
            } catch (Exception ignored) {
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                docx.write(out);
            }
        }
    }

    private void writeParagraph(Paragraph p, XWPFParagraph poiP) {
        switch (p.getAlignment()) {
            case CENTER -> poiP.setAlignment(ParagraphAlignment.CENTER);
            case RIGHT -> poiP.setAlignment(ParagraphAlignment.RIGHT);
            case JUSTIFY -> poiP.setAlignment(ParagraphAlignment.BOTH);
            default -> poiP.setAlignment(ParagraphAlignment.LEFT);
        }

        switch (p.getHeadingStyle()) {
            case TITLE -> poiP.setStyle("Title");
            case HEADING_1 -> poiP.setStyle("Heading1");
            case HEADING_2 -> poiP.setStyle("Heading2");
            case HEADING_3 -> poiP.setStyle("Heading3");
            default -> {
            }
        }

        // Export custom tab stops
        if (!p.getTabStops().isEmpty()) {
            try {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr = poiP.getCTP().isSetPPr() ? poiP.getCTP().getPPr() : poiP.getCTP().addNewPPr();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs tabs = ppr.isSetTabs() ? ppr.getTabs() : ppr.addNewTabs();
                for (TabStop ts : p.getTabStops()) {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop ctTab = tabs.addNewTab();
                    ctTab.setPos(java.math.BigInteger.valueOf((long) Math.round(ts.getPosition() * 20.0)));
                    switch (ts.getAlignment()) {
                        case RIGHT -> ctTab.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.RIGHT);
                        case CENTER -> ctTab.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.CENTER);
                        case DECIMAL -> ctTab.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.DECIMAL);
                        default -> ctTab.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.LEFT);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        for (TextRun run : p.getRuns()) {
            XWPFRun poiRun = poiP.createRun();
            poiRun.setText(run.getText());
            poiRun.setFontFamily(run.getFontFamily());
            poiRun.setFontSize((int) run.getFontSize());
            poiRun.setBold(run.isBold());
            poiRun.setItalic(run.isItalic());
            if (run.isUnderline()) poiRun.setUnderline(UnderlinePatterns.SINGLE);
            poiRun.setStrikeThrough(run.isStrikethrough());

            if (run.getTextColor() != null) {
                int r = (int) Math.round(run.getTextColor().getRed() * 255);
                int g = (int) Math.round(run.getTextColor().getGreen() * 255);
                int b = (int) Math.round(run.getTextColor().getBlue() * 255);
                poiRun.setColor(String.format("%02X%02X%02X", r, g, b));
            }
        }
    }

    private void writeTable(DocumentTable table, XWPFDocument docx) {
        if (table.getRows().isEmpty()) return;
        int numRows = table.getRows().size();
        int numCols = table.getColumnCount();

        XWPFTable poiTable = docx.createTable(numRows, numCols);

        for (int r = 0; r < numRows; r++) {
            DocumentTableRow row = table.getRows().get(r);
            XWPFTableRow poiRow = poiTable.getRow(r);

            for (int c = 0; c < Math.min(numCols, row.getCells().size()); c++) {
                DocumentTableCell cell = row.getCells().get(c);
                XWPFTableCell poiCell = poiRow.getCell(c);

                // Clear default paragraph in POI cell
                if (!cell.getParagraphs().isEmpty()) {
                    for (int i = 0; i < cell.getParagraphs().size(); i++) {
                        Paragraph p = cell.getParagraphs().get(i);
                        XWPFParagraph poiP = (i == 0 && poiCell.getParagraphs().size() > 0) ?
                                poiCell.getParagraphs().get(0) : poiCell.addParagraph();
                        writeParagraph(p, poiP);
                    }
                }
            }
        }
    }

    private void writeImage(DocumentImage img, XWPFDocument docx) {
        try {
            XWPFParagraph p = docx.createParagraph();
            if ("CENTER".equalsIgnoreCase(img.getAlignment())) {
                p.setAlignment(ParagraphAlignment.CENTER);
            } else if ("RIGHT".equalsIgnoreCase(img.getAlignment())) {
                p.setAlignment(ParagraphAlignment.RIGHT);
            }
            XWPFRun run = p.createRun();

            byte[] bytes = img.getRawBytes();
            if (bytes == null && img.getImage() != null) {
                java.awt.image.BufferedImage bImg = SwingFXUtils.fromFXImage(img.getImage(), null);
                if (bImg != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bImg, "png", baos);
                    bytes = baos.toByteArray();
                }
            }

            if (bytes != null) {
                int widthEmu = (int) Math.round(img.getWidth() * 12700);
                int heightEmu = (int) Math.round(img.getHeight() * 12700);
                run.addPicture(new ByteArrayInputStream(bytes), XWPFDocument.PICTURE_TYPE_PNG, "image.png", widthEmu, heightEmu);
            }
        } catch (Exception ignored) {
        }
    }
}
