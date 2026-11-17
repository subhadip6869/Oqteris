package app.vercel.originsd.oqteris.ui.icon;

import javafx.scene.image.Image;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Centralized resource manager for application branding and file association icons.
 * Standardized icon sets:
 * - Application Icon: /icons/oqteris-app-icon.png and /icons/oqteris-app-icon.ico
 * - PDF Document Icon: /icons/oqteris-pdf-file-icon.png and /icons/oqteris-pdf-file-icon.ico
 */
public final class AppResources {

    public static final String APP_LOGO_PNG = "/icons/oqteris-app-icon.png";
    public static final String APP_LOGO_ICO = "/icons/oqteris-app-icon.ico";
    public static final String PDF_DOCUMENT_PNG = "/icons/oqteris-pdf-file-icon.png";
    public static final String PDF_DOCUMENT_ICO = "/icons/oqteris-pdf-file-icon.ico";

    private AppResources() {}

    public static InputStream getAppLogoStream() {
        return AppResources.class.getResourceAsStream(APP_LOGO_PNG);
    }

    public static InputStream getPdfDocumentIconStream() {
        return AppResources.class.getResourceAsStream(PDF_DOCUMENT_PNG);
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
