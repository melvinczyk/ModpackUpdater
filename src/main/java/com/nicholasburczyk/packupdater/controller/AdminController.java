package com.nicholasburczyk.packupdater.controller;

import com.nicholasburczyk.packupdater.GUI;
import com.nicholasburczyk.packupdater.Main;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.Instant;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import javafx.stage.Stage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AdminController implements Initializable {

    @FXML
    private ComboBox<String> localModpackSelector;
    @FXML
    private ComboBox<String> serverModpackSelector;
    @FXML
    private Button uploadNewModpackButton;
    @FXML
    private Button selectAllButton;
    @FXML
    private Button deselectAllButton;
    @FXML
    private Button stageSelectedButton;
    @FXML
    private Button uploadVersionButton;
    @FXML
    private Button clearStagedButton;
    @FXML
    private VBox diffContainer;
    @FXML
    private VBox stagedContainer;
    @FXML
    private Label statusLabel;
    @FXML
    private Label stagedCountLabel;
    @FXML
    private Label filteredCountLabel;
    @FXML
    private Label stagedFilteredCountLabel;
    @FXML
    private Label currentVersion;
    @FXML
    private ProgressBar progressBar;

    @FXML
    private CheckBox showAddedChanges;
    @FXML
    private CheckBox showModifiedChanges;
    @FXML
    private CheckBox showDeletedChanges;

    @FXML
    private CheckBox showStagedAdded;
    @FXML
    private CheckBox showStagedModified;
    @FXML
    private CheckBox showStagedDeleted;

    @FXML
    private VBox foldersContainer;
    @FXML
    private TextField newVersionField;
    @FXML
    private TextField changelogMessageField;

    private Config config = ConfigManager.getInstance().getConfig();
    private List<FileChange> allChanges = new ArrayList<>();
    private Set<FileChange> stagedChanges = new HashSet<>();
    private Map<String, CheckBox> changeCheckBoxes = new HashMap<>();
    private Map<String, HBox> changeDisplayBoxes = new HashMap<>();
    private Set<String> selectedFolders = new HashSet<>();
    private Map<String, CheckBox> folderCheckBoxes = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadModpackSelectors();
        updateStagedCount();
        updateFilterCounts();
        clearStagedButton.setDisable(true);

        localModpackSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                initializeFolderManagement();
            }
        });

        serverModpackSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadExistingFolders();
            }
        });
    }

    @FXML
    private void filterChanges(ActionEvent event) {
        displayChanges();
    }

    @FXML
    private void filterStagedChanges(ActionEvent event) {
        displayStagedChanges();
    }

    @FXML
    private void uploadNewModpack(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Modpack Directory");

        Stage stage = (Stage) uploadNewModpackButton.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            // TODO: Implement new modpack upload logic
            showAlert("Upload new modpack functionality to be implemented.", Alert.AlertType.INFORMATION);
        }
    }

    @FXML
    private void goToMain(ActionEvent event) throws Exception {
        GUI.setRoot("main_view.fxml");
    }

    @FXML
    private void uploadNewVersion(ActionEvent event) {
        if (stagedChanges.isEmpty()) {
            showAlert("No changes staged for upload.", Alert.AlertType.WARNING);
            return;
        }

        String newVersion = newVersionField.getText().trim();
        String changelogMessage = changelogMessageField.getText().trim();

        if (newVersion.isEmpty()) {
            showAlert("Please enter a version number.", Alert.AlertType.WARNING);
            return;
        }

        if (changelogMessage.isEmpty()) {
            showAlert("Please enter a changelog message.", Alert.AlertType.WARNING);
            return;
        }

        String serverModpackName = serverModpackSelector.getSelectionModel().getSelectedItem();
        if (serverModpackName == null) {
            showAlert("Please select a server modpack.", Alert.AlertType.WARNING);
            return;
        }

        statusLabel.setText("Uploading changes and updating manifest...");
        progressBar.setVisible(true);

        Task<Void> uploadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                uploadStagedChanges(serverModpackName, newVersion, changelogMessage);
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    statusLabel.setText("Upload completed successfully!");
                    progressBar.setVisible(false);

                    stagedChanges.clear();
                    displayStagedChanges();
                    updateStagedCount();
                    updateStagedFilterCounts();
                    uploadVersionButton.setDisable(true);
                    clearStagedButton.setDisable(true);
                    newVersionField.clear();
                    changelogMessageField.clear();

                    showAlert("New version " + newVersion + " uploaded successfully!", Alert.AlertType.INFORMATION);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    statusLabel.setText("Upload failed: " + getException().getMessage());
                    progressBar.setVisible(false);
                    showAlert("Upload failed: " + getException().getMessage(), Alert.AlertType.ERROR);
                    System.out.println("Upload failed: " + getException().getMessage());
                });
            }
        };

        new Thread(uploadTask).start();
    }

    @FXML
    private void compareModpacks(ActionEvent event) {
        String localModpackName = localModpackSelector.getSelectionModel().getSelectedItem();
        String serverModpackName = serverModpackSelector.getSelectionModel().getSelectedItem();

        if (localModpackName == null || serverModpackName == null) {
            showAlert("Please select both local and server modpacks to compare.", Alert.AlertType.WARNING);
            return;
        }

        ModpackInfo localModpack = ModpackRegistry.getLocalModpacks().get(localModpackName);
        currentVersion.setText("Current Version: " + localModpack.getVersion());
        statusLabel.setText("Comparing modpacks...");
        progressBar.setVisible(true);

        Task<List<FileChange>> compareTask = new Task<>() {
            @Override
            protected List<FileChange> call() throws Exception {
                return performModpackComparison(localModpackName, serverModpackName);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    allChanges = getValue();
                    displayChanges();
                    updateFilterCounts();
                    statusLabel.setText("Comparison complete. Found " + allChanges.size() + " differences.");
                    progressBar.setVisible(false);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    statusLabel.setText("Comparison failed: " + getException().getMessage());
                    progressBar.setVisible(false);
                    showAlert("Error during comparison: " + getException().getMessage(), Alert.AlertType.ERROR);
                });
            }
        };

        new Thread(compareTask).start();
    }

    @FXML
    private void refreshFolders(ActionEvent event) {
        String localModpackName = localModpackSelector.getSelectionModel().getSelectedItem();
        if (localModpackName == null) {
            showAlert("Please select a local modpack first.", Alert.AlertType.WARNING);
            return;
        }

        Set<String> previouslySelected = new HashSet<>(selectedFolders);

        initializeFolderManagement();

        for (String folder : previouslySelected) {
            if (folderCheckBoxes.containsKey(folder)) {
                folderCheckBoxes.get(folder).setSelected(true);
                selectedFolders.add(folder);
            }
        }

        statusLabel.setText("Folders refreshed from local modpack.");
    }

    @FXML
    private void selectAllChanges(ActionEvent event) {
        List<FileChange> filteredChanges = getFilteredChanges();
        for (FileChange change : filteredChanges) {
            CheckBox checkBox = changeCheckBoxes.get(change.getPath());
            if (checkBox != null) {
                checkBox.setSelected(true);
            }
        }
    }

    @FXML
    private void deselectAllChanges(ActionEvent event) {
        List<FileChange> filteredChanges = getFilteredChanges();
        for (FileChange change : filteredChanges) {
            CheckBox checkBox = changeCheckBoxes.get(change.getPath());
            if (checkBox != null) {
                checkBox.setSelected(false);
            }
        }
    }

    @FXML
    private void stageSelectedChanges(ActionEvent event) {
        stagedChanges.clear();

        for (FileChange change : allChanges) {
            CheckBox checkBox = changeCheckBoxes.get(change.getPath());
            if (checkBox != null && checkBox.isSelected()) {
                stagedChanges.add(change);
            }
        }

        displayStagedChanges();
        updateStagedCount();
        updateStagedFilterCounts();

        if (!stagedChanges.isEmpty()) {
            uploadVersionButton.setDisable(false);
            clearStagedButton.setDisable(false);
        } else {
            clearStagedButton.setDisable(true);
        }
    }

    @FXML
    private void clearStagedChanges(ActionEvent event) {
        stagedChanges.clear();
        displayStagedChanges();
        updateStagedCount();
        updateStagedFilterCounts();
        uploadVersionButton.setDisable(true);
        clearStagedButton.setDisable(true);
    }

    private void loadModpackSelectors() {
        Map<String, ModpackInfo> localModpacks = ModpackRegistry.getLocalModpacks();
        localModpackSelector.getItems().clear();
        localModpackSelector.getItems().addAll(localModpacks.keySet());

        Map<String, ModpackInfo> serverModpacks = ModpackRegistry.getServerModpacks();
        serverModpackSelector.getItems().clear();
        serverModpackSelector.getItems().addAll(serverModpacks.keySet());
    }

    private void initializeFolderManagement() {
        foldersContainer.getChildren().clear();
        folderCheckBoxes.clear();
        selectedFolders.clear();

        loadFoldersFromLocalModpack();

        loadExistingFolders();
    }

    private void loadFoldersFromLocalModpack() {
        String localModpackName = localModpackSelector.getSelectionModel().getSelectedItem();
        if (localModpackName == null) {
            return;
        }

        ModpackInfo localModpack = ModpackRegistry.getLocalModpacks().get(localModpackName);
        if (localModpack == null) {
            return;
        }

        File localModpackPath = new File(config.getCurseforge_path(), localModpack.getRoot());
        if (!localModpackPath.exists() || !localModpackPath.isDirectory()) {
            return;
        }

        try {
            File[] directories = localModpackPath.listFiles(File::isDirectory);
            if (directories != null) {
                Arrays.sort(directories, Comparator.comparing(File::getName));

                for (File directory : directories) {
                    String folderName = directory.getName();

                    if (shouldSkipFolder(folderName)) {
                        continue;
                    }

                    if (isFolderEmpty(directory)) {
                        continue;
                    }

                    addFolderCheckBox(folderName, false);
                }
            }
        } catch (Exception e) {
            System.err.println("Error scanning local modpack directories: " + e.getMessage());
            showAlert("Error scanning local modpack directories: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadExistingFolders() {
        String serverModpackName = serverModpackSelector.getSelectionModel().getSelectedItem();
        if (serverModpackName != null) {
            ModpackInfo serverModpack = ModpackRegistry.getServerModpacks().get(serverModpackName);
            if (serverModpack != null && serverModpack.getFolders() != null) {
                for (String folder : serverModpack.getFolders()) {
                    if (folderCheckBoxes.containsKey(folder)) {
                        folderCheckBoxes.get(folder).setSelected(true);
                        selectedFolders.add(folder);
                    } else {
                        addFolderCheckBox(folder + " (missing locally)", false);
                        folderCheckBoxes.get(folder + " (missing locally)").setDisable(true);
                        folderCheckBoxes.get(folder + " (missing locally)").setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 12px;");
                    }
                }
            }
        }
    }

    private void addFolderCheckBox(String folderName, boolean selected) {
        CheckBox checkBox = new CheckBox(folderName);
        checkBox.setSelected(selected);
        checkBox.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px;");

        checkBox.setOnAction(e -> {
            if (checkBox.isSelected()) {
                selectedFolders.add(folderName);
            } else {
                selectedFolders.remove(folderName);
            }
        });

        folderCheckBoxes.put(folderName, checkBox);
        foldersContainer.getChildren().add(checkBox);

        if (selected) {
            selectedFolders.add(folderName);
        }
    }

    private void uploadStagedChanges(String serverModpackName, String newVersion, String changelogMessage) throws Exception {
        ModpackInfo serverModpack = ModpackRegistry.getServerModpacks().get(serverModpackName);
        if (serverModpack == null) {
            throw new Exception("Server modpack not found");
        }

        ModpackInfo localModpack = ModpackRegistry.getLocalModpacks().get(localModpackSelector.getSelectionModel().getSelectedItem());
        if (localModpack == null) {
            throw new Exception("Local modpack not found");
        }

        S3Client client = B2ClientProvider.getClient();
        String bucketName = config.getBucketName();
        File localModpackPath = new File(config.getCurseforge_path(), localModpack.getRoot());

        List<String> operations = new ArrayList<>();

        for (FileChange change : stagedChanges) {
            String serverPath = serverModpack.getRoot() + "/" + change.getPath();

            switch (change.getType()) {
                case ADDED:
                case MODIFIED:
                    File localFile = new File(localModpackPath, change.getPath());
                    if (localFile.exists()) {
                        PutObjectRequest putRequest = PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(serverPath)
                                .build();

                        client.putObject(putRequest, RequestBody.fromFile(localFile));
                        operations.add((change.getType() == FileChangeType.ADDED ? "Added: " : "Modified: ") + change.getPath());
                    }
                    break;

                case DELETED:
                    client.deleteObject(b -> b.bucket(bucketName).key(serverPath));
                    operations.add("Deleted: " + change.getPath());
                    break;
            }
        }

        updateServerManifest(serverModpack, newVersion, changelogMessage, operations, bucketName, client);

        updateLocalManifest(localModpack, localModpackPath, newVersion, changelogMessage, operations);
    }

    private void updateServerManifest(ModpackInfo serverModpack, String newVersion, String changelogMessage,
                                      List<String> operations, String bucketName, S3Client client) throws Exception {

        String manifestPath = serverModpack.getRoot() + "/manifest.json";

        String currentManifest;
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(manifestPath)
                    .build());

            currentManifest = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            response.close();
        } catch (Exception e) {
            currentManifest = createBasicManifest(serverModpack);
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode manifestJson = (ObjectNode) mapper.readTree(currentManifest);

        manifestJson.put("version", newVersion);
        manifestJson.put("lastUpdated", Instant.now().toString());

        ArrayNode foldersArray = mapper.createArrayNode();
        for (String folder : selectedFolders) {
            foldersArray.add(folder);
        }
        manifestJson.set("folders", foldersArray);

        ArrayNode changelog = (ArrayNode) manifestJson.get("changelog");
        if (changelog == null) {
            changelog = mapper.createArrayNode();
            manifestJson.set("changelog", changelog);
        }

        ObjectNode changelogEntry = mapper.createObjectNode();
        changelogEntry.put("version", newVersion);
        changelogEntry.put("timestamp", Instant.now().toString());
        changelogEntry.put("message", changelogMessage);

        ArrayNode operationsArray = mapper.createArrayNode();
        for (String operation : operations) {
            ObjectNode operationObj = mapper.createObjectNode();

            if (operation.startsWith("Added: ")) {
                operationObj.put("type", "Added");
                operationObj.put("path", operation.substring(7));
            } else if (operation.startsWith("Modified: ")) {
                operationObj.put("type", "Modified");
                operationObj.put("path", operation.substring(10));
            } else if (operation.startsWith("Deleted: ")) {
                operationObj.put("type", "Deleted");
                operationObj.put("path", operation.substring(9));
            } else {
                operationObj.put("type", "Unknown");
                operationObj.put("path", operation);
            }

            operationsArray.add(operationObj);
        }
        changelogEntry.set("operations", operationsArray);

        changelog.insert(0, changelogEntry);

        String updatedManifest = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifestJson);

        PutObjectRequest manifestRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(manifestPath)
                .contentType("application/json")
                .build();

        client.putObject(manifestRequest, RequestBody.fromString(updatedManifest));
    }

    private void updateLocalManifest(ModpackInfo localModpack, File localModpackPath, String newVersion,
                                     String changelogMessage, List<String> operations) throws Exception {

        File manifestFile = new File(localModpackPath, "manifest.json");
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode manifestJson;
        if (manifestFile.exists()) {
            manifestJson = (ObjectNode) mapper.readTree(manifestFile);
        } else {
            manifestJson = (ObjectNode) mapper.readTree(createBasicManifest(localModpack));
        }

        manifestJson.put("version", newVersion);
        manifestJson.put("lastUpdated", Instant.now().toString());

        ArrayNode foldersArray = mapper.createArrayNode();
        for (String folder : selectedFolders) {
            foldersArray.add(folder);
        }
        manifestJson.set("folders", foldersArray);

        ArrayNode changelog = (ArrayNode) manifestJson.get("changelog");
        if (changelog == null) {
            changelog = mapper.createArrayNode();
            manifestJson.set("changelog", changelog);
        }

        ObjectNode changelogEntry = mapper.createObjectNode();
        changelogEntry.put("version", newVersion);
        changelogEntry.put("timestamp", Instant.now().toString());
        changelogEntry.put("message", changelogMessage);

        ArrayNode operationsArray = mapper.createArrayNode();
        for (String operation : operations) {
            ObjectNode operationObj = mapper.createObjectNode();

            if (operation.startsWith("Added: ")) {
                operationObj.put("type", "Added");
                operationObj.put("path", operation.substring(7));
            } else if (operation.startsWith("Modified: ")) {
                operationObj.put("type", "Modified");
                operationObj.put("path", operation.substring(10));
            } else if (operation.startsWith("Deleted: ")) {
                operationObj.put("type", "Deleted");
                operationObj.put("path", operation.substring(9));
            } else {
                operationObj.put("type", "Unknown");
                operationObj.put("path", operation);
            }

            operationsArray.add(operationObj);
        }
        changelogEntry.set("operations", operationsArray);

        changelog.insert(0, changelogEntry);

        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifestJson);

        System.out.println("Local manifest updated successfully: " + manifestFile.getAbsolutePath());
    }

    private String createBasicManifest(ModpackInfo modpack) {
        return String.format("""
                        {
                            "modpackId": "%s",
                            "displayName": "%s",
                            "author": "Admin",
                            "description": "Modpack managed via admin panel",
                            "version": "1.0.0",
                            "minecraftVersion": "1.20.1",
                            "modLoader": "Forge",
                            "modLoaderVersion": "47.3.22",
                            "created": "%s",
                            "lastUpdated": "%s",
                            "folders": [],
                            "changelog": []
                        }""",
                modpack.getRoot(),
                modpack.getRoot(),
                Instant.now().toString(),
                Instant.now().toString()
        );
    }

    private List<FileChange> performModpackComparison(String localModpackName, String serverModpackName) throws Exception {
        List<FileChange> changes = new ArrayList<>();

        ModpackInfo localModpack = ModpackRegistry.getLocalModpacks().get(localModpackName);
        ModpackInfo serverModpack = ModpackRegistry.getServerModpacks().get(serverModpackName);

        if (localModpack == null || serverModpack == null) {
            throw new Exception("Modpack information not found");
        }

        if (selectedFolders.isEmpty()) {
            throw new Exception("No folders selected for comparison. Please select at least one folder.");
        }

        File localModpackPath = new File(config.getCurseforge_path(), localModpack.getRoot());

        Map<String, FileInfo> serverFiles = getServerFileInfo(serverModpack.getRoot(), new ArrayList<>(selectedFolders));

        Set<String> processedFiles = new HashSet<>();

        for (String folder : selectedFolders) {
            if (folder.contains("(missing locally)")) {
                continue;
            }

            File folderPath = new File(localModpackPath, folder);
            if (folderPath.exists()) {
                Files.walk(folderPath.toPath())
                        .filter(Files::isRegularFile)
                        .filter(localFile -> !shouldIgnoreFile(localFile.getFileName().toString()))
                        .forEach(localFile -> {
                            try {
                                String relativePath = localModpackPath.toPath().relativize(localFile).toString().replace("\\", "/");
                                String serverPath = serverModpack.getRoot() + "/" + relativePath;
                                processedFiles.add(serverPath);

                                long localSize = Files.size(localFile);
                                String localMd5 = calculateMD5(localFile);
                                if (!serverFiles.containsKey(serverPath)) {
                                    changes.add(new FileChange(relativePath, FileChangeType.ADDED, localSize, localMd5, null, 0));
                                } else {
                                    FileInfo serverInfo = serverFiles.get(serverPath);
                                    if (localSize != serverInfo.size || !localMd5.equals(serverInfo.md5)) {
                                        changes.add(new FileChange(relativePath, FileChangeType.MODIFIED, localSize, localMd5, serverInfo.md5, serverInfo.size));
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Error processing file: " + localFile + ", " + e.getMessage());
                            }
                        });
            }
        }

        for (String serverPath : serverFiles.keySet()) {
            if (!processedFiles.contains(serverPath)) {
                FileInfo serverInfo = serverFiles.get(serverPath);
                String relativePath = serverPath.substring(serverModpack.getRoot().length() + 1);

                String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
                if (!shouldIgnoreFile(fileName)) {
                    changes.add(new FileChange(relativePath, FileChangeType.DELETED, 0, null, serverInfo.md5, serverInfo.size));
                }
            }
        }

        return changes;
    }

    private boolean shouldIgnoreFile(String fileName) {
        if (config.getIgnoredFiles() == null || config.getIgnoredFiles().length == 0) {
            return false;
        }

        for (String ignoredPattern : config.getIgnoredFiles()) {
            if (ignoredPattern == null || ignoredPattern.trim().isEmpty()) {
                continue;
            }

            String pattern = ignoredPattern.trim();

            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (fileName.startsWith(prefix)) {
                    return true;
                }
            } else if (fileName.equals(pattern)) {
                return true;
            } else if (fileName.contains(pattern) || pattern.contains(fileName)) {
                return true;
            }
        }

        return false;
    }

    private Map<String, FileInfo> getServerFileInfo(String serverRoot, List<String> folders) {
        Map<String, FileInfo> serverFiles = new ConcurrentHashMap<>();
        S3Client client = B2ClientProvider.getClient();
        String bucketName = config.getBucketName();

        for (String folder : folders) {
            String prefix = serverRoot + "/" + folder + "/";

            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response response = client.listObjectsV2(request);

            for (S3Object object : response.contents()) {
                String key = object.key();

                String fileName = key.substring(key.lastIndexOf('/') + 1);
                if (!shouldIgnoreFile(fileName)) {
                    String md5Hash = object.eTag().replace("\"", "");
                    serverFiles.put(key, new FileInfo(md5Hash, object.size()));
                }
            }
        }

        return serverFiles;
    }

    private void displayChanges() {
        diffContainer.getChildren().clear();
        changeCheckBoxes.clear();
        changeDisplayBoxes.clear();

        if (allChanges.isEmpty()) {
            Label noChangesLabel = new Label("No differences found between modpacks.");
            noChangesLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12px; -fx-font-style: italic;");
            diffContainer.getChildren().add(noChangesLabel);
            selectAllButton.setDisable(true);
            deselectAllButton.setDisable(true);
            stageSelectedButton.setDisable(true);
            updateFilteredCount(0, 0);
            return;
        }

        selectAllButton.setDisable(false);
        deselectAllButton.setDisable(false);
        stageSelectedButton.setDisable(false);

        List<FileChange> filteredChanges = getFilteredChanges();

        for (FileChange change : filteredChanges) {
            HBox changeBox = createChangeDisplay(change);
            diffContainer.getChildren().add(changeBox);
            changeDisplayBoxes.put(change.getPath(), changeBox);
        }

        updateFilteredCount(filteredChanges.size(), allChanges.size());
    }

    private List<FileChange> getFilteredChanges() {
        return allChanges.stream()
                .filter(change -> {
                    switch (change.getType()) {
                        case ADDED:
                            return showAddedChanges.isSelected();
                        case MODIFIED:
                            return showModifiedChanges.isSelected();
                        case DELETED:
                            return showDeletedChanges.isSelected();
                        default:
                            return true;
                    }
                })
                .collect(Collectors.toList());
    }

    private List<FileChange> getFilteredStagedChanges() {
        return stagedChanges.stream()
                .filter(change -> {
                    switch (change.getType()) {
                        case ADDED:
                            return showStagedAdded.isSelected();
                        case MODIFIED:
                            return showStagedModified.isSelected();
                        case DELETED:
                            return showStagedDeleted.isSelected();
                        default:
                            return true;
                    }
                })
                .collect(Collectors.toList());
    }

    private HBox createChangeDisplay(FileChange change) {
        HBox changeBox = new HBox(10);
        changeBox.setStyle("-fx-padding: 8; -fx-background-color: #4a4a4a; -fx-background-radius: 6;");

        CheckBox checkBox = new CheckBox();
        checkBox.setStyle("-fx-text-fill: white;");
        changeCheckBoxes.put(change.getPath(), checkBox);

        Label typeLabel = new Label(change.getType().getSymbol());
        typeLabel.setStyle("-fx-text-fill: " + change.getType().getColor() + "; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label pathLabel = new Label(change.getPath());
        pathLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px;");

        Label detailsLabel = new Label(change.getDetailsText());
        detailsLabel.setStyle("-fx-text-fill: #bbbbbb; -fx-font-size: 11px;");

        changeBox.getChildren().addAll(checkBox, typeLabel, pathLabel, detailsLabel);
        return changeBox;
    }

    private void displayStagedChanges() {
        stagedContainer.getChildren().clear();

        if (stagedChanges.isEmpty()) {
            Label noStagedLabel = new Label("No changes staged.");
            noStagedLabel.setStyle("-fx-text-fill: #bbbbbb; -fx-font-size: 12px; -fx-font-style: italic;");
            stagedContainer.getChildren().add(noStagedLabel);
            updateStagedFilteredCount(0, 0);
            return;
        }

        List<FileChange> filteredStagedChanges = getFilteredStagedChanges();

        for (FileChange change : filteredStagedChanges) {
            HBox stagedBox = new HBox(10);
            stagedBox.setStyle("-fx-padding: 6; -fx-background-color: #5a5a5a; -fx-background-radius: 4;");

            Label typeLabel = new Label(change.getType().getSymbol());
            typeLabel.setStyle("-fx-text-fill: " + change.getType().getColor() + "; -fx-font-weight: bold; -fx-font-size: 12px;");

            Label pathLabel = new Label(change.getPath());
            pathLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 11px;");

            stagedBox.getChildren().addAll(typeLabel, pathLabel);
            stagedContainer.getChildren().add(stagedBox);
        }

        updateStagedFilteredCount(filteredStagedChanges.size(), stagedChanges.size());
    }

    private void updateStagedCount() {
        stagedCountLabel.setText(stagedChanges.size() + " changes staged");
    }

    private void updateFilterCounts() {
        long addedCount = allChanges.stream().filter(c -> c.getType() == FileChangeType.ADDED).count();
        long modifiedCount = allChanges.stream().filter(c -> c.getType() == FileChangeType.MODIFIED).count();
        long deletedCount = allChanges.stream().filter(c -> c.getType() == FileChangeType.DELETED).count();

        showAddedChanges.setText("Added (" + addedCount + ")");
        showModifiedChanges.setText("Modified (" + modifiedCount + ")");
        showDeletedChanges.setText("Deleted (" + deletedCount + ")");
    }

    private void updateStagedFilterCounts() {
        long stagedAddedCount = stagedChanges.stream().filter(c -> c.getType() == FileChangeType.ADDED).count();
        long stagedModifiedCount = stagedChanges.stream().filter(c -> c.getType() == FileChangeType.MODIFIED).count();
        long stagedDeletedCount = stagedChanges.stream().filter(c -> c.getType() == FileChangeType.DELETED).count();

        showStagedAdded.setText("Added (" + stagedAddedCount + ")");
        showStagedModified.setText("Modified (" + stagedModifiedCount + ")");
        showStagedDeleted.setText("Deleted (" + stagedDeletedCount + ")");
    }

    private void updateFilteredCount(int shown, int total) {
        filteredCountLabel.setText(shown + " of " + total + " shown");
    }

    private void updateStagedFilteredCount(int shown, int total) {
        stagedFilteredCountLabel.setText(shown + " of " + total + " shown");
    }

    private String calculateMD5(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Admin Panel");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class FileInfo {
        final String md5;
        final long size;

        FileInfo(String md5, long size) {
            this.md5 = md5;
            this.size = size;
        }
    }

    private boolean shouldSkipFolder(String folderName) {
        String[] skipFolders = {
                ".git", ".idea", ".vscode", ".minecraft", "logs", "crash-reports",
                "screenshots", "saves", "shaderpacks", "options.txt", "servers.dat",
                "usercache.json", "usernamecache.json", ".DS_Store", "Thumbs.db", ".bzEmpty"
        };

        for (String skipFolder : skipFolders) {
            if (folderName.equals(skipFolder) || folderName.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private boolean isFolderEmpty(File directory) {
        try {
            File[] files = directory.listFiles();
            if (files == null || files.length == 0) {
                return true;
            }

            for (File file : files) {
                if (!file.getName().startsWith(".")) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    // Finle change class //

    private static class FileChange {
        private final String path;
        private final FileChangeType type;
        private final long localSize;
        private final String localMd5;
        private final String serverMd5;
        private final long serverSize;

        public FileChange(String path, FileChangeType type, long localSize, String localMd5, String serverMd5, long serverSize) {
            this.path = path;
            this.type = type;
            this.localSize = localSize;
            this.localMd5 = localMd5;
            this.serverMd5 = serverMd5;
            this.serverSize = serverSize;
        }

        public String getPath() {
            return path;
        }

        public FileChangeType getType() {
            return type;
        }

        public String getDetailsText() {
            switch (type) {
                case ADDED:
                    return "New file (" + formatBytes(localSize) + ")";
                case DELETED:
                    return "Deleted from local (" + formatBytes(serverSize) + ")";
                case MODIFIED:
                    return "Modified (Local: " + formatBytes(localSize) + ", Server: " + formatBytes(serverSize) + ")";
                default:
                    return "";
            }
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            FileChange that = (FileChange) obj;
            return Objects.equals(path, that.path) && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, type);
        }
    }

    private enum FileChangeType {
        ADDED("✅", "#4CAF50"),
        MODIFIED("✏️", "#FF9800"),
        DELETED("❌", "#F44336");

        private final String symbol;
        private final String color;

        FileChangeType(String symbol, String color) {
            this.symbol = symbol;
            this.color = color;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getColor() {
            return color;
        }
    }
}