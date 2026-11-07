package org.originsd.oqteris.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultViewerServiceTest {

    @Test
    public void testFactoryReturnsAppropriateService() {
        DefaultViewerService service = DefaultViewerServiceFactory.getInstance();
        assertNotNull(service);
        assertNotNull(service.getPlatformName());
        assertFalse(service.getPlatformName().isBlank());

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            assertInstanceOf(WindowsDefaultViewerService.class, service);
            assertEquals("Windows", service.getPlatformName());
            assertTrue(service.canDetectDefault());
        } else if (os.contains("linux") || os.contains("unix")) {
            assertInstanceOf(LinuxDefaultViewerService.class, service);
            assertEquals("Linux", service.getPlatformName());
        } else if (os.contains("mac") || os.contains("darwin")) {
            assertInstanceOf(MacDefaultViewerService.class, service);
            assertEquals("macOS", service.getPlatformName());
        }
    }

    @Test
    public void testWindowsDefaultViewerServiceCommands() {
        WindowsDefaultViewerService winService = new WindowsDefaultViewerService();
        assertEquals("Windows", winService.getPlatformName());
        assertTrue(winService.canDetectDefault());

        String cmd = winService.buildLaunchCommand();
        assertNotNull(cmd);
        assertTrue(cmd.contains("java") || cmd.contains("javaw"));
        assertTrue(cmd.contains("%1"));

        String effectiveCmd = winService.getEffectiveLaunchCommand("C:\\Program Files\\Oqteris\\Oqteris.exe");
        assertNotNull(effectiveCmd);
        assertTrue(effectiveCmd.contains("Oqteris.exe"));
        assertTrue(effectiveCmd.contains("%1"));

        // Status check shouldn't throw exceptions
        DefaultViewerStatus status = winService.checkStatus();
        assertNotNull(status);
    }

    @Test
    public void testWindowsRegisterAsDefault() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            WindowsDefaultViewerService winService = new WindowsDefaultViewerService();
            RegistrationResult res = winService.registerAsDefault();
            assertNotNull(res);
            assertNotEquals(RegistrationResult.Outcome.ERROR, res.getOutcome());
        }
    }

    @Test
    public void testLinuxDefaultViewerService() {
        LinuxDefaultViewerService linuxService = new LinuxDefaultViewerService();
        assertEquals("Linux", linuxService.getPlatformName());
        DefaultViewerStatus status = linuxService.checkStatus();
        assertNotNull(status);
    }

    @Test
    public void testMacDefaultViewerService() {
        MacDefaultViewerService macService = new MacDefaultViewerService();
        assertEquals("macOS", macService.getPlatformName());
        DefaultViewerStatus status = macService.checkStatus();
        assertNotNull(status);
    }

    @Test
    public void testNoopDefaultViewerService() {
        NoopDefaultViewerService noop = new NoopDefaultViewerService();
        assertEquals("Unknown", noop.getPlatformName());
        assertFalse(noop.canDetectDefault());
        assertEquals(DefaultViewerStatus.UNKNOWN, noop.checkStatus());
        RegistrationResult res = noop.registerAsDefault();
        assertEquals(RegistrationResult.Outcome.UNSUPPORTED, res.getOutcome());
    }
}
