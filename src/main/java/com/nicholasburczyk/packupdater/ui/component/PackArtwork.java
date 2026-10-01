package com.nicholasburczyk.packupdater.ui.component;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.ui.Theme;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PackArtwork {

    private static final String FOLDER = "profileImage";
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private PackArtwork() {
    }

    public static Icon lookup(ModpackInfo pack, boolean local, int size) {
        String key = (local ? "local:" : "server:") + pack.getRoot() + ":" + size;
        return CACHE.get(key);
    }

    public static Icon load(ModpackInfo pack, boolean local, int size) {
        String key = (local ? "local:" : "server:") + pack.getRoot() + ":" + size;
        Icon cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = local ? readLocal(pack) : readRemote(pack);
        if (image == null) {
            return null;
        }
        Icon icon = new ImageIcon(round(image, size));
        CACHE.put(key, icon);
        return icon;
    }

    public static void invalidate() {
        CACHE.clear();
    }

    private static BufferedImage readLocal(ModpackInfo pack) {
        String instances = ConfigManager.getInstance().getConfig().getCurseforge_path();
        if (instances == null || instances.isBlank() || pack.getRoot() == null) {
            return null;
        }
        Path dir = Path.of(instances, pack.getRoot(), FOLDER);
        File[] files = dir.toFile().listFiles(file -> file.isFile() && isImage(file.getName()));
        if (files == null || files.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(files[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage readRemote(ModpackInfo pack) {
        try {
            String bucket = ConfigManager.getInstance().getConfig().getBucketName();
            String prefix = pack.getRoot() + "/" + FOLDER + "/";
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();
            String imageKey = null;
            for (S3Object object : B2ClientProvider.getClient().listObjectsV2Paginator(request).contents()) {
                if (isImage(object.key())) {
                    imageKey = object.key();
                    break;
                }
            }
            if (imageKey == null) {
                return null;
            }
            try (InputStream stream = B2ClientProvider.getClient().getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(imageKey)
                    .build())) {
                return ImageIO.read(stream);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static BufferedImage round(BufferedImage source, int size) {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, size, size, size * 0.22, size * 0.22));
        g.setColor(Theme.SURFACE_HOVER);
        g.fillRect(0, 0, size, size);

        int sourceSize = Math.min(source.getWidth(), source.getHeight());
        int sx = (source.getWidth() - sourceSize) / 2;
        int sy = (source.getHeight() - sourceSize) / 2;
        g.drawImage(source, 0, 0, size, size, sx, sy, sx + sourceSize, sy + sourceSize, null);
        g.dispose();
        return output;
    }
}
