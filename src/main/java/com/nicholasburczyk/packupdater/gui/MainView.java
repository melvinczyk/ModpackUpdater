package com.nicholasburczyk.packupdater.gui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class MainView {

    public Scene createMainScene() {
        Button checkUpdatesButton = new Button("Check for Updates");
        checkUpdatesButton.setOnAction(e -> {
            System.out.println("Update check clicked!");
        });

        VBox layout = new VBox(10);
        layout.getChildren().addAll(checkUpdatesButton);

        return new Scene(layout, 300, 200);
    }
}