package com.nicholasburczyk.packupdater.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class CurseForgeLocator {

    private CurseForgeLocator() {
    }

    public static Optional<Path> detect() {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> candidates = List.of(
                home.resolve("curseforge/minecraft/Instances"),
                home.resolve("Documents/curseforge/minecraft/Instances"),
                home.resolve("OneDrive/Documents/curseforge/minecraft/Instances"),
                home.resolve("OneDrive/curseforge/minecraft/Instances"),
                home.resolve("AppData/Roaming/CurseForge/minecraft/Instances"),
                home.resolve("Library/Application Support/CurseForge/minecraft/Instances")
        );
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    public static boolean looksLikeInstancesFolder(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Path normalized = Path.of(path).normalize();
        int count = normalized.getNameCount();
        if (count < 3) {
            return false;
        }
        String last = normalized.getName(count - 1).toString();
        String middle = normalized.getName(count - 2).toString();
        String first = normalized.getName(count - 3).toString();
        return last.equalsIgnoreCase("Instances")
                && middle.equalsIgnoreCase("minecraft")
                && first.toLowerCase().contains("curseforge");
    }
}
