package com.nicholasburczyk.packupdater.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SyncReport {

    public enum Kind {
        MISSING("Missing"),
        OUTDATED("Changed"),
        EXTRA("Unexpected");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Item(Kind kind, String path, long size) {
    }

    private final List<Item> items = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private String targetVersion;
    private String currentVersion;

    public void add(Kind kind, String path, long size) {
        items.add(new Item(kind, path, size));
    }

    public void addFailure(String detail) {
        failures.add(detail);
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    public List<Item> itemsOf(Kind kind) {
        List<Item> filtered = new ArrayList<>();
        for (Item item : items) {
            if (item.kind() == kind) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public List<String> failures() {
        return Collections.unmodifiableList(failures);
    }

    public int count(Kind kind) {
        return itemsOf(kind).size();
    }

    public int total() {
        return items.size();
    }

    public boolean isClean() {
        return items.isEmpty();
    }

    public long bytesToDownload() {
        long total = 0;
        for (Item item : items) {
            if (item.kind() != Kind.EXTRA) {
                total += item.size();
            }
        }
        return total;
    }

    public String targetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public String currentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public boolean isVersionChange() {
        return currentVersion != null && targetVersion != null
                && !currentVersion.equals(targetVersion);
    }
}
