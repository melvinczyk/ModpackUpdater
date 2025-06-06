package com.nicholasburczyk.packupdater.server;

public class ConnectionStatus {
    public final boolean isConnected;
    public final String message;

    public ConnectionStatus(boolean isConnected, String message) {
        this.isConnected = isConnected;
        this.message = message;
    }
}
