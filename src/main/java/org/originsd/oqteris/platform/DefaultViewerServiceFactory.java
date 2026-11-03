package org.originsd.oqteris.platform;

/**
 * Factory providing the platform-appropriate DefaultViewerService.
 */
public final class DefaultViewerServiceFactory {
    private static DefaultViewerService instance;

    private DefaultViewerServiceFactory() {}

    public static synchronized DefaultViewerService getInstance() {
        if (instance == null) {
            instance = createServiceForCurrentPlatform();
        }
        return instance;
    }

    public static synchronized void setInstance(DefaultViewerService customService) {
        instance = customService;
    }

    private static DefaultViewerService createServiceForCurrentPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return new WindowsDefaultViewerService();
        } else if (osName.contains("linux") || osName.contains("unix")) {
            return new LinuxDefaultViewerService();
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return new MacDefaultViewerService();
        }
        return new NoopDefaultViewerService();
    }
}
