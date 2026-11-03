package org.originsd.oqteris.update;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parsed Git release payload containing tag, changelog notes, and distribution assets.
 */
public record ReleaseInfo(
        String tagName,
        String name,
        String body,
        String htmlUrl,
        String publishedAt,
        List<ReleaseAsset> assets
) {
    public ReleaseInfo {
        if (assets == null) {
            assets = Collections.emptyList();
        } else {
            assets = Collections.unmodifiableList(assets);
        }
    }

    public SemVer getSemVer() {
        return SemVer.parse(tagName);
    }

    /**
     * Finds the best matching installable asset for the specified platform.
     */
    public ReleaseAsset findBestAssetForPlatform(TargetPlatform platform) {
        if (assets.isEmpty()) return null;

        return switch (platform) {
            case WINDOWS -> findFirstMatching(
                    ".msi",
                    ".exe",
                    "windows-x64.zip",
                    "win64.zip",
                    "windows.zip",
                    "-win.zip",
                    ".zip",
                    ".jar"
            );
            case MACOS -> findFirstMatching(
                    ".dmg",
                    ".pkg",
                    "-mac.zip",
                    "-osx.zip",
                    "macos.zip",
                    ".zip",
                    ".jar"
            );
            case LINUX -> findFirstMatching(
                    ".deb",
                    ".AppImage",
                    ".rpm",
                    "-linux.tar.gz",
                    ".tar.gz",
                    ".zip",
                    ".jar"
            );
            case UNKNOWN -> findFirstMatching(".zip", ".jar");
        };
    }

    private ReleaseAsset findFirstMatching(String... patterns) {
        for (String pattern : patterns) {
            String lowerPattern = pattern.toLowerCase(Locale.ROOT);
            for (ReleaseAsset asset : assets) {
                if (asset.name() != null && asset.name().toLowerCase(Locale.ROOT).endsWith(lowerPattern)) {
                    return asset;
                }
            }
        }
        // If no extension matches, check substring
        for (String pattern : patterns) {
            String lowerPattern = pattern.toLowerCase(Locale.ROOT);
            for (ReleaseAsset asset : assets) {
                if (asset.name() != null && asset.name().toLowerCase(Locale.ROOT).contains(lowerPattern)) {
                    return asset;
                }
            }
        }
        return assets.get(0);
    }
}
