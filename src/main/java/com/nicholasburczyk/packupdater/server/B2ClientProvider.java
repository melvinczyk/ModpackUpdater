package com.nicholasburczyk.packupdater.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class B2ClientProvider {

    private static final Pattern REGION_PATTERN =
            Pattern.compile("https?://s3[.\\-]([a-z0-9-]+)\\.backblazeb2\\.com", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_REGION = "us-east-005";

    private static S3Client client;

    public static synchronized S3Client getClient() {
        if (client == null) {
            client = build();
        }
        return client;
    }

    public static synchronized void reconnectToClient() {
        closeQuietly();
        client = null;
    }

    public static ConnectionStatus checkConnection() {
        Config config = ConfigManager.getInstance().getConfig();
        if (!config.hasCredentials()) {
            return new ConnectionStatus(false, "Not configured");
        }
        try {
            getClient().headBucket(HeadBucketRequest.builder()
                    .bucket(config.getBucketName())
                    .build());
            return new ConnectionStatus(true, "Connected");
        } catch (S3Exception e) {
            String code = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
            if (code == null || code.isBlank()) {
                code = "HTTP " + e.statusCode();
            }
            return new ConnectionStatus(false, friendly(code));
        } catch (Exception e) {
            return new ConnectionStatus(false, friendly(e.getMessage()));
        }
    }

    public static Map<String, ModpackInfo> fetchModpacks() throws Exception {
        Config config = ConfigManager.getInstance().getConfig();
        String bucket = config.getBucketName();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, ModpackInfo> modpacks = new LinkedHashMap<>();

        Map<String, String> roots;
        try (InputStream stream = getClient().getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key("modpacks.json")
                .build())) {
            roots = mapper.readValue(stream, new TypeReference<>() {
            });
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("modpacks.json was not found in bucket " + bucket, e);
        }

        for (Map.Entry<String, String> entry : roots.entrySet()) {
            String displayName = entry.getKey();
            String root = entry.getValue();
            try (InputStream stream = getClient().getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(root + "/manifest.json")
                    .build())) {
                ModpackInfo info = mapper.readValue(stream, ModpackInfo.class);
                info.setRoot(root);
                if (info.getDisplayName() == null || info.getDisplayName().isBlank()) {
                    info.setDisplayName(displayName);
                }
                if (info.getModpackId() == null || info.getModpackId().isBlank()) {
                    info.setModpackId(root);
                }
                modpacks.put(info.getModpackId(), info);
            } catch (NoSuchKeyException e) {
                System.err.println("No manifest.json for modpack root " + root);
            } catch (Exception e) {
                System.err.println("Could not read manifest for " + root + ": " + e.getMessage());
            }
        }

        return modpacks;
    }

    public static void fetchAndStoreModpackInfo() throws Exception {
        ModpackRegistry.setServerModpacks(fetchModpacks());
    }

    private static S3Client build() {
        Config config = ConfigManager.getInstance().getConfig();
        String endpoint = config.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("No server URL is set in Settings");
        }
        try {
            return S3Client.builder()
                    .region(Region.of(regionFrom(endpoint)))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(config.getKeyID(), config.getAppKey())))
                    .endpointOverride(new URI(endpoint))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not connect to " + endpoint + ": " + e.getMessage(), e);
        }
    }

    private static String regionFrom(String endpoint) {
        Matcher matcher = REGION_PATTERN.matcher(endpoint);
        if (matcher.find()) {
            return matcher.group(1);
        }
        System.err.println("Could not read a region from " + endpoint + ", using " + DEFAULT_REGION);
        return DEFAULT_REGION;
    }

    private static void closeQuietly() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                System.err.println("Could not close the previous connection: " + e.getMessage());
            }
        }
    }

    private static String friendly(String code) {
        if (code == null || code.isBlank()) {
            return "Unknown error";
        }
        return switch (code) {
            case "InvalidAccessKeyId", "AccessDenied", "SignatureDoesNotMatch", "Unauthorized" ->
                    "Bad credentials";
            case "NoSuchBucket" -> "Bucket not found";
            case "UnknownHostException" -> "No internet";
            default -> code;
        };
    }
}
