package org.originsd.oqteris.writer.io;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.apache.poi.xwpf.usermodel.*;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

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

    private static String attr(String tag, String name) {
        int i = tag.indexOf(name + "=\"");
        if (i < 0) i = tag.indexOf(name + "='");
        if (i < 0) return null;
        int start = i + name.length() + 2;
        char quote = tag.charAt(start - 1);
        int end = tag.indexOf(quote, start);
        if (end < 0) return null;
        return tag.substring(start, end);
    }

    private static String textBetween(String s, String open, String close) {
        int a = s.indexOf(open);
        if (a < 0) return null;
        a += open.length();
        int b = s.indexOf(close, a);
        if (b < 0) return null;
        return s.substring(a, b).trim();
    }

    private static double parseCrop100000(String v) {
        if (v == null || v.isBlank()) return 0;
        try {
            return Long.parseLong(v.trim()) / 100000.0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static DocumentImage.HRelative mapHRelative(String rel) {
        if (rel == null) return DocumentImage.HRelative.PAGE;
        return switch (rel.toLowerCase()) {
            case "margin" -> DocumentImage.HRelative.MARGIN;
            case "column" -> DocumentImage.HRelative.COLUMN;
            case "character" -> DocumentImage.HRelative.CHARACTER;
            case "left-margin", "leftmargin" -> DocumentImage.HRelative.LEFT_MARGIN;
            case "right-margin", "rightmargin" -> DocumentImage.HRelative.RIGHT_MARGIN;
            default -> DocumentImage.HRelative.PAGE;
        };
    }

    private static DocumentImage.VRelative mapVRelative(String rel) {
        if (rel == null) return DocumentImage.VRelative.PARAGRAPH;
        return switch (rel.toLowerCase()) {
            case "page" -> DocumentImage.VRelative.PAGE;
            case "margin" -> DocumentImage.VRelative.MARGIN;
            case "line" -> DocumentImage.VRelative.LINE;
            case "top-margin", "topmargin" -> DocumentImage.VRelative.TOP_MARGIN;
            case "bottom-margin", "bottommargin" -> DocumentImage.VRelative.BOTTOM_MARGIN;
            default -> DocumentImage.VRelative.PARAGRAPH;
        };
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
                    List<DocumentImage> images = extractImagesFromParagraph(p);

                    Paragraph para = convertParagraph(p);
                    // Floating images first so layout can position relative to following text Y
                    for (DocumentImage img : images) {
                        if (img.isFloating()) {
                            document.addElement(img);
                        }
                    }
                    if (!para.getRuns().isEmpty() || para.getHeadingStyle() != Paragraph.HeadingStyle.NORMAL
                            || images.isEmpty()) {
                        document.addElement(para);
                    }
                    for (DocumentImage img : images) {
                        if (!img.isFloating()) {
                            document.addElement(img);
                            document.addElement(new Paragraph());
                        }
                    }
                    if (para.getRuns().isEmpty() && images.isEmpty()) {
                        document.addElement(para);
                    }
                } else if (element instanceof XWPFTable t) {
                    document.addElement(convertTable(t));
                }
            }

            importPageSetup(docx, document);
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    /**
     * Extract both inline and floating (anchor) images from a paragraph,
     * including wrap mode and absolute positioning from DrawingML.
     */
    private List<DocumentImage> extractImagesFromParagraph(XWPFParagraph poiP) {
        List<DocumentImage> images = new ArrayList<>();
        try {
            String xml = poiP.getCTP().xmlText();
            images.addAll(parseDrawingsFromXml(xml, poiP));
        } catch (Exception ignored) {
        }
        if (images.isEmpty()) {
            images.addAll(extractInlineImages(poiP));
        }
        return images;
    }

    private List<DocumentImage> parseDrawingsFromXml(String xml, XWPFParagraph poiP) {
        List<DocumentImage> images = new ArrayList<>();
        if (xml == null || !xml.contains("drawing")) return images;

        int pos = 0;
        while (pos < xml.length()) {
            int a = xml.indexOf("<wp:anchor", pos);
            int i = xml.indexOf("<wp:inline", pos);
            int start;
            boolean isAnchor;
            if (a < 0 && i < 0) break;
            if (a < 0 || (i >= 0 && i < a)) {
                start = i;
                isAnchor = false;
            } else {
                start = a;
                isAnchor = true;
            }
            String endTag = isAnchor ? "</wp:anchor>" : "</wp:inline>";
            int end = xml.indexOf(endTag, start);
            if (end < 0) break;
            end += endTag.length();
            String block = xml.substring(start, end);
            pos = end;

            try {
                DocumentImage img = buildImageFromDrawingBlock(block, isAnchor, poiP);
                if (img != null) images.add(img);
            } catch (Exception ignored) {
            }
        }
        return images;
    }

    private DocumentImage buildImageFromDrawingBlock(String block, boolean isAnchor, XWPFParagraph poiP) {
        String embed = attr(block, "r:embed");
        if (embed == null) embed = attr(block, "embed");
        if (embed == null) return null;

        byte[] picData = null;
        try {
            XWPFDocument doc = poiP.getDocument();
            org.apache.poi.xwpf.usermodel.XWPFPictureData pd = null;
            for (XWPFRun run : poiP.getRuns()) {
                for (XWPFPicture pic : run.getEmbeddedPictures()) {
                    if (pic.getPictureData() != null) {
                        pd = pic.getPictureData();
                        break;
                    }
                }
                if (pd != null) break;
            }
            if (pd == null) {
                for (org.apache.poi.xwpf.usermodel.XWPFPictureData pdd : doc.getAllPictures()) {
                    pd = pdd;
                    break;
                }
            }
            if (pd != null) picData = pd.getData();
        } catch (Exception ignored) {
        }
        if (picData == null) return null;

        Image fxImg = new Image(new ByteArrayInputStream(picData));
        if (fxImg.isError()) return null;

        double widthPt = 200, heightPt = 150;
        int extIdx = block.indexOf("<wp:extent");
        if (extIdx >= 0) {
            int extEnd = block.indexOf("/>", extIdx);
            if (extEnd < 0) extEnd = block.indexOf(">", extIdx);
            String extTag = block.substring(extIdx, Math.min(block.length(), extEnd + 2));
            String ecx = attr(extTag, "cx");
            String ecy = attr(extTag, "cy");
            if (ecx != null) widthPt = Long.parseLong(ecx) / 12700.0;
            if (ecy != null) heightPt = Long.parseLong(ecy) / 12700.0;
        }

        double cropL = 0, cropT = 0, cropR = 0, cropB = 0;
        int srcIdx = block.indexOf("<a:srcRect");
        if (srcIdx >= 0) {
            int srcEnd = block.indexOf("/>", srcIdx);
            if (srcEnd < 0) srcEnd = block.indexOf(">", srcIdx);
            String srcTag = block.substring(srcIdx, Math.min(block.length(), srcEnd + 2));
            cropL = parseCrop100000(attr(srcTag, "l"));
            cropT = parseCrop100000(attr(srcTag, "t"));
            cropR = parseCrop100000(attr(srcTag, "r"));
            cropB = parseCrop100000(attr(srcTag, "b"));
        }

        DocumentImage docImg = new DocumentImage(fxImg, widthPt, heightPt);
        docImg.setRawBytes(picData);
        if (cropL > 0 || cropT > 0 || cropR > 0 || cropB > 0) {
            docImg.setCrop(cropL, cropT, cropR, cropB);
        }

        if (!isAnchor) {
            docImg.setWrapMode(DocumentImage.WrapMode.INLINE);
            if (poiP.getAlignment() != null) {
                docImg.setAlignment(poiP.getAlignment().name());
            }
            return docImg;
        }

        // --- Anchor (floating) ---
        docImg.setFloating(true);

        if (block.contains("<wp:wrapNone")) {
            boolean behind = block.contains("behindDoc=\"1\"");
            docImg.setBehindText(behind);
            docImg.setWrapMode(behind
                    ? DocumentImage.WrapMode.BEHIND_TEXT
                    : DocumentImage.WrapMode.IN_FRONT_OF_TEXT);
        } else if (block.contains("<wp:wrapSquare")) {
            docImg.setWrapMode(DocumentImage.WrapMode.SQUARE);
        } else if (block.contains("<wp:wrapTight") || block.contains("<wp:wrapThrough")) {
            docImg.setWrapMode(DocumentImage.WrapMode.TIGHT);
        } else if (block.contains("<wp:wrapTopAndBottom")) {
            docImg.setWrapMode(DocumentImage.WrapMode.TOP_AND_BOTTOM);
        } else {
            docImg.setWrapMode(DocumentImage.WrapMode.IN_FRONT_OF_TEXT);
        }

        String head = block.substring(0, Math.min(block.length(), 400));
        String distL = attr(head, "distL");
        String distR = attr(head, "distR");
        String distT = attr(head, "distT");
        String distB = attr(head, "distB");
        docImg.setDist(
                distL != null ? Long.parseLong(distL) / 12700.0 : 9,
                distR != null ? Long.parseLong(distR) / 12700.0 : 9,
                distT != null ? Long.parseLong(distT) / 12700.0 : 0,
                distB != null ? Long.parseLong(distB) / 12700.0 : 0
        );

        int ph = block.indexOf("<wp:positionH");
        if (ph >= 0) {
            int phEnd = block.indexOf("</wp:positionH>", ph);
            String phBlock = phEnd > ph ? block.substring(ph, phEnd) : block.substring(ph, Math.min(block.length(), ph + 300));
            String rel = attr(phBlock, "relativeFrom");
            docImg.setHRelative(mapHRelative(rel));
            if (phBlock.contains("<wp:align>")) {
                String al = textBetween(phBlock, "<wp:align>", "</wp:align>");
                if ("right".equalsIgnoreCase(al)) docImg.setHAlign(DocumentImage.HAlign.RIGHT);
                else if ("center".equalsIgnoreCase(al)) docImg.setHAlign(DocumentImage.HAlign.CENTER);
                else docImg.setHAlign(DocumentImage.HAlign.LEFT);
            } else if (phBlock.contains("<wp:posOffset>")) {
                String off = textBetween(phBlock, "<wp:posOffset>", "</wp:posOffset>");
                docImg.setHAlign(DocumentImage.HAlign.ABSOLUTE);
                if (off != null) docImg.setHOffsetPt(Long.parseLong(off.trim()) / 12700.0);
            }
        }

        int pv = block.indexOf("<wp:positionV");
        if (pv >= 0) {
            int pvEnd = block.indexOf("</wp:positionV>", pv);
            String pvBlock = pvEnd > pv ? block.substring(pv, pvEnd) : block.substring(pv, Math.min(block.length(), pv + 300));
            String rel = attr(pvBlock, "relativeFrom");
            docImg.setVRelative(mapVRelative(rel));
            if (pvBlock.contains("<wp:posOffset>")) {
                String off = textBetween(pvBlock, "<wp:posOffset>", "</wp:posOffset>");
                if (off != null) docImg.setVOffsetPt(Long.parseLong(off.trim()) / 12700.0);
            }
        }

        return docImg;
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

                        double widthEmu = pic.getCTPicture().getSpPr().getXfrm().getExt().getCx();
                        double heightEmu = pic.getCTPicture().getSpPr().getXfrm().getExt().getCy();
                        double widthPt = widthEmu / 12700.0;
                        double heightPt = heightEmu / 12700.0;
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

        if (poiP.getAlignment() != null) {
            switch (poiP.getAlignment()) {
                case CENTER -> para.setAlignment(Paragraph.Alignment.CENTER);
                case RIGHT -> para.setAlignment(Paragraph.Alignment.RIGHT);
                case BOTH -> para.setAlignment(Paragraph.Alignment.JUSTIFY);
                default -> para.setAlignment(Paragraph.Alignment.LEFT);
            }
        }

        if (poiP.getSpacingBefore() > 0) para.setSpaceBefore(poiP.getSpacingBefore() / 20.0);
        if (poiP.getSpacingAfter() > 0) para.setSpaceAfter(poiP.getSpacingAfter() / 20.0);
        if (poiP.getSpacingBetween() > 0) para.setLineSpacing(poiP.getSpacingBetween());

        // Indentation — allow negative values (hanging into margin)
        int indLeft = poiP.getIndentationLeft();
        int indRight = poiP.getIndentationRight();
        int indFirst = poiP.getIndentationFirstLine();
        if (indLeft != -1) para.setLeftIndent(indLeft / 20.0);
        if (indRight != -1) para.setRightIndent(indRight / 20.0);
        if (indFirst != -1) para.setFirstLineIndent(indFirst / 20.0);

        // Explicit tab stops from pPr
        try {
            if (poiP.getCTP().isSetPPr() && poiP.getCTP().getPPr().isSetTabs()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs ctTabs = poiP.getCTP().getPPr().getTabs();
                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop ts : ctTabs.getTabList()) {
                    if (ts.getPos() != null) {
                        double posPt = Double.parseDouble(String.valueOf(ts.getPos())) / 20.0;
                        TabStop.Alignment align = TabStop.Alignment.LEFT;
                        if (ts.getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.RIGHT
                                || ts.getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.END) {
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

        String style = poiP.getStyle();
        if (style != null) {
            String lower = style.toLowerCase();
            if (lower.contains("title")) para.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
            else if (lower.equals("heading1") || lower.equals("heading 1")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
            else if (lower.equals("heading2") || lower.equals("heading 2")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
            else if (lower.equals("heading3") || lower.equals("heading 3")) para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
        }

        try {
            if (poiP.getNumID() != null && poiP.getNumID().longValue() > 0) {
                para.setListType(Paragraph.ListType.BULLET);
            }
        } catch (Exception ignored) {
        }

        // Runs — preserve <w:tab/> as '\t'
        for (XWPFRun poiRun : poiP.getRuns()) {
            StringBuilder runText = new StringBuilder();
            try {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = poiRun.getCTR();
                if (ctr != null) {
                    for (org.apache.xmlbeans.XmlObject child : ctr.selectPath(
                            "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' *")) {
                        String local = child.getDomNode().getLocalName();
                        if ("t".equals(local)) {
                            String t = ((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText) child).getStringValue();
                            if (t != null) runText.append(t);
                        } else if ("tab".equals(local)) {
                            runText.append('\t');
                        } else if ("br".equals(local)) {
                            runText.append('\n');
                        }
                    }
                }
            } catch (Exception ignored) {
                String t = poiRun.getText(0);
                if (t != null) runText.append(t);
            }

            if (runText.length() == 0) continue;

            TextRun run = new TextRun(runText.toString());
            if (poiRun.getFontFamily() != null && !poiRun.getFontFamily().isBlank()) {
                run.setFontFamily(poiRun.getFontFamily());
            }
            double fontSize = (double) poiRun.getFontSize();
            if (fontSize > 0) run.setFontSize(fontSize);
            run.setBold(poiRun.isBold());
            run.setItalic(poiRun.isItalic());
            run.setUnderline(poiRun.getUnderline() != UnderlinePatterns.NONE);
            run.setStrikethrough(poiRun.isStrikeThrough());

            String colorHex = poiRun.getColor();
            if (colorHex != null && colorHex.length() == 6) {
                try {
                    run.setTextColor(javafx.scene.paint.Color.web("#" + colorHex));
                } catch (Exception ignored) {
                }
            }

            para.addRun(run);
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