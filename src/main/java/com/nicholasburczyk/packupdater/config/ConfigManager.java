package com.nicholasburczyk.packupdater.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.model.Config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigManager {

    private Config config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final File configFile = new File("settings.json");
    private final String resourcePath = "/settings.json";

    private ConfigManager() {
        ensureConfigFileExists();
        reloadConfig();
    }

    private static final class InstanceHolder {
        public static final ConfigManager instance = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return InstanceHolder.instance;
    }

    private void ensureConfigFileExists() {
        if (!configFile.exists()) {
            // Copy default settings from JAR resources to external file
            try (InputStream resourceStream = getClass().getResourceAsStream(resourcePath)) {
                if (resourceStream != null) {
                    Files.copy(resourceStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Created settings.json from default configuration");
                } else {
                    // Fallback: create default config if resource doesn't exist
                    config = new Config();
                    saveConfig();
                    System.out.println("Created default settings.json");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void reloadConfig() {
        try {
            if (configFile.exists()) {
                config = mapper.readValue(configFile, Config.class);
            } else {
                config = new Config();
                saveConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
            config = new Config();
        }
    }

    public Config getConfig() {
        return config;
    }

    public void saveConfig() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getConfigFilePath() {
        return configFile.getAbsolutePath();
    }
}