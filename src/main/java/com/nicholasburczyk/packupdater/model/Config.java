package com.nicholasburczyk.packupdater.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {

    private String curseforge_path = "";
    private String[] modpack_path_overrides = new String[0];
    private String endpoint = "https://s3.us-east-005.backblazeb2.com";
    private boolean autoUpdate = false;
    private String keyID = "";
    private String appKey = "";
    private String bucketName = "GroidPack";
    private String[] ignoredFiles = {".DS_Store", ".bzEmpty"};
    private String[] customIconModpacks = new String[0];

    public String getCurseforge_path() {
        return curseforge_path;
    }

    public void setCurseforge_path(String curseforge_path) {
        this.curseforge_path = curseforge_path;
    }

    public String[] getModpack_path_overrides() {
        return modpack_path_overrides;
    }

    public void setModpack_path_overrides(String[] modpack_path_overrides) {
        this.modpack_path_overrides = modpack_path_overrides;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean getAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public String getKeyID() {
        return keyID;
    }

    public void setKeyID(String keyID) {
        this.keyID = keyID;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String[] getIgnoredFiles() {
        return ignoredFiles;
    }

    public void setIgnoredFiles(String[] ignoredFiles) {
        this.ignoredFiles = ignoredFiles;
    }

    public String[] getCustomIconModpacks() {
        return customIconModpacks;
    }

    public void setCustomIconModpacks(String[] customIconModpacks) {
        this.customIconModpacks = customIconModpacks == null ? new String[0] : customIconModpacks;
    }

    @JsonIgnore
    public boolean usesPackIcon(String modpackId) {
        if (modpackId == null) {
            return true;
        }
        for (String id : customIconModpacks) {
            if (modpackId.equals(id)) {
                return false;
            }
        }
        return true;
    }

    @JsonIgnore
    public void setUsesPackIcon(String modpackId, boolean usePackIcon) {
        if (modpackId == null) {
            return;
        }
        java.util.List<String> ids = new java.util.ArrayList<>(
                java.util.Arrays.asList(customIconModpacks));
        ids.remove(modpackId);
        if (!usePackIcon) {
            ids.add(modpackId);
        }
        customIconModpacks = ids.toArray(new String[0]);
    }

    @JsonIgnore
    public boolean hasCredentials() {
        return isSet(keyID) && isSet(appKey) && isSet(endpoint) && isSet(bucketName);
    }

    @JsonIgnore
    public boolean hasInstancesPath() {
        return isSet(curseforge_path);
    }

    @JsonIgnore
    public boolean isReady() {
        return hasCredentials() && hasInstancesPath();
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
