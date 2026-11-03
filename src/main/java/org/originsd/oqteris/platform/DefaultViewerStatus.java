package org.originsd.oqteris.platform;

/**
 * Status of the application as the operating system's default PDF viewer.
 */
public enum DefaultViewerStatus {
    DEFAULT("Oqteris is currently the default PDF viewer"),
    NOT_DEFAULT("Oqteris is not currently the default PDF viewer"),
    UNKNOWN("Default viewer status could not be determined on this system");

    private final String description;

    DefaultViewerStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }
}
