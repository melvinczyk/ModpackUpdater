package com.nicholasburczyk.packupdater.ui.panel;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.core.AdminPublisher;
import com.nicholasburczyk.packupdater.core.IgnoreRules;
import com.nicholasburczyk.packupdater.core.ModpackSync;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.MainFrame;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;
import com.nicholasburczyk.packupdater.ui.dialog.Dialogs;
import com.nicholasburczyk.packupdater.ui.dialog.NewModpackDialog;
import com.nicholasburczyk.packupdater.ui.dialog.TaskDialog;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AdminPanel extends JPanel {

    private static final Set<String> SKIPPED_FOLDERS = Set.of(
            "logs", "crash-reports", "screenshots", "saves", "backups", "local",
            "shaderpacks", "resourcepacks", "profileImage"
    );

    private final MainFrame frame;
    private final JComboBox<ModpackInfo> localSelector = new JComboBox<>();
    private final JComboBox<ModpackInfo> serverSelector = new JComboBox<>();
    private final JLabel versionLabel = Ui.dim("No modpack selected");
    private final ChangeTableModel tableModel = new ChangeTableModel();
    private final JTable table = new JTable(tableModel);
    private final JCheckBox showAdded = filterBox("Added", Theme.SUCCESS);
    private final JCheckBox showModified = filterBox("Changed", Theme.WARNING);
    private final JCheckBox showDeleted = filterBox("Removed", Theme.DANGER);
    private final JLabel countLabel = Ui.faint("Nothing compared yet");
    private final JPanel foldersBox = Ui.column();
    private final JPanel filesBox = Ui.column();
    private final JTextField versionField = Ui.field("1.4.1");
    private final JLabel versionHint = Ui.faint(" ");
    private final JTextField messageField = Ui.field("What changed in this version");
    private final JButton publishButton;

    private final Set<String> trackedFolders = new LinkedHashSet<>();
    private final Set<String> trackedFiles = new LinkedHashSet<>();
    private boolean wasShowing;

    public AdminPanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(Ui.pad(Theme.GAP_L));

        publishButton = Ui.button("Publish", Icons.UPLOAD, Ui.ButtonStyle.PRIMARY);
        publishButton.setEnabled(false);
        publishButton.addActionListener(e -> publish());

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.font(Font.BOLD, 12));
        tabs.addTab("Changes", buildChangesTab());
        tabs.addTab("Tracked content", buildTrackedTab());
        add(tabs, BorderLayout.CENTER);

        localSelector.addActionListener(e -> onSelectionChanged());
        serverSelector.addActionListener(e -> onSelectionChanged());

        addHierarchyListener(e -> {
            boolean showing = isShowing();
            if (showing != wasShowing) {
                wasShowing = showing;
                if (showing) {
                    reloadSelectors();
                }
            }
        });
    }

    private JPanel buildHeader() {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new BorderLayout());
        card.setBorder(Ui.pad(Theme.GAP_M, Theme.GAP_L, Theme.GAP_M, Theme.GAP_L));

        JPanel row = Ui.row();
        localSelector.setRenderer(packRenderer("Pick a local modpack"));
        serverSelector.setRenderer(packRenderer("Pick a server modpack"));
        localSelector.setPreferredSize(new Dimension(230, 34));
        serverSelector.setPreferredSize(new Dimension(230, 34));
        Ui.capHeight(localSelector, 34);
        Ui.capHeight(serverSelector, 34);

        JButton compare = Ui.button("Compare", Icons.SEARCH, Ui.ButtonStyle.PRIMARY);
        compare.addActionListener(e -> compare());

        JButton newPack = Ui.button("New modpack", Icons.PLUS, Ui.ButtonStyle.SECONDARY);
        newPack.addActionListener(e -> createModpack());

        JButton template = Ui.button("Set template", Icons.FOLDER_NEW, Ui.ButtonStyle.SECONDARY);
        template.setToolTipText("Upload this local instance as the profile template players get");
        template.addActionListener(e -> publishTemplate());

        row.add(Ui.dim("Local"));
        row.add(Ui.strut(Theme.GAP_S));
        row.add(localSelector);
        row.add(Ui.strut(Theme.GAP_L));
        row.add(Ui.dim("Server"));
        row.add(Ui.strut(Theme.GAP_S));
        row.add(serverSelector);
        row.add(Ui.strut(Theme.GAP_L));
        row.add(compare);
        row.add(Ui.strut(Theme.GAP_S));
        row.add(versionLabel);
        row.add(Ui.glue());
        row.add(template);
        row.add(Ui.strut(Theme.GAP_S));
        row.add(newPack);

        JPanel warning = Ui.row(Theme.GAP_S,
                new JLabel(Icons.of(Icons.WARNING, 13, Theme.WARNING)),
                Ui.faint("Both must be the same modpack."));

        JPanel stack = Ui.column();
        stack.add(row);
        stack.add(Ui.strut(Theme.GAP_S));
        stack.add(warning);
        card.add(stack, BorderLayout.CENTER);

        JPanel wrapper = Ui.column();
        JLabel heading = Ui.title("Admin panel");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(heading);
        wrapper.add(Ui.strut(Theme.GAP_M));
        wrapper.add(card);
        wrapper.add(Ui.strut(Theme.GAP_M));
        return wrapper;
    }

    private JPanel buildChangesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(Ui.pad(Theme.GAP_M, 0, 0, 0));

        JButton selectAll = Ui.button("Select all", null, Ui.ButtonStyle.SECONDARY);
        selectAll.addActionListener(e -> tableModel.setAllSelected(true));
        JButton selectNone = Ui.button("Select none", null, Ui.ButtonStyle.SECONDARY);
        selectNone.addActionListener(e -> tableModel.setAllSelected(false));

        JPanel toolbar = Ui.row(Theme.GAP_S,
                showAdded, showModified, showDeleted,
                Ui.glue(), countLabel, Ui.strut(Theme.GAP_M), selectAll, selectNone);
        Ui.capHeight(toolbar, 36);

        configureTable();
        JScrollPane scroll = Ui.scroll(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.SURFACE);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buildPublishBar(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPublishBar() {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new BorderLayout());
        card.setBorder(Ui.pad(Theme.GAP_M, Theme.GAP_L, Theme.GAP_M, Theme.GAP_L));

        Ui.capHeight(versionField, 34);
        Ui.capHeight(messageField, 34);
        versionField.setPreferredSize(new Dimension(110, 34));
        versionField.setMaximumSize(new Dimension(110, 34));

        JPanel row = Ui.row(Theme.GAP_S,
                Ui.dim("New version"), versionField,
                Ui.strut(Theme.GAP_S), versionHint,
                Ui.strut(Theme.GAP_M), messageField,
                Ui.strut(Theme.GAP_M), publishButton);

        versionField.getDocument().addDocumentListener(new VersionWatcher(this::updatePublishState));
        messageField.getDocument().addDocumentListener(new VersionWatcher(this::updatePublishState));

        card.add(row, BorderLayout.CENTER);
        JPanel wrapper = Ui.column();
        wrapper.add(Ui.strut(Theme.GAP_M));
        wrapper.add(card);
        return wrapper;
    }

    private JPanel buildTrackedTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(Ui.pad(Theme.GAP_M, 0, 0, 0));

        JLabel intro = Ui.wrapped("Checked items are synced to everyone on publish.", Theme.TEXT_DIM);

        JButton rescan = Ui.button("Rescan", Icons.REFRESH, Ui.ButtonStyle.SECONDARY);
        rescan.addActionListener(e -> loadTrackedContent());

        JPanel head = Ui.column();
        head.add(intro);
        head.add(Ui.strut(Theme.GAP_S));
        JPanel rescanRow = Ui.row(0, rescan, Ui.glue());
        Ui.capHeight(rescanRow, 36);
        head.add(rescanRow);
        head.add(Ui.strut(Theme.GAP_M));

        JPanel columns = new JPanel(new GridLayout(1, 2, Theme.GAP_L, 0));
        columns.setOpaque(false);
        columns.add(listCard("Folders", foldersBox));
        columns.add(listCard("Files in the instance root", filesBox));

        panel.add(head, BorderLayout.NORTH);
        panel.add(columns, BorderLayout.CENTER);
        return panel;
    }

    private JPanel listCard(String title, JPanel content) {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new BorderLayout());
        card.setBorder(Ui.pad(Theme.GAP_M));
        JLabel heading = Ui.label(title, Theme.font(Font.BOLD, 13), Theme.TEXT);
        heading.setBorder(Ui.pad(0, Theme.GAP_XS, Theme.GAP_S, 0));
        JScrollPane scroll = Ui.scroll(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(heading, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private void configureTable() {
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(Theme.SURFACE);
        table.setForeground(Theme.TEXT_DIM);
        table.setFont(Theme.small());
        table.setSelectionBackground(Theme.SURFACE_HOVER);
        table.setSelectionForeground(Theme.TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(Theme.font(Font.BOLD, 11));
        table.getTableHeader().setReorderingAllowed(false);

        TableColumn check = table.getColumnModel().getColumn(0);
        check.setMaxWidth(34);
        check.setMinWidth(34);

        TableColumn type = table.getColumnModel().getColumn(1);
        type.setMaxWidth(92);
        type.setMinWidth(92);
        type.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                AdminPublisher.ChangeType changeType = tableModel.typeAt(row);
                setForeground(switch (changeType) {
                    case ADDED -> Theme.SUCCESS;
                    case MODIFIED -> Theme.WARNING;
                    case DELETED -> Theme.DANGER;
                });
                setFont(Theme.font(Font.BOLD, 11));
                setBorder(Ui.pad(0, Theme.GAP_S, 0, 0));
                return this;
            }
        });

        TableColumn size = table.getColumnModel().getColumn(3);
        size.setMaxWidth(96);
        size.setMinWidth(96);

        tableModel.addTableModelListener(e -> updateCounts());
        showAdded.addActionListener(e -> applyFilter());
        showModified.addActionListener(e -> applyFilter());
        showDeleted.addActionListener(e -> applyFilter());
    }

    private static JCheckBox filterBox(String label, Color color) {
        JCheckBox box = new JCheckBox(label, true);
        box.setFont(Theme.small());
        box.setForeground(color);
        box.setOpaque(false);
        return box;
    }

    private static DefaultListCellRenderer packRenderer(String emptyText) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof ModpackInfo pack) {
                    String name = pack.getDisplayName();
                    setText((name == null || name.isBlank() ? pack.getRoot() : name)
                            + "   v" + pack.getVersion());
                } else {
                    setText(emptyText);
                }
                return this;
            }
        };
    }

    public void reloadSelectors() {
        ModpackInfo previousLocal = selectedLocal();
        ModpackInfo previousServer = selectedServer();

        List<ModpackInfo> locals = new ArrayList<>(ModpackRegistry.getLocalModpacks().values());
        List<ModpackInfo> servers = new ArrayList<>(ModpackRegistry.getServerModpacks().values());
        locals.sort(Comparator.comparing(AdminPanel::nameOf, String.CASE_INSENSITIVE_ORDER));
        servers.sort(Comparator.comparing(AdminPanel::nameOf, String.CASE_INSENSITIVE_ORDER));

        localSelector.setModel(new DefaultComboBoxModel<>(locals.toArray(new ModpackInfo[0])));
        serverSelector.setModel(new DefaultComboBoxModel<>(servers.toArray(new ModpackInfo[0])));

        restore(localSelector, previousLocal);
        restore(serverSelector, previousServer);
        pairByModpackId();
        onSelectionChanged();
    }

    private void pairByModpackId() {
        ModpackInfo local = selectedLocal();
        if (local == null || selectedServer() != null) {
            return;
        }
        for (int i = 0; i < serverSelector.getItemCount(); i++) {
            ModpackInfo candidate = serverSelector.getItemAt(i);
            if (candidate.getModpackId() != null
                    && candidate.getModpackId().equals(local.getModpackId())) {
                serverSelector.setSelectedIndex(i);
                return;
            }
        }
    }

    private static void restore(JComboBox<ModpackInfo> selector, ModpackInfo previous) {
        if (previous == null) {
            return;
        }
        for (int i = 0; i < selector.getItemCount(); i++) {
            ModpackInfo candidate = selector.getItemAt(i);
            if (candidate.getRoot() != null && candidate.getRoot().equals(previous.getRoot())) {
                selector.setSelectedIndex(i);
                return;
            }
        }
    }

    private void onSelectionChanged() {
        ModpackInfo local = selectedLocal();
        ModpackInfo server = selectedServer();

        if (server == null) {
            versionLabel.setText("No server modpack selected");
        } else {
            versionLabel.setText("Server is on v" + server.getVersion());
            versionField.setText(AdminPublisher.suggestNextVersion(server.getVersion()));
        }

        if (local != null && server != null
                && local.getModpackId() != null && server.getModpackId() != null
                && !local.getModpackId().equals(server.getModpackId())) {
            versionLabel.setText("Different modpacks selected");
            versionLabel.setForeground(Theme.DANGER);
        } else {
            versionLabel.setForeground(Theme.TEXT_DIM);
        }

        loadTrackedContent();
    }

    private void loadTrackedContent() {
        foldersBox.removeAll();
        filesBox.removeAll();
        trackedFolders.clear();
        trackedFiles.clear();

        ModpackInfo local = selectedLocal();
        ModpackInfo server = selectedServer();
        if (local == null) {
            foldersBox.add(Ui.faint("Pick a local modpack first."));
            filesBox.add(Ui.faint("Pick a local modpack first."));
            refreshTrackedBoxes();
            return;
        }

        Path root = ModpackSync.localRoot(local);
        IgnoreRules ignore = IgnoreRules.from(ConfigManager.getInstance().getConfig().getIgnoredFiles());

        Set<String> serverFolders = new LinkedHashSet<>(
                server != null && server.getFolders() != null ? server.getFolders() : List.of());
        Set<String> serverFiles = new LinkedHashSet<>(
                server != null && server.getFiles() != null ? server.getFiles() : List.of());

        File[] directories = root.toFile().listFiles(File::isDirectory);
        if (directories != null) {
            Arrays.sort(directories, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File directory : directories) {
                String name = directory.getName();
                if (name.startsWith(".") || SKIPPED_FOLDERS.contains(name.toLowerCase())) {
                    continue;
                }
                addCheckBox(foldersBox, trackedFolders, name, serverFolders.contains(name), false);
            }
        }
        for (String missing : serverFolders) {
            if (!Files.isDirectory(root.resolve(missing))) {
                addCheckBox(foldersBox, trackedFolders, missing, true, true);
            }
        }

        File[] rootFiles = root.toFile().listFiles(File::isFile);
        if (rootFiles != null) {
            Arrays.sort(rootFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : rootFiles) {
                String name = file.getName();
                if (ignore.ignores(name)) {
                    continue;
                }
                addCheckBox(filesBox, trackedFiles, name, serverFiles.contains(name), false);
            }
        }
        for (String missing : serverFiles) {
            if (!Files.isRegularFile(root.resolve(missing))) {
                addCheckBox(filesBox, trackedFiles, missing, true, true);
            }
        }

        if (foldersBox.getComponentCount() == 0) {
            foldersBox.add(Ui.faint("No folders found in this instance."));
        }
        if (filesBox.getComponentCount() == 0) {
            filesBox.add(Ui.faint("No loose files in the instance root."));
        }
        refreshTrackedBoxes();
    }

    private void addCheckBox(JPanel container, Set<String> target, String name,
                             boolean selected, boolean missingLocally) {
        JCheckBox box = new JCheckBox(missingLocally ? name + "  (missing locally)" : name, selected);
        box.setFont(Theme.small());
        box.setForeground(missingLocally ? Theme.DANGER : Theme.TEXT_DIM);
        box.setOpaque(false);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setBorder(Ui.pad(2, 0, 2, 0));
        if (missingLocally) {
            box.setEnabled(false);
            box.setToolTipText("Tracked on the server, missing locally");
        }
        if (selected && !missingLocally) {
            target.add(name);
        }
        box.addActionListener(e -> {
            if (box.isSelected()) {
                target.add(name);
            } else {
                target.remove(name);
            }
        });
        container.add(box);
    }

    private void refreshTrackedBoxes() {
        foldersBox.add(Ui.glue());
        filesBox.add(Ui.glue());
        foldersBox.revalidate();
        foldersBox.repaint();
        filesBox.revalidate();
        filesBox.repaint();
    }

    private void compare() {
        ModpackInfo local = selectedLocal();
        ModpackInfo server = selectedServer();
        if (local == null || server == null) {
            Dialogs.warn(this, "Pick both modpacks", "Select a local and a server modpack first.");
            return;
        }
        if (trackedFolders.isEmpty() && trackedFiles.isEmpty()) {
            Dialogs.warn(this, "Nothing selected",
                    "Check something on the Tracked content tab.");
            return;
        }

        List<String> folders = new ArrayList<>(trackedFolders);
        List<String> files = new ArrayList<>(trackedFiles);

        TaskDialog.run(frame, "Comparing " + nameOf(local),
                progress -> AdminPublisher.compare(local, server, folders, files, progress),
                changes -> {
                    tableModel.setChanges(changes);
                    applyFilter();
                },
                error -> Dialogs.error(this, "Comparison failed", Dialogs.readable(error)));
    }

    private void applyFilter() {
        Set<AdminPublisher.ChangeType> visible = new LinkedHashSet<>();
        if (showAdded.isSelected()) {
            visible.add(AdminPublisher.ChangeType.ADDED);
        }
        if (showModified.isSelected()) {
            visible.add(AdminPublisher.ChangeType.MODIFIED);
        }
        if (showDeleted.isSelected()) {
            visible.add(AdminPublisher.ChangeType.DELETED);
        }
        tableModel.setVisibleTypes(visible);
        updateCounts();
    }

    private void updateCounts() {
        int staged = tableModel.selectedChanges().size();
        countLabel.setText(tableModel.getRowCount() + " shown, " + staged + " selected");
        updatePublishState();
    }

    private void updatePublishState() {
        ModpackInfo server = selectedServer();
        String current = server == null ? "0" : server.getVersion();
        String candidate = versionField.getText() == null ? "" : versionField.getText().trim();
        boolean staged = !tableModel.selectedChanges().isEmpty();
        boolean described = messageField.getText() != null && !messageField.getText().isBlank();

        boolean wellFormed = candidate.matches("\\d+(\\.\\d+)*");
        boolean higher = wellFormed && AdminPublisher.isVersionAcceptable(candidate, current);

        if (candidate.isEmpty()) {
            versionHint.setText("needs a version");
            versionHint.setForeground(Theme.TEXT_FAINT);
        } else if (!wellFormed) {
            versionHint.setText("numbers and dots only");
            versionHint.setForeground(Theme.DANGER);
        } else if (!higher) {
            versionHint.setText("must beat " + current);
            versionHint.setForeground(Theme.DANGER);
        } else {
            versionHint.setText("ok");
            versionHint.setForeground(Theme.SUCCESS);
        }

        publishButton.setEnabled(staged && higher && described);
    }

    private void createModpack() {
        AdminPublisher.NewModpack request = NewModpackDialog.show(
                javax.swing.SwingUtilities.getWindowAncestor(this));
        if (request == null) {
            return;
        }
        TaskDialog.run(frame, "Creating " + request.displayName(), false, progress -> {
            AdminPublisher.createModpack(request, progress);
            return null;
        }, ignored -> {
            Dialogs.info(this, "Created",
                    request.displayName() + " is on the server at version " + request.version() + ".");
            if (frame != null) {
                frame.refresh();
            }
            reloadSelectors();
        }, error -> Dialogs.error(this, "Could not create the modpack", Dialogs.readable(error)));
    }

    private void publishTemplate() {
        ModpackInfo local = selectedLocal();
        ModpackInfo server = selectedServer();
        if (local == null || server == null) {
            Dialogs.warn(this, "Pick both modpacks", "Select a local and a server modpack first.");
            return;
        }
        java.nio.file.Path instanceFile = ModpackSync.localRoot(local).resolve("minecraftinstance.json");
        if (!java.nio.file.Files.isRegularFile(instanceFile)) {
            Dialogs.warn(this, "No instance file",
                    "This folder has no minecraftinstance.json, so there is nothing to publish.");
            return;
        }
        if (!Dialogs.confirm(this, "Publish instance template",
                "Players adding " + nameOf(server) + " will get a CurseForge profile built from "
                        + "this instance.", "Publish")) {
            return;
        }
        TaskDialog.run(frame, "Uploading template", false, progress -> {
            AdminPublisher.uploadInstanceTemplate(server.getRoot(), instanceFile);
            return null;
        }, ignored -> Dialogs.info(this, "Published", "Players can now have this profile made for them."),
                error -> Dialogs.error(this, "Upload failed", Dialogs.readable(error)));
    }

    private record VersionWatcher(Runnable action) implements javax.swing.event.DocumentListener {

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }
    }

    private void publish() {
        ModpackInfo local = selectedLocal();
        ModpackInfo server = selectedServer();
        if (local == null || server == null) {
            return;
        }

        List<AdminPublisher.FileChange> staged = tableModel.selectedChanges();
        if (staged.isEmpty()) {
            Dialogs.warn(this, "Nothing selected", "Tick the changes you want to publish.");
            return;
        }

        String version = versionField.getText() == null ? "" : versionField.getText().trim();
        String message = messageField.getText() == null ? "" : messageField.getText().trim();

        if (!AdminPublisher.isVersionAcceptable(version, server.getVersion())) {
            Dialogs.warn(this, "Version problem",
                    "The new version must be higher than the current server version ("
                            + server.getVersion() + ").");
            return;
        }
        if (message.isEmpty()) {
            Dialogs.warn(this, "Changelog needed",
                    "Add a short line describing this version.");
            return;
        }

        long uploads = staged.stream()
                .filter(change -> change.type() != AdminPublisher.ChangeType.DELETED).count();
        long deletes = staged.size() - uploads;

        boolean go = Dialogs.confirm(this, "Publish " + version,
                nameOf(server) + "   " + server.getVersion() + " to " + version + "\n\n"
                        + uploads + " uploaded, " + deletes + " deleted\n"
                        + trackedFolders.size() + " folders, " + trackedFiles.size() + " files tracked\n\n"
                        + "This goes live for everyone.",
                "Publish");
        if (!go) {
            return;
        }

        List<String> folders = new ArrayList<>(trackedFolders);
        List<String> files = new ArrayList<>(trackedFiles);

        TaskDialog.run(frame, "Publishing " + version, false, progress -> {
            AdminPublisher.publish(local, server, staged, folders, files, version, message, progress);
            return null;
        }, ignored -> {
            Dialogs.info(this, "Published", "Version " + version + " is live.");
            messageField.setText("");
            tableModel.setChanges(List.of());
            updateCounts();
            frame.refresh();
        }, error -> Dialogs.error(this, "Publish failed",
                "Some files may already be uploaded. Compare again.\n\n" + Dialogs.readable(error)));
    }

    private ModpackInfo selectedLocal() {
        return (ModpackInfo) localSelector.getSelectedItem();
    }

    private ModpackInfo selectedServer() {
        return (ModpackInfo) serverSelector.getSelectedItem();
    }

    private static String nameOf(ModpackInfo pack) {
        String name = pack.getDisplayName();
        if (name == null || name.isBlank()) {
            name = pack.getModpackId();
        }
        return name == null || name.isBlank() ? String.valueOf(pack.getRoot()) : name;
    }

    private static final class ChangeTableModel extends AbstractTableModel {

        private final String[] columns = {"", "Change", "File", "Size"};
        private final List<AdminPublisher.FileChange> all = new ArrayList<>();
        private final List<AdminPublisher.FileChange> visible = new ArrayList<>();
        private final Set<String> selected = new LinkedHashSet<>();
        private Set<AdminPublisher.ChangeType> visibleTypes =
                new LinkedHashSet<>(Arrays.asList(AdminPublisher.ChangeType.values()));

        void setChanges(List<AdminPublisher.FileChange> changes) {
            all.clear();
            all.addAll(changes);
            selected.clear();
            for (AdminPublisher.FileChange change : changes) {
                selected.add(change.path());
            }
            rebuild();
        }

        void setVisibleTypes(Set<AdminPublisher.ChangeType> types) {
            visibleTypes = new LinkedHashSet<>(types);
            rebuild();
        }

        void setAllSelected(boolean value) {
            for (AdminPublisher.FileChange change : visible) {
                if (value) {
                    selected.add(change.path());
                } else {
                    selected.remove(change.path());
                }
            }
            fireTableDataChanged();
        }

        List<AdminPublisher.FileChange> selectedChanges() {
            List<AdminPublisher.FileChange> result = new ArrayList<>();
            for (AdminPublisher.FileChange change : all) {
                if (selected.contains(change.path())) {
                    result.add(change);
                }
            }
            return result;
        }

        AdminPublisher.ChangeType typeAt(int row) {
            return visible.get(row).type();
        }

        private void rebuild() {
            visible.clear();
            for (AdminPublisher.FileChange change : all) {
                if (visibleTypes.contains(change.type())) {
                    visible.add(change);
                }
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return visible.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int column) {
            AdminPublisher.FileChange change = visible.get(row);
            return switch (column) {
                case 0 -> selected.contains(change.path());
                case 1 -> change.type().label();
                case 2 -> change.path();
                default -> change.displaySize() > 0 ? Ui.formatBytes(change.displaySize()) : "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 0) {
                return;
            }
            AdminPublisher.FileChange change = visible.get(row);
            if (Boolean.TRUE.equals(value)) {
                selected.add(change.path());
            } else {
                selected.remove(change.path());
            }
            fireTableRowsUpdated(row, row);
        }
    }
}
