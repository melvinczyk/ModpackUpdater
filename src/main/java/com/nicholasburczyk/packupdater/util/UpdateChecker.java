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

    private static boolean isVersionGreater(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");

        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length ? Integer.parseInt(a[i]) : 0;
            int bi = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (ai > bi) return true;
            if (ai < bi) return false;
        }
        return false;
    }
}
