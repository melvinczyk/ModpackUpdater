package com.nicholasburczyk.packupdater.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class ModpackInfo {
    private String root;
    private String modpackId;
    private String displayName;
    private String author;
    private String description;
    private String version;
    private String minecraftVersion;
    private String modLoaderVersion;
    private String modLoader;

    @JsonIgnore
    private int updateCount = 0;

    private String created;
    private String lastUpdated;

    private List<String> folders;
    private List<ChangelogEntry> changelog;

    // --- Getters ---
    public String getRoot() {
        return root;
    }

    public int getUpdateCount() { return updateCount; }
    public void setUpdateCount(int updateCount) { this.updateCount = updateCount; }

    public String getModpackId() {
        return modpackId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getModLoaderVersion() {
        return modLoaderVersion;
    }

    public String getModLoader() {
        return modLoader;
    }

    public String getCreated() {
        return created;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public List<String> getFolders() {
        return folders;
    }

    public List<ChangelogEntry> getChangelog() {
        return changelog;
    }

    // --- Setters ---
    public void setRoot(String root) {
        this.root = root;
    }

    public void setModpackId(String modpackId) {
        this.modpackId = modpackId;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public void setModLoaderVersion(String modLoaderVersion) {
        this.modLoaderVersion = modLoaderVersion;
    }

    public void setModLoader(String modLoader) {
        this.modLoader = modLoader;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setFolders(List<String> folders) {
        this.folders = folders;
    }

    public void setChangelog(List<ChangelogEntry> changelog) {
        this.changelog = changelog;
    }

    @Override
    public String toString() {
        return String.format("%s - root: %s - %s %s - Version: %s", modpackId, root,modLoader, modLoaderVersion, version);
    }
}
