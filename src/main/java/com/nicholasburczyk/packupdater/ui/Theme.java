package com.nicholasburczyk.packupdater.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Theme {

    public static final Color BG = new Color(0x0D1117);
    public static final Color SURFACE = new Color(0x161B22);
    public static final Color SURFACE_ALT = new Color(0x1C2129);
    public static final Color SURFACE_HOVER = new Color(0x21262D);
    public static final Color BORDER = new Color(0x30363D);
    public static final Color BORDER_STRONG = new Color(0x3D444D);
    public static final Color TEXT = new Color(0xE6EDF3);
    public static final Color TEXT_DIM = new Color(0x9198A1);
    public static final Color TEXT_FAINT = new Color(0x6E7681);
    public static final Color ACCENT = new Color(0x2F81F7);
    public static final Color ACCENT_HOVER = new Color(0x4A94F8);
    public static final Color ACCENT_SOFT = new Color(0x15335C);
    public static final Color SUCCESS = new Color(0x3FB950);
    public static final Color WARNING = new Color(0xD29922);
    public static final Color DANGER = new Color(0xF85149);
    public static final Color ON_ACCENT = Color.WHITE;

    public static final int GAP_XS = 4;
    public static final int GAP_S = 8;
    public static final int GAP_M = 14;
    public static final int GAP_L = 20;
    public static final int GAP_XL = 28;
    public static final int RADIUS = 10;
    public static final int SIDEBAR_WIDTH = 278;

    private static Font baseFont;

    private Theme() {
    }

    public static void install() {
        FlatLaf.setGlobalExtraDefaults(extraDefaults());
        FlatDarkLaf.setup();

        baseFont = pickFont();
        UIManager.put("defaultFont", new FontUIResource(baseFont));

        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("ProgressBar.arc", 6);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("Button.innerFocusWidth", 0);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 10);
        UIManager.put("ScrollBar.trackArc", 10);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollPane.smoothScrolling", true);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("Button.default.boldText", true);
        UIManager.put("ProgressBar.height", 5);
        UIManager.put("ToolTip.background", SURFACE_HOVER);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
    }

    private static Map<String, String> extraDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("@accentColor", hex(ACCENT));
        defaults.put("@background", hex(BG));
        defaults.put("@foreground", hex(TEXT));
        defaults.put("@componentBackground", hex(SURFACE_ALT));
        defaults.put("@disabledText", hex(TEXT_FAINT));
        defaults.put("Component.borderColor", hex(BORDER));
        defaults.put("Component.disabledBorderColor", hex(BORDER));
        defaults.put("Button.background", hex(SURFACE_ALT));
        defaults.put("Button.hoverBackground", hex(SURFACE_HOVER));
        defaults.put("Button.focusedBorderColor", hex(ACCENT));
        defaults.put("TextField.background", hex(SURFACE_ALT));
        defaults.put("PasswordField.background", hex(SURFACE_ALT));
        defaults.put("TextArea.background", hex(SURFACE_ALT));
        defaults.put("ComboBox.background", hex(SURFACE_ALT));
        defaults.put("ComboBox.buttonBackground", hex(SURFACE_ALT));
        defaults.put("List.background", hex(SURFACE));
        defaults.put("ScrollBar.track", hex(BG));
        defaults.put("ScrollBar.thumb", hex(BORDER_STRONG));
        defaults.put("ScrollBar.hoverThumbColor", hex(TEXT_FAINT));
        defaults.put("ProgressBar.background", hex(SURFACE_HOVER));
        defaults.put("ProgressBar.foreground", hex(ACCENT));
        defaults.put("TabbedPane.underlineColor", hex(ACCENT));
        defaults.put("TabbedPane.selectedBackground", hex(SURFACE));
        defaults.put("TabbedPane.contentAreaColor", hex(BORDER));
        return defaults;
    }

    private static Font pickFont() {
        Set<String> available = Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames())
                .collect(Collectors.toSet());
        List<String> preferred = List.of(
                "Segoe UI Variable Text", "Segoe UI", "SF Pro Text", "Helvetica Neue",
                "Inter", "Roboto", "Ubuntu", "DejaVu Sans"
        );
        for (String family : preferred) {
            if (available.contains(family)) {
                return new Font(family, Font.PLAIN, 13);
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    public static Font font(int style, int size) {
        Font base = baseFont != null ? baseFont : new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return base.deriveFont(style, size);
    }

    public static Font display() {
        return font(Font.BOLD, 24);
    }

    public static Font title() {
        return font(Font.BOLD, 19);
    }

    public static Font heading() {
        return font(Font.BOLD, 14);
    }

    public static Font body() {
        return font(Font.PLAIN, 13);
    }

    public static Font small() {
        return font(Font.PLAIN, 12);
    }

    public static Font tiny() {
        return font(Font.PLAIN, 11);
    }

    public static Font label() {
        return font(Font.BOLD, 10);
    }

    public static String hex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static Color mix(Color from, Color to, double amount) {
        double clamped = Math.max(0, Math.min(1, amount));
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped));
    }
}
