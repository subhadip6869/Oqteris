package org.oqteris.samples;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.oqteris.calc.formula.CalculationEngine;
import org.oqteris.calc.io.XlsxExporter;
import org.oqteris.calc.model.*;
import org.oqteris.presentation.io.PptxExporter;
import org.oqteris.presentation.model.*;
import org.oqteris.writer.io.DocxExporter;
import org.oqteris.writer.model.*;

import java.io.*;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator test that creates real, high quality sample office documents
 * in the samples/ directory:
 * - sample_document.docx
 * - sample_sheet.xlsx
 * - sample_presentation.pptx
 * - sample_signed.pdf
 * - oqteris_demo_signer.cer
 */
public class SampleDataGeneratorTest {

    @Test
    public void generateAllSampleDocuments() throws Exception {
        File samplesDir = new File("samples");
        if (!samplesDir.exists()) {
            samplesDir.mkdirs();
        }

        generateSampleDocx(new File(samplesDir, "sample_document.docx"));
        generateSampleXlsx(new File(samplesDir, "sample_sheet.xlsx"));
        generateSamplePptx(new File(samplesDir, "sample_presentation.pptx"));
        generateSampleSignedPdf(
                new File(samplesDir, "sample_signed.pdf"),
                new File(samplesDir, "oqteris_demo_signer.cer")
        );
    }

    private void generateSampleDocx(File target) throws IOException {
        WriterDocument doc = new WriterDocument("Oqteris Document Specification");
        doc.clearElements();

        // Title Paragraph
        Paragraph titleP = new Paragraph();
        titleP.setAlignment(Paragraph.Alignment.CENTER);
        TextRun titleRun = new TextRun("Oqteris Office Suite Specification");
        titleRun.setFontSize(24.0);
        titleRun.setBold(true);
        titleRun.setTextColor(javafx.scene.paint.Color.rgb(16, 68, 115));
        titleP.addRun(titleRun);
        doc.addElement(titleP);

        // Subtitle
        Paragraph subP = new Paragraph();
        subP.setAlignment(Paragraph.Alignment.CENTER);
        TextRun subRun = new TextRun("Independent Cross-Platform Desktop Office Architecture & Implementation");
        subRun.setFontSize(13.0);
        subRun.setItalic(true);
        subRun.setTextColor(javafx.scene.paint.Color.rgb(100, 100, 100));
        subP.addRun(subRun);
        doc.addElement(subP);

        // Section 1
        Paragraph h1 = new Paragraph();
        h1.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
        TextRun h1Run = new TextRun("1. Executive Summary");
        h1Run.setFontSize(18.0);
        h1Run.setBold(true);
        h1.addRun(h1Run);
        doc.addElement(h1);

        Paragraph body1 = new Paragraph();
        TextRun b1Run = new TextRun("Oqteris is an independent, cross-platform desktop office suite developed in modern Java 21 LTS and JavaFX. It provides full-featured productivity applications comprising a Word Processor (Writer), Spreadsheet Editor (Calc), Presentation Designer (Impress), and PDF Viewer with cryptographic digital signature verification and X.509 certificate trust management.");
        b1Run.setFontSize(11.0);
        body1.addRun(b1Run);
        doc.addElement(body1);

        // Section 2 - Bullet Points
        Paragraph h2 = new Paragraph();
        h2.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
        TextRun h2Run = new TextRun("2. Architectural Highlights");
        h2Run.setFontSize(14.0);
        h2Run.setBold(true);
        h2.addRun(h2Run);
        doc.addElement(h2);

        String[] highlights = {
                "Independent layout and rendering pipeline without external office runtime dependencies",
                "Advanced spreadsheet formula engine with DAG dependency cycle detection and 30+ Excel functions",
                "Presentation editor featuring interactive vector shape canvases, z-ordering, and full-screen slideshow",
                "Cryptographic PDF verification separating mathematical signature validity from certificate trust authority"
        };
        for (String item : highlights) {
            Paragraph bp = new Paragraph();
            bp.setListType(Paragraph.ListType.BULLET);
            TextRun r = new TextRun(item);
            r.setFontSize(11.0);
            bp.addRun(r);
            doc.addElement(bp);
        }

        // Section 3 - Table
        Paragraph h3 = new Paragraph();
        h3.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
        TextRun h3Run = new TextRun("3. Core Subsystem Performance Matrix");
        h3Run.setFontSize(14.0);
        h3Run.setBold(true);
        h3.addRun(h3Run);
        doc.addElement(h3);

        DocumentTable table = new DocumentTable(4, 3);
        String[][] tableData = {
                {"Subsystem", "Primary Format", "Engine Status"},
                {"Word Processor", "DOCX / RTF / HTML", "Active / Independent Pagination"},
                {"Spreadsheet Calc", "XLSX / CSV", "Active / DAG Recalculation Engine"},
                {"Presentation Impress", "PPTX / PDF Export", "Active / Canvas Vector Rendering"}
        };

        for (int r = 0; r < 4; r++) {
            DocumentTableRow row = table.getRows().get(r);
            for (int c = 0; c < 3; c++) {
                DocumentTableCell cell = row.getCells().get(c);
                Paragraph cp = new Paragraph();
                TextRun cr = new TextRun(tableData[r][c]);
                if (r == 0) {
                    cr.setBold(true);
                    cell.setBackgroundColor(javafx.scene.paint.Color.rgb(230, 240, 250));
                }
                cp.addRun(cr);
                cell.getParagraphs().clear();
                cell.addParagraph(cp);
            }
        }
        doc.addElement(table);

        DocxExporter exporter = new DocxExporter();
        exporter.exportDocument(doc, target);
        assertTrue(target.exists() && target.length() > 0);
    }

    private void generateSampleXlsx(File target) throws IOException {
        Workbook wb = new Workbook("Q1 Financial Report");
        Worksheet sheet = wb.getSheet(0);
        sheet.setName("Q1 Financials");

        // Title row
        Cell titleCell = sheet.getOrCreateCell(0, 0);
        titleCell.setValue("Oqteris Corp - Q1 Financial Overview");
        CellStyle titleStyle = new CellStyle();
        titleStyle.setBold(true);
        titleStyle.setFontSize(14.0);
        titleStyle.setTextColor(javafx.scene.paint.Color.rgb(16, 68, 115));
        titleCell.setStyle(titleStyle);

        // Header row
        String[] headers = {"Department", "Budget ($)", "Actual ($)", "Variance ($)", "Performance"};
        sheet.setColumnWidth(0, 160.0);
        sheet.setColumnWidth(1, 120.0);
        sheet.setColumnWidth(2, 120.0);
        sheet.setColumnWidth(3, 120.0);
        sheet.setColumnWidth(4, 130.0);

        for (int c = 0; c < headers.length; c++) {
            Cell cell = sheet.getOrCreateCell(2, c);
            cell.setValue(headers[c]);
            CellStyle hs = new CellStyle();
            hs.setBold(true);
            hs.setFillColor(javafx.scene.paint.Color.rgb(220, 235, 245));
            hs.setAlignment(CellStyle.Alignment.CENTER);
            cell.setStyle(hs);
        }

        // Data rows
        Object[][] data = {
                {"Engineering", 150000.0, 142500.0},
                {"Marketing", 80000.0, 84200.0},
                {"Operations", 65000.0, 61800.0},
                {"Sales", 110000.0, 108900.0}
        };

        CellStyle numStyle = new CellStyle();
        numStyle.setNumberFormat("$#,##0.00");
        numStyle.setAlignment(CellStyle.Alignment.RIGHT);

        for (int i = 0; i < data.length; i++) {
            int rowIdx = 3 + i;
            int excelRow = rowIdx + 1; // 1-based

            // Dept
            Cell c0 = sheet.getOrCreateCell(rowIdx, 0);
            c0.setValue((String) data[i][0]);

            // Budget
            Cell c1 = sheet.getOrCreateCell(rowIdx, 1);
            c1.setValue((Double) data[i][1]);
            c1.setStyle(numStyle);

            // Actual
            Cell c2 = sheet.getOrCreateCell(rowIdx, 2);
            c2.setValue((Double) data[i][2]);
            c2.setStyle(numStyle);

            // Variance: =B - C
            Cell c3 = sheet.getOrCreateCell(rowIdx, 3);
            c3.setFormula("B" + excelRow + "-C" + excelRow);
            c3.setStyle(numStyle);

            // Performance: IF(B>C, "Under Budget", "Over Budget")
            Cell c4 = sheet.getOrCreateCell(rowIdx, 4);
            c4.setFormula("IF(B" + excelRow + ">=C" + excelRow + ",\"Under Budget\",\"Over Budget\")");
            CellStyle ps = new CellStyle();
            ps.setAlignment(CellStyle.Alignment.CENTER);
            c4.setStyle(ps);
        }

        // Summary row
        int totalRow = 3 + data.length;
        int totalExcelRow = totalRow + 1;

        Cell labelCell = sheet.getOrCreateCell(totalRow, 0);
        labelCell.setValue("Total / Average");
        CellStyle totalStyle = new CellStyle();
        totalStyle.setBold(true);
        totalStyle.setFillColor(javafx.scene.paint.Color.rgb(240, 240, 240));
        labelCell.setStyle(totalStyle);

        // SUM Budget
        Cell totalBudget = sheet.getOrCreateCell(totalRow, 1);
        totalBudget.setFormula("SUM(B4:B" + (totalExcelRow - 1) + ")");
        CellStyle ts1 = numStyle.copy();
        ts1.setBold(true);
        totalBudget.setStyle(ts1);

        // SUM Actual
        Cell totalActual = sheet.getOrCreateCell(totalRow, 2);
        totalActual.setFormula("SUM(C4:C" + (totalExcelRow - 1) + ")");
        CellStyle ts2 = numStyle.copy();
        ts2.setBold(true);
        totalActual.setStyle(ts2);

        // SUM Variance
        Cell totalVar = sheet.getOrCreateCell(totalRow, 3);
        totalVar.setFormula("SUM(D4:D" + (totalExcelRow - 1) + ")");
        CellStyle ts3 = numStyle.copy();
        ts3.setBold(true);
        totalVar.setStyle(ts3);

        CalculationEngine engine = new CalculationEngine();
        engine.recalculateWorkbook(wb);

        XlsxExporter exporter = new XlsxExporter();
        exporter.exportDocument(wb, target);
        assertTrue(target.exists() && target.length() > 0);
    }

    private void generateSamplePptx(File target) throws IOException {
        Presentation pres = new Presentation("Oqteris Overview");
        pres.clearSlides();

        // Slide 1 - Title
        Slide s1 = Slide.createTitleSlide("Oqteris Office Suite", "Independent, Modern Cross-Platform Office Productivity");
        pres.addSlide(s1);

        // Slide 2 - Architecture Highlights
        Slide s2 = Slide.createContentSlide("Productivity Applications",
                "• Oqteris Writer: Standalone Word Processor with DOCX layout & pagination\n" +
                "• Oqteris Calc: Virtualized Canvas Spreadsheet with DAG formula recalculation\n" +
                "• Oqteris Impress: Presentation Canvas with transitions & slideshow mode\n" +
                "• Oqteris PDF: Fast PDF viewer with cryptographic signature & trust verification");
        pres.addSlide(s2);

        // Slide 3 - Diagram & Shapes
        Slide s3 = new Slide("Vector Geometry Canvas");
        TextBoxShape h3 = new TextBoxShape(60, 40, 840, 50, "Vector Geometry & Custom Visuals", 26.0, true);
        h3.setTextColor(javafx.scene.paint.Color.rgb(16, 68, 115));
        s3.addShape(h3);

        GeometricShape rect = new GeometricShape(GeometricShape.Geometry.ROUNDED_RECTANGLE, 80, 120, 240, 140);
        rect.setFillColor(javafx.scene.paint.Color.rgb(65, 117, 164));
        s3.addShape(rect);

        GeometricShape ellipse = new GeometricShape(GeometricShape.Geometry.ELLIPSE, 360, 120, 240, 140);
        ellipse.setFillColor(javafx.scene.paint.Color.rgb(76, 175, 80));
        s3.addShape(ellipse);

        GeometricShape tri = new GeometricShape(GeometricShape.Geometry.TRIANGLE, 640, 120, 240, 140);
        tri.setFillColor(javafx.scene.paint.Color.rgb(230, 81, 0));
        s3.addShape(tri);

        TextBoxShape descBox = new TextBoxShape(80, 300, 800, 100,
                "Shapes are rendered cleanly on JavaFX Canvas and preserved accurately through standard Office Open XML (PPTX) export and import pipelines.",
                16.0, false);
        descBox.setTextColor(javafx.scene.paint.Color.rgb(60, 60, 60));
        s3.addShape(descBox);

        pres.addSlide(s3);

        PptxExporter exporter = new PptxExporter();
        exporter.exportDocument(pres, target);
        assertTrue(target.exists() && target.length() > 0);
    }

    private void generateSampleSignedPdf(File targetPdf, File targetCert) throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // 1. Generate RSA Key Pair and X.509 Certificate
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        long now = System.currentTimeMillis();
        Date startDate = new Date(now - 1000L * 60 * 60 * 24);
        Date endDate = new Date(now + 1000L * 60 * 60 * 24 * 365 * 3);
        BigInteger serial = BigInteger.valueOf(now);
        X500Name dnName = new X500Name("CN=Oqteris Certified Signer, O=Oqteris Office Project, C=US");

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, serial, startDate, endDate, dnName, keyPair.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certBuilder.build(signer));

        // Export certificate to file
        try (FileOutputStream fos = new FileOutputStream(targetCert)) {
            fos.write(cert.getEncoded());
        }

        // 2. Create base PDF document
        File tempBasePdf = File.createTempFile("oqteris_base_", ".pdf");
        tempBasePdf.deleteOnExit();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
                cs.newLineAtOffset(50, 750);
                cs.showText("Oqteris Office Suite - Digitally Signed Document");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 710);
                cs.showText("This sample PDF demonstrates Oqteris cryptographic signature verification.");
                cs.newLineAtOffset(0, -20);
                cs.showText("The document is sealed with an embedded CMS / PKCS#7 digital signature.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Subject: CN=Oqteris Certified Signer, O=Oqteris Office Project, C=US");
                cs.newLineAtOffset(0, -20);
                cs.showText("Algorithm: SHA256withRSA (2048-bit)");
                cs.newLineAtOffset(0, -20);
                cs.showText("Mathematical validity: Cryptographically authentic and untampered.");
                cs.endText();
            }

            doc.save(tempBasePdf);
        }

        // 3. Digitally sign the PDF using PDFBox incremental saving
        try (PDDocument docToSign = org.apache.pdfbox.Loader.loadPDF(tempBasePdf)) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("Oqteris Certified Signer");
            signature.setLocation("Worldwide Desktop");
            signature.setReason("Demonstration of Cryptographic Integrity & Signature Verification");
            signature.setSignDate(Calendar.getInstance());

            docToSign.addSignature(signature, new SignatureInterface() {
                @Override
                public byte[] sign(InputStream content) throws IOException {
                    try {
                        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                        ContentSigner shaSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate());
                        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build()
                        ).build(shaSigner, cert));
                        gen.addCertificates(new JcaCertStore(Collections.singletonList(cert)));

                        byte[] data = content.readAllBytes();
                        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(data), false);
                        return signedData.getEncoded();
                    } catch (Exception ex) {
                        throw new IOException("Signing failed", ex);
                    }
                }
            });

            try (FileOutputStream fos = new FileOutputStream(targetPdf)) {
                docToSign.saveIncremental(fos);
            }
        }

        assertTrue(targetPdf.exists() && targetPdf.length() > 0);
        assertTrue(targetCert.exists() && targetCert.length() > 0);
    }
}
