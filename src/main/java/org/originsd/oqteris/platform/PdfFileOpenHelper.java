package org.originsd.oqteris.platform;

import java.io.File;
import java.io.FileInputStream;

/**
 * Validates and normalizes PDF file paths passed from CLI, file managers, or drag-and-drop.
 */
public final class PdfFileOpenHelper {

    private PdfFileOpenHelper() {}

    /**
     * Cleans and validates a path string. Returns a readable PDF File, or null if invalid.
     */
    public static File validatePdfFile(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String cleaned = rawPath.trim();
        // Remove enclosing quotes if passed by OS / command line
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            if (cleaned.length() >= 2) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
        }

        try {
            File file = new File(cleaned).getCanonicalFile();
            if (!file.exists() || !file.isFile() || !file.canRead()) {
                System.err.println("[PdfFileOpenHelper] File does not exist or is unreadable: " + cleaned);
                return null;
            }

            if (!isPdfFile(file)) {
                System.err.println("[PdfFileOpenHelper] File is not a recognized PDF: " + cleaned);
                return null;
            }

            return file;
        } catch (Exception e) {
            System.err.println("[PdfFileOpenHelper] Error resolving file path: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether a file is a PDF either by .pdf extension or %PDF- header magic bytes.
     */
    public static boolean isPdfFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        if (file.getName().toLowerCase().endsWith(".pdf")) {
            return true;
        }

        // Magic bytes inspection
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[5];
            int read = in.read(header);
            if (read == 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-') {
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }
}
