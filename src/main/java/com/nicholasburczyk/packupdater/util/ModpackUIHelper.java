package com.nicholasburczyk.packupdater.util;

import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

public class ModpackUIHelper {

    public static void populateModpackList(VBox container, Map<String, ModpackInfo> modpacks, boolean isLocal) {
        container.getChildren().clear();
        Config config = ConfigManager.getInstance().getConfig();

        for (Map.Entry<String, ModpackInfo> entry : modpacks.entrySet()) {
            String modpackId = entry.getKey();
            ModpackInfo info = entry.getValue();

            String profileImagePrefix = info.getRoot() + "/profileImage/";

            Image image;

            if (isLocal) {
                try {
                    String localProfileImageFolder = config.getCurseforge_path() + "/" + profileImagePrefix;
                    File profileImageDir = new File(localProfileImageFolder);

                    File imageFile = null;
                    if (profileImageDir.exists() && profileImageDir.isDirectory()) {
                        File[] files = profileImageDir.listFiles((dir, name) -> {
                            String lower = name.toLowerCase();
                            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                        });

                        if (files != null && files.length > 0) {
                            imageFile = files[0];
                        }
                    }

                    if (imageFile != null && imageFile.exists()) {
                        image = new Image(new FileInputStream(imageFile));
                    } else {
                        throw new Exception("No image file found locally");
                    }
                } catch (Exception e) {
                    System.out.println("Could not load local image for modpack: " + modpackId + ", using default.");
                    image = new Image(Objects.requireNonNull(ModpackUIHelper.class.getResourceAsStream("/com/nicholasburczyk/packupdater/images/default.png")));
                }
            } else {
                try {
                    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                            .bucket(ConfigManager.getInstance().getConfig().getBucketName())
                            .prefix(profileImagePrefix)
                            .build();

                    ListObjectsV2Response listResponse = B2ClientProvider.getClient().listObjectsV2(listRequest);

                    String imageKey = null;
                    for (S3Object obj : listResponse.contents()) {
                        String key = obj.key().toLowerCase();
                        if (key.endsWith(".png") || key.endsWith(".jpg") || key.endsWith(".jpeg")) {
                            imageKey = obj.key();
                            break;
                        }
                    }

                    if (imageKey == null) {
                        throw new Exception("No image file found");
                    }

                    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                            .bucket(ConfigManager.getInstance().getConfig().getBucketName())
                            .key(imageKey)
                            .build();

                    InputStream inputStream = B2ClientProvider.getClient().getObject(getObjectRequest);
                    image = new Image(inputStream);

                } catch (Exception e) {
                    System.out.println("Could not load image for modpack: " + modpackId + ", using default.");
                    image = new Image(Objects.requireNonNull(ModpackUIHelper.class.getResourceAsStream("/com/nicholasburczyk/packupdater/images/default.png")));
                }
            }
            HBox entryBox = UIComponents.createModpackEntry(info, image, isLocal);
            container.getChildren().add(entryBox);
        }
    }
}
