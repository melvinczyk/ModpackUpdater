package com.nicholasburczyk.packupdater.server;

import java.net.URI;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public class B2Connector {
    // Change this to the endpoint from your bucket details, prefixed with "https://"
    private static String ENDPOINT_URL = "https://s3.us-east-005.backblazeb2.com";

    public static void main(String[] args)
            throws Exception {
        // Extract the region from the endpoint URL
        Matcher matcher = Pattern.compile("https://s3\\.([a-z0-9-]+)\\.backblazeb2\\.com").matcher(ENDPOINT_URL);
        if (!matcher.find()) {
            System.err.println("Can't find a region in the endpoint URL: " + ENDPOINT_URL);
        }
        String region = matcher.group(1);

        String keyId = "005d09ffb61168e0000000006";
        String appKey = "K005UqEe9zoa9A2OL6Za+4sNZG5u9sk";
        // Create a client. The try-with-resources pattern ensures the client is cleaned up when we're done with it
        try (S3Client b2 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(keyId, appKey)))
                .endpointOverride(new URI(ENDPOINT_URL))
                .build();) {

            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket("GroidPack-Season3").build();
            ListObjectsV2Response result = b2.listObjectsV2(request);

            System.out.println("Files in bucket '" + "GroidPack-Season3" + "':");
            for (S3Object object : result.contents()) {
                String localPath = "/Users/nicholasburczyk/Documents/Coding/PackUpdater/PackUpdater/downloads/" + object.key();
                System.out.printf("- %s (%d bytes)%n", object.key(), object.size());
                GetObjectRequest downloadRequest = GetObjectRequest.builder().bucket("GroidPack-Season3").key(object.key()).build();
                System.out.println("Downloading: " + object.key());
                b2.getObject(downloadRequest, Paths.get(localPath));
                System.out.println("Download complete: " + localPath);
            }
        }
    }
}
