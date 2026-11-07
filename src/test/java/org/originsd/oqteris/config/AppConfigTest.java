package org.originsd.oqteris.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AppConfigTest {

    @Test
    public void testAppDataDirectoryResolution() {
        Path appDataDir = AppConfig.getAppDataDirectory();
        assertNotNull(appDataDir);
        assertTrue(Files.exists(appDataDir));
        assertTrue(appDataDir.toString().contains("OriginSD") || appDataDir.toString().contains("originsd"));
        assertTrue(appDataDir.toString().contains("Oqteris") || appDataDir.toString().contains("oqteris"));
    }

    @Test
    public void testSettingsPersistence(@TempDir Path tempDir) {
        Path customSettingsPath = tempDir.resolve("test-settings.json");
        UserSettingsService service = new UserSettingsService(customSettingsPath);

        // 1. Initial settings should be created with defaults
        UserSettings settings = service.getSettings();
        assertNotNull(settings);
        assertEquals("SYSTEM", settings.getTheme());
        assertEquals(0, settings.getFontSizeDelta());
        assertTrue(settings.isAutosaveEnabled());
        assertEquals(2, settings.getAutosaveIntervalMinutes());
        assertTrue(Files.exists(customSettingsPath));

        // 2. Modify settings and persist
        settings.setTheme("DARK");
        settings.setFontSizeDelta(2);
        settings.setDefaultAuthor("Test Author");
        service.save(settings);

        // 3. Reload in a new service instance to verify persistence
        UserSettingsService reloadedService = new UserSettingsService(customSettingsPath);
        UserSettings reloaded = reloadedService.getSettings();
        assertEquals("DARK", reloaded.getTheme());
        assertEquals(2, reloaded.getFontSizeDelta());
        assertEquals("Test Author", reloaded.getDefaultAuthor());
    }

    @Test
    public void testPublisherIdentityConstants() {
        assertEquals("OriginSD", AppConfig.PUBLISHER);
        assertEquals("Subhadip Dutta", AppConfig.DEVELOPER);
        assertEquals("Bankura, West Bengal, India", AppConfig.LOCATION);
        assertEquals("https://originsd.org", AppConfig.WEBSITE_URL);
        assertEquals("https://github.com/subhadip6869", AppConfig.GITHUB_PROFILE);
        assertEquals("https://github.com/subhadip6869/Oqteris.git", AppConfig.REPO_URL);
    }
}
