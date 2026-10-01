package com.nicholasburczyk.packupdater.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Icons {

    public static final String REFRESH = "refresh-cw";
    public static final String RECHECK = "rotate-ccw";
    public static final String DOWNLOAD = "download";
    public static final String UPLOAD = "upload";
    public static final String FOLDER = "folder-open";
    public static final String LIST = "list";
    public static final String SETTINGS = "settings";
    public static final String HELP = "circle-help";
    public static final String ADMIN = "shield-check";
    public static final String SEARCH = "search";
    public static final String PLUS = "plus";
    public static final String CHECK = "check";
    public static final String CLOSE = "x";
    public static final String CLOUD = "cloud";
    public static final String DRIVE = "hard-drive";
    public static final String PACKAGE = "package";
    public static final String ALERT = "circle-alert";
    public static final String WARNING = "triangle-alert";
    public static final String BACK = "arrow-left";
    public static final String CHEVRON = "chevron-right";
    public static final String CHEVRON_DOWN = "chevron-down";
    public static final String FOLDER_NEW = "folder-plus";
    public static final String MAGIC = "sparkles";
    public static final String TRASH = "trash-2";
    public static final String FILE_NEW = "file-plus";
    public static final String FILE_DIFF = "file-diff";
    public static final String INFO = "info";
    public static final String OK_CIRCLE = "circle-check";
    public static final String FAIL_CIRCLE = "circle-x";
    public static final String WRENCH = "wrench";

    private static final String PATH = "com/nicholasburczyk/packupdater/icons/";
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private Icons() {
    }

    public static Icon of(String name, int size, Color color) {
        String key = name + ':' + size + ':' + color.getRGB();
        return CACHE.computeIfAbsent(key, ignored -> build(name, size, color));
    }

    public static Icon of(String name, int size) {
        return of(name, size, Theme.TEXT_DIM);
    }

    private static Icon build(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon(PATH + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> color));
        return icon;
    }

    public static Icon packPlaceholder(int size) {
        return of(PACKAGE, size, Theme.TEXT_FAINT);
    }
}
