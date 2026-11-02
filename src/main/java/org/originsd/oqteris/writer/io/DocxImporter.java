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
        // name="value" or name='value'
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

            try {
                importPageSetup(docx, document);
            } catch (Throwable ignored) {
            }

            detectDocumentDefaultFont(docx, document);
            String docFont = document.getDetectedFontFamily();
            double docSize = document.getDefaultFontSize();

            for (IBodyElement element : docx.getBodyElements()) {
                try {
                    if (element instanceof XWPFParagraph p) {
                        if (isPageBreakParagraph(p)) {
                            document.addElement(new PageBreakElement());
                            Paragraph para = convertParagraph(p, docFont, docSize);
                            if (!para.getRuns().isEmpty() && !para.getPlainText().isBlank()) {
                                document.addElement(para);
                            }
                            continue;
                        }

                        List<DocumentImage> images;
                        try {
                            images = extractImagesFromParagraph(p, document.getPageSetup());
                        } catch (Throwable imgEx) {
                            images = new ArrayList<>();
                        }

                        Paragraph para;
                        try {
                            para = convertParagraph(p, docFont, docSize);
                        } catch (Throwable paraEx) {
                            para = new Paragraph();
                        }

                        // Floating images (they don't advance the text flow)
                        for (DocumentImage img : images) {
                            if (img != null && img.isFloating()) {
                                document.addElement(img);
                            }
                        }

                        // Inline images
                        boolean hasInline = false;
                        for (DocumentImage img : images) {
                            if (img != null && !img.isFloating()) {
                                document.addElement(img);
                                hasInline = true;
                            }
                        }

                        // Add paragraph once (avoids doubling empty paragraphs)
                        if (!para.getRuns().isEmpty() || para.getHeadingStyle() != Paragraph.HeadingStyle.NORMAL || !hasInline) {
                            document.addElement(para);
                        }
                    } else if (element instanceof XWPFTable t) {
                        try {
                            document.addElement(convertTable(t, docFont, docSize));
                        } catch (Throwable tableEx) {
                            // skip broken table rather than failing the whole document
                        }
                    }
                } catch (Throwable elEx) {
                    // continue with remaining body elements
                }
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable e) {
            throw new IOException("Failed to parse DOCX: " + e.getMessage(), e);
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private void detectDocumentDefaultFont(XWPFDocument docx, WriterDocument document) {
        String defaultFont = "Calibri";
        double defaultSize = 11.0;
        try {
            XWPFStyles styles = docx.getStyles();
            if (styles != null) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles ctStyles = styles.getCtStyles();
                if (ctStyles != null && ctStyles.isSetDocDefaults()) {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults docDefaults = ctStyles.getDocDefaults();
                    if (docDefaults.isSetRPrDefault() && docDefaults.getRPrDefault().isSetRPr()) {
                        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = docDefaults.getRPrDefault().getRPr();
                        if (rPr.sizeOfRFontsArray() > 0) {
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts f = rPr.getRFontsArray(0);
                            if (f.getAscii() != null && !f.getAscii().isBlank()) defaultFont = f.getAscii();
                            else if (f.getHAnsi() != null && !f.getHAnsi().isBlank()) defaultFont = f.getHAnsi();
                            else if (f.getCs() != null && !f.getCs().isBlank()) defaultFont = f.getCs();
                        }
                        if (rPr.sizeOfSzArray() > 0 && rPr.getSzArray(0).getVal() != null) {
                            double hp = Double.parseDouble(String.valueOf(rPr.getSzArray(0).getVal()));
                            if (hp > 0) defaultSize = hp / 2.0;
                        }
                    }
                }
                if ("Calibri".equals(defaultFont)) {
                    XWPFStyle normalStyle = styles.getStyle("Normal");
                    if (normalStyle != null && normalStyle.getCTStyle() != null && normalStyle.getCTStyle().isSetRPr()) {
                        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = normalStyle.getCTStyle().getRPr();
                        if (rPr.sizeOfRFontsArray() > 0) {
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts f = rPr.getRFontsArray(0);
                            if (f.getAscii() != null && !f.getAscii().isBlank()) defaultFont = f.getAscii();
                            else if (f.getHAnsi() != null && !f.getHAnsi().isBlank()) defaultFont = f.getHAnsi();
                        }
                        if (rPr.sizeOfSzArray() > 0 && rPr.getSzArray(0).getVal() != null) {
                            double hp = Double.parseDouble(String.valueOf(rPr.getSzArray(0).getVal()));
                            if (hp > 0) defaultSize = hp / 2.0;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        document.setDefaultFontFamily(defaultFont);
        document.setDefaultFontSize(defaultSize);
    }

    /**
     * Extract both inline and floating (anchor) images from a paragraph,
     * including wrap mode and absolute positioning from DrawingML.
     */
    private List<DocumentImage> extractImagesFromParagraph(XWPFParagraph poiP, PageSetup setup) {
        List<DocumentImage> images = new ArrayList<>();
        // 1) Floating anchors / drawings from the paragraph XML
        try {
            String xml = poiP.getCTP().xmlText();
            images.addAll(parseDrawingsFromXml(xml, poiP, setup));
        } catch (Exception ignored) {
        }
        // 2) Fallback: POI embedded pictures without anchor metadata
        if (images.isEmpty()) {
            images.addAll(extractInlineImages(poiP, setup));
        }
        return images;
    }

    private List<DocumentImage> parseDrawingsFromXml(String xml, XWPFParagraph poiP, PageSetup setup) {
        List<DocumentImage> images = new ArrayList<>();
        if (xml == null || !xml.contains("drawing")) return images;

        // Process each <wp:anchor> or <wp:inline> block
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
                if (block.contains("wordprocessingGroup") || block.contains("wpg:wgp")
                        || block.contains("wpg:grpSp")) {
                    images.addAll(extractImagesFromGroup(block, isAnchor, poiP, setup));
                } else {
                    DocumentImage img = buildImageFromDrawingBlock(block, isAnchor, poiP, setup);
                    if (img != null) images.add(img);
                }
            } catch (Exception ignored) {
            }
        }
        return images;
    }

    /**
     * Pictures inside a DrawingML group keep their own a:xfrm size/offset,
     * scaled from child coordinate space (chOff/chExt) to group extent.
     */
    private List<DocumentImage> extractImagesFromGroup(String block, boolean isAnchor, XWPFParagraph poiP, PageSetup setup) {
        List<DocumentImage> images = new ArrayList<>();
        double pageWidth = setup != null ? setup.getPageWidth() : 595.0;
        double groupX = 0, groupY = 0;
        double groupW = 0, groupH = 0;

        try {
            int extIdx = block.indexOf("<wp:extent");
            if (extIdx >= 0) {
                int extEnd = block.indexOf("/>", extIdx);
                if (extEnd < 0) extEnd = block.indexOf(">", extIdx);
                String extTag = block.substring(extIdx, Math.min(block.length(), extEnd + 2));
                String ecx = attr(extTag, "cx");
                String ecy = attr(extTag, "cy");
                if (ecx != null) groupW = Long.parseLong(ecx) / 12700.0;
                if (ecy != null) groupH = Long.parseLong(ecy) / 12700.0;
            }

            int ph = block.indexOf("<wp:positionH");
            if (ph >= 0) {
                int phEnd = block.indexOf("</wp:positionH>", ph);
                String phBlock = phEnd > ph ? block.substring(ph, phEnd) : block.substring(ph, Math.min(block.length(), ph + 300));
                if (phBlock.contains("<wp:posOffset>")) {
                    String off = textBetween(phBlock, "<wp:posOffset>", "</wp:posOffset>");
                    if (off != null) groupX = Long.parseLong(off.trim()) / 12700.0;
                } else if (phBlock.contains("<wp:align>")) {
                    String al = textBetween(phBlock, "<wp:align>", "</wp:align>");
                    if ("center".equalsIgnoreCase(al)) groupX = Math.max(0, (pageWidth - groupW) / 2.0);
                    else if ("right".equalsIgnoreCase(al)) groupX = Math.max(0, pageWidth - groupW);
                }
            }
            int pv = block.indexOf("<wp:positionV");
            if (pv >= 0) {
                int pvEnd = block.indexOf("</wp:positionV>", pv);
                String pvBlock = pvEnd > pv ? block.substring(pv, pvEnd) : block.substring(pv, Math.min(block.length(), pv + 300));
                if (pvBlock.contains("<wp:posOffset>")) {
                    String off = textBetween(pvBlock, "<wp:posOffset>", "</wp:posOffset>");
                    if (off != null) groupY = Long.parseLong(off.trim()) / 12700.0;
                } else if (pvBlock.contains("<wp:align>")) {
                    String al = textBetween(pvBlock, "<wp:align>", "</wp:align>");
                    double pageH = setup != null ? setup.getPageHeight() : 842.0;
                    if ("center".equalsIgnoreCase(al)) groupY = Math.max(0, (pageH - groupH) / 2.0);
                    else if ("bottom".equalsIgnoreCase(al)) groupY = Math.max(0, pageH - groupH - 72.0);
                }
            }
        } catch (Exception ignored) {
        }

        long chOffX = 0, chOffY = 0;
        long chExtW = 0, chExtH = 0;
        try {
            int grpPr = block.indexOf("<wpg:grpSpPr");
            if (grpPr >= 0) {
                int grpPrEnd = block.indexOf("</wpg:grpSpPr>", grpPr);
                String grpBlock = grpPrEnd > grpPr ? block.substring(grpPr, grpPrEnd) : block.substring(grpPr, Math.min(block.length(), grpPr + 400));
                int cho = grpBlock.indexOf("<a:chOff");
                if (cho >= 0) {
                    String chTag = grpBlock.substring(cho, Math.min(grpBlock.length(), cho + 100));
                    String cx = attr(chTag, "x");
                    String cy = attr(chTag, "y");
                    if (cx != null) chOffX = Long.parseLong(cx);
                    if (cy != null) chOffY = Long.parseLong(cy);
                }
                int che = grpBlock.indexOf("<a:chExt");
                if (che >= 0) {
                    String chTag = grpBlock.substring(che, Math.min(grpBlock.length(), che + 100));
                    String cx = attr(chTag, "cx");
                    String cy = attr(chTag, "cy");
                    if (cx != null) chExtW = Long.parseLong(cx);
                    if (cy != null) chExtH = Long.parseLong(cy);
                }
            }
        } catch (Exception ignored) {
        }

        double scaleX = (chExtW > 0 && groupW > 0) ? (groupW * 12700.0) / chExtW : 1.0;
        double scaleY = (chExtH > 0 && groupH > 0) ? (groupH * 12700.0) / chExtH : scaleX;
        boolean isBehind = block.contains("behindDoc=\"1\"");

        int search = 0;
        while (true) {
            int embedIdx = block.indexOf("r:embed=\"", search);
            if (embedIdx < 0) embedIdx = block.indexOf("embed=\"", search);
            if (embedIdx < 0) break;

            int quoteChar = block.indexOf("\"", embedIdx);
            int valStart = quoteChar + 1;
            int valEnd = block.indexOf("\"", valStart);
            if (valEnd < 0) break;
            String embed = block.substring(valStart, valEnd);
            search = valEnd;

            int shapeStart = block.lastIndexOf("<pic:pic", embedIdx);
            int shapeEnd = -1;
            if (shapeStart >= 0) {
                int pe = block.indexOf("</pic:pic>", embedIdx);
                if (pe >= 0) shapeEnd = pe + "</pic:pic>".length();
            }
            if (shapeStart < 0 || shapeEnd < 0) {
                shapeStart = block.lastIndexOf("<wps:wsp", embedIdx);
                if (shapeStart >= 0) {
                    int we = block.indexOf("</wps:wsp>", embedIdx);
                    if (we >= 0) shapeEnd = we + "</wps:wsp>".length();
                }
            }
            if (shapeStart < 0 || shapeEnd < 0) {
                shapeStart = Math.max(0, embedIdx - 300);
                shapeEnd = Math.min(block.length(), embedIdx + 300);
            }
            String shapeBlock = block.substring(shapeStart, shapeEnd);

            try {
                byte[] picData = resolvePictureBytes(poiP.getDocument(), embed);
                if (picData == null) continue;

                double childW = 0, childH = 0, childX = 0, childY = 0;
                int rotDeg = 0;

                int xfrm = shapeBlock.indexOf("<a:xfrm");
                if (xfrm >= 0) {
                    int xfrmEnd = shapeBlock.indexOf("</a:xfrm>", xfrm);
                    if (xfrmEnd < 0) xfrmEnd = shapeBlock.indexOf("/>", xfrm);
                    String xfrmBlock = xfrmEnd > xfrm ? shapeBlock.substring(xfrm, xfrmEnd + 9) : shapeBlock.substring(xfrm);

                    String rotStr = attr(xfrmBlock, "rot");
                    if (rotStr != null) {
                        try {
                            rotDeg = (int) (Long.parseLong(rotStr.trim()) / 60000.0);
                        } catch (Exception ignored) {}
                    }

                    int ei = xfrmBlock.indexOf("<a:ext");
                    if (ei >= 0) {
                        int ee = xfrmBlock.indexOf("/>", ei);
                        if (ee < 0) ee = xfrmBlock.indexOf(">", ei);
                        String et = xfrmBlock.substring(ei, Math.min(xfrmBlock.length(), ee + 2));
                        String ecx = attr(et, "cx");
                        String ecy = attr(et, "cy");
                        if (ecx != null) childW = Long.parseLong(ecx);
                        if (ecy != null) childH = Long.parseLong(ecy);
                    }
                    int oi = xfrmBlock.indexOf("<a:off");
                    if (oi >= 0) {
                        int oe = xfrmBlock.indexOf("/>", oi);
                        if (oe < 0) oe = xfrmBlock.indexOf(">", oi);
                        String ot = xfrmBlock.substring(oi, Math.min(xfrmBlock.length(), oe + 2));
                        String ox = attr(ot, "x");
                        String oy = attr(ot, "y");
                        if (ox != null) childX = Long.parseLong(ox);
                        if (oy != null) childY = Long.parseLong(oy);
                    }
                }

                double widthPt = (childW > 0) ? (childW * scaleX) / 12700.0 : (groupW > 0 ? groupW : 100);
                double heightPt = (childH > 0) ? (childH * scaleY) / 12700.0 : (groupH > 0 ? groupH : 100);
                double offX = (chExtW > 0) ? ((childX - chOffX) * scaleX) / 12700.0 : childX / 12700.0;
                double offY = (chExtH > 0) ? ((childY - chOffY) * scaleY) / 12700.0 : childY / 12700.0;

                if (widthPt < 0.5 || heightPt < 0.5 || widthPt > 2000 || heightPt > 2000) continue;

                Image fxImg = new Image(new ByteArrayInputStream(picData));
                if (fxImg.isError()) continue;

                DocumentImage docImg = new DocumentImage(fxImg, widthPt, heightPt);
                docImg.setRawBytes(picData);
                docImg.setFloating(true);
                docImg.setBehindText(isBehind);
                docImg.setWrapMode(isBehind ? DocumentImage.WrapMode.BEHIND_TEXT : DocumentImage.WrapMode.IN_FRONT_OF_TEXT);
                docImg.setHRelative(DocumentImage.HRelative.PAGE);
                docImg.setVRelative(DocumentImage.VRelative.PAGE);
                docImg.setHAlign(DocumentImage.HAlign.ABSOLUTE);
                docImg.setHOffsetPt(Math.max(0, groupX + offX));
                docImg.setVOffsetPt(Math.max(0, groupY + offY));
                docImg.setRotationDegrees(rotDeg);
                images.add(docImg);
            } catch (Exception ignored) {
            }
        }
        return images;
    }

    private DocumentImage buildImageFromDrawingBlock(String block, boolean isAnchor, XWPFParagraph poiP, PageSetup setup) {
        // Relationship id
        String embed = attr(block, "r:embed");
        if (embed == null) embed = attr(block, "embed");
        if (embed == null) return null;

        byte[] picData = resolvePictureBytes(poiP.getDocument(), embed);
        if (picData == null) return null;

        Image fxImg = new Image(new ByteArrayInputStream(picData));
        if (fxImg.isError()) return null;

        // Extent
        double widthPt = 200, heightPt = 150;
        String cx = attr(block, "cx");
        String cy = attr(block, "cy");
        // Prefer wp:extent
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

        // Crop from a:srcRect (percent in 100000ths)
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

        // Rotation from a:xfrm rot
        int xfrmIdx = block.indexOf("<a:xfrm");
        if (xfrmIdx >= 0) {
            int xfrmEnd = block.indexOf("</a:xfrm>", xfrmIdx);
            if (xfrmEnd < 0) xfrmEnd = block.indexOf("/>", xfrmIdx);
            String xfrmBlock = xfrmEnd > xfrmIdx ? block.substring(xfrmIdx, xfrmEnd + 9) : block.substring(xfrmIdx);
            String rotStr = attr(xfrmBlock, "rot");
            if (rotStr != null) {
                try {
                    int rotDeg = (int) (Long.parseLong(rotStr.trim()) / 60000.0);
                    docImg.setRotationDegrees(rotDeg);
                } catch (Exception ignored) {}
            }
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

        // Wrap mode
        if (block.contains("<wp:wrapNone")) {
            boolean behind = block.contains("behindDoc=\"1\"");
            docImg.setBehindText(behind);
            docImg.setWrapMode(behind ? DocumentImage.WrapMode.BEHIND_TEXT : DocumentImage.WrapMode.IN_FRONT_OF_TEXT);
        } else if (block.contains("<wp:wrapSquare")) {
            docImg.setWrapMode(DocumentImage.WrapMode.SQUARE);
        } else if (block.contains("<wp:wrapTight") || block.contains("<wp:wrapThrough")) {
            docImg.setWrapMode(DocumentImage.WrapMode.TIGHT);
        } else if (block.contains("<wp:wrapTopAndBottom")) {
            docImg.setWrapMode(DocumentImage.WrapMode.TOP_AND_BOTTOM);
        } else {
            docImg.setWrapMode(DocumentImage.WrapMode.IN_FRONT_OF_TEXT);
        }

        // Distances
        String distL = attr(block.substring(0, Math.min(block.length(), 400)), "distL");
        String distR = attr(block.substring(0, Math.min(block.length(), 400)), "distR");
        String distT = attr(block.substring(0, Math.min(block.length(), 400)), "distT");
        String distB = attr(block.substring(0, Math.min(block.length(), 400)), "distB");
        docImg.setDist(
                distL != null ? Long.parseLong(distL) / 12700.0 : 9,
                distR != null ? Long.parseLong(distR) / 12700.0 : 9,
                distT != null ? Long.parseLong(distT) / 12700.0 : 0,
                distB != null ? Long.parseLong(distB) / 12700.0 : 0
        );

        // positionH
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
                else if ("left".equalsIgnoreCase(al)) docImg.setHAlign(DocumentImage.HAlign.LEFT);
                else docImg.setHAlign(DocumentImage.HAlign.LEFT);
            } else if (phBlock.contains("<wp:posOffset>")) {
                String off = textBetween(phBlock, "<wp:posOffset>", "</wp:posOffset>");
                docImg.setHAlign(DocumentImage.HAlign.ABSOLUTE);
                if (off != null) docImg.setHOffsetPt(Long.parseLong(off.trim()) / 12700.0);
            }
        }

        // positionV
        int pv = block.indexOf("<wp:positionV");
        if (pv >= 0) {
            int pvEnd = block.indexOf("</wp:positionV>", pv);
            String pvBlock = pvEnd > pv ? block.substring(pv, pvEnd) : block.substring(pv, Math.min(block.length(), pv + 300));
            String rel = attr(pvBlock, "relativeFrom");
            docImg.setVRelative(mapVRelative(rel));
            if (pvBlock.contains("<wp:align>")) {
                String al = textBetween(pvBlock, "<wp:align>", "</wp:align>");
                double pageH = setup != null ? setup.getPageHeight() : 842.0;
                if ("center".equalsIgnoreCase(al)) {
                    docImg.setVOffsetPt(Math.max(0, (pageH - heightPt) / 2.0));
                } else if ("bottom".equalsIgnoreCase(al)) {
                    docImg.setVOffsetPt(Math.max(0, pageH - heightPt - 72.0));
                } else {
                    docImg.setVOffsetPt(0);
                }
            } else if (pvBlock.contains("<wp:posOffset>")) {
                String off = textBetween(pvBlock, "<wp:posOffset>", "</wp:posOffset>");
                if (off != null) docImg.setVOffsetPt(Long.parseLong(off.trim()) / 12700.0);
            }
        }

        return docImg;
    }

    /**
     * Resolve r:embed / relationship id to image bytes (never pick the first package image).
     */
    private byte[] resolvePictureBytes(XWPFDocument doc, String embedId) {
        if (doc == null || embedId == null || embedId.isBlank()) return null;
        try {
            org.apache.poi.openxml4j.opc.PackagePart part = doc.getPackagePart();
            org.apache.poi.openxml4j.opc.PackageRelationship rel = null;
            try {
                rel = part.getRelationship(embedId);
            } catch (Exception ignored) {
            }
            if (rel == null) {
                try {
                    for (org.apache.poi.openxml4j.opc.PackageRelationship r : part.getRelationships()) {
                        if (r != null && embedId.equals(r.getId())) {
                            rel = r;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (rel == null) return null;
            org.apache.poi.openxml4j.opc.PackagePart imgPart = part.getRelatedPart(rel);
            try (java.io.InputStream in = imgPart.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private List<DocumentImage> extractInlineImages(XWPFParagraph poiP, PageSetup setup) {
        double maxPrintableW = setup != null ? setup.getPrintableWidth() : 500.0;
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
                        if (widthPt > maxPrintableW) {
                            heightPt = heightPt * (maxPrintableW / widthPt);
                            widthPt = maxPrintableW;
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
                            double w = Math.min(fxImg.getWidth(), maxPrintableW);
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

    private Paragraph convertParagraph(XWPFParagraph poiP, String docDefaultFont, double docDefaultSize) {
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
        boolean spacingSet = false;
        try {
            if (poiP.getCTP() != null && poiP.getCTP().isSetPPr() && poiP.getCTP().getPPr().isSetSpacing()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing sp = poiP.getCTP().getPPr().getSpacing();
                if (sp.isSetBefore() && sp.getBefore() != null) {
                    para.setSpaceBefore(Double.parseDouble(String.valueOf(sp.getBefore())) / 20.0);
                    spacingSet = true;
                }
                if (sp.isSetAfter() && sp.getAfter() != null) {
                    para.setSpaceAfter(Double.parseDouble(String.valueOf(sp.getAfter())) / 20.0);
                    spacingSet = true;
                }
                if (sp.isSetLine() && sp.getLine() != null) {
                    double lineVal = Double.parseDouble(String.valueOf(sp.getLine()));
                    para.setLineSpacing(Math.max(0.7, lineVal / 240.0));
                }
            }
        } catch (Exception ignored) {
        }

        if (!spacingSet) {
            if (poiP.getSpacingBefore() >= 0) {
                para.setSpaceBefore(poiP.getSpacingBefore() / 20.0);
            } else {
                para.setSpaceBefore(0.0);
            }
            if (poiP.getSpacingAfter() >= 0) {
                para.setSpaceAfter(poiP.getSpacingAfter() / 20.0);
            } else {
                para.setSpaceAfter(0.0);
            }
            if (poiP.getSpacingBetween() > 0) {
                para.setLineSpacing(poiP.getSpacingBetween());
            } else {
                para.setLineSpacing(1.15);
            }
        }

        // Indentation — allow negative values (hanging into margin, common in official memos)
        int indLeft = poiP.getIndentationLeft();
        int indRight = poiP.getIndentationRight();
        int indFirst = poiP.getIndentationFirstLine();
        if (indLeft != -1) para.setLeftIndent(indLeft / 20.0);
        if (indRight != -1) para.setRightIndent(indRight / 20.0);
        if (indFirst != -1) para.setFirstLineIndent(indFirst / 20.0);

        // Tab stops (explicit positions from pPr)
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
            else if (lower.equals("heading1") || lower.equals("heading 1"))
                para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
            else if (lower.equals("heading2") || lower.equals("heading 2"))
                para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
            else if (lower.equals("heading3") || lower.equals("heading 3"))
                para.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
        }

        // List type
        try {
            if (poiP.getNumID() != null && poiP.getNumID().longValue() > 0) {
                para.setListType(Paragraph.ListType.BULLET);
            }
        } catch (Exception ignored) {
        }

        // Runs — preserve tab characters (w:tab) that getText() would drop
        for (XWPFRun poiRun : poiP.getRuns()) {
            StringBuilder runText = new StringBuilder();
            try {
                // Walk CTR children so we keep <w:tab/> as '\t'
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
                // Fallback to POI text API
                String t = poiRun.getText(0);
                if (t != null) runText.append(t);
            }

            if (runText.length() == 0) continue;

            TextRun run = new TextRun(runText.toString());
            String family = resolveRunFontFamily(poiRun, docDefaultFont);
            if (family != null) run.setFontFamily(family);
            double fontSize = resolveRunFontSize(poiRun, docDefaultSize);
            if (fontSize > 0) run.setFontSize(fontSize);
            run.setBold(poiRun.isBold());
            run.setItalic(poiRun.isItalic());
            run.setUnderline(poiRun.getUnderline() != UnderlinePatterns.NONE);
            run.setStrikethrough(poiRun.isStrikeThrough());

            String colorHex = poiRun.getColor();
            if (colorHex != null && (colorHex.length() == 6 || colorHex.length() == 8)) {
                try {
                    String hex = colorHex.startsWith("#") ? colorHex : "#" + colorHex;
                    run.setTextColor(javafx.scene.paint.Color.web(hex));
                } catch (Exception ignored) {
                }
            }

            // Highlight color
            try {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = poiRun.getCTR();
                if (ctr != null && ctr.isSetRPr() && ctr.getRPr().sizeOfHighlightArray() > 0 && ctr.getRPr().getHighlightArray(0).getVal() != null) {
                    String hl = ctr.getRPr().getHighlightArray(0).getVal().toString().toLowerCase();
                    javafx.scene.paint.Color hlCol = switch (hl) {
                        case "yellow" -> javafx.scene.paint.Color.rgb(255, 255, 0, 0.45);
                        case "green" -> javafx.scene.paint.Color.rgb(34, 197, 94, 0.35);
                        case "cyan" -> javafx.scene.paint.Color.rgb(6, 182, 212, 0.35);
                        case "magenta" -> javafx.scene.paint.Color.rgb(217, 70, 239, 0.35);
                        case "lightgray", "gray" -> javafx.scene.paint.Color.rgb(203, 213, 225, 0.5);
                        default -> null;
                    };
                    if (hlCol != null) run.setHighlightColor(hlCol);
                }
            } catch (Exception ignored) {
            }

            para.addRun(run);
        }

        return para;
    }

    private String resolveRunFontFamily(XWPFRun poiRun, String docDefaultFont) {
        if (poiRun == null) return docDefaultFont;
        try {
            String f = poiRun.getFontName();
            if (f != null && !f.isBlank()) return f;
        } catch (Exception ignored) {
        }
        try {
            String f = poiRun.getFontFamily();
            if (f != null && !f.isBlank()) return f;
        } catch (Exception ignored) {
        }
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = poiRun.getCTR();
            if (ctr != null && ctr.isSetRPr()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = ctr.getRPr();
                if (rPr.sizeOfRFontsArray() > 0) {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts fonts = rPr.getRFontsArray(0);
                    if (fonts != null) {
                        if (fonts.getAscii() != null && !fonts.getAscii().isBlank()) return fonts.getAscii();
                        if (fonts.getHAnsi() != null && !fonts.getHAnsi().isBlank()) return fonts.getHAnsi();
                        if (fonts.getCs() != null && !fonts.getCs().isBlank()) return fonts.getCs();
                        if (fonts.getEastAsia() != null && !fonts.getEastAsia().isBlank()) return fonts.getEastAsia();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return docDefaultFont;
    }

    private double resolveRunFontSize(XWPFRun poiRun, double docDefaultSize) {
        if (poiRun == null) return docDefaultSize;
        try {
            Double fs = poiRun.getFontSizeAsDouble();
            if (fs != null && fs > 0) return fs;
        } catch (Exception ignored) {
        }
        try {
            int fs = poiRun.getFontSize();
            if (fs > 0) return fs;
        } catch (Exception ignored) {
        }
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = poiRun.getCTR();
            if (ctr != null && ctr.isSetRPr()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = ctr.getRPr();
                if (rPr.sizeOfSzArray() > 0 && rPr.getSzArray(0).getVal() != null) {
                    double halfPoints = Double.parseDouble(String.valueOf(rPr.getSzArray(0).getVal()));
                    if (halfPoints > 0) return halfPoints / 2.0;
                }
                if (rPr.sizeOfSzCsArray() > 0 && rPr.getSzCsArray(0).getVal() != null) {
                    double halfPoints = Double.parseDouble(String.valueOf(rPr.getSzCsArray(0).getVal()));
                    if (halfPoints > 0) return halfPoints / 2.0;
                }
            }
        } catch (Exception ignored) {
        }
        return docDefaultSize;
    }

    private DocumentTable convertTable(XWPFTable poiTable, String docDefaultFont, double docDefaultSize) {
        DocumentTable table = new DocumentTable();

        // 1. Table alignment
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr =
                    poiTable.getCTTbl() != null ? poiTable.getCTTbl().getTblPr() : null;
            if (tblPr != null && tblPr.isSetJc() && tblPr.getJc().getVal() != null) {
                String jc = tblPr.getJc().getVal().toString();
                if ("center".equalsIgnoreCase(jc)) table.setAlignment(DocumentTable.Alignment.CENTER);
                else if ("right".equalsIgnoreCase(jc)) table.setAlignment(DocumentTable.Alignment.RIGHT);
                else table.setAlignment(DocumentTable.Alignment.LEFT);
            }
        } catch (Exception ignored) {
        }

        // 2. Default cell padding from tblCellMar
        double defaultPadTop = 3.0;
        double defaultPadBottom = 3.0;
        double defaultPadLeft = 5.4;
        double defaultPadRight = 5.4;
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr =
                    poiTable.getCTTbl() != null ? poiTable.getCTTbl().getTblPr() : null;
            if (tblPr != null && tblPr.isSetTblCellMar()) {
                var mar = tblPr.getTblCellMar();
                if (mar.isSetTop() && mar.getTop().getW() != null)
                    defaultPadTop = Double.parseDouble(String.valueOf(mar.getTop().getW())) / 20.0;
                if (mar.isSetBottom() && mar.getBottom().getW() != null)
                    defaultPadBottom = Double.parseDouble(String.valueOf(mar.getBottom().getW())) / 20.0;
                if (mar.isSetLeft() && mar.getLeft().getW() != null)
                    defaultPadLeft = Double.parseDouble(String.valueOf(mar.getLeft().getW())) / 20.0;
                if (mar.isSetRight() && mar.getRight().getW() != null)
                    defaultPadRight = Double.parseDouble(String.valueOf(mar.getRight().getW())) / 20.0;
            }
        } catch (Exception ignored) {
        }

        // 3. Column widths from tblGrid
        double[] gridWidths = null;
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl ctTbl = poiTable.getCTTbl();
            if (ctTbl != null && ctTbl.getTblGrid() != null) {
                java.util.List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol> cols =
                        ctTbl.getTblGrid().getGridColList();
                if (cols != null && !cols.isEmpty()) {
                    gridWidths = new double[cols.size()];
                    for (int i = 0; i < cols.size(); i++) {
                        Object w = cols.get(i).getW();
                        double twips = w != null ? Double.parseDouble(String.valueOf(w)) : 1440;
                        gridWidths[i] = twips / 20.0; // twips → points
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 4. Table borders
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr =
                    poiTable.getCTTbl() != null ? poiTable.getCTTbl().getTblPr() : null;
            if (tblPr != null && tblPr.isSetTblBorders()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders borders = tblPr.getTblBorders();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder b = null;
                if (borders.isSetTop()) b = borders.getTop();
                else if (borders.isSetBottom()) b = borders.getBottom();
                else if (borders.isSetLeft()) b = borders.getLeft();
                else if (borders.isSetInsideH()) b = borders.getInsideH();
                else if (borders.isSetInsideV()) b = borders.getInsideV();

                if (b != null) {
                    if (b.getSz() != null) {
                        table.setBorderWidth(Math.max(0.5, Double.parseDouble(String.valueOf(b.getSz())) / 8.0));
                    }
                    String hex = extractHexColor(b.getColor(), b.xgetColor());
                    if (hex != null) {
                        table.setBorderColor(javafx.scene.paint.Color.web("#" + hex));
                    }
                    if (b.getVal() != null) {
                        String v = b.getVal().toString();
                        if ("nil".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v)) {
                            table.setBorderWidth(0.0);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        int maxCols = 0;
        for (XWPFTableRow poiRow : poiTable.getRows()) {
            DocumentTableRow row = new DocumentTableRow();
            try {
                if (poiRow.getHeight() > 0) {
                    row.setHeight(poiRow.getHeight() / 20.0);
                }
            } catch (Exception ignored) {
            }

            int rowCols = 0;
            for (XWPFTableCell poiCell : poiRow.getTableCells()) {
                DocumentTableCell cell = new DocumentTableCell();
                cell.getParagraphs().clear();
                cell.setPaddingTop(defaultPadTop);
                cell.setPaddingBottom(defaultPadBottom);
                cell.setPaddingLeft(defaultPadLeft);
                cell.setPaddingRight(defaultPadRight);

                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = null;
                try {
                    tcPr = poiCell.getCTTc() != null ? poiCell.getCTTc().getTcPr() : null;
                } catch (Exception ignored) {}

                if (tcPr != null) {
                    // Cell shading
                    try {
                        if (tcPr.isSetShd()) {
                            var shd = tcPr.getShd();
                            String hex = extractHexColor(shd.getFill(), shd.xgetFill());
                            if (hex == null) {
                                hex = extractHexColor(shd.getColor(), shd.xgetColor());
                            }
                            if (hex != null) {
                                cell.setBackgroundColor(javafx.scene.paint.Color.web("#" + hex));
                            }
                        }
                    } catch (Exception ignored) {}

                    // Cell borders
                    try {
                        if (tcPr.isSetTcBorders()) {
                            var tb = tcPr.getTcBorders();
                            if (tb.isSetTop() && tb.getTop().getVal() != null) {
                                String v = tb.getTop().getVal().toString();
                                if ("nil".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v)) cell.setBorderTop(false);
                            }
                            if (tb.isSetBottom() && tb.getBottom().getVal() != null) {
                                String v = tb.getBottom().getVal().toString();
                                if ("nil".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v)) cell.setBorderBottom(false);
                            }
                            if (tb.isSetLeft() && tb.getLeft().getVal() != null) {
                                String v = tb.getLeft().getVal().toString();
                                if ("nil".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v)) cell.setBorderLeft(false);
                            }
                            if (tb.isSetRight() && tb.getRight().getVal() != null) {
                                String v = tb.getRight().getVal().toString();
                                if ("nil".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v)) cell.setBorderRight(false);
                            }
                        }
                    } catch (Exception ignored) {}

                    // GridSpan (merged columns)
                    try {
                        if (tcPr.isSetGridSpan() && tcPr.getGridSpan().getVal() != null) {
                            int span = Integer.parseInt(String.valueOf(tcPr.getGridSpan().getVal()));
                            if (span > 1) cell.setColSpan(span);
                        }
                    } catch (Exception ignored) {}

                    // Vertical alignment
                    try {
                        if (tcPr.isSetVAlign() && tcPr.getVAlign().getVal() != null) {
                            String va = tcPr.getVAlign().getVal().toString();
                            if ("center".equalsIgnoreCase(va)) cell.setVerticalAlignment(DocumentTableCell.VAlign.CENTER);
                            else if ("bottom".equalsIgnoreCase(va)) cell.setVerticalAlignment(DocumentTableCell.VAlign.BOTTOM);
                        }
                    } catch (Exception ignored) {}

                    // Cell margins (padding) overrides
                    try {
                        if (tcPr.isSetTcMar()) {
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar mar = tcPr.getTcMar();
                            if (mar.isSetLeft() && mar.getLeft().getW() != null)
                                cell.setPaddingLeft(Double.parseDouble(String.valueOf(mar.getLeft().getW())) / 20.0);
                            if (mar.isSetRight() && mar.getRight().getW() != null)
                                cell.setPaddingRight(Double.parseDouble(String.valueOf(mar.getRight().getW())) / 20.0);
                            if (mar.isSetTop() && mar.getTop().getW() != null)
                                cell.setPaddingTop(Double.parseDouble(String.valueOf(mar.getTop().getW())) / 20.0);
                            if (mar.isSetBottom() && mar.getBottom().getW() != null)
                                cell.setPaddingBottom(Double.parseDouble(String.valueOf(mar.getBottom().getW())) / 20.0);
                        }
                    } catch (Exception ignored) {}
                }

                for (XWPFParagraph p : poiCell.getParagraphs()) {
                    Paragraph cp = convertParagraph(p, docDefaultFont, docDefaultSize);
                    // Table cells default to tight spacing unless explicit
                    if (p.getSpacingBefore() <= 0 && (p.getCTP() == null || !p.getCTP().isSetPPr() || !p.getCTP().getPPr().isSetSpacing() || !p.getCTP().getPPr().getSpacing().isSetBefore())) {
                        cp.setSpaceBefore(0.0);
                    }
                    if (p.getSpacingAfter() <= 0 && (p.getCTP() == null || !p.getCTP().isSetPPr() || !p.getCTP().getPPr().isSetSpacing() || !p.getCTP().getPPr().getSpacing().isSetAfter())) {
                        cp.setSpaceAfter(0.0);
                    }
                    cell.addParagraph(cp);
                }
                if (cell.getParagraphs().isEmpty()) {
                    cell.addParagraph(new Paragraph());
                }

                rowCols += cell.getColSpan();
                row.addCell(cell);
            }
            maxCols = Math.max(maxCols, rowCols);
            table.addRow(row);
        }

        // Table-level shading inheritance
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr =
                    poiTable.getCTTbl() != null ? poiTable.getCTTbl().getTblPr() : null;
            if (tblPr != null && tblPr.isSetShd()) {
                String hex = extractHexColor(tblPr.getShd().getFill(), tblPr.getShd().xgetFill());
                if (hex != null) {
                    javafx.scene.paint.Color tblBg = javafx.scene.paint.Color.web("#" + hex);
                    for (DocumentTableRow r : table.getRows()) {
                        for (DocumentTableCell c : r.getCells()) {
                            if (c.getBackgroundColor() == null) {
                                c.setBackgroundColor(tblBg);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (gridWidths != null && gridWidths.length > 0) {
            table.setColumnWidths(gridWidths);
            table.setColumnCount(gridWidths.length);
        } else {
            table.setColumnCount(Math.max(1, maxCols));
        }

        return table;
    }

    private boolean isPageBreakParagraph(XWPFParagraph p) {
        if (p == null) return false;
        try {
            if (p.isPageBreak()) return true;
        } catch (Exception ignored) {}
        try {
            if (p.getCTP() != null && p.getCTP().getPPr() != null && p.getCTP().getPPr().isSetPageBreakBefore()) {
                return true;
            }
        } catch (Exception ignored) {}
        try {
            for (XWPFRun r : p.getRuns()) {
                if (r.getCTR() != null) {
                    for (var br : r.getCTR().getBrList()) {
                        if (br.getType() != null && "page".equalsIgnoreCase(br.getType().toString())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String extractHexColor(Object colorObj, org.apache.xmlbeans.XmlObject xmlObj) {
        if (xmlObj != null) {
            try {
                if (xmlObj instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.STHexColor hc) {
                    String s = hc.getStringValue();
                    if (s != null && !s.isBlank() && !"auto".equalsIgnoreCase(s)) return s.trim();
                }
            } catch (Exception ignored) {}
            try {
                String s = xmlObj.toString();
                if (s != null && s.contains("=")) {
                    s = s.substring(s.lastIndexOf('=') + 1).replace("\"", "").replace("'", "").trim();
                }
                if (s != null && s.length() == 6 && !"auto".equalsIgnoreCase(s)) return s;
            } catch (Exception ignored) {}
        }
        if (colorObj instanceof byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        }
        if (colorObj != null) {
            String s = colorObj.toString().trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6 && !"auto".equalsIgnoreCase(s)) return s;
        }
        return null;
    }
}

