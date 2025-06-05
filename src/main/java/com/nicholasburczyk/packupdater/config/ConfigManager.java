package com.nicholasburczyk.packupdater.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private Config config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final File configFile = new File("src/main/resources/com/nicholasburczyk/packupdater/config/settings.json");

    private ConfigManager() {
        loadConfig();
    }

    private static final class InstanceHolder {
        public static final ConfigManager instance = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return InstanceHolder.instance;
    }

    private void loadConfig() {
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
}
