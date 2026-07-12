package com.nicholasburczyk.packupdater.util;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.ChangeOperation;
import com.nicholasburczyk.packupdater.model.ChangelogEntry;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UIComponents {

    public static HBox createModpackEntry(ModpackInfo modpack, ImageView imageView, boolean isLocal) {
        imageView.setFitHeight(64);
        imageView.setFitWidth(64);
        imageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");

        VBox textBox = new VBox(5);
        textBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(modpack.getDisplayName());
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label descLabel = new Label("MC: " + modpack.getMinecraftVersion() + " | " + modpack.getModLoader() + ": " + modpack.getModLoaderVersion() + " | Version: " + modpack.getVersion());
        descLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        textBox.getChildren().addAll(titleLabel, descLabel);

        VBox buttonContainer = new VBox(5);
        buttonContainer.setAlignment(Pos.CENTER_RIGHT);
        buttonContainer.setPadding(new Insets(0, 0, 0, 10));

        if (isLocal) {
            Button openButton = new Button("Open");
            openButton.setStyle("""
                        -fx-background-color: #4CAF50;
                        -fx-text-fill: white;
                        -fx-background-radius: 8;
                        -fx-padding: 6 14;
                        -fx-font-size: 12px;
                    """);
            openButton.setOnAction(e -> {
                try {
                    File modpackFolder = new File(ConfigManager.getInstance().getConfig().getCurseforge_path(), modpack.getRoot());
                    if (modpackFolder.exists()) {
                        Desktop.getDesktop().open(modpackFolder);
                    } else {
                        System.err.println("Modpack folder not found: " + modpackFolder.getAbsolutePath());
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
            buttonContainer.getChildren().add(openButton);

            if (modpack.getUpdateCount() > 0) {
                Button updateButton = new Button("Update");
                updateButton.setStyle("""
                            -fx-background-color: orange;
                            -fx-text-fill: white;
                            -fx-background-radius: 8;
                            -fx-padding: 6 14;
                            -fx-font-size: 12px;
                        """);

                updateButton.setOnAction(e -> {
                    System.out.println("Updating modpack: " + modpack.getModpackId());
                    ModpackInfo remoteModpack = ModpackRegistry.getServerModpacks().get(modpack.getModpackId());
                    if (remoteModpack != null) {
                        ModpackUpdater.applyUpdates(modpack, remoteModpack);
                        descLabel.setText("MC: " + modpack.getMinecraftVersion() + " | " + modpack.getModLoader() + ": " + modpack.getModLoaderVersion() + " | Version: " + modpack.getVersion());
                        updateButton.setDisable(true);
                        updateButton.setText("Updated");
                    } else {
                        System.err.println("Remote modpack not found for: " + modpack.getModpackId());
                    }
                });

                buttonContainer.getChildren().add(updateButton);
            }
        }

        Button changelogButton = new Button("Changelog");
        changelogButton.setStyle("""
                    -fx-background-color: #2196F3;
                    -fx-text-fill: white;
                    -fx-background-radius: 8;
                    -fx-padding: 6 14;
                    -fx-font-size: 12px;
                """);

        changelogButton.setOnAction(e -> showChangelogDialog(modpack));
        buttonContainer.getChildren().add(changelogButton);

        HBox card = new HBox(12);
        card.setPadding(new Insets(10));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(520);
        card.setStyle("""
                    -fx-background-color: #3c3c3c;
                    -fx-background-radius: 12;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0, 0, 2);
                """);

        card.getChildren().addAll(imageView, textBox, buttonContainer);
        return card;
    }

    private static void showChangelogDialog(ModpackInfo modpack) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Changelog - " + modpack.getDisplayName());
        dialogStage.setWidth(600);
        dialogStage.setHeight(500);

        VBox dialogContent = new VBox(10);
        dialogContent.setPadding(new Insets(20));
        dialogContent.setStyle("-fx-background-color: #2b2b2b;");

        Label titleLabel = new Label("Changelog for " + modpack.getDisplayName());
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        VBox changelogContent = new VBox(15);
        changelogContent.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 8;");
        changelogContent.setPadding(new Insets(15));

        // Render straight from the changelog already loaded into the manifest.
        // Fetching it from S3 by root broke for local packs, whose root is a
        // local folder name that isn't a valid server key.
        List<ChangelogEntry> changelog = modpack.getChangelog();

        if (changelog != null && !changelog.isEmpty()) {
            boolean isLatest = true;
            for (ChangelogEntry entry : changelog) {
                changelogContent.getChildren().add(createVersionBlock(entry, isLatest));
                isLatest = false;
            }
        } else {
            Label noChangelogLabel = new Label("No changelog available for this modpack.");
            noChangelogLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");
            changelogContent.getChildren().add(noChangelogLabel);
        }

        ScrollPane scrollPane = new ScrollPane(changelogContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #2b2b2b; -fx-background-color: #2b2b2b;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button closeButton = new Button("Close");
        closeButton.setStyle("""
                    -fx-background-color: #f44336;
                    -fx-text-fill: white;
                    -fx-background-radius: 8;
                    -fx-padding: 8 16;
                    -fx-font-size: 12px;
                """);
        closeButton.setOnAction(e -> dialogStage.close());

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().add(closeButton);

        dialogContent.getChildren().addAll(titleLabel, scrollPane, buttonBox);

        Scene dialogScene = new Scene(dialogContent);
        dialogStage.setScene(dialogScene);
        dialogStage.showAndWait();
    }

    private static VBox createVersionBlock(ChangelogEntry entry, boolean isLatest) {
        VBox versionBox = new VBox(8);
        versionBox.setStyle("-fx-background-color: #4a4a4a; -fx-background-radius: 6; -fx-padding: 12;");

        String color = isLatest ? "#4CAF50" : "#FFA500";
        String version = entry.getVersion() != null ? entry.getVersion() : "Unknown";
        Label versionLabel = new Label("Version: " + version);
        versionLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        versionLabel.setWrapText(true);
        versionBox.getChildren().add(versionLabel);

        String timestamp = entry.getTimestamp();
        if (timestamp != null && !timestamp.isEmpty()) {
            String formattedDate = timestamp;
            try {
                ZonedDateTime dateTime = ZonedDateTime.parse(timestamp);
                formattedDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
            } catch (Exception ignored) {
                // fall back to the raw timestamp
            }
            versionBox.getChildren().add(infoLabel("Date: " + formattedDate));
        }

        String message = entry.getMessage();
        if (message != null && !message.isEmpty()) {
            versionBox.getChildren().add(infoLabel("Changes: " + message));
        }

        List<ChangeOperation> operations = entry.getOperations();
        if (operations != null && !operations.isEmpty()) {
            versionBox.getChildren().add(infoLabel("Operations:"));
            for (ChangeOperation op : operations) {
                StringBuilder line = new StringBuilder("  - ").append(op.getType());
                if ("Moved".equals(op.getType()) && op.getOldPath() != null) {
                    line.append(": ").append(op.getOldPath()).append(" -> ").append(op.getNewPath());
                } else if (op.getPath() != null && !op.getPath().isEmpty()) {
                    line.append(": ").append(op.getPath());
                }

                Label opLabel = new Label(line.toString());
                opLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 13px;");
                opLabel.setWrapText(true);
                versionBox.getChildren().add(opLabel);
            }
        }

        return versionBox;
    }

    private static Label infoLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");
        label.setWrapText(true);
        return label;
    }
}
