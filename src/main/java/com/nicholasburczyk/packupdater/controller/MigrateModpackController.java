package com.nicholasburczyk.packupdater.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MigrateModpackController implements Initializable{
    @FXML
    private TextField pathTextField;
    @FXML
    private ComboBox<String> serverModpackComboBox;

    Config config = ConfigManager.getInstance().getConfig();

    private Stage dialogStage;
    private boolean confirmed = false;

    @FXML
    private void onModpackSelected(ActionEvent event) {
        String selectedModpack = serverModpackComboBox.getSelectionModel().getSelectedItem();
        if (selectedModpack != null && !selectedModpack.isEmpty()) {
            showInfoAlert("Selected modpack: " + selectedModpack);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serverModpackComboBox.getSelectionModel().clearSelection();
        serverModpackComboBox.setPromptText("Select a modpack...");
    }

    private void showInfoAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(dialogStage);
        alert.setTitle("Modpack Selected");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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

            List<String> foldersToLook = localManifest.getFolders();
            foldersToLook.add("profileImage");

            compareModpackFiles(selectedFolder, selectedModpackInfo.getRoot(), localManifest.getFolders(), localManifest.getFiles());

            ModpackRegistry.addLocalModpack(localManifest.getModpackId(), localManifest);
            confirmed = true;
            dialogStage.close();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("An error occurred while validating the path or writing manifest.");
        }
    }

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


    private void compareModpackFiles(File localRoot, String serverRootKey, List<String> folders, List<String> files) {
        S3Client client = B2ClientProvider.getClient();
        String bucketName = config.getBucketName();

        Map<String, FileInfo> serverFileInfo = new HashMap<>();
        Set<String> allowedFolders = folders.stream().map(f -> serverRootKey + "/" + f + "/").collect(Collectors.toSet());

        try {
            // Handle folders (existing logic)
            for (String folder : folders) {
                String prefix = serverRootKey + "/" + folder + "/";
                ListObjectsV2Request request = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .build();
                ListObjectsV2Response response = client.listObjectsV2(request);
                for (S3Object object : response.contents()) {
                    String key = object.key();
                    String md5Hash = object.eTag().replace("\"", "");
                    serverFileInfo.put(key, new FileInfo(md5Hash, object.size()));
                }
            }

            // Handle root files (new logic)
            if (files != null && !files.isEmpty()) {
                for (String file : files) {
                    String fileKey = serverRootKey + "/" + file;
                    try {
                        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                                .bucket(bucketName)
                                .key(fileKey)
                                .build();
                        HeadObjectResponse headResponse = client.headObject(headRequest);
                        String md5Hash = headResponse.eTag().replace("\"", "");
                        serverFileInfo.put(fileKey, new FileInfo(md5Hash, headResponse.contentLength()));
                    } catch (NoSuchKeyException e) {
                        System.out.println("Root file not found on server: " + fileKey);
                    }
                }
            }

            Path localRootPath = localRoot.toPath();

            // Create set of local relative paths for folders
            Set<String> localRelativePaths = Files.walk(localRootPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> folders.stream().anyMatch(folder -> path.toString().replace("\\", "/").contains("/" + folder + "/")))
                    .map(localRootPath::relativize)
                    .map(Path::toString)
                    .map(path -> serverRootKey + "/" + path.replace("\\", "/"))
                    .collect(Collectors.toSet());

            // Handle folder files (existing logic)
            for (String serverPath : serverFileInfo.keySet()) {
                if (allowedFolders.stream().noneMatch(serverPath::startsWith)) {
                    // Check if this is a root file
                    boolean isRootFile = files != null && files.stream()
                            .anyMatch(file -> serverPath.equals(serverRootKey + "/" + file));

                    if (!isRootFile) {
                        continue;
                    }
                }

                String relativePath = serverPath.substring(serverRootKey.length() + 1);
                Path localFilePath = localRootPath.resolve(relativePath);
                boolean needsDownload = false;

                if (Files.notExists(localFilePath)) {
                    System.out.println(">> Will download missing file: " + relativePath);
                    needsDownload = true;
                } else {
                    String localMd5 = calculateMD5(localFilePath);
                    String serverMd5 = serverFileInfo.get(serverPath).sha1;
                    if (!localMd5.equals(serverMd5)) {
                        System.out.println(">> Will overwrite mismatched file: " + relativePath);
                        needsDownload = true;
                    }
                }

                if (needsDownload) {
                    downloadFileFromServer(client, bucketName, serverPath, localFilePath);
                }
            }

            // Delete extra local files in folders (existing logic)
            Files.walk(localRootPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> folders.stream().anyMatch(folder -> path.toString().replace("\\", "/").contains("/" + folder + "/")))
                    .forEach(localFile -> {
                        String relativePath = localRootPath.relativize(localFile).toString().replace("\\", "/");
                        String fullServerKey = serverRootKey + "/" + relativePath;

                        if (!serverFileInfo.containsKey(fullServerKey)) {
                            try {
                                System.out.println(">> Deleting extra local file: " + relativePath);
                                Files.delete(localFile);
                            } catch (IOException e) {
                                System.err.println("Failed to delete: " + localFile + " (" + e.getMessage() + ")");
                            }
                        }
                    });

            // Delete extra local root files (new logic)
            if (files != null && !files.isEmpty()) {
                Files.list(localRootPath)
                        .filter(Files::isRegularFile)
                        .forEach(localFile -> {
                            String fileName = localFile.getFileName().toString();
                            String fullServerKey = serverRootKey + "/" + fileName;

                            // Check if this root file should exist according to server
                            boolean shouldExist = files.contains(fileName) && serverFileInfo.containsKey(fullServerKey);

                            // If it's a tracked root file but doesn't exist on server, delete it
                            if (files.contains(fileName) && !shouldExist) {
                                try {
                                    System.out.println(">> Deleting extra local root file: " + fileName);
                                    Files.delete(localFile);
                                } catch (IOException e) {
                                    System.err.println("Failed to delete: " + localFile + " (" + e.getMessage() + ")");
                                }
                            }
                        });
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("An error occurred during file comparison and sync.");
        }
    }

    private void downloadFileFromServer(S3Client client, String bucket, String key, Path localPath) {
        if (key.endsWith(".bzEmpty")) {
            System.out.println("Ignoring .bzEmpty file on server: " + key);
            return;
        }

        try {
            Files.createDirectories(localPath.getParent());

            if (Files.exists(localPath)) {
                Files.delete(localPath);
            }

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            client.getObject(request, ResponseTransformer.toFile(localPath));
            System.out.println("Downloaded: " + key + " → " + localPath);
        } catch (IOException e) {
            System.err.println("Error downloading " + key + ": " + e.getMessage());
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


    private String calculateMD5(Path filePath) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }

    private static class FileInfo {
        final String sha1;
        final long size;

        FileInfo(String hash, long size) {
            this.sha1 = hash;
            this.size = size;
        }
    }
}