package org.originsd.oqteris.platform;

/**
 * Fallback no-op implementation of DefaultViewerService for unsupported platforms.
 */
public class NoopDefaultViewerService implements DefaultViewerService {

    @Override
    public DefaultViewerStatus checkStatus() {
        return DefaultViewerStatus.UNKNOWN;
    }

    @Override
    public RegistrationResult registerAsDefault() {
        return RegistrationResult.unsupported("Default viewer registration is not supported on this platform.");
    }

    @Override
    public boolean canDetectDefault() {
        return false;
    }

    @Override
    public String getPlatformName() {
        return "Unknown";
    }
}
