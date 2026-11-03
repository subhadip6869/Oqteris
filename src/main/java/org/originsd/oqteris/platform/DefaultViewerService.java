package org.originsd.oqteris.platform;

/**
 * Service abstraction for detecting and registering Oqteris as the default PDF viewer.
 */
public interface DefaultViewerService {

    /**
     * Checks whether Oqteris is currently registered as the operating system's default PDF viewer.
     */
    DefaultViewerStatus checkStatus();

    /**
     * Registers Oqteris with the operating system as a PDF viewer and guides the user or sets it as default.
     */
    RegistrationResult registerAsDefault();

    /**
     * Whether default detection is reliably supported on this operating system.
     */
    boolean canDetectDefault();

    /**
     * Returns the user-friendly name of the platform (e.g. "Windows", "Linux", "macOS").
     */
    String getPlatformName();
}
