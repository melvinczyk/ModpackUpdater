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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.Instant;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
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
import java.util.function.Consumer;
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
    @FXML
    private VBox filesContainer;

    private Config config = ConfigManager.getInstance().getConfig();
    private List<FileChange> allChanges = new ArrayList<>();
    private Set<FileChange> stagedChanges = new HashSet<>();
    private Map<String, CheckBox> changeCheckBoxes = new HashMap<>();
    private Map<String, HBox> changeDisplayBoxes = new HashMap<>();
    private Set<String> selectedFolders = new HashSet<>();
    private Map<String, CheckBox> folderCheckBoxes = new HashMap<>();
    private Set<String> selectedFiles = new HashSet<>();
    private Map<String, CheckBox> fileCheckBoxes = new HashMap<>();


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

        serverModpackSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadExistingFiles();
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
        directoryChooser.setTitle("Select New Modpack Directory");

        Stage stage = (Stage) uploadNewModpackButton.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            showNewModpackDialog(selectedDirectory);
        }
    }

    /**
     * Dialog for registering a brand-new server modpack from an arbitrary folder.
     * The admin picks which top-level folders and root files to track, confirms
     * the metadata (pre-filled from minecraftinstance.json when present), and the
     * pack is uploaded and registered without touching any existing modpack.
     */
    private void showNewModpackDialog(File folder) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Upload New Modpack");

        InstanceMeta meta = readInstanceMetadata(folder);

        TextField displayNameField = new TextField(meta.name);
        TextField rootField = new TextField(sanitizeKey(meta.name.isBlank() ? folder.getName() : meta.name));
        TextField mcField = new TextField(meta.minecraftVersion);
        TextField loaderField = new TextField(meta.modLoader);
        TextField loaderVersionField = new TextField(meta.modLoaderVersion);
        TextField versionField = new TextField("1.0.0");

        // Trackable top-level folders.
        Set<String> selFolders = new HashSet<>();
        VBox foldersBox = new VBox(4);
        File[] dirs = folder.listFiles(File::isDirectory);
        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(File::getName));
            for (File d : dirs) {
                if (shouldSkipFolder(d.getName()) || isFolderEmpty(d)) continue;
                CheckBox cb = new CheckBox(d.getName());
                cb.setStyle("-fx-text-fill: #e0e0e0;");
                cb.setOnAction(e -> {
                    if (cb.isSelected()) selFolders.add(d.getName());
                    else selFolders.remove(d.getName());
                });
                foldersBox.getChildren().add(cb);
            }
        }
        if (foldersBox.getChildren().isEmpty()) {
            foldersBox.getChildren().add(mutedLabel("(no trackable folders found)"));
        }

        // Trackable top-level files.
        Set<String> selFiles = new HashSet<>();
        VBox filesBox = new VBox(4);
        File[] rootFiles = folder.listFiles(File::isFile);
        if (rootFiles != null) {
            Arrays.sort(rootFiles, Comparator.comparing(File::getName));
            for (File f : rootFiles) {
                if (shouldIgnoreFile(f.getName())) continue;
                // Never let these be tracked: manifest.json is per-install app
                // state (a client update would clobber its local copy), and
                // minecraftinstance.json is client-specific CurseForge metadata.
                if (f.getName().equals("manifest.json") || f.getName().equals("minecraftinstance.json")) continue;
                CheckBox cb = new CheckBox(f.getName());
                cb.setStyle("-fx-text-fill: #e0e0e0;");
                cb.setOnAction(e -> {
                    if (cb.isSelected()) selFiles.add(f.getName());
                    else selFiles.remove(f.getName());
                });
                filesBox.getChildren().add(cb);
            }
        }
        if (filesBox.getChildren().isEmpty()) {
            filesBox.getChildren().add(mutedLabel("(no trackable root files found)"));
        }

        Label status = new Label();
        status.setWrapText(true);
        status.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        ProgressBar progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);

        Button create = new Button("Create & Upload");
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> dialog.close());

        create.setOnAction(e -> {
            String displayName = displayNameField.getText().trim();
            String root = sanitizeKey(rootField.getText());
            String version = versionField.getText().trim();

            if (displayName.isEmpty() || root.isEmpty() || version.isEmpty()) {
                status.setText("Display name, root and version are required.");
                return;
            }
            if (selFolders.isEmpty() && selFiles.isEmpty()) {
                status.setText("Select at least one folder or file to track.");
                return;
            }

            NewModpackSpec spec = new NewModpackSpec(
                    folder, displayName, root, root,
                    mcField.getText().trim(), loaderField.getText().trim(), loaderVersionField.getText().trim(),
                    version, new ArrayList<>(selFolders), new ArrayList<>(selFiles));

            create.setDisable(true);
            cancel.setDisable(true);
            progress.setVisible(true);

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    createAndUploadModpack(spec, this::updateMessage);
                    return null;
                }
            };
            status.textProperty().bind(task.messageProperty());

            task.setOnSucceeded(ev -> {
                status.textProperty().unbind();
                progress.setVisible(false);
                dialog.close();
                showAlert("Modpack '" + displayName + "' uploaded and registered successfully.", Alert.AlertType.INFORMATION);
                refreshServerModpacksAfterUpload();
            });
            task.setOnFailed(ev -> {
                status.textProperty().unbind();
                progress.setVisible(false);
                create.setDisable(false);
                cancel.setDisable(false);
                Throwable ex = task.getException();
                String msg = ex != null ? ex.getMessage() : "Unknown error";
                status.setText("Failed: " + msg);
                showAlert("Upload failed: " + msg + "\n\nExisting modpacks were not modified.", Alert.AlertType.ERROR);
            });

            new Thread(task).start();
        });

        HBox buttons = new HBox(10, create, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Label header = new Label("Upload New Modpack");
        header.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        Label folderLbl = mutedLabel("Folder: " + folder.getAbsolutePath());
        folderLbl.setWrapText(true);

        VBox form = new VBox(6,
                labeledField("Display name", displayNameField),
                labeledField("Root / ID (S3 folder key)", rootField),
                labeledField("Minecraft version", mcField),
                labeledField("Mod loader", loaderField),
                labeledField("Loader version", loaderVersionField),
                labeledField("Initial version", versionField));

        ScrollPane foldersScroll = new ScrollPane(foldersBox);
        foldersScroll.setFitToWidth(true);
        foldersScroll.setPrefHeight(140);
        ScrollPane filesScroll = new ScrollPane(filesBox);
        filesScroll.setFitToWidth(true);
        filesScroll.setPrefHeight(100);

        VBox content = new VBox(12,
                header, folderLbl, form,
                mutedLabel("Folders to track:"), foldersScroll,
                mutedLabel("Root files to track:"), filesScroll,
                progress, status, buttons);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #2b2b2b;");

        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.setWidth(560);
        dialog.setHeight(720);
        dialog.showAndWait();
    }

    private Label mutedLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        return label;
    }

    private VBox labeledField(String labelText, TextField field) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        return new VBox(2, label, field);
    }

    private String sanitizeKey(String s) {
        String k = s == null ? "" : s.trim();
        while (k.startsWith("/")) k = k.substring(1);
        while (k.endsWith("/")) k = k.substring(0, k.length() - 1);
        return k;
    }

    /**
     * Uploads the tracked files, writes the modpack's manifest, and finally
     * registers it in modpacks.json. Ordering matters: files and manifest are
     * written first, and modpacks.json (the shared index) is updated last with a
     * read-modify-write so an existing pack is never dropped. Aborts before
     * writing anything if the target root/display name collides with an existing
     * modpack.
     */
    private void createAndUploadModpack(NewModpackSpec spec, Consumer<String> status) throws Exception {
        S3Client client = B2ClientProvider.getClient();
        String bucket = config.getBucketName();
        ObjectMapper mapper = new ObjectMapper();

        logServer("Creating new modpack '" + spec.displayName() + "' at root '" + spec.root()
                + "' (bucket=" + bucket + ")");

        // 1. Read the shared index, preserving whatever is already there.
        status.accept("Reading modpacks.json...");
        Map<String, String> modpacks;
        try (ResponseInputStream<GetObjectResponse> in = client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key("modpacks.json").build())) {
            modpacks = mapper.readValue(in, new TypeReference<LinkedHashMap<String, String>>() {});
            logServer("GET modpacks.json -> " + modpacks.size() + " existing entries");
        } catch (NoSuchKeyException e) {
            modpacks = new LinkedHashMap<>();
            logServer("GET modpacks.json -> not found, starting a new index");
        }
        Map<String, String> existingBefore = new LinkedHashMap<>(modpacks);

        // 2. Collision checks — never overwrite an existing modpack.
        if (modpacks.containsKey(spec.displayName())) {
            throw new Exception("A modpack named '" + spec.displayName() + "' is already registered. Choose a different display name.");
        }
        if (modpacks.containsValue(spec.root()) || serverManifestExists(client, bucket, spec.root())) {
            throw new Exception("A modpack already exists at root '" + spec.root() + "'. Choose a different root.");
        }

        // 3. Upload tracked files first, so the pack only becomes visible once its
        // data actually exists on the server.
        int uploaded = uploadModpackFiles(client, bucket, spec, status);

        // 4. Write this modpack's own manifest.
        status.accept("Writing manifest.json...");
        String manifestJson = buildManifestJson(mapper, spec);
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key(spec.root() + "/manifest.json").contentType("application/json").build(),
                RequestBody.fromString(manifestJson));
        logServer("PUT " + spec.root() + "/manifest.json (" + manifestJson.length() + " bytes)");

        // 5. Register in the shared index last (read-modify-write).
        status.accept("Updating modpacks.json...");
        modpacks.put(spec.displayName(), spec.root());
        String modpacksJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(modpacks);
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key("modpacks.json").contentType("application/json").build(),
                RequestBody.fromString(modpacksJson));
        logServer("PUT modpacks.json (" + modpacks.size() + " entries, added '" + spec.displayName() + "')");

        // 6. Read both JSONs back and confirm nothing was lost or corrupted.
        status.accept("Verifying server state...");
        verifyUpload(client, bucket, mapper, spec, existingBefore);

        logServer("New modpack '" + spec.displayName() + "' registered successfully (" + uploaded + " files)");
        status.accept("Done — uploaded " + uploaded + " file(s).");
    }

    private int uploadModpackFiles(S3Client client, String bucket, NewModpackSpec spec, Consumer<String> status) throws IOException {
        Path base = spec.folder().toPath();
        int uploaded = 0;

        for (String folder : spec.folders()) {
            File folderPath = new File(spec.folder(), folder);
            if (!folderPath.exists() || !folderPath.isDirectory()) continue;

            status.accept("Uploading folder: " + folder);
            List<Path> paths;
            try (var walk = Files.walk(folderPath.toPath())) {
                paths = walk.filter(Files::isRegularFile)
                        .filter(p -> !shouldIgnoreFile(p.getFileName().toString()))
                        .collect(Collectors.toList());
            }
            for (Path p : paths) {
                String rel = base.relativize(p).toString().replace("\\", "/");
                String key = spec.root() + "/" + rel;
                client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        RequestBody.fromFile(p));
                logServer("PUT " + key + " (" + p.toFile().length() + " bytes)");
                uploaded++;
            }
        }

        for (String file : spec.files()) {
            File f = new File(spec.folder(), file);
            if (f.exists() && f.isFile() && !shouldIgnoreFile(f.getName())) {
                status.accept("Uploading file: " + file);
                String key = spec.root() + "/" + file;
                client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        RequestBody.fromFile(f));
                logServer("PUT " + key + " (" + f.length() + " bytes)");
                uploaded++;
            }
        }

        logServer("Uploaded " + uploaded + " file(s) under root '" + spec.root() + "'");
        return uploaded;
    }

    private String buildManifestJson(ObjectMapper mapper, NewModpackSpec spec) throws Exception {
        ObjectNode m = mapper.createObjectNode();
        m.put("modpackId", spec.modpackId());
        m.put("displayName", spec.displayName());
        m.put("author", "Admin");
        m.put("description", "");
        m.put("version", spec.version());
        m.put("minecraftVersion", spec.minecraftVersion());
        m.put("modLoader", spec.modLoader());
        m.put("modLoaderVersion", spec.modLoaderVersion());

        String now = Instant.now().toString();
        m.put("created", now);
        m.put("lastUpdated", now);

        ArrayNode foldersArr = mapper.createArrayNode();
        for (String f : spec.folders()) foldersArr.add(f);
        m.set("folders", foldersArr);

        ArrayNode filesArr = mapper.createArrayNode();
        for (String f : spec.files()) filesArr.add(f);
        m.set("files", filesArr);

        ArrayNode changelog = mapper.createArrayNode();
        ObjectNode entry = mapper.createObjectNode();
        entry.put("version", spec.version());
        entry.put("timestamp", now);
        entry.put("message", "Initial modpack upload");
        entry.set("operations", mapper.createArrayNode());
        changelog.add(entry);
        m.set("changelog", changelog);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(m);
    }

    private boolean serverManifestExists(S3Client client, String bucket, String root) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(root + "/manifest.json").build());
            logServer("HEAD " + root + "/manifest.json -> exists");
            return true;
        } catch (NoSuchKeyException e) {
            logServer("HEAD " + root + "/manifest.json -> not found");
            return false;
        }
    }

    private void verifyUpload(S3Client client, String bucket, ObjectMapper mapper,
                              NewModpackSpec spec, Map<String, String> existingBefore) throws Exception {
        // modpacks.json must read back with the new entry AND every prior entry intact.
        Map<String, String> after;
        try (ResponseInputStream<GetObjectResponse> in = client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key("modpacks.json").build())) {
            after = mapper.readValue(in, new TypeReference<LinkedHashMap<String, String>>() {});
        }
        logServer("GET modpacks.json (verify) -> " + after.size() + " entries");

        if (!spec.root().equals(after.get(spec.displayName()))) {
            throw new Exception("Verification failed: modpacks.json is missing the new entry after upload.");
        }
        for (Map.Entry<String, String> prior : existingBefore.entrySet()) {
            if (!prior.getValue().equals(after.get(prior.getKey()))) {
                throw new Exception("Verification failed: existing entry '" + prior.getKey()
                        + "' was altered or lost in modpacks.json.");
            }
        }

        // The new manifest must read back as valid JSON with the expected id.
        try (ResponseInputStream<GetObjectResponse> in = client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(spec.root() + "/manifest.json").build())) {
            JsonNode manifest = mapper.readTree(in);
            if (!spec.modpackId().equals(manifest.path("modpackId").asText(null))) {
                throw new Exception("Verification failed: uploaded manifest.json did not read back correctly.");
            }
        }
        logServer("GET " + spec.root() + "/manifest.json (verify) -> ok; all "
                + existingBefore.size() + " prior entries intact");
    }

    private void refreshServerModpacksAfterUpload() {
        Task<Void> refresh = new Task<>() {
            @Override
            protected Void call() {
                B2ClientProvider.fetchAndStoreModpackInfo(config.getBucketName());
                return null;
            }
        };
        refresh.setOnSucceeded(e -> Platform.runLater(this::loadModpackSelectors));
        new Thread(refresh).start();
    }

    private InstanceMeta readInstanceMetadata(File folder) {
        InstanceMeta meta = new InstanceMeta();
        meta.name = folder.getName();

        File instanceFile = new File(folder, "minecraftinstance.json");
        if (!instanceFile.exists()) {
            return meta;
        }

        try {
            JsonNode root = new ObjectMapper().readTree(instanceFile);
            if (root.hasNonNull("name")) {
                meta.name = root.get("name").asText();
            }

            JsonNode bml = root.path("baseModLoader");
            String mc = bml.path("minecraftVersion").asText(root.path("gameVersion").asText(""));
            if (!mc.isBlank()) {
                meta.minecraftVersion = mc;
            }

            String bmlName = bml.path("name").asText("");        // e.g. "forge-47.3.22"
            String loaderVersion = bml.path("forgeVersion").asText("");
            if (!bmlName.isBlank() && bmlName.contains("-")) {
                int dash = bmlName.indexOf('-');
                meta.modLoader = capitalizeLoader(bmlName.substring(0, dash));
                if (loaderVersion.isBlank()) {
                    loaderVersion = bmlName.substring(dash + 1);
                }
            }
            if (!loaderVersion.isBlank()) {
                meta.modLoaderVersion = loaderVersion;
            }
        } catch (Exception e) {
            System.err.println("Could not parse minecraftinstance.json: " + e.getMessage());
        }
        return meta;
    }

    private String capitalizeLoader(String loaderId) {
        return switch (loaderId.toLowerCase()) {
            case "forge" -> "Forge";
            case "neoforge" -> "NeoForge";
            case "fabric" -> "Fabric";
            case "quilt" -> "Quilt";
            default -> loaderId.isEmpty() ? loaderId
                    : Character.toUpperCase(loaderId.charAt(0)) + loaderId.substring(1);
        };
    }

    private static class InstanceMeta {
        String name = "";
        String minecraftVersion = "1.20.1";
        String modLoader = "Forge";
        String modLoaderVersion = "";
    }

    private record NewModpackSpec(File folder, String displayName, String root, String modpackId,
                                  String minecraftVersion, String modLoader, String modLoaderVersion,
                                  String version, List<String> folders, List<String> files) {}

    /** Logs a server (S3) interaction to stdout with a consistent prefix. */
    private static void logServer(String message) {
        System.out.println("[Server] " + message);
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

        Set<String> previouslySelectedFolders = new HashSet<>(selectedFolders);
        Set<String> previouslySelectedFiles = new HashSet<>(selectedFiles);

        initializeFolderManagement();

        for (String folder : previouslySelectedFolders) {
            if (folderCheckBoxes.containsKey(folder)) {
                folderCheckBoxes.get(folder).setSelected(true);
                selectedFolders.add(folder);
            }
        }

        for (String file : previouslySelectedFiles) {
            if (fileCheckBoxes.containsKey(file)) {
                fileCheckBoxes.get(file).setSelected(true);
                selectedFiles.add(file);
            }
        }

        statusLabel.setText("Folders and files refreshed from local modpack.");
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

        filesContainer.getChildren().clear();
        fileCheckBoxes.clear();
        selectedFiles.clear();

        loadFoldersFromLocalModpack();
        loadFilesFromLocalModpack();

        loadExistingFolders();
        loadExistingFiles();
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

    private void loadFilesFromLocalModpack() {
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
            File[] files = localModpackPath.listFiles(File::isFile);
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));

                for (File file : files) {
                    String fileName = file.getName();

                    if (shouldIgnoreFile(fileName)) {
                        continue;
                    }

                    addFileCheckBox(fileName, false);
                }
            }
        } catch (Exception e) {
            System.err.println("Error scanning local modpack files: " + e.getMessage());
            showAlert("Error scanning local modpack files: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadExistingFiles() {
        String serverModpackName = serverModpackSelector.getSelectionModel().getSelectedItem();
        if (serverModpackName != null) {
            ModpackInfo serverModpack = ModpackRegistry.getServerModpacks().get(serverModpackName);
            if (serverModpack != null && serverModpack.getFiles() != null) {
                for (String file : serverModpack.getFiles()) {
                    if (fileCheckBoxes.containsKey(file)) {
                        fileCheckBoxes.get(file).setSelected(true);
                        selectedFiles.add(file);
                    } else {
                        addFileCheckBox(file + " (missing locally)", false);
                        fileCheckBoxes.get(file + " (missing locally)").setDisable(true);
                        fileCheckBoxes.get(file + " (missing locally)").setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 12px;");
                    }
                }
            }
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

    private void addFileCheckBox(String fileName, boolean selected) {
        CheckBox checkBox = new CheckBox(fileName);
        checkBox.setSelected(selected);
        checkBox.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px;");

        checkBox.setOnAction(e -> {
            if (checkBox.isSelected()) {
                selectedFiles.add(fileName);
            } else {
                selectedFiles.remove(fileName);
            }
        });

        fileCheckBoxes.put(fileName, checkBox);
        filesContainer.getChildren().add(checkBox);

        if (selected) {
            selectedFiles.add(fileName);
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

        logServer("Uploading version " + newVersion + " for '" + serverModpack.getRoot() + "': "
                + stagedChanges.size() + " staged change(s)");

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
                        logServer("PUT " + serverPath + " (" + change.getType() + ", " + localFile.length() + " bytes)");
                        operations.add((change.getType() == FileChangeType.ADDED ? "Added: " : "Modified: ") + change.getPath());
                    } else {
                        logServer("SKIP " + serverPath + " (" + change.getType() + ") - local file missing");
                    }
                    break;

                case DELETED:
                    client.deleteObject(b -> b.bucket(bucketName).key(serverPath));
                    logServer("DELETE " + serverPath);
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
            logServer("GET " + manifestPath + " (" + currentManifest.length() + " bytes)");
        } catch (Exception e) {
            logServer("GET " + manifestPath + " failed (" + e.getMessage() + "), creating a fresh manifest");
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

        ArrayNode filesArray = mapper.createArrayNode();
        for (String file : selectedFiles) {
            filesArray.add(file);
        }
        manifestJson.set("files", filesArray);

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
        logServer("PUT " + manifestPath + " (version " + newVersion + ", " + updatedManifest.length() + " bytes)");
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

        ArrayNode filesArray = mapper.createArrayNode();
        for (String file : selectedFiles) {
            filesArray.add(file);
        }
        manifestJson.set("files", filesArray);

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
                        "files": [],
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

        if (selectedFolders.isEmpty() && selectedFiles.isEmpty()) {
            throw new Exception("No folders or files selected for comparison. Please select at least one folder or file.");
        }

        File localModpackPath = new File(config.getCurseforge_path(), localModpack.getRoot());

        List<String> allTargets = new ArrayList<>();
        allTargets.addAll(selectedFolders);
        allTargets.addAll(selectedFiles);

        Map<String, FileInfo> serverFiles = getServerFileInfo(serverModpack.getRoot(), allTargets, true);

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

        for (String fileName : selectedFiles) {
            if (fileName.contains("(missing locally)")) {
                continue;
            }

            File localFile = new File(localModpackPath, fileName);
            if (localFile.exists() && localFile.isFile()) {
                try {
                    String serverPath = serverModpack.getRoot() + "/" + fileName;
                    processedFiles.add(serverPath);

                    long localSize = Files.size(localFile.toPath());
                    String localMd5 = calculateMD5(localFile.toPath());

                    if (!serverFiles.containsKey(serverPath)) {
                        changes.add(new FileChange(fileName, FileChangeType.ADDED, localSize, localMd5, null, 0));
                    } else {
                        FileInfo serverInfo = serverFiles.get(serverPath);
                        if (localSize != serverInfo.size || !localMd5.equals(serverInfo.md5)) {
                            changes.add(new FileChange(fileName, FileChangeType.MODIFIED, localSize, localMd5, serverInfo.md5, serverInfo.size));
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file: " + localFile + ", " + e.getMessage());
                }
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

    private Map<String, FileInfo> getServerFileInfo(String serverRoot, List<String> targets, boolean includeFiles) {
        Map<String, FileInfo> serverFiles = new ConcurrentHashMap<>();
        S3Client client = B2ClientProvider.getClient();
        String bucketName = config.getBucketName();

        for (String target : targets) {
            if (includeFiles && selectedFiles.contains(target)) {
                String filePath = serverRoot + "/" + target;
                try {
                    ListObjectsV2Request request = ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .prefix(filePath)
                            .build();

                    logServer("LIST " + filePath);
                    for (S3Object object : client.listObjectsV2Paginator(request).contents()) {
                        String key = object.key();
                        if (key.equals(filePath)) {
                            String fileName = key.substring(key.lastIndexOf('/') + 1);
                            if (!shouldIgnoreFile(fileName)) {
                                String md5Hash = object.eTag().replace("\"", "");
                                serverFiles.put(key, new FileInfo(md5Hash, object.size()));
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error fetching server file info for: " + target + ", " + e.getMessage());
                }
            } else if (selectedFolders.contains(target)) {
                String prefix = serverRoot + "/" + target + "/";

                try {
                    ListObjectsV2Request request = ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .build();

                    // Paginate so folders with more than 1000 objects are listed
                    // completely; otherwise the diff would be computed against a
                    // partial view of the server.
                    int before = serverFiles.size();
                    for (S3Object object : client.listObjectsV2Paginator(request).contents()) {
                        String key = object.key();

                        String fileName = key.substring(key.lastIndexOf('/') + 1);
                        if (!shouldIgnoreFile(fileName)) {
                            String md5Hash = object.eTag().replace("\"", "");
                            serverFiles.put(key, new FileInfo(md5Hash, object.size()));
                        }
                    }
                    logServer("LIST " + prefix + " -> " + (serverFiles.size() - before) + " object(s)");
                } catch (Exception e) {
                    System.err.println("Error fetching server file info for folder: " + target + ", " + e.getMessage());
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
            try (java.io.InputStream is = Files.newInputStream(filePath);
                 java.security.DigestInputStream dis =
                         new java.security.DigestInputStream(new java.io.BufferedInputStream(is, 1 << 16), digest)) {
                byte[] buffer = new byte[1 << 16];
                while (dis.read(buffer) != -1) {
                    // read() feeds the digest as a side effect
                }
            }

            byte[] hashBytes = digest.digest();
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