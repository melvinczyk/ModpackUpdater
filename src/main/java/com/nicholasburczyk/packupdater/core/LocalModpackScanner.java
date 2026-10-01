package com.nicholasburczyk.packupdater.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalModpackScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LocalModpackScanner() {
    }

    public static Map<String, ModpackInfo> scan() {
        Map<String, ModpackInfo> found = new LinkedHashMap<>();
        String configured = ConfigManager.getInstance().getConfig().getCurseforge_path();
        if (configured == null || configured.isBlank()) {
            return found;
        }

        Path instances = Path.of(configured);
        if (!Files.isDirectory(instances)) {
            return found;
        }

        File[] folders = instances.toFile().listFiles(File::isDirectory);
        if (folders == null) {
            return found;
        }

        for (File folder : folders) {
            Path manifest = folder.toPath().resolve("manifest.json");
            if (!Files.isRegularFile(manifest)) {
                continue;
            }
            try {
                ModpackInfo info = MAPPER.readValue(manifest.toFile(), ModpackInfo.class);
                info.setRoot(folder.getName());
                String key = keyFor(info, folder.getName());
                info.setModpackId(key);
                found.put(key, info);
            } catch (IOException e) {
                System.err.println("Skipping " + folder.getName() + ", unreadable manifest: " + e.getMessage());
            }
        }

        return found;
    }

    public static void refreshRegistry() {
        ModpackRegistry.setLocalModpacks(scan());
    }

    private static String keyFor(ModpackInfo info, String folderName) {
        String id = info.getModpackId();
        return id == null || id.isBlank() ? folderName : id;
    }
}
