package org.originsd.oqteris.core.render;

import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages font discovery, cross-platform substitution for Microsoft Office fonts,
 * and text measurement caching.
 */
public class FontManager {
    static {
        if (System.getProperty("javafx.cachedir") == null) {
            String tempDir = System.getProperty("java.io.tmpdir", ".");
            System.setProperty("javafx.cachedir", new java.io.File(tempDir, ".openjfx-cache").getAbsolutePath());
        }
    }

    private static final FontManager INSTANCE = new FontManager();

    private final Set<String> installedFamilies = new HashSet<>();
    private final Map<String, String> substitutions = new HashMap<>();
    private final Map<String, Font> fontCache = new ConcurrentHashMap<>();

    // Measurement helper reusing a Text node
    private final Text measurementText = new Text();
    private boolean toolkitWorking = true;

    private FontManager() {
        ensureInstalledFamiliesLoaded();
    }

    public static FontManager getInstance() {
        return INSTANCE;
    }

    private synchronized void ensureInstalledFamiliesLoaded() {
        if (installedFamilies.isEmpty()) {
            try {
                installedFamilies.addAll(Font.getFamilies());
            } catch (Exception ignored) {
            }
            initSubstitutions();
        }
    }

    private void initSubstitutions() {
        // Map common MS Office fonts to available Linux/system fonts if not installed
        registerSub("Calibri", "Segoe UI", "DejaVu Sans", "Liberation Sans", "Arial");
        registerSub("Calibri Light", "Segoe UI Light", "DejaVu Sans", "Liberation Sans", "Arial");
        registerSub("Aptos", "Calibri", "Segoe UI", "Liberation Sans", "Arial");
        registerSub("Cambria", "Georgia", "DejaVu Serif", "Liberation Serif", "Times New Roman");
        registerSub("Consolas", "Cascadia Code", "Courier New", "Liberation Mono", "DejaVu Sans Mono");
        registerSub("Times New Roman", "Liberation Serif", "DejaVu Serif", "Times", "Georgia");
        registerSub("Arial", "Liberation Sans", "DejaVu Sans", "Helvetica");
        registerSub("Arial Narrow", "Liberation Sans Narrow", "Arial", "Liberation Sans");
        registerSub("Aptos Display", "Calibri", "Segoe UI", "Arial");
        registerSub("Aptos Narrow", "Calibri", "Segoe UI", "Arial");
        registerSub("Helvetica", "Arial", "Liberation Sans");
        registerSub("Garamond", "Times New Roman", "Liberation Serif", "Georgia");
        registerSub("Book Antiqua", "Palatino", "Georgia", "Times New Roman");
        registerSub("Palatino Linotype", "Palatino", "Georgia", "Times New Roman");
        registerSub("Comic Sans MS", "Comic Sans MS");
        registerSub("Trebuchet MS", "Trebuchet MS", "Verdana");
        registerSub("Century Gothic", "Futura", "Century Gothic", "Verdana");
        registerSub("Courier", "Courier New", "Liberation Mono");
    }

    private void registerSub(String primary, String... fallbacks) {
        if (!installedFamilies.contains(primary)) {
            for (String fallback : fallbacks) {
                if (installedFamilies.contains(fallback)) {
                    substitutions.put(primary.toLowerCase(Locale.ROOT), fallback);
                    return;
                }
            }
        }
    }

    /**
     * Resolves a font family name, substituting an available font if not installed.
     */
    public String resolveFamily(String requestedFamily) {
        if (requestedFamily == null || requestedFamily.isBlank()) {
            return getDefaultOfficeFont();
        }
        ensureInstalledFamiliesLoaded();
        if (installedFamilies.contains(requestedFamily)) {
            return requestedFamily;
        }
        String sub = substitutions.get(requestedFamily.toLowerCase(Locale.ROOT));
        if (sub != null) {
            return sub;
        }
        // Case-insensitive search
        String reqLower = requestedFamily.toLowerCase(Locale.ROOT);
        for (String family : installedFamilies) {
            if (family.toLowerCase(Locale.ROOT).equals(reqLower)) {
                return family;
            }
        }

        // Check heuristic fallbacks for common font categories
        if (reqLower.contains("calibri") || reqLower.contains("aptos") || reqLower.contains("sans")) {
            for (String f : List.of("Calibri", "Segoe UI", "Liberation Sans", "Arial", "DejaVu Sans")) {
                if (installedFamilies.contains(f)) return f;
            }
        } else if (reqLower.contains("times") || reqLower.contains("cambria") || reqLower.contains("serif") || reqLower.contains("roman")) {
            for (String f : List.of("Times New Roman", "Liberation Serif", "Georgia", "DejaVu Serif")) {
                if (installedFamilies.contains(f)) return f;
            }
        } else if (reqLower.contains("consolas") || reqLower.contains("courier") || reqLower.contains("mono")) {
            for (String f : List.of("Consolas", "Courier New", "Cascadia Code", "Liberation Mono")) {
                if (installedFamilies.contains(f)) return f;
            }
        }

        return getDefaultOfficeFont();
    }

    private String getDefaultOfficeFont() {
        ensureInstalledFamiliesLoaded();
        for (String f : List.of("Calibri", "Segoe UI", "Liberation Sans", "Arial")) {
            if (installedFamilies.contains(f)) return f;
        }
        return "System";
    }

    /**
     * Returns a cached JavaFX Font with the given family, size, weight, and posture.
     */
    public Font getFont(String family, double size, boolean bold, boolean italic) {
        String resolvedFamily = resolveFamily(family);
        double validSize = size > 0 ? size : 12.0;
        FontWeight weight = bold ? FontWeight.BOLD : FontWeight.NORMAL;
        FontPosture posture = italic ? FontPosture.ITALIC : FontPosture.REGULAR;
        String key = resolvedFamily + ":" + validSize + ":" + weight.name() + ":" + posture.name();

        return fontCache.computeIfAbsent(key, k -> Font.font(resolvedFamily, weight, posture, validSize));
    }

    /**
     * Measures the rendered advance width of a string in the specified font.
     */
    public synchronized double measureWidth(String text, Font font) {
        if (text == null || text.isEmpty()) return 0.0;
        if (toolkitWorking) {
            try {
                measurementText.setFont(font);
                measurementText.setText(text);
                return measurementText.getLayoutBounds().getWidth();
            } catch (Throwable t) {
                toolkitWorking = false;
            }
        }
        double size = font != null ? font.getSize() : 12.0;
        return text.length() * (size * 0.54);
    }

    /**
     * Measures the rendered height of a line of text in the specified font.
     */
    public synchronized double measureHeight(Font font) {
        if (toolkitWorking) {
            try {
                measurementText.setFont(font);
                measurementText.setText("Mg");
                return measurementText.getLayoutBounds().getHeight();
            } catch (Throwable t) {
                toolkitWorking = false;
            }
        }
        double size = font != null ? font.getSize() : 12.0;
        return size * 1.25;
    }
}
