package com.nicholasburczyk.packupdater.ui;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.core.InstanceFactory;
import com.nicholasburczyk.packupdater.core.LocalModpackScanner;
import com.nicholasburczyk.packupdater.core.ModpackSync;
import com.nicholasburczyk.packupdater.core.PackOrder;
import com.nicholasburczyk.packupdater.core.SoftwareUpdater;
import com.nicholasburczyk.packupdater.core.SyncReport;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ConnectionStatus;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import com.nicholasburczyk.packupdater.ui.component.PackArtwork;
import com.nicholasburczyk.packupdater.ui.component.Sidebar;
import com.nicholasburczyk.packupdater.ui.dialog.AdminLoginDialog;
import com.nicholasburczyk.packupdater.ui.dialog.Dialogs;
import com.nicholasburczyk.packupdater.ui.dialog.SyncReportDialog;
import com.nicholasburczyk.packupdater.ui.dialog.TaskDialog;
import com.nicholasburczyk.packupdater.ui.panel.AdminPanel;
import com.nicholasburczyk.packupdater.ui.panel.BrowsePanel;
import com.nicholasburczyk.packupdater.ui.panel.HelpPanel;
import com.nicholasburczyk.packupdater.ui.panel.MessagePanel;
import com.nicholasburczyk.packupdater.ui.panel.PackPanel;
import com.nicholasburczyk.packupdater.ui.panel.SettingsPanel;

import javax.swing.JFrame;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainFrame extends JFrame implements Sidebar.Listener,
        PackPanel.Actions, BrowsePanel.Actions {

    private final Sidebar sidebar = new Sidebar(this);
    private final TransitionPane content = new TransitionPane();
    private final SettingsPanel settingsPanel;
    private final AdminPanel adminPanel;

    private Map<String, ModpackInfo> serverPacks = new LinkedHashMap<>();
    private Map<String, ModpackInfo> installedPacks = new LinkedHashMap<>();
    private SoftwareUpdater.Release pendingRelease;
    private String openPackId;
    private Path createdFolder;
    private boolean loading;

    public MainFrame() {
        super("GroidPack Updater");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setSize(new Dimension(1100, 720));
        setIconImages(appIcons());

        settingsPanel = new SettingsPanel(this);
        adminPanel = new AdminPanel(this);

        setLayout(new BorderLayout());
        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        setLocationRelativeTo(null);
    }

    public void start() {
        setVisible(true);
        content.show(new MessagePanel(Icons.REFRESH, Theme.TEXT_FAINT, "Loading",
                "Reading your instances and checking the server.", null, null));
        refresh();
        checkSoftwareUpdate();
    }

    public void refresh() {
        Config config = ConfigManager.getInstance().getConfig();
        if (!config.isReady()) {
            sidebar.setPacks(List.<ModpackInfo>of(), Map.<String, Integer>of());
            sidebar.setConnection(false, "Not set up");
            content.show(new MessagePanel(Icons.SETTINGS, Theme.ACCENT, "Finish setting up",
                    config.hasInstancesPath()
                            ? "Add the key ID and application key from your admin."
                            : "Add your CurseForge instances folder and the keys from your admin.",
                    "Open settings", () -> onNavigate(Sidebar.SETTINGS)));
            return;
        }

        if (loading) {
            return;
        }
        loading = true;
        sidebar.setConnecting();

        new SwingWorker<Map<String, ModpackInfo>, Void>() {
            @Override
            protected Map<String, ModpackInfo> doInBackground() throws Exception {
                return B2ClientProvider.fetchModpacks();
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    serverPacks = get();
                    ModpackRegistry.setServerModpacks(serverPacks);
                    sidebar.setConnection(true, "Connected");
                    applyPacks();
                } catch (Exception e) {
                    sidebar.setConnection(false, "Offline");
                    sidebar.setPacks(List.<ModpackInfo>of(), Map.<String, Integer>of());
                    content.show(new MessagePanel(Icons.ALERT, Theme.DANGER, "Cannot reach the server",
                            Dialogs.readable(e), "Try again", MainFrame.this::refresh));
                }
            }
        }.execute();
    }

    private void applyPacks() {
        Map<String, ModpackInfo> scanned = LocalModpackScanner.scan();
        installedPacks = new LinkedHashMap<>();
        for (Map.Entry<String, ModpackInfo> entry : scanned.entrySet()) {
            if (serverPacks.containsKey(entry.getKey())) {
                installedPacks.put(entry.getKey(), entry.getValue());
            }
        }
        ModpackRegistry.setLocalModpacks(installedPacks);

        List<ModpackInfo> ordered = new ArrayList<>(installedPacks.values());
        ordered.sort(PackOrder.byRecent());

        Map<String, Integer> pending = new LinkedHashMap<>();
        for (ModpackInfo local : ordered) {
            pending.put(local.getModpackId(),
                    ModpackSync.countNewerVersions(serverPacks.get(local.getModpackId()), local));
        }
        sidebar.setPacks(ordered, pending);

        if (ordered.isEmpty()) {
            onNavigate(Sidebar.BROWSE);
            return;
        }
        ModpackInfo target = openPackId != null && installedPacks.containsKey(openPackId)
                ? installedPacks.get(openPackId)
                : ordered.get(0);
        sidebar.selectPack(target.getModpackId());
        onSelectPack(target);
    }

    @Override
    public void onSelectPack(ModpackInfo pack) {
        openPackId = pack.getModpackId();
        content.show(new PackPanel(pack, serverPacks.get(pack.getModpackId()), this));
    }

    @Override
    public void onNavigate(String key) {
        switch (key) {
            case Sidebar.BROWSE -> {
                List<ModpackInfo> available = new ArrayList<>();
                for (ModpackInfo pack : serverPacks.values()) {
                    if (!installedPacks.containsKey(pack.getModpackId())) {
                        available.add(pack);
                    }
                }
                available.sort(PackOrder.byRecent());
                content.show(new BrowsePanel(available, installedPacks.size(), this));
            }
            case Sidebar.SETTINGS -> {
                settingsPanel.reload();
                content.show(settingsPanel);
            }
            case Sidebar.HELP -> content.show(new HelpPanel(this));
            case Sidebar.ADMIN -> {
                if (AdminLoginDialog.authenticate(this)) {
                    adminPanel.reloadSelectors();
                    content.show(adminPanel);
                } else if (openPackId != null && installedPacks.containsKey(openPackId)) {
                    sidebar.selectPack(openPackId);
                    onSelectPack(installedPacks.get(openPackId));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void onReconnect() {
        B2ClientProvider.reconnectToClient();
        PackArtwork.invalidate();
        refresh();
    }

    @Override
    public void update(ModpackInfo local, ModpackInfo server) {
        runSync(local, server, "Checking " + PackPanel.nameOf(local),
                "Update to " + server.getVersion(), true);
    }

    @Override
    public void recheck(ModpackInfo local, ModpackInfo server) {
        runSync(local, server, "Checking " + PackPanel.nameOf(local), "Repair files", false);
    }

    private void runSync(ModpackInfo local, ModpackInfo server, String title,
                         String confirmLabel, boolean isUpdate) {
        if (server == null) {
            Dialogs.warn(this, "Not on the server", "This modpack is no longer on the server.");
            return;
        }
        TaskDialog.run(this, title,
                progress -> ModpackSync.plan(local, server, progress),
                report -> {
                    if (report == null) {
                        return;
                    }
                    if (report.isClean()) {
                        Dialogs.info(this, "Nothing to do",
                                PackPanel.nameOf(local) + " already matches version "
                                        + report.targetVersion() + ".");
                        return;
                    }
                    if (!SyncReportDialog.show(this, PackPanel.nameOf(local), report, confirmLabel)) {
                        return;
                    }
                    applyPlan(local, server, report, isUpdate);
                },
                error -> Dialogs.error(this, "Check failed", Dialogs.readable(error)));
    }

    private void applyPlan(ModpackInfo local, ModpackInfo server, SyncReport report, boolean isUpdate) {
        TaskDialog.run(this, isUpdate ? "Updating" : "Repairing", progress -> {
            ModpackSync.apply(local, server, report, progress);
            return report;
        }, applied -> {
            refreshPackIcon(local);
            PackArtwork.invalidate();
            refresh();
            if (!applied.failures().isEmpty()) {
                Dialogs.warn(this, "Finished with problems",
                        String.join("\n", applied.failures()));
                return;
            }
            Dialogs.info(this, isUpdate ? "Updated" : "Repaired",
                    PackPanel.nameOf(local) + " now matches version " + applied.targetVersion() + ".");
        }, error -> {
            refresh();
            Dialogs.error(this, "Could not finish", Dialogs.readable(error));
        });
    }

    @Override
    public void openFolder(ModpackInfo local) {
        Path folder = ModpackSync.localRoot(local);
        if (!Files.isDirectory(folder)) {
            Dialogs.warn(this, "Folder missing", "That folder no longer exists:\n\n" + folder);
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(new File(folder.toString()));
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Dialogs.error(MainFrame.this, "Could not open folder", Dialogs.readable(e));
                }
            }
        }.execute();
    }

    @Override
    public void createInstance(ModpackInfo server) {
        Path instancesRoot = Choosers.instancesRoot();
        if (instancesRoot == null) {
            Dialogs.warn(this, "No instances folder",
                    "Set your CurseForge instances folder in Settings first.");
            return;
        }

        String suggested = sanitizeFolderName(PackPanel.nameOf(server));
        String name = Dialogs.prompt(this, "Name the instance",
                "This folder is created inside\n" + instancesRoot, suggested);
        if (name == null) {
            return;
        }
        String folderName = sanitizeFolderName(name);
        if (folderName.isBlank()) {
            Dialogs.warn(this, "Bad name", "Use letters, numbers, spaces, dashes or underscores.");
            return;
        }
        if (Files.exists(instancesRoot.resolve(folderName))) {
            Dialogs.warn(this, "Already exists",
                    "A folder called " + folderName + " is already there.");
            return;
        }

        TaskDialog.run(this, "Creating " + folderName, progress -> {
            Path folder = InstanceFactory.create(server, instancesRoot, folderName, progress);
            ModpackInfo local = seedManifest(server, folder);
            SyncReport report = ModpackSync.plan(local, server, progress);
            return new Prepared(local, report);
        }, prepared -> {
            if (prepared == null) {
                return;
            }
            createdFolder = instancesRoot.resolve(folderName);
            finishAdd(prepared.local(), server, prepared.report());
        }, error -> Dialogs.error(this, "Could not create the instance", Dialogs.readable(error)));
    }

    private record Prepared(ModpackInfo local, SyncReport report) {
    }

    private static String sanitizeFolderName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    @Override
    public void useExistingFolder(ModpackInfo server) {
        String title = "Select the instance folder for " + PackPanel.nameOf(server);
        Choosers.chooseDirectory(this, title, Choosers.instancesRoot(), folder -> {
            if (folder == null) {
                return;
            }
            String problem = validateInstanceFolder(folder);
            if (problem != null) {
                Dialogs.warn(this, "Cannot use that folder", problem);
                return;
            }
            ModpackInfo local = seedManifest(server, folder);
            TaskDialog.run(this, "Preparing " + PackPanel.nameOf(server),
                    progress -> ModpackSync.plan(local, server, progress),
                    report -> {
                        if (report == null) {
                            return;
                        }
                        if (!report.isClean()
                                && !SyncReportDialog.show(this, PackPanel.nameOf(server),
                                report, "Download")) {
                            return;
                        }
                        finishAdd(local, server, report);
                    },
                    error -> Dialogs.error(this, "Could not prepare the modpack",
                            Dialogs.readable(error)));
        });
    }

    private void finishAdd(ModpackInfo local, ModpackInfo server, SyncReport report) {
        TaskDialog.run(this, "Adding " + PackPanel.nameOf(server), progress -> {
            ModpackSync.apply(local, server, report, progress);
            return report;
        }, applied -> {
            refreshPackIcon(local);
            createdFolder = null;
            PackArtwork.invalidate();
            openPackId = server.getModpackId();
            refresh();
            if (!applied.failures().isEmpty()) {
                Dialogs.warn(this, "Added with problems", String.join("\n", applied.failures()));
                return;
            }
            Dialogs.info(this, "Ready",
                    PackPanel.nameOf(server) + " is installed. Launch it from CurseForge.");
        }, error -> {
            refresh();
            Dialogs.error(this, "Could not add the modpack", Dialogs.readable(error));
        });
    }

    private void refreshPackIcon(ModpackInfo local) {
        if (!ConfigManager.getInstance().getConfig().usesPackIcon(local.getModpackId())) {
            return;
        }
        Path folder = createdFolder != null ? createdFolder : ModpackSync.localRoot(local);
        if (Files.isDirectory(folder)) {
            InstanceFactory.syncProfileImage(folder, local.getModpackId());
        }
    }

    @Override
    public void setUsePackIcon(ModpackInfo local, boolean usePackIcon) {
        Config config = ConfigManager.getInstance().getConfig();
        config.setUsesPackIcon(local.getModpackId(), usePackIcon);
        ConfigManager.getInstance().saveConfig();
        if (usePackIcon) {
            refreshPackIcon(local);
        }
    }

    private String validateInstanceFolder(Path folder) {
        if (!Files.isDirectory(folder)) {
            return "That folder does not exist.";
        }
        if (Files.isRegularFile(folder.resolve("manifest.json"))) {
            return "This instance is already tracked. Use Re-check on it instead.";
        }
        Path root = Choosers.instancesRoot();
        if (root != null && !root.equals(folder.getParent())) {
            return "Pick a folder directly inside\n" + root;
        }
        return null;
    }

    private static ModpackInfo seedManifest(ModpackInfo server, Path folder) {
        ModpackInfo local = new ModpackInfo();
        local.setRoot(folder.getFileName().toString());
        local.setModpackId(server.getModpackId());
        local.setDisplayName(server.getDisplayName());
        local.setVersion("0");
        local.setFolders(server.getFolders() == null ? new ArrayList<>() : new ArrayList<>(server.getFolders()));
        local.setFiles(server.getFiles() == null ? new ArrayList<>() : new ArrayList<>(server.getFiles()));
        local.setCreated(Instant.now().toString());
        return local;
    }

    public void checkConnection() {
        sidebar.setConnecting();
        new SwingWorker<ConnectionStatus, Void>() {
            @Override
            protected ConnectionStatus doInBackground() {
                return B2ClientProvider.checkConnection();
            }

            @Override
            protected void done() {
                try {
                    ConnectionStatus status = get();
                    sidebar.setConnection(status.isConnected,
                            status.isConnected ? "Connected" : status.message);
                } catch (Exception e) {
                    sidebar.setConnection(false, "Offline");
                }
            }
        }.execute();
    }

    private void checkSoftwareUpdate() {
        if (SoftwareUpdater.hasStagedUpdate()) {
            sidebar.setSoftwareUpdate("Restart to update", this::restartForUpdate);
            return;
        }
        new SwingWorker<SoftwareUpdater.Release, Void>() {
            @Override
            protected SoftwareUpdater.Release doInBackground() {
                return SoftwareUpdater.findUpdate();
            }

            @Override
            protected void done() {
                try {
                    pendingRelease = get();
                } catch (Exception e) {
                    pendingRelease = null;
                }
                if (pendingRelease != null) {
                    sidebar.setSoftwareUpdate("Version " + pendingRelease.version() + " available",
                            MainFrame.this::installSoftwareUpdate);
                }
            }
        }.execute();
    }

    private void installSoftwareUpdate() {
        if (SoftwareUpdater.hasStagedUpdate()) {
            restartForUpdate();
            return;
        }
        if (pendingRelease == null) {
            return;
        }
        SoftwareUpdater.Release release = pendingRelease;
        TaskDialog.run(this, "Downloading " + release.version(), progress -> {
            SoftwareUpdater.stage(release, progress);
            return null;
        }, ignored -> {
            if (Dialogs.confirm(this, "Ready to install",
                    "The app will close, swap in version " + release.version() + ", and reopen.",
                    "Restart now")) {
                restartForUpdate();
            } else {
                sidebar.setSoftwareUpdate("Restart to update", this::restartForUpdate);
            }
        }, error -> Dialogs.error(this, "Update failed",
                "Nothing was changed.\n\n" + Dialogs.readable(error)));
    }

    private void restartForUpdate() {
        if (SoftwareUpdater.restartToInstall()) {
            dispose();
            System.exit(0);
        } else {
            Dialogs.error(this, "Update failed", "Your current version is untouched.");
        }
    }

    private static List<Image> appIcons() {
        return List.of(iconImage(16), iconImage(32), iconImage(64), iconImage(128));
    }

    private static Image iconImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BG);
        g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, size, size, size * 0.28, size * 0.28));
        g.setColor(Theme.BORDER_STRONG);
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 24f)));
        g.draw(new java.awt.geom.RoundRectangle2D.Double(0.5, 0.5, size - 1.0, size - 1.0,
                size * 0.28, size * 0.28));
        int inner = (int) Math.round(size * 0.56);
        Icons.of(Icons.PACKAGE, inner, Theme.TEXT)
                .paintIcon(null, g, (size - inner) / 2, (size - inner) / 2);
        g.dispose();
        return image;
    }
}
