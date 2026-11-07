package org.originsd.oqteris.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global application configuration and OS-specific user directory management
 * adhering to the OriginSD Desktop Application Master Architecture Standard.
 */
public final class AppConfig {

    public static final String APP_NAME = "Oqteris";
    public static final String APP_DIR_NAME = "Oqteris";
    public static final String PUBLISHER = "OriginSD";
    public static final String DEVELOPER = "Subhadip Dutta";
    public static final String LOCATION = "Bankura, West Bengal, India";
    public static final String WEBSITE_URL = "https://originsd.org";
    public static final String GITHUB_PROFILE = "https://github.com/subhadip6869";
    public static final String REPO_URL = "https://github.com/subhadip6869/Oqteris.git";
    public static final String RELEASES_API_URL = "https://api.github.com/repos/subhadip6869/Oqteris/releases/latest";
    public static final String DESCRIPTION = "Dedicated cross-platform desktop PDF reader and editor";
    public static final String COPYRIGHT = "© 2026 OriginSD";
    public static final String SETTINGS_FILENAME = "settings.json";

    private AppConfig() {}

    /**
     * Resolves the authoritative OS-specific application data directory:
     * - Windows: %LOCALAPPDATA%\OriginSD\Oqteris\
     * - macOS: ~/Library/Application Support/OriginSD/Oqteris/
     * - Linux: ~/.local/share/originsd/oqteris/
     */
    public static Path getAppDataDirectory() {
        String customDir = System.getProperty("oqteris.config.dir");
        if (customDir != null && !customDir.isBlank()) {
            Path p = Path.of(customDir).resolve("OriginSD").resolve(APP_DIR_NAME);
            try {
                Files.createDirectories(p);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to create user application directory: " + p, e);
            }
            return p;
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        Path basePath;
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            basePath = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(System.getProperty("user.home"), "AppData", "Local");
            basePath = basePath.resolve("OriginSD").resolve(APP_DIR_NAME);
        } else if (os.contains("mac")) {
            basePath = Path.of(System.getProperty("user.home"), "Library", "Application Support", "OriginSD", APP_DIR_NAME);
        } else {
            String xdgData = System.getenv("XDG_DATA_HOME");
            basePath = (xdgData != null && !xdgData.isBlank())
                    ? Path.of(xdgData, "originsd", APP_DIR_NAME.toLowerCase())
                    : Path.of(System.getProperty("user.home"), ".local", "share", "originsd", APP_DIR_NAME.toLowerCase());
        }

        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create user application directory: " + basePath, e);
        }
        return basePath;
    }

    public static Path getSettingsPath() {
        return getAppDataDirectory().resolve(SETTINGS_FILENAME);
    }

    public static Path getRecoveryDirectory() {
        Path dir = getAppDataDirectory().resolve("recovery");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create recovery directory: " + dir, e);
        }
        return dir;
    }

    public static Path getTrustStorePath() {
        return getAppDataDirectory().resolve("truststore.p12");
    }
}
