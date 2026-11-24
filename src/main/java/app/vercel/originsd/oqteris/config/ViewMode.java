package app.vercel.originsd.oqteris.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * PDF document display zoom and viewport fitting mode.
 */
public enum ViewMode {
    FIT_WIDTH("Fit to Width"),
    FIT_PAGE("Fit to Page"),
    ACTUAL_SIZE("Actual Size (100%)"),
    CUSTOM("Custom Zoom");

    private final String displayName;

    ViewMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static ViewMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return FIT_WIDTH;
        }
        String clean = value.trim().toUpperCase();
        if ("FIT_HEIGHT".equals(clean)) {
            return FIT_PAGE;
        }
        for (ViewMode mode : values()) {
            if (mode.name().equalsIgnoreCase(clean) || mode.displayName.equalsIgnoreCase(clean)) {
                return mode;
            }
        }
        return FIT_WIDTH;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
