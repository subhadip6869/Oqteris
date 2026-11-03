package org.originsd.oqteris.update;

import java.util.Locale;

/**
 * Operating system classification for platform-specific update package selection.
 */
public enum TargetPlatform {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN;

    public static TargetPlatform current() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return WINDOWS;
        if (os.contains("mac") || os.contains("darwin")) return MACOS;
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) return LINUX;
        return UNKNOWN;
    }

    public String getDisplayName() {
        return switch (this) {
            case WINDOWS -> "Windows";
            case MACOS -> "macOS";
            case LINUX -> "Linux";
            case UNKNOWN -> "Cross-Platform";
        };
    }
}
