package com.nicholasburczyk.packupdater.ui.dash;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Sounds;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.TransitionPane;
import com.nicholasburczyk.packupdater.ui.Ui;
import com.nicholasburczyk.packupdater.ui.component.PackArtwork;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The whole window: channel menu, tile row with a detail strip under it (or a full page such as
 * the admin panel), the connection light in the corner and the admin link, over a dark backdrop.
 */
public final class Dashboard extends JPanel {

    public static final int GUTTER = 150;
    private static final int WHEEL_COOLDOWN_MS = 220;
    private static final int DETAIL_DELAY_MS = 140;

    public interface Listener {
        void onChannel(String id);

        void onTileSelected(String channel, int index);

        void onReconnect();
    }

    public record Channel(String id, String name, int badge) {
    }

    /**
     * One tile. Pack tiles show the pack's artwork (loaded from the server, falling back to the
     * installed copy); other tiles show a large pixel icon. {@code detail} builds the strip under
     * the row when the tile is selected and {@code primary} runs on Enter or a click on the tile.
     */
    public record TileSpec(String title, String subtitle, String chip, Color chipColor,
                           ModpackInfo server, ModpackInfo local, String pixel,
                           Supplier<List<BufferedImage>> photos, Supplier<List<BufferedImage>> faces,
                           Supplier<JComponent> detail, Runnable primary) {

        public static TileSpec pack(String title, String subtitle, String chip, Color chipColor,
                                    ModpackInfo server, ModpackInfo local,
                                    Supplier<JComponent> detail, Runnable primary) {
            return new TileSpec(title, subtitle, chip, chipColor, server, local, null, null, null, detail, primary);
        }

        public static TileSpec icon(String title, String subtitle, String chip, Color chipColor, String pixel,
                                    Supplier<JComponent> detail, Runnable primary) {
            return new TileSpec(title, subtitle, chip, chipColor, null, null, pixel, null, null, detail, primary);
        }

        public static TileSpec photos(String title, String subtitle, String chip, Color chipColor, ModpackInfo pack,
                                      Supplier<List<BufferedImage>> photos, Supplier<List<BufferedImage>> faces,
                                      Supplier<JComponent> detail, Runnable primary) {
            return new TileSpec(title, subtitle, chip, chipColor, pack, null, null, photos, faces, detail, primary);
        }

        boolean isPack() {
            return photos == null && (server != null || local != null);
        }
    }

    private final Listener listener;
    private final ChannelMenu menu;
    private final TileStrip strip = new TileStrip(GUTTER);
    private final JPanel detail = new DetailPlate();
    private final KeyHints hints = new KeyHints();
    private final javax.swing.Timer detailDelay = new javax.swing.Timer(DETAIL_DELAY_MS, e -> showDetail(strip.selected()));
    private final TransitionPane page = new TransitionPane();
    private final JPanel backSlot = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel connectionText = Ui.label("Connecting", Theme.font(Font.BOLD, 12), Theme.TEXT_DIM);
    private final StatusLight statusLight = new StatusLight();
    private final JButton adminLink;
    private BufferedImage backdrop;
    private BufferedImage vignette;
    private BufferedImage grain;
    private java.awt.image.VolatileImage scene;
    private boolean sceneDirty = true;
    private boolean backdropLite;
    private final Aurora aurora = new Aurora(() -> {
        sceneDirty = true;
        repaint();
    });
    private java.awt.Window auroraWindow;
    private final List<TileSpec> specs = new ArrayList<>();
    private String channel = "";
    private boolean pageMode;
    private Runnable onBack;
    private long lastWheel;

    public Dashboard(Listener listener, Runnable onAdmin) {
        super(new BorderLayout());
        this.listener = listener;
        this.menu = new ChannelMenu(GUTTER, id -> {
            if (!id.equals(channel) || pageMode) {
                Sounds.play(Sounds.Effect.CHANNEL);
                listener.onChannel(id);
            }
        });

        adminLink = Ui.button("Admin", Icons.USER, Ui.ButtonStyle.GHOST, 24);
        adminLink.setFocusable(false);
        adminLink.addActionListener(e -> onAdmin.run());
        setOpaque(true);
        setBackground(Theme.BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        detailDelay.setRepeats(false);
        strip.onSelect(index -> {
            Sounds.play(Sounds.Effect.MOVE);
            detailDelay.restart();
            listener.onTileSelected(channel, index);
        });
        strip.onActivate(index -> {
            if (index < specs.size() && specs.get(index).primary() != null) {
                Sounds.play(Sounds.Effect.SELECT);
                specs.get(index).primary().run();
            }
        });
        strip.addMouseWheelListener(this::wheelTiles);
        menu.addMouseWheelListener(this::wheelChannels);
        installKeys();
        runAuroraWhileVisible();
    }

    /** The aurora clock runs only while the window is showing, and idles while it is in the background. */
    private void runAuroraWhileVisible() {
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (!isShowing()) {
                aurora.stop();
                return;
            }
            aurora.start();
            java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (window != null && window != auroraWindow) {
                auroraWindow = window;
                window.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowActivated(java.awt.event.WindowEvent event) {
                        aurora.setWindowActive(true);
                    }

                    @Override
                    public void windowDeactivated(java.awt.event.WindowEvent event) {
                        aurora.setWindowActive(false);
                    }
                });
            }
        });
    }

    @Override
    public void paint(Graphics g) {
        java.awt.Rectangle clip = g.getClipBounds();
        boolean whole = clip == null || clip.width >= getWidth() && clip.height >= getHeight();
        long start = System.nanoTime();
        super.paint(g);
        if (whole) {
            aurora.paintCost(System.nanoTime() - start);
        }
    }

    /**
     * Dark green-black backdrop and vignette (rendered once per size), with the aurora between them,
     * composed into one image only when the aurora moves, so other animations just copy it. Without
     * a graphics card the vignette and grain are baked into the backdrop and the aurora is drawn at
     * half size.
     */
    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            compose((Graphics2D) g, w, h, false);
            return;
        }
        do {
            if (scene == null || scene.getWidth() != w || scene.getHeight() != h
                    || scene.validate(gc) == java.awt.image.VolatileImage.IMAGE_INCOMPATIBLE) {
                scene = gc.createCompatibleVolatileImage(w, h);
                sceneDirty = true;
            } else if (scene.validate(gc) == java.awt.image.VolatileImage.IMAGE_RESTORED) {
                sceneDirty = true;
            }
            boolean lite = !scene.getCapabilities().isAccelerated();
            if (lite != aurora.isLite()) {
                aurora.setLite(lite);
                sceneDirty = true;
            }
            if (sceneDirty) {
                sceneDirty = false;
                Graphics2D s = scene.createGraphics();
                compose(s, w, h, lite);
                s.dispose();
            }
            g.drawImage(scene, 0, 0, null);
        } while (scene.contentsLost());
    }

    private void compose(Graphics2D g, int w, int h, boolean lite) {
        if (backdrop == null || backdrop.getWidth() != w || backdrop.getHeight() != h || backdropLite != lite) {
            backdropLite = lite;
            backdrop = getGraphicsConfiguration() != null
                    ? getGraphicsConfiguration().createCompatibleImage(w, h)
                    : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D b = backdrop.createGraphics();
            b.setPaint(new java.awt.GradientPaint(0f, 0f, new Color(0x06150C), 0f, h, new Color(0x010302)));
            b.fillRect(0, 0, w, h);
            b.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.18f, h * 0.05f), Math.max(w, h) * 0.7f,
                    new float[]{0f, 1f}, new Color[]{new Color(90, 255, 60, 34), new Color(0, 0, 0, 0)}));
            b.fillRect(0, 0, w, h);
            b.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.95f, h * 1.0f), Math.max(w, h) * 0.6f,
                    new float[]{0f, 1f}, new Color[]{new Color(20, 210, 130, 24), new Color(0, 0, 0, 0)}));
            b.fillRect(0, 0, w, h);
            paintWeb(b, w, h);
            if (lite) {
                paintVignette(b, w, h);
                paintGrain(b, w, h);
            }
            b.dispose();
        }
        g.drawImage(backdrop, 0, 0, null);
        Graphics2D g2 = (Graphics2D) g.create();
        aurora.paint(g2, w, h, getGraphicsConfiguration());
        if (!lite) {
            paintVignette(g2, w, h);
            paintGrain(g2, w, h);
        }
        g2.dispose();
    }

    private void paintVignette(Graphics2D g, int w, int h) {
        if (vignette == null || vignette.getWidth() != w || vignette.getHeight() != h) {
            vignette = getGraphicsConfiguration() != null
                    ? getGraphicsConfiguration().createCompatibleImage(w, h, java.awt.Transparency.TRANSLUCENT)
                    : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D v = vignette.createGraphics();
            v.scale(w / 320.0, h / 200.0);
            v.setPaint(new RadialGradientPaint(new Point2D.Float(134f, 90f), 240f,
                    new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 190)}));
            v.fillRect(0, 0, 320, 200);
            v.dispose();
        }
        g.drawImage(vignette, 0, 0, null);
    }

    private void paintGrain(Graphics2D g, int w, int h) {
        if (grain == null) {
            grain = buildGrain();
        }
        g.setPaint(new java.awt.TexturePaint(grain, new java.awt.Rectangle(0, 0, grain.getWidth(), grain.getHeight())));
        g.fillRect(0, 0, w, h);
    }

    private static void paintWeb(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double cx = 72;
        double cy = 80;
        double far = Math.hypot(w, h);
        int spokes = 44;
        double[] rings = new double[18];
        for (int k = 0; k < rings.length; k++) {
            rings[k] = 34 * Math.pow(1.3, k);
        }
        g.setStroke(new java.awt.BasicStroke(1f));
        for (int i = 0; i < spokes; i++) {
            double a = Math.PI * 2 * i / spokes + 0.04 * Math.sin(i * 1.7);
            double x = cx + Math.cos(a) * far;
            double y = cy + Math.sin(a) * far;
            g.setPaint(new java.awt.LinearGradientPaint((float) cx, (float) cy,
                    (float) (cx + Math.cos(a) * far * 0.8), (float) (cy + Math.sin(a) * far * 0.8),
                    new float[]{0f, 0.08f, 1f}, new Color[]{new Color(110, 230, 90, 0), new Color(110, 230, 90, 30),
                            new Color(110, 230, 90, 0)}));
            g.draw(new java.awt.geom.Line2D.Double(cx, cy, x, y));
        }
        for (double r : rings) {
            if (r > far) {
                break;
            }
            int alpha = (int) (26 * Math.pow(1 - r / far, 1.4) * Math.min(1, (r - 20) / 80));
            g.setColor(new Color(110, 230, 90, Math.max(2, alpha)));
            java.awt.geom.Path2D web = new java.awt.geom.Path2D.Double();
            for (int i = 0; i <= spokes; i++) {
                double a = Math.PI * 2 * (i % spokes) / spokes + 0.04 * Math.sin((i % spokes) * 1.7);
                double sag = r * (0.94 + 0.06 * Math.cos(i * 0.9 + r * 0.01));
                double x = cx + Math.cos(a) * sag;
                double y = cy + Math.sin(a) * sag;
                if (i == 0) {
                    web.moveTo(x, y);
                } else {
                    double mid = Math.PI * 2 * (i - 0.5) / spokes;
                    double inner = sag * 0.965;
                    web.quadTo(cx + Math.cos(mid) * inner, cy + Math.sin(mid) * inner, x, y);
                }
            }
            g.draw(web);
        }
    }

    private static BufferedImage buildGrain() {
        int size = 128;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.util.Random random = new java.util.Random(7);
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = random.nextInt(4) << 24 | 0xFFFFFF;
        }
        image.getRaster().setDataElements(0, 0, size, size, pixels);
        return image;
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(menu, BorderLayout.CENTER);

        // Back button for full pages; same width as the gutter so the channel name stays aligned.
        JButton back = Ui.button("Back", null, Ui.ButtonStyle.GHOST);
        back.putClientProperty(Sounds.SILENT, true);
        back.addActionListener(e -> {
            if (onBack != null) {
                Sounds.play(Sounds.Effect.BACK);
                onBack.run();
            }
        });
        backSlot.setOpaque(false);
        backSlot.setPreferredSize(new Dimension(GUTTER - 30, 10));
        backSlot.setBorder(Ui.pad(0, 18, 14, 0));
        backSlot.add(back, BorderLayout.SOUTH);
        backSlot.setVisible(false);
        header.add(backSlot, BorderLayout.WEST);

        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        corner.setOpaque(false);
        corner.setBorder(Ui.pad(20, 0, 0, 26));
        corner.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        corner.setToolTipText("Click to reconnect");
        corner.add(connectionText);
        corner.add(statusLight);
        corner.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                listener.onReconnect();
            }
        });
        JPanel cornerWrap = new JPanel(new BorderLayout());
        cornerWrap.setOpaque(false);
        cornerWrap.add(corner, BorderLayout.NORTH);
        header.add(cornerWrap, BorderLayout.EAST);
        return header;
    }

    private JComponent buildCenter() {
        center.setOpaque(false);

        JPanel tilesView = new JPanel(new BorderLayout());
        tilesView.setOpaque(false);
        tilesView.add(strip, BorderLayout.NORTH);
        detail.setOpaque(false);
        detail.setBorder(Ui.pad(0, GUTTER, 6, 32));
        tilesView.add(detail, BorderLayout.CENTER);

        JPanel pageView = new JPanel(new BorderLayout());
        pageView.setOpaque(false);
        pageView.setBorder(Ui.pad(0, 24, 0, 24));
        pageView.add(page, BorderLayout.CENTER);

        center.add(tilesView, "tiles");
        center.add(pageView, "page");
        return center;
    }

    private JComponent buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(Ui.pad(4, GUTTER - 8, 14, 22));
        bottom.add(hints, BorderLayout.WEST);
        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        links.setOpaque(false);
        boolean on = ConfigManager.getInstance().getConfig().getSound();
        JButton sound = Ui.button(on ? "Sound on" : "Sound off", on ? Icons.SPEAKER_ON : Icons.SPEAKER_OFF,
                Ui.ButtonStyle.GHOST);
        sound.putClientProperty(Sounds.SILENT, true);
        sound.setFocusable(false);
        sound.addActionListener(e -> {
            boolean next = !Sounds.isEnabled();
            Sounds.setEnabled(next);
            sound.setText(next ? "Sound on" : "Sound off");
            Ui.setIcon(sound, next ? Icons.SPEAKER_ON : Icons.SPEAKER_OFF, Ui.ButtonStyle.GHOST);
            ConfigManager.getInstance().getConfig().setSound(next);
            ConfigManager.getInstance().saveConfig();
            if (next) {
                Sounds.play(Sounds.Effect.SELECT);
            }
        });
        links.add(sound);
        links.add(adminLink);
        bottom.add(links, BorderLayout.EAST);
        return bottom;
    }

    // --------------------------------------------------------------- public api

    public void setChannels(List<Channel> channels, String activeId) {
        List<ChannelMenu.Item> items = new ArrayList<>();
        for (Channel c : channels) {
            items.add(new ChannelMenu.Item(c.id(), c.name(), c.badge()));
        }
        menu.setItems(items, activeId);
    }

    /** Shows a tile row; {@code animate} flies the tiles in (used when the channel changes). */
    public void showTiles(String channelId, List<TileSpec> tiles, int selected, boolean animate) {
        int direction = 0;
        if (animate) {
            direction = 1;
        }
        boolean wasPage = pageMode;
        channel = channelId;
        pageMode = false;
        onBack = null;
        menu.setCompact(false);
        aurora.setCalm(false);
        menu.setPreferredSize(new Dimension(600, 150));
        backSlot.setVisible(false);
        hints.setVisible(true);
        cards.show(center, "tiles");

        specs.clear();
        specs.addAll(tiles);
        List<TileStrip.Tile> rendered = new ArrayList<>();
        for (TileSpec spec : tiles) {
            TileStrip.Tile tile = new TileStrip.Tile(spec.title(), spec.subtitle(), spec.chip(),
                    spec.chipColor(), spec.isPack(), spec.pixel(), spec.photos() != null);
            rendered.add(tile);
            if (spec.isPack()) {
                loadArt(spec, tile);
            } else if (spec.photos() != null) {
                loadPhotos(spec, tile);
            }
        }
        strip.setTiles(rendered, selected, wasPage ? 1 : direction);
        showDetail(strip.selected());
    }

    /** Swaps the tile row for a full page (Admin, server settings) with a Back button. */
    public void showPage(String channelId, JComponent content, Runnable back) {
        channel = channelId;
        pageMode = true;
        onBack = back;
        menu.setCompact(true);
        aurora.setCalm(true);
        menu.setPreferredSize(new Dimension(600, 74));
        backSlot.setVisible(true);
        hints.setVisible(false);
        revalidate();
        cards.show(center, "page");
        page.show(content);
    }

    public String channel() {
        return channel;
    }

    public boolean inPage() {
        return pageMode;
    }

    public void refreshDetail() {
        showDetail(strip.selected());
    }

    /** {@code connected} null means a check is running. */
    public void setConnection(Boolean connected, String text) {
        statusLight.color = connected == null ? Theme.WARNING : connected ? Theme.LIME : Theme.DANGER;
        connectionText.setText(text);
        connectionText.setForeground(connected != null && !connected ? Theme.DANGER : Theme.TEXT_DIM);
        statusLight.repaint();
    }

    public void setAdminLabel(String text) {
        adminLink.setText(text);
    }

    // ------------------------------------------------------------------ helpers

    private void showDetail(int index) {
        detailDelay.stop();
        detail.removeAll();
        if (index >= 0 && index < specs.size() && specs.get(index).detail() != null) {
            JComponent content = specs.get(index).detail().get();
            content.setOpaque(false);
            detail.add(content, BorderLayout.CENTER);
        }
        detail.revalidate();
        detail.repaint();
    }

    private void loadArt(TileSpec spec, TileStrip.Tile tile) {
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return PackArtwork.image(spec.server(), spec.local());
            }

            @Override
            protected void done() {
                try {
                    BufferedImage image = get();
                    if (image != null) {
                        tile.art = image;
                        strip.artLoaded(tile);
                    }
                } catch (Exception ignored) {
                    // Keep the empty frame.
                }
            }
        }.execute();
    }

    private void loadPhotos(TileSpec spec, TileStrip.Tile tile) {
        new SwingWorker<List<BufferedImage>, Void>() {
            private BufferedImage icon;
            private List<BufferedImage> faces;

            @Override
            protected List<BufferedImage> doInBackground() {
                icon = spec.server() == null ? null : PackArtwork.image(spec.server(), null);
                faces = spec.faces() == null ? null : spec.faces().get();
                return spec.photos().get();
            }

            @Override
            protected void done() {
                try {
                    tile.art = icon;
                    tile.faces = faces;
                    tile.photos = get();
                    strip.artLoaded(tile);
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void wheelTiles(MouseWheelEvent e) {
        if (pageMode || !cooledDown()) {
            return;
        }
        strip.step(e.getWheelRotation() > 0 ? 1 : -1);
    }

    private void wheelChannels(MouseWheelEvent e) {
        if (!cooledDown()) {
            return;
        }
        moveChannel(e.getWheelRotation() > 0 ? 1 : -1);
    }

    private boolean cooledDown() {
        long now = System.currentTimeMillis();
        if (now - lastWheel < WHEEL_COOLDOWN_MS) {
            return false;
        }
        lastWheel = now;
        return true;
    }

    private void moveChannel(int delta) {
        int next = menu.activeIndex() + delta;
        if (next >= 0 && next < menu.itemCount()) {
            Sounds.play(Sounds.Effect.CHANNEL);
            listener.onChannel(menu.idAt(next));
        }
    }

    private void installKeys() {
        bind(KeyEvent.VK_LEFT, "tile-left", () -> strip.step(-1));
        bind(KeyEvent.VK_RIGHT, "tile-right", () -> strip.step(1));
        bind(KeyEvent.VK_UP, "channel-up", () -> moveChannel(-1));
        bind(KeyEvent.VK_DOWN, "channel-down", () -> moveChannel(1));
        bind(KeyEvent.VK_ENTER, "tile-activate", () -> {
            int index = strip.selected();
            if (index < specs.size() && specs.get(index).primary() != null) {
                Sounds.play(Sounds.Effect.SELECT);
                specs.get(index).primary().run();
            }
        });
    }

    private void bind(int key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (pageMode || focus instanceof JTextComponent) {
                    return;
                }
                action.run();
            }
        });
    }

    private static BufferedImage layer(Component owner, int w, int h) {
        java.awt.GraphicsConfiguration gc = owner.getGraphicsConfiguration();
        return gc != null ? gc.createCompatibleImage(w, h, java.awt.Transparency.TRANSLUCENT)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static final class DetailPlate extends JPanel {
        private BufferedImage cache;
        private java.awt.Rectangle cached;

        DetailPlate() {
            super(new BorderLayout());
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (getComponentCount() == 0 || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            Component content = getComponent(0);
            int height = Math.max(1, bottom(content, 0) - content.getY());
            java.awt.Rectangle plate = new java.awt.Rectangle(content.getX() - 24, content.getY() - 6,
                    content.getWidth() + 40, height + 20);
            if (cache == null || !plate.equals(cached)) {
                cached = plate;
                cache = layer(this, plate.width + 4, plate.height + 4);
                Graphics2D g2 = cache.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintPlate(g2, 2, 2, plate.width, plate.height);
                g2.dispose();
            }
            g.drawImage(cache, plate.x - 2, plate.y - 2, null);
        }

        private static int bottom(Component component, int offset) {
            int top = offset + component.getY();
            if (component instanceof java.awt.Container box && box.getComponentCount() > 0) {
                int lowest = top;
                for (Component child : box.getComponents()) {
                    if (child.isVisible()) {
                        lowest = Math.max(lowest, bottom(child, top));
                    }
                }
                return lowest;
            }
            return top + Math.min(component.getHeight(), component.getPreferredSize().height);
        }

        private static void paintPlate(Graphics2D g2, double x, double y, double w, double h) {
            java.awt.geom.Path2D plate = TileStrip.plate(x, y, w, h, 16, 5);
            g2.setPaint(new java.awt.GradientPaint(0f, (float) y, new Color(14, 40, 20, 150), 0f, (float) (y + h),
                    new Color(4, 14, 7, 120)));
            g2.fill(plate);
            g2.setStroke(new java.awt.BasicStroke(1.2f));
            g2.setPaint(new java.awt.GradientPaint((float) x, (float) y, new Color(120, 220, 100, 120),
                    (float) (x + w * 0.5), (float) (y + h), new Color(40, 100, 50, 60)));
            g2.draw(plate);
            g2.setPaint(new java.awt.GradientPaint((float) x, 0f, new Color(200, 255, 160, 110), (float) (x + w * 0.4), 0f,
                    new Color(200, 255, 160, 0)));
            g2.fill(new java.awt.geom.Rectangle2D.Double(x + 16, y + 1, w * 0.4, 1.2));
            g2.setColor(new Color(170, 255, 120, 170));
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Line2D.Double(x + 2, y + 16 - 3, x + 16 - 3, y + 2));
        }
    }

    private static final class KeyHints extends JComponent {
        private static final String[][] HINTS = {{"", "Browse"}, {"", "Channels"}, {"Enter", "Open"}};
        private BufferedImage cache;

        KeyHints() {
            setPreferredSize(new Dimension(420, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            if (cache == null || cache.getWidth() != getWidth() || cache.getHeight() != getHeight()) {
                cache = layer(this, getWidth(), getHeight());
                Graphics2D g2 = cache.createGraphics();
                paintHints(g2, getHeight());
                g2.dispose();
            }
            g.drawImage(cache, 0, 0, null);
        }

        private static void paintHints(Graphics2D g2, int height) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font keyFont = Theme.font(Font.BOLD, 11);
            Font labelFont = Theme.font(Font.PLAIN, 12);
            double x = 8;
            double cy = height / 2.0;
            for (int index = 0; index < HINTS.length; index++) {
                String[] hint = HINTS[index];
                g2.setFont(keyFont);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                double kw = hint[0].isEmpty() ? 34 : Math.max(26, fm.stringWidth(hint[0]) + 16);
                double kh = 20;
                java.awt.geom.RoundRectangle2D cap = new java.awt.geom.RoundRectangle2D.Double(x, cy - kh / 2, kw, kh, kh, kh);
                g2.setPaint(new java.awt.GradientPaint(0f, (float) (cy - kh / 2), new Color(0x2A6A2C), 0f,
                        (float) (cy + kh / 2), new Color(0x0A2410)));
                g2.fill(cap);
                g2.setColor(new Color(150, 240, 120, 150));
                g2.draw(cap);
                g2.setPaint(new java.awt.GradientPaint(0f, (float) (cy - kh / 2), new Color(255, 255, 255, 70), 0f,
                        (float) cy, new Color(255, 255, 255, 0)));
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(x + 3, cy - kh / 2 + 1.5, kw - 6, kh / 2 - 1, kh / 2, kh / 2));
                g2.setColor(new Color(0xE6FFD8));
                if (hint[0].isEmpty()) {
                    double mid = x + kw / 2;
                    boolean horizontal = index == 0;
                    g2.fill(arrow(mid - 6, cy, horizontal ? Math.PI : -Math.PI / 2));
                    g2.fill(arrow(mid + 6, cy, horizontal ? 0 : Math.PI / 2));
                } else {
                    g2.drawString(hint[0], (float) (x + (kw - fm.stringWidth(hint[0])) / 2),
                            (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
                }
                x += kw + 7;
                g2.setFont(labelFont);
                fm = g2.getFontMetrics();
                g2.setColor(Theme.TEXT_FAINT);
                g2.drawString(hint[1], (float) x, (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
                x += fm.stringWidth(hint[1]) + 22;
            }
        }

        private static java.awt.Shape arrow(double cx, double cy, double angle) {
            java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
            path.moveTo(4, 0);
            path.lineTo(-3, -4);
            path.lineTo(-3, 4);
            path.closePath();
            java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform.getTranslateInstance(cx, cy);
            at.rotate(angle);
            return at.createTransformedShape(path);
        }
    }

    /** Glossy bead in the corner: green when online, amber while checking, red when offline. */
    private static final class StatusLight extends JComponent {
        Color color = Theme.WARNING;

        StatusLight() {
            setPreferredSize(new Dimension(34, 34));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float size = 24;
            float x = (getWidth() - size) / 2f;
            float y = (getHeight() - size) / 2f;
            g2.setColor(Theme.alpha(color, 70));
            g2.fill(new Ellipse2D.Double(x - 4, y - 4, size + 8, size + 8));
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(x + size / 2, y + size * 0.75f), size * 0.65f,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{Theme.mix(color, Color.WHITE, 0.45), color, Theme.mix(color, Color.BLACK, 0.8)}));
            g2.fill(new Ellipse2D.Double(x, y, size, size));
            g2.setPaint(new java.awt.GradientPaint(0f, y, new Color(255, 255, 255, 220),
                    0f, y + size * 0.45f, new Color(255, 255, 255, 0)));
            g2.fill(new Ellipse2D.Double(x + 5, y + 2, size - 10, size * 0.45));
            g2.dispose();
        }
    }
}
