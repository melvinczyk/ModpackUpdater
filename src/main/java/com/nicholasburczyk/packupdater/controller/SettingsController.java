package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.Main;
import com.nicholasburczyk.packupdater.config.Config;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {
    Config config = ConfigManager.getInstance().getConfig();

    @FXML
    private TextField instancesPathField;

    @FXML
    private TextField serverUrlField;

    @FXML
    private TextField serverKeyIdField;

    @FXML
    private TextField serverAppKeyField;

    @FXML
    private Label pathStatusIcon;

    @FXML
    private Label pathStatusLabel;

    @FXML
    private Label saveStatusLabel;

    @FXML
    private Label pathStatusLabelFalsePath;

    @FXML
    private Label pathStatusIconFalsePath;

    @FXML
    private CheckBox autoUpdateCheckBox;

    @FXML
    private void goToMain(ActionEvent event) throws Exception {
        Main.setRoot("main_view.fxml");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        pathStatusLabelFalsePath.setVisible(false);
        pathStatusIconFalsePath.setVisible(false);
        if (config.getCurseforge_path() != null && !config.getCurseforge_path().isEmpty()) {
            instancesPathField.setText(config.getCurseforge_path());
        }

        instancesPathField.textProperty().addListener(((observableValue, oldValue, newValue) -> {
            boolean isEmpty = newValue.trim().isEmpty();
            pathStatusIcon.setVisible(isEmpty);
            pathStatusLabel.setVisible(isEmpty);
        }));

        boolean initEmpty = instancesPathField.getText().trim().isEmpty();
        pathStatusIcon.setVisible(initEmpty);
        pathStatusLabel.setVisible(initEmpty);

        if (config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
            serverUrlField.setText(config.getEndpoint());
        }
        if (config.getKeyID() != null && !config.getKeyID().isEmpty()) {
            serverKeyIdField.setText(config.getKeyID());
        }
        if (config.getAppKey() != null && !config.getAppKey().isEmpty()) {
            serverAppKeyField.setText(config.getAppKey());
        }

        autoUpdateCheckBox.setSelected(config.getAutoUpdate());
    }

    @FXML
    private void saveSettings(ActionEvent event) {
        if (isCurseforgeValid(instancesPathField.getText())) {
            config.setCurseforge_path(instancesPathField.getText());
        } else {
            showStatusLabel("⚠️ Not the correct 'curseforge/minecraft/Instances' folder.", "-fx-text-fill: #FF9800; -fx-font-size: 11px;");
            return;
        }
        config.setEndpoint(serverUrlField.getText());
        config.setKeyID(serverKeyIdField.getText());
        config.setAppKey(serverAppKeyField.getText());
        config.setAutoUpdate(autoUpdateCheckBox.isSelected());

        ConfigManager.getInstance().saveConfig();
        ConfigManager.getInstance().reloadConfig();
        showStatusLabel("✔️ Settings saved successfully", "-fx-text-fill: #8BC34A; -fx-font-size: 12px; -fx-font-weight: bold;");
    }

    @FXML
    private void browseForFolder(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Curseforge Instances Folder");

        File initDir = new File(System.getProperty("user.home"));
        if (initDir.exists()) {
            directoryChooser.setInitialDirectory(initDir);
        }

        Stage stage = (Stage) instancesPathField.getScene().getWindow();
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            String selectedPath = selectedDir.getAbsolutePath();
            instancesPathField.setText(selectedPath);

            if (isCurseforgeValid(selectedPath)) {
                pathStatusLabelFalsePath.setVisible(false);
                pathStatusIconFalsePath.setVisible(false);
                pathStatusLabelFalsePath.setText("");
            } else {
                pathStatusLabelFalsePath.setVisible(true);
                pathStatusIconFalsePath.setVisible(true);
                pathStatusLabelFalsePath.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 11px;");
            }
        }
    }

    private void showStatusLabel(String text, String style) {
        saveStatusLabel.setVisible(true);
        saveStatusLabel.setStyle(style);
        saveStatusLabel.setText(text);
        PauseTransition pause = new PauseTransition(Duration.seconds(5));
        pause.setOnFinished(e -> saveStatusLabel.setVisible(false));
        pause.play();
    }

    private boolean isCurseforgeValid(String curseforgePath) {
        Path path = Paths.get(curseforgePath).normalize();

        int nameCount = path.getNameCount();
        if (nameCount < 3) {
            return false;
        }
        String last = path.getName(nameCount - 1).toString();
        String secondLast = path.getName(nameCount - 2).toString();
        String thirdLast = path.getName(nameCount - 3).toString();

        return last.equals("Instances") && secondLast.equals("minecraft") && thirdLast.equals("curseforge");
    }
}
