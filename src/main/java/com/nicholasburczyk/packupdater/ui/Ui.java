package com.nicholasburczyk.packupdater.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public final class Ui {

    /** Gloss palettes: top, mid (just above the gloss split), split, bottom, rim, glow, text. */
    public enum ButtonStyle {
        PRIMARY(0x6BE33A, 0x2AA316, 0x127A09, 0x33C41A, 0xBEFF96, 0x5AFF28, 0xFFFFFF),
        SECONDARY(0x2C3A31, 0x18221B, 0x0B110D, 0x172019, 0x4F7A57, 0x78FF50, 0xD9EFDC),
        SUCCESS(0x6BE33A, 0x2AA316, 0x127A09, 0x33C41A, 0xBEFF96, 0x5AFF28, 0xFFFFFF),
        DANGER(0xFF7A6B, 0xD9281C, 0xA3130C, 0xF0453A, 0xFFD0C9, 0xFF4632, 0xFFFFFF),
        GHOST(0, 0, 0, 0, 0, 0x78FF50, 0xA3BCA8);

        final Color top;
        final Color mid;
        final Color split;
        final Color bottom;
        final Color rim;
        final Color glow;
        final Color foreground;

        ButtonStyle(int top, int mid, int split, int bottom, int rim, int glow, int foreground) {
            this.top = new Color(top);
            this.mid = new Color(mid);
            this.split = new Color(split);
            this.bottom = new Color(bottom);
            this.rim = new Color(rim);
            this.glow = new Color(glow);
            this.foreground = new Color(foreground);
        }
    }

    private Ui() {
    }

    public static RoundPanel card() {
        return new RoundPanel(Theme.SURFACE, Theme.BORDER, Theme.RADIUS);
    }

    public static RoundPanel card(Color background) {
        return new RoundPanel(background, Theme.BORDER, Theme.RADIUS);
    }

    public static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public static JPanel row() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        return panel;
    }

    public static JPanel column(int gap, Component... children) {
        JPanel panel = column();
        addAll(panel, gap, children);
        return panel;
    }

    public static JPanel row(int gap, Component... children) {
        JPanel panel = row();
        addAll(panel, gap, children);
        return panel;
    }

    public static void addAll(JPanel parent, int gap, Component... children) {
        for (int i = 0; i < children.length; i++) {
            if (i > 0 && gap > 0) {
                parent.add(strut(gap));
            }
            parent.add(children[i]);
        }
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public static JLabel display(String text) {
        return label(text, Theme.display(), Theme.TEXT);
    }

    public static JLabel title(String text) {
        return label(text, Theme.title(), Theme.TEXT);
    }

    public static JLabel heading(String text) {
        return label(text, Theme.heading(), Theme.TEXT);
    }

    public static JLabel body(String text) {
        return label(text, Theme.body(), Theme.TEXT);
    }

    public static JLabel dim(String text) {
        return label(text, Theme.small(), Theme.TEXT_DIM);
    }

    public static JLabel faint(String text) {
        return label(text, Theme.tiny(), Theme.TEXT_FAINT);
    }

    public static JLabel sectionLabel(String text) {
        JLabel label = label(text.toUpperCase(), Theme.label(), Theme.TEXT_FAINT);
        label.setBorder(pad(0, 2, 0, 0));
        return label;
    }

    public static JLabel wrapped(String text, Color color) {
        JLabel label = new JLabel("<html><body style='width:100%'>" + escape(text) + "</body></html>");
        label.setFont(Theme.small());
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public static JButton button(String text, String iconName, ButtonStyle style) {
        return button(text, iconName, style, 16);
    }

    public static JButton button(String text, String iconName, ButtonStyle style, int iconSize) {
        GlossButton button = new GlossButton(text, style);
        if (iconName != null) {
            button.setIcon(Icons.of(iconName, iconSize, style.foreground));
            button.setDisabledIcon(Icons.of(iconName, iconSize, Theme.TEXT_FAINT));
        }
        return button;
    }

    public static JButton iconButton(String iconName, String tooltip) {
        return iconButton(iconName, tooltip, Theme.TEXT_DIM, 17);
    }

    public static JButton iconButton(String iconName, String tooltip, Color color, int size) {
        JButton button = new JButton(Icons.of(iconName, size, color));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        Icon normal = Icons.of(iconName, size, color);
        Icon bright = Icons.of(iconName, size, Theme.TEXT);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setIcon(bright);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setIcon(normal);
            }
        });
        return button;
    }


    public static JComponent chip(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, 11));
        label.setForeground(color);
        label.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        RoundPanel holder = new RoundPanel(Theme.alpha(color, 36), Theme.alpha(color, 86), 999) {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        holder.setLayout(new BorderLayout());
        holder.add(label, BorderLayout.CENTER);
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        return holder;
    }

    public static JLabel statusDot(Color color) {
        return new JLabel(new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(color, 60));
                g2.fill(new Ellipse2D.Double(x, y, 10, 10));
                g2.setColor(color);
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
        });
    }

    public static JTextField field(String placeholder) {
        JTextField field = new JTextField();
        field.setFont(Theme.body());
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }

    public static JPasswordField passwordField(String placeholder) {
        JPasswordField field = new JPasswordField();
        field.setFont(Theme.body());
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }

    public static JTextArea readOnlyText(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.small());
        area.setForeground(Theme.TEXT_DIM);
        area.setBackground(Theme.SURFACE);
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    public static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    public static Component glue() {
        return Box.createGlue();
    }

    public static Component strut(int size) {
        return Box.createRigidArea(new Dimension(size, size));
    }

    public static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static Border pad(int all) {
        return BorderFactory.createEmptyBorder(all, all, all, all);
    }

    public static JComponent divider() {
        JPanel line = new JPanel();
        line.setBackground(Theme.BORDER);
        line.setPreferredSize(new Dimension(1, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    public static void capHeight(JComponent component, int height) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    public static void fixSize(JComponent component, int width, int height) {
        Dimension size = new Dimension(width, height);
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        component.setMaximumSize(size);
    }

    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Frutiger-style glossy button: rounded body with a hard gloss split, a white specular cap,
     * a bright rim, a soft glow that fades in on hover and a small squish while pressed.
     */
    public static final class GlossButton extends JButton {

        private static final int INSET = 3;

        private final ButtonStyle style;
        private float hover;
        private float press;
        private Animator.Handle hoverAnim;
        private Animator.Handle pressAnim;

        GlossButton(String text, ButtonStyle style) {
            super(text);
            this.style = style;
            setFont(Theme.font(Font.BOLD, 12));
            setForeground(style.foreground);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setIconTextGap(Theme.GAP_S);
            setBorder(BorderFactory.createEmptyBorder(8 + INSET, 14 + INSET, 8 + INSET, 14 + INSET));
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hoverAnim = animate(hoverAnim, hover, 1f, Animator.NORMAL, v -> hover = v);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverAnim = animate(hoverAnim, hover, 0f, Animator.SLOW, v -> hover = v);
                }
            });
            getModel().addChangeListener(e -> {
                float target = getModel().isPressed() && getModel().isArmed() ? 1f : 0f;
                if ((target == 1f) != (press > 0.5f)) {
                    pressAnim = animate(pressAnim, press, target, target == 1f ? 70 : Animator.NORMAL, v -> press = v);
                }
            });
        }

        private Animator.Handle animate(Animator.Handle running, float from, float to, int millis,
                                        java.util.function.Consumer<Float> apply) {
            if (running != null) {
                running.stop();
            }
            return Animator.tween(millis, 0, p -> {
                apply.accept((float) Animator.lerp(from, to, p));
                repaint();
            }, null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            double scale = 1 - 0.05 * press;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            boolean enabled = isEnabled();
            if (!enabled) {
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
            }
            double x = INSET;
            double y = INSET;
            double bw = w - INSET * 2.0;
            double bh = h - INSET * 2.0;
            double arc = Math.min(bh, 20);

            if (style == ButtonStyle.GHOST) {
                if (hover > 0.01f) {
                    g2.setColor(Theme.alpha(style.glow, Math.round(26 * hover)));
                    g2.fill(new RoundRectangle2D.Double(x, y, bw, bh, arc, arc));
                }
                setForeground(Theme.mix(style.foreground, Theme.TEXT, hover));
                g2.dispose();
                super.paintComponent(g);
                return;
            }

            if (enabled && hover > 0.01f) {
                for (int i = INSET; i >= 1; i--) {
                    g2.setColor(Theme.alpha(style.glow, Math.round(34 * hover / i)));
                    g2.fill(new RoundRectangle2D.Double(x - i, y - i, bw + i * 2, bh + i * 2, arc + i * 2, arc + i * 2));
                }
            }

            RoundRectangle2D body = new RoundRectangle2D.Double(x, y, bw, bh, arc, arc);
            float lift = enabled ? 0.12f * hover : 0f;
            g2.setPaint(new java.awt.LinearGradientPaint(0f, (float) y, 0f, (float) (y + bh),
                    new float[]{0f, 0.49f, 0.5f, 1f},
                    new Color[]{Theme.mix(style.top, Color.WHITE, lift), Theme.mix(style.mid, Color.WHITE, lift),
                            style.split, Theme.mix(style.bottom, Color.WHITE, lift)}));
            g2.fill(body);

            Shape old = g2.getClip();
            g2.clip(body);
            g2.setPaint(new java.awt.GradientPaint(0f, (float) y, new Color(255, 255, 255, 105),
                    0f, (float) (y + bh * 0.5), new Color(255, 255, 255, 12)));
            g2.fill(new RoundRectangle2D.Double(x + 1, y + 1, bw - 2, bh * 0.5, arc - 2, arc - 2));
            g2.setClip(old);

            g2.setColor(Theme.alpha(style.rim, 150 + Math.round(60 * hover)));
            g2.setStroke(new java.awt.BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, bw - 1, bh - 1, arc, arc));
            g2.dispose();

            Graphics2D text = (Graphics2D) g.create();
            text.translate(w / 2.0, h / 2.0);
            text.scale(scale, scale);
            text.translate(-w / 2.0, -h / 2.0);
            super.paintComponent(text);
            text.dispose();
        }
    }

    public static class RoundPanel extends JPanel {

        private Color fill;
        private Color line;
        private final int radius;

        public RoundPanel(Color fill, Color line, int radius) {
            this.fill = fill;
            this.line = line;
            this.radius = radius;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        public void setFill(Color fill) {
            this.fill = fill;
            repaint();
        }

        public void setLine(Color line) {
            this.line = line;
            repaint();
        }

        public Color fill() {
            return fill;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = Math.min(radius * 2, Math.min(getWidth(), getHeight()));
            RoundRectangle2D shape = new RoundRectangle2D.Double(
                    0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, arc, arc);
            boolean glass = fill != null && fill.getAlpha() == 255;
            if (glass) {
                // Translucent glass over the aurora: lit from above, darker at the base.
                g2.setPaint(new java.awt.GradientPaint(0f, 0f, Theme.alpha(Theme.mix(fill, Theme.LIME, 0.07), 205),
                        0f, getHeight(), Theme.alpha(fill, 228)));
                g2.fill(shape);
                g2.setPaint(new java.awt.GradientPaint(0f, 0f, new Color(255, 255, 255, 20),
                        0f, Math.min(getHeight(), 90), new Color(255, 255, 255, 0)));
                g2.fill(shape);
            } else if (fill != null) {
                g2.setColor(fill);
                g2.fill(shape);
            }
            if (line != null) {
                if (glass) {
                    g2.setPaint(new java.awt.GradientPaint(0f, 0f, Theme.alpha(Theme.LIME, 70),
                            0f, getHeight(), Theme.alpha(line, 160)));
                } else {
                    g2.setColor(line);
                }
                g2.draw(shape);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
