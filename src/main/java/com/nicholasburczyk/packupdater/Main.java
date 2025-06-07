package com.nicholasburczyk.packupdater;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import com.nicholasburczyk.packupdater.view.ViewLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main extends Application {

    private static Stage primaryStage;

    Config config = ConfigManager.getInstance().getConfig();
    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        loadLocalModpacks();
        Scene scene = ViewLoader.load("main_view.fxml");
        stage.setTitle("Pack Updater");
        stage.setScene(scene);
        stage.show();
    }

    private void loadLocalModpacks() {
        File instancesFolder = new File(config.getCurseforge_path());
        Map<String, ModpackInfo> localModpacks = new HashMap<>();

        if (instancesFolder.exists() && instancesFolder.isDirectory()) {
            File[] folders = instancesFolder.listFiles(File::isDirectory);
            if (folders != null) {
                ObjectMapper mapper = new ObjectMapper();

                for (File folder : folders) {
                    File manifestFile = new File(folder, "manifest.json");
                    if (manifestFile.exists()) {
                        try {
                            ModpackInfo info = mapper.readValue(manifestFile, ModpackInfo.class);
                            localModpacks.put(folder.getName(), info);
                        } catch (IOException e) {
                            System.err.println("Failed to load manifest in " + folder.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        } else {
            System.err.println("Instances folder does not exist or is not a directory: " + config.getCurseforge_path());
        }
        ModpackRegistry.setLocalModpacks(localModpacks);
    }

    public static void setRoot(String fxml) throws Exception {
        primaryStage.setScene(ViewLoader.load(fxml));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
