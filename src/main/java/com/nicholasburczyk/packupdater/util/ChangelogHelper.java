package com.nicholasburczyk.packupdater.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ChangelogHelper {

    private static final Map<String, String> changelogCache = new HashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String getChangelogForModpack(String modpackId) {
        if (changelogCache.containsKey(modpackId)) {
            return changelogCache.get(modpackId);
        }

        try {
            String manifestKey = modpackId + "/manifest.json";

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(ConfigManager.getInstance().getConfig().getBucketName())
                    .key(manifestKey)
                    .build();

            InputStream inputStream = B2ClientProvider.getClient().getObject(getObjectRequest);
            String manifestContent = readInputStream(inputStream);

            JsonNode manifestJson = objectMapper.readTree(manifestContent);
            JsonNode changelogArray = manifestJson.get("changelog");

            if (changelogArray == null || !changelogArray.isArray() || changelogArray.size() == 0) {
                System.out.println("No changelog found in manifest for modpack: " + modpackId);
                return "No changelog available for this modpack.";
            }

            StringBuilder formattedChangelog = new StringBuilder();
            formattedChangelog.append("Changelog for ").append(modpackId).append(":\n\n");

            for (JsonNode entry : changelogArray) {
                String version = entry.has("version") ? entry.get("version").asText() : "Unknown";
                String timestamp = entry.has("timestamp") ? entry.get("timestamp").asText() : "";
                String message = entry.has("message") ? entry.get("message").asText() : "";

                formattedChangelog.append("Version: ").append(version).append("\n");

                if (!timestamp.isEmpty()) {
                    try {
                        ZonedDateTime dateTime = ZonedDateTime.parse(timestamp);
                        String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                        formattedChangelog.append("Date: ").append(formattedDate).append("\n");
                    } catch (Exception e) {
                        formattedChangelog.append("Date: ").append(timestamp).append("\n");
                    }
                }

                if (!message.isEmpty()) {
                    formattedChangelog.append("Changes: ").append(message).append("\n");
                }

                JsonNode operations = entry.get("operations");
                if (operations != null && operations.isArray() && operations.size() > 0) {
                    formattedChangelog.append("Operations:\n");
                    for (JsonNode operation : operations) {
                        String operationType = operation.has("type") ? operation.get("type").asText() : "Unknown";
                        String operationPath = operation.has("path") ? operation.get("path").asText() : "";

                        formattedChangelog.append("  - ").append(operationType);
                        if (!operationPath.isEmpty()) {
                            formattedChangelog.append(": ").append(operationPath);
                        }
                        formattedChangelog.append("\n");
                    }
                }

                formattedChangelog.append("\n");
            }

            String result = formattedChangelog.toString().trim();

            changelogCache.put(modpackId, result);

            return result;

        } catch (Exception e) {
            System.err.println("Error fetching changelog from manifest for modpack " + modpackId + ": " + e.getMessage());
            e.printStackTrace();
            return "Error loading changelog: " + e.getMessage();
        }
    }

    private static String readInputStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            return content.toString();
        } catch (Exception e) {
            System.err.println("Error reading input stream: " + e.getMessage());
            return "Error reading changelog content.";
        }
    }
}