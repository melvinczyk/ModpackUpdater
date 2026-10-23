package com.nicholasburczyk.packupdater.ui.dash;

import com.nicholasburczyk.packupdater.ui.FrameClock;
import com.nicholasburczyk.packupdater.ui.Theme;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Channel list fanned around a glowing orb in the top-left corner, after the original Xbox
 * dashboard: every channel is a slanted glass bar on a socket, and a lit bar slides to the active one.
 */
final class ChannelMenu extends JComponent {

    record Item(String id, String name, int badge) {
    }

    private static final double ORB_X = 72;
    private static final double ORB_Y = 80;
    private static final int ORB_R = 46;
    private static final double ARC_R = 82;
    private static final double ROW = 36;
    private static final double BAR_H = 30;
    private static final double SKEW = 9;
    private static final double MIN_BAR_W = 250;
    private static final double COMPACT_X = 22;
    private static final double EASE = 0.2;
    private static final double EASE_FRAME_NANOS = 16_666_667;
    private static final double PULSE_SECONDS = 0.55;
    private static final long ORIGIN = System.nanoTime();
    private static final Orb ORB = new Orb(ORB_R);

    private final int gutter;
    private final Consumer<String> onPick;
    private final FrameClock timer = new FrameClock(16, this::step);
    private final List<Rectangle> hits = new ArrayList<>();
    private List<Item> items = List.of();
    private int active;
    private int hovered = -1;
    private double pos;
    private boolean compact;
    private double compactness;
    private long lastStep;
    private long pulseStart;
    private java.awt.image.BufferedImage chrome;

    ChannelMenu(int gutter, Consumer<String> onPick) {
        this.gutter = gutter;
        this.onPick = onPick;
        setOpaque(false);
        setPreferredSize(new Dimension(600, 150));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = hitTest(e.getX(), e.getY());
                if (index >= 0) {
                    onPick.accept(items.get(index).id());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover(-1);
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hover(hitTest(e.getX(), e.getY()));
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
        if (first) {
            pos = index;
        } else if (index != active) {
            pulseStart = System.nanoTime();
        }
        active = index;
        chrome = null;
        animate();
        repaint();
    }

    void setCompact(boolean value) {
        compact = value;
        animate();
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

    private void hover(int index) {
        if (index != hovered) {
            hovered = index;
            chrome = null;
            setCursor(index >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }
    }

    private int hitTest(int x, int y) {
        for (int i = 0; i < hits.size() && i < items.size(); i++) {
            Rectangle hit = hits.get(i);
            if (hit != null && hit.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void animate() {
        if (!timer.isRunning()) {
            lastStep = System.nanoTime();
            timer.start();
        }
    }

    private void step() {
        long now = System.nanoTime();
        double k = 1 - Math.pow(1 - EASE, Math.min(4, (now - lastStep) / EASE_FRAME_NANOS));
        lastStep = now;
        pos += (active - pos) * k;
        double target = compact ? 1 : 0;
        compactness += (target - compactness) * k;
        boolean pulsing = (now - pulseStart) / 1e9 < PULSE_SECONDS;
        if (Math.abs(active - pos) < 0.002 && Math.abs(target - compactness) < 0.002 && !pulsing) {
            pos = active;
            compactness = target;
            timer.stop();
        }
        repaint();
    }

    private double rowY(double index) {
        double list = items.size() * ROW - (ROW - BAR_H);
        double top = ORB_Y - list / 2;
        double y = top + index * ROW;
        double compactY = (getHeight() - BAR_H) / 2.0 - (top + pos * ROW);
        return y + compactY * compactness;
    }

    private double socketX(double index) {
        double list = items.size() * ROW - (ROW - BAR_H);
        double dy = ORB_Y - list / 2 + index * ROW + BAR_H / 2 - ORB_Y;
        return ORB_X + Math.sqrt(Math.max(0, ARC_R * ARC_R - dy * dy));
    }

    private double barX(double index) {
        double open = Math.max(gutter - 12, socketX(index) + 16);
        return open + (COMPACT_X - open) * compactness;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        hints(g2);
        double t = (System.nanoTime() - ORIGIN) / 1e9;
        double since = (System.nanoTime() - pulseStart) / 1e9;
        double flash = since < PULSE_SECONDS * 1.6 ? 1 - since / (PULSE_SECONDS * 1.6) : 0;
        double open = 1 - compactness;
        if (open > 0.01) {
            ORB.paint(g2, ORB_X, ORB_Y, 0.6 + 0.4 * open, open, t, flash);
        }
        if (timer.isRunning() || getWidth() <= 0 || getHeight() <= 0) {
            chrome = null;
            paintChrome(g2, open, flash);
        } else {
            if (chrome == null || chrome.getWidth() != getWidth() || chrome.getHeight() != getHeight()) {
                java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
                chrome = gc != null ? gc.createCompatibleImage(getWidth(), getHeight(), java.awt.Transparency.TRANSLUCENT)
                        : new java.awt.image.BufferedImage(getWidth(), getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D c = chrome.createGraphics();
                hints(c);
                paintChrome(c, open, 0);
                c.dispose();
            }
            g2.drawImage(chrome, 0, 0, null);
        }
        if (open > 0.01 && since < PULSE_SECONDS && active < items.size()) {
            paintPulse(g2, active, since / PULSE_SECONDS, open);
        }
        g2.dispose();
    }

    private static void hints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void paintChrome(Graphics2D g2, double open, double flash) {
        Font font = Theme.font(Font.BOLD, 17);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        double[] widths = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            widths[i] = Math.max(MIN_BAR_W, fm.stringWidth(item.name()) + 64 + (item.badge() > 0 ? 34 : 0));
        }

        if (open > 0.01) {
            for (int i = 0; i < items.size(); i++) {
                paintConnector(g2, i, open, i == active ? Math.max(0, 1 - Math.abs(i - pos)) : 0);
            }
        }

        hits.clear();
        for (int i = 0; i < items.size(); i++) {
            double alpha = i == active ? 1 : open;
            double x = barX(i);
            double y = rowY(i);
            if (alpha > 0.01) {
                paintBar(g2, x, y, widths[i], alpha, i == hovered && i != active);
            }
            hits.add(alpha > 0.5 ? new Rectangle((int) x, (int) y, (int) (widths[i] + SKEW), (int) BAR_H) : null);
        }

        if (!items.isEmpty()) {
            int lo = (int) Math.floor(pos);
            int hi = Math.min(items.size() - 1, lo + 1);
            double f = pos - lo;
            double x = barX(lo) + (barX(hi) - barX(lo)) * f;
            double w = widths[lo] + (widths[hi] - widths[lo]) * f;
            paintLit(g2, x, rowY(pos), w, flash);
        }

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            double alpha = i == active ? 1 : open;
            if (alpha <= 0.01) {
                continue;
            }
            double focus = Math.max(0, 1 - Math.abs(i - pos) * 1.4);
            double x = barX(i) + 20;
            double y = rowY(i);
            float baseline = (float) Math.round(y + (BAR_H - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent());
            Color text = Theme.mix(i == hovered ? Theme.TEXT : Theme.TEXT_DIM, new Color(0x072A0B), focus);
            g2.setColor(Theme.alpha(text, (int) (255 * alpha)));
            g2.drawString(item.name(), (float) Math.round(x), baseline);
            if (item.badge() > 0) {
                paintBadge(g2, item.badge(), x + fm.stringWidth(item.name()) + 12, y + BAR_H / 2, 0.8, alpha);
            }
        }
    }

    private static Path2D slant(double x, double y, double w, double h) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + SKEW, y);
        path.lineTo(x + w + SKEW, y);
        path.lineTo(x + w, y + h);
        path.lineTo(x, y + h);
        path.closePath();
        return path;
    }

    private void paintConnector(Graphics2D g2, int index, double alpha, double lit) {
        double y = rowY(index) + BAR_H / 2;
        double sx = socketX(index);
        double angle = Math.atan2(y - ORB_Y, sx - ORB_X);
        double fromX = ORB_X + Math.cos(angle) * (ORB_R + 3);
        double fromY = ORB_Y + Math.sin(angle) * (ORB_R + 3);
        double barX = barX(index);
        g2.setStroke(new BasicStroke(1.4f));
        g2.setColor(Theme.alpha(Theme.mix(new Color(0x3E8A3A), Theme.LIME, lit), (int) ((70 + 120 * lit) * alpha)));
        g2.draw(new Line2D.Double(fromX, fromY, sx - 7, y));
        g2.draw(new Line2D.Double(sx + 7, y, barX + 2, y));

        Ellipse2D socket = new Ellipse2D.Double(sx - 6.5, y - 6.5, 13, 13);
        g2.setColor(Theme.alpha(new Color(0x041208), (int) (230 * alpha)));
        g2.fill(socket);
        if (lit > 0.01) {
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(sx, y + 2), 7f, new float[]{0f, 1f},
                    new Color[]{Theme.alpha(new Color(0xF0FF9A), (int) (255 * lit * alpha)),
                            Theme.alpha(new Color(0x4CC41C), (int) (220 * lit * alpha))}));
            g2.fill(new Ellipse2D.Double(sx - 4.5, y - 4.5, 9, 9));
        }
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(Theme.alpha(new Color(0x7FD86A), (int) (170 * alpha)));
        g2.draw(socket);
        g2.setColor(Theme.alpha(Color.WHITE, (int) (90 * alpha)));
        g2.draw(new java.awt.geom.Arc2D.Double(sx - 5, y - 5, 10, 10, 40, 100, java.awt.geom.Arc2D.OPEN));
    }

    private void paintPulse(Graphics2D g2, int index, double progress, double alpha) {
        double y = rowY(index) + BAR_H / 2;
        double sx = socketX(index);
        double angle = Math.atan2(y - ORB_Y, sx - ORB_X);
        double fromX = ORB_X + Math.cos(angle) * ORB_R;
        double fromY = ORB_Y + Math.sin(angle) * ORB_R;
        double p = 1 - Math.pow(1 - progress, 2);
        double px = fromX + (barX(index) - fromX) * p;
        double py = fromY + (y - fromY) * Math.min(1, p * 1.6);
        float fade = (float) (alpha * Math.sin(Math.PI * Math.min(1, progress * 1.1)));
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), 11f, new float[]{0f, 0.3f, 1f},
                new Color[]{Theme.alpha(Color.WHITE, (int) (255 * fade)),
                        Theme.alpha(Theme.LIME, (int) (170 * fade)), Theme.alpha(Theme.LIME, 0)}));
        g2.fill(new Ellipse2D.Double(px - 11, py - 11, 22, 22));
    }

    private static void paintBar(Graphics2D g2, double x, double y, double w, double alpha, boolean hover) {
        Path2D bar = slant(x, y, w, BAR_H);
        int a = (int) (255 * alpha);
        g2.setPaint(new LinearGradientPaint(0f, (float) y, 0f, (float) (y + BAR_H), new float[]{0f, 0.5f, 1f},
                new Color[]{Theme.alpha(new Color(hover ? 0x1D4F26 : 0x15391C), (int) (a * 0.86)),
                        Theme.alpha(new Color(0x0B2210), (int) (a * 0.86)),
                        Theme.alpha(new Color(0x07170A), (int) (a * 0.86))}));
        g2.fill(bar);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(Theme.alpha(new Color(hover ? 0x7FD86A : 0x3E7A44), (int) (a * 0.75)));
        g2.draw(bar);
        g2.setColor(Theme.alpha(new Color(0xCFFFC0), (int) (a * 0.22)));
        g2.draw(new Line2D.Double(x + SKEW + 2, y + 1.5, x + w + SKEW - 3, y + 1.5));
    }

    private static void paintLit(Graphics2D g2, double x, double y, double w, double flash) {
        Path2D bar = slant(x - 1, y - 1, w + 2, BAR_H + 2);
        g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.alpha(Theme.LIME, (int) (22 + 30 * flash)));
        g2.draw(bar);
        g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.alpha(Theme.LIME, (int) (46 + 40 * flash)));
        g2.draw(bar);
        g2.setPaint(new LinearGradientPaint(0f, (float) y, 0f, (float) (y + BAR_H), new float[]{0f, 0.48f, 0.52f, 1f},
                new Color[]{new Color(0xF1FFB0), new Color(0xA8F244), new Color(0x5CC522), new Color(0x8CE83A)}));
        g2.fill(bar);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(0xF6FFD6));
        g2.draw(bar);
        g2.setColor(new Color(255, 255, 255, 150));
        g2.draw(new Line2D.Double(x + SKEW + 2, y + 1, x + w + SKEW - 2, y + 1));
    }

    private static void paintBadge(Graphics2D g2, int count, double x, double centerY, double scale, double alpha) {
        String text = String.valueOf(count);
        Font old = g2.getFont();
        Font font = Theme.font(Font.BOLD, Math.max(9, (int) Math.round(13 * scale)));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        double h = 22 * scale;
        double w = Math.max(h, fm.stringWidth(text) + 14 * scale);
        double y = centerY - h / 2;
        int a = (int) (255 * alpha);
        RoundRectangle2D pill = new RoundRectangle2D.Double(x, y, w, h, h, h);
        g2.setColor(Theme.alpha(Theme.UPDATE, (int) (90 * alpha)));
        g2.fill(new RoundRectangle2D.Double(x - 2, y - 2, w + 4, h + 4, h + 4, h + 4));
        g2.setPaint(new LinearGradientPaint(0f, (float) y, 0f, (float) (y + h),
                new float[]{0f, 0.5f, 0.51f, 1f},
                new Color[]{new Color(0xFF, 0xD4, 0xD0, a), new Color(0xFF, 0x6A, 0x5E, a),
                        new Color(0xD8, 0x1E, 0x1E, a), new Color(0xFF, 0x3B, 0x3B, a)}));
        g2.fill(pill);
        g2.setColor(new Color(0xFF, 0xFF, 0xFF, a));
        float tx = (float) (x + (w - fm.stringWidth(text)) / 2);
        float ty = (float) (y + (h - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent());
        g2.drawString(text, tx, ty);
        g2.setFont(old);
    }
}
