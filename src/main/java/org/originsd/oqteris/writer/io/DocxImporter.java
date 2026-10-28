package org.originsd.oqteris.writer.io;

import org.apache.poi.xwpf.usermodel.*;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;
import org.originsd.oqteris.writer.model.*;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Importer for Office Open XML Word Documents (.docx).
 */
public class DocxImporter implements DocumentImporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "DOCX",
            "Word Document (*.docx)",
            DocumentType.WRITER,
            List.of("docx", "docm"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new DocxImporter());
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
        return name.endsWith(".docx") || name.endsWith(".docm");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        try (FileInputStream in = new FileInputStream(file);
             XWPFDocument docx = new XWPFDocument(in)) {

            for (IBodyElement element : docx.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    // Check for inline images before/after paragraph text
                    List<DocumentImage> inlineImages = extractInlineImages(p);

                    Paragraph para = convertParagraph(p);
                    if (!para.getRuns().isEmpty() || para.getHeadingStyle() != Paragraph.HeadingStyle.NORMAL) {
                        document.addElement(para);
                    }
                    // Add inline images after paragraph
                    for (DocumentImage img : inlineImages) {
                        document.addElement(img);
                        document.addElement(new Paragraph()); // space after image
                    }
                    // If paragraph was empty and no images, still add it as blank line
                    if (para.getRuns().isEmpty() && inlineImages.isEmpty()) {
                        document.addElement(para);
                    }
                } else if (element instanceof XWPFTable t) {
                    DocumentTable table = convertTable(t);
                    document.addElement(table);
                }
            }

            // Import page setup from document section properties
            importPageSetup(docx, document);
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private List<DocumentImage> extractInlineImages(XWPFParagraph poiP) {
        List<DocumentImage> images = new ArrayList<>();
        for (XWPFRun run : poiP.getRuns()) {
            List<XWPFPicture> pics = run.getEmbeddedPictures();
            for (XWPFPicture pic : pics) {
                try {
                    byte[] picData = pic.getPictureData().getData();
                    Image fxImg = new Image(new ByteArrayInputStream(picData));
                    if (!fxImg.isError()) {
                        // Check for srcRect cropping (a:srcRect l, t, r, b in 100,000ths)
                        try {
                            org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture ctPic = pic.getCTPicture();
                            if (ctPic != null && ctPic.getBlipFill() != null && ctPic.getBlipFill().isSetSrcRect()) {
                                org.openxmlformats.schemas.drawingml.x2006.main.CTRelativeRect srcRect = ctPic.getBlipFill().getSrcRect();
                                if (srcRect != null) {
                                    double cropL = parseCropPercent(srcRect.isSetL() ? srcRect.getL() : null);
                                    double cropT = parseCropPercent(srcRect.isSetT() ? srcRect.getT() : null);
                                    double cropR = parseCropPercent(srcRect.isSetR() ? srcRect.getR() : null);
                                    double cropB = parseCropPercent(srcRect.isSetB() ? srcRect.getB() : null);

                                    int origW = (int) fxImg.getWidth();
                                    int origH = (int) fxImg.getHeight();
                                    int startX = (int) Math.round(cropL * origW);
                                    int startY = (int) Math.round(cropT * origH);
                                    int cropW = (int) Math.round((1.0 - cropL - cropR) * origW);
                                    int cropH = (int) Math.round((1.0 - cropT - cropB) * origH);

                                    if (cropW > 0 && cropH > 0 && (startX > 0 || startY > 0 || cropW < origW || cropH < origH)) {
                                        startX = Math.max(0, Math.min(startX, origW - 1));
                                        startY = Math.max(0, Math.min(startY, origH - 1));
                                        cropW = Math.max(1, Math.min(cropW, origW - startX));
                                        cropH = Math.max(1, Math.min(cropH, origH - startY));
                                        fxImg = new WritableImage(fxImg.getPixelReader(), startX, startY, cropW, cropH);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        // Get picture dimensions from the picture data (in EMU, 1 inch = 914400 EMU, 1 pt = 12700 EMU)
                        double widthEmu = pic.getCTPicture().getSpPr().getXfrm().getExt().getCx();
                        double heightEmu = pic.getCTPicture().getSpPr().getXfrm().getExt().getCy();
                        double widthPt = widthEmu / 12700.0;
                        double heightPt = heightEmu / 12700.0;
                        // Clamp to printable width (~451 pts for A4 with default margins)
                        double maxW = 451.0;
                        if (widthPt > maxW) {
                            heightPt = heightPt * (maxW / widthPt);
                            widthPt = maxW;
                        }
                        DocumentImage docImg = new DocumentImage(fxImg, widthPt, heightPt);
                        docImg.setRawBytes(picData);
                        if (poiP.getAlignment() != null) {
                            docImg.setAlignment(poiP.getAlignment().name());
                        }
                        images.add(docImg);
                    }
                } catch (Exception ignored) {
                    // If we can't extract dimensions, use natural image size
                    try {
                        byte[] picData = pic.getPictureData().getData();
                        Image fxImg = new Image(new ByteArrayInputStream(picData));
                        if (!fxImg.isError()) {
                            double w = Math.min(fxImg.getWidth(), 451.0);
                            double h = fxImg.getHeight() * (w / Math.max(1, fxImg.getWidth()));
                            DocumentImage docImg = new DocumentImage(fxImg, w, h);
                            docImg.setRawBytes(picData);
                            if (poiP.getAlignment() != null) {
                                docImg.setAlignment(poiP.getAlignment().name());
                            }
                            images.add(docImg);
                        }
                    } catch (Exception ex2) {
                        // ignore
                    }
                }
            }
        }
        return images;
    }

    private double parseCropPercent(Object val) {
        if (val == null) return 0.0;
        try {
            if (val instanceof Number num) {
                double v = num.doubleValue();
                return v > 1.0 ? v / 100000.0 : v;
            }
            String str = String.valueOf(val).trim();
            if (str.endsWith("%")) {
                return Double.parseDouble(str.substring(0, str.length() - 1)) / 100.0;
            }
            double v = Double.parseDouble(str);
            return v > 1.0 ? v / 100000.0 : v;
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private void importPageSetup(XWPFDocument docx, WriterDocument document) {
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr sectPr = null;
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody body = docx.getDocument().getBody();
            if (body != null && body.isSetSectPr()) {
                sectPr = body.getSectPr();
            }
            if (sectPr == null) {
                // If sectPr not on body, check last paragraph
                List<XWPFParagraph> paras = docx.getParagraphs();
                for (int i = paras.size() - 1; i >= 0; i--) {
                    XWPFParagraph p = paras.get(i);
                    if (p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetSectPr()) {
                        sectPr = p.getCTP().getPPr().getSectPr();
                        break;
                    }
                }
            }
            if (sectPr == null) return;

            if (sectPr.isSetPgSz()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz pgSz = sectPr.getPgSz();
                if (pgSz.getW() != null) {
                    double wPt = Double.parseDouble(String.valueOf(pgSz.getW())) / 20.0;
                    if (wPt > 200 && wPt < 2000) {
                        document.getPageSetup().setPageWidth(wPt);
                    }
                }
                if (pgSz.getH() != null) {
                    double hPt = Double.parseDouble(String.valueOf(pgSz.getH())) / 20.0;
                    if (hPt > 200 && hPt < 2000) {
                        document.getPageSetup().setPageHeight(hPt);
                    }
                }
            }

            // Page margins
            if (sectPr.isSetPgMar()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar pgMar = sectPr.getPgMar();
                if (pgMar.getTop() != null) {
                    document.getPageSetup().setMarginTop(Double.parseDouble(String.valueOf(pgMar.getTop())) / 20.0);
                }
                if (pgMar.getBottom() != null) {
                    document.getPageSetup().setMarginBottom(Double.parseDouble(String.valueOf(pgMar.getBottom())) / 20.0);
                }
                if (pgMar.getLeft() != null) {
                    document.getPageSetup().setMarginLeft(Double.parseDouble(String.valueOf(pgMar.getLeft())) / 20.0);
                }
                if (pgMar.getRight() != null) {
                    document.getPageSetup().setMarginRight(Double.parseDouble(String.valueOf(pgMar.getRight())) / 20.0);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Paragraph convertParagraph(XWPFParagraph poiP) {
        Paragraph para = new Paragraph();

        // Alignment
        if (poiP.getAlignment() != null) {
            switch (poiP.getAlignment()) {
                case CENTER -> para.setAlignment(Paragraph.Alignment.CENTER);
                case RIGHT -> para.setAlignment(Paragraph.Alignment.RIGHT);
                case BOTH -> para.setAlignment(Paragraph.Alignment.JUSTIFY);
                default -> para.setAlignment(Paragraph.Alignment.LEFT);
            }
        }

        // Spacing
        if (poiP.getSpacingBefore() > 0) para.setSpaceBefore(poiP.getSpacingBefore() / 20.0);
        if (poiP.getSpacingAfter() > 0) para.setSpaceAfter(poiP.getSpacingAfter() / 20.0);
        if (poiP.getSpacingBetween() > 0) para.setLineSpacing(poiP.getSpacingBetween());

        // Indentation
        if (poiP.getIndentationLeft() > 0) para.setLeftIndent(poiP.getIndentationLeft() / 20.0);
        if (poiP.getIndentationRight() > 0) para.setRightIndent(poiP.getIndentationRight() / 20.0);

        // Tab stops
        try {
            if (poiP.getCTP().isSetPPr() && poiP.getCTP().getPPr().isSetTabs()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs ctTabs = poiP.getCTP().getPPr().getTabs();
                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop ts : ctTabs.getTabList()) {
                    if (ts.getPos() != null) {
                        double posPt = Double.parseDouble(String.valueOf(ts.getPos())) / 20.0;
                        TabStop.Alignment align = TabStop.Alignment.LEFT;
                        if (ts.getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.RIGHT ||
                                ts.getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.END) {
                            align = TabStop.Alignment.RIGHT;
                        } else if (ts.getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.CENTER) {
                            align = TabStop.Alignment.CENTER;
                        } else if (ts.getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.DECIMAL) {
                            align = TabStop.Alignment.DECIMAL;
                        }
                        para.addTabStop(new TabStop(posPt, align));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Heading style
        String style = poiP.getStyle();
        if (style != null) {
            String lower = style.toLowerCase();
            if (lower.contains("title")) para.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
            else if (lower.equals("heading1") || lower.equals("heading 1")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
            else if (lower.equals("heading2") || lower.equals("heading 2")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
            else if (lower.equals("heading3") || lower.equals("heading 3")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
        }

        // List type
        try {
            if (poiP.getNumID() != null && poiP.getNumID().longValue() > 0) {
                // This is a list paragraph; default to bullet
                para.setListType(Paragraph.ListType.BULLET);
            }
        } catch (Exception ignored) {
        }

        // Runs
        for (XWPFRun poiRun : poiP.getRuns()) {
            String text = poiRun.getText(0);
            if (text != null && !text.isEmpty()) {
                TextRun run = new TextRun(text);
                if (poiRun.getFontFamily() != null && !poiRun.getFontFamily().isBlank()) {
                    run.setFontFamily(poiRun.getFontFamily());
                }
                // Font size: getFontSizeAsDouble() returns in points
                double fontSize = (double) poiRun.getFontSize();
                if (fontSize > 0) run.setFontSize(fontSize);
                run.setBold(poiRun.isBold());
                run.setItalic(poiRun.isItalic());
                run.setUnderline(poiRun.getUnderline() != UnderlinePatterns.NONE);
                run.setStrikethrough(poiRun.isStrikeThrough());

                // Color
                String colorHex = poiRun.getColor();
                if (colorHex != null && colorHex.length() == 6) {
                    try {
                        run.setTextColor(javafx.scene.paint.Color.web("#" + colorHex));
                    } catch (Exception ignored) {
                    }
                }

                para.addRun(run);
            }
        }

        return para;
    }

    private DocumentTable convertTable(XWPFTable poiTable) {
        DocumentTable table = new DocumentTable();

        for (XWPFTableRow poiRow : poiTable.getRows()) {
            DocumentTableRow row = new DocumentTableRow();
            for (XWPFTableCell poiCell : poiRow.getTableCells()) {
                DocumentTableCell cell = new DocumentTableCell();
                cell.getParagraphs().clear();

                for (XWPFParagraph p : poiCell.getParagraphs()) {
                    cell.addParagraph(convertParagraph(p));
                }
                if (cell.getParagraphs().isEmpty()) {
                    cell.addParagraph(new Paragraph());
                }
                row.addCell(cell);
            }
            table.addRow(row);
        }

        return table;
    }
}
