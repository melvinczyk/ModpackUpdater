package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.Main;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {

    @FXML
    private void goToSettings(ActionEvent event) throws Exception {
        Main.setRoot("settings_view.fxml");
    }

    @FXML
    private Button adminLoginButton;

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
