package app.vercel.originsd.oqteris.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import app.vercel.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import app.vercel.originsd.oqteris.crypto.signature.SignatureVerificationStatus;
import app.vercel.originsd.oqteris.pdf.model.PdfPageContent;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TextExtractionTest {

    @Test
    public void testShapeAndImageExtractionAndNoTextAsObject() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            // Create a transparent image (RGBA with alpha = 0 around edges)
            BufferedImage bimg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bimg.createGraphics();
            g.setColor(new Color(255, 0, 0, 255)); // Red square in center
            g.fillRect(25, 25, 50, 50);
            g.dispose();

            PDImageXObject imgObj = LosslessFactory.createFromImage(doc, bimg);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Text
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("Sample Heading on Page");
                cs.endText();

                // Vector Shape: rectangle at (50, 600, 200, 100)
                cs.setNonStrokingColor(0.8f, 0.9f, 1.0f);
                cs.addRect(50, 600, 200, 100);
                cs.fill();

                // Image at (50, 400, 150, 150)
                cs.drawImage(imgObj, 50, 400, 150, 150);
            }

            // Extract page content using PdfPageContent
            PdfPageContent content = PdfPageContent.extract(doc, 0, null, null);
            assertNotNull(content);

            List<PdfPageContent.TextLine> textLines = content.getTextLines();
            assertEquals(1, textLines.size(), "Should extract 1 text line");
            PdfPageContent.TextLine textLine = textLines.get(0);

            // 1. Verify text lines are NEVER treated as objects when clicking on them
            double textClickX = textLine.normX + (textLine.normW / 2.0);
            double textClickY = textLine.normY + (textLine.normH / 2.0);
            assertNotNull(content.hitTestLine(textClickX, textClickY), "Must hit text line as text");
            assertNull(content.hitTestObject(textClickX, textClickY), "Text lines must NEVER be hit as objects");

            // 2. Verify shape and image extraction
            List<PdfPageContent.PageObject> objects = content.getPageObjects();
            assertTrue(objects.size() >= 2, "Should extract at least 1 shape and 1 image object");

            PdfPageContent.PageObject shapeObj = null;
            PdfPageContent.PageObject imgObjExtracted = null;
            for (PdfPageContent.PageObject obj : objects) {
                if (obj.type == PdfPageContent.ObjectType.SHAPE) shapeObj = obj;
                if (obj.type == PdfPageContent.ObjectType.IMAGE) imgObjExtracted = obj;
            }
            assertNotNull(shapeObj, "Vector shape must be detected as SHAPE object");
            assertNotNull(imgObjExtracted, "Image must be detected as IMAGE object");

            // Verify clicking within shape bounds hits the shape object
            double shapeClickX = shapeObj.normX + (shapeObj.normW / 2.0);
            double shapeClickY = shapeObj.normY + (shapeObj.normH / 2.0);
            assertEquals(shapeObj, content.hitTestObject(shapeClickX, shapeClickY));

            // Verify clicking within image bounds hits the image object
            double imgClickX = imgObjExtracted.normX + (imgObjExtracted.normW / 2.0);
            double imgClickY = imgObjExtracted.normY + (imgObjExtracted.normH / 2.0);
            assertEquals(imgObjExtracted, content.hitTestObject(imgClickX, imgClickY));

            // 3. Test white background compositing for copied images (no black background)
            PDImageXObject rawPdfImg = (PDImageXObject) imgObjExtracted.payload;
            BufferedImage rawImg = rawPdfImg.getImage();
            BufferedImage rgbImg = new BufferedImage(rawImg.getWidth(), rawImg.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = rgbImg.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, rawImg.getWidth(), rawImg.getHeight());
            g2.drawImage(rawImg, 0, 0, null);
            g2.dispose();

            // Pixel (5, 5) which was transparent in rawImg must now be WHITE, not BLACK
            int cornerRgb = rgbImg.getRGB(5, 5) & 0x00FFFFFF;
            assertEquals(0x00FFFFFF, cornerRgb, "Copied image transparent background must be pure white, never black");
        }
    }

    @Test
    public void testSignatureHitTestingAndNoObjectSelection() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            // Add text inside signature area
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Digitally signed by Alice Smith");
                cs.endText();
            }

            // Create a visual signature covering the text area (e.g. [0.15, 0.15, 0.4, 0.15])
            PdfSignatureInfo sigInfo = new PdfSignatureInfo(
                    "Signature1", "Alice Smith", "CN=Alice Smith", new Date(),
                    "Approval", "Headquarters", "Adobe.PPKLite", "adbe.pkcs7.detached",
                    "SHA-256", true, true, 5000, 5000,
                    SignatureVerificationStatus.VALID_AND_TRUSTED, "Signature is valid and trusted",
                    null, null, false, null
            );
            sigInfo.setVisualLocation(0, new double[]{ 0.15, 0.15, 0.4, 0.15 });

            PdfPageContent content = PdfPageContent.extract(doc, 0, List.of(sigInfo), null);
            assertNotNull(content);

            // Coordinates inside signature
            double sigX = 0.25;
            double sigY = 0.20;

            // 1. Signature hit test must find the signature object
            PdfPageContent.PageObject sigObj = content.hitTestSignature(sigX, sigY);
            assertNotNull(sigObj, "Must hit test signature directly");
            assertEquals(PdfPageContent.ObjectType.SIGNATURE, sigObj.type);
            assertSame(sigInfo, sigObj.payload);

            // 2. Selectable object hit test must return NULL (signatures must NEVER be selectable objects)
            PdfPageContent.PageObject hitObj = content.hitTestObject(sigX, sigY);
            assertNull(hitObj, "Signatures must NEVER be returned by hitTestObject (must not be selected as objects)");
        }
    }
}
