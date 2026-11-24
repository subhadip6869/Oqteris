package app.vercel.originsd.oqteris.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Document canvas interaction and cursor mode.
 */
public enum CursorMode {
    SELECT("Select Tool"),
    HAND("Hand (Pan) Tool");

    private final String displayName;

    CursorMode(String displayName) {
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
    public static CursorMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return SELECT;
        }
        String clean = value.trim().toUpperCase();
        for (CursorMode mode : values()) {
            if (mode.name().equalsIgnoreCase(clean) || mode.displayName.equalsIgnoreCase(clean)) {
                return mode;
            }
        }
        return SELECT;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
