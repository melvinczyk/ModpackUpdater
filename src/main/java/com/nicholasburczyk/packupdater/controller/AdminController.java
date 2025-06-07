package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.Main;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class AdminController {
    @FXML
    private void goToMain(ActionEvent event) throws Exception {
        Main.setRoot("main_view.fxml");
    }
}
