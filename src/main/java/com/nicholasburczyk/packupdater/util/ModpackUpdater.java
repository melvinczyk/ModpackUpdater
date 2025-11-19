package com.nicholasburczyk.packupdater.util;

import com.nicholasburczyk.packupdater.model.*;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ModpackUpdater {

    public static void applyUpdates(ModpackInfo local, ModpackInfo server) {
        System.out.println("Applying updates for modpack: " + local.getModpackId());

        String localVersion = local.getVersion();
        String serverModpackRoot = server.getRoot();

        List<ChangelogEntry> serverChangelog = new ArrayList<>(server.getChangelog());
        serverChangelog.sort((a, b) -> compareVersions(a.getVersion(), b.getVersion()));

        boolean updating = false;
        List<ChangelogEntry> entriesToApply = new ArrayList<>();

        for (ChangelogEntry entry : serverChangelog) {
            String version = entry.getVersion();

            if (version.equals(localVersion)) {
                updating = true;
                continue;
            }

            if (!updating) continue;

            System.out.println("Applying changelog entry: " + version);

            for (ChangeOperation op : entry.getOperations()) {
                handleOperation(local, serverModpackRoot, op);
            }

            entriesToApply.add(entry);

            local.setVersion(version);
            local.setUpdateCount(local.getUpdateCount() + 1);
            local.setLastUpdated(entry.getTimestamp());
        }

        if (server.getFolders() != null) {
            local.setFolders(new ArrayList<>(server.getFolders()));
        }
        if (server.getFiles() != null) {
            local.setFiles(new ArrayList<>(server.getFiles()));
        }

        syncRootFiles(local, server);

        List<ChangelogEntry> serverChangelogCopy = new ArrayList<>(server.getChangelog());
        serverChangelogCopy.sort((a, b) -> compareVersions(b.getVersion(), a.getVersion()));
        local.setChangelog(serverChangelogCopy);

        saveUpdatedManifest(local);

        System.out.println("Update complete. New version: " + local.getVersion());
    }


    private static int compareVersions(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");

        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int ai = (i < a.length) ? Integer.parseInt(a[i]) : 0;
            int bi = (i < b.length) ? Integer.parseInt(b[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0; // equal
    }


    private static void syncRootFiles(ModpackInfo local, ModpackInfo server) {
        if (server.getFiles() == null || server.getFiles().isEmpty()) {
            System.out.println("No root files to sync");
            return;
        }

        System.out.println("Syncing root files...");
        File baseDir = new File(ConfigManager.getInstance().getConfig().getCurseforge_path(), local.getRoot());
        String serverModpackRoot = server.getRoot();

        for (String fileName : server.getFiles()) {
            File localFile = new File(baseDir, fileName);

            try {
                boolean needsDownload = false;

                if (!localFile.exists()) {
                    System.out.println("Root file missing locally: " + fileName);
                    needsDownload = true;
                } else {
                    String serverMd5 = getServerFileMD5(serverModpackRoot, fileName);
                    if (serverMd5 != null) {
                        String localMd5 = calculateMD5(localFile.toPath());
                        if (!localMd5.equals(serverMd5)) {
                            System.out.println("Root file outdated: " + fileName);
                            needsDownload = true;
                        }
                    } else {
                        System.out.println("Could not verify root file on server: " + fileName);
                    }
                }

                if (needsDownload) {
                    downloadFileFromS3(serverModpackRoot, fileName, localFile);
                }

            } catch (Exception e) {
                System.err.println("Error syncing root file " + fileName + ": " + e.getMessage());
            }
        }

        try {
            Files.list(baseDir.toPath())
                    .filter(Files::isRegularFile)
                    .forEach(localFilePath -> {
                        String fileName = localFilePath.getFileName().toString();

                        if (fileName.equals("manifest.json")) {
                            return;
                        }

                        if (!server.getFiles().contains(fileName)) {
                            if (local.getFiles() != null && local.getFiles().contains(fileName)) {
                                try {
                                    Files.delete(localFilePath);
                                    System.out.println("Removed obsolete root file: " + fileName);
                                } catch (IOException e) {
                                    System.err.println("Failed to delete obsolete root file " + fileName + ": " + e.getMessage());
                                }
                            }
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error cleaning up root files: " + e.getMessage());
        }
    }

    private static String getServerFileMD5(String serverModpackRoot, String fileName) {
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String key = serverModpackRoot + "/" + fileName;

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            return B2ClientProvider.getClient().headObject(headRequest).eTag().replace("\"", "");
        } catch (NoSuchKeyException e) {
            System.out.println("Root file not found on server: " + fileName);
            return null;
        } catch (Exception e) {
            System.err.println("Error getting server file MD5 for " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private static String calculateMD5(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }

    private static void saveUpdatedManifest(ModpackInfo modpackInfo) {
        try {
            File manifestFile = new File(
                    ConfigManager.getInstance().getConfig().getCurseforge_path(),
                    modpackInfo.getRoot() + "/manifest.json"
            );

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

            mapper.writeValue(manifestFile, modpackInfo);
            System.out.println("Saved updated manifest to: " + manifestFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Failed to save updated manifest");
            e.printStackTrace();
        }
    }

    private static void handleOperation(ModpackInfo modpack, String serverModpackRoot, ChangeOperation op) {
        try {
            File baseDir = new File(ConfigManager.getInstance().getConfig().getCurseforge_path(), modpack.getRoot());
            File targetFile;

            switch (op.getType()) {
                case "Added":
                case "Modified":
                    targetFile = new File(baseDir, op.getPath());
                    downloadFileFromS3(serverModpackRoot, op.getPath(), targetFile);
                    break;

                case "Deleted":
                    targetFile = new File(baseDir, op.getPath());
                    if (targetFile.exists()) {
                        Files.delete(targetFile.toPath());
                        System.out.println("Deleted file: " + op.getPath());
                    }
                    break;

                case "Moved":
                    File oldFile = new File(baseDir, op.getOldPath());
                    File newFile = new File(baseDir, op.getNewPath());
                    if (oldFile.exists()) {
                        newFile.getParentFile().mkdirs();
                        Files.move(oldFile.toPath(), newFile.toPath());
                        System.out.println("Moved file: " + op.getOldPath() + " -> " + op.getNewPath());
                    }
                    break;

                default:
                    System.out.println("Unknown operation: " + op);
            }

        } catch (Exception e) {
            System.err.println("Error handling operation: " + op);
            e.printStackTrace();
        }
    }

    private static void downloadFileFromS3(String modpackId, String path, File destination) {
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String key = modpackId + "/" + path;

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            B2ClientProvider.getClient().headObject(headRequest);

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            try (InputStream inputStream = B2ClientProvider.getClient().getObject(getRequest)) {
                destination.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(destination)) {
                    inputStream.transferTo(out);
                }
                System.out.println("Downloaded: " + path);
            }

        } catch (NoSuchKeyException e) {
            System.out.println("File not found on server, skipping download: " + path);

        } catch (Exception e) {
            System.err.println("Failed to download: " + path);
            e.printStackTrace();
        }
    }
}