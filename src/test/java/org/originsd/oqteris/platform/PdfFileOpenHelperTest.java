package org.originsd.oqteris.platform;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class PdfFileOpenHelperTest {

    @Test
    public void testValidatePdfFileNullOrBlank() {
        assertNull(PdfFileOpenHelper.validatePdfFile(null));
        assertNull(PdfFileOpenHelper.validatePdfFile(""));
        assertNull(PdfFileOpenHelper.validatePdfFile("   "));
    }

    @Test
    public void testValidatePdfFileNonExistent() {
        assertNull(PdfFileOpenHelper.validatePdfFile("non_existent_file_12345.pdf"));
        assertNull(PdfFileOpenHelper.validatePdfFile("\"non_existent_quoted_file.pdf\""));
    }

    @Test
    public void testValidateExistingSamplePdf() {
        File samplePdf = new File("samples", "sample_signed.pdf");
        if (samplePdf.exists()) {
            File validated = PdfFileOpenHelper.validatePdfFile(samplePdf.getAbsolutePath());
            assertNotNull(validated);
            assertTrue(validated.exists());
            assertTrue(PdfFileOpenHelper.isPdfFile(validated));

            // Test with quoted path
            File quoted = PdfFileOpenHelper.validatePdfFile("\"" + samplePdf.getAbsolutePath() + "\"");
            assertNotNull(quoted);
            assertEquals(validated.getAbsolutePath(), quoted.getAbsolutePath());

            // Test with single quotes
            File singleQuoted = PdfFileOpenHelper.validatePdfFile("'" + samplePdf.getAbsolutePath() + "'");
            assertNotNull(singleQuoted);
            assertEquals(validated.getAbsolutePath(), singleQuoted.getAbsolutePath());
        }
    }

    @Test
    public void testIsPdfFileWithNonPdf() {
        File pom = new File("pom.xml");
        assertTrue(pom.exists());
        assertFalse(PdfFileOpenHelper.isPdfFile(pom));
    }
}
