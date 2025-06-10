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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ModpackUpdater {

    public static void applyUpdates(ModpackInfo local, ModpackInfo server) {
        System.out.println("Applying updates for modpack: " + local.getModpackId());

        String localVersion = local.getVersion();
        String serverModpackRoot = server.getRoot();

        List<ChangelogEntry> serverChangelog = server.getChangelog();
        serverChangelog.sort(Comparator.comparing(ChangelogEntry::getVersion));

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

        List<ChangelogEntry> serverChangelogCopy = new ArrayList<>(server.getChangelog());
        Collections.reverse(serverChangelogCopy);
        local.setChangelog(serverChangelogCopy);

        saveUpdatedManifest(local);

        System.out.println("Update complete. New version: " + local.getVersion());
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
