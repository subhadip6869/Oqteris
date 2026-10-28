package org.oqteris.core.render;

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
        try {
            installedFamilies.addAll(Font.getFamilies());
        } catch (Exception ignored) {
        }
        initSubstitutions();
    }

    public static FontManager getInstance() {
        return INSTANCE;
    }

    private void initSubstitutions() {
        // Map common MS Office fonts to available Linux/system fonts if not installed
        registerSub("Calibri", "Segoe UI", "DejaVu Sans", "Liberation Sans", "Arial", "System");
        registerSub("Calibri Light", "Segoe UI Light", "DejaVu Sans", "Liberation Sans", "Arial", "System");
        registerSub("Aptos", "Segoe UI", "Calibri", "Liberation Sans", "Arial", "System");
        registerSub("Cambria", "Georgia", "DejaVu Serif", "Liberation Serif", "Times New Roman", "System");
        registerSub("Consolas", "Cascadia Code", "Courier New", "Liberation Mono", "DejaVu Sans Mono", "Monospaced");
        registerSub("Times New Roman", "Liberation Serif", "DejaVu Serif", "Times", "Georgia", "System");
        registerSub("Arial", "Liberation Sans", "DejaVu Sans", "Helvetica", "System");
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
            return "System";
        }
        if (installedFamilies.contains(requestedFamily)) {
            return requestedFamily;
        }
        String sub = substitutions.get(requestedFamily.toLowerCase(Locale.ROOT));
        if (sub != null) {
            return sub;
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
