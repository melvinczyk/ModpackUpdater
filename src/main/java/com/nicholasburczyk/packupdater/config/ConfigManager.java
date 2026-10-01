package com.nicholasburczyk.packupdater.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nicholasburczyk.packupdater.core.AppPaths;
import com.nicholasburczyk.packupdater.core.CurseForgeLocator;
import com.nicholasburczyk.packupdater.model.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigManager {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path configFile = AppPaths.settingsFile();
    private Config config;

    private ConfigManager() {
        reloadConfig();
    }

    private static final class InstanceHolder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public Config getConfig() {
        return config;
    }

    public final void reloadConfig() {
        if (Files.isRegularFile(configFile)) {
            try {
                config = mapper.readValue(configFile.toFile(), Config.class);
            } catch (IOException e) {
                System.err.println("settings.json could not be read, using defaults: " + e.getMessage());
                config = new Config();
            }
        } else {
            config = new Config();
            CurseForgeLocator.detect().ifPresent(path -> config.setCurseforge_path(path.toString()));
            saveConfig();
        }
    }

    public void saveConfig() {
        try {
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent == null ? configFile : parent, "settings", ".tmp");
            try {
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(temp.toFile(), config);
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            System.err.println("Could not save settings.json: " + e.getMessage());
        }
    }

    public String getConfigFilePath() {
        return configFile.toAbsolutePath().toString();
    }
}
