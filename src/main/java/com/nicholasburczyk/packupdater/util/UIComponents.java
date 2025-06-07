package com.nicholasburczyk.packupdater.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class UIComponents {

    public static HBox createModpackEntry(String title, String description, Image image, boolean isLocal) {
        ImageView imageView = new ImageView(image);
        imageView.setFitHeight(64);
        imageView.setFitWidth(64);
        imageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");

        VBox textBox = new VBox(5);
        textBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        textBox.getChildren().addAll(titleLabel, descLabel);

        Button actionButton = new Button(isLocal ? "Open" : "Download");
        actionButton.setStyle("""
                    -fx-background-color: #4CAF50;
                    -fx-text-fill: white;
                    -fx-background-radius: 8;
                    -fx-padding: 6 14;
                    -fx-font-size: 12px;
                """);

        VBox buttonWrapper = new VBox(actionButton);
        buttonWrapper.setAlignment(Pos.CENTER_RIGHT);
        buttonWrapper.setPadding(new Insets(0, 0, 0, 10));

        HBox card = new HBox(12);
        card.setPadding(new Insets(10));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(520);
        card.setStyle("""
                    -fx-background-color: #3c3c3c;
                    -fx-background-radius: 12;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0, 0, 2);
                """);

        card.getChildren().addAll(imageView, textBox, buttonWrapper);
        return card;
    }

}
