package app.vercel.originsd.oqteris.platform;

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

    @Test
    public void testValidateNonEnglishPdfFiles() throws Exception {
        File samplePdf = new File("samples", "sample_signed.pdf");
        if (!samplePdf.exists()) return;

        File targetDir = new File("target", "test-unicode-open");
        targetDir.mkdirs();

        String[] testNames = new String[]{
                "বাংলা_নথি_টেস্ট.pdf",
                "日本語_ドキュメント.pdf",
                "тестовый_документ_кириллица.pdf",
                "rapport_financier_café_naïve.pdf",
                "ملف_اختبار_عربي.pdf",
                "हिन्दी_दस्तावेज.pdf"
        };

        for (String name : testNames) {
            File dest = new File(targetDir, name);
            java.nio.file.Files.copy(samplePdf.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            assertTrue(dest.exists());

            File validated = PdfFileOpenHelper.validatePdfFile(dest.getAbsolutePath());
            assertNotNull(validated, "Should validate non-English PDF: " + name);
            assertTrue(validated.exists());
            assertTrue(PdfFileOpenHelper.isPdfFile(validated));

            // Quoted
            File quoted = PdfFileOpenHelper.validatePdfFile("\"" + dest.getAbsolutePath() + "\"");
            assertNotNull(quoted);
            assertEquals(validated.getAbsolutePath(), quoted.getAbsolutePath());

            // URI format
            String uriStr = dest.toURI().toString();
            File fromUri = PdfFileOpenHelper.validatePdfFile(uriStr);
            assertNotNull(fromUri, "Should validate file URI: " + uriStr);
            assertEquals(validated.getAbsolutePath(), fromUri.getAbsolutePath());

            // Cleanup
            dest.delete();
        }
    }

    @Test
    public void testResolveMangledWindowsCliArguments() throws Exception {
        File samplePdf = new File("samples", "sample_signed.pdf");
        if (!samplePdf.exists()) return;

        File targetDir = new File("target", "test-mangled-resolve");
        targetDir.mkdirs();

        String[] testNames = new String[]{
                "বাংলা_নথি.pdf",
                "日本語_書類.pdf",
                "документ_отчёт.pdf",
                "contrat_français.pdf"
        };

        for (String name : testNames) {
            File dest = new File(targetDir, name);
            java.nio.file.Files.copy(samplePdf.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            assertTrue(dest.exists());

            // Simulate Windows CP_ACP downsampling where non-ASCII characters become '?'
            StringBuilder mangledSb = new StringBuilder();
            for (char c : dest.getAbsolutePath().toCharArray()) {
                mangledSb.append(c < 128 ? c : '?');
            }
            String mangledCliArg = mangledSb.toString();
            assertTrue(mangledCliArg.contains("?"), "Mangled arg must contain ?");

            // validatePdfFile should automatically resolve the mangled '?' path back to the real file on disk
            File resolved = PdfFileOpenHelper.validatePdfFile(mangledCliArg);
            assertNotNull(resolved, "validatePdfFile should successfully resolve mangled CLI argument for: " + name);
            assertEquals(dest.getCanonicalPath(), resolved.getCanonicalPath());
            assertTrue(resolved.exists());
            assertTrue(PdfFileOpenHelper.isPdfFile(resolved));

            dest.delete();
        }
    }
}
