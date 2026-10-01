package com.nicholasburczyk.packupdater.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class AdminPublisher {

    public enum ChangeType {
        ADDED("Added"),
        MODIFIED("Modified"),
        DELETED("Deleted");

        private final String label;

        ChangeType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record FileChange(String path, ChangeType type, long localSize, long serverSize) {

        public long displaySize() {
            return type == ChangeType.DELETED ? serverSize : localSize;
        }
    }

    private record RemoteFile(String eTag, long size) {
    }

    private AdminPublisher() {
    }

    public static List<FileChange> compare(ModpackInfo local, ModpackInfo server,
                                           Collection<String> folders, Collection<String> files,
                                           Progress progress) throws IOException {
        IgnoreRules ignore = IgnoreRules.from(ConfigManager.getInstance().getConfig().getIgnoredFiles());
        Path localRoot = ModpackSync.localRoot(local);
        String rootPrefix = server.getRoot() + "/";

        progress.message("Reading the server");
        Map<String, RemoteFile> remote = listRemote(server, folders, files, ignore, progress);

        progress.message("Hashing local files");
        List<FileChange> changes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        List<String> localPaths = new ArrayList<>();
        for (String folder : folders) {
            if (isUnusable(folder)) {
                continue;
            }
            Path folderPath = localRoot.resolve(folder);
            if (!Files.isDirectory(folderPath)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(folderPath)) {
                walk.filter(Files::isRegularFile).forEach(path -> {
                    String relative = localRoot.relativize(path).toString().replace('\\', '/');
                    if (!ignore.ignoresPath(relative)) {
                        localPaths.add(relative);
                    }
                });
            }
        }
        for (String file : files) {
            if (isUnusable(file) || ignore.ignoresPath(file)) {
                continue;
            }
            if (Files.isRegularFile(localRoot.resolve(file))) {
                localPaths.add(file);
            }
        }

        int index = 0;
        for (String relative : localPaths) {
            if (progress.isCancelled()) {
                return changes;
            }
            progress.step(index++, localPaths.size());
            seen.add(relative);
            Path localFile = localRoot.resolve(relative);
            RemoteFile remoteFile = remote.get(rootPrefix + relative);
            long localSize = Files.size(localFile);

            if (remoteFile == null) {
                changes.add(new FileChange(relative, ChangeType.ADDED, localSize, 0));
                continue;
            }
            boolean different;
            if (Hashing.isMultipartETag(remoteFile.eTag())) {
                different = localSize != remoteFile.size();
            } else {
                different = localSize != remoteFile.size()
                        || !Hashing.md5(localFile).equalsIgnoreCase(Hashing.normalizeETag(remoteFile.eTag()));
            }
            if (different) {
                changes.add(new FileChange(relative, ChangeType.MODIFIED, localSize, remoteFile.size()));
            }
        }

        for (Map.Entry<String, RemoteFile> entry : remote.entrySet()) {
            String relative = entry.getKey().substring(rootPrefix.length());
            if (!seen.contains(relative)) {
                changes.add(new FileChange(relative, ChangeType.DELETED, 0, entry.getValue().size()));
            }
        }

        changes.sort((a, b) -> {
            int byType = a.type().compareTo(b.type());
            return byType != 0 ? byType : a.path().compareToIgnoreCase(b.path());
        });
        return changes;
    }

    public static void publish(ModpackInfo local, ModpackInfo server, List<FileChange> staged,
                               Collection<String> folders, Collection<String> files,
                               String newVersion, String message, Progress progress) throws Exception {
        S3Client client = B2ClientProvider.getClient();
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        Path localRoot = ModpackSync.localRoot(local);
        String rootPrefix = server.getRoot() + "/";

        List<ObjectNode> operations = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        int done = 0;
        for (FileChange change : staged) {
            if (progress.isCancelled()) {
                throw new IOException("Publish cancelled before the manifest was written");
            }
            progress.step(done++, staged.size());
            String key = rootPrefix + change.path();

            switch (change.type()) {
                case ADDED, MODIFIED -> {
                    Path source = localRoot.resolve(change.path());
                    if (!Files.isRegularFile(source)) {
                        continue;
                    }
                    progress.message("Uploading " + change.path());
                    client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build(), RequestBody.fromFile(source));
                    operations.add(operation(mapper, change.type().label(), change.path()));
                }
                case DELETED -> {
                    progress.message("Removing " + change.path());
                    client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());
                    operations.add(operation(mapper, ChangeType.DELETED.label(), change.path()));
                }
            }
        }

        progress.step(staged.size(), staged.size());
        progress.message("Updating the server manifest");
        ObjectNode manifest = readServerManifest(client, bucket, server, mapper);
        applyManifestUpdate(mapper, manifest, newVersion, message, operations, folders, files);

        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(rootPrefix + "manifest.json")
                .contentType("application/json")
                .build(), RequestBody.fromString(
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest)));

        progress.message("Updating the local manifest");
        Path localManifest = localRoot.resolve("manifest.json");
        ObjectNode localNode = Files.isRegularFile(localManifest)
                ? (ObjectNode) mapper.readTree(localManifest.toFile())
                : mapper.createObjectNode();
        applyManifestUpdate(mapper, localNode, newVersion, message, operations, folders, files);
        localNode.put("root", local.getRoot());
        mapper.writerWithDefaultPrettyPrinter().writeValue(localManifest.toFile(), localNode);
    }

    public record NewModpack(String displayName, String root, String version, String message,
                             String minecraftVersion, String modLoader, String modLoaderVersion,
                             String author, String description, Path localFolder,
                             List<String> folders, List<String> files, Path instanceTemplate) {
    }

    public static void createModpack(NewModpack request, Progress progress) throws Exception {
        S3Client client = B2ClientProvider.getClient();
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String rootPrefix = request.root() + "/";
        IgnoreRules ignore = IgnoreRules.from(ConfigManager.getInstance().getConfig().getIgnoredFiles());
        ObjectMapper mapper = new ObjectMapper();

        progress.message("Collecting files");
        List<String> uploads = new ArrayList<>();
        for (String folder : request.folders()) {
            Path folderPath = request.localFolder().resolve(folder);
            if (!Files.isDirectory(folderPath)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(folderPath)) {
                walk.filter(Files::isRegularFile).forEach(path -> {
                    String relative = request.localFolder().relativize(path)
                            .toString().replace('\\', '/');
                    if (!ignore.ignoresPath(relative)) {
                        uploads.add(relative);
                    }
                });
            }
        }
        for (String file : request.files()) {
            if (!ignore.ignoresPath(file) && Files.isRegularFile(request.localFolder().resolve(file))) {
                uploads.add(file);
            }
        }

        int done = 0;
        for (String relative : uploads) {
            if (progress.isCancelled()) {
                throw new IOException("Cancelled before the manifest was written");
            }
            progress.message("Uploading " + relative);
            progress.step(done++, uploads.size());
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(rootPrefix + relative)
                            .build(),
                    RequestBody.fromFile(request.localFolder().resolve(relative)));
        }
        progress.step(uploads.size(), uploads.size());

        if (request.instanceTemplate() != null) {
            progress.message("Uploading the instance template");
            uploadInstanceTemplate(request.root(), request.instanceTemplate());
        }

        progress.message("Writing the manifest");
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("modpackId", request.root());
        manifest.put("displayName", request.displayName());
        manifest.put("author", request.author());
        manifest.put("description", request.description());
        manifest.put("minecraftVersion", request.minecraftVersion());
        manifest.put("modLoader", request.modLoader());
        manifest.put("modLoaderVersion", request.modLoaderVersion());
        manifest.put("created", Instant.now().toString());

        List<ObjectNode> operations = new ArrayList<>();
        for (String relative : uploads) {
            operations.add(operation(mapper, ChangeType.ADDED.label(), relative));
        }
        applyManifestUpdate(mapper, manifest, request.version(), request.message(),
                operations, request.folders(), request.files());

        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(rootPrefix + "manifest.json")
                .contentType("application/json")
                .build(), RequestBody.fromString(
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest)));

        progress.message("Registering the modpack");
        ObjectNode registry = readRegistry(client, bucket, mapper);
        registry.put(request.displayName(), request.root());
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key("modpacks.json")
                .contentType("application/json")
                .build(), RequestBody.fromString(
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registry)));

        progress.message("Writing the local manifest");
        ObjectNode local = manifest.deepCopy();
        local.put("root", request.localFolder().getFileName().toString());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(request.localFolder().resolve("manifest.json").toFile(), local);
    }

    public static void uploadInstanceTemplate(String root, Path instanceFile) throws Exception {
        ObjectNode template = InstanceFactory.scrubForTemplate(instanceFile);
        ObjectMapper mapper = new ObjectMapper();
        B2ClientProvider.getClient().putObject(PutObjectRequest.builder()
                .bucket(ConfigManager.getInstance().getConfig().getBucketName())
                .key(root + "/" + InstanceFactory.TEMPLATE_KEY)
                .contentType("application/json")
                .build(), RequestBody.fromString(
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(template)));
    }

    public static boolean rootExists(String root) {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(ConfigManager.getInstance().getConfig().getBucketName())
                    .prefix(root + "/")
                    .maxKeys(1)
                    .build();
            return !B2ClientProvider.getClient().listObjectsV2(request).contents().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static ObjectNode readRegistry(S3Client client, String bucket, ObjectMapper mapper) {
        try (ResponseInputStream<GetObjectResponse> response = client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key("modpacks.json")
                .build())) {
            return (ObjectNode) mapper.readTree(new String(response.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    public static String suggestNextVersion(String current) {
        String normalized = VersionUtil.normalize(current);
        String[] parts = normalized.split("\\.");
        if (parts.length == 0) {
            return "1.0.1";
        }
        try {
            int last = Integer.parseInt(parts[parts.length - 1].replaceAll("\\D", ""));
            parts[parts.length - 1] = String.valueOf(last + 1);
            return String.join(".", parts);
        } catch (NumberFormatException e) {
            return normalized + ".1";
        }
    }

    public static boolean isVersionAcceptable(String candidate, String current) {
        return candidate != null && !candidate.isBlank() && VersionUtil.isGreater(candidate, current);
    }

    private static ObjectNode operation(ObjectMapper mapper, String type, String path) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        node.put("path", path);
        return node;
    }

    private static ObjectNode readServerManifest(S3Client client, String bucket, ModpackInfo server,
                                                 ObjectMapper mapper) throws IOException {
        try (ResponseInputStream<GetObjectResponse> response = client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(server.getRoot() + "/manifest.json")
                .build())) {
            String json = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            return (ObjectNode) mapper.readTree(json);
        } catch (Exception e) {
            ObjectNode fresh = mapper.createObjectNode();
            fresh.put("modpackId", server.getModpackId() == null ? server.getRoot() : server.getModpackId());
            fresh.put("displayName", server.getDisplayName() == null ? server.getRoot() : server.getDisplayName());
            fresh.put("created", Instant.now().toString());
            return fresh;
        }
    }

    private static void applyManifestUpdate(ObjectMapper mapper, ObjectNode manifest, String newVersion,
                                            String message, List<ObjectNode> operations,
                                            Collection<String> folders, Collection<String> files) {
        manifest.put("version", newVersion);
        manifest.put("lastUpdated", Instant.now().toString());

        ArrayNode folderArray = mapper.createArrayNode();
        for (String folder : sorted(folders)) {
            folderArray.add(folder);
        }
        manifest.set("folders", folderArray);

        ArrayNode fileArray = mapper.createArrayNode();
        for (String file : sorted(files)) {
            fileArray.add(file);
        }
        manifest.set("files", fileArray);

        ArrayNode changelog = manifest.get("changelog") instanceof ArrayNode existing
                ? existing
                : mapper.createArrayNode();

        ObjectNode entry = mapper.createObjectNode();
        entry.put("version", newVersion);
        entry.put("timestamp", Instant.now().toString());
        entry.put("message", message);
        ArrayNode operationArray = mapper.createArrayNode();
        for (ObjectNode operation : operations) {
            operationArray.add(operation.deepCopy());
        }
        entry.set("operations", operationArray);

        changelog.insert(0, entry);
        manifest.set("changelog", changelog);
    }

    private static List<String> sorted(Collection<String> values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            if (!isUnusable(value)) {
                list.add(value);
            }
        }
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    private static Map<String, RemoteFile> listRemote(ModpackInfo server, Collection<String> folders,
                                                      Collection<String> files, IgnoreRules ignore,
                                                      Progress progress) {
        Map<String, RemoteFile> remote = new LinkedHashMap<>();
        S3Client client = B2ClientProvider.getClient();
        String bucket = ConfigManager.getInstance().getConfig().getBucketName();
        String rootPrefix = server.getRoot() + "/";

        for (String folder : folders) {
            if (isUnusable(folder) || progress.isCancelled()) {
                continue;
            }
            progress.message("Reading " + folder);
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(rootPrefix + folder + "/")
                    .build();
            for (S3Object object : client.listObjectsV2Paginator(request).contents()) {
                String key = object.key();
                if (key.endsWith("/")) {
                    continue;
                }
                String relative = key.substring(rootPrefix.length());
                if (!ignore.ignoresPath(relative)) {
                    remote.put(key, new RemoteFile(object.eTag(), object.size()));
                }
            }
        }

        for (String file : files) {
            if (isUnusable(file) || ignore.ignoresPath(file)) {
                continue;
            }
            String key = rootPrefix + file;
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(key)
                    .build();
            for (S3Object object : client.listObjectsV2Paginator(request).contents()) {
                if (object.key().equals(key)) {
                    remote.put(key, new RemoteFile(object.eTag(), object.size()));
                    break;
                }
            }
        }

        return remote;
    }

    private static boolean isUnusable(String value) {
        return value == null || value.isBlank() || value.contains("(missing locally)");
    }
}
