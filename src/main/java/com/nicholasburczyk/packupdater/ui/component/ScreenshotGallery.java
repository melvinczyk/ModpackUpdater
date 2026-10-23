package com.nicholasburczyk.packupdater.ui.component;

import com.nicholasburczyk.packupdater.core.PlayerFaces;
import com.nicholasburczyk.packupdater.core.Screenshots;
import com.nicholasburczyk.packupdater.core.Screenshots.Shot;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Sounds;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;
import com.nicholasburczyk.packupdater.ui.dialog.Dialogs;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreenshotGallery extends JPanel {

    private static final int LIST_W = 330;
    private static final int ROW_H = 64;
    private static final int ROW_GAP = 6;
    private static final int THUMB_W = 92;
    private static final int THUMB_H = 52;
    private static final double SKEW = 9;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "gallery-loader");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService FULL = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gallery-full");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, String> packs;
    private final boolean admin;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel subtitle = Ui.label(" ", Theme.font(Font.PLAIN, 13), Theme.TEXT_DIM);
    private final JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private final JPanel packRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel message = Ui.label(" ", Theme.font(Font.PLAIN, 15), Theme.TEXT_DIM);
    private final Captures captures = new Captures();
    private final JScrollPane captureScroll = Ui.scroll(captures);
    private final Stage preview = new Stage(false);
    private final Stage stage = new Stage(true);
    private final Map<String, BufferedImage> rowThumbs = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();
    private final Map<String, BufferedImage> faces = new ConcurrentHashMap<>();
    private final Set<String> facesRequested = ConcurrentHashMap.newKeySet();
    private final Timer statusTimer = new Timer(1000, e -> updateSubtitle());
    private Map<String, List<Shot>> byPack = Map.of();
    private String packId;
    private List<Shot> all = List.of();
    private List<Shot> shown = List.of();
    private String player;
    private int selected;
    private boolean full;
    private String loadedHash;
    private BufferedImage image;
    private BufferedImage placeholder;

    public ScreenshotGallery(Map<String, String> packs, boolean admin, String startPack,
                             Map<String, List<Shot>> preloaded) {
        super(new BorderLayout());
        this.packs = packs;
        this.admin = admin;
        setOpaque(false);
        setBorder(Ui.pad(6, 120, 16, 32));

        JPanel titles = Ui.column();
        titles.add(Ui.label("Gallery", Theme.font(Font.BOLD, 30), Theme.TEXT));
        titles.add(Ui.strut(2));
        titles.add(subtitle);
        titles.add(Ui.strut(14));
        packRow.setOpaque(false);
        packRow.setAlignmentX(0f);
        titles.add(packRow);
        filters.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(Ui.pad(0, 0, 16, 0));
        header.add(titles, BorderLayout.WEST);
        JPanel filterWrap = new JPanel(new BorderLayout());
        filterWrap.setOpaque(false);
        filterWrap.add(filters, BorderLayout.SOUTH);
        header.add(filterWrap, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        body.setOpaque(false);
        message.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel messageCard = new JPanel(new BorderLayout());
        messageCard.setOpaque(false);
        messageCard.add(message, BorderLayout.CENTER);
        body.add(messageCard, "message");
        body.add(browseCard(), "browse");
        body.add(fullCard(), "full");
        add(body, BorderLayout.CENTER);

        bind(KeyEvent.VK_UP, "gallery-up", () -> browse(-1));
        bind(KeyEvent.VK_DOWN, "gallery-down", () -> browse(1));
        bind(KeyEvent.VK_LEFT, "gallery-left", () -> browse(-1));
        bind(KeyEvent.VK_RIGHT, "gallery-right", () -> browse(1));
        bind(KeyEvent.VK_ENTER, "gallery-view", () -> setFull(true));
        bind(KeyEvent.VK_ESCAPE, "gallery-close", () -> setFull(false));
        bind(KeyEvent.VK_DELETE, "gallery-delete", this::deleteSelected);

        statusTimer.start();
        if (preloaded != null) {
            byPack = new java.util.LinkedHashMap<>();
            preloaded.forEach((id, shots) -> byPack.put(id, new ArrayList<>(shots)));
            open(startPack);
        } else {
            showCard("message", "Loading the gallery");
            load(startPack);
        }
    }

    @Override
    public void removeNotify() {
        statusTimer.stop();
        super.removeNotify();
    }

    private JComponent browseCard() {
        captureScroll.setPreferredSize(new Dimension(LIST_W, 10));
        captureScroll.setBorder(Ui.pad(0, 0, 0, 8));

        JButton view = silent(Ui.button("View", Icons.FOCUS, Ui.ButtonStyle.PRIMARY));
        view.addActionListener(e -> setFull(true));
        JButton open = Ui.button("Download", Icons.SAVE, Ui.ButtonStyle.SECONDARY);
        open.setToolTipText("Saves the full-size capture to your Pictures folder");
        open.addActionListener(e -> download());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(view);
        actions.add(open);
        actions.add(deleteButton());

        preview.setActions(actions);
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.setBorder(Ui.pad(0, 22, 0, 0));
        right.add(preview, BorderLayout.CENTER);

        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        card.add(captureScroll, BorderLayout.WEST);
        card.add(right, BorderLayout.CENTER);
        return card;
    }

    private JComponent fullCard() {
        JButton back = silent(Ui.button("Back to gallery", Icons.HOME, Ui.ButtonStyle.GHOST));
        back.addActionListener(e -> setFull(false));
        JButton prev = silent(Ui.button("Previous", Icons.SKIP_BACK, Ui.ButtonStyle.SECONDARY));
        prev.addActionListener(e -> browse(-1));
        JButton next = silent(Ui.button("Next", Icons.SKIP_NEXT, Ui.ButtonStyle.SECONDARY));
        next.addActionListener(e -> browse(1));
        JButton open = Ui.button("Download", Icons.SAVE, Ui.ButtonStyle.PRIMARY);
        open.setToolTipText("Saves the full-size capture to your Pictures folder");
        open.addActionListener(e -> download());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(back);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(deleteButton());
        right.add(prev);
        right.add(next);
        right.add(open);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(Ui.pad(10, 0, 0, 0));
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        card.add(stage, BorderLayout.CENTER);
        card.add(bar, BorderLayout.SOUTH);
        return card;
    }

    private JButton deleteButton() {
        JButton delete = silent(Ui.button("Delete", null, Ui.ButtonStyle.SECONDARY));
        delete.setToolTipText("Removes it from the server for everyone");
        delete.addActionListener(e -> deleteSelected());
        delete.setVisible(admin);
        return delete;
    }

    private static JButton silent(JButton button) {
        button.putClientProperty(Sounds.SILENT, true);
        return button;
    }

    private void bind(int key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!shown.isEmpty()) {
                    action.run();
                }
            }
        });
    }

    private void load(String startPack) {
        new SwingWorker<Map<String, List<Shot>>, Void>() {
            @Override
            protected Map<String, List<Shot>> doInBackground() {
                return Screenshots.listAll(packs.keySet());
            }

            @Override
            protected void done() {
                try {
                    byPack = get();
                } catch (Exception e) {
                    showCard("message", "Could not load the gallery: " + Dialogs.readable(e));
                    return;
                }
                open(startPack);
            }
        }.execute();
    }

    private void open(String startPack) {
        if (startPack != null && !byPack.getOrDefault(startPack, List.of()).isEmpty()) {
            selectPack(startPack);
            return;
        }
        String newest = null;
        long latest = Long.MIN_VALUE;
        for (Map.Entry<String, List<Shot>> entry : byPack.entrySet()) {
            if (!entry.getValue().isEmpty() && entry.getValue().get(0).taken() > latest) {
                latest = entry.getValue().get(0).taken();
                newest = entry.getKey();
            }
        }
        if (newest == null) {
            updateSubtitle();
            showCard("message", "Nothing here yet. Press F2 in game, captures show up the next time the updater opens.");
            return;
        }
        selectPack(newest);
    }

    private void selectPack(String id) {
        packId = id;
        all = byPack.getOrDefault(id, List.of());
        player = null;
        buildPacks();
        buildFilters();
        applyFilter(null);
    }

    private void buildPacks() {
        packRow.removeAll();
        for (Map.Entry<String, String> pack : packs.entrySet()) {
            List<Shot> shots = byPack.getOrDefault(pack.getKey(), List.of());
            if (shots.isEmpty() && !pack.getKey().equals(packId)) {
                continue;
            }
            boolean on = pack.getKey().equals(packId);
            JButton button = silent(Ui.button(pack.getValue(), null, on ? Ui.ButtonStyle.PRIMARY : Ui.ButtonStyle.SECONDARY));
            button.addActionListener(e -> {
                if (!pack.getKey().equals(packId)) {
                    Sounds.play(Sounds.Effect.CHANNEL);
                    full = false;
                    selectPack(pack.getKey());
                }
            });
            packRow.add(button);
        }
        packRow.revalidate();
        packRow.repaint();
    }

    private void buildFilters() {
        filters.removeAll();
        List<String> players = new ArrayList<>();
        Screenshots.forEachPlayer(all, players::add);
        if (players.size() > 1) {
            players.sort(String.CASE_INSENSITIVE_ORDER);
            filters.add(filterButton("Everyone", null));
            for (String name : players) {
                filters.add(filterButton(name, name));
            }
        }
        filters.revalidate();
        filters.repaint();
    }

    private JButton filterButton(String text, String value) {
        boolean on = value == null ? player == null : value.equals(player);
        JButton button = silent(Ui.button(text, null, on ? Ui.ButtonStyle.PRIMARY : Ui.ButtonStyle.GHOST));
        button.addActionListener(e -> {
            if (!on) {
                Sounds.play(Sounds.Effect.CHANNEL);
                applyFilter(value);
                buildFilters();
            }
        });
        return button;
    }

    private void applyFilter(String value) {
        player = value;
        List<Shot> next = new ArrayList<>();
        for (Shot shot : all) {
            if (player == null || player.equals(shot.player())) {
                next.add(shot);
            }
        }
        shown = next;
        updateSubtitle();
        if (shown.isEmpty()) {
            full = false;
            showCard("message", all.isEmpty()
                    ? "Nothing here yet. Press F2 in game, captures show up the next time the updater opens."
                    : "Nothing from this player yet.");
            return;
        }
        captures.revalidate();
        captures.repaint();
        select(0, false);
        showCard(full ? "full" : "browse", null);
    }

    private void updateSubtitle() {
        List<String> players = new ArrayList<>();
        Screenshots.forEachPlayer(all, players::add);
        if (packId == null) {
            subtitle.setText("Captures from everyone in the group.");
            return;
        }
        String text = packs.get(packId) + ". " + (all.size() == 1 ? "1 capture" : all.size() + " captures")
                + (players.size() == 1 ? " from 1 player" : " from " + players.size() + " players") + ".";
        String status = Screenshots.status();
        subtitle.setText(status.isEmpty() ? text : text + " " + status + ".");
    }

    private void showCard(String card, String text) {
        if (text != null) {
            message.setText(text);
        }
        cards.show(body, card);
    }

    private void browse(int delta) {
        if (shown.isEmpty()) {
            return;
        }
        int next = full ? Math.floorMod(selected + delta, shown.size())
                : Math.max(0, Math.min(shown.size() - 1, selected + delta));
        if (next != selected) {
            Sounds.play(Sounds.Effect.MOVE);
            select(next, true);
        }
    }

    private void select(int index, boolean scroll) {
        selected = index;
        Shot shot = current();
        if (shot == null) {
            return;
        }
        if (!shot.hash().equals(loadedHash)) {
            loadedHash = shot.hash();
            image = null;
            placeholder = null;
            loadImage(shot);
        }
        if (scroll) {
            captures.scrollRectToVisible(captures.row(index));
        }
        captures.repaint();
        preview.repaint();
        stage.repaint();
        if (index + 1 < shown.size()) {
            Shot ahead = shown.get(index + 1);
            FULL.execute(() -> {
                try {
                    Screenshots.prefetch(ahead);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void loadImage(Shot shot) {
        LOADER.execute(() -> {
            try {
                BufferedImage thumb = Screenshots.thumbnail(shot);
                SwingUtilities.invokeLater(() -> {
                    if (shot.hash().equals(loadedHash) && image == null) {
                        placeholder = thumb;
                        preview.revalidate();
                        preview.repaint();
                        stage.repaint();
                    }
                });
            } catch (Exception ignored) {
            }
        });
        FULL.execute(() -> {
            if (!shot.hash().equals(loadedHash)) {
                return;
            }
            try {
                BufferedImage loaded = Screenshots.full(shot);
                SwingUtilities.invokeLater(() -> {
                    if (shot.hash().equals(loadedHash)) {
                        image = loaded;
                        preview.revalidate();
                        preview.repaint();
                        stage.repaint();
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (shot.hash().equals(loadedHash)) {
                        loadedHash = null;
                    }
                });
            }
        });
    }

    private Shot current() {
        return shown.isEmpty() ? null : shown.get(Math.max(0, Math.min(selected, shown.size() - 1)));
    }

    private void setFull(boolean value) {
        if (value == full || shown.isEmpty()) {
            return;
        }
        full = value;
        Sounds.play(value ? Sounds.Effect.SELECT : Sounds.Effect.BACK);
        showCard(value ? "full" : "browse", null);
        if (!value) {
            captures.scrollRectToVisible(captures.row(selected));
        }
    }

    private void download() {
        Shot shot = current();
        if (shot == null) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                java.nio.file.Path file = Screenshots.download(shot, packs.get(packId));
                if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                    new ProcessBuilder("explorer.exe", "/select," + file).start();
                } else {
                    Desktop.getDesktop().open(file.getParent().toFile());
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Dialogs.error(ScreenshotGallery.this, "Could not download the capture", Dialogs.readable(e));
                }
            }
        }.execute();
    }

    private void deleteSelected() {
        Shot shot = current();
        if (shot == null || !admin) {
            return;
        }
        Sounds.play(Sounds.Effect.BACK);
        int at = selected;
        List<Shot> remaining = new ArrayList<>(all);
        remaining.remove(shot);
        all = remaining;
        byPack.put(packId, remaining);
        refreshAfterChange(at);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                Screenshots.delete(shot);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    List<Shot> restored = new ArrayList<>(all);
                    restored.add(shot);
                    restored.sort((a, b) -> Long.compare(b.taken(), a.taken()));
                    all = restored;
                    byPack.put(packId, restored);
                    refreshAfterChange(at);
                    Dialogs.error(ScreenshotGallery.this, "Could not delete the capture", Dialogs.readable(e));
                }
            }
        }.execute();
    }

    private void refreshAfterChange(int at) {
        boolean wasFull = full;
        buildFilters();
        applyFilter(player);
        if (!shown.isEmpty()) {
            full = wasFull;
            select(Math.min(at, shown.size() - 1), true);
            showCard(full ? "full" : "browse", null);
        }
    }

    private void requestThumb(Shot shot, int width, int height) {
        if (!requested.add(shot.hash())) {
            return;
        }
        LOADER.execute(() -> {
            try {
                BufferedImage loaded = Screenshots.thumbnail(shot);
                if (loaded != null) {
                    rowThumbs.put(shot.hash(), cover(loaded, width, height));
                    SwingUtilities.invokeLater(() -> {
                        captures.repaint();
                        preview.repaint();
                    });
                }
            } catch (Exception e) {
                requested.remove(shot.hash());
            }
        });
    }

    private BufferedImage face(String name) {
        BufferedImage known = faces.get(name);
        if (known == null && facesRequested.add(name)) {
            LOADER.execute(() -> {
                BufferedImage loaded = PlayerFaces.face(name);
                if (loaded != null) {
                    faces.put(name, loaded);
                    SwingUtilities.invokeLater(() -> {
                        captures.repaint();
                        preview.repaint();
                        stage.repaint();
                    });
                }
            });
        }
        return known;
    }

    private static double drawFace(Graphics2D g2, BufferedImage face, double x, double y, double size) {
        if (face == null) {
            return x;
        }
        Path2D frame = Ui.plate(x, y, size, size, Math.max(3, size / 4), 2);
        Shape old = g2.getClip();
        g2.clip(frame);
        Object interpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(face, (int) x, (int) y, (int) size, (int) size, null);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g2.setClip(old);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(190, 255, 150, 200));
        g2.draw(frame);
        return x + size + 7;
    }

    private static BufferedImage cover(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
        int w = (int) Math.ceil(source.getWidth() * scale);
        int h = (int) Math.ceil(source.getHeight() * scale);
        g.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    private static String when(Shot shot) {
        return WHEN.format(Instant.ofEpochMilli(shot.taken()).atZone(ZoneId.systemDefault()));
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g2;
    }

    private static Path2D slant(double x, double y, double w, double h) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + SKEW, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w - SKEW, y + h);
        path.lineTo(x, y + h);
        path.closePath();
        return path;
    }

    private final class Captures extends JComponent implements Scrollable {
        private int hovered = -1;

        Captures() {
            setOpaque(false);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int index = indexAt(e.getY());
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

                @Override
                public void mouseClicked(MouseEvent e) {
                    int index = indexAt(e.getY());
                    if (index < 0) {
                        return;
                    }
                    if (index != selected) {
                        Sounds.play(Sounds.Effect.MOVE);
                        select(index, false);
                    }
                    if (e.getClickCount() >= 2) {
                        setFull(true);
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        Rectangle row(int index) {
            return new Rectangle(0, index * (ROW_H + ROW_GAP), getWidth(), ROW_H);
        }

        private int indexAt(int y) {
            int index = y / (ROW_H + ROW_GAP);
            return index >= 0 && index < shown.size() && y % (ROW_H + ROW_GAP) < ROW_H ? index : -1;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(LIST_W, Math.max(1, shown.size() * (ROW_H + ROW_GAP)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Rectangle clip = g2.getClipBounds();
            double scale = g2.getTransform().getScaleX();
            Font nameFont = Theme.font(Font.BOLD, 14);
            Font dateFont = Theme.font(Font.PLAIN, 12);
            double w = getWidth() - 2;
            for (int i = 0; i < shown.size(); i++) {
                Rectangle r = row(i);
                if (clip != null && !clip.intersects(r)) {
                    continue;
                }
                Shot shot = shown.get(i);
                boolean on = i == selected;
                Path2D bar = slant(1, r.y + 0.5, w, ROW_H - 1);
                if (on) {
                    g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(Theme.alpha(Theme.LIME, 40));
                    g2.draw(bar);
                    g2.setPaint(new LinearGradientPaint(0f, r.y, 0f, r.y + ROW_H, new float[]{0f, 0.48f, 0.52f, 1f},
                            new Color[]{new Color(0xF1FFB0), new Color(0xA8F244), new Color(0x5CC522),
                                    new Color(0x8CE83A)}));
                    g2.fill(bar);
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor(new Color(0xF6FFD6));
                    g2.draw(bar);
                } else {
                    g2.setPaint(new GradientPaint(0f, r.y, new Color(i == hovered ? 0x1D4F26 : 0x15391C), 0f,
                            r.y + ROW_H, new Color(0x07170A)));
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.86f));
                    g2.fill(bar);
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor(Theme.alpha(new Color(i == hovered ? 0x7FD86A : 0x3E7A44), 190));
                    g2.draw(bar);
                }
                g2.setColor(Theme.alpha(Color.WHITE, on ? 150 : 40));
                g2.draw(new Line2D.Double(1 + SKEW + 2, r.y + 1.5, w - 3, r.y + 1.5));

                double tx = 1 + SKEW + 8;
                double ty = r.y + (ROW_H - THUMB_H) / 2.0;
                Path2D frame = Ui.plate(tx, ty, THUMB_W, THUMB_H, 9, 3);
                BufferedImage thumb = rowThumbs.get(shot.hash());
                if (thumb == null) {
                    requestThumb(shot, (int) Math.ceil(THUMB_W * scale), (int) Math.ceil(THUMB_H * scale));
                    g2.setColor(new Color(4, 14, 7));
                    g2.fill(frame);
                } else {
                    Shape old = g2.getClip();
                    g2.clip(frame);
                    g2.drawImage(thumb, (int) tx, (int) ty, THUMB_W, THUMB_H, null);
                    g2.setClip(old);
                }
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(on ? new Color(0x1E5A12) : new Color(120, 210, 100, 150));
                g2.draw(frame);

                double textX = tx + THUMB_W + 14;
                double nameX = drawFace(g2, face(shot.player()), textX, r.y + 13, 18);
                g2.setFont(nameFont);
                g2.setColor(on ? new Color(0x072A0B) : Theme.TEXT);
                g2.drawString(shot.player(), (float) nameX, (float) (r.y + 27));
                g2.setFont(dateFont);
                g2.setColor(on ? new Color(0x1E5A12) : Theme.TEXT_FAINT);
                g2.drawString(when(shot), (float) textX, (float) (r.y + 45));
            }
            g2.dispose();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_H + ROW_GAP;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(ROW_H, visibleRect.height - ROW_H);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private final class Stage extends JComponent {
        private final boolean large;
        private JComponent actions;

        Stage(boolean large) {
            this.large = large;
            setOpaque(false);
            setCursor(large ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!large) {
                        setFull(true);
                    } else if (e.getX() < getWidth() * 0.25) {
                        browse(-1);
                    } else if (e.getX() > getWidth() * 0.75) {
                        browse(1);
                    }
                }
            });
        }

        void setActions(JComponent bar) {
            actions = bar;
            setLayout(null);
            add(bar);
        }

        private BufferedImage source(Shot shot) {
            if (image != null) {
                return image;
            }
            return placeholder != null ? placeholder : rowThumbs.get(shot.hash());
        }

        private double[] frame(BufferedImage source) {
            int caption = large ? 40 : 58;
            int below = actions != null ? actions.getPreferredSize().height + 6 : 0;
            int arrows = large ? 56 : 0;
            double availW = getWidth() - arrows * 2;
            double availH = getHeight() - caption - below;
            double ratio = source != null ? (double) source.getWidth() / source.getHeight() : 16 / 9.0;
            double w = Math.max(10, Math.min(availW, availH * ratio));
            double h = w / ratio;
            double x = large ? (getWidth() - w) / 2 : 0;
            double y = large ? Math.max(0, (availH - h) / 2) : 0;
            return new double[]{x, y, w, h};
        }

        @Override
        public void doLayout() {
            if (actions == null) {
                return;
            }
            Shot shot = current();
            double[] f = frame(shot == null ? null : source(shot));
            Dimension size = actions.getPreferredSize();
            actions.setBounds((int) f[0] - 8, (int) (f[1] + f[3] + 48), size.width, size.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Shot shot = current();
            if (shot == null) {
                return;
            }
            Graphics2D g2 = smooth(g);
            BufferedImage source = source(shot);
            double[] f = frame(source);
            double x = f[0];
            double y = f[1];
            double w = f[2];
            double h = f[3];
            int arrows = large ? 56 : 0;
            Path2D plate = Ui.plate(x, y, w, h, 22, 6);
            if (source != null) {
                Shape old = g2.getClip();
                g2.clip(plate);
                g2.drawImage(source, (int) x, (int) y, (int) Math.ceil(w), (int) Math.ceil(h), null);
                g2.setPaint(new GradientPaint(0f, (float) y, new Color(255, 255, 255, 34), 0f, (float) (y + h * 0.22),
                        new Color(255, 255, 255, 0)));
                g2.fill(new Rectangle((int) x, (int) y, (int) Math.ceil(w), (int) (h * 0.22)));
                g2.setClip(old);
            } else {
                g2.setColor(new Color(6, 18, 9, 200));
                g2.fill(plate);
            }
            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.alpha(Theme.LIME, 26));
            g2.draw(plate);
            g2.setStroke(new BasicStroke(2f));
            g2.setPaint(new GradientPaint((float) x, (float) y, new Color(190, 250, 150, 220),
                    (float) (x + w * 0.4), (float) (y + h), new Color(30, 90, 36, 230)));
            g2.draw(plate);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xF4FFC8));
            g2.draw(new Line2D.Double(x + w - 22 + 4, y + h - 1, x + w - 1, y + h - 22 + 4));

            float base = (float) (y + h + (large ? 27 : 30));
            double nameX = drawFace(g2, face(shot.player()), x, base - 17, 22);
            g2.setFont(Theme.font(Font.BOLD, large ? 16 : 17));
            g2.setColor(Theme.TEXT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(shot.player(), (float) nameX, base);
            g2.setFont(Theme.font(Font.PLAIN, 13));
            g2.setColor(Theme.TEXT_DIM);
            String detail = when(shot) + "     " + (selected + 1) + " of " + shown.size();
            g2.drawString(detail, (float) (nameX + fm.stringWidth(shot.player()) + 14), base);
            if (image == null) {
                g2.setColor(Theme.TEXT_FAINT);
                String loading = "Loading full size";
                g2.drawString(loading, (float) (x + w - g2.getFontMetrics().stringWidth(loading)), base);
            }
            if (large) {
                arrow(g2, arrows / 2.0, y + h / 2, -1);
                arrow(g2, getWidth() - arrows / 2.0, y + h / 2, 1);
            }
            g2.dispose();
        }

        private void arrow(Graphics2D g2, double cx, double cy, int dir) {
            double r = 20;
            Ellipse2D ring = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
            g2.setPaint(new GradientPaint(0f, (float) (cy - r), new Color(0x2A6A2C), 0f, (float) (cy + r),
                    new Color(0x0A2410)));
            g2.fill(ring);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(150, 240, 120, 170));
            g2.draw(ring);
            Path2D chevron = new Path2D.Double();
            chevron.moveTo(cx - 3 * dir, cy - 7);
            chevron.lineTo(cx + 4 * dir, cy);
            chevron.lineTo(cx - 3 * dir, cy + 7);
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xE6FFD8));
            g2.draw(chevron);
        }
    }
}
