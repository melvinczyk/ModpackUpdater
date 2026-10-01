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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public final class Ui {

    public enum ButtonStyle {
        PRIMARY(Theme.ACCENT, Theme.ON_ACCENT),
        SECONDARY(Theme.SURFACE_ALT, Theme.TEXT),
        SUCCESS(Theme.SUCCESS, new Color(0x062611)),
        DANGER(Theme.DANGER, Color.WHITE),
        GHOST(null, Theme.TEXT_DIM);

        final Color background;
        final Color foreground;

        ButtonStyle(Color background, Color foreground) {
            this.background = background;
            this.foreground = foreground;
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
        JButton button = new JButton(text);
        button.setFont(Theme.font(Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setIconTextGap(Theme.GAP_S);
        button.setForeground(style.foreground);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        button.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        if (iconName != null) {
            button.setIcon(Icons.of(iconName, 15, style.foreground));
            button.setDisabledIcon(Icons.of(iconName, 15, Theme.TEXT_FAINT));
        }

        if (style == ButtonStyle.GHOST) {
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            hoverColor(button, Theme.TEXT_DIM, Theme.TEXT, button::setForeground);
        } else {
            Color base = style.background;
            button.setBackground(base);
            hoverColor(button, base, Theme.mix(base, Color.WHITE, 0.14), button::setBackground);
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

    private static void hoverColor(JComponent target, Color from, Color to,
                                   java.util.function.Consumer<Color> apply) {
        target.addMouseListener(new MouseAdapter() {
            private Animator.Handle handle;

            @Override
            public void mouseEntered(MouseEvent e) {
                if (target.isEnabled()) {
                    animate(to);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                animate(from);
            }

            private void animate(Color target2) {
                if (handle != null) {
                    handle.stop();
                }
                Color start = target2 == to ? from : to;
                handle = Animator.tween(Animator.FAST, progress ->
                        apply.accept(Theme.mix(start, target2, progress)));
            }
        });
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
            if (fill != null) {
                g2.setColor(fill);
                g2.fill(shape);
            }
            if (line != null) {
                g2.setColor(line);
                g2.draw(shape);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
