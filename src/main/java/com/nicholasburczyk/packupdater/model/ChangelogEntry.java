package com.nicholasburczyk.packupdater.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangelogEntry {

    private String version;
    private String timestamp;
    private String message;
    private List<ChangeOperation> operations;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ChangeOperation> getOperations() {
        return operations;
    }

    public void setOperations(List<ChangeOperation> operations) {
        this.operations = operations;
    }
}
