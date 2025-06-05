package com.nicholasburczyk.packupdater;

import com.nicholasburczyk.packupdater.gui.MainView;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Pack Updater");

        MainView mainView = new MainView();
        primaryStage.setScene(mainView.createMainScene());
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
