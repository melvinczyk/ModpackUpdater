package com.nicholasburczyk.packupdater.util;

import com.nicholasburczyk.packupdater.model.*;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ModpackUpdater {

    /** A server object's identity: its etag (== MD5 for the single-part uploads this app does) and size. */
    private record ServerObject(String etag, long size) {}

    public static void applyUpdates(ModpackInfo local, ModpackInfo server) {
        System.out.println("Applying updates for modpack: " + local.getModpackId());

        String localVersion = local.getVersion();

        List<ChangelogEntry> serverChangelog = server.getChangelog();
        // Sort ascending by numeric version so version bookkeeping advances
        // oldest-first (string sorting would place "1.10" before "1.9").
        serverChangelog.sort((e1, e2) -> UpdateChecker.compareVersions(e1.getVersion(), e2.getVersion()));

        // Advance version/count/timestamp for every entry newer than the local
        // version. The actual file changes are applied by the folder/root-file
        // mirror below, which reconciles against the server directly, so we no
        // longer replay each changelog operation (that was redundant work).
        for (ChangelogEntry entry : serverChangelog) {
            String version = entry.getVersion();
            if (!UpdateChecker.isVersionGreater(version, localVersion)) continue;

            System.out.println("Recording changelog entry: " + version);
            local.setVersion(version);
            local.setUpdateCount(local.getUpdateCount() + 1);
            local.setLastUpdated(entry.getTimestamp());
        }

        // Sync root files before overwriting the local file/folder lists so the
        // cleanup step can compare the server list against the previously
        // tracked local list to find obsolete files.
        syncRootFiles(local, server);

        // Fully reconcile every tracked folder against the server so the local
        // pack is an exact mirror, regardless of whether the changelog captured
        // every change. This mirrors the migrate flow's behaviour.
        syncTrackedFolders(local, server);

        if (server.getFolders() != null) {
            local.setFolders(new ArrayList<>(server.getFolders()));
        }
        if (server.getFiles() != null) {
            local.setFiles(new ArrayList<>(server.getFiles()));
        }

        List<ChangelogEntry> serverChangelogCopy = new ArrayList<>(server.getChangelog());
        Collections.reverse(serverChangelogCopy);
        local.setChangelog(serverChangelogCopy);

        saveUpdatedManifest(local);

        System.out.println("Update complete. New version: " + local.getVersion());
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
                    HeadObjectResponse head = getServerHead(serverModpackRoot, fileName);
                    if (head != null) {
                        // Cheap size check first; only hash when sizes match.
                        if (localFile.length() != head.contentLength()) {
                            System.out.println("Root file outdated (size): " + fileName);
                            needsDownload = true;
                        } else {
                            String serverMd5 = head.eTag().replace("\"", "");
                            String localMd5 = calculateMD5(localFile.toPath());
                            if (!localMd5.equalsIgnoreCase(serverMd5)) {
                                System.out.println("Root file outdated: " + fileName);
                                needsDownload = true;
                            }
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

        try (var localFiles = Files.list(baseDir.toPath())) {
            localFiles
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

    /**
     * Reconciles every tracked folder so the local pack exactly mirrors the
     * server: downloads files that are missing or changed and deletes any local
     * file under a tracked folder that no longer exists on the server.
     *
     * If the server listing for a folder fails, that folder is skipped entirely
     * (no deletions) so a transient error can never wipe out local files.
     */
    private static void syncTrackedFolders(ModpackInfo local, ModpackInfo server) {
        List<String> folders = server.getFolders();
        if (folders == null || folders.isEmpty()) {
            System.out.println("No tracked folders to sync");
            return;
        }

        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String serverModpackRoot = server.getRoot();
        File baseDir = new File(ConfigManager.getInstance().getConfig().getCurseforge_path(), local.getRoot());

        int threads = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (String folder : folders) {
                System.out.println("Syncing tracked folder: " + folder);
                String prefix = serverModpackRoot + "/" + folder + "/";

                // 1. Collect every object the server has under this folder. Keys are
                // stored relative to the modpack root (e.g. "mods/foo.jar") so they
                // line up with local relative paths. Uses the paginator so folders
                // with more than 1000 objects are listed completely — a truncated
                // listing here would make valid files look local-only and be deleted.
                Map<String, ServerObject> serverFiles = new HashMap<>();
                boolean listingComplete = true;
                try {
                    ListObjectsV2Request request = ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build();

                    for (S3Object obj : B2ClientProvider.getClient().listObjectsV2Paginator(request).contents()) {
                        String key = obj.key();
                        if (key.endsWith("/")) continue; // skip folder placeholder keys
                        String relativePath = key.substring(serverModpackRoot.length() + 1);
                        serverFiles.put(relativePath, new ServerObject(obj.eTag().replace("\"", ""), obj.size()));
                    }
                } catch (Exception e) {
                    listingComplete = false;
                    System.err.println("Failed to list server folder '" + folder + "', skipping to avoid data loss: " + e.getMessage());
                }

                if (!listingComplete) {
                    continue;
                }

                // 2. Download anything missing or changed, in parallel.
                List<Future<?>> futures = new ArrayList<>();
                for (Map.Entry<String, ServerObject> entry : serverFiles.entrySet()) {
                    String relativePath = entry.getKey();
                    ServerObject serverObj = entry.getValue();

                    if (relativePath.endsWith(".bzEmpty")) continue; // Backblaze empty-folder marker

                    futures.add(pool.submit(() -> {
                        File localFile = new File(baseDir, relativePath);
                        try {
                            boolean needsDownload = false;
                            if (!localFile.exists()) {
                                needsDownload = true;
                            } else if (localFile.length() != serverObj.size()) {
                                // Cheap size check avoids hashing when it can't match.
                                needsDownload = true;
                            } else {
                                String localMd5 = calculateMD5(localFile.toPath());
                                if (!localMd5.equalsIgnoreCase(serverObj.etag())) {
                                    needsDownload = true;
                                }
                            }
                            if (needsDownload) {
                                downloadFileFromS3(serverModpackRoot, relativePath, localFile);
                            }
                        } catch (Exception e) {
                            System.err.println("Error syncing " + relativePath + ": " + e.getMessage());
                        }
                    }));
                }
                for (Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (Exception e) {
                        System.err.println("Download task failed: " + e.getMessage());
                    }
                }

                // 3. Delete local files under this folder that are not on the server.
                File folderDir = new File(baseDir, folder);
                if (folderDir.exists() && folderDir.isDirectory()) {
                    try (var localWalk = Files.walk(folderDir.toPath())) {
                        localWalk
                                .filter(Files::isRegularFile)
                                .forEach(localFilePath -> {
                                    String relativePath = baseDir.toPath().relativize(localFilePath)
                                            .toString().replace("\\", "/");
                                    if (!serverFiles.containsKey(relativePath)) {
                                        try {
                                            Files.delete(localFilePath);
                                            System.out.println("Removed local-only file: " + relativePath);
                                        } catch (IOException e) {
                                            System.err.println("Failed to delete local-only file " + relativePath + ": " + e.getMessage());
                                        }
                                    }
                                });
                    } catch (IOException e) {
                        System.err.println("Error scanning local folder '" + folder + "': " + e.getMessage());
                    }
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    private static HeadObjectResponse getServerHead(String serverModpackRoot, String fileName) {
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String key = serverModpackRoot + "/" + fileName;

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            return B2ClientProvider.getClient().headObject(headRequest);
        } catch (NoSuchKeyException e) {
            System.out.println("Root file not found on server: " + fileName);
            return null;
        } catch (Exception e) {
            System.err.println("Error getting server file metadata for " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private static String calculateMD5(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            // Stream the file through the digest instead of loading it wholesale
            // into memory (a mods folder can be gigabytes).
            try (InputStream is = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(new BufferedInputStream(is, 1 << 16), digest)) {
                byte[] buffer = new byte[1 << 16];
                while (dis.read(buffer) != -1) {
                    // read() feeds the digest as a side effect
                }
            }

            byte[] hashBytes = digest.digest();
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

    private static void downloadFileFromS3(String modpackId, String path, File destination) {
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String key = modpackId + "/" + path;

        try {
            // GetObject alone throws NoSuchKeyException when the key is missing,
            // so a preceding HeadObject would just be a wasted round trip.
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            try (InputStream inputStream = B2ClientProvider.getClient().getObject(getRequest)) {
                File parent = destination.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
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
