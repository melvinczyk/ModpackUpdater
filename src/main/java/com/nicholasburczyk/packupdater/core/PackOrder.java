package com.nicholasburczyk.packupdater.core;

import com.nicholasburczyk.packupdater.model.ChangelogEntry;
import com.nicholasburczyk.packupdater.model.ModpackInfo;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

public final class PackOrder {

    private PackOrder() {
    }

    public static Comparator<ModpackInfo> byRecent() {
        return Comparator.comparing(PackOrder::updatedAt).reversed()
                .thenComparing(PackOrder::displayName, String.CASE_INSENSITIVE_ORDER);
    }

    public static Instant updatedAt(ModpackInfo pack) {
        Instant fromField = parse(pack.getLastUpdated());
        if (fromField != null) {
            return fromField;
        }
        Instant newest = null;
        List<ChangelogEntry> changelog = pack.getChangelog();
        if (changelog != null) {
            for (ChangelogEntry entry : changelog) {
                Instant stamp = entry == null ? null : parse(entry.getTimestamp());
                if (stamp != null && (newest == null || stamp.isAfter(newest))) {
                    newest = stamp;
                }
            }
        }
        if (newest != null) {
            return newest;
        }
        Instant created = parse(pack.getCreated());
        return created != null ? created : Instant.EPOCH;
    }

    public static String displayName(ModpackInfo pack) {
        String name = pack.getDisplayName();
        if (name == null || name.isBlank()) {
            name = pack.getModpackId();
        }
        return name == null || name.isBlank() ? String.valueOf(pack.getRoot()) : name;
    }

    private static Instant parse(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception ignored) {
            try {
                return ZonedDateTime.parse(timestamp).toInstant();
            } catch (Exception alsoIgnored) {
                return null;
            }
        }
    }
}
