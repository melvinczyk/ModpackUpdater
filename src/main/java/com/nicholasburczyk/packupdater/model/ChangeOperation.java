package com.nicholasburczyk.packupdater.model;

public class ChangeOperation {
    private String type;
    private String path;
    private String oldPath;
    private String newPath;

    public ChangeOperation() {}

    public ChangeOperation(String operationString) {
        parseOperationString(operationString);
    }

    private void parseOperationString(String operationString) {
        if (operationString.startsWith("Added: ")) {
            this.type = "Added";
            this.path = operationString.substring(7);
        } else if (operationString.startsWith("Removed: ")) {
            this.type = "Removed";
            this.path = operationString.substring(9);
        } else if (operationString.startsWith("Modified: ")) {
            this.type = "Modified";
            this.path = operationString.substring(10);
        } else if (operationString.startsWith("Moved: ")) {
            this.type = "Moved";
            String pathPart = operationString.substring(7);
            if (pathPart.contains(" -> ")) {
                String[] parts = pathPart.split(" -> ");
                this.oldPath = parts[0];
                this.newPath = parts.length > 1 ? parts[1] : parts[0];
                this.path = this.newPath;
            } else {
                this.path = pathPart;
            }
        } else {
            this.type = "Unknown";
            this.path = operationString;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOldPath() {
        return oldPath;
    }

    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public void setNewPath(String newPath) {
        this.newPath = newPath;
    }

    @Override
    public String toString() {
        return "ChangeOperation{" +
                "type='" + type + '\'' +
                ", path='" + path + '\'' +
                ", oldPath='" + oldPath + '\'' +
                ", newPath='" + newPath + '\'' +
                '}';
    }
}