package com.nicholasburczyk.packupdater.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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

            String selectedModpackName = getSelectedServerModpack();
            if (selectedModpackName == null) {
                showAlert("Please select a server modpack from the dropdown.");
                return;
            }

            ModpackInfo selectedModpackInfo = ModpackRegistry.getServerModpacks().get(selectedModpackName);
            if (selectedModpackInfo == null) {
                showAlert("Selected modpack info not found.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();

            ModpackInfo localManifest = mapper.readValue(
                    mapper.writeValueAsString(selectedModpackInfo),
                    ModpackInfo.class
            );
            localManifest.setRoot(selectedFolder.getName());

            mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, localManifest);

            compareModpackFilesWithBackblaze(selectedFolder, selectedModpackInfo.getRoot(), localManifest.getFolders());

            ModpackRegistry.addLocalModpack(localManifest.getRoot(), localManifest);
            confirmed = true;
            dialogStage.close();


        } catch (IOException e) {
            e.printStackTrace();
            showAlert("An error occurred while validating the path or writing manifest.");
        }
    }

    private void compareModpackFilesWithBackblaze(File localRoot, String serverRootKey, List<String> folders) {
        S3Client client = B2ClientProvider.getClient();
        String bucketName = config.getBucketName();

        Map<String, FileInfo> serverFileInfo = new HashMap<>();
        Set<String> allowedFolders = folders.stream().map(f -> serverRootKey + "/" + f + "/").collect(Collectors.toSet());

        try {
            System.out.println("=== DEBUG: Starting server file collection ===");
            System.out.println("Server root key: " + serverRootKey);
            System.out.println("Folders to check: " + folders);
            System.out.println("Bucket name: " + bucketName);

            for (String folder : folders) {
                String prefix = serverRootKey + "/" + folder + "/";
                System.out.println("Checking server folder with prefix: " + prefix);

                ListObjectsV2Request request = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .build();

                ListObjectsV2Response response = client.listObjectsV2(request);
                System.out.println("Found " + response.contents().size() + " objects with prefix: " + prefix);

                for (S3Object object : response.contents()) {
                    String key = object.key();
                    System.out.println("Found S3 object: " + key + " | Size: " + object.size() + " bytes");

                    serverFileInfo.put(key, new FileInfo("", object.size()));
                    System.out.println("Added to serverFileInfo: " + key + " | Size: " + object.size() + " bytes");
                }
            }
            System.out.println("=== DEBUG: Finished server file collection ===");

            Path localRootPath = localRoot.toPath();

            System.out.println("=== SERVER FILES FOUND ===");
            serverFileInfo.keySet().forEach(key ->
                    System.out.println("Server: " + key + " | Size: " + serverFileInfo.get(key).size + " bytes"));
            System.out.println("=== END SERVER FILES ===");

            Files.walk(localRootPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> folders.stream().anyMatch(folder -> path.toString().replace("\\", "/").contains("/" + folder + "/")))
                    .forEach(localFile -> {
                        try {
                            String relativePath = localRootPath.relativize(localFile).toString().replace("\\", "/");
                            String serverPath = serverRootKey + "/" + relativePath;
                            long localSize = Files.size(localFile);

                            System.out.println("Local file: " + relativePath + " | Size: " + localSize + " bytes");
                            System.out.println("    Looking for server path: " + serverPath);
                            System.out.println("    Available server paths: " + serverFileInfo.keySet().stream()
                                    .filter(key -> key.contains(relativePath.substring(relativePath.lastIndexOf('/') + 1)))
                                    .collect(Collectors.toList()));

                            if (!serverFileInfo.containsKey(serverPath)) {
                                System.out.println(">>> Missing on server: " + relativePath + " (Local size: " + localSize + " bytes)");
                            } else {
                                FileInfo serverInfo = serverFileInfo.get(serverPath);
                                if (localSize != serverInfo.size) {
                                    System.out.println(">>> Size mismatch: " + relativePath +
                                            " | Local size: " + localSize + " bytes | Server size: " + serverInfo.size + " bytes");
                                } else {
                                    System.out.println(">>> Size match: " + relativePath + " (" + localSize + " bytes)");
                                }
                            }
                        } catch (IOException e) {
                            System.err.println("Error comparing file: " + localFile + ", " + e.getMessage());
                        }
                    });

            Set<String> localRelativePaths = Files.walk(localRootPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> folders.stream().anyMatch(folder -> path.toString().replace("\\", "/").contains("/" + folder + "/")))
                    .map(localRootPath::relativize)
                    .map(Path::toString)
                    .map(path -> serverRootKey + "/" + path.replace("\\", "/"))
                    .collect(Collectors.toSet());

            for (String serverPath : serverFileInfo.keySet()) {
                if (allowedFolders.stream().anyMatch(serverPath::startsWith)) {
                    if (!localRelativePaths.contains(serverPath)) {
                        FileInfo serverInfo = serverFileInfo.get(serverPath);
                        System.out.println(">>> Missing locally: " + serverPath.substring(serverRootKey.length() + 1) +
                                " (Server size: " + serverInfo.size + " bytes)");
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("An error occurred during file comparison.");
        }
    }

    private static class FileInfo {
        final String sha1;
        final long size;

        FileInfo(String sha1, long size) {
            this.sha1 = sha1;
            this.size = size;
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
