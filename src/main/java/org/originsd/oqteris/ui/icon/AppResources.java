package org.originsd.oqteris.ui.icon;

import javafx.scene.image.Image;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Centralized resource manager for application branding and file association icons.
 * Provides access to:
 * - Icon A: Oqteris application logo (oqteris-logo.png / oqteris-logo.ico / oqteris-app-icon)
 * - Icon B: PDF document association icon (oqteris-document.png / oqteris-document.ico / oqteris-pdf-file-icon)
 */
public final class AppResources {

    public static final String APP_LOGO_PNG = "/icons/oqteris-logo.png";
    public static final String APP_LOGO_ICO = "/icons/oqteris-logo.ico";
    public static final String PDF_DOCUMENT_PNG = "/icons/oqteris-document.png";
    public static final String PDF_DOCUMENT_ICO = "/icons/oqteris-document.ico";

    private AppResources() {}

    public static InputStream getAppLogoStream() {
        InputStream in = AppResources.class.getResourceAsStream(APP_LOGO_PNG);
        if (in == null) {
            in = AppResources.class.getResourceAsStream("/icons/oqteris-app-icon.png");
        }
        return in;
    }

    public static InputStream getPdfDocumentIconStream() {
        InputStream in = AppResources.class.getResourceAsStream(PDF_DOCUMENT_PNG);
        if (in == null) {
            in = AppResources.class.getResourceAsStream("/icons/oqteris-pdf-file-icon.png");
        }
        return in;
    }

    public static Image getAppLogo() {
        try (InputStream in = getAppLogoStream()) {
            if (in != null) {
                return new Image(in);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Image getAppLogo(double width, double height) {
        try (InputStream in = getAppLogoStream()) {
            if (in != null) {
                return new Image(in, width, height, true, true);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Image getPdfDocumentIcon() {
        try (InputStream in = getPdfDocumentIconStream()) {
            if (in != null) {
                return new Image(in);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Image getPdfDocumentIcon(double width, double height) {
        try (InputStream in = getPdfDocumentIconStream()) {
            if (in != null) {
                return new Image(in, width, height, true, true);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Extracts an icon resource to a file on disk (for OS registry / desktop integration).
     */
    public static File extractResourceToDisk(String resourcePath, File targetFile) throws IOException {
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile;
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream in = AppResources.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile;
    }
}
