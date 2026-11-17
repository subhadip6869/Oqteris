package app.vercel.originsd.oqteris.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import app.vercel.originsd.oqteris.pdf.model.PdfDocumentModel;
import app.vercel.originsd.oqteris.pdf.render.BoundedResourceCache;
import app.vercel.originsd.oqteris.pdf.render.PdfPageRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class BoundedResourceCacheTest {

    @TempDir
    File tempDir;

    @Test
    public void testBoundedResourceCacheEviction() {
        BoundedResourceCache cache = new BoundedResourceCache(3);
        assertEquals(0, cache.getCachedXObjectCount());

        org.apache.pdfbox.cos.COSObject o1 = new org.apache.pdfbox.cos.COSObject(org.apache.pdfbox.cos.COSName.A);
        org.apache.pdfbox.cos.COSObject o2 = new org.apache.pdfbox.cos.COSObject(org.apache.pdfbox.cos.COSName.B);
        org.apache.pdfbox.cos.COSObject o3 = new org.apache.pdfbox.cos.COSObject(org.apache.pdfbox.cos.COSName.C);
        org.apache.pdfbox.cos.COSObject o4 = new org.apache.pdfbox.cos.COSObject(org.apache.pdfbox.cos.COSName.D);

        try (PDDocument doc = new PDDocument()) {
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            PDImageXObject x1 = LosslessFactory.createFromImage(doc, img);
            PDImageXObject x2 = LosslessFactory.createFromImage(doc, img);
            PDImageXObject x3 = LosslessFactory.createFromImage(doc, img);
            PDImageXObject x4 = LosslessFactory.createFromImage(doc, img);

            cache.put(o1, x1);
            cache.put(o2, x2);
            cache.put(o3, x3);
            assertEquals(3, cache.getCachedXObjectCount());

            // Adding a 4th entry must evict the eldest (o1)
            cache.put(o4, x4);
            assertEquals(3, cache.getCachedXObjectCount());
            assertNull(cache.getXObject(o1), "Oldest entry o1 should have been evicted");
            assertNotNull(cache.getXObject(o2));
            assertNotNull(cache.getXObject(o3));
            assertNotNull(cache.getXObject(o4));

            // Clear cache
            cache.clear();
            assertEquals(0, cache.getCachedXObjectCount());
        } catch (IOException e) {
            fail("Unexpected IOException: " + e.getMessage());
        }
    }

    @Test
    public void testPdfDocumentModelUsesBoundedCache() throws IOException {
        File pdfFile = new File(tempDir, "test_bounded_model.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 5; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            doc.save(pdfFile);
        }

        try (PdfDocumentModel model = new PdfDocumentModel(pdfFile)) {
            assertNotNull(model.getPdDocument());
            assertInstanceOf(BoundedResourceCache.class, model.getPdDocument().getResourceCache(),
                    "PdfDocumentModel must configure BoundedResourceCache on the PDDocument");
        }
    }

    @Test
    public void testMultiPageRenderingMemoryStability() throws IOException {
        File pdfFile = new File(tempDir, "test_multi_page.pdf");
        int pageCount = 20;

        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                BufferedImage img = new BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.RED);
                g.fillRect(0, 0, 150, 150);
                g.dispose();

                PDImageXObject ximage = LosslessFactory.createFromImage(doc, img);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(ximage, 50, 200, 150, 150);
                }
            }
            doc.save(pdfFile);
        }

        try (PdfDocumentModel model = new PdfDocumentModel(pdfFile)) {
            PdfPageRenderer renderer = new PdfPageRenderer(model.getPdDocument());
            for (int i = 0; i < pageCount; i++) {
                assertNotNull(renderer.renderPage(i, 1.0), "Page " + i + " must render");
            }

            BoundedResourceCache cache = (BoundedResourceCache) model.getPdDocument().getResourceCache();
            assertTrue(cache.getCachedXObjectCount() <= BoundedResourceCache.DEFAULT_MAX_XOBJECTS,
                    "Cached XObjects count should not exceed DEFAULT_MAX_XOBJECTS even after rendering " + pageCount + " pages");

            renderer.clearCache();
        }
    }
}
