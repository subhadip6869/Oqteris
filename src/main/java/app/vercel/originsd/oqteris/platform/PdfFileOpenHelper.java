package app.vercel.originsd.oqteris.platform;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates and normalizes PDF file paths passed from CLI, file managers, or drag-and-drop.
 * Fully supports international and non-English file names across all scripts and encodings.
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

        // Handle file URI schemes (e.g. file:///... or file:/...)
        if (cleaned.startsWith("file:///") || cleaned.startsWith("file:/")) {
            try {
                URI uri = URI.create(cleaned.replace(" ", "%20"));
                cleaned = new File(uri).getAbsolutePath();
            } catch (Exception ignored) {
                try {
                    String sub = cleaned.replaceFirst("^file:[/]+", "");
                    // On Windows, fix drive letter format if leading slash is stripped
                    if (sub.length() >= 2 && sub.charAt(1) == ':') {
                        // Already drive letter e.g. C:/...
                    } else if (sub.startsWith("/")) {
                        sub = sub.substring(1);
                    }
                    cleaned = URLDecoder.decode(sub, StandardCharsets.UTF_8);
                } catch (Exception ignored2) {
                }
            }
        }

        try {
            File file = new File(cleaned);
            // If file does not exist but contains % URL-encoded sequences, attempt UTF-8 decoding
            if (!file.exists() && cleaned.contains("%")) {
                try {
                    String decoded = URLDecoder.decode(cleaned, StandardCharsets.UTF_8);
                    File decodedFile = new File(decoded);
                    if (decodedFile.exists()) {
                        file = decodedFile;
                    }
                } catch (Exception ignored) {
                }
            }

            try {
                file = file.getCanonicalFile();
            } catch (Exception ignored) {
                file = file.getAbsoluteFile();
            }

            if (!file.exists() || !file.isFile() || !file.canRead()) {
                // If path contains '?', it is likely a Unicode path mangled by Windows ANSI code page (CP_ACP)
                if (cleaned.contains("?")) {
                    File resolved = resolveMangledPath(cleaned);
                    if (resolved != null && resolved.exists() && resolved.isFile() && resolved.canRead()) {
                        file = resolved;
                    } else {
                        System.err.println("[PdfFileOpenHelper] File does not exist or is unreadable: " + cleaned);
                        return null;
                    }
                } else {
                    System.err.println("[PdfFileOpenHelper] File does not exist or is unreadable: " + cleaned);
                    return null;
                }
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
     * Resolves a Windows command-line file path where non-ASCII Unicode characters
     * were mangled into '?' by the Windows ANSI code page (CP_ACP) or JVM launcher.
     * Matches the mangled pattern against actual files/directories on the filesystem.
     */
    public static File resolveMangledPath(String mangledPath) {
        if (mangledPath == null || !mangledPath.contains("?")) {
            return null;
        }

        File direct = new File(mangledPath);
        if (direct.exists()) {
            return direct;
        }

        String normalized = mangledPath.replace('/', '\\');
        int lastBackslash = normalized.lastIndexOf('\\');
        if (lastBackslash == -1) {
            return findMatchInDir(new File("."), normalized);
        }

        String parentPath = normalized.substring(0, lastBackslash);
        String fileNamePattern = normalized.substring(lastBackslash + 1);

        File parentDir = new File(parentPath);
        if (!parentDir.exists() && parentPath.contains("?")) {
            parentDir = resolveMangledDirectory(parentPath);
        }

        if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
            return findMatchInDir(parentDir, fileNamePattern);
        }

        return null;
    }

    private static File resolveMangledDirectory(String mangledDirPath) {
        int lastBackslash = mangledDirPath.lastIndexOf('\\');
        if (lastBackslash == -1) {
            return null;
        }
        String grandParentPath = mangledDirPath.substring(0, lastBackslash);
        String dirPattern = mangledDirPath.substring(lastBackslash + 1);

        File grandParent = new File(grandParentPath);
        if (!grandParent.exists() && grandParentPath.contains("?")) {
            grandParent = resolveMangledDirectory(grandParentPath);
        }

        if (grandParent != null && grandParent.exists() && grandParent.isDirectory()) {
            File[] subs = grandParent.listFiles(File::isDirectory);
            if (subs == null) return null;
            List<File> matches = new ArrayList<>();
            for (File sub : subs) {
                if (matchesPattern(sub.getName(), dirPattern)) {
                    matches.add(sub);
                }
            }
            if (matches.size() == 1) return matches.get(0);
            if (matches.size() > 1) {
                matches.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                return matches.get(0);
            }
        }
        return null;
    }

    private static File findMatchInDir(File dir, String filePattern) {
        File[] files = dir.listFiles(File::isFile);
        if (files == null || files.length == 0) return null;

        List<File> matches = new ArrayList<>();
        for (File f : files) {
            if (matchesPattern(f.getName(), filePattern)) {
                matches.add(f);
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            matches.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            return matches.get(0);
        }
        return null;
    }

    private static boolean matchesPattern(String actual, String pattern) {
        int aIdx = 0;
        int pIdx = 0;
        int aLen = actual.length();
        int pLen = pattern.length();

        while (aIdx < aLen && pIdx < pLen) {
            char p = pattern.charAt(pIdx);
            char a = actual.charAt(aIdx);

            if (p == '?') {
                if (a < 128) {
                    return false;
                }
                if (Character.isHighSurrogate(a) && aIdx + 1 < aLen && Character.isLowSurrogate(actual.charAt(aIdx + 1))) {
                    aIdx++;
                }
                aIdx++;
                pIdx++;
            } else {
                if (Character.toLowerCase(a) != Character.toLowerCase(p)) {
                    return false;
                }
                aIdx++;
                pIdx++;
            }
        }

        return aIdx == aLen && pIdx == pLen;
    }

    /**
     * Checks whether a file is a PDF either by .pdf extension or %PDF- header magic bytes.
     */
    public static boolean isPdfFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
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
