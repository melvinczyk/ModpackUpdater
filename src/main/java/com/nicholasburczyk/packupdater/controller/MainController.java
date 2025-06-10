package com.nicholasburczyk.packupdater.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.GUI;
import com.nicholasburczyk.packupdater.Main;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ConnectionStatus;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import com.nicholasburczyk.packupdater.util.ModpackUIHelper;
import com.nicholasburczyk.packupdater.util.UpdateChecker;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainController {

    @FXML
    private Button adminLoginButton;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private Label connectionStatusIcon;
    @FXML
    private Label lastUpdateLabel;
    @FXML
    private VBox serverModpacksContainer;
    @FXML
    private VBox localModpacksContainer;
    @FXML
    private Label totalServerModpacksLabel;
    @FXML
    private Label updatesAvailableLabel;
    @FXML
    private Label totalLocalModpacksLabel;

    Config config = ConfigManager.getInstance().getConfig();

    // idgaf
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "goon4lyfe";

    @FXML
    public void initialize() {
        updateLastCheckTime();

        connectionStatusLabel.setText("Checking server...");
        connectionStatusLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        connectionStatusIcon.setText("●");
        connectionStatusIcon.setStyle("-fx-text-fill: #ffa500; -fx-font-size: 14px;");

        Task<ConnectionStatus> connectionTask = getConnectionStatusTask();
        new Thread(connectionTask).start();

        Task<Void> fetchModpacksTask = getTask();
        new Thread(fetchModpacksTask).start();
    }

    @FXML
    private void reconnectToClientAction() {
        B2ClientProvider.reconnectToClient();
        checkServerStatus();
    }


    @FXML
    private void goToSettings(ActionEvent event) throws Exception {
        GUI.setRoot("settings_view.fxml");
    }

    @FXML
    private void goToHelp(ActionEvent event) throws Exception {
        GUI.setRoot("help_view.fxml");
    }

    private void goToAdminPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nicholasburczyk/packupdater/fxml/admin_view.fxml"));
            Parent adminRoot = loader.load();

            Stage currentStage = (Stage) adminLoginButton.getScene().getWindow();

            Scene adminScene = new Scene(adminRoot);
            currentStage.setScene(adminScene);
            currentStage.setTitle("Modpack Manager - Admin Panel");

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Error", "Could not load admin page: " + e.getMessage());
        }
    }

    @FXML
    private void showAdminLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nicholasburczyk/packupdater/fxml/admin_login.fxml"));
            Parent root = loader.load();

            AdminLoginController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Admin Login");
            dialogStage.setScene(new Scene(root));
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.showAndWait();

            if (controller.isSubmitted()) {
                String username = controller.getUsername();
                String password = controller.getPassword();

                if (validateAdminCredentials(username, password)) {
                    goToAdminPage();
                } else {
                    showErrorAlert("Login Failed", "Invalid admin credentials.");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Error", "Could not load admin login: " + e.getMessage());
        }
    }

    @FXML
    private void migrateOldModpacksAction(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nicholasburczyk/packupdater/fxml/migrate_modpack.fxml"));
            Parent root = loader.load();

            MigrateModpackController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Migrate Old Modpack");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setResizable(false);
            dialogStage.setScene(new Scene(root));
            controller.setDialogStage(dialogStage);

            Map<String, ModpackInfo> localModpacks = ModpackRegistry.getLocalModpacks();
            Set<String> localModpackIds = localModpacks.keySet();

            Map<String, ModpackInfo> serverModpacks = ModpackRegistry.getServerModpacks();
            List<String> filteredServerModpacks = new ArrayList<>();

            for (String serverModpackId : serverModpacks.keySet()) {
                if (!localModpackIds.contains(serverModpackId)) {
                    filteredServerModpacks.add(serverModpackId);
                }
            }

            controller.setServerModpacks(filteredServerModpacks);

            dialogStage.showAndWait();

            if (controller.isConfirmed()) {
                String path = controller.getSelectedPath();
                String selectedServerModpack = controller.getSelectedServerModpack();

                System.out.println("Migrating from: " + path + " to server modpack: " + selectedServerModpack);
            }
            refreshModpackList(event);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void refreshModpackList(ActionEvent event) {
        serverModpacksContainer.getChildren().clear();
        localModpacksContainer.getChildren().clear();

        Task<Void> fetchModpacksTask = getVoidTask();
        loadLocalModpacks();

        fetchModpacksTask.setOnSucceeded(e -> {
            System.out.println("Successfully fetched modpack info.");

            Map<String, ModpackInfo> localModpacks = ModpackRegistry.getLocalModpacks();
            Map<String, ModpackInfo> serverModpacks = ModpackRegistry.getServerModpacks();

            int totalUpdates = 0;
            for (String modpackId : localModpacks.keySet()) {
                ModpackInfo local = localModpacks.get(modpackId);
                ModpackInfo server = serverModpacks.get(modpackId);

                if (server != null) {
                    int updates = UpdateChecker.countUpdatesAvailable(server, local);
                    totalUpdates += updates;
                    local.setUpdateCount(updates);
                }
            }

            int finalTotalUpdates = totalUpdates;

            Platform.runLater(() -> {
                ModpackUIHelper.populateModpackList(serverModpacksContainer, serverModpacks, false);
                ModpackUIHelper.populateModpackList(localModpacksContainer, localModpacks, true);

                totalServerModpacksLabel.setText(String.valueOf(serverModpacks.size()));
                totalLocalModpacksLabel.setText(String.valueOf(localModpacks.size()));

                updatesAvailableLabel.setText(finalTotalUpdates > 0
                        ? finalTotalUpdates + " updates available"
                        : "Up to date");
            });
        });

        new Thread(fetchModpacksTask).start();
    }

    private Task<Void> getTask() {
        Task<Void> fetchModpacksTask = getVoidTask();
        fetchModpacksTask.setOnSucceeded(e -> {
            System.out.println("Successfully fetched modpack info.");
            Map<String, ModpackInfo> localModpacks = ModpackRegistry.getLocalModpacks();
            Map<String, ModpackInfo> serverModpacks = ModpackRegistry.getServerModpacks();

            int totalUpdates = 0;

            for (String modpackId : localModpacks.keySet()) {
                ModpackInfo local = localModpacks.get(modpackId);
                ModpackInfo server = serverModpacks.get(modpackId);

                if (server != null) {
                    int updates = UpdateChecker.countUpdatesAvailable(server, local);
                    totalUpdates += updates;
                    local.setUpdateCount(updates);
                }
            }

            int finalTotalUpdates = totalUpdates;
            Platform.runLater(() -> {
                ModpackUIHelper.populateModpackList(serverModpacksContainer, serverModpacks, false);
                ModpackUIHelper.populateModpackList(localModpacksContainer, localModpacks, true);
                totalServerModpacksLabel.setText(String.valueOf(serverModpacks.size()));
                totalLocalModpacksLabel.setText(String.valueOf(localModpacks.size()));
                updatesAvailableLabel.setText(finalTotalUpdates > 0
                        ? finalTotalUpdates + " updates available"
                        : "Up to date");
            });
        });

        return fetchModpacksTask;
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

    private Task<Void> getVoidTask() {
        Task<Void> fetchModpacksTask = new Task<>() {
            @Override
            protected Void call() {
                B2ClientProvider.fetchAndStoreModpackInfo(config.getBucketName());
                return null;
            }
        };

        fetchModpacksTask.setOnSucceeded(e -> {
            System.out.println("Successfully fetched modpack info.");
        });

        fetchModpacksTask.setOnFailed(e -> {
            Throwable ex = fetchModpacksTask.getException();
            ex.printStackTrace();
            Platform.runLater(() -> showErrorAlert("Modpack Load Failed",
                    "Could not load modpacks.json:\n" + ex.getMessage()));
        });
        return fetchModpacksTask;
    }

    private Task<ConnectionStatus> getConnectionStatusTask() {
        Task<ConnectionStatus> connectionTask = new Task<>() {
            @Override
            protected ConnectionStatus call() {
                return B2ClientProvider.checkConnection();
            }
        };

        connectionTask.setOnSucceeded(event -> {
            ConnectionStatus status = connectionTask.getValue();
            Platform.runLater(() -> {
                connectionStatusLabel.setText(status.isConnected ? "Connected" : "Error: " + status.message);
                connectionStatusIcon.setText("●");
                connectionStatusIcon.setStyle("-fx-text-fill: " +
                        (status.isConnected ? "#4CAF50" : "#F44336") + "; -fx-font-size: 14px;");
            });
        });

        connectionTask.setOnFailed(event -> {
            Throwable ex = connectionTask.getException();
            Platform.runLater(() -> {
                connectionStatusLabel.setText("Error: " + (ex != null ? ex.getMessage() : "Unknown"));
                connectionStatusIcon.setText("●");
                connectionStatusIcon.setStyle("-fx-text-fill: #F44336; -fx-font-size: 14px;");
            });
        });
        return connectionTask;
    }

    private void checkServerStatus() {
        connectionStatusLabel.setText("Checking server...");
        connectionStatusLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        connectionStatusIcon.setText("●");
        connectionStatusIcon.setStyle("-fx-text-fill: #ffa500; -fx-font-size: 14px;");
        Task<ConnectionStatus> task = new Task<>() {
            @Override
            protected ConnectionStatus call() {
                return B2ClientProvider.checkConnection();
            }
        };

        task.setOnSucceeded(event -> {
            ConnectionStatus status = task.getValue();
            Platform.runLater(() -> {
                connectionStatusLabel.setText(status.isConnected ? "Connected" : "Error: " + status.message);
                connectionStatusIcon.setText("●");
                connectionStatusIcon.setStyle("-fx-text-fill: " +
                        (status.isConnected ? "#4CAF50" : "#F44336") +
                        "; -fx-font-size: 14px;");
            });
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            String message = (ex != null) ? ex.getMessage() : "Unknown error";

            Platform.runLater(() -> {
                connectionStatusLabel.setText("Error: " + message);
                connectionStatusIcon.setText("●");
                connectionStatusIcon.setStyle("-fx-text-fill: #F44336; -fx-font-size: 14px;");
            });
        });

        updateLastCheckTime();

        new Thread(task).start();
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateLastCheckTime() {
        LocalTime now = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        lastUpdateLabel.setText("Last checked: " + now.format(formatter));
    }

    private boolean validateAdminCredentials(String username, String password) {
        return ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password);
    }
}
