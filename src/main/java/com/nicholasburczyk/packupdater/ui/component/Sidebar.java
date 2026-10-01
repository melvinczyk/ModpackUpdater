package com.nicholasburczyk.packupdater.ui.component;

import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.ui.Animator;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Sidebar extends JPanel {

    public static final String BROWSE = "browse";
    public static final String SETTINGS = "settings";
    public static final String HELP = "help";
    public static final String ADMIN = "admin";

    private static final int PACK_ART = 38;

    public interface Listener {
        void onSelectPack(ModpackInfo pack);

        void onNavigate(String key);

        void onReconnect();
    }

    private final Listener listener;
    private final JPanel packList = Ui.column();
    private final JLabel connectionText = Ui.faint("Checking");
    private final JLabel connectionDot = new JLabel();
    private final JLabel librarySection = Ui.sectionLabel("Library");
    private final List<Row> rows = new ArrayList<>();
    private final JPanel updateSlot = Ui.column();
    private Row selected;
    private Color dotColor = Theme.WARNING;

    public Sidebar(Listener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        setPreferredSize(new Dimension(Theme.SIDEBAR_WIDTH, 0));
        setMinimumSize(new Dimension(Theme.SIDEBAR_WIDTH, 0));

        add(buildBrand(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JComponent buildBrand() {
        JPanel brand = Ui.column();
        brand.setBorder(Ui.pad(Theme.GAP_L, Theme.GAP_M, Theme.GAP_M, Theme.GAP_M));

        JPanel row = Ui.row();
        row.add(new JLabel(new LogoIcon(28)));
        row.add(Ui.strut(Theme.GAP_S + 2));
        JPanel stack = Ui.column();
        stack.add(Ui.label("GroidPack", Theme.font(Font.BOLD, 14), Theme.TEXT));
        stack.add(Ui.faint("Updater"));
        row.add(stack);
        row.add(Ui.glue());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(row);
        brand.add(Ui.strut(Theme.GAP_M));
        brand.add(buildConnection());
        return brand;
    }

    private JComponent buildConnection() {
        Ui.RoundPanel pill = new Ui.RoundPanel(Theme.SURFACE_ALT, Theme.BORDER, 999);
        pill.setLayout(new BorderLayout(Theme.GAP_S, 0));
        pill.setBorder(Ui.pad(7, 11, 7, 11));
        pill.setCursor(new Cursor(Cursor.HAND_CURSOR));
        pill.setToolTipText("Reconnect");

        connectionDot.setIcon(new DotIcon());
        pill.add(connectionDot, BorderLayout.WEST);
        pill.add(connectionText, BorderLayout.CENTER);
        pill.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                listener.onReconnect();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                pill.setFill(Theme.SURFACE_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                pill.setFill(Theme.SURFACE_ALT);
            }
        });
        pill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return pill;
    }

    private JComponent buildCenter() {
        JPanel holder = Ui.column();
        holder.setBorder(Ui.pad(0, Theme.GAP_S, 0, Theme.GAP_S));

        librarySection.setBorder(Ui.pad(Theme.GAP_S, Theme.GAP_S, Theme.GAP_S, Theme.GAP_S));
        holder.add(librarySection);
        holder.add(packList);
        holder.add(Ui.strut(Theme.GAP_S));

        Row browse = Row.nav(Icons.PLUS, "Add modpack");
        browse.setOnClick(() -> {
            select(browse);
            listener.onNavigate(BROWSE);
        });
        rows.add(browse);
        holder.add(browse);
        holder.add(Ui.glue());

        JScrollPane scroll = Ui.scroll(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private JComponent buildFooter() {
        JPanel footer = Ui.column();
        footer.setBorder(Ui.pad(Theme.GAP_S, Theme.GAP_S, Theme.GAP_M, Theme.GAP_S));
        footer.add(updateSlot);
        footer.add(Ui.divider());
        footer.add(Ui.strut(Theme.GAP_S));
        footer.add(navEntry(Icons.SETTINGS, "Settings", SETTINGS));
        footer.add(navEntry(Icons.HELP, "Help", HELP));
        footer.add(navEntry(Icons.ADMIN, "Admin", ADMIN));
        return footer;
    }

    private Row navEntry(String icon, String text, String key) {
        Row row = Row.nav(icon, text);
        row.setOnClick(() -> {
            select(row);
            listener.onNavigate(key);
        });
        rows.add(row);
        return row;
    }

    public void setSoftwareUpdate(String text, Runnable onClick) {
        updateSlot.removeAll();
        if (text != null && onClick != null) {
            Ui.RoundPanel banner = new Ui.RoundPanel(Theme.ACCENT_SOFT, Theme.ACCENT, 8);
            banner.setLayout(new BorderLayout(Theme.GAP_S, 0));
            banner.setBorder(Ui.pad(9, 10, 9, 10));
            banner.setCursor(new Cursor(Cursor.HAND_CURSOR));
            banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            banner.add(new JLabel(Icons.of(Icons.DOWNLOAD, 14, Theme.ACCENT)), BorderLayout.WEST);
            banner.add(Ui.label(text, Theme.font(Font.BOLD, 11), Theme.TEXT), BorderLayout.CENTER);
            banner.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }
            });
            updateSlot.add(banner);
            updateSlot.add(Ui.strut(Theme.GAP_S));
        }
        updateSlot.revalidate();
        updateSlot.repaint();
    }

    public void setConnection(boolean connected, String text) {
        dotColor = connected ? Theme.SUCCESS : Theme.DANGER;
        connectionText.setText(text);
        connectionText.setForeground(connected ? Theme.TEXT_FAINT : Theme.DANGER);
        connectionDot.repaint();
    }

    public void setConnecting() {
        dotColor = Theme.WARNING;
        connectionText.setText("Connecting");
        connectionText.setForeground(Theme.TEXT_FAINT);
        connectionDot.repaint();
    }

    public void setPacks(List<ModpackInfo> packs, Map<String, Integer> pending) {
        rows.removeIf(row -> row.pack != null);
        packList.removeAll();

        librarySection.setText(packs.isEmpty() ? "LIBRARY" : "LIBRARY  (" + packs.size() + ")");

        if (packs.isEmpty()) {
            JLabel empty = Ui.faint("Nothing installed");
            empty.setBorder(Ui.pad(Theme.GAP_S, Theme.GAP_S + 2, Theme.GAP_M, Theme.GAP_S));
            packList.add(empty);
        }

        for (ModpackInfo pack : packs) {
            Row row = Row.pack(pack, pending.getOrDefault(pack.getModpackId(), 0));
            row.setOnClick(() -> {
                select(row);
                listener.onSelectPack(pack);
            });
            rows.add(row);
            packList.add(row);
            packList.add(Ui.strut(2));
        }

        packList.revalidate();
        packList.repaint();
    }

    public void selectPack(String modpackId) {
        for (Row row : rows) {
            if (row.pack != null && row.pack.getModpackId().equals(modpackId)) {
                select(row);
                return;
            }
        }
    }

    private void select(Row row) {
        if (selected == row) {
            return;
        }
        if (selected != null) {
            selected.setSelected(false);
        }
        selected = row;
        if (selected != null) {
            selected.setSelected(true);
        }
    }

    private final class DotIcon implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.alpha(dotColor, 70));
            g2.fill(new Ellipse2D.Double(x, y, 10, 10));
            g2.setColor(dotColor);
            g2.fill(new Ellipse2D.Double(x + 2.5, y + 2.5, 5, 5));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }

    private static final class Row extends JPanel {

        private final ModpackInfo pack;
        private final String iconName;
        private final JLabel title;
        private final JLabel subtitle;
        private final JLabel art;
        private Color background = Theme.SURFACE;
        private boolean selectedState;
        private float selectBar;
        private Runnable onClick;
        private Animator.Handle hoverHandle;
        private Animator.Handle barHandle;

        private Row(String iconName, String label, ModpackInfo pack, int updates) {
            this.pack = pack;
            this.iconName = iconName;
            setOpaque(false);
            setLayout(new BorderLayout(Theme.GAP_S + 2, 0));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            if (pack != null) {
                setBorder(Ui.pad(7, Theme.GAP_S, 7, Theme.GAP_S));
                art = new JLabel(Icons.of(Icons.PACKAGE, PACK_ART - 10, Theme.TEXT_FAINT));
                art.setHorizontalAlignment(JLabel.CENTER);
                Ui.fixSize(art, PACK_ART, PACK_ART);
                add(art, BorderLayout.WEST);

                JPanel stack = Ui.column();
                title = Ui.label(label, Theme.font(Font.BOLD, 12), Theme.TEXT);
                subtitle = Ui.faint(updates > 0
                        ? (updates == 1 ? "1 update" : updates + " updates")
                        : "Up to date");
                subtitle.setForeground(updates > 0 ? Theme.WARNING : Theme.TEXT_FAINT);
                stack.add(Ui.glue());
                stack.add(title);
                stack.add(Ui.strut(2));
                stack.add(subtitle);
                stack.add(Ui.glue());
                add(stack, BorderLayout.CENTER);

                setMaximumSize(new Dimension(Integer.MAX_VALUE, PACK_ART + 14));
                setPreferredSize(new Dimension(Theme.SIDEBAR_WIDTH, PACK_ART + 14));
                loadArtwork();
            } else {
                setBorder(Ui.pad(8, Theme.GAP_S + 2, 8, Theme.GAP_S));
                art = new JLabel(Icons.of(iconName, 16, Theme.TEXT_DIM));
                add(art, BorderLayout.WEST);
                title = Ui.label(label, Theme.small(), Theme.TEXT_DIM);
                subtitle = null;
                add(title, BorderLayout.CENTER);
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            }

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) {
                        onClick.run();
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    animateBackground(selectedState ? Theme.SURFACE_HOVER : Theme.SURFACE_ALT);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    animateBackground(selectedState ? Theme.SURFACE_ALT : Theme.SURFACE);
                }
            });
        }

        static Row nav(String iconName, String label) {
            return new Row(iconName, label, null, 0);
        }

        static Row pack(ModpackInfo pack, int updates) {
            String name = pack.getDisplayName();
            if (name == null || name.isBlank()) {
                name = pack.getModpackId();
            }
            return new Row(null, name == null ? pack.getRoot() : name, pack, updates);
        }

        void setOnClick(Runnable onClick) {
            this.onClick = onClick;
        }

        void setSelected(boolean value) {
            selectedState = value;
            title.setForeground(value || pack != null ? Theme.TEXT : Theme.TEXT_DIM);
            if (pack == null) {
                title.setFont(value ? Theme.font(Font.BOLD, 12) : Theme.small());
                art.setIcon(Icons.of(iconName, 16, value ? Theme.TEXT : Theme.TEXT_DIM));
            }
            animateBackground(value ? Theme.SURFACE_ALT : Theme.SURFACE);

            if (barHandle != null) {
                barHandle.stop();
            }
            float from = selectBar;
            float to = value ? 1f : 0f;
            barHandle = Animator.tween(Animator.NORMAL, progress -> {
                selectBar = (float) Animator.lerp(from, to, progress);
                repaint();
            });
        }

        private void animateBackground(Color target) {
            if (hoverHandle != null) {
                hoverHandle.stop();
            }
            Color from = background;
            hoverHandle = Animator.tween(Animator.FAST, progress -> {
                background = Theme.mix(from, target, progress);
                repaint();
            });
        }

        private void loadArtwork() {
            new SwingWorker<Icon, Void>() {
                @Override
                protected Icon doInBackground() {
                    return PackArtwork.load(pack, true, PACK_ART);
                }

                @Override
                protected void done() {
                    try {
                        Icon icon = get();
                        if (icon != null) {
                            art.setIcon(icon);
                        }
                    } catch (Exception e) {
                        art.setIcon(Icons.of(Icons.PACKAGE, PACK_ART - 10, Theme.TEXT_FAINT));
                    }
                }
            }.execute();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(background);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
            if (selectBar > 0.01f) {
                g2.setColor(Theme.ACCENT);
                double height = getHeight() * 0.55 * selectBar;
                double y = (getHeight() - height) / 2.0;
                g2.fill(new RoundRectangle2D.Double(0, y, 3, height, 3, 3));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class LogoIcon implements Icon {

        private final int size;

        LogoIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.BG);
            g2.fill(new RoundRectangle2D.Double(x, y, size, size, size * 0.3, size * 0.3));
            g2.setColor(Theme.BORDER_STRONG);
            g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, size - 1.0, size - 1.0,
                    size * 0.3, size * 0.3));
            int inner = (int) Math.round(size * 0.56);
            Icons.of(Icons.PACKAGE, inner, Theme.TEXT)
                    .paintIcon(c, g2, x + (size - inner) / 2, y + (size - inner) / 2);
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
