package com.nicholasburczyk.packupdater.core;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    private static final String FALLBACK_DIR_NAME = ".packupdater";

    private static Path cachedAppDir;
    private static Path cachedRunningJar;

    private AppPaths() {
    }

    public static synchronized Path runningJar() {
        if (cachedRunningJar == null) {
            cachedRunningJar = resolveRunningJar();
        }
        return cachedRunningJar;
    }

    public static synchronized Path appDir() {
        if (cachedAppDir == null) {
            cachedAppDir = resolveAppDir();
        }
        return cachedAppDir;
    }

    public static Path dataDir() {
        Path dir = appDir();
        if (isWritable(dir)) {
            return dir;
        }
        Path fallback = Paths.get(System.getProperty("user.home"), FALLBACK_DIR_NAME);
        try {
            Files.createDirectories(fallback);
        } catch (IOException e) {
            return dir;
        }
        return fallback;
    }

    public static Path settingsFile() {
        return dataDir().resolve("settings.json");
    }

    public static Path stagedUpdateDir() {
        return dataDir().resolve(".update");
    }

    public static boolean isRunningFromJar() {
        return runningJar() != null;
    }

    private static Path resolveRunningJar() {
        try {
            URI location = AppPaths.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            Path path = Paths.get(location);
            if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                return path.toAbsolutePath().normalize();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static Path resolveAppDir() {
        Path jar = runningJar();
        if (jar != null && jar.getParent() != null) {
            return jar.getParent();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static boolean isWritable(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try {
            Path probe = Files.createTempFile(dir, ".write-probe", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static Path javaExecutable() {
        String home = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String name = windows ? "java.exe" : "java";
        Path candidate = Paths.get(home, "bin", name);
        return Files.isExecutable(candidate) ? candidate : Paths.get(name);
    }

    public static File asFile(Path path) {
        return path == null ? null : path.toFile();
    }
}
