package com.nicholasburczyk.packupdater.ui.dash;

import com.nicholasburczyk.packupdater.ui.FrameClock;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Horizontal row of tiles in the style of the Xbox 360 dashboard. The selected tile sits large at
 * the left; later tiles recede to the right in perspective and the previous tile peeks in from the
 * left. Tiles are rendered once into images and drawn in vertical slices to fake the perspective.
 */
final class TileStrip extends JComponent {

    static final int TILE_W = 430;
    static final int TILE_H = 250;
    private static final int TOP = 12;
    private static final int ART = 150;
    private static final int PIXEL_ART = 128;
    private static final int RENDER_SCALE = 2;
    /** Height of a reflection relative to its tile. */
    private static final double REFLECT = 0.32;
    private static final int FLOOR_Y = TOP + TILE_H;
    private static final long ORIGIN = System.nanoTime();
    /** Perspective steps cached per tile; enough that stepping between them is invisible. */
    private static final int LEVELS = 10;
    private static final double EASE = 0.16;
    private static final double EASE_FRAME_NANOS = 16_666_667;
    private static final int RENDER_AHEAD = 5;
    private static final int KEEP_WARPS = 3;
    private static final double CUT = 24;
    private static final double SMALL_CUT = 7;
    private static final double ART_CUT = 16;
    private static final int MAX_PIPS = 14;
    private static final int GLOW_PAD = 10;
    private static final Shape GLASS_PLAIN = glassArea(false);
    private static final Shape GLASS_PACK = glassArea(true);
    private static final int PHOTO_W = 244;
    private static final int PHOTO_H = 137;
    private static final double STACK_X = TILE_W - 30 - PHOTO_W / 2.0;
    private static final double STACK_Y = 24 + PHOTO_H / 2.0;
    private static final int FACE = 20;
    private static final double[][] STACK_LAYERS = {{-9, -24, 8}, {7, 20, 3}, {-1.5, 0, 0}};
    private static final double BADGE = 52;
    private static final Shape GLASS_STACK = stackGlass();
    private static final ExecutorService WARMER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tile-warp");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    static final class Tile {
        final String title;
        final String subtitle;
        final String chip;
        final Color chipColor;
        final boolean pack;
        final String pixel;
        final boolean stack;
        BufferedImage art;
        volatile List<BufferedImage> photos;
        volatile List<BufferedImage> faces;
        private volatile BufferedImage rendered;
        private volatile BufferedImage reflection;
        private final Map<Integer, BufferedImage> warps = new ConcurrentHashMap<>();
        private final AtomicInteger generation = new AtomicInteger();

        Tile(String title, String subtitle, String chip, Color chipColor, boolean pack, String pixel, boolean stack) {
            this.title = title;
            this.subtitle = subtitle;
            this.chip = chip;
            this.chipColor = chipColor;
            this.pack = pack;
            this.pixel = pixel;
            this.stack = stack;
        }
    }

    private final int gutter;
    private final FrameClock timer = new FrameClock(16, this::step);
    private final List<Tile> tiles = new ArrayList<>();
    private final List<Shape> hits = new ArrayList<>();
    private IntConsumer onSelect = i -> {
    };
    private IntConsumer onActivate = i -> {
    };
    private int selected;
    private double pos;
    private double intro = 1;
    private int introDir = 1;
    private long introStart;
    private long shineStart;
    private BufferedImage specular;
    private BufferedImage floor;
    private int hovered = -1;
    private long lastStep;
    private final AtomicInteger warmEpoch = new AtomicInteger();
    private volatile boolean stale;

    TileStrip(int gutter) {
        this.gutter = gutter;
        setOpaque(false);
        setPreferredSize(new Dimension(900, FLOOR_Y + (int) (TILE_H * REFLECT) + 30));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = hitTest(e.getX(), e.getY());
                if (index < 0) {
                    return;
                }
                if (index == selected) {
                    onActivate.accept(index);
                } else {
                    select(index);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int index = hitTest(e.getX(), e.getY());
                if (index != hovered) {
                    hovered = index;
                    setCursor(index >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void onSelect(IntConsumer listener) {
        onSelect = listener;
    }

    void onActivate(IntConsumer listener) {
        onActivate = listener;
    }

    /** Replaces the row; {@code direction} 0 swaps in place, +-1 flies the new tiles in. */
    void setTiles(List<Tile> next, int selectedIndex, int direction) {
        tiles.clear();
        tiles.addAll(next);
        selected = Math.max(0, Math.min(tiles.size() - 1, selectedIndex));
        pos = selected;
        hovered = -1;
        if (direction != 0) {
            introDir = direction;
            intro = 0;
            introStart = System.nanoTime();
        }
        shineStart = System.nanoTime();
        animate();
        warm();
        repaint();
    }

    void artLoaded(Tile tile) {
        synchronized (tile) {
            tile.generation.incrementAndGet();
            tile.rendered = null;
            tile.reflection = null;
            tile.warps.clear();
        }
        warm();
        repaint();
    }

    int selected() {
        return selected;
    }

    int count() {
        return tiles.size();
    }

    void step(int delta) {
        int next = Math.max(0, Math.min(tiles.size() - 1, selected + delta));
        if (next != selected) {
            select(next);
        }
    }

    private void select(int index) {
        selected = index;
        shineStart = System.nanoTime();
        animate();
        warm();
        onSelect.accept(index);
    }

    private void animate() {
        if (!timer.isRunning()) {
            lastStep = System.nanoTime();
            timer.start();
        }
    }

    private void step() {
        long now = System.nanoTime();
        double frames = Math.min(4, (now - lastStep) / EASE_FRAME_NANOS);
        lastStep = now;
        pos += (selected - pos) * (1 - Math.pow(1 - EASE, frames));
        if (intro < 1) {
            intro = Math.min(1, (now - introStart) / 1e6 / 900.0);
        }
        if (Math.abs(selected - pos) < 0.001 && intro >= 1) {
            pos = selected;
            timer.stop();
        }
        repaint();
    }

    private int hitTest(int x, int y) {
        for (int i = 0; i < hits.size(); i++) {
            Shape hit = hits.get(i);
            if (hit != null && hit.contains(x, y) && i == selected) {
                return i;
            }
        }
        for (int i = 0; i < hits.size(); i++) {
            Shape hit = hits.get(i);
            if (hit != null && hit.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ painting

    private record Placement(double x, double scale, double alpha, double persp, int side) {
    }

    private Placement place(double d) {
        if (d >= 0) {
            double near = Math.min(1, d);
            double x = d <= 1 ? 455 * d : 455 + (d - 1) * 150;
            double scale = d <= 1 ? 1 - 0.18 * d : Math.max(0.5, 0.82 - (d - 1) * 0.08);
            double alpha = d <= 3 ? 1 : Math.max(0, 1 - (d - 3) * 0.6);
            return new Placement(x, scale, alpha, near, 1);
        }
        double back = -d;
        double near = Math.min(1, back);
        double scale = 1 - 0.2 * near;
        double rightEdge = back <= 1 ? TILE_W + (-12 - TILE_W) * back : -12 - (back - 1) * 140;
        double x = rightEdge - TILE_W * scale;
        double alpha = back <= 1 ? 1 - 0.25 * back : Math.max(0, 0.75 - (back - 1) * 0.9);
        return new Placement(x, scale, alpha, near, -1);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        stale = false;

        hits.clear();
        for (int i = 0; i < tiles.size(); i++) {
            hits.add(null);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> -Math.abs(i - pos)));

        // Horizontal span of every visible tile, so a nearer tile can hide the reflection of a
        // farther one behind it, just as it hides the tile itself.
        double[] left = new double[tiles.size()];
        double[] right = new double[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            Placement p = place(i - pos);
            double enter = easeOut(clamp((intro - i * 0.08) / 0.6));
            left[i] = gutter + p.x + (1 - enter) * (120 + i * 50);
            right[i] = p.alpha * enter > 0.01 ? left[i] + TILE_W * p.scale : left[i];
        }

        paintFloor(g2);
        double sinceSelect = (System.nanoTime() - shineStart) / 1e9;
        for (int i : order) {
            Tile tile = tiles.get(i);
            Placement p = place(i - pos);
            double enter = easeOut(clamp((intro - i * 0.08) / 0.6));
            double alpha = p.alpha * enter;
            if (alpha <= 0.01) {
                continue;
            }
            double w = TILE_W * p.scale;
            double h = TILE_H * p.scale;
            double x = left[i];
            // Tiles stand on an invisible glass plane seen from slightly above: the further back a
            // tile is (the smaller it gets), the closer its base sits to the horizon.
            double base = FLOOR_Y - (1 - p.scale) * TILE_H * 0.6;
            double y = base - h + (1 - enter) * 50 * introDir;
            if (tile.rendered == null) {
                if (Math.abs(i - pos) > 1.5) {
                    stale = true;
                    continue;
                }
                render(tile);
            }
            int level = shownLevel(tile, (int) Math.round(clamp(p.persp) * LEVELS), p.side);
            double persp = (double) level / LEVELS;
            BufferedImage body = cached(tile, level, p.side, false);
            // The angled reflection carries a top margin so each column can start right at its
            // tile's (sloped) bottom edge; shift it up by that margin.
            double scale = h / TILE_H;
            int margin = mirrorMargin((double) level / LEVELS);
            double from = x;
            double to = x + w;
            for (int j = 0; j < tiles.size(); j++) {
                if (j == i || Math.abs(j - pos) >= Math.abs(i - pos) || right[j] <= left[j]) {
                    continue;
                }
                if (left[j] <= from && right[j] > from) {
                    from = right[j];
                } else if (left[j] < to && right[j] >= to) {
                    to = left[j];
                }
            }
            if (to > from) {
                Shape oldClip = g2.getClip();
                g2.clip(new java.awt.geom.Rectangle2D.Double(from, 0, to - from, getHeight()));
                blit(g2, cached(tile, level, p.side, true), x, y + h - margin * scale, w,
                        (TILE_H * REFLECT + margin) * scale, alpha);
                g2.setClip(oldClip);
            }
            blit(g2, body, x, y, w, h, alpha);
            if (i == hovered && level > 0) {
                // Lift a dimmed tile under the cursor by drawing it once more over itself.
                blit(g2, body, x, y, w, h, alpha * 0.45);
            }

            Shape outline = trapezoid(x, y, w, h, persp, p.side);
            hits.set(i, outline);
            boolean isSelected = i == selected;
            double focus = 1 - Math.min(1, Math.abs(i - pos) * 2);
            if (isSelected && focus > 0.02) {
                double boost = sinceSelect < 0.9 ? 1 - sinceSelect / 0.9 : 0;
                paintGloss(g2, tile, x, y, w, h, persp * p.side, alpha * focus, boost);
                paintGlow(g2, x, y, w, h, p.scale, focus * alpha);
            }
        }

        paintPips(g2, gutter + 4, FLOOR_Y + TILE_H * REFLECT + 16);
        g2.dispose();
    }

    static Path2D plate(double x, double y, double w, double h, double cut, double small) {
        return com.nicholasburczyk.packupdater.ui.Ui.plate(x, y, w, h, cut, small);
    }

    private void paintPips(Graphics2D g2, double x, double cy) {
        int n = tiles.size();
        if (n <= 1) {
            return;
        }
        if (n > MAX_PIPS) {
            g2.setFont(Theme.font(Font.PLAIN, 12));
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString((selected + 1) + " of " + n, (float) x, (float) (cy + 4));
            return;
        }
        for (int i = 0; i < n; i++) {
            double focus = Math.max(0, 1 - Math.abs(i - pos));
            double px = x + i * 16;
            double r = 3.5 + 1.8 * focus;
            if (focus > 0.01) {
                g2.setPaint(new RadialGradientPaint(new Point2D.Double(px, cy), (float) (r * 3), new float[]{0f, 1f},
                        new Color[]{Theme.alpha(Theme.LIME, (int) (90 * focus)), Theme.alpha(Theme.LIME, 0)}));
                g2.fill(new java.awt.geom.Ellipse2D.Double(px - r * 3, cy - r * 3, r * 6, r * 6));
            }
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(px, cy + r * 0.4), (float) r, new float[]{0f, 1f},
                    new Color[]{Theme.mix(new Color(0x1E4A24), new Color(0xF2FF9A), focus),
                            Theme.mix(new Color(0x0A2410), new Color(0x5CC522), focus)}));
            java.awt.geom.Ellipse2D bead = new java.awt.geom.Ellipse2D.Double(px - r, cy - r, r * 2, r * 2);
            g2.fill(bead);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Theme.alpha(new Color(0x8FE07A), (int) (110 + 100 * focus)));
            g2.draw(bead);
        }
    }

    private void paintFloor(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight() - FLOOR_Y;
        if (w <= 0 || h <= 0) {
            return;
        }
        if (floor == null || floor.getWidth() != w || floor.getHeight() != h) {
            java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
            floor = gc != null ? gc.createCompatibleImage(w, h, java.awt.Transparency.TRANSLUCENT)
                    : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = floor.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double vx = gutter + TILE_W * 0.5;
            double vy = -260;
            g.setStroke(new BasicStroke(1f));
            for (double fx = vx - 48 * 40; fx < w + 48 * 40; fx += 48) {
                double edge = Math.abs(fx - vx) / (w * 0.9);
                int a = (int) (26 * Math.max(0, 1 - edge));
                if (a <= 0) {
                    continue;
                }
                double bx = vx + (fx - vx) * (h - vy) / -vy;
                g.setPaint(new GradientPaint(0f, 0f, new Color(90, 220, 80, a), 0f, h, new Color(90, 220, 80, 0)));
                g.draw(new java.awt.geom.Line2D.Double(fx, 0, bx, h));
            }
            for (double d = 5; d < h; d *= 1.5) {
                int a = (int) (34 * Math.pow(1 - d / h, 1.6));
                g.setPaint(new java.awt.LinearGradientPaint(0f, 0f, w, 0f, new float[]{0f, 0.25f, 0.7f, 1f},
                        new Color[]{new Color(90, 220, 80, 0), new Color(90, 220, 80, a), new Color(90, 220, 80, a / 2),
                                new Color(90, 220, 80, 0)}));
                g.draw(new java.awt.geom.Line2D.Double(0, d, w, d));
            }
            g.setPaint(new java.awt.LinearGradientPaint(0f, 0f, w, 0f, new float[]{0f, 0.22f, 0.65f, 1f},
                    new Color[]{new Color(160, 255, 110, 0), new Color(160, 255, 110, 80), new Color(160, 255, 110, 30),
                            new Color(160, 255, 110, 0)}));
            g.fill(new java.awt.geom.Rectangle2D.Double(0, 0, w, 1.2));
            g.setPaint(new GradientPaint(0f, 0f, new Color(120, 255, 90, 26), 0f, 26f, new Color(120, 255, 90, 0)));
            g.fill(new java.awt.geom.Rectangle2D.Double(0, 0, w, 26));
            g.dispose();
        }
        g2.drawImage(floor, 0, FLOOR_Y, null);
    }

    private int shownLevel(Tile tile, int level, int side) {
        if (level == 0 || isCached(tile, level, side)) {
            return level;
        }
        stale = true;
        for (int d = 1; d < LEVELS; d++) {
            if (level + d <= LEVELS && isCached(tile, level + d, side)) {
                return level + d;
            }
            if (level - d > 0 && isCached(tile, level - d, side)) {
                return level - d;
            }
        }
        if (level * 2 < LEVELS) {
            return 0;
        }
        warpFor(tile, level, side, false);
        warpFor(tile, level, side, true);
        return level;
    }

    private static boolean isCached(Tile tile, int level, int side) {
        return tile.warps.containsKey(warpKey(level, side, false)) && tile.warps.containsKey(warpKey(level, side, true));
    }

    private static BufferedImage cached(Tile tile, int level, int side, boolean mirror) {
        if (level <= 0) {
            return mirror ? tile.reflection : tile.rendered;
        }
        return tile.warps.get(warpKey(level, side, mirror));
    }

    /**
     * Precomputes off the UI thread, nearest first, what the row can show next: renders of the tiles
     * around the selection, the fully angled step of the tiles beside it, then every in-between step
     * a one-tile move needs. A newer selection abandons the pass. Far-away tiles drop their steps so
     * memory stays bounded.
     */
    private void warm() {
        int epoch = warmEpoch.incrementAndGet();
        List<Tile> row = new ArrayList<>(tiles);
        int center = selected;
        WARMER.execute(() -> warmPass(row, center, epoch));
    }

    private void warmPass(List<Tile> row, int center, int epoch) {
        for (int i = 0; i < row.size(); i++) {
            if (Math.abs(i - center) > KEEP_WARPS) {
                row.get(i).warps.clear();
            }
        }
        for (int d = 0; d <= RENDER_AHEAD; d++) {
            for (int i : new int[]{center + d, center - d}) {
                if (epoch != warmEpoch.get()) {
                    return;
                }
                if (i >= 0 && i < row.size() && row.get(i).rendered == null) {
                    render(row.get(i));
                    warmed();
                }
            }
        }
        for (int d = 1; d <= KEEP_WARPS; d++) {
            if (!warmStep(row, center + d, LEVELS, 1, epoch) || !warmStep(row, center - d, LEVELS, -1, epoch)) {
                return;
            }
        }
        for (int level = 1; level <= LEVELS; level++) {
            if (!warmStep(row, center, level, 1, epoch) || !warmStep(row, center, level, -1, epoch)) {
                return;
            }
        }
        for (int level = LEVELS - 1; level >= 1; level--) {
            if (!warmStep(row, center + 1, level, 1, epoch) || !warmStep(row, center - 1, level, -1, epoch)) {
                return;
            }
        }
    }

    private boolean warmStep(List<Tile> row, int index, int level, int side, int epoch) {
        if (epoch != warmEpoch.get()) {
            return false;
        }
        if (index < 0 || index >= row.size() || row.get(index).rendered == null) {
            return true;
        }
        Tile tile = row.get(index);
        if (!isCached(tile, level, side)) {
            warpFor(tile, level, side, false);
            warpFor(tile, level, side, true);
            warmed();
        }
        return true;
    }

    private void warmed() {
        if (stale) {
            SwingUtilities.invokeLater(() -> {
                if (stale) {
                    repaint();
                }
            });
        }
    }

    private static Shape trapezoid(double x, double y, double w, double h, double persp, int side) {
        double shrink = h * 0.14 * persp / 2;
        double leftInset = side < 0 ? shrink : 0;
        double rightInset = side > 0 ? shrink : 0;
        Path2D path = new Path2D.Double();
        path.moveTo(x, y + leftInset);
        path.lineTo(x + w, y + rightInset);
        path.lineTo(x + w, y + h - rightInset);
        path.lineTo(x, y + h - leftInset);
        path.closePath();
        return path;
    }

    /**
     * The tile (or its reflection) squeezed into a trapezoid at one of {@link #LEVELS} perspective
     * steps: vertical slices whose height tapers away from the viewer. Built once per step at the
     * tile's base size, then only scaled on the GPU, so animations never re-slice anything.
     * Thread-safe; normally called from the warm-up thread.
     */
    private static void warpFor(Tile tile, int level, int side, boolean mirror) {
        int key = warpKey(level, side, mirror);
        int generation;
        BufferedImage source;
        synchronized (tile) {
            generation = tile.generation.get();
            source = mirror ? tile.reflection : tile.rendered;
        }
        if (source == null || level <= 0 || tile.warps.containsKey(key)) {
            return;
        }
        // Receding tiles are darkened in the cached image so they stay opaque over one another.
        double dim = (side > 0 ? 0.45 : 0.5) * level / LEVELS;
        BufferedImage out = mirror
                ? warpMirror(source, (double) level / LEVELS, side, dim)
                : warp(source, (double) level / LEVELS, side, dim);
        synchronized (tile) {
            if (generation == tile.generation.get()) {
                tile.warps.put(key, out);
            }
        }
    }

    private static int warpKey(int level, int side, boolean mirror) {
        return (level * 2 + (side > 0 ? 1 : 0)) * 2 + (mirror ? 1 : 0);
    }

    /** Vertical space a tile's sloped bottom edge can rise by, at a given perspective. */
    private static int mirrorMargin(double persp) {
        return (int) Math.ceil(TILE_H * 0.14 * persp / 2);
    }

    /**
     * The reflection for an angled tile, matching the tile's taper: every column starts exactly at
     * that column's bottom edge and shrinks with it, the way a mirror plane under the tile would
     * show it.
     */
    private static BufferedImage warpMirror(BufferedImage reflection, double persp, int side, double dim) {
        return warpColumns(reflection, persp, side, dim, true);
    }

    private static BufferedImage warp(BufferedImage source, double persp, int side, double dim) {
        return warpColumns(source, persp, side, dim, false);
    }

    /**
     * Squeezes a tile (rendered at {@link #RENDER_SCALE}) into its perspective trapezoid, keeping
     * that full resolution so angled tiles stay as sharp as the selected one on HiDPI screens.
     * Every output column is resampled on its own with area averaging, and the partly covered
     * pixels at a column's top and bottom get partial alpha, so the sloped edges come out smooth
     * instead of stepped. {@code dim} darkens the result (alpha untouched) so receding tiles stay
     * opaque over one another.
     */
    private static BufferedImage warpColumns(BufferedImage source, double persp, int side, double dim,
                                             boolean mirror) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int[] src = (int[]) source.getRaster().getDataElements(0, 0, sw, sh, null);
        int margin = mirror ? mirrorMargin(persp) * RENDER_SCALE : 0;
        int oh = sh + margin;
        int[] out = new int[sw * oh];
        double shade = 1 - dim;
        double[] tops = new double[sw];
        double[] heights = new double[sw];
        int minY = oh;
        int maxY = 0;
        for (int x = 0; x < sw; x++) {
            double t = (x + 0.5) / sw;
            double taper = side > 0 ? t : 1 - t;
            double factor = 1 - 0.14 * persp * taper;
            double inset = TILE_H * RENDER_SCALE * (1 - factor) / 2;
            tops[x] = mirror ? margin - inset : inset;
            heights[x] = sh * factor;
            minY = Math.min(minY, Math.max(0, (int) Math.floor(tops[x])));
            maxY = Math.max(maxY, Math.min(oh, (int) Math.ceil(tops[x] + heights[x])));
        }
        for (int y = minY; y < maxY; y++) {
            int row = y * sw;
            for (int x = 0; x < sw; x++) {
                double top = tops[x];
                double height = heights[x];
                double a = Math.max(y, top);
                double b = Math.min(y + 1, top + height);
                if (b <= a) {
                    continue;
                }
                double k = sh / height;
                double s0 = (a - top) * k;
                double s1 = Math.min(sh, (b - top) * k);
                double sa = 0;
                double sr = 0;
                double sg = 0;
                double sb = 0;
                double total = 0;
                for (int r = (int) s0; r < s1 && r < sh; r++) {
                    double wy = Math.min(r + 1, s1) - Math.max(r, s0);
                    int p = src[r * sw + x];
                    double pa = (p >>> 24) * wy;
                    sa += pa;
                    sr += ((p >> 16) & 0xFF) * pa;
                    sg += ((p >> 8) & 0xFF) * pa;
                    sb += (p & 0xFF) * pa;
                    total += wy;
                }
                if (total <= 0 || sa <= 0) {
                    continue;
                }
                int ia = (int) Math.round(Math.min(255, sa / total * (b - a)));
                if (ia == 0) {
                    continue;
                }
                int ir = (int) Math.round(Math.min(255, sr / sa * shade));
                int ig = (int) Math.round(Math.min(255, sg / sa * shade));
                int ib = (int) Math.round(Math.min(255, sb / sa * shade));
                out[row + x] = ia << 24 | ir << 16 | ig << 8 | ib;
            }
        }
        BufferedImage image = new BufferedImage(sw, oh, BufferedImage.TYPE_INT_ARGB);
        image.getRaster().setDataElements(0, 0, sw, oh, out);
        return image;
    }

    private static void blit(Graphics2D g2, BufferedImage image, double x, double y, double w, double h,
                             double alpha) {
        if (image == null || alpha <= 0.01) {
            return;
        }
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(alpha)));
        g2.drawImage(image, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        g2.setComposite(old);
    }

    private static BufferedImage glow;

    private static void paintGlow(Graphics2D g2, double x, double y, double w, double h, double scale,
                                  double strength) {
        if (glow == null) {
            int s = RENDER_SCALE;
            glow = new BufferedImage((TILE_W + GLOW_PAD * 2) * s, (TILE_H + GLOW_PAD * 2) * s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = glow.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(s, s);
            paintGlowShape(g, GLOW_PAD, GLOW_PAD, TILE_W, TILE_H, 1, 1);
            g.dispose();
        }
        blit(g2, glow, x - GLOW_PAD * scale, y - GLOW_PAD * scale, (TILE_W + GLOW_PAD * 2) * scale,
                (TILE_H + GLOW_PAD * 2) * scale, strength);
    }

    private static void paintGlowShape(Graphics2D g2, double x, double y, double w, double h, double scale,
                                       double strength) {
        Shape outline = plate(x, y, w, h, CUT * scale, SMALL_CUT * scale);
        g2.setColor(Theme.alpha(Theme.LIME, (int) (24 * strength)));
        g2.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(outline);
        g2.setColor(Theme.alpha(Theme.LIME, (int) (48 * strength)));
        g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(outline);
        g2.setStroke(new BasicStroke(1.6f));
        g2.setColor(Theme.alpha(new Color(0xD8FFA6), (int) (210 * strength)));
        g2.draw(plate(x + 0.5, y + 0.5, w - 1, h - 1, CUT * scale, SMALL_CUT * scale));
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.alpha(new Color(0xF4FFC8), (int) (230 * strength)));
        double c = CUT * scale;
        g2.draw(new java.awt.geom.Line2D.Double(x + w - c + 3, y + h - 1, x + w - 1, y + h - c + 3));
        g2.draw(new java.awt.geom.Line2D.Double(x + 1, y + c - 3, x + c - 3, y + 1));
    }

    /**
     * The live part of the selected tile's gloss; its volume (pillow shading, glass cap, bounce
     * light) is baked into the cached image. A soft specular spot drifts across the glass cap like a
     * window reflected in a bubble, and the lit rim follows it. The artwork is cut out of both so
     * the highlight never washes it out. The light follows out-of-step waves, so its path never
     * visibly repeats, and the spot is one pre-rendered image moved around on the GPU.
     */
    private void paintGloss(Graphics2D g2, Tile tile, double x, double y, double w, double h,
                            double turn, double strength, double boost) {
        if (strength <= 0.02) {
            return;
        }
        double t = (System.nanoTime() - ORIGIN) / 1e9;
        double lx = 0.6 * Math.sin(t * 0.29) + 0.4 * Math.sin(t * 0.17 + 1.1) - turn * 0.55;
        double ly = 0.5 * Math.sin(t * 0.23 + 0.4) + 0.5 * Math.sin(t * 0.131 + 2.0);
        lx = Math.max(-1, Math.min(1, lx));
        double s = w / TILE_W;

        Graphics2D g = (Graphics2D) g2.create();
        g.translate(x, y);
        g.scale(s, h / TILE_H);
        g.clip(glass(tile));

        if (specular == null) {
            specular = buildSpecular();
        }
        double sw = TILE_W * 0.62;
        double sh = TILE_H * 0.42;
        double cx = TILE_W * (0.5 + 0.38 * lx);
        double cy = TILE_H * (0.16 + 0.08 * ly);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                (float) clamp((0.5 + 0.2 * Math.abs(lx) + 0.3 * boost) * strength)));
        g.drawImage(specular, (int) (cx - sw / 2), (int) (cy - sh / 2), (int) sw, (int) sh, null);

        g.setComposite(AlphaComposite.SrcOver);
        g.setStroke(new BasicStroke(2f));
        float fx = (float) (TILE_W * (0.5 + 0.5 * lx));
        g.setPaint(new LinearGradientPaint(fx, 0f, (float) (TILE_W - fx) + 0.01f, TILE_H,
                new float[]{0f, 0.55f}, new Color[]{new Color(225, 255, 205, (int) (150 * strength)),
                new Color(225, 255, 205, 0)}));
        g.draw(plate(1, 1, TILE_W - 2, TILE_H - 2, CUT, SMALL_CUT));
        g.dispose();
    }

    /** Soft white oval, built once, that the specular highlight is drawn from. */
    private BufferedImage buildSpecular() {
        int w = 256;
        int h = 128;
        java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
        BufferedImage image = gc != null
                ? gc.createCompatibleImage(w, h, java.awt.Transparency.TRANSLUCENT)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(1, (double) h / w);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, w / 2f), w / 2f, new float[]{0f, 0.3f, 1f},
                new Color[]{new Color(255, 255, 255, 120), new Color(235, 255, 225, 50),
                        new Color(235, 255, 225, 0)}));
        g.fillRect(0, 0, w, w);
        g.dispose();
        return image;
    }

    /** Where gloss may land, in tile coordinates: the tile minus a pack's artwork (plus a little room). */
    private static Shape glass(Tile tile) {
        return tile.stack ? GLASS_STACK : tile.pack ? GLASS_PACK : GLASS_PLAIN;
    }

    private static Shape stackGlass() {
        Area area = new Area(plate(0, 0, TILE_W, TILE_H, CUT, SMALL_CUT));
        for (int i = 0; i < STACK_LAYERS.length; i++) {
            double w = layerSize(i, PHOTO_W);
            double h = layerSize(i, PHOTO_H);
            java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform.getTranslateInstance(
                    STACK_X + STACK_LAYERS[i][1], STACK_Y + STACK_LAYERS[i][2]);
            at.rotate(Math.toRadians(STACK_LAYERS[i][0]));
            area.subtract(new Area(at.createTransformedShape(plate(-w / 2 - 1, -h / 2 - 1, w + 2, h + 2, 12, 3))));
        }
        area.subtract(new Area(plate(badgeX() - 1, badgeY() - 1, BADGE + 2, BADGE + 2, 10, 3)));
        return area;
    }

    private static double layerSize(int layer, double size) {
        return size * (layer == STACK_LAYERS.length - 1 ? 1 : 0.94);
    }

    private static double badgeX() {
        return STACK_X - PHOTO_W / 2.0 - 14;
    }

    private static double badgeY() {
        return STACK_Y + PHOTO_H / 2.0 - BADGE + 18;
    }

    private static void paintStack(Graphics2D g, Tile tile) {
        List<BufferedImage> photos = tile.photos == null ? List.of() : tile.photos;
        double[][] layers = STACK_LAYERS;
        for (int i = 0; i < layers.length; i++) {
            int index = layers.length - 1 - i;
            BufferedImage photo = index < photos.size() ? photos.get(index) : null;
            Graphics2D p = (Graphics2D) g.create();
            p.translate(STACK_X + layers[i][1], STACK_Y + layers[i][2]);
            p.rotate(Math.toRadians(layers[i][0]));
            double w = layerSize(i, PHOTO_W);
            double h = layerSize(i, PHOTO_H);
            Shape frame = plate(-w / 2, -h / 2, w, h, 12, 3);
            p.setColor(new Color(0, 0, 0, 120));
            p.fill(plate(-w / 2 + 3, -h / 2 + 6, w, h, 12, 3));
            Shape clip = p.getClip();
            p.clip(frame);
            if (photo != null) {
                double scale = Math.max(w / photo.getWidth(), h / photo.getHeight());
                double pw = photo.getWidth() * scale;
                double ph = photo.getHeight() * scale;
                p.drawImage(photo, (int) Math.round(-pw / 2), (int) Math.round(-ph / 2), (int) Math.ceil(pw),
                        (int) Math.ceil(ph), null);
                if (i < layers.length - 1) {
                    p.setColor(new Color(2, 10, 4, 90));
                    p.fill(frame);
                }
            } else {
                p.setPaint(new GradientPaint(0f, (float) (-h / 2), new Color(0x17391D), 0f, (float) (h / 2),
                        new Color(0x061108)));
                p.fill(frame);
            }
            p.setPaint(new GradientPaint(0f, (float) (-h / 2), new Color(255, 255, 255, 46), 0f,
                    (float) (-h / 2 + h * 0.3), new Color(255, 255, 255, 0)));
            p.fill(new java.awt.geom.Rectangle2D.Double(-w / 2, -h / 2, w, h * 0.3));
            p.setClip(clip);
            p.setStroke(new BasicStroke(2f));
            p.setPaint(new GradientPaint((float) (-w / 2), (float) (-h / 2), new Color(200, 255, 160, 210),
                    (float) (w * 0.2), (float) (h / 2), new Color(30, 90, 36, 230)));
            p.draw(frame);
            p.dispose();
        }
        double size = BADGE;
        double bx = badgeX();
        double by = badgeY();
        Shape badge = plate(bx, by, size, size, 10, 3);
        g.setColor(new Color(0, 0, 0, 130));
        g.fill(plate(bx + 2, by + 4, size, size, 10, 3));
        Shape clip = g.getClip();
        g.clip(badge);
        BufferedImage icon = tile.art != null ? tile.art : defaultArt();
        int side = Math.min(icon.getWidth(), icon.getHeight());
        int sx = (icon.getWidth() - side) / 2;
        int sy = (icon.getHeight() - side) / 2;
        if (side <= 96) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        g.drawImage(icon, (int) bx, (int) by, (int) (bx + size), (int) (by + size), sx, sy, sx + side, sy + side, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setClip(clip);
        g.setStroke(new BasicStroke(2f));
        g.setPaint(new GradientPaint((float) bx, (float) by, new Color(220, 255, 170, 230), (float) (bx + size * 0.4),
                (float) (by + size), new Color(40, 110, 40, 230)));
        g.draw(badge);
        g.setStroke(new BasicStroke(1f));
    }

    private static Shape glassArea(boolean pack) {
        Area area = new Area(plate(0, 0, TILE_W, TILE_H, CUT, SMALL_CUT));
        if (pack) {
            area.subtract(new Area(plate(TILE_W - 24 - ART - 4, 20, ART + 8, ART + 12, ART_CUT + 4, 5)));
        }
        return area;
    }

    // ----------------------------------------------------------------- rendering

    private static void render(Tile tile) {
        int generation = tile.generation.get();
        int s = RENDER_SCALE;
        BufferedImage image = new BufferedImage(TILE_W * s, TILE_H * s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(s, s);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        Shape shape = plate(0, 0, TILE_W, TILE_H, CUT, SMALL_CUT);
        g.clip(shape);
        g.setPaint(new LinearGradientPaint(0f, 0f, TILE_W * 0.4f, TILE_H,
                new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0x0F2A16), new Color(0x071409), new Color(0x030A05)}));
        g.fill(shape);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(TILE_W * 0.85f, TILE_H * 0.1f), TILE_W * 0.6f,
                new float[]{0f, 1f}, new Color[]{new Color(160, 255, 120, 42), new Color(160, 255, 120, 0)}));
        g.fill(shape);
        // Pillow shading: the middle bulges towards the viewer, the edges curve away into shadow.
        g.setPaint(new RadialGradientPaint(new Point2D.Float(TILE_W * 0.45f, TILE_H * 0.38f), TILE_W * 0.62f,
                new Point2D.Float(TILE_W * 0.45f, TILE_H * 0.38f), new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 30), new Color(0, 0, 0, 120)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB,
                java.awt.geom.AffineTransform.getScaleInstance(1, (double) TILE_H / TILE_W * 1.5)));
        g.fill(shape);
        // Light that went through the glass and bounces back up off the bottom edge.
        g.setPaint(new GradientPaint(0f, TILE_H, new Color(140, 255, 110, 46), 0f, TILE_H * 0.72f,
                new Color(140, 255, 110, 0)));
        g.fill(new Rectangle(0, (int) (TILE_H * 0.72), TILE_W, TILE_H));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(170, 255, 150, 9));
        for (int d = -TILE_H; d < TILE_W; d += 9) {
            g.draw(new java.awt.geom.Line2D.Double(d, TILE_H, d + TILE_H, 0));
        }

        if (tile.stack) {
            paintStack(g, tile);
        } else if (tile.pack) {
            paintArtFrame(g, tile.art, TILE_W - 24 - ART, 24);
        } else if (tile.pixel != null) {
            Icons.pixel(tile.pixel, PIXEL_ART).paintIcon(null, g, TILE_W - 30 - PIXEL_ART, 30);
        }

        // Glass cap: a bright dome over the top with a curved lower edge, kept off the artwork.
        Shape tileClip = g.getClip();
        g.clip(glass(tile));
        g.setPaint(new GradientPaint(0f, 0f, new Color(255, 255, 255, 64), 0f, TILE_H * 0.5f,
                new Color(255, 255, 255, 8)));
        g.fill(new java.awt.geom.Ellipse2D.Double(-TILE_W * 0.25, -TILE_H * 0.62, TILE_W * 1.5, TILE_H * 1.12));
        g.setClip(tileClip);

        if (tile.chip != null) {
            paintChip(g, tile.chip, tile.chipColor, 22, 20);
        }

        g.setFont(Theme.font(Font.BOLD, 24));
        String title = ellipsize(g.getFontMetrics(), tile.title, TILE_W - 44);
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(title, 23, TILE_H - 42);
        g.setColor(Color.WHITE);
        g.drawString(title, 22, TILE_H - 44);
        int subtitleX = 22;
        List<BufferedImage> faces = tile.faces;
        if (faces != null && !faces.isEmpty()) {
            int fy = TILE_H - 22 - 15;
            for (BufferedImage face : faces) {
                Shape frame = plate(subtitleX, fy, FACE, FACE, 5, 2);
                g.setColor(new Color(0, 0, 0, 120));
                g.fill(plate(subtitleX + 1, fy + 2, FACE, FACE, 5, 2));
                Shape clip = g.getClip();
                g.clip(frame);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(face, subtitleX, fy, FACE, FACE, null);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setClip(clip);
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(new Color(190, 255, 150, 200));
                g.draw(frame);
                subtitleX += FACE + 5;
            }
            subtitleX += 4;
        }
        if (tile.subtitle != null) {
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.setColor(new Color(235, 255, 235, 190));
            g.drawString(ellipsize(g.getFontMetrics(), tile.subtitle, TILE_W - 22 - subtitleX), subtitleX, TILE_H - 22);
        }

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setPaint(new GradientPaint(0f, 0f, new Color(150, 240, 110, 170), TILE_W * 0.35f, TILE_H,
                new Color(20, 70, 28, 220)));
        g.draw(plate(1.5, 1.5, TILE_W - 3, TILE_H - 3, CUT - 0.6, SMALL_CUT - 0.6));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(4, 16, 7, 200));
        g.draw(plate(3.5, 3.5, TILE_W - 7, TILE_H - 7, CUT - 1.5, SMALL_CUT - 1.5));
        // Bevel: a lit inner edge along the top that fades out down the sides.
        g.setPaint(new GradientPaint(0f, 0f, new Color(235, 255, 220, 120), 0f, TILE_H * 0.4f,
                new Color(235, 255, 220, 0)));
        g.draw(plate(4.5, 4.5, TILE_W - 9, TILE_H - 9, CUT - 2, SMALL_CUT - 2));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(200, 255, 140, 150));
        g.draw(new java.awt.geom.Line2D.Double(TILE_W - CUT + 6, TILE_H - 2, TILE_W - 2, TILE_H - CUT + 6));
        g.dispose();
        BufferedImage reflection = reflect(image);
        synchronized (tile) {
            if (generation == tile.generation.get() && tile.rendered == null) {
                tile.reflection = reflection;
                tile.rendered = image;
            }
        }
    }

    private static BufferedImage defaultArt;

    private static synchronized BufferedImage defaultArt() {
        if (defaultArt == null) {
            defaultArt = Icons.appIcon(ART * RENDER_SCALE);
        }
        return defaultArt;
    }

    private static void paintArtFrame(Graphics2D g, BufferedImage art, int x, int y) {
        Shape frame = plate(x, y, ART, ART, ART_CUT, 4);
        Shape oldClip = g.getClip();
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(plate(x + 2, y + 6, ART, ART, ART_CUT, 4));
        g.clip(frame);
        if (art != null) {
            int side = Math.min(art.getWidth(), art.getHeight());
            int sx = (art.getWidth() - side) / 2;
            int sy = (art.getHeight() - side) / 2;
            boolean pixelArt = side <= 96;
            if (pixelArt) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            }
            g.drawImage(art, x, y, x + ART, y + ART, sx, sy, sx + side, sy + side, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        } else {
            // Packs without their own image use the app icon.
            g.drawImage(defaultArt(), x, y, ART, ART, null);
        }
        // Only a thin sliver of glass along the top, so the artwork itself stays crisp.
        g.setPaint(new GradientPaint(0f, y, new Color(255, 255, 255, 34), 0f, y + ART * 0.16f,
                new Color(255, 255, 255, 0)));
        g.fill(new Rectangle(x, y, ART, (int) (ART * 0.16)));
        g.setClip(oldClip);
        g.setStroke(new BasicStroke(2.5f));
        g.setPaint(new GradientPaint(x, y, new Color(190, 255, 150, 190), x + ART * 0.4f, y + ART,
                new Color(30, 90, 36, 220)));
        g.draw(plate(x - 0.5, y - 0.5, ART + 1, ART + 1, ART_CUT + 0.3, 4.2));
        g.setStroke(new BasicStroke(1f));
    }

    private static void paintChip(Graphics2D g, String text, Color color, int x, int y) {
        g.setFont(Theme.font(Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int h = 22;
        int skew = 7;
        int w = fm.stringWidth(text) + 22 + skew;
        Path2D pill = new Path2D.Double();
        pill.moveTo(x + skew, y);
        pill.lineTo(x + w, y);
        pill.lineTo(x + w - skew, y + h);
        pill.lineTo(x, y + h);
        pill.closePath();
        Color textColor;
        if (color == null) {
            g.setColor(new Color(0, 0, 0, 100));
            g.fill(pill);
            g.setColor(new Color(160, 255, 150, 90));
            g.draw(pill);
            textColor = new Color(0xD6FFE0);
        } else {
            Color light = Theme.mix(color, Color.WHITE, 0.72);
            Color dark = Theme.mix(color, Color.BLACK, 0.25);
            g.setPaint(new LinearGradientPaint(0f, y, 0f, y + h, new float[]{0f, 0.5f, 0.51f, 1f},
                    new Color[]{light, Theme.mix(color, Color.WHITE, 0.2), dark, color}));
            g.fill(pill);
            textColor = Color.WHITE;
        }
        g.setColor(textColor);
        g.drawString(text, x + 11 + skew / 2f, y + (h - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent());
    }

    /** Mirror image of the bottom of a tile, strongest at the floor and fading out like polished glass. */
    private static BufferedImage reflect(BufferedImage source) {
        int w = source.getWidth();
        int h = Math.max(1, (int) (source.getHeight() * REFLECT));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source, 0, 0, w, h, 0, source.getHeight(), w, source.getHeight() - h, null);
        g.setComposite(AlphaComposite.DstIn);
        g.setPaint(new LinearGradientPaint(0f, 0f, 0f, h, new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(0, 0, 0, 105), new Color(0, 0, 0, 38), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return out;
    }


    private static String ellipsize(FontMetrics fm, String text, int max) {
        if (text == null || fm.stringWidth(text) <= max) {
            return text == null ? "" : text;
        }
        String cut = text;
        while (cut.length() > 1 && fm.stringWidth(cut + "...") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.trim() + "...";
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double easeOut(double t) {
        double inverse = 1 - clamp(t);
        return 1 - inverse * inverse * inverse;
    }
}
