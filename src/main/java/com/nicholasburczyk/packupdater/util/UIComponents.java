package com.nicholasburczyk.packupdater.util;

import com.nicholasburczyk.packupdater.config.ConfigManager;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class UIComponents {

    public static HBox createModpackEntry(ModpackInfo modpack, Image image, boolean isLocal) {
        ImageView imageView = new ImageView(image);
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
                    ModpackInfo remoteModpack = ModpackRegistry.getServerModpacks().get(modpack.getRoot());
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

        changelogButton.setOnAction(e -> showChangelogDialog(modpack.getDisplayName(), modpack.getRoot()));
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

    private static void showChangelogDialog(String modpackTitle, String modpackRoot) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Changelog - " + modpackTitle);
        dialogStage.setWidth(600);
        dialogStage.setHeight(500);

        VBox dialogContent = new VBox(10);
        dialogContent.setPadding(new Insets(20));
        dialogContent.setStyle("-fx-background-color: #2b2b2b;");

        Label titleLabel = new Label("Changelog for " + modpackTitle);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        VBox changelogContent = new VBox(15);
        changelogContent.setStyle("-fx-background-color: #3c3c3c; -fx-background-radius: 8;");
        changelogContent.setPadding(new Insets(15));

        String changelogText = ChangelogHelper.getChangelogForModpack(modpackRoot);

        if (changelogText != null && !changelogText.trim().isEmpty()) {
            String[] versions = changelogText.split("(?=Version \\d+\\.\\d+(?:\\.\\d+)?)");

            for (int i = 0; i < versions.length; i++) {
                String versionBlock = versions[i];
                if (versionBlock.trim().isEmpty()) continue;

                boolean isLatest = (i == 0);
                VBox versionBox = createVersionBlock(versionBlock.trim(), isLatest);
                changelogContent.getChildren().add(versionBox);
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

    private static VBox createVersionBlock(String versionText, boolean isLatest) {
        VBox versionBox = new VBox(8);
        versionBox.setStyle("-fx-background-color: #4a4a4a; -fx-background-radius: 6; -fx-padding: 12;");

        String[] lines = versionText.split("\n");
        boolean isFirstLine = true;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Label lineLabel = new Label(line);

            if (isFirstLine && line.toLowerCase().contains("version:")) {
                String color = isLatest ? "#4CAF50" : "#FFA500";
                lineLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 16px; -fx-font-weight: bold;");
                isFirstLine = false;
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                lineLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 13px;");
            } else {
                lineLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");
            }
            lineLabel.setWrapText(true);
            versionBox.getChildren().add(lineLabel);
        }
        return versionBox;
    }
}
