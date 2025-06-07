package com.nicholasburczyk.packupdater.model;

public class FileMetadata {
    private String hash;
    private long size;
    private String addedInVersion;

    public String getHash() {
        return hash;
    }

    public long getSize() {
        return size;
    }

    public String getAddedInVersion() {
        return addedInVersion;
    }
}
