package com.nicholasburczyk.packupdater;

import com.nicholasburczyk.packupdater.view.ViewLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        Scene scene = ViewLoader.load("main_view.fxml");
        stage.setTitle("Pack Updater");
        stage.setScene(scene);
        stage.show();
    }

    public static void setRoot(String fxml) throws Exception {
        primaryStage.setScene(ViewLoader.load(fxml));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
