package org.originsd.oqteris.update;

import java.util.Locale;

/**
 * Metadata for an installable release asset associated with a Git release.
 */
public record ReleaseAsset(
        String name,
        String downloadUrl,
        long sizeBytes,
        String contentType
) {
    public String getFormattedSize() {
        if (sizeBytes <= 0) return "";
        if (sizeBytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
