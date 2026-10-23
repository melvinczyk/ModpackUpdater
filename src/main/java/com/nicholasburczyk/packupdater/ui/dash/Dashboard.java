package com.nicholasburczyk.packupdater.ui.dash;

import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.ui.Icons;
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
                           Supplier<JComponent> detail, Runnable primary) {

        public static TileSpec pack(String title, String subtitle, String chip, Color chipColor,
                                    ModpackInfo server, ModpackInfo local,
                                    Supplier<JComponent> detail, Runnable primary) {
            return new TileSpec(title, subtitle, chip, chipColor, server, local, null, detail, primary);
        }

        public static TileSpec icon(String title, String subtitle, String chip, Color chipColor, String pixel,
                                    Supplier<JComponent> detail, Runnable primary) {
            return new TileSpec(title, subtitle, chip, chipColor, null, null, pixel, detail, primary);
        }

        boolean isPack() {
            return server != null || local != null;
        }
    }

    private final Listener listener;
    private final ChannelMenu menu;
    private final TileStrip strip = new TileStrip(GUTTER);
    private final JPanel detail = new JPanel(new BorderLayout());
    private final TransitionPane page = new TransitionPane();
    private final JPanel backSlot = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel connectionText = Ui.label("Connecting", Theme.font(Font.BOLD, 12), Theme.TEXT_DIM);
    private final StatusLight statusLight = new StatusLight();
    private final JButton adminLink;
    private BufferedImage backdrop;
    private BufferedImage vignette;
    private final Aurora aurora = new Aurora(this::repaint);
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
                listener.onChannel(id);
            }
        });

        adminLink = Ui.button("Admin", Icons.USER, Ui.ButtonStyle.GHOST, 24);
        adminLink.addActionListener(e -> onAdmin.run());
        setOpaque(true);
        setBackground(Theme.BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        strip.onSelect(index -> {
            showDetail(index);
            listener.onTileSelected(channel, index);
        });
        strip.onActivate(index -> {
            if (index < specs.size() && specs.get(index).primary() != null) {
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

    /** Dark green-black backdrop (rendered once per size), the aurora, then a soft edge vignette. */
    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (backdrop == null || backdrop.getWidth() != w || backdrop.getHeight() != h) {
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
            b.dispose();
        }
        g.drawImage(backdrop, 0, 0, null);

        Graphics2D g2 = (Graphics2D) g.create();
        aurora.paint(g2, w, h, getGraphicsConfiguration());
        if (vignette == null) {
            vignette = getGraphicsConfiguration() != null
                    ? getGraphicsConfiguration().createCompatibleImage(320, 200, java.awt.Transparency.TRANSLUCENT)
                    : new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
            Graphics2D v = vignette.createGraphics();
            v.setPaint(new RadialGradientPaint(new Point2D.Float(134f, 90f), 240f,
                    new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 190)}));
            v.fillRect(0, 0, 320, 200);
            v.dispose();
        }
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(vignette, 0, 0, w, h, null);
        g2.dispose();
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(menu, BorderLayout.CENTER);

        // Back button for full pages; same width as the gutter so the channel name stays aligned.
        JButton back = Ui.button("Back", null, Ui.ButtonStyle.GHOST);
        back.addActionListener(e -> {
            if (onBack != null) {
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
        bottom.setBorder(Ui.pad(4, 54, 14, 22));
        bottom.add(adminLink, BorderLayout.EAST);
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
        cards.show(center, "tiles");

        specs.clear();
        specs.addAll(tiles);
        List<TileStrip.Tile> rendered = new ArrayList<>();
        for (TileSpec spec : tiles) {
            TileStrip.Tile tile = new TileStrip.Tile(spec.title(), spec.subtitle(), spec.chip(),
                    spec.chipColor(), spec.isPack(), spec.pixel());
            rendered.add(tile);
            if (spec.isPack()) {
                loadArt(spec, tile);
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
