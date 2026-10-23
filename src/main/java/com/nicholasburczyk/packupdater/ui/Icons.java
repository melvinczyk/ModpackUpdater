package com.nicholasburczyk.packupdater.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Icons {

    public static final String REFRESH = "refresh-cw";
    public static final String RECHECK = "rotate-ccw";
    public static final String DOWNLOAD = "download";
    public static final String UPLOAD = "upload";
    public static final String FOLDER = "folder-open";
    public static final String LIST = "list";
    public static final String SETTINGS = "settings";
    public static final String HELP = "circle-help";
    public static final String ADMIN = "shield-check";
    public static final String SEARCH = "search";
    public static final String CHECK = "check";
    public static final String CLOSE = "x";
    public static final String CLOUD = "cloud";
    public static final String PACKAGE = "package";
    public static final String WARNING = "triangle-alert";
    public static final String BACK = "arrow-left";
    public static final String CHEVRON = "chevron-right";
    public static final String CHEVRON_DOWN = "chevron-down";
    public static final String FOLDER_NEW = "folder-plus";
    public static final String MAGIC = "sparkles";
    public static final String TRASH = "trash-2";
    public static final String FILE_NEW = "file-plus";
    public static final String FILE_DIFF = "file-diff";
    public static final String INFO = "info";
    public static final String OK_CIRCLE = "circle-check";
    public static final String WRENCH = "wrench";

    // Names that only exist in the pixel sheet.
    public static final String UP = "up";
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String DOWN = "down";
    public static final String WIFI = "wifi";
    public static final String USER = "user";
    public static final String HEART = "heart";
    public static final String HOME = "home";
    public static final String SAVE = "save";
    public static final String CHAT = "chat";
    public static final String TARGET = "target";
    public static final String PLAY = "play";
    public static final String FOCUS = "focus";
    public static final String SKIP_BACK = "skip-back";
    public static final String SKIP_NEXT = "skip-next";
    public static final String SPEAKER_ON = "speaker-on";
    public static final String SPEAKER_OFF = "speaker-off";

    private static final String PATH = "com/nicholasburczyk/packupdater/icons/";
    private static final String SPRITES = "/com/nicholasburczyk/packupdater/pixel/green-sprites.png";
    private static final int CELL = 16;
    private static final int STEP = 17;
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    /** Glossy ("on") cells of the green pixel sheet: {row, column}. */
    private static final Map<String, int[]> PIXEL = Map.ofEntries(
            Map.entry(USER, new int[]{0, 1}), Map.entry(ADMIN, new int[]{0, 1}),
            Map.entry(HEART, new int[]{0, 3}),
            Map.entry(CHECK, new int[]{0, 5}), Map.entry(OK_CIRCLE, new int[]{0, 5}),
            Map.entry(CLOSE, new int[]{0, 7}),
            Map.entry(SETTINGS, new int[]{0, 9}), Map.entry(WRENCH, new int[]{0, 9}),
            Map.entry(TARGET, new int[]{1, 3}), Map.entry(RECHECK, new int[]{1, 3}),
            Map.entry(REFRESH, new int[]{1, 3}), Map.entry(SEARCH, new int[]{1, 3}),
            Map.entry(SAVE, new int[]{1, 7}),
            Map.entry(FOLDER, new int[]{1, 9}), Map.entry(FOLDER_NEW, new int[]{1, 9}),
            Map.entry(UP, new int[]{2, 1}), Map.entry(UPLOAD, new int[]{2, 1}),
            Map.entry(LEFT, new int[]{2, 3}), Map.entry(BACK, new int[]{2, 3}),
            Map.entry(RIGHT, new int[]{2, 5}), Map.entry(CHEVRON, new int[]{2, 5}),
            Map.entry(DOWN, new int[]{2, 7}), Map.entry(DOWNLOAD, new int[]{2, 7}),
            Map.entry(CHEVRON_DOWN, new int[]{2, 7}), Map.entry(MAGIC, new int[]{2, 7}),
            Map.entry(HOME, new int[]{2, 9}),
            Map.entry(FOCUS, new int[]{1, 5}),
            Map.entry(PLAY, new int[]{3, 1}),
            Map.entry(SKIP_BACK, new int[]{3, 5}), Map.entry(SKIP_NEXT, new int[]{3, 7}),
            Map.entry(SPEAKER_OFF, new int[]{4, 0}), Map.entry(SPEAKER_ON, new int[]{4, 2}),
            Map.entry(CHAT, new int[]{3, 9}), Map.entry(HELP, new int[]{3, 9}), Map.entry(INFO, new int[]{3, 9}),
            Map.entry(LIST, new int[]{4, 4}),
            Map.entry(WIFI, new int[]{4, 8}), Map.entry(CLOUD, new int[]{4, 8})
    );
    private static BufferedImage sheet;

    private Icons() {
    }

    /**
     * Pixel-sheet icons keep their own colours (the colour only matters for the faded disabled
     * look); everything else is a tinted SVG. Pixel sizes snap to multiples of 8 so pixels stay square.
     */
    public static Icon of(String name, int size, Color color) {
        String key = name + ':' + size + ':' + color.getRGB();
        return CACHE.computeIfAbsent(key, ignored -> {
            int[] cell = PIXEL.get(name);
            BufferedImage sprites = cell == null ? null : sheet();
            if (sprites != null) {
                return new PixelIcon(sprites.getSubimage(cell[1] * STEP, cell[0] * STEP, CELL, CELL),
                        Math.max(16, Math.round(size / 8f) * 8), color.equals(Theme.TEXT_FAINT));
            }
            return build(name, size, color);
        });
    }

    public static Icon pixel(String name, int size) {
        return of(name, size, Theme.TEXT);
    }

    private static synchronized BufferedImage sheet() {
        if (sheet == null) {
            try (InputStream stream = Icons.class.getResourceAsStream(SPRITES)) {
                sheet = stream == null ? null : ImageIO.read(stream);
            } catch (Exception e) {
                sheet = null;
            }
        }
        return sheet;
    }

    public static Icon of(String name, int size) {
        return of(name, size, Theme.TEXT_DIM);
    }

    private static Icon build(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon(PATH + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> color));
        return icon;
    }

    /**
     * App and Dock icon, also the default image for packs without artwork: the original
     * package-on-a-rounded-tile design, restyled as a glossy dark-green glass tile with a lime rim,
     * a glowing glyph and a specular highlight.
     */
    public static BufferedImage appIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double arc = size * 0.28;
        double inset = Math.max(1, size / 32.0);
        java.awt.geom.RoundRectangle2D tile = new java.awt.geom.RoundRectangle2D.Double(
                inset, inset, size - inset * 2, size - inset * 2, arc, arc);

        g.setPaint(new java.awt.GradientPaint(0f, 0f, new Color(0x16381F), 0f, size, new Color(0x020804)));
        g.fill(tile);
        g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(size / 2f, size * 1.02f), size * 0.62f,
                new float[]{0f, 1f}, new Color[]{new Color(110, 255, 60, 120), new Color(110, 255, 60, 0)}));
        g.fill(tile);

        int inner = (int) Math.round(size * 0.56);
        int at = (size - inner) / 2;
        if (size >= 32) {
            int halo = (int) Math.round(inner * 1.06);
            of(PACKAGE, halo, Theme.alpha(Theme.LIME, 55)).paintIcon(null, g, (size - halo) / 2, (size - halo) / 2);
        }
        of(PACKAGE, inner, new Color(0xD8FFB8)).paintIcon(null, g, at, at);

        java.awt.Shape old = g.getClip();
        g.clip(tile);
        g.setPaint(new java.awt.GradientPaint(0f, (float) inset, new Color(255, 255, 255, 120),
                0f, size * 0.5f, new Color(255, 255, 255, 8)));
        g.fill(new java.awt.geom.Ellipse2D.Double(-size * 0.15, -size * 0.55, size * 1.3, size * 1.02));
        g.setClip(old);

        g.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 64f)));
        g.setPaint(new java.awt.GradientPaint(0f, 0f, new Color(0xD4FFAA), 0f, size, new Color(0x2E8F1C)));
        g.draw(tile);
        g.dispose();
        return image;
    }

    private static final class PixelIcon implements Icon {

        private final BufferedImage image;
        private final BufferedImage shadow;
        private final int size;
        private final boolean faded;

        PixelIcon(BufferedImage image, int size, boolean faded) {
            this.image = image;
            this.shadow = silhouette(image);
            this.size = size;
            this.faded = faded;
        }

        private static BufferedImage silhouette(BufferedImage source) {
            BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int alpha = source.getRGB(x, y) >>> 24;
                    out.setRGB(x, y, (alpha * 170 / 255) << 24 | 0x041A08);
                }
            }
            return out;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (faded) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            }
            int offset = Math.max(1, size / 16);
            g2.drawImage(shadow, x + offset, y + offset, size, size, null);
            g2.drawImage(image, x, y, size, size, null);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
