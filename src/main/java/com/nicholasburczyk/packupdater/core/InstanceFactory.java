package com.nicholasburczyk.packupdater.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class InstanceFactory {

    public static final String TEMPLATE_KEY = "instance-template.json";

    private static final String PROFILE_IMAGE_FOLDER = "profileImage";
    private static final String INSTANCE_FILE = "minecraftinstance.json";
    private static final String CLIENT_FILE = ".curseclient";
    private static final String EPOCH = "0001-01-01T00:00:00";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InstanceFactory() {
    }

    public static boolean hasTemplate(ModpackInfo server) {
        try {
            B2ClientProvider.getClient().headObject(HeadObjectRequest.builder()
                    .bucket(ConfigManager.getInstance().getConfig().getBucketName())
                    .key(server.getRoot() + "/" + TEMPLATE_KEY)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            System.err.println("Could not check for an instance template: " + e.getMessage());
            return false;
        }
    }

    public static Path create(ModpackInfo server, Path instancesRoot, String folderName, Progress progress)
            throws IOException {
        progress.message("Reading the instance template");
        ObjectNode template = downloadTemplate(server);
        return createFromTemplate(template, instancesRoot, folderName, trackedFolders(server), progress);
    }

    public static Path createFromTemplate(ObjectNode template, Path instancesRoot, String folderName,
                                          List<String> folders, Progress progress) throws IOException {
        Path folder = instancesRoot.resolve(folderName);
        if (Files.exists(folder)) {
            throw new IOException("A folder called " + folderName + " already exists.");
        }

        progress.message("Creating " + folderName);
        Files.createDirectories(folder);

        String guid = UUID.randomUUID().toString();
        stamp(template, guid, folderName, folder);

        Path instanceFile = folder.resolve(INSTANCE_FILE);
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        MAPPER.writeValue(instanceFile.toFile(), template);

        Files.writeString(folder.resolve(CLIENT_FILE), guid, StandardCharsets.UTF_8);

        for (String name : folders) {
            Files.createDirectories(folder.resolve(name));
        }
        Files.createDirectories(folder.resolve("mods"));

        return folder;
    }

    private static List<String> trackedFolders(ModpackInfo server) {
        return server.getFolders() == null ? List.of() : server.getFolders();
    }

    private static ObjectNode downloadTemplate(ModpackInfo server) throws IOException {
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String key = server.getRoot() + "/" + TEMPLATE_KEY;
        try (ResponseInputStream<GetObjectResponse> stream = B2ClientProvider.getClient()
                .getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return (ObjectNode) MAPPER.readTree(stream);
        } catch (NoSuchKeyException e) {
            throw new IOException("This modpack has no instance template yet. "
                    + "Ask your admin to publish one, or make the CurseForge profile yourself.", e);
        }
    }

    private static void stamp(ObjectNode template, String guid, String folderName, Path folder) {
        String installPath = folder.toAbsolutePath().normalize().toString();
        if (!installPath.endsWith(separator())) {
            installPath = installPath + separator();
        }

        template.put("guid", guid);
        template.put("name", folderName);
        template.put("installPath", installPath);
        template.put("installDate", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        template.put("lastPlayed", EPOCH);
        template.put("lastPreviousMatchUpdate", EPOCH);
        template.put("lastRefreshAttempt", EPOCH);
        template.put("fileDate", EPOCH);
        template.put("playedCount", 0);
        template.put("timePlayed", 0);
        template.put("isValid", true);
        template.put("isEnabled", true);
        template.put("isUnlocked", true);
        template.put("isVanilla", false);
        template.put("wasNameManuallyChanged", false);
        template.putNull("profileImagePath");
        template.putNull("groupId");
        template.set("installedAddons", MAPPER.createArrayNode());
        template.set("cachedScans", MAPPER.createArrayNode());
        template.set("modpackOverrides", MAPPER.createArrayNode());

        ObjectNode sync = MAPPER.createObjectNode();
        sync.put("PreferenceEnabled", false);
        sync.put("PreferenceAutoSync", true);
        sync.put("PreferenceAutoDelete", false);
        sync.put("PreferenceBackupSavedVariables", false);
        sync.put("GameInstanceGuid", "00000000-0000-0000-0000-000000000000");
        sync.put("SyncProfileID", 0);
        sync.putNull("SavedVariablesProfile");
        sync.put("LastSyncDate", EPOCH);
        template.set("syncProfile", sync);
    }

    private static String separator() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "\\" : "/";
    }

    public static boolean syncProfileImage(Path instanceFolder, String modpackId) {
        Path source = findArtwork(instanceFolder.resolve(PROFILE_IMAGE_FOLDER));
        if (source == null) {
            return false;
        }
        Path assets = curseForgeCustomImages();
        if (assets == null) {
            return false;
        }
        Path instanceFile = instanceFolder.resolve(INSTANCE_FILE);
        if (!Files.isRegularFile(instanceFile)) {
            return false;
        }

        try {
            Files.createDirectories(assets);
            Path target = assets.resolve(stableImageName(modpackId, instanceFolder,
                    extensionOf(source.getFileName().toString())));

            ObjectNode node = (ObjectNode) MAPPER.readTree(instanceFile.toFile());
            String current = node.path("profileImagePath").asText(null);
            boolean pointsAtOurs = current != null && !current.isBlank()
                    && Path.of(current).normalize().equals(target.normalize());

            if (pointsAtOurs && Files.isRegularFile(target) && sameContent(source, target)) {
                return false;
            }

            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            node.put("profileImagePath", target.toAbsolutePath().toString());
            MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
            MAPPER.writeValue(instanceFile.toFile(), node);
            return true;
        } catch (IOException e) {
            System.err.println("Could not set the profile image: " + e.getMessage());
            return false;
        }
    }

    private static boolean sameContent(Path left, Path right) {
        try {
            return Files.size(left) == Files.size(right)
                    && Hashing.md5(left).equalsIgnoreCase(Hashing.md5(right));
        } catch (IOException e) {
            return false;
        }
    }

    private static String stableImageName(String modpackId, Path instanceFolder, String extension) {
        String base = modpackId == null || modpackId.isBlank()
                ? instanceFolder.getFileName().toString()
                : modpackId;
        String safe = base.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (safe.isEmpty()) {
            safe = "modpack";
        }
        return "GroidPack-" + safe + extension;
    }

    private static Path findArtwork(Path folder) {
        if (!Files.isDirectory(folder)) {
            return null;
        }
        File[] files = folder.toFile().listFiles(file -> file.isFile() && isImage(file.getName()));
        return files == null || files.length == 0 ? null : files[0].toPath();
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".png" : name.substring(dot);
    }

    private static Path curseForgeCustomImages() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null && !appData.isBlank()
                    ? Path.of(appData)
                    : home.resolve("AppData/Roaming");
            return base.resolve("CurseForge/CfApp_Assets/ModpackImages/custom");
        }
        if (os.contains("mac")) {
            return home.resolve("Library/Application Support/CurseForge/CfApp_Assets/ModpackImages/custom");
        }
        return null;
    }

    public static ObjectNode scrubForTemplate(Path instanceFile) throws IOException {
        ObjectNode node = (ObjectNode) MAPPER.readTree(instanceFile.toFile());
        if (node.get("baseModLoader") == null || node.get("baseModLoader").isNull()) {
            throw new IOException("That instance has no mod loader information yet. "
                    + "Open it in CurseForge once, then try again.");
        }
        node.putNull("guid");
        node.put("name", "");
        node.put("installPath", "");
        node.put("installDate", EPOCH);
        node.set("installedAddons", MAPPER.createArrayNode());
        node.set("cachedScans", MAPPER.createArrayNode());
        node.putNull("profileImagePath");
        return node;
    }

    public static String describeTemplate(ObjectNode template) {
        var loader = template.get("baseModLoader");
        String game = template.path("gameVersion").asText("unknown");
        if (loader == null || loader.isNull()) {
            return "Minecraft " + game;
        }
        String name = loader.path("name").asText("");
        return "Minecraft " + game + (name.isBlank() ? "" : "   " + name);
    }
}
