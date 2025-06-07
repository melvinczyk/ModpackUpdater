package com.nicholasburczyk.packupdater.model;

public class ChangeOperation {
    private String type;
    private String path;
    private String oldPath;
    private String newPath;

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }
}
