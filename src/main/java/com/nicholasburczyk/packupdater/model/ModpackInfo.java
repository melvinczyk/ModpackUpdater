package com.nicholasburczyk.packupdater.model;

public class ModpackInfo {
    private String root;
    private String description;
    private String forge_version;
    private String minecraft_version;

    public String getRoot() {
        return root;
    }

    public String getDescription() {
        return description;
    }

    public String getForge_version() {
        return forge_version;
    }

    public String getMinecraft_version() {
        return minecraft_version;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setForge_version(String version) {
        this.forge_version = version;
    }

    public void setMinecraft_version(String version) {
        this.minecraft_version = version;
    }

    @Override
    public String toString() {
        return String.format("%s (MC %s / Forge %s)", description, minecraft_version, forge_version);
    }
}
