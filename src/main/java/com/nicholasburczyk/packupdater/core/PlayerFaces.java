package com.nicholasburczyk.packupdater.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerFaces {

    private static final int SIZE = 64;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, BufferedImage> MEMORY = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private PlayerFaces() {
    }

    public static BufferedImage face(String player) {
        if (!Screenshots.isPlayerName(player)) {
            return null;
        }
        String key = player.toLowerCase(java.util.Locale.ROOT);
        BufferedImage known = MEMORY.get(key);
        if (known != null) {
            return known;
        }
        BufferedImage fetched = fetch(player);
        if (fetched != null) {
            MEMORY.put(key, fetched);
        }
        return fetched;
    }

    private static BufferedImage fetch(String player) {
        try {
            JsonNode profile = json("https://api.mojang.com/users/profiles/minecraft/" + player);
            String uuid = profile == null ? null : profile.path("id").asText(null);
            if (uuid == null || !uuid.matches("[0-9a-fA-F]{32}")) {
                return null;
            }
            JsonNode session = json("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
            if (session == null) {
                return null;
            }
            String skinUrl = null;
            for (JsonNode property : session.path("properties")) {
                if ("textures".equals(property.path("name").asText())) {
                    byte[] decoded = Base64.getDecoder().decode(property.path("value").asText());
                    skinUrl = MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8))
                            .path("textures").path("SKIN").path("url").asText(null);
                }
            }
            if (skinUrl == null || !skinUrl.startsWith("http://textures.minecraft.net/")
                    && !skinUrl.startsWith("https://textures.minecraft.net/")) {
                return null;
            }
            byte[] png = bytes(skinUrl.replaceFirst("^http://", "https://"));
            BufferedImage skin = png == null ? null : ImageIO.read(new ByteArrayInputStream(png));
            return skin == null || skin.getWidth() < 64 || skin.getHeight() < 32 ? null : crop(skin);
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage crop(BufferedImage skin) {
        BufferedImage face = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(skin, 0, 0, SIZE, SIZE, 8, 8, 16, 16, null);
        g.drawImage(skin, 0, 0, SIZE, SIZE, 40, 8, 48, 16, null);
        g.dispose();
        return face;
    }

    private static JsonNode json(String url) throws IOException, InterruptedException {
        byte[] body = bytes(url);
        return body == null ? null : MAPPER.readTree(body);
    }

    private static byte[] bytes(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        return response.statusCode() == 200 ? response.body() : null;
    }

}
