package com.nicholasburczyk.packupdater.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class IgnoreRules {

    private static final List<String> ALWAYS_IGNORED_NAMES = List.of(
            "manifest.json", ".ds_store", ".bzempty", "thumbs.db", "desktop.ini"
    );

    private final List<String> patterns;

    private IgnoreRules(List<String> patterns) {
        this.patterns = patterns;
    }

    public static IgnoreRules from(String[] configured) {
        List<String> cleaned = new ArrayList<>();
        if (configured != null) {
            for (String pattern : configured) {
                if (pattern != null && !pattern.isBlank()) {
                    cleaned.add(pattern.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new IgnoreRules(Collections.unmodifiableList(cleaned));
    }

    public boolean ignores(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return true;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (ALWAYS_IGNORED_NAMES.contains(name)) {
            return true;
        }
        for (String pattern : patterns) {
            if (matches(name, pattern)) {
                return true;
            }
        }
        return false;
    }

    public boolean ignoresPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return true;
        }
        String normalized = relativePath.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (!segment.isEmpty() && ignores(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String name, String pattern) {
        if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 2) {
            return name.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return name.endsWith(pattern.substring(1));
        }
        if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return name.equals(pattern);
    }
}
