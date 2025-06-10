package com.nicholasburczyk.packupdater.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class B2ClientProvider {
    private static S3Client client;

    public static synchronized S3Client getClient() {
        if (client == null) {
            Config config = ConfigManager.getInstance().getConfig();
            try {
                Matcher matcher = Pattern.compile("https://s3\\.([a-z0-9-]+)\\.backblazeb2\\.com").matcher(config.getEndpoint());
                if (!matcher.find()) {
                    System.err.println("Can't find a region in the endpoint URL: " + config.getEndpoint());
                }
                String region = matcher.group(1);
                client = S3Client.builder().region(Region.of(region)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getKeyID(), config.getAppKey()))).endpointOverride(new URI(config.getEndpoint())).build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize S3Client");
            }
        }
        return client;
    }

    public static synchronized void reconnectToClient() {
        Config config = ConfigManager.getInstance().getConfig();

        try {
            Matcher matcher = Pattern.compile("https://s3\\.([a-z0-9-]+)\\.backblazeb2\\.com").matcher(config.getEndpoint());
            if (!matcher.find()) {
                System.err.println("Can't find a region in the endpoint URL: " + config.getEndpoint());
                return;
            }

            String region = matcher.group(1);
            client = S3Client.builder().region(Region.of(region)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getKeyID(), config.getAppKey()))).endpointOverride(new URI(config.getEndpoint())).build();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ConnectionStatus checkConnection() {
        try {
            getClient().listBuckets(ListBucketsRequest.builder().build());
            return new ConnectionStatus(true, "Connected");
        } catch (S3Exception e) {
            String errorCode = e.awsErrorDetails().errorCode();
            System.err.println("S3 connection check failed: " + errorCode);
            return new ConnectionStatus(false, errorCode);
        } catch (Exception e) {
            System.err.println("Unexpected error during S3 check: " + e.getMessage());
            return new ConnectionStatus(false, "UnexpectedError");
        }
    }

    public static void fetchAndStoreModpackInfo(String bucketName) {
        try {
            GetObjectRequest modpacksRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key("modpacks.json")
                    .build();
            InputStream modpacksStream = getClient().getObject(modpacksRequest);
            ObjectMapper mapper = new ObjectMapper();

            Map<String, String> modpackRoots = mapper.readValue(modpacksStream, new TypeReference<>() {});

            Map<String, ModpackInfo> modpacks = new HashMap<>();

            for (Map.Entry<String, String> entry : modpackRoots.entrySet()) {
                String root = entry.getValue();
                String displayName = entry.getKey();

                String manifestKey = root + "/manifest.json";

                try {
                    GetObjectRequest manifestRequest = GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(manifestKey)
                            .build();
                    InputStream manifestStream = getClient().getObject(manifestRequest);

                    ModpackInfo info = mapper.readValue(manifestStream, ModpackInfo.class);

                    info.setRoot(root);
                    if (info.getDisplayName() == null || info.getDisplayName().isBlank()) {
                        info.setDisplayName(displayName);
                    }

                    modpacks.put(root, info);

                } catch (NoSuchKeyException e) {
                    System.err.println("manifest.json not found for modpack root: " + root);
                } catch (Exception e) {
                    System.err.println("Failed to fetch manifest.json for modpack root " + root + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            ModpackRegistry.setServerModpacks(modpacks);
            System.out.println("Fetched modpacks: " + modpacks.keySet());

        } catch (NoSuchKeyException e) {
            System.err.println("modpacks.json not found in bucket: " + bucketName);
        } catch (Exception e) {
            System.err.println("Failed to fetch modpacks.json: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
