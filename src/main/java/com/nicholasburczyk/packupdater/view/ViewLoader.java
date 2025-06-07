package com.nicholasburczyk.packupdater.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class ViewLoader {
    public static Scene load(String fxmlFile) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                ViewLoader.class.getResource("/com/nicholasburczyk/packupdater/fxml/" + fxmlFile)
        );
        Parent root = loader.load();
        return new Scene(root, 1000, 600);
    }
}
