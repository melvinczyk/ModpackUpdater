package com.nicholasburczyk.packupdater.ui.panel;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.core.CurseForgeLocator;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ConnectionStatus;
import com.nicholasburczyk.packupdater.ui.Choosers;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.MainFrame;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;
import com.nicholasburczyk.packupdater.ui.dialog.Dialogs;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SettingsPanel extends JPanel {

    private final MainFrame frame;
    private final JTextField instancesField = Ui.field("curseforge/minecraft/Instances");
    private final JTextField endpointField = Ui.field("https://s3.us-east-005.backblazeb2.com");
    private final JTextField keyIdField = Ui.field("From your admin");
    private final JPasswordField appKeyField = Ui.passwordField("From your admin");
    private final JTextField bucketField = Ui.field("Bucket name");
    private final JLabel pathStatus = Ui.faint(" ");
    private final JLabel saveStatus = Ui.faint(" ");

    public SettingsPanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);

        JPanel content = Ui.column();
        content.setBorder(Ui.pad(Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL));
        content.add(Ui.label("Settings", Theme.display(), Theme.TEXT));
        content.add(Ui.strut(Theme.GAP_XL));
        content.add(buildInstances());
        content.add(Ui.strut(Theme.GAP_L));
        content.add(buildServer());
        content.add(Ui.strut(Theme.GAP_L));
        content.add(buildFooter());
        content.add(Ui.glue());

        add(Ui.scroll(content), BorderLayout.CENTER);
        reload();
    }

    private JPanel buildInstances() {
        JPanel card = section("Instances folder",
                "The folder holding every CurseForge profile.");

        JButton browse = Ui.button("Browse", Icons.FOLDER, Ui.ButtonStyle.SECONDARY);
        browse.addActionListener(e -> Choosers.chooseDirectory(frame,
                "Select your CurseForge Instances folder",
                currentPath().orElse(null),
                picked -> {
                    if (picked != null) {
                        instancesField.setText(picked.toString());
                        validatePath();
                    }
                }));

        JButton detect = Ui.button("Detect", Icons.SEARCH, Ui.ButtonStyle.SECONDARY);
        detect.addActionListener(e -> {
            Optional<Path> found = CurseForgeLocator.detect();
            if (found.isPresent()) {
                instancesField.setText(found.get().toString());
                validatePath();
            } else {
                pathStatus.setText("Not found automatically, use Browse.");
                pathStatus.setForeground(Theme.WARNING);
            }
        });

        JPanel row = Ui.row(Theme.GAP_S, instancesField, browse, detect);
        Ui.capHeight(row, 38);
        instancesField.getDocument().addDocumentListener(new Watcher(this::validatePath));

        card.add(row);
        card.add(Ui.strut(Theme.GAP_S));
        card.add(pathStatus);
        return card;
    }

    private JPanel buildServer() {
        JPanel card = section("Server", "Ask your admin for the keys.");

        JCheckBox reveal = new JCheckBox("Show key");
        reveal.setFont(Theme.small());
        reveal.setForeground(Theme.TEXT_DIM);
        reveal.setOpaque(false);
        reveal.setAlignmentX(Component.LEFT_ALIGNMENT);
        char echo = appKeyField.getEchoChar();
        reveal.addActionListener(e -> appKeyField.setEchoChar(reveal.isSelected() ? (char) 0 : echo));

        card.add(labelled("Server URL", endpointField));
        card.add(Ui.strut(Theme.GAP_M));
        card.add(labelled("Bucket", bucketField));
        card.add(Ui.strut(Theme.GAP_M));
        card.add(labelled("Key ID", keyIdField));
        card.add(Ui.strut(Theme.GAP_M));
        card.add(labelled("Application key", appKeyField));
        card.add(Ui.strut(Theme.GAP_S));
        card.add(reveal);
        return card;
    }

    private JPanel buildFooter() {
        JButton save = Ui.button("Save", Icons.CHECK, Ui.ButtonStyle.PRIMARY);
        save.addActionListener(e -> {
            save();
            frame.refresh();
        });

        JButton test = Ui.button("Test connection", Icons.CLOUD, Ui.ButtonStyle.SECONDARY);
        test.addActionListener(e -> testConnection());

        JPanel row = Ui.row(Theme.GAP_S, save, test, Ui.strut(Theme.GAP_S), saveStatus, Ui.glue());
        Ui.capHeight(row, 40);

        JPanel wrapper = Ui.column();
        wrapper.add(row);
        wrapper.add(Ui.strut(Theme.GAP_S));
        wrapper.add(Ui.faint(ConfigManager.getInstance().getConfigFilePath()));
        return wrapper;
    }

    private JPanel section(String title, String description) {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new javax.swing.BoxLayout(card, javax.swing.BoxLayout.Y_AXIS));
        card.setBorder(Ui.pad(Theme.GAP_L));
        card.setMaximumSize(new Dimension(760, Integer.MAX_VALUE));
        card.add(Ui.heading(title));
        card.add(Ui.strut(Theme.GAP_XS));
        card.add(Ui.wrapped(description, Theme.TEXT_DIM));
        card.add(Ui.strut(Theme.GAP_L));
        return card;
    }

    private JPanel labelled(String labelText, JComponent field) {
        JPanel stack = Ui.column();
        stack.add(Ui.dim(labelText));
        stack.add(Ui.strut(Theme.GAP_XS));
        Ui.capHeight(field, 38);
        stack.add(field);
        return stack;
    }

    public void reload() {
        Config config = ConfigManager.getInstance().getConfig();
        instancesField.setText(orEmpty(config.getCurseforge_path()));
        endpointField.setText(orEmpty(config.getEndpoint()));
        keyIdField.setText(orEmpty(config.getKeyID()));
        appKeyField.setText(orEmpty(config.getAppKey()));
        bucketField.setText(orEmpty(config.getBucketName()));
        saveStatus.setText(" ");
        validatePath();
    }

    private void validatePath() {
        String raw = instancesField.getText();
        if (raw == null || raw.isBlank()) {
            pathStatus.setText("Required.");
            pathStatus.setForeground(Theme.WARNING);
            return;
        }
        Path path = Path.of(raw);
        if (!Files.isDirectory(path)) {
            pathStatus.setText("That folder does not exist.");
            pathStatus.setForeground(Theme.DANGER);
            return;
        }
        java.io.File[] folders = path.toFile().listFiles(java.io.File::isDirectory);
        int count = folders == null ? 0 : folders.length;
        if (!CurseForgeLocator.looksLikeInstancesFolder(raw)) {
            pathStatus.setText("Unusual path, but it will be used.");
            pathStatus.setForeground(Theme.WARNING);
            return;
        }
        pathStatus.setText(count == 1 ? "1 instance found." : count + " instances found.");
        pathStatus.setForeground(Theme.SUCCESS);
    }

    private Optional<Path> currentPath() {
        String raw = instancesField.getText();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(raw);
        return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
    }

    private void save() {
        Config config = ConfigManager.getInstance().getConfig();
        config.setCurseforge_path(instancesField.getText().trim());
        config.setEndpoint(endpointField.getText().trim());
        config.setKeyID(keyIdField.getText().trim());
        config.setAppKey(new String(appKeyField.getPassword()).trim());
        config.setBucketName(bucketField.getText().trim());
        ConfigManager.getInstance().saveConfig();
        B2ClientProvider.reconnectToClient();
        saveStatus.setText("Saved");
        saveStatus.setForeground(Theme.SUCCESS);
    }

    private void testConnection() {
        save();
        saveStatus.setText("Testing");
        saveStatus.setForeground(Theme.TEXT_DIM);

        new SwingWorker<ConnectionStatus, Void>() {
            @Override
            protected ConnectionStatus doInBackground() {
                return B2ClientProvider.checkConnection();
            }

            @Override
            protected void done() {
                ConnectionStatus status;
                try {
                    status = get();
                } catch (Exception e) {
                    status = new ConnectionStatus(false, Dialogs.readable(e));
                }
                saveStatus.setText(status.isConnected ? "Connected" : status.message);
                saveStatus.setForeground(status.isConnected ? Theme.SUCCESS : Theme.DANGER);
                frame.checkConnection();
            }
        }.execute();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Watcher(Runnable action) implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }
}
