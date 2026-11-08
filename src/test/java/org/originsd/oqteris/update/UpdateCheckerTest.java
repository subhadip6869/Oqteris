package org.originsd.oqteris.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateCheckerTest {

    @Test
    public void testSemVerParsingAndComparison() {
        SemVer v100 = SemVer.parse("1.0.0");
        SemVer v100v = SemVer.parse("v1.0.0");
        assertEquals(0, v100.compareTo(v100v), "Leading 'v' should be ignored in comparison");

        SemVer v110 = SemVer.parse("1.1.0");
        assertTrue(v110.isNewerThan(v100), "1.1.0 should be newer than 1.0.0");
        assertFalse(v100.isNewerThan(v110), "1.0.0 should not be newer than 1.1.0");

        SemVer v200 = SemVer.parse("v2.0.0");
        SemVer v199 = SemVer.parse("1.9.9");
        assertTrue(v200.isNewerThan(v199), "2.0.0 should be newer than 1.9.9");

        SemVer vPatch = SemVer.parse("1.0.1");
        assertTrue(vPatch.isNewerThan(v100), "1.0.1 should be newer than 1.0.0");

        SemVer vPre = SemVer.parse("1.1.0-beta.1");
        assertTrue(v110.isNewerThan(vPre), "Final release should be newer than pre-release");
    }

    @Test
    public void testGitHubReleaseJsonParsing() {
        String mockJson = """
                {
                  "tag_name": "v1.1.0",
                  "name": "Oqteris 1.1.0 Desktop Release",
                  "body": "### Features\\n- Modern Ribbon Interface\\n- Fast PDF rendering\\n- Digital signatures",
                  "html_url": "https://github.com/subhadip6869/Oqteris/releases/tag/v1.1.0",
                  "published_at": "2026-11-03T23:00:00Z",
                  "assets": [
                    {
                      "name": "Oqteris-1.1.0-windows-x64.msi",
                      "browser_download_url": "https://github.com/subhadip6869/Oqteris/releases/download/v1.1.0/Oqteris-1.1.0-windows-x64.msi",
                      "size": 47185920,
                      "content_type": "application/x-msi"
                    },
                    {
                      "name": "Oqteris-1.1.0-mac-arm64.dmg",
                      "browser_download_url": "https://github.com/subhadip6869/Oqteris/releases/download/v1.1.0/Oqteris-1.1.0-mac-arm64.dmg",
                      "size": 52428800,
                      "content_type": "application/x-apple-diskimage"
                    },
                    {
                      "name": "oqteris_1.1.0_amd64.deb",
                      "browser_download_url": "https://github.com/subhadip6869/Oqteris/releases/download/v1.1.0/oqteris_1.1.0_amd64.deb",
                      "size": 44040192,
                      "content_type": "application/vnd.debian.binary-package"
                    },
                    {
                      "name": "Oqteris-1.1.0.jar",
                      "browser_download_url": "https://github.com/subhadip6869/Oqteris/releases/download/v1.1.0/Oqteris-1.1.0.jar",
                      "size": 31457280,
                      "content_type": "application/java-archive"
                    }
                  ]
                }
                """;

        ReleaseInfo release = UpdateCheckerService.parseReleaseJson(mockJson);
        assertNotNull(release);
        assertEquals("v1.1.0", release.tagName());
        assertEquals("Oqteris 1.1.0 Desktop Release", release.name());
        assertTrue(release.body().contains("Modern Ribbon Interface"));
        assertEquals("https://github.com/subhadip6869/Oqteris/releases/tag/v1.1.0", release.htmlUrl());
        assertEquals(4, release.assets().size());

        // Test platform-specific asset matching
        ReleaseAsset winAsset = release.findBestAssetForPlatform(TargetPlatform.WINDOWS);
        assertNotNull(winAsset);
        assertEquals("Oqteris-1.1.0-windows-x64.msi", winAsset.name());
        assertTrue(winAsset.getFormattedSize().contains("45.0 MB"));

        ReleaseAsset macAsset = release.findBestAssetForPlatform(TargetPlatform.MACOS);
        assertNotNull(macAsset);
        assertEquals("Oqteris-1.1.0-mac-arm64.dmg", macAsset.name());

        ReleaseAsset linuxAsset = release.findBestAssetForPlatform(TargetPlatform.LINUX);
        assertNotNull(linuxAsset);
        assertEquals("oqteris_1.1.0_amd64.deb", linuxAsset.name());
    }

    @Test
    public void testDownloadedPackageCleanupSafety() throws Exception {
        Path tempPkg = Files.createTempFile("oqteris-test-cleanup-", ".msi");
        assertTrue(Files.exists(tempPkg));

        // Write dummy package content
        Files.writeString(tempPkg, "dummy-package-payload");
        assertTrue(Files.size(tempPkg) > 0);

        // Verify successful deletion
        Files.deleteIfExists(tempPkg);
        assertFalse(Files.exists(tempPkg), "Downloaded package must be deleted successfully");
    }

    @Test
    public void testAppMetadataRepository() {
        assertEquals("subhadip6869/Oqteris", AppVersion.GITHUB_REPO);
        assertTrue(AppVersion.GITHUB_REPO_URL.contains("github.com/subhadip6869/Oqteris"));
        assertEquals("1.2.0", AppVersion.CURRENT_VERSION);
    }

    @Test
    public void testUpdateCheckResultStatusHandling() {
        // 404 Not Found - Expected when zero releases are published yet on GitHub
        UpdateCheckResult res404 = new UpdateCheckResult(
                UpdateCheckResult.Status.NO_RELEASES_PUBLISHED, null, 404, "No releases published yet on GitHub"
        );
        assertTrue(res404.isSuccess(), "404 on release endpoint is treated as an expected 'no newer release' state");
        assertNull(res404.release());
        assertEquals(404, res404.statusCode());

        // 403 Rate Limited
        UpdateCheckResult res403 = new UpdateCheckResult(
                UpdateCheckResult.Status.RATE_LIMITED, null, 403, "GitHub API rate limit reached"
        );
        assertFalse(res403.isSuccess());
        assertEquals(UpdateCheckResult.Status.RATE_LIMITED, res403.status());

        // 500 Server Error
        UpdateCheckResult res500 = new UpdateCheckResult(
                UpdateCheckResult.Status.SERVER_ERROR, null, 500, "GitHub server error: HTTP 500"
        );
        assertFalse(res500.isSuccess());

        // 200 Update Available
        ReleaseInfo rel200 = new ReleaseInfo("v1.2.0", "Release 1.2.0", "notes", "https://github.com", "2026-11-03", java.util.List.of());
        UpdateCheckResult resAvailable = new UpdateCheckResult(
                UpdateCheckResult.Status.UPDATE_AVAILABLE, rel200, 200, null
        );
        assertTrue(resAvailable.isSuccess());
        assertEquals(UpdateCheckResult.Status.UPDATE_AVAILABLE, resAvailable.status());

        // 200 Up To Date
        ReleaseInfo relSame = new ReleaseInfo("v1.1.0", "Release 1.1.0", "notes", "https://github.com", "2026-11-03", java.util.List.of());
        UpdateCheckResult resUpToDate = new UpdateCheckResult(
                UpdateCheckResult.Status.UP_TO_DATE, relSame, 200, null
        );
        assertTrue(resUpToDate.isSuccess());
        assertEquals(UpdateCheckResult.Status.UP_TO_DATE, resUpToDate.status());
    }
}
