package org.originsd.oqteris.ui;

import org.originsd.oqteris.core.model.DocumentType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Persists and manages the user's recently opened document history.
 */
public class RecentFilesManager {
    private static final RecentFilesManager INSTANCE = new RecentFilesManager();
    private static final String PREF_KEY_RECENT = "recent_files_v1";
    private static final int MAX_RECENT = 15;

    public static class RecentFileEntry {
        private final File file;
        private final DocumentType type;
        private final long lastAccessed;

        public RecentFileEntry(File file, DocumentType type, long lastAccessed) {
            this.file = file;
            this.type = type;
            this.lastAccessed = lastAccessed;
        }

        public File getFile() {
            return file;
        }

        public DocumentType getType() {
            return type;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public String getFileName() {
            return file.getName();
        }

        public String getAbsolutePath() {
            return file.getAbsolutePath();
        }
    }

    private final List<RecentFileEntry> entries = new ArrayList<>();
    private final java.nio.file.Path storagePath;

    private RecentFilesManager() {
        this.storagePath = org.originsd.oqteris.config.AppConfig.getAppDataDirectory().resolve("recent_files.dat");
        load();
    }

    public static RecentFilesManager getInstance() {
        return INSTANCE;
    }

    public synchronized List<RecentFileEntry> getRecentFiles() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized void addFile(File file, DocumentType type) {
        if (file == null || !file.exists()) return;
        entries.removeIf(e -> e.getFile().getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath()));
        entries.add(0, new RecentFileEntry(file, type, System.currentTimeMillis()));
        while (entries.size() > MAX_RECENT) {
            entries.remove(entries.size() - 1);
        }
        save();
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    private void load() {
        if (!java.nio.file.Files.exists(storagePath)) return;
        try {
            String data = java.nio.file.Files.readString(storagePath);
            if (data == null || data.isBlank()) return;

            String[] parts = data.split("\\|\\|");
            for (String p : parts) {
                if (p.isBlank()) continue;
                String[] fields = p.split("::");
                if (fields.length >= 2) {
                    File file = new File(fields[0]);
                    if (file.exists()) {
                        try {
                            DocumentType type = DocumentType.valueOf(fields[1]);
                            long time = fields.length >= 3 ? Long.parseLong(fields[2]) : System.currentTimeMillis();
                            entries.add(new RecentFileEntry(file, type, time));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            if (storagePath.getParent() != null) {
                java.nio.file.Files.createDirectories(storagePath.getParent());
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                RecentFileEntry e = entries.get(i);
                sb.append(e.getAbsolutePath()).append("::")
                  .append(e.getType().name()).append("::")
                  .append(e.getLastAccessed());
                if (i < entries.size() - 1) {
                    sb.append("||");
                }
            }
            java.nio.file.Files.writeString(storagePath, sb.toString());
        } catch (Exception ignored) {
        }
    }
}
