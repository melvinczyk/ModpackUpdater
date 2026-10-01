package com.nicholasburczyk.packupdater.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ChangelogEntry;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ModpackSync {

    private static final String PROFILE_IMAGE_FOLDER = "profileImage";

    private record RemoteFile(String key, String relativePath, String eTag, long size) {
    }

    private ModpackSync() {
    }

    public static SyncReport plan(ModpackInfo local, ModpackInfo server, Progress progress) throws IOException {
        Config config = ConfigManager.getInstance().getConfig();
        IgnoreRules ignore = IgnoreRules.from(config.getIgnoredFiles());
        Path localRoot = localRoot(local);

        SyncReport report = new SyncReport();
        report.setCurrentVersion(local.getVersion());
        report.setTargetVersion(server.getVersion());

        progress.message("Reading server file list");
        Map<String, RemoteFile> remote = listTrackedRemoteFiles(server, ignore, progress);

        progress.message("Checking local files");
        int index = 0;
        for (RemoteFile file : remote.values()) {
            if (progress.isCancelled()) {
                return report;
            }
            progress.step(index++, remote.size());
            Path localFile = localRoot.resolve(file.relativePath());
            if (!Files.isRegularFile(localFile)) {
                report.add(SyncReport.Kind.MISSING, file.relativePath(), file.size());
                continue;
            }
            if (differs(localFile, file, report)) {
                report.add(SyncReport.Kind.OUTDATED, file.relativePath(), file.size());
            }
        }

        progress.message("Looking for unexpected files");
        for (String relativePath : listTrackedLocalFiles(localRoot, deletableTargets(server), ignore)) {
            if (progress.isCancelled()) {
                return report;
            }
            if (!remote.containsKey(relativePath)) {
                long size;
                try {
                    size = Files.size(localRoot.resolve(relativePath));
                } catch (IOException e) {
                    size = 0;
                }
                report.add(SyncReport.Kind.EXTRA, relativePath, size);
            }
        }

        return report;
    }

    public static void apply(ModpackInfo local, ModpackInfo server, SyncReport report, Progress progress)
            throws IOException {
        Config config = ConfigManager.getInstance().getConfig();
        Path localRoot = localRoot(local);
        S3Client client = B2ClientProvider.getClient();
        String bucket = config.getBucketName();
        String serverRoot = server.getRoot();

        List<SyncReport.Item> downloads = new ArrayList<>();
        downloads.addAll(report.itemsOf(SyncReport.Kind.MISSING));
        downloads.addAll(report.itemsOf(SyncReport.Kind.OUTDATED));
        List<SyncReport.Item> deletions = report.itemsOf(SyncReport.Kind.EXTRA);

        int total = downloads.size() + deletions.size();
        int done = 0;

        for (SyncReport.Item item : downloads) {
            if (progress.isCancelled()) {
                return;
            }
            progress.message("Downloading " + item.path());
            progress.step(done++, total);
            try {
                download(client, bucket, serverRoot + "/" + item.path(), localRoot.resolve(item.path()));
            } catch (Exception e) {
                report.addFailure("Could not download " + item.path() + ": " + rootMessage(e));
            }
        }

        for (SyncReport.Item item : deletions) {
            if (progress.isCancelled()) {
                return;
            }
            progress.message("Removing " + item.path());
            progress.step(done++, total);
            try {
                Files.deleteIfExists(localRoot.resolve(item.path()));
            } catch (IOException e) {
                report.addFailure("Could not remove " + item.path() + ": " + rootMessage(e));
            }
        }

        progress.step(total, total);
        if (!report.failures().isEmpty()) {
            progress.message("Finished with problems, leaving the recorded version alone");
            return;
        }
        progress.message("Writing manifest");
        writeManifest(local, server, localRoot);
    }

    public static boolean hasPendingUpdate(ModpackInfo server, ModpackInfo local) {
        if (server == null || local == null) {
            return false;
        }
        return VersionUtil.isGreater(server.getVersion(), local.getVersion());
    }

    public static int countNewerVersions(ModpackInfo server, ModpackInfo local) {
        if (server == null || local == null) {
            return 0;
        }
        int count = 0;
        if (server.getChangelog() != null) {
            for (ChangelogEntry entry : server.getChangelog()) {
                if (VersionUtil.isGreater(entry.getVersion(), local.getVersion())) {
                    count++;
                }
            }
        }
        if (count == 0 && hasPendingUpdate(server, local)) {
            return 1;
        }
        return count;
    }

    private static void writeManifest(ModpackInfo local, ModpackInfo server, Path localRoot) throws IOException {
        local.setVersion(server.getVersion());
        local.setLastUpdated(server.getLastUpdated());
        local.setMinecraftVersion(server.getMinecraftVersion());
        local.setModLoader(server.getModLoader());
        local.setModLoaderVersion(server.getModLoaderVersion());
        local.setDisplayName(server.getDisplayName());
        local.setDescription(server.getDescription());
        local.setAuthor(server.getAuthor());
        local.setModpackId(server.getModpackId());
        if (server.getFolders() != null) {
            local.setFolders(new ArrayList<>(server.getFolders()));
        }
        if (server.getFiles() != null) {
            local.setFiles(new ArrayList<>(server.getFiles()));
        }
        if (server.getChangelog() != null) {
            List<ChangelogEntry> changelog = new ArrayList<>(server.getChangelog());
            changelog.sort(Comparator.comparing(ChangelogEntry::getVersion, VersionUtil::compare).reversed());
            local.setChangelog(changelog);
        }
        local.setUpdateCount(0);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        Files.createDirectories(localRoot);
        Path manifest = localRoot.resolve("manifest.json");
        Path temp = Files.createTempFile(localRoot, "manifest", ".tmp");
        try {
            mapper.writeValue(temp.toFile(), local);
            Files.move(temp, manifest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean differs(Path localFile, RemoteFile remote, SyncReport report) {
        try {
            long localSize = Files.size(localFile);
            if (Hashing.isMultipartETag(remote.eTag())) {
                return localSize != remote.size();
            }
            if (localSize != remote.size()) {
                return true;
            }
            String localHash = Hashing.md5(localFile);
            return !localHash.equalsIgnoreCase(Hashing.normalizeETag(remote.eTag()));
        } catch (IOException e) {
            report.addFailure("Could not read " + localFile.getFileName() + ": " + rootMessage(e));
            return false;
        }
    }

    private static Set<String> trackedTargets(ModpackInfo server) {
        Set<String> targets = deletableTargets(server);
        targets.add(PROFILE_IMAGE_FOLDER);
        return targets;
    }

    private static Set<String> deletableTargets(ModpackInfo server) {
        Set<String> targets = new LinkedHashSet<>();
        if (server.getFolders() != null) {
            for (String folder : server.getFolders()) {
                if (!PROFILE_IMAGE_FOLDER.equals(folder)) {
                    targets.add(folder);
                }
            }
        }
        return targets;
    }

    private static Map<String, RemoteFile> listTrackedRemoteFiles(ModpackInfo server, IgnoreRules ignore,
                                                                  Progress progress) {
        Map<String, RemoteFile> files = new LinkedHashMap<>();
        S3Client client = B2ClientProvider.getClient();
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String rootPrefix = server.getRoot() + "/";

        for (String folder : trackedTargets(server)) {
            if (progress.isCancelled()) {
                return files;
            }
            if (isUnusableTarget(folder)) {
                continue;
            }
            progress.message("Reading " + folder);
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(rootPrefix + folder + "/")
                    .build();
            for (S3Object object : client.listObjectsV2Paginator(request).contents()) {
                String key = object.key();
                if (key.endsWith("/") || !key.startsWith(rootPrefix)) {
                    continue;
                }
                String relativePath = key.substring(rootPrefix.length());
                if (ignore.ignoresPath(relativePath)) {
                    continue;
                }
                files.put(relativePath, new RemoteFile(key, relativePath, object.eTag(), object.size()));
            }
        }

        if (server.getFiles() != null) {
            for (String file : server.getFiles()) {
                if (isUnusableTarget(file) || ignore.ignoresPath(file)) {
                    continue;
                }
                String key = rootPrefix + file;
                ListObjectsV2Request request = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(key)
                        .build();
                for (S3Object object : client.listObjectsV2Paginator(request).contents()) {
                    if (object.key().equals(key)) {
                        files.put(file, new RemoteFile(key, file, object.eTag(), object.size()));
                        break;
                    }
                }
            }
        }

        return files;
    }

    private static List<String> listTrackedLocalFiles(Path localRoot, Set<String> folders, IgnoreRules ignore) {
        List<String> found = new ArrayList<>();
        for (String folder : folders) {
            if (isUnusableTarget(folder)) {
                continue;
            }
            Path folderPath = localRoot.resolve(folder);
            if (!Files.isDirectory(folderPath)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(folderPath)) {
                walk.filter(Files::isRegularFile).forEach(path -> {
                    String relativePath = localRoot.relativize(path).toString().replace('\\', '/');
                    if (!ignore.ignoresPath(relativePath)) {
                        found.add(relativePath);
                    }
                });
            } catch (IOException e) {
                System.err.println("Could not scan " + folderPath + ": " + rootMessage(e));
            }
        }
        return found;
    }

    private static boolean isUnusableTarget(String target) {
        return target == null || target.isBlank() || target.contains("(missing locally)");
    }

    private static void download(S3Client client, String bucket, String key, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempDir = parent == null ? destination.toAbsolutePath().getParent() : parent;
        Path temp = Files.createTempFile(tempDir, ".download", ".tmp");
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try {
            try (InputStream in = client.getObject(request);
                 OutputStream out = Files.newOutputStream(temp)) {
                in.transferTo(out);
            }
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static Path localRoot(ModpackInfo local) {
        Config config = ConfigManager.getInstance().getConfig();
        return Path.of(config.getCurseforge_path(), local.getRoot());
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
