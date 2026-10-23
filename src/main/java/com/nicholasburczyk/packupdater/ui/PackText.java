package com.nicholasburczyk.packupdater.ui;

import com.nicholasburczyk.packupdater.core.VersionUtil;
import com.nicholasburczyk.packupdater.model.ChangelogEntry;
import com.nicholasburczyk.packupdater.model.ModpackInfo;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Short, display-ready strings for a modpack. */
public final class PackText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private PackText() {
    }

    public static String nameOf(ModpackInfo pack) {
        String name = pack.getDisplayName();
        if (name == null || name.isBlank()) {
            name = pack.getModpackId();
        }
        return name == null || name.isBlank() ? String.valueOf(pack.getRoot()) : name;
    }

    public static String metaOf(ModpackInfo pack) {
        StringBuilder meta = new StringBuilder();
        if (pack.getMinecraftVersion() != null && !pack.getMinecraftVersion().isBlank()) {
            meta.append("Minecraft ").append(pack.getMinecraftVersion());
        }
        if (pack.getModLoader() != null && !pack.getModLoader().isBlank()) {
            if (meta.length() > 0) {
                meta.append(", ");
            }
            String loader = pack.getModLoader();
            meta.append(Character.toUpperCase(loader.charAt(0))).append(loader.substring(1));
        }
        return meta.length() == 0 ? safe(pack.getRoot()) : meta.toString();
    }

    /** Message of the newest changelog entry, or null when there is none. */
    public static String latestMessage(ModpackInfo pack) {
        if (pack == null || pack.getChangelog() == null) {
            return null;
        }
        ChangelogEntry newest = null;
        for (ChangelogEntry entry : pack.getChangelog()) {
            if (newest == null || VersionUtil.isGreater(entry.getVersion(), newest.getVersion())) {
                newest = entry;
            }
        }
        String message = newest == null ? null : newest.getMessage();
        return message == null || message.isBlank() ? null : message.trim();
    }

    public static String date(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "never";
        }
        try {
            return ZonedDateTime.parse(timestamp).format(DATE);
        } catch (Exception e) {
            return timestamp.length() > 10 ? timestamp.substring(0, 10) : timestamp;
        }
    }

    public static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
