package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.Main;
import com.nicholasburczyk.packupdater.config.Config;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {
    Config config = ConfigManager.getInstance().getConfig();

    @FXML
    private TextField instancesPathField;

    @FXML
    private Button saveButton;

    @FXML
    private void goToMain(ActionEvent event) throws Exception {
        Main.setRoot("main_view.fxml");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (config.curseforge_path != null && !config.curseforge_path.isEmpty()) {
            instancesPathField.setText(config.curseforge_path);
        }
    }

    @FXML
    private void saveSettings(ActionEvent event) {
        config.curseforge_path = instancesPathField.getText();
        ConfigManager.getInstance().saveConfig();
        System.out.println("Settings saved!");
    }
}
