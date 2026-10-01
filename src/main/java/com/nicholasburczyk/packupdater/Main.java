package com.nicholasburczyk.packupdater;

import com.nicholasburczyk.packupdater.core.SoftwareUpdater;
import com.nicholasburczyk.packupdater.ui.MainFrame;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.dialog.Dialogs;

import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "GroidPack Updater");
        System.setProperty("sun.java2d.dpiaware", "true");

        if (SoftwareUpdater.handleStartup(args)) {
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            SwingUtilities.invokeLater(() -> Dialogs.error(null, "Something went wrong",
                    "An unexpected problem occurred. Nothing was deleted.\n\n"
                            + Dialogs.readable(error)));
        });

        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new MainFrame().start();
        });
    }
}
