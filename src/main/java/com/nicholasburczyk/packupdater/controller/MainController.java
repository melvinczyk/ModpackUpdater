package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.Main;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ConnectionStatus;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import com.nicholasburczyk.packupdater.util.ModpackUIHelper;
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

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    Config config = ConfigManager.getInstance().getConfig();

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    @FXML
    public void initialize() {
        updateLastCheckTime();

        connectionStatusLabel.setText("Checking server...");
        connectionStatusLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        connectionStatusIcon.setText("●");
        connectionStatusIcon.setStyle("-fx-text-fill: #ffa500; -fx-font-size: 14px;");

        Task<ConnectionStatus> connectionTask = getConnectionStatusTask();
        new Thread(connectionTask).start();

        Task<Void> fetchModpacksTask = getVoidTask();
        fetchModpacksTask.setOnSucceeded(e -> {
            System.out.println("Successfully fetched modpack info.");
            Platform.runLater(() -> {
                ModpackUIHelper.populateModpackList(serverModpacksContainer, ModpackRegistry.getServerModpacks(), false);
                ModpackUIHelper.populateModpackList(localModpacksContainer, ModpackRegistry.getLocalModpacks(), true);
            });
        });
        new Thread(fetchModpacksTask).start();
    }

    @FXML
    private void reconnectToClientAction() {
        B2ClientProvider.reconnectToClient();
        checkServerStatus();
    }


    @FXML
    private void goToSettings(ActionEvent event) throws Exception {
        Main.setRoot("settings_view.fxml");
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
    private void refreshModpackList(ActionEvent event) {
        serverModpacksContainer.getChildren().clear();
        localModpacksContainer.getChildren().clear();
        Task<Void> fetchModpacksTask = getVoidTask();
        fetchModpacksTask.setOnSucceeded(e -> {
            System.out.println("Successfully fetched modpack info.");
            Platform.runLater(() -> {
                ModpackUIHelper.populateModpackList(serverModpacksContainer, ModpackRegistry.getServerModpacks(), false);
                ModpackUIHelper.populateModpackList(localModpacksContainer, ModpackRegistry.getLocalModpacks(), true);
            });
        });
        new Thread(fetchModpacksTask).start();
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

            List<String> serverModpackNames = new ArrayList<>(ModpackRegistry.getServerModpacks().keySet());
            controller.setServerModpacks(serverModpackNames);

            dialogStage.showAndWait();

            if (controller.isConfirmed()) {
                String path = controller.getSelectedPath();
                String selectedServerModpack = controller.getSelectedServerModpack();

                System.out.println("Migrating from: " + path + " to server modpack: " + selectedServerModpack);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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

    private boolean validateAdminCredentials(String username, String password) {
        return ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password);
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
}
