package com.nicholasburczyk.packupdater.util;

import com.nicholasburczyk.packupdater.model.ModpackInfo;

public class UpdateChecker {

    public static int countUpdatesAvailable(ModpackInfo serverInfo, ModpackInfo localInfo) {
        if (serverInfo == null || localInfo == null) return 0;

        String localVersion = localInfo.getVersion();
        int updates = 0;

        for (var entry : serverInfo.getChangelog()) {
            if (isVersionGreater(entry.getVersion(), localVersion)) {
                updates++;
            }
        }

        return updates;
    }

    public static boolean isVersionGreater(String v1, String v2) {
        return compareVersions(v1, v2) > 0;
    }

    /**
     * Compares two dotted version strings numerically (so 1.10 > 1.9).
     * Missing trailing segments are treated as 0, and any non-numeric
     * suffix on a segment (e.g. "2-beta") is ignored.
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null) v1 = "";
        if (v2 == null) v2 = "";

        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");

        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length ? parseSegment(a[i]) : 0;
            int bi = i < b.length ? parseSegment(b[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int parseSegment(String segment) {
        if (segment == null) return 0;
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        if (end == 0) return 0;
        try {
            return Integer.parseInt(segment.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
