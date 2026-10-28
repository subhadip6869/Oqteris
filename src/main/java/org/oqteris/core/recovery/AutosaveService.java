package org.oqteris.core.recovery;

import org.oqteris.core.model.DocumentModel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Handles automatic periodic backups of modified documents to protect against unexpected closures.
 */
public class AutosaveService {
    private static final AutosaveService INSTANCE = new AutosaveService();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Oqteris-Autosave-Worker");
        t.setDaemon(true);
        return t;
    });

    private final Path recoveryDir;
    private Supplier<List<DocumentModel>> documentsSupplier = ArrayList::new;
    private int intervalSeconds = 120; // Default: 2 minutes
    private boolean enabled = true;

    private AutosaveService() {
        String userHome = System.getProperty("user.home", ".");
        this.recoveryDir = Paths.get(userHome, ".oqteris", "recovery");
        try {
            Files.createDirectories(recoveryDir);
        } catch (IOException ignored) {
        }
        startScheduler();
    }

    public static AutosaveService getInstance() {
        return INSTANCE;
    }

    public Path getRecoveryDir() {
        return recoveryDir;
    }

    public void setDocumentsSupplier(Supplier<List<DocumentModel>> supplier) {
        this.documentsSupplier = supplier != null ? supplier : ArrayList::new;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int seconds) {
        this.intervalSeconds = Math.max(30, seconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void startScheduler() {
        scheduler.scheduleWithFixedDelay(this::runAutosave, 30, intervalSeconds, TimeUnit.SECONDS);
    }

    private void runAutosave() {
        if (!enabled) return;
        try {
            List<DocumentModel> docs = documentsSupplier.get();
            if (docs == null) return;

            for (DocumentModel doc : docs) {
                if (doc.isModified()) {
                    performAutosave(doc);
                }
            }
        } catch (Exception e) {
            System.err.println("[AutosaveService] Error during autosave: " + e.getMessage());
        }
    }

    public void performAutosave(DocumentModel doc) {
        if (doc == null) return;
        try {
            String safeTitle = doc.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
            String filename = "recovery_" + safeTitle + "_" + doc.getType().name() + ".meta";
            Path target = recoveryDir.resolve(filename);

            try (FileWriter writer = new FileWriter(target.toFile())) {
                writer.write("Title: " + doc.getTitle() + "\n");
                writer.write("Type: " + doc.getType().name() + "\n");
                writer.write("OriginalPath: " + (doc.getFile() != null ? doc.getFile().getAbsolutePath() : "none") + "\n");
                writer.write("Timestamp: " + Instant.now().toString() + "\n");
            }
        } catch (Exception e) {
            System.err.println("[AutosaveService] Could not save recovery snapshot for " + doc.getTitle() + ": " + e.getMessage());
        }
    }

    public void clearRecovery(DocumentModel doc) {
        if (doc == null) return;
        try {
            String safeTitle = doc.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
            String prefix = "recovery_" + safeTitle;
            File[] files = recoveryDir.toFile().listFiles((dir, name) -> name.startsWith(prefix));
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
