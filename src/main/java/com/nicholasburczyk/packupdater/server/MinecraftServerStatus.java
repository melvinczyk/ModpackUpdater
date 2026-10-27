package com.nicholasburczyk.packupdater.server;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MinecraftServerStatus {

    private static final int DEFAULT_PORT = 25565;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE = 1 << 21;
    private static final String[] PLACEHOLDER_VERSIONS = {"offline", "sleeping", "starting", "stopping", "maintenance"};

    public record Status(boolean running, int online, int max, String version, String message) {
        static Status down(String message) {
            return new Status(false, 0, 0, "", message);
        }
    }

    private MinecraftServerStatus() {
    }

    public static boolean isConfigured() {
        return !address().isBlank();
    }

    public static Status check() {
        String address = address();
        if (address.isBlank()) {
            return Status.down("No Minecraft Server set");
        }
        String host = address;
        int port = DEFAULT_PORT;
        int colon = address.lastIndexOf(':');
        if (colon > 0) {
            host = address.substring(0, colon);
            try {
                port = Integer.parseInt(address.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                return Status.down("Bad Minecraft Server address");
            }
        }
        try {
            return read(ping(host.trim(), port));
        } catch (Exception e) {
            return Status.down("Minecraft Server offline");
        }
    }

    private static String address() {
        String address = ConfigManager.getInstance().getConfig().getMinecraftServer();
        return address == null ? "" : address.trim();
    }

    private static String ping(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream packet = new DataOutputStream(handshake);
            writeVarInt(packet, 0x00);
            writeVarInt(packet, -1);
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(packet, hostBytes.length);
            packet.write(hostBytes);
            packet.writeShort(port);
            writeVarInt(packet, 1);

            writeVarInt(out, handshake.size());
            out.write(handshake.toByteArray());
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            int length = readVarInt(in);
            if (length <= 0 || length > MAX_RESPONSE) {
                throw new IOException("Unexpected status length");
            }
            if (readVarInt(in) != 0x00) {
                throw new IOException("Unexpected status packet");
            }
            int jsonLength = readVarInt(in);
            if (jsonLength <= 0 || jsonLength > length) {
                throw new IOException("Unexpected status body");
            }
            byte[] json = new byte[jsonLength];
            in.readFully(json);
            return new String(json, StandardCharsets.UTF_8);
        }
    }

    private static Status read(String json) {
        JSONObject root = new JSONObject(json);
        JSONObject version = root.optJSONObject("version");
        JSONObject players = root.optJSONObject("players");
        if (version == null || players == null) {
            return Status.down("Minecraft Server offline");
        }
        String name = version.optString("name", "");
        int protocol = version.optInt("protocol", -1);
        String plain = name.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        for (String placeholder : PLACEHOLDER_VERSIONS) {
            if (plain.contains(placeholder)) {
                return Status.down("Minecraft Server offline");
            }
        }
        if (protocol <= 0) {
            return Status.down("Minecraft Server offline");
        }
        int online = Math.max(0, players.optInt("online", 0));
        int max = Math.max(0, players.optInt("max", 0));
        return new Status(true, online, max, name.replaceAll("§.", "").trim(), "Minecraft Server online");
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("VarInt too long");
    }
}
