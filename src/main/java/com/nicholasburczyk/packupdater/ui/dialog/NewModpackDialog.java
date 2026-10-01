package com.nicholasburczyk.packupdater.ui.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicholasburczyk.packupdater.core.AdminPublisher;
import com.nicholasburczyk.packupdater.core.IgnoreRules;
import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.ui.Choosers;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NewModpackDialog extends JDialog {

    private static final java.util.Set<String> SKIPPED = java.util.Set.of(
            "logs", "crash-reports", "screenshots", "saves", "backups", "local",
            "shaderpacks", "profileimage");

    private final JTextField nameField = Ui.field("GroidPack V6");
    private final JTextField idField = Ui.field("groidpack-v6");
    private final JTextField versionField = Ui.field("1.0.0");
    private final JTextField messageField = Ui.field("First release");
    private final JTextField mcField = Ui.field("1.20.1");
    private final JTextField loaderField = Ui.field("Forge");
    private final JTextField loaderVersionField = Ui.field("47.4.10");
    private final JTextField authorField = Ui.field("Nick");
    private final JTextField descriptionField = Ui.field("What this pack is");
    private final JLabel folderLabel = Ui.faint("No folder picked");
    private final JLabel templateLabel = Ui.faint(" ");
    private final JCheckBox uploadTemplate = new JCheckBox("Publish instance template", true);
    private final JPanel foldersBox = Ui.column();
    private final JPanel filesBox = Ui.column();
    private final Map<String, JCheckBox> folderBoxes = new LinkedHashMap<>();
    private final Map<String, JCheckBox> fileBoxes = new LinkedHashMap<>();

    private Path localFolder;
    private Path instanceTemplate;
    private AdminPublisher.NewModpack result;

    private NewModpackDialog(Window owner) {
        super(owner, "New modpack", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = Ui.column();
        content.setOpaque(true);
        content.setBackground(Theme.BG);
        content.setBorder(Ui.pad(Theme.GAP_L));

        content.add(Ui.label("New modpack", Theme.title(), Theme.TEXT));
        content.add(Ui.strut(Theme.GAP_S));
        content.add(Ui.wrapped("Pick a local instance. Its mod loader details are read from "
                + "minecraftinstance.json so players can have the profile made for them.",
                Theme.TEXT_DIM));
        content.add(Ui.strut(Theme.GAP_L));

        JButton browse = Ui.button("Pick instance folder", Icons.FOLDER, Ui.ButtonStyle.PRIMARY);
        browse.addActionListener(e -> Choosers.chooseDirectory(this, "Select the modpack instance",
                Choosers.instancesRoot(), this::useFolder));
        JPanel folderRow = Ui.row(Theme.GAP_S, browse, folderLabel, Ui.glue());
        Ui.capHeight(folderRow, 40);
        content.add(folderRow);
        content.add(Ui.strut(Theme.GAP_S));
        content.add(templateLabel);
        content.add(Ui.strut(Theme.GAP_S));
        uploadTemplate.setFont(Theme.small());
        uploadTemplate.setForeground(Theme.TEXT_DIM);
        uploadTemplate.setOpaque(false);
        uploadTemplate.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(uploadTemplate);

        content.add(Ui.strut(Theme.GAP_L));
        JPanel grid = new JPanel(new GridLayout(0, 2, Theme.GAP_M, Theme.GAP_S));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(labelled("Display name", nameField));
        grid.add(labelled("Server id", idField));
        grid.add(labelled("First version", versionField));
        grid.add(labelled("Changelog line", messageField));
        grid.add(labelled("Minecraft", mcField));
        grid.add(labelled("Mod loader", loaderField));
        grid.add(labelled("Loader version", loaderVersionField));
        grid.add(labelled("Author", authorField));
        content.add(grid);
        content.add(Ui.strut(Theme.GAP_M));
        content.add(labelled("Description", descriptionField));

        content.add(Ui.strut(Theme.GAP_L));
        JPanel lists = new JPanel(new GridLayout(1, 2, Theme.GAP_M, 0));
        lists.setOpaque(false);
        lists.setAlignmentX(Component.LEFT_ALIGNMENT);
        lists.add(listCard("Folders to track", foldersBox));
        lists.add(listCard("Root files to track", filesBox));
        lists.setPreferredSize(new Dimension(640, 180));
        lists.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        content.add(lists);

        JButton cancel = Ui.button("Cancel", null, Ui.ButtonStyle.SECONDARY);
        cancel.addActionListener(e -> dispose());
        JButton create = Ui.button("Create on server", Icons.UPLOAD, Ui.ButtonStyle.PRIMARY);
        create.addActionListener(e -> submit());
        JPanel footer = Ui.row(Theme.GAP_S, Ui.glue(), cancel, create);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Ui.strut(Theme.GAP_L));
        content.add(footer);

        JScrollPane scroll = Ui.scroll(content);
        setContentPane(scroll);
        setSize(new Dimension(720, 760));
        setLocationRelativeTo(owner);
    }

    public static AdminPublisher.NewModpack show(Window owner) {
        NewModpackDialog dialog = new NewModpackDialog(owner);
        dialog.setVisible(true);
        return dialog.result;
    }

    private JPanel labelled(String label, JComponent field) {
        JPanel stack = Ui.column();
        stack.add(Ui.dim(label));
        stack.add(Ui.strut(Theme.GAP_XS));
        Ui.capHeight(field, 36);
        stack.add(field);
        return stack;
    }

    private JPanel listCard(String title, JPanel box) {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new BorderLayout());
        card.setBorder(Ui.pad(Theme.GAP_M));
        JLabel heading = Ui.dim(title);
        heading.setBorder(Ui.pad(0, 0, Theme.GAP_S, 0));
        card.add(heading, BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(box);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private void useFolder(Path folder) {
        if (folder == null) {
            return;
        }
        localFolder = folder;
        folderLabel.setText(folder.toString());
        folderLabel.setForeground(Theme.SUCCESS);

        if (nameField.getText().isBlank()) {
            nameField.setText(folder.getFileName().toString());
        }
        if (idField.getText().isBlank()) {
            idField.setText(folder.getFileName().toString()
                    .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
        }

        readInstanceJson(folder);
        scanFolder(folder);
    }

    private void readInstanceJson(Path folder) {
        Path instanceFile = folder.resolve("minecraftinstance.json");
        if (!Files.isRegularFile(instanceFile)) {
            instanceTemplate = null;
            uploadTemplate.setSelected(false);
            uploadTemplate.setEnabled(false);
            templateLabel.setText("No minecraftinstance.json here, so players must make their own profile.");
            templateLabel.setForeground(Theme.WARNING);
            return;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(instanceFile.toFile());
            String game = node.path("gameVersion").asText("");
            JsonNode loader = node.path("baseModLoader");
            if (!game.isBlank()) {
                mcField.setText(game);
            }
            if (!loader.isMissingNode() && !loader.isNull()) {
                String loaderName = loader.path("name").asText("");
                if (loaderName.startsWith("forge-")) {
                    loaderField.setText("Forge");
                    loaderVersionField.setText(loader.path("forgeVersion").asText(""));
                } else if (loaderName.startsWith("neoforge-")) {
                    loaderField.setText("NeoForge");
                    loaderVersionField.setText(loader.path("forgeVersion").asText(""));
                } else if (loaderName.startsWith("fabric-")) {
                    loaderField.setText("Fabric");
                    loaderVersionField.setText(loader.path("forgeVersion").asText(""));
                }
            }
            instanceTemplate = instanceFile;
            uploadTemplate.setEnabled(true);
            uploadTemplate.setSelected(true);
            templateLabel.setText("Instance template found: " + mcField.getText()
                    + "   " + loaderField.getText() + " " + loaderVersionField.getText());
            templateLabel.setForeground(Theme.SUCCESS);
        } catch (Exception e) {
            instanceTemplate = null;
            uploadTemplate.setSelected(false);
            uploadTemplate.setEnabled(false);
            templateLabel.setText("Could not read minecraftinstance.json: " + e.getMessage());
            templateLabel.setForeground(Theme.DANGER);
        }
    }

    private void scanFolder(Path folder) {
        foldersBox.removeAll();
        filesBox.removeAll();
        folderBoxes.clear();
        fileBoxes.clear();

        IgnoreRules ignore = IgnoreRules.from(ConfigManager.getInstance().getConfig().getIgnoredFiles());

        File[] directories = folder.toFile().listFiles(File::isDirectory);
        if (directories != null) {
            Arrays.sort(directories, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File directory : directories) {
                String name = directory.getName();
                if (name.startsWith(".") || SKIPPED.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                boolean common = name.equalsIgnoreCase("mods") || name.equalsIgnoreCase("config");
                foldersBox.add(checkBox(name, common, folderBoxes));
            }
        }

        File[] files = folder.toFile().listFiles(File::isFile);
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                String name = file.getName();
                if (ignore.ignores(name) || name.equals("minecraftinstance.json")) {
                    continue;
                }
                filesBox.add(checkBox(name, false, fileBoxes));
            }
        }

        foldersBox.add(Ui.glue());
        filesBox.add(Ui.glue());
        foldersBox.revalidate();
        foldersBox.repaint();
        filesBox.revalidate();
        filesBox.repaint();
    }

    private JCheckBox checkBox(String name, boolean selected, Map<String, JCheckBox> registry) {
        JCheckBox box = new JCheckBox(name, selected);
        box.setFont(Theme.small());
        box.setForeground(Theme.TEXT_DIM);
        box.setOpaque(false);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        registry.put(name, box);
        return box;
    }

    private void submit() {
        if (localFolder == null) {
            Dialogs.warn(this, "No folder", "Pick the local instance folder first.");
            return;
        }
        String name = nameField.getText().trim();
        String id = idField.getText().trim();
        String version = versionField.getText().trim();
        String message = messageField.getText().trim();

        if (name.isBlank() || id.isBlank()) {
            Dialogs.warn(this, "Missing details", "A display name and server id are both needed.");
            return;
        }
        if (!id.matches("[A-Za-z0-9._-]+")) {
            Dialogs.warn(this, "Bad server id",
                    "Use letters, numbers, dots, dashes or underscores only.");
            return;
        }
        if (!version.matches("\\d+(\\.\\d+)*")) {
            Dialogs.warn(this, "Bad version", "Use numbers separated by dots, such as 1.0.0.");
            return;
        }
        if (message.isBlank()) {
            Dialogs.warn(this, "Missing changelog", "Add a short line describing this release.");
            return;
        }

        List<String> folders = selected(folderBoxes);
        List<String> files = selected(fileBoxes);
        if (folders.isEmpty() && files.isEmpty()) {
            Dialogs.warn(this, "Nothing tracked", "Tick at least one folder or file.");
            return;
        }
        if (AdminPublisher.rootExists(id)) {
            if (!Dialogs.confirm(this, "Server id in use",
                    "Something already exists at " + id + " on the server. Writing here may "
                            + "overwrite it.", "Use it anyway")) {
                return;
            }
        }

        result = new AdminPublisher.NewModpack(name, id, version, message,
                mcField.getText().trim(), loaderField.getText().trim(),
                loaderVersionField.getText().trim(), authorField.getText().trim(),
                descriptionField.getText().trim(), localFolder, folders, files,
                uploadTemplate.isSelected() ? instanceTemplate : null);
        dispose();
    }

    private static List<String> selected(Map<String, JCheckBox> registry) {
        List<String> chosen = new ArrayList<>();
        registry.forEach((name, box) -> {
            if (box.isSelected()) {
                chosen.add(name);
            }
        });
        return chosen;
    }
}
