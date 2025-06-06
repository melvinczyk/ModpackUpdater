package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.Main;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ConnectionStatus;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

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
    public void initialize() {
        boolean success = B2ClientProvider.reconnectToClient();

        LocalTime lastConnectionCheck = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String formattedTime = lastConnectionCheck.format(formatter);
        lastUpdateLabel.setText("Last checked: " + formattedTime);

        if (!success) {
            connectionStatusLabel.setText("S3 Connection Failed");
            connectionStatusIcon.setText("●");
            connectionStatusIcon.setStyle("-fx-text-fill: #F44336;");
        } else {
            checkServerStatus();
        }
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

        LocalTime lastConnectionCheck = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String formattedTime = lastConnectionCheck.format(formatter);
        lastUpdateLabel.setText("Last checked: " + formattedTime);

        new Thread(task).start();
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

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

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
}
