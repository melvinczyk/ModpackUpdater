package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MigrateModpackController {
    @FXML private TextField pathTextField;
    @FXML private ComboBox<String> serverModpackComboBox;

    Config config = ConfigManager.getInstance().getConfig();

    private Stage dialogStage;
    private boolean confirmed = false;

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setServerModpacks(List<String> modpackNames) {
        serverModpackComboBox.getItems().setAll(modpackNames);
        if (!modpackNames.isEmpty()) {
            serverModpackComboBox.getSelectionModel().selectFirst();
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getSelectedPath() {
        return pathTextField.getText();
    }

    public String getSelectedServerModpack() {
        return serverModpackComboBox.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void browseFolder(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Modpack Folder");
        File selectedDirectory = directoryChooser.showDialog(dialogStage);
        if (selectedDirectory != null) {
            pathTextField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void cancelAction(ActionEvent event) {
        confirmed = false;
        dialogStage.close();
    }

    @FXML
    private void okAction(ActionEvent event) {
        String inputPath = pathTextField.getText();

        if (inputPath == null || inputPath.isBlank()) {
            showAlert("Please enter a path.");
            return;
        }

        File selectedFolder = new File(inputPath).getAbsoluteFile();
        File instancesFolder = new File(config.getCurseforge_path()).getAbsoluteFile();

        try {
            String instancesPath = instancesFolder.getCanonicalPath();
            String selectedPath = selectedFolder.getCanonicalPath();

            if (!selectedPath.startsWith(instancesPath)) {
                showAlert("The folder must be inside " + config.getCurseforge_path());
                return;
            }

            File parentFolder = selectedFolder.getParentFile();
            if (parentFolder == null || !parentFolder.getCanonicalPath().equals(instancesPath)) {
                showAlert("The folder must be directly inside " + config.getCurseforge_path() + " (no nested folders)");
                return;
            }

            if (!selectedFolder.exists() || !selectedFolder.isDirectory()) {
                showAlert("The specified path does not exist or is not a directory.");
                return;
            }

            File manifestFile = new File(selectedFolder, "manifest.json");
            if (manifestFile.exists()) {
                showAlert("Modpack already migrated.");
                return;
            }

            String selectedModpackName = serverModpackComboBox.getSelectionModel().getSelectedItem();
            if (selectedModpackName == null) {
                showAlert("Please select a server modpack from the dropdown.");
                return;
            }

            ModpackInfo selectedModpackInfo = ModpackRegistry.getServerModpacks().get(selectedModpackName);
            if (selectedModpackInfo == null) {
                showAlert("Selected modpack info not found.");
                return;
            }

            ModpackInfo localModpack = new ModpackInfo();
            localModpack.setRoot(selectedFolder.getName());
            localModpack.setDescription(selectedModpackInfo.getDescription());
            localModpack.setForge_version(selectedModpackInfo.getForge_version());
            localModpack.setMinecraft_version(selectedModpackInfo.getMinecraft_version());

            ModpackRegistry.addLocalModpack(localModpack.getRoot(), localModpack);

            confirmed = true;
            dialogStage.close();

        } catch (IOException e) {
            showAlert("An error occurred while validating the path.");
        }
    }


    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(dialogStage);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
