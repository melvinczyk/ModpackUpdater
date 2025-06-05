package com.nicholasburczyk.packupdater.config;

public class Config {
    public String curseforge_path;
    public String[] modpack_path_overrides;
    public String endpoint;
    public String keyID;
    public String appKey;

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
}
