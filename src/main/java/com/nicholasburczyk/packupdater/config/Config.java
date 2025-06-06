package com.nicholasburczyk.packupdater.config;

public class Config {
    private String curseforge_path;
    private String[] modpack_path_overrides;
    private String endpoint;
    private boolean autoUpdate;
    private String keyID;
    private String appKey;

    public void setCurseforge_path(String path) {
        this.curseforge_path = path;
    }

    public void setModpack_path_overrides(String[] overrides) {
        this.modpack_path_overrides = overrides;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public void setKeyID(String keyID) {
        this.keyID = keyID;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getCurseforge_path() {
        return this.curseforge_path;
    }

    public String[] getModpack_path_overrides() {
        return this.modpack_path_overrides;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public String getKeyID() {
        return keyID;
    }

    public String getAppKey() {
        return this.appKey;
    }

    public boolean getAutoUpdate() {
        return this.autoUpdate;
    }
}
