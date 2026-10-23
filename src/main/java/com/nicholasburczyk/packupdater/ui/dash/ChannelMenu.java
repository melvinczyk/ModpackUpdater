package com.nicholasburczyk.packupdater.ui.dash;

import com.nicholasburczyk.packupdater.ui.Theme;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vertical channel list in the top-left corner, like the Xbox 360 dashboard: the active channel
 * sits large at the bottom of the stack, earlier channels shrink and fade upwards.
 */
final class ChannelMenu extends JComponent {

    record Item(String id, String name, int badge) {
    }

    private static final int BASE_SIZE = 30;

    private final int gutter;
    private final Consumer<String> onPick;
    private final Timer timer = new Timer(16, e -> step());
    private final List<Rectangle> hits = new ArrayList<>();
    private List<Item> items = List.of();
    private int active;
    private double pos;
    private boolean compact;
    private double compactness;

    ChannelMenu(int gutter, Consumer<String> onPick) {
        this.gutter = gutter;
        this.onPick = onPick;
        setOpaque(false);
        setPreferredSize(new Dimension(600, 150));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (int i = 0; i < hits.size(); i++) {
                    Rectangle hit = hits.get(i);
                    if (hit != null && hit.contains(e.getPoint()) && i < items.size()) {
                        onPick.accept(items.get(i).id());
                        return;
                    }
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = hits.stream().anyMatch(r -> r != null && r.contains(e.getPoint()));
                setCursor(over ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
    }

    void setItems(List<Item> next, String activeId) {
        boolean first = items.isEmpty();
        items = List.copyOf(next);
        int index = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(activeId)) {
                index = i;
            }
        }
        active = index;
        if (first) {
            pos = active;
        }
        timer.start();
        repaint();
    }

    void setCompact(boolean value) {
        compact = value;
        timer.start();
    }

    int activeIndex() {
        return active;
    }

    int itemCount() {
        return items.size();
    }

    String idAt(int index) {
        return items.get(Math.max(0, Math.min(items.size() - 1, index))).id();
    }

    private void step() {
        pos += (active - pos) * 0.2;
        double target = compact ? 1 : 0;
        compactness += (target - compactness) * 0.2;
        if (Math.abs(active - pos) < 0.002 && Math.abs(target - compactness) < 0.002) {
            pos = active;
            compactness = target;
            timer.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        hits.clear();
        double baseline = getHeight() - 24;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            double d = i - pos;
            double scale;
            double rise;
            double alpha;
            if (d >= 0) {
                scale = 1;
                rise = -d * 18;
                alpha = Math.max(0, 1 - d * 1.8);
            } else {
                double back = -d;
                scale = back <= 1 ? 1 - 0.38 * back : Math.max(0.42, 0.62 - (back - 1) * 0.07);
                rise = back <= 1 ? back * 42 : 42 + (back - 1) * 26;
                alpha = back <= 1 ? 1 - 0.2 * back : Math.max(0, 0.8 - (back - 1) * 0.22);
            }
            if (Math.abs(d) > 0.01) {
                alpha *= 1 - compactness;
            }
            if (alpha <= 0.01) {
                hits.add(null);
                continue;
            }

            float size = (float) (BASE_SIZE * scale);
            Font font = Theme.font(Font.BOLD, Math.round(size));
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            // Whole-pixel positions keep the glyph edges crisp.
            float x = gutter;
            float y = Math.round(baseline - rise);
            double focus = 1 - Math.min(1, Math.abs(d));

            Color text = Theme.mix(Theme.TEXT_DIM, Color.WHITE, focus);
            g2.setColor(Theme.alpha(text, (int) (255 * alpha)));
            g2.drawString(item.name(), x, y);

            int textWidth = fm.stringWidth(item.name());
            if (item.badge() > 0) {
                paintBadge(g2, item.badge(), x + textWidth + 12 * scale, y - fm.getAscent() * 0.36, scale, alpha);
            }
            hits.add(new Rectangle((int) x - 26, (int) (y - fm.getAscent()), textWidth + 60, fm.getAscent() + 8));
        }
        g2.dispose();
    }

    /** Glossy red count pill, vertically centred on the text's x-height. */
    private static void paintBadge(Graphics2D g2, int count, double x, double centerY, double scale, double alpha) {
        String text = String.valueOf(count);
        Font font = Theme.font(Font.BOLD, Math.max(9, (int) Math.round(12 * scale)));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        double h = 22 * scale;
        double w = Math.max(h, fm.stringWidth(text) + 14 * scale);
        double y = centerY - h / 2;
        int a = (int) (255 * alpha);
        RoundRectangle2D pill = new RoundRectangle2D.Double(x, y, w, h, h, h);
        g2.setColor(Theme.alpha(Theme.UPDATE, (int) (90 * alpha)));
        g2.fill(new RoundRectangle2D.Double(x - 2, y - 2, w + 4, h + 4, h + 4, h + 4));
        g2.setPaint(new java.awt.LinearGradientPaint(0f, (float) y, 0f, (float) (y + h),
                new float[]{0f, 0.5f, 0.51f, 1f},
                new Color[]{new Color(0xFF, 0xD4, 0xD0, a), new Color(0xFF, 0x6A, 0x5E, a),
                        new Color(0xD8, 0x1E, 0x1E, a), new Color(0xFF, 0x3B, 0x3B, a)}));
        g2.fill(pill);
        g2.setColor(new Color(0xFF, 0xFF, 0xFF, a));
        float tx = (float) (x + (w - fm.stringWidth(text)) / 2);
        float ty = (float) (y + (h - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent());
        g2.drawString(text, tx, ty);
    }
}
