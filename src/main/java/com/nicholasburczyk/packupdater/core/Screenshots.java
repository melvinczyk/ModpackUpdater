package com.nicholasburczyk.packupdater.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class Screenshots {

    public record Shot(String modpackId, String player, long taken, String hash) {

        public String key() {
            return folder(modpackId) + player + "/" + taken + "-" + hash + ".jpg";
        }

        public String thumbKey() {
            return folder(modpackId) + player + "/" + taken + "-" + hash + THUMB_SUFFIX;
        }

        public String deletedKey() {
            return folder(modpackId) + player + "/" + taken + "-" + hash + DELETED_SUFFIX;
        }
    }

    private record Listing(List<Shot> live, Set<String> deleted) {
    }

    private static final String ROOT = "screenshots/";
    private static final String THUMB_SUFFIX = ".t.jpg";
    private static final String DELETED_SUFFIX = ".deleted";
    private static final int FULL_MAX = 1920;
    private static final int THUMB_MAX = 480;
    private static final float FULL_QUALITY = 0.85f;
    private static final float THUMB_QUALITY = 0.78f;
    private static final int HASH_LENGTH = 16;
    private static final Pattern USER = Pattern.compile("Setting user: ([A-Za-z0-9_]{2,16})");
    private static final Pattern OBJECT = Pattern.compile("([A-Za-z0-9_]{2,16})/(\\d{1,15})-([0-9a-f]{16})(\\.jpg|\\.deleted)");
    private static final DateTimeFormatter MINECRAFT_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DOWNLOAD_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");
    private static final Map<String, byte[]> THUMBS = memory(300);
    private static final Map<String, byte[]> FULLS = memory(8);
    private static volatile String status = "";

    private Screenshots() {
    }

    public static String status() {
        return status;
    }

    public static boolean isPlayerName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{2,16}");
    }

    public static String detectPlayer(Path instancesRoot) {
        if (instancesRoot == null || !Files.isDirectory(instancesRoot)) {
            return null;
        }
        List<Path> logs = new ArrayList<>();
        try (Stream<Path> folders = Files.list(instancesRoot)) {
            folders.map(folder -> folder.resolve("logs").resolve("latest.log"))
                    .filter(Files::isRegularFile)
                    .forEach(logs::add);
        } catch (IOException e) {
            return null;
        }
        logs.sort(Comparator.comparingLong(Screenshots::modified).reversed());
        for (Path log : logs) {
            String found = null;
            try (Stream<String> lines = Files.lines(log, StandardCharsets.ISO_8859_1)) {
                for (String line : (Iterable<String>) lines::iterator) {
                    Matcher matcher = USER.matcher(line);
                    if (matcher.find()) {
                        found = matcher.group(1);
                    }
                }
            } catch (Exception ignored) {
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static int upload(List<ModpackInfo> locals, String player) {
        if (!isPlayerName(player)) {
            return 0;
        }
        Set<String> sent = loadSent();
        int uploaded = 0;
        try {
            for (ModpackInfo local : locals) {
                String id = safe(local.getModpackId());
                List<Path> pending = new ArrayList<>();
                for (Path image : images(ModpackSync.localRoot(local).resolve("screenshots"))) {
                    if (!sent.contains(sentKey(id, image))) {
                        pending.add(image);
                    }
                }
                if (pending.isEmpty()) {
                    continue;
                }
                Listing listing = scan(local.getModpackId());
                Set<String> remote = new HashSet<>();
                for (Shot shot : listing.live()) {
                    if (shot.player().equals(player)) {
                        remote.add(shot.hash());
                    }
                }
                for (String gone : listing.deleted()) {
                    if (gone.startsWith(player + "/")) {
                        remote.add(gone.substring(player.length() + 1));
                    }
                }
                for (int i = 0; i < pending.size(); i++) {
                    Path image = pending.get(i);
                    status = "Sharing screenshots from " + local.getDisplayName() + " (" + (i + 1) + " of "
                            + pending.size() + ")";
                    try {
                        String hash = Hashing.md5(image).substring(0, HASH_LENGTH);
                        if (!remote.contains(hash)) {
                            Shot shot = new Shot(local.getModpackId(), player, takenAt(image), hash);
                            put(shot, image);
                            remote.add(hash);
                            uploaded++;
                        }
                        sent.add(sentKey(id, image));
                        saveSent(sent);
                    } catch (Exception e) {
                        System.err.println("Could not share " + image + ": " + e.getMessage());
                    }
                }
            }
        } finally {
            status = "";
        }
        return uploaded;
    }

    public static Map<String, List<Shot>> listAll(Collection<String> modpackIds) {
        Map<String, String> bySafe = new HashMap<>();
        Map<String, List<Shot>> live = new LinkedHashMap<>();
        Map<String, Set<String>> deleted = new HashMap<>();
        for (String id : modpackIds) {
            bySafe.put(safe(id), id);
            live.put(id, new ArrayList<>());
            deleted.put(id, new HashSet<>());
        }
        S3Client client = B2ClientProvider.getClient();
        for (S3Object object : client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                .bucket(bucket()).prefix(ROOT).build()).contents()) {
            String rest = object.key().substring(ROOT.length());
            int slash = rest.indexOf('/');
            String id = slash > 0 ? bySafe.get(rest.substring(0, slash)) : null;
            if (id != null) {
                parse(id, rest.substring(slash + 1), live.get(id), deleted.get(id));
            }
        }
        for (String id : modpackIds) {
            tidy(live.get(id), deleted.get(id));
        }
        return live;
    }

    private static Listing scan(String modpackId) {
        String prefix = folder(modpackId);
        List<Shot> shots = new ArrayList<>();
        Set<String> deleted = new HashSet<>();
        S3Client client = B2ClientProvider.getClient();
        for (S3Object object : client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                .bucket(bucket()).prefix(prefix).build()).contents()) {
            parse(modpackId, object.key().substring(prefix.length()), shots, deleted);
        }
        tidy(shots, deleted);
        return new Listing(shots, deleted);
    }

    private static void parse(String modpackId, String name, List<Shot> shots, Set<String> deleted) {
        Matcher matcher = OBJECT.matcher(name);
        if (!matcher.matches()) {
            return;
        }
        if (matcher.group(4).equals(DELETED_SUFFIX)) {
            deleted.add(matcher.group(1) + "/" + matcher.group(3));
        } else {
            shots.add(new Shot(modpackId, matcher.group(1), Long.parseLong(matcher.group(2)), matcher.group(3)));
        }
    }

    private static void tidy(List<Shot> shots, Set<String> deleted) {
        shots.removeIf(shot -> deleted.contains(shot.player() + "/" + shot.hash()));
        shots.sort(Comparator.comparingLong(Shot::taken).reversed());
    }

    public static BufferedImage thumbnail(Shot shot) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(fetch(THUMBS, shot.thumbKey())));
    }

    public static BufferedImage full(Shot shot) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(fetch(FULLS, shot.key())));
    }

    public static void prefetch(Shot shot) {
        try {
            fetch(FULLS, shot.key());
        } catch (Exception ignored) {
        }
    }

    public static Path download(Shot shot, String packName) throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), "Pictures", "GroidPack", folderName(packName));
        Files.createDirectories(dir);
        String stamp = DOWNLOAD_NAME.format(Instant.ofEpochMilli(shot.taken()).atZone(ZoneId.systemDefault()));
        Path file = dir.resolve(shot.player() + " " + stamp + ".jpg");
        Files.write(file, fetch(FULLS, shot.key()));
        return file;
    }

    public static void clearOldCache() {
        for (String name : List.of("screenshots", "faces")) {
            Path dir = AppPaths.dataDir().resolve("cache").resolve(name);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
            }
        }
    }

    public static void delete(Shot shot) {
        S3Client client = B2ClientProvider.getClient();
        client.putObject(PutObjectRequest.builder().bucket(bucket()).key(shot.deletedKey()).contentType("text/plain")
                .build(), RequestBody.fromBytes(new byte[0]));
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(shot.key()).build());
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(shot.thumbKey()).build());
    }

    public static void forEachPlayer(List<Shot> shots, Consumer<String> action) {
        Set<String> seen = new HashSet<>();
        for (Shot shot : shots) {
            if (seen.add(shot.player())) {
                action.accept(shot.player());
            }
        }
    }

    private static void put(Shot shot, Path image) throws IOException {
        BufferedImage source = ImageIO.read(image.toFile());
        if (source == null) {
            throw new IOException("not an image");
        }
        byte[] full = jpeg(fit(source, FULL_MAX), FULL_QUALITY);
        byte[] thumb = jpeg(fit(source, THUMB_MAX), THUMB_QUALITY);
        S3Client client = B2ClientProvider.getClient();
        client.putObject(PutObjectRequest.builder().bucket(bucket()).key(shot.key()).contentType("image/jpeg").build(),
                RequestBody.fromBytes(full));
        client.putObject(PutObjectRequest.builder().bucket(bucket()).key(shot.thumbKey()).contentType("image/jpeg")
                .build(), RequestBody.fromBytes(thumb));
    }

    private static byte[] fetch(Map<String, byte[]> memory, String key) throws IOException {
        byte[] known = memory.get(key);
        if (known != null) {
            return known;
        }
        try (InputStream in = B2ClientProvider.getClient().getObject(GetObjectRequest.builder()
                .bucket(bucket()).key(key).build())) {
            byte[] bytes = in.readAllBytes();
            memory.put(key, bytes);
            return bytes;
        }
    }

    private static Map<String, byte[]> memory(int max) {
        return java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > max;
            }
        });
    }

    private static String folderName(String raw) {
        StringBuilder cleaned = new StringBuilder();
        for (char c : (raw == null ? "" : raw).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '(' || c == ')') {
                cleaned.append(c);
            }
        }
        String name = cleaned.toString().trim();
        return name.isEmpty() ? "Pack" : name;
    }

    static BufferedImage fit(BufferedImage source, int max) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1, (double) max / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage current = source;
        int cw = w;
        int ch = h;
        while (cw / 2 >= tw && ch / 2 >= th) {
            cw /= 2;
            ch /= 2;
            current = draw(current, cw, ch, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, tw, th, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage draw(BufferedImage source, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static List<Path> images(Path folder) {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return found;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).filter(file -> {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
            }).forEach(found::add);
        } catch (IOException ignored) {
        }
        found.sort(Comparator.comparing(Path::toString));
        return found;
    }

    private static long takenAt(Path image) {
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        try {
            String base = stem.length() >= 19 ? stem.substring(0, 19) : stem;
            return LocalDateTime.parse(base, MINECRAFT_NAME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return modified(image);
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String sentKey(String id, Path image) {
        long size;
        try {
            size = Files.size(image);
        } catch (IOException e) {
            size = -1;
        }
        return id + "|" + image.getFileName() + "|" + size + "|" + modified(image);
    }

    private static Path sentFile() {
        return AppPaths.dataDir().resolve("screenshots-shared.json");
    }

    private static Set<String> loadSent() {
        Path file = sentFile();
        if (!Files.isRegularFile(file)) {
            return new HashSet<>();
        }
        try {
            return new HashSet<>(MAPPER.readValue(file.toFile(), new TypeReference<List<String>>() {
            }));
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    private static void saveSent(Set<String> sent) {
        try {
            MAPPER.writeValue(sentFile().toFile(), new ArrayList<>(sent));
        } catch (IOException e) {
            System.err.println("Could not remember shared screenshots: " + e.getMessage());
        }
    }

    private static String folder(String modpackId) {
        return ROOT + safe(modpackId) + "/";
    }

    private static String safe(String raw) {
        return raw == null ? "pack" : raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String bucket() {
        return ConfigManager.getInstance().getConfig().getBucketName();
    }
}
