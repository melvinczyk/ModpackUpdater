package com.nicholasburczyk.packupdater.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.regex.*;

public class SoftwareUpdateManager {

    private static final String GITHUB_RELEASE_URL = "https://api.github.com/repos/melvinczyk/ModpackUpdater/releases/latest";
    private static final String JAR_PREFIX = "packupdater-";
    private static final String JAR_SUFFIX = ".jar";

    private static String currentVersion = null;
    private static String latestVersion = null;
    private static String latestJarName = null;
    private static String downloadUrl = null;

    public static boolean checkForUpdate() {
        try {
            Path currentJar = findLocalJar();
            if (currentJar == null) return false;

            currentVersion = extractVersion(currentJar.getFileName().toString());
            if (currentVersion == null) return false;

            HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_RELEASE_URL).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            JSONObject releaseJson = new JSONObject(new JSONTokener(conn.getInputStream()));

            String latestTag = releaseJson.getString("tag_name");
            latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;

            if (currentVersion.equals(latestVersion)) {
                System.out.println("No new software updates yet!");
                return false;
            }

            JSONArray assets = releaseJson.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.getString("name");

                if (name.endsWith(".jar")) {
                    latestJarName = name;
                    downloadUrl = asset.getString("browser_download_url");
                    System.out.println("Found a new update: " + latestJarName);
                    return true;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean performUpdate() {
        try {
            if (latestJarName == null || downloadUrl == null || currentVersion == null || latestVersion == null) {
                System.err.println("Update info not initialized. Call checkForUpdate() first.");
                return false;
            }

            Path currentJar = findLocalJar();
            if (currentJar == null) {
                System.err.println("No local jar found.");
                return false;
            }

            Path newJarPath = Paths.get(latestJarName);
            try (InputStream in = new URL(downloadUrl).openStream()) {
                Files.copy(in, newJarPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Downloaded " + latestJarName);
            }

            Files.deleteIfExists(currentJar);
            System.out.println("Deleted old JAR: " + currentJar.getFileName());

            updateCommandScript(currentJar.getFileName().toString(), latestJarName);
            updateBatScript(currentJar.getFileName().toString(), latestJarName);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Path findLocalJar() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), JAR_PREFIX + "*" + JAR_SUFFIX)) {
            for (Path entry : stream) {
                return entry;
            }
        }
        return null;
    }

    private static String extractVersion(String jarName) {
        Pattern pattern = Pattern.compile(Pattern.quote(JAR_PREFIX) + "(\\d+\\.\\d+)" + Pattern.quote(JAR_SUFFIX));
        Matcher matcher = pattern.matcher(jarName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void updateCommandScript(String oldJar, String newJar) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "*.command")) {
            for (Path commandFile : stream) {
                String content = Files.readString(commandFile);
                content = content.replace(oldJar, newJar);
                Files.writeString(commandFile, content, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Updated " + commandFile.getFileName());
                break;
            }
        } catch (IOException e) {
            System.err.println("Failed to update .command file: " + e.getMessage());
        }
    }

    private static void updateBatScript(String oldJar, String newJar) {
        Path batPath = Paths.get("(CLICK ME) PackUpdater.bat");
        if (!Files.exists(batPath)) return;

        try {
            String content = Files.readString(batPath);
            content = content.replace(oldJar, newJar);
            Files.writeString(batPath, content, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Updated " + batPath.getFileName());
        } catch (IOException e) {
            System.err.println("Failed to update .bat file: " + e.getMessage());
        }
    }
}
