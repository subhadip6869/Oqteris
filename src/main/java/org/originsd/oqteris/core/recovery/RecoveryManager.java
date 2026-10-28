package org.originsd.oqteris.core.recovery;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks for and restores unsaved document recovery records created by previous sessions.
 */
public class RecoveryManager {
    public static class RecoveryRecord {
        private final File metaFile;
        private final String title;
        private final String type;
        private final String originalPath;
        private final String timestamp;

        public RecoveryRecord(File metaFile, String title, String type, String originalPath, String timestamp) {
            this.metaFile = metaFile;
            this.title = title;
            this.type = type;
            this.originalPath = originalPath;
            this.timestamp = timestamp;
        }

        public File getMetaFile() {
            return metaFile;
        }

        public String getTitle() {
            return title;
        }

        public String getType() {
            return type;
        }

        public String getOriginalPath() {
            return originalPath;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void discard() {
            if (metaFile != null && metaFile.exists()) {
                metaFile.delete();
            }
        }
    }

    public static List<RecoveryRecord> findRecoverableDocuments() {
        List<RecoveryRecord> records = new ArrayList<>();
        Path recoveryDir = AutosaveService.getInstance().getRecoveryDir();
        File dir = recoveryDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return records;
        }

        File[] files = dir.listFiles((d, name) -> name.startsWith("recovery_") && name.endsWith(".meta"));
        if (files == null) return records;

        for (File f : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String title = "";
                String type = "";
                String originalPath = "";
                String timestamp = "";
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Title: ")) title = line.substring(7);
                    else if (line.startsWith("Type: ")) type = line.substring(6);
                    else if (line.startsWith("OriginalPath: ")) originalPath = line.substring(14);
                    else if (line.startsWith("Timestamp: ")) timestamp = line.substring(11);
                }
                if (!title.isEmpty()) {
                    records.add(new RecoveryRecord(f, title, type, originalPath, timestamp));
                }
            } catch (Exception ignored) {
            }
        }

        return records;
    }
}
