package app.vercel.originsd.oqteris.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * PDF document page layout and vertical arrangement mode.
 */
public enum PageLayoutMode {
    CONTINUOUS("Continuous Vertical"),
    SINGLE_PAGE("Single Page"),
    FACING("Facing Pages (Two-Up)"),
    FACING_CONTINUOUS("Facing Continuous");

    private final String displayName;

    PageLayoutMode(String displayName) {
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
    public static PageLayoutMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return CONTINUOUS;
        }
        String clean = value.trim().toUpperCase();
        for (PageLayoutMode mode : values()) {
            if (mode.name().equalsIgnoreCase(clean) || mode.displayName.equalsIgnoreCase(clean)) {
                return mode;
            }
        }
        return CONTINUOUS;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
