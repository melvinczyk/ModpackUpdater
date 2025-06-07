package com.nicholasburczyk.packupdater.model;

import java.util.List;

public class ChangelogEntry {
    private String version;
    private String timestamp;
    private String message;
    private List<ChangeOperation> operations;

    public String getVersion() {
        return version;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public List<ChangeOperation> getOperations() {
        return operations;
    }
}
