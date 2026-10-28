package org.originsd.oqteris.presentation.io;

import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.presentation.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Importer for OpenDocument Presentation (.odp) files.
 */
public class OdpImporter implements DocumentImporter<Presentation> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "ODP",
            "OpenDocument Presentation (*.odp)",
            DocumentType.IMPRESS,
            List.of("odp"),
            "application/vnd.oasis.opendocument.presentation",
            true,
            false
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new OdpImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".odp");
    }

    @Override
    public Presentation importDocument(File file) throws IOException {
        Presentation presentation = new Presentation(file.getName());
        presentation.setFile(file);
        presentation.clearSlides();

        try (FileInputStream fis = new FileInputStream(file);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("content.xml".equalsIgnoreCase(entry.getName())) {
                    parseContentXml(zis, presentation);
                    break;
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse ODP presentation: " + e.getMessage(), e);
        }

        if (presentation.getSlideCount() == 0) {
            presentation.createSlide("Slide 1");
        }

        presentation.setModified(false);
        return presentation;
    }

    private void parseContentXml(InputStream is, Presentation presentation) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document dom = db.parse(is);

        NodeList pages = dom.getElementsByTagNameNS("*", "page");
        for (int p = 0; p < pages.getLength(); p++) {
            Element pageEl = (Element) pages.item(p);
            String pageName = pageEl.getAttributeNS("*", "name");
            if (pageName == null || pageName.isBlank()) {
                pageName = "Slide " + (p + 1);
            }
            Slide slide = new Slide(pageName);

            // Extract frames (text boxes, shapes)
            NodeList frames = pageEl.getElementsByTagNameNS("*", "frame");
            for (int f = 0; f < frames.getLength(); f++) {
                Element frameEl = (Element) frames.item(f);
                double x = parseDimension(frameEl.getAttributeNS("*", "x"), 80 + (f * 30));
                double y = parseDimension(frameEl.getAttributeNS("*", "y"), 60 + (f * 40));
                double w = parseDimension(frameEl.getAttributeNS("*", "width"), 700);
                double h = parseDimension(frameEl.getAttributeNS("*", "height"), 120);

                NodeList textNodes = frameEl.getElementsByTagNameNS("*", "p");
                StringBuilder sb = new StringBuilder();
                for (int t = 0; t < textNodes.getLength(); t++) {
                    String pText = textNodes.item(t).getTextContent();
                    if (!pText.isBlank()) {
                        sb.append(pText.trim()).append("\n");
                    }
                }
                String text = sb.toString().trim();
                if (!text.isEmpty()) {
                    boolean isTitle = f == 0 || y < 150;
                    slide.addShape(new TextBoxShape(x, y, w, h, text, isTitle ? 26.0 : 18.0, isTitle));
                }
            }

            presentation.addSlide(slide);
        }
    }

    private double parseDimension(String dim, double fallback) {
        if (dim == null || dim.isBlank()) return fallback;
        try {
            if (dim.endsWith("cm")) {
                double cm = Double.parseDouble(dim.replace("cm", "").trim());
                return cm * 37.795; // 1cm ~ 37.8px
            } else if (dim.endsWith("in") || dim.endsWith("inch")) {
                double in = Double.parseDouble(dim.replace("in", "").replace("ch", "").trim());
                return in * 96.0;
            } else if (dim.endsWith("pt")) {
                double pt = Double.parseDouble(dim.replace("pt", "").trim());
                return pt * 1.333;
            } else {
                return Double.parseDouble(dim.replaceAll("[^0-9.]", "").trim());
            }
        } catch (Exception e) {
            return fallback;
        }
    }
}
