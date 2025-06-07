package com.nicholasburczyk.packupdater.server;

import com.nicholasburczyk.packupdater.model.ModpackInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ModpackRegistry {
    private static final Map<String, ModpackInfo> serverModpacks = new HashMap<>();
    private static final Map<String, ModpackInfo> localModpacks = new HashMap<>();

    public static void setServerModpacks(Map<String, ModpackInfo> newData) {
        serverModpacks.clear();
        serverModpacks.putAll(newData);
    }

    public static Map<String, ModpackInfo> getServerModpacks() {
        return Collections.unmodifiableMap(serverModpacks);
    }

    public static void setLocalModpacks(Map<String, ModpackInfo> newData) {
        localModpacks.clear();
        localModpacks.putAll(newData);
    }

    public static void addLocalModpack(String id, ModpackInfo modpackInfo) {
        if (id != null && modpackInfo != null) {
            localModpacks.put(id, modpackInfo);
        }
    }

    public static Map<String, ModpackInfo> getLocalModpacks() {
        return Collections.unmodifiableMap(localModpacks);
    }
}
