package com.nicholasburczyk.packupdater.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Theme {

    public static final Color BG = new Color(0x020604);
    public static final Color SURFACE = new Color(0x0A1810);
    public static final Color SURFACE_ALT = new Color(0x102217);
    public static final Color SURFACE_HOVER = new Color(0x17301F);
    public static final Color BORDER = new Color(0x1E3A25);
    public static final Color BORDER_STRONG = new Color(0x2E5638);
    public static final Color TEXT = new Color(0xEAF7EC);
    public static final Color TEXT_DIM = new Color(0xA3BCA8);
    public static final Color TEXT_FAINT = new Color(0x6A8670);
    public static final Color ACCENT = new Color(0x4FC926);
    public static final Color ACCENT_HOVER = new Color(0x6BE33A);
    public static final Color ACCENT_SOFT = new Color(0x123A16);
    public static final Color LIME = new Color(0x8DFF3C);
    public static final Color UPDATE = new Color(0xFF3B3B);
    public static final Color SUCCESS = new Color(0x7FE04A);
    public static final Color WARNING = new Color(0xFFC23A);
    public static final Color DANGER = new Color(0xFF5B4D);
    public static final Color ON_ACCENT = Color.WHITE;

    public static final int GAP_XS = 4;
    public static final int GAP_S = 8;
    public static final int GAP_M = 14;
    public static final int GAP_L = 20;
    public static final int GAP_XL = 28;
    public static final int RADIUS = 12;

    private static final String FONT_PATH = "/com/nicholasburczyk/packupdater/fonts/";
    private static final String[] FONT_FILES = {"Exo2-Regular.ttf", "Exo2-SemiBold.ttf", "Exo2-Bold.ttf"};

    private static String family;

    private Theme() {
    }

    public static void install() {
        FlatLaf.setGlobalExtraDefaults(extraDefaults());
        FlatDarkLaf.setup();

        family = registerFonts();
        UIManager.put("defaultFont", new FontUIResource(font(Font.PLAIN, 13)));

        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
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
        defaults.put("TabbedPane.hoverColor", hex(SURFACE_HOVER));
        defaults.put("TabbedPane.focusColor", hex(SURFACE_ALT));
        defaults.put("Table.selectionBackground", hex(SURFACE_HOVER));
        defaults.put("Table.selectionForeground", hex(TEXT));
        defaults.put("TableHeader.background", hex(SURFACE_ALT));
        defaults.put("TableHeader.separatorColor", hex(BORDER));
        defaults.put("TableHeader.bottomSeparatorColor", hex(BORDER));
        defaults.put("CheckBox.icon.checkmarkColor", "#FFFFFF");
        defaults.put("CheckBox.icon.selectedBackground", hex(ACCENT));
        defaults.put("CheckBox.icon.selectedBorderColor", hex(LIME));
        defaults.put("Component.focusColor", hex(LIME));
        defaults.put("Component.focusedBorderColor", hex(ACCENT));
        defaults.put("TextComponent.selectionBackground", hex(ACCENT));
        defaults.put("List.selectionBackground", hex(ACCENT_SOFT));
        defaults.put("ComboBox.selectionBackground", hex(ACCENT_SOFT));
        defaults.put("TitlePane.background", hex(BG));
        defaults.put("TitlePane.inactiveBackground", hex(BG));
        return defaults;
    }

    /**
     * Registers the bundled Exo 2 faces so {@code new Font("Exo 2", BOLD, n)} picks the real
     * bold face instead of a synthesized one. Falls back to the platform sans if loading fails.
     */
    private static String registerFonts() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String name = null;
        for (String file : FONT_FILES) {
            try (InputStream stream = Theme.class.getResourceAsStream(FONT_PATH + file)) {
                if (stream == null) {
                    continue;
                }
                Font loaded = Font.createFont(Font.TRUETYPE_FONT, stream);
                env.registerFont(loaded);
                if (name == null) {
                    name = loaded.getFamily();
                }
            } catch (Exception e) {
                System.err.println("Could not load font " + file + ": " + e.getMessage());
            }
        }
        return name != null ? name : Font.SANS_SERIF;
    }

    public static Font font(int style, int size) {
        return new Font(family != null ? family : Font.SANS_SERIF, style, size);
    }

    public static Font display() {
        return font(Font.BOLD, 26);
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
