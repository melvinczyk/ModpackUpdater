package com.nicholasburczyk.packupdater.controller;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class AdminLoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    private boolean submitted = false;

    @FXML
    private void handleLogin() {
        submitted = true;

        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }

    public String getUsername() {
        return usernameField.getText();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public boolean isSubmitted() {
        return submitted;
    }
}
