package com.nicholasburczyk.packupdater.util;

import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModpackUIHelper {

    public static void populateModpackList(VBox container, Map<String, ModpackInfo> modpacks, boolean isLocal) {
        container.getChildren().clear();
        Config config = ConfigManager.getInstance().getConfig();

        Image defaultImage = new Image(Objects.requireNonNull(
                ModpackUIHelper.class.getResourceAsStream("/com/nicholasburczyk/packupdater/images/default.png")));

        // Build the list immediately with placeholder images so the UI never
        // blocks on the network, then resolve the real images off-thread.
        List<ImageView> imageViews = new ArrayList<>();
        List<ModpackInfo> infos = new ArrayList<>();

        for (Map.Entry<String, ModpackInfo> entry : modpacks.entrySet()) {
            ModpackInfo info = entry.getValue();

            ImageView imageView = new ImageView(defaultImage);
            HBox entryBox = UIComponents.createModpackEntry(info, imageView, isLocal);
            container.getChildren().add(entryBox);

            imageViews.add(imageView);
            infos.add(info);
        }

        if (infos.isEmpty()) {
            return;
        }

        Thread imageLoader = new Thread(() -> {
            for (int i = 0; i < infos.size(); i++) {
                ModpackInfo info = infos.get(i);
                ImageView target = imageViews.get(i);
                Image image = loadModpackImage(info, isLocal, config);
                if (image != null) {
                    Platform.runLater(() -> target.setImage(image));
                }
            }
        }, "modpack-image-loader");
        imageLoader.setDaemon(true);
        imageLoader.start();
    }

    /**
     * Resolves a modpack's profile image from the local folder (or S3 for server
     * modpacks). Returns {@code null} when none is found, in which case the
     * placeholder already in the {@link ImageView} is kept. Runs off the UI thread.
     */
    private static Image loadModpackImage(ModpackInfo info, boolean isLocal, Config config) {
        String profileImagePrefix = info.getRoot() + "/profileImage/";

        if (isLocal) {
            try {
                File profileImageDir = new File(config.getCurseforge_path() + "/" + profileImagePrefix);

                if (profileImageDir.exists() && profileImageDir.isDirectory()) {
                    File[] files = profileImageDir.listFiles((dir, name) -> {
                        String lower = name.toLowerCase();
                        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                    });

                    if (files != null && files.length > 0 && files[0].exists()) {
                        try (InputStream in = new FileInputStream(files[0])) {
                            return new Image(in);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Could not load local image for modpack: " + info.getModpackId() + ", using default.");
            }
            return null;
        }

        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(config.getBucketName())
                    .prefix(profileImagePrefix)
                    .build();

            String imageKey = null;
            for (S3Object obj : B2ClientProvider.getClient().listObjectsV2Paginator(listRequest).contents()) {
                String key = obj.key().toLowerCase();
                if (key.endsWith(".png") || key.endsWith(".jpg") || key.endsWith(".jpeg")) {
                    imageKey = obj.key();
                    break;
                }
            }

            if (imageKey == null) {
                return null;
            }

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(imageKey)
                    .build();

            try (InputStream inputStream = B2ClientProvider.getClient().getObject(getObjectRequest)) {
                return new Image(inputStream);
            }
        } catch (Exception e) {
            System.out.println("Could not load image for modpack: " + info.getModpackId() + ", using default.");
            return null;
        }
    }
}
