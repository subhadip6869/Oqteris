package org.originsd.oqteris.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Service for loading and persisting user preferences to settings.json in the user data directory.
 * Adheres strictly to the OriginSD Settings Management standard.
 */
public class UserSettingsService {

    private static volatile UserSettingsService instance;

    private final ObjectMapper mapper;
    private final Path settingsPath;
    private UserSettings currentSettings;

    public static UserSettingsService getInstance() {
        if (instance == null) {
            synchronized (UserSettingsService.class) {
                if (instance == null) {
                    instance = new UserSettingsService();
                }
            }
        }
        return instance;
    }

    public UserSettingsService() {
        this(AppConfig.getSettingsPath());
    }

    public UserSettingsService(Path settingsPath) {
        this.settingsPath = settingsPath;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.currentSettings = loadOrInitialize();
    }

    public synchronized UserSettings getSettings() {
        if (currentSettings == null) {
            currentSettings = loadOrInitialize();
        }
        return currentSettings;
    }

    public synchronized void save(UserSettings settings) {
        this.currentSettings = (settings != null) ? settings : new UserSettings();
        persist(this.currentSettings);
    }

    public synchronized void save() {
        if (currentSettings != null) {
            persist(currentSettings);
        }
    }

    public synchronized void update(Consumer<UserSettings> modifier) {
        if (currentSettings == null) {
            currentSettings = loadOrInitialize();
        }
        if (modifier != null) {
            modifier.accept(currentSettings);
            persist(currentSettings);
        }
    }

    private UserSettings loadOrInitialize() {
        File file = settingsPath.toFile();
        if (file.exists() && file.length() > 0) {
            try {
                UserSettings s = mapper.readValue(file, UserSettings.class);
                if (s != null) {
                    return s;
                }
            } catch (Exception e) {
                // If invalid or unreadable, fall back to default
            }
        }

        // Initialize defaults and save immediately
        UserSettings defaults = new UserSettings();
        persist(defaults);
        return defaults;
    }

    private void persist(UserSettings settings) {
        try {
            if (settingsPath.getParent() != null) {
                Files.createDirectories(settingsPath.getParent());
            }
            mapper.writeValue(settingsPath.toFile(), settings);
        } catch (Exception e) {
            // Unchecked fallback to avoid runtime crashes
            System.err.println("[UserSettingsService] Failed to persist settings: " + e.getMessage());
        }
    }
}
