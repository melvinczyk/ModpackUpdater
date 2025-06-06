package com.nicholasburczyk.packupdater.server;

import com.nicholasburczyk.packupdater.config.Config;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
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
                client = S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(config.getKeyID(), config.getAppKey())))
                        .endpointOverride(new URI(config.getEndpoint()))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize S3Client");
            }
        }
        return client;
    }

    public static synchronized boolean reconnectToClient() {
        Config config = ConfigManager.getInstance().getConfig();

        try {
            Matcher matcher = Pattern.compile("https://s3\\.([a-z0-9-]+)\\.backblazeb2\\.com").matcher(config.getEndpoint());
            if (!matcher.find()) {
                System.err.println("Can't find a region in the endpoint URL: " + config.getEndpoint());
                return false; // Early exit, invalid endpoint
            }

            String region = matcher.group(1);
            client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(config.getKeyID(), config.getAppKey())))
                    .endpointOverride(new URI(config.getEndpoint()))
                    .build();

            return true;
        } catch (Exception e) {
            e.printStackTrace(); // Optional: log more detail
            return false; // Prevent crash
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

}
