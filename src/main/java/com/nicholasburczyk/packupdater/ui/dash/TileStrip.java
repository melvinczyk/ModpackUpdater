package com.nicholasburczyk.packupdater.ui.dash;

import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;

import javax.swing.JComponent;
import javax.swing.Timer;
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
import java.awt.geom.RoundRectangle2D;
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
        BufferedImage art;
        private volatile BufferedImage rendered;
        private volatile BufferedImage reflection;
        private final Map<Integer, BufferedImage> warps = new ConcurrentHashMap<>();
        private final AtomicInteger generation = new AtomicInteger();

        Tile(String title, String subtitle, String chip, Color chipColor, boolean pack, String pixel) {
            this.title = title;
            this.subtitle = subtitle;
            this.chip = chip;
            this.chipColor = chipColor;
            this.pack = pack;
            this.pixel = pixel;
        }
    }

    private final int gutter;
    private final Timer timer = new Timer(16, e -> step());
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
    private int hovered = -1;
    private boolean warmed;

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
        warmed = false;
        timer.start();
        repaint();
    }

    void artLoaded(Tile tile) {
        tile.generation.incrementAndGet();
        tile.rendered = null;
        tile.reflection = null;
        tile.warps.clear();
        warmed = false;
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
        timer.start();
        onSelect.accept(index);
    }

    private void step() {
        pos += (selected - pos) * 0.16;
        if (intro < 1) {
            intro = Math.min(1, (System.nanoTime() - introStart) / 1e6 / 900.0);
        }
        if (Math.abs(selected - pos) < 0.001 && intro >= 1) {
            pos = selected;
            timer.stop();
            warmed = false;
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
            double persp = p.persp;

            ensureRendered(tile);
            int level = (int) Math.round(clamp(persp) * LEVELS);
            persp = (double) level / LEVELS;
            BufferedImage body = warpFor(tile, level, p.side, false);
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
                blit(g2, warpFor(tile, level, p.side, true), x, y + h - margin * scale, w,
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
                paintGlow(g2, x, y, w, h, focus * alpha);
            }
        }

        if (!tiles.isEmpty()) {
            g2.setFont(Theme.font(Font.PLAIN, 12));
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString((selected + 1) + " of " + tiles.size(), gutter, FLOOR_Y + (int) (TILE_H * REFLECT) + 20);
        }
        g2.dispose();
        if (!warmed && !timer.isRunning()) {
            warmed = true;
            warmNeighbours();
        }
    }

    /**
     * Precomputes, off the UI thread, every perspective step the next move can need: the tiles on
     * either side of the selection plus the selected one in both directions. Far-away tiles drop
     * their in-between steps so memory stays bounded.
     */
    private void warmNeighbours() {
        List<Runnable> jobs = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            int d = i - selected;
            if (Math.abs(d) > 2) {
                tile.warps.clear();
                continue;
            }
            if (tile.rendered == null || Math.abs(d) > 1) {
                continue;
            }
            int[] sides = d == 0 ? new int[]{1, -1} : new int[]{d > 0 ? 1 : -1};
            for (int side : sides) {
                for (int level = 1; level <= LEVELS; level++) {
                    int lv = level;
                    jobs.add(() -> {
                        warpFor(tile, lv, side, false);
                        warpFor(tile, lv, side, true);
                    });
                }
            }
        }
        if (!jobs.isEmpty()) {
            WARMER.execute(() -> jobs.forEach(Runnable::run));
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
     * Thread-safe; also called from the warm-up thread.
     */
    private static BufferedImage warpFor(Tile tile, int level, int side, boolean mirror) {
        if (tile.rendered == null || level <= 0) {
            return mirror ? tile.reflection : tile.rendered;
        }
        int key = (level * 2 + (side > 0 ? 1 : 0)) * 2 + (mirror ? 1 : 0);
        BufferedImage cached = tile.warps.get(key);
        if (cached != null) {
            return cached;
        }
        int generation = tile.generation.get();
        BufferedImage out;
        if (mirror) {
            out = warpMirror(tile.reflection, (double) level / LEVELS, side, (side > 0 ? 0.45 : 0.5) * level / LEVELS);
        } else {
            // Receding tiles are darkened in the cached image so they stay opaque over one another.
            double dim = (side > 0 ? 0.45 : 0.5) * level / LEVELS;
            out = warp(tile.rendered, (double) level / LEVELS, side, dim);
        }
        if (generation == tile.generation.get()) {
            tile.warps.put(key, out);
        }
        return out;
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
        int[] src = source.getRGB(0, 0, sw, sh, null, 0, sw);
        int bw = sw;
        int bh = sh;
        int margin = mirror ? mirrorMargin(persp) * RENDER_SCALE : 0;
        int oh = bh + margin;
        int[] out = new int[bw * oh];
        double shade = 1 - dim;
        for (int x = 0; x < bw; x++) {
            double t = (x + 0.5) / bw;
            double taper = side > 0 ? t : 1 - t;
            double factor = 1 - 0.14 * persp * taper;
            double inset = TILE_H * RENDER_SCALE * (1 - factor) / 2;
            double top = mirror ? margin - inset : inset;
            double height = bh * factor;
            double k = sh / height;
            int y0 = Math.max(0, (int) Math.floor(top));
            int y1 = Math.min(oh, (int) Math.ceil(top + height));
            for (int y = y0; y < y1; y++) {
                double a = Math.max(y, top);
                double b = Math.min(y + 1, top + height);
                if (b <= a) {
                    continue;
                }
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
                double alpha = sa / total * (b - a);
                int ia = (int) Math.round(Math.min(255, alpha));
                if (ia == 0) {
                    continue;
                }
                int ir = (int) Math.round(Math.min(255, sr / sa * shade));
                int ig = (int) Math.round(Math.min(255, sg / sa * shade));
                int ib = (int) Math.round(Math.min(255, sb / sa * shade));
                out[y * bw + x] = ia << 24 | ir << 16 | ig << 8 | ib;
            }
        }
        // setRGB copies the pixels in, so the image stays eligible for GPU caching.
        BufferedImage image = new BufferedImage(bw, oh, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, bw, oh, out, 0, bw);
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

    private static void paintGlow(Graphics2D g2, double x, double y, double w, double h, double strength) {
        g2.setColor(Theme.alpha(Theme.LIME, (int) (26 * strength)));
        g2.setStroke(new BasicStroke(9f));
        g2.draw(new RoundRectangle2D.Double(x, y, w, h, 18, 18));
        g2.setColor(Theme.alpha(Theme.LIME, (int) (44 * strength)));
        g2.setStroke(new BasicStroke(4f));
        g2.draw(new RoundRectangle2D.Double(x, y, w, h, 18, 18));
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(Theme.alpha(new Color(0xC8FF96), (int) (190 * strength)));
        g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, 18, 18));
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
        g.clip(glassArea(tile));

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
        g.draw(new RoundRectangle2D.Double(1, 1, TILE_W - 2, TILE_H - 2, 18, 18));
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
    private static Shape glassArea(Tile tile) {
        Area area = new Area(new RoundRectangle2D.Double(0, 0, TILE_W, TILE_H, 18, 18));
        if (tile.pack) {
            area.subtract(new Area(new RoundRectangle2D.Double(TILE_W - 24 - ART - 4, 20, ART + 8, ART + 12, 28, 28)));
        }
        return area;
    }

    // ----------------------------------------------------------------- rendering

    private void ensureRendered(Tile tile) {
        if (tile.rendered != null) {
            return;
        }
        int s = RENDER_SCALE;
        BufferedImage image = new BufferedImage(TILE_W * s, TILE_H * s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(s, s);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, TILE_W, TILE_H, 18, 18);
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

        if (tile.pack) {
            paintArtFrame(g, tile.art, TILE_W - 24 - ART, 24);
        } else if (tile.pixel != null) {
            Icons.pixel(tile.pixel, PIXEL_ART).paintIcon(this, g, TILE_W - 30 - PIXEL_ART, 30);
        }

        // Glass cap: a bright dome over the top with a curved lower edge, kept off the artwork.
        Shape tileClip = g.getClip();
        g.clip(glassArea(tile));
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
        if (tile.subtitle != null) {
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.setColor(new Color(235, 255, 235, 190));
            g.drawString(ellipsize(g.getFontMetrics(), tile.subtitle, TILE_W - 44), 22, TILE_H - 22);
        }

        g.setClip(null);
        g.setColor(new Color(170, 255, 150, 58));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(0.5, 0.5, TILE_W - 1, TILE_H - 1, 18, 18));
        // Bevel: a lit inner edge along the top that fades out down the sides.
        g.setPaint(new GradientPaint(0f, 0f, new Color(235, 255, 220, 120), 0f, TILE_H * 0.4f,
                new Color(235, 255, 220, 0)));
        g.draw(new RoundRectangle2D.Double(1.5, 1.5, TILE_W - 3, TILE_H - 3, 16, 16));
        g.dispose();
        tile.rendered = image;
        tile.reflection = reflect(image);
    }

    private static BufferedImage defaultArt;

    private static synchronized BufferedImage defaultArt() {
        if (defaultArt == null) {
            defaultArt = Icons.appIcon(ART * RENDER_SCALE);
        }
        return defaultArt;
    }

    private static void paintArtFrame(Graphics2D g, BufferedImage art, int x, int y) {
        RoundRectangle2D frame = new RoundRectangle2D.Double(x, y, ART, ART, 24, 24);
        Shape oldClip = g.getClip();
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 6, ART, ART, 24, 24));
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
        g.setColor(new Color(190, 255, 170, 70));
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, ART - 1, ART - 1, 24, 24));
    }

    private static void paintChip(Graphics2D g, String text, Color color, int x, int y) {
        g.setFont(Theme.font(Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 22;
        int h = 22;
        RoundRectangle2D pill = new RoundRectangle2D.Double(x, y, w, h, h, h);
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
        g.drawString(text, x + 11, y + (h - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent());
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
