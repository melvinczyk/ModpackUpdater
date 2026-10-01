package com.nicholasburczyk.packupdater.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
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
    private List<String> files;
    private List<ChangelogEntry> changelog;

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

    @JsonGetter("folders")
    public List<String> getFolders() {
        return folders;
    }

    public List<ChangelogEntry> getChangelog() {
        return changelog;
    }

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

    @JsonIgnore
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

    @JsonGetter("files")
    public List<String> getFiles() {
        return files;
    }

    @JsonIgnore
    public void setFiles(List<String> files) {
        this.files = files;
    }

    @JsonSetter("files")
    public void readFiles(JsonNode node) {
        this.files = toNameList(node);
    }

    @JsonSetter("folders")
    public void readFolders(JsonNode node) {
        this.folders = toNameList(node);
    }

    private static List<String> toNameList(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node == null || node.isNull()) {
            return names;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    names.add(element.asText());
                } else if (element.isObject()) {
                    JsonNode path = element.has("path") ? element.get("path") : element.get("name");
                    if (path != null && path.isTextual()) {
                        names.add(path.asText());
                    }
                }
            }
        } else if (node.isObject()) {
            node.fieldNames().forEachRemaining(names::add);
        } else if (node.isTextual()) {
            names.add(node.asText());
        }
        return names;
    }
}
