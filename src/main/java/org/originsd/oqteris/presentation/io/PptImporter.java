package org.originsd.oqteris.presentation.io;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.presentation.model.Presentation;
import org.originsd.oqteris.presentation.model.Slide;
import org.originsd.oqteris.presentation.model.TextBoxShape;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Importer for legacy Microsoft PowerPoint 97-2003 binary presentations (.ppt).
 */
public class PptImporter implements DocumentImporter<Presentation> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "PPT",
            "PowerPoint 97-2003 Presentation (*.ppt)",
            DocumentType.IMPRESS,
            List.of("ppt"),
            "application/vnd.ms-powerpoint",
            true,
            false
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new PptImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".ppt");
    }

    @Override
    public Presentation importDocument(File file) throws IOException {
        Presentation presentation = new Presentation(file.getName());
        presentation.setFile(file);
        presentation.clearSlides();

        String extracted = null;

        try (POITextExtractor extractor = ExtractorFactory.createExtractor(file)) {
            extracted = extractor.getText();
        } catch (Exception ex) {
            try (FileInputStream fis = new FileInputStream(file);
                 POIFSFileSystem poifs = new POIFSFileSystem(fis)) {
                if (poifs.getRoot().hasEntry("PowerPoint Document")) {
                    try (DocumentInputStream dis = poifs.createDocumentInputStream("PowerPoint Document")) {
                        byte[] bytes = dis.readAllBytes();
                        extracted = extractPrintableText(bytes);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (extracted != null && !extracted.isBlank()) {
            // Split into slides by double newlines or form feed
            String[] slideTexts = extracted.split("(\\r?\\n\\r?\\n|\\f)");
            int sNum = 1;
            for (String st : slideTexts) {
                st = st.trim();
                if (st.isEmpty()) continue;
                Slide slide = new Slide("Slide " + sNum);
                String[] lines = st.split("\\r?\\n");
                String title = lines[0].trim();
                StringBuilder body = new StringBuilder();
                for (int i = 1; i < lines.length; i++) {
                    body.append(lines[i].trim()).append("\n");
                }

                // Add Title shape
                slide.addShape(new TextBoxShape(100, 60, 760, 60, title, 28.0, true));
                // Add Body shape if exists
                if (body.length() > 0) {
                    slide.addShape(new TextBoxShape(100, 150, 760, 340, body.toString().trim(), 18.0, false));
                }
                presentation.addSlide(slide);
                sNum++;
            }
        }

        if (presentation.getSlideCount() == 0) {
            presentation.createSlide("Slide 1");
        }

        presentation.setModified(false);
        return presentation;
    }

    private String extractPrintableText(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (b >= 32 && b <= 126) {
                sb.append((char) b);
            } else if (b == '\n') {
                sb.append('\n');
            } else if (b == '\t') {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
