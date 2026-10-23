package com.nicholasburczyk.packupdater.ui;

import com.nicholasburczyk.packupdater.config.ConfigManager;
import com.nicholasburczyk.packupdater.core.CurseForgeLocator;
import com.nicholasburczyk.packupdater.core.InstanceFactory;
import com.nicholasburczyk.packupdater.core.LocalModpackScanner;
import com.nicholasburczyk.packupdater.core.ModpackSync;
import com.nicholasburczyk.packupdater.core.PackOrder;
import com.nicholasburczyk.packupdater.core.PlayerFaces;
import com.nicholasburczyk.packupdater.core.Screenshots;
import com.nicholasburczyk.packupdater.core.SoftwareUpdater;
import com.nicholasburczyk.packupdater.core.SyncReport;
import com.nicholasburczyk.packupdater.model.Config;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.server.B2ClientProvider;
import com.nicholasburczyk.packupdater.server.ConnectionStatus;
import com.nicholasburczyk.packupdater.server.ModpackRegistry;
import com.nicholasburczyk.packupdater.ui.component.PackArtwork;
import com.nicholasburczyk.packupdater.ui.component.ScreenshotGallery;
import com.nicholasburczyk.packupdater.ui.dash.Dashboard;
import com.nicholasburczyk.packupdater.ui.dash.Dashboard.TileSpec;
import com.nicholasburczyk.packupdater.ui.dialog.AdminLoginDialog;
import com.nicholasburczyk.packupdater.ui.dialog.Dialogs;
import com.nicholasburczyk.packupdater.ui.dialog.HistoryDialog;
import com.nicholasburczyk.packupdater.ui.dialog.SyncReportDialog;
import com.nicholasburczyk.packupdater.ui.dialog.TaskDialog;
import com.nicholasburczyk.packupdater.ui.panel.AdminPanel;
import com.nicholasburczyk.packupdater.ui.panel.SettingsPanel;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MainFrame extends JFrame implements Dashboard.Listener {

    private static final String PACKS = "packs";
    private static final String ADD = "add";
    private static final String SETTINGS = "settings";
    private static final String ADMIN = "admin";
    private static final String GALLERY = "gallery";
    private static final int CURSEFORGE_MINECRAFT = 432;

    private enum State { LOADING, NOT_READY, OFFLINE, READY }

    private final Dashboard dashboard;
    private final SettingsPanel settingsPanel;
    private final AdminPanel adminPanel;
    private final Map<String, Integer> remembered = new HashMap<>();
    private final Map<String, Boolean> templates = new ConcurrentHashMap<>();

    private Map<String, ModpackInfo> serverPacks = new LinkedHashMap<>();
    private Map<String, ModpackInfo> installedPacks = new LinkedHashMap<>();
    private List<ModpackInfo> orderedInstalled = new ArrayList<>();
    private Map<String, Integer> pending = new LinkedHashMap<>();
    private State state = State.LOADING;
    private String offlineReason = "";
    private Boolean connected;
    private String channel = PACKS;
    private boolean adminSignedIn;
    private Map<String, List<Screenshots.Shot>> captures;
    private boolean capturesLoading;
    private boolean firstLoad = true;
    private String openPackId;
    private Path createdFolder;
    private boolean loading;

    public MainFrame() {
        super("GroidPack Updater");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 640));
        setSize(new Dimension(1180, 760));
        setIconImages(appIcons());

        settingsPanel = new SettingsPanel(this);
        adminPanel = new AdminPanel(this);
        dashboard = new Dashboard(this, this::toggleAdmin);

        setContentPane(dashboard);
        setLocationRelativeTo(null);
    }

    public void start() {
        Sounds.init(ConfigManager.getInstance().getConfig().getSound(), null);
        Sounds.startMusic(1000);
        setVisible(true);
        render(true);
        refresh();
        checkSoftwareUpdate();
    }

    public void refresh() {
        Config config = ConfigManager.getInstance().getConfig();
        if (!config.isReady()) {
            state = State.NOT_READY;
            connected = false;
            dashboard.setConnection(false, "Not set up");
            render(false);
            return;
        }

        if (loading) {
            return;
        }
        loading = true;
        dashboard.setConnection(null, "Connecting");

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
                    connected = true;
                    dashboard.setConnection(true, "Connected");
                    applyPacks();
                } catch (Exception e) {
                    connected = false;
                    state = State.OFFLINE;
                    offlineReason = Dialogs.readable(e);
                    installedPacks = new LinkedHashMap<>();
                    orderedInstalled = new ArrayList<>();
                    pending = new LinkedHashMap<>();
                    dashboard.setConnection(false, "Offline");
                    render(false);
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

        orderedInstalled = new ArrayList<>(installedPacks.values());
        orderedInstalled.sort(PackOrder.byRecent());
        pending = new LinkedHashMap<>();
        for (ModpackInfo local : orderedInstalled) {
            pending.put(local.getModpackId(),
                    ModpackSync.countNewerVersions(serverPacks.get(local.getModpackId()), local));
        }
        state = State.READY;

        boolean animate = false;
        if (firstLoad && orderedInstalled.isEmpty() && PACKS.equals(channel)) {
            channel = ADD;
            animate = true;
        }
        if (firstLoad) {
            shareScreenshots(new ArrayList<>(orderedInstalled));
        }
        firstLoad = false;
        render(animate);
    }

    private void shareScreenshots(List<ModpackInfo> locals) {
        Path instances = Path.of(ConfigManager.getInstance().getConfig().getCurseforge_path());
        Thread worker = new Thread(() -> {
            try {
                Screenshots.clearOldCache();
                String player = Screenshots.detectPlayer(instances);
                if (player != null) {
                    Screenshots.upload(locals, player);
                }
            } catch (Exception e) {
                System.err.println("Could not share screenshots: " + e.getMessage());
            }
        }, "screenshot-share");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    // ------------------------------------------------------------- dashboard

    @Override
    public void onChannel(String id) {
        boolean changed = !id.equals(channel) || dashboard.inPage();
        channel = id;
        render(changed);
    }

    @Override
    public void onTileSelected(String channelId, int index) {
        remembered.put(channelId, index);
        if (PACKS.equals(channelId) && index < orderedInstalled.size()) {
            openPackId = orderedInstalled.get(index).getModpackId();
        }
    }

    @Override
    public void onReconnect() {
        B2ClientProvider.reconnectToClient();
        PackArtwork.invalidate();
        refresh();
    }

    private void render(boolean animate) {
        List<Dashboard.Channel> channels = new ArrayList<>();
        int updates = (int) pending.values().stream().filter(count -> count > 0).count();
        channels.add(new Dashboard.Channel(PACKS, "My modpacks", updates));
        channels.add(new Dashboard.Channel(GALLERY, "Gallery", 0));
        channels.add(new Dashboard.Channel(ADD, "Add modpack", 0));
        channels.add(new Dashboard.Channel(SETTINGS, "Settings", 0));
        if (adminSignedIn) {
            channels.add(new Dashboard.Channel(ADMIN, "Admin", 0));
        }
        if (ADMIN.equals(channel) && !adminSignedIn) {
            channel = PACKS;
        }
        dashboard.setChannels(channels, channel);
        dashboard.setAdminLabel(adminSignedIn ? "Sign out" : "Admin");

        switch (channel) {
            case ADMIN -> dashboard.showTiles(ADMIN, adminTiles(), 0, animate);
            case GALLERY -> dashboard.showTiles(GALLERY, galleryTiles(), remembered.getOrDefault(GALLERY, 0), animate);
            case ADD -> dashboard.showTiles(ADD, addTiles(), remembered.getOrDefault(ADD, 0), animate);
            case SETTINGS -> dashboard.showTiles(SETTINGS, settingsTiles(), remembered.getOrDefault(SETTINGS, 0), animate);
            default -> {
                List<TileSpec> tiles = packTiles();
                int selected = remembered.getOrDefault(PACKS, 0);
                for (int i = 0; i < orderedInstalled.size(); i++) {
                    if (orderedInstalled.get(i).getModpackId().equals(openPackId)) {
                        selected = i;
                    }
                }
                dashboard.showTiles(PACKS, tiles, selected, animate);
            }
        }
    }

    private void toggleAdmin() {
        if (adminSignedIn) {
            adminSignedIn = false;
            onChannel(PACKS);
            return;
        }
        if (AdminLoginDialog.authenticate(this)) {
            adminSignedIn = true;
            channel = ADMIN;
            render(true);
            openAdmin();
        }
    }

    private void openAdmin() {
        adminPanel.reloadSelectors();
        dashboard.showPage(ADMIN, adminPanel, () -> onChannel(ADMIN));
    }

    private List<TileSpec> adminTiles() {
        return List.of(TileSpec.icon("Admin panel", "Publish updates and manage modpacks", null, null, Icons.ADMIN,
                () -> detail(text("Upload new versions, create modpacks and manage what players get."),
                        List.of(button("Open", Icons.SETTINGS, Ui.ButtonStyle.PRIMARY, this::openAdmin))),
                this::openAdmin));
    }

    private Map<String, String> galleryNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (ModpackInfo pack : serverPacks.values()) {
            names.put(pack.getModpackId(), PackText.nameOf(pack));
        }
        return names;
    }

    private void loadCaptures() {
        if (capturesLoading) {
            return;
        }
        capturesLoading = true;
        Map<String, String> names = galleryNames();
        new SwingWorker<Map<String, List<Screenshots.Shot>>, Void>() {
            @Override
            protected Map<String, List<Screenshots.Shot>> doInBackground() {
                return Screenshots.listAll(names.keySet());
            }

            @Override
            protected void done() {
                capturesLoading = false;
                try {
                    captures = get();
                } catch (Exception e) {
                    captures = new LinkedHashMap<>();
                    System.err.println("Could not load the gallery: " + e.getMessage());
                }
                if (GALLERY.equals(channel) && !dashboard.inPage()) {
                    render(false);
                }
            }
        }.execute();
    }

    private List<TileSpec> galleryTiles() {
        if (state != State.READY) {
            return statusTiles();
        }
        if (captures == null) {
            loadCaptures();
            return List.of(TileSpec.icon("Loading", "Collecting everyone's captures", null, null, Icons.HEART,
                    () -> detail(text("Checking the server for captures."), List.of()), null));
        }
        Map<String, String> names = galleryNames();
        List<Map.Entry<String, List<Screenshots.Shot>>> packs = new ArrayList<>();
        for (Map.Entry<String, List<Screenshots.Shot>> entry : captures.entrySet()) {
            if (!entry.getValue().isEmpty() && names.containsKey(entry.getKey())) {
                packs.add(entry);
            }
        }
        packs.sort((a, b) -> Long.compare(b.getValue().get(0).taken(), a.getValue().get(0).taken()));
        if (packs.isEmpty()) {
            return List.of(TileSpec.icon("No captures yet", "Press F2 in game", null, null, Icons.HEART,
                    () -> detail(text("Screenshots from every pack show up here the next time the updater opens."),
                            List.of()), null));
        }
        List<TileSpec> tiles = new ArrayList<>();
        for (Map.Entry<String, List<Screenshots.Shot>> entry : packs) {
            String id = entry.getKey();
            List<Screenshots.Shot> shots = entry.getValue();
            int players = (int) shots.stream().map(Screenshots.Shot::player).distinct().count();
            String counts = (shots.size() == 1 ? "1 capture" : shots.size() + " captures")
                    + (players == 1 ? " from 1 player" : " from " + players + " players");
            List<Screenshots.Shot> front = shots.subList(0, Math.min(3, shots.size()));
            Runnable open = () -> openGallery(id);
            tiles.add(TileSpec.photos(names.get(id), counts, null, null, serverPacks.get(id),
                    () -> {
                        List<java.awt.image.BufferedImage> images = new ArrayList<>();
                        for (Screenshots.Shot shot : front) {
                            try {
                                images.add(Screenshots.thumbnail(shot));
                            } catch (Exception ignored) {
                            }
                        }
                        return images;
                    },
                    () -> {
                        List<java.awt.image.BufferedImage> faces = new ArrayList<>();
                        shots.stream().map(Screenshots.Shot::player).distinct().limit(6).forEach(name -> {
                            java.awt.image.BufferedImage face = PlayerFaces.face(name);
                            if (face != null) {
                                faces.add(face);
                            }
                        });
                        return faces;
                    },
                    () -> detail(text(counts + ". Latest " + PackText.date(java.time.Instant.ofEpochMilli(
                                    shots.get(0).taken()).atZone(java.time.ZoneId.systemDefault()).toString()) + "."),
                            List.of(button("Open gallery", Icons.FOCUS, Ui.ButtonStyle.PRIMARY, open))),
                    open));
        }
        return tiles;
    }

    private void openGallery(String packId) {
        dashboard.showPage(GALLERY, new ScreenshotGallery(galleryNames(), adminSignedIn, packId, captures), () -> {
            captures = null;
            onChannel(GALLERY);
        });
    }

    private void openServerSettings() {
        settingsPanel.reload();
        dashboard.showPage(SETTINGS, settingsPanel, () -> onChannel(SETTINGS));
    }

    // ---------------------------------------------------------------- tiles

    private List<TileSpec> statusTiles() {
        return switch (state) {
            case LOADING -> List.of(TileSpec.icon("Loading", "Checking the server", null, null, Icons.WIFI,
                    () -> detail(text("Reading your instances and checking the server."), List.of()), null));
            case NOT_READY -> {
                Runnable open = () -> onChannel(SETTINGS);
                yield List.of(TileSpec.icon("Finish setting up", "Two quick settings", "Setup", Theme.WARNING,
                        Icons.SETTINGS, () -> detail(text(ConfigManager.getInstance().getConfig().hasInstancesPath()
                                        ? "Add the key ID and application key from your admin."
                                        : "Pick your CurseForge instances folder and add the keys from your admin."),
                                List.of(button("Open settings", Icons.SETTINGS, Ui.ButtonStyle.PRIMARY, open))), open));
            }
            case OFFLINE -> {
                Runnable retry = this::refresh;
                yield List.of(TileSpec.icon("Can't reach the server", "Check your connection", "Offline", Theme.DANGER,
                        Icons.WIFI, () -> detail(text(offlineReason),
                                List.of(button("Try again", Icons.RECHECK, Ui.ButtonStyle.PRIMARY, retry),
                                        button("Settings", Icons.SETTINGS, Ui.ButtonStyle.SECONDARY,
                                                () -> onChannel(SETTINGS)))), retry));
            }
            case READY -> List.of();
        };
    }

    private List<TileSpec> packTiles() {
        if (state != State.READY) {
            return statusTiles();
        }
        if (orderedInstalled.isEmpty()) {
            Runnable add = () -> onChannel(ADD);
            return List.of(TileSpec.icon("No modpacks yet", "Add one to get started", null, null, Icons.DOWN,
                    () -> detail(text("Pick a pack under Add modpack and choose Set up for me."),
                            List.of(button("Add a modpack", Icons.DOWN, Ui.ButtonStyle.PRIMARY, add))), add));
        }
        List<TileSpec> tiles = new ArrayList<>();
        for (ModpackInfo local : orderedInstalled) {
            ModpackInfo server = serverPacks.get(local.getModpackId());
            int updates = pending.getOrDefault(local.getModpackId(), 0);
            boolean hasUpdate = updates > 0 && server != null;
            Runnable primary = hasUpdate ? () -> updateAndPlay(local, server) : () -> play(local);
            tiles.add(TileSpec.pack(PackText.nameOf(local), PackText.metaOf(local),
                    hasUpdate ? "Update ready" : null, hasUpdate ? Theme.UPDATE : null,
                    server, local, () -> packDetail(local, server, hasUpdate), primary));
        }
        return tiles;
    }

    private JComponent packDetail(ModpackInfo local, ModpackInfo server, boolean hasUpdate) {
        JPanel line = Ui.row();
        line.setAlignmentX(0f);
        String installed = "v" + PackText.safe(local.getVersion());
        if (hasUpdate) {
            line.add(Ui.label(installed, Theme.font(Font.BOLD, 18), Theme.TEXT));
            line.add(Ui.label(" to ", Theme.font(Font.PLAIN, 14), Theme.TEXT_DIM));
            line.add(Ui.label("v" + server.getVersion(), Theme.font(Font.BOLD, 18), Theme.LIME));
            String message = PackText.latestMessage(server);
            if (message != null) {
                line.add(Ui.label(".  " + shorten(message, 70), Theme.font(Font.PLAIN, 13), Theme.TEXT_DIM));
            }
        } else {
            line.add(Ui.label(installed, Theme.font(Font.BOLD, 18), Theme.TEXT));
            line.add(Ui.label(". Updated " + PackText.date(local.getLastUpdated()) + ".",
                    Theme.font(Font.PLAIN, 13), Theme.TEXT_DIM));
        }

        List<JButton> buttons = new ArrayList<>();
        if (hasUpdate) {
            buttons.add(button("Update", Icons.DOWN, Ui.ButtonStyle.PRIMARY, () -> update(local, server)));
            buttons.add(button("Repair", Icons.TARGET, Ui.ButtonStyle.SECONDARY, () -> recheck(local, server)));
            buttons.add(button("Open folder", Icons.FOLDER, Ui.ButtonStyle.SECONDARY, () -> openFolder(local)));
        } else {
            buttons.add(button("Play", Icons.PLAY, Ui.ButtonStyle.PRIMARY, () -> play(local)));
            buttons.add(button("Open folder", Icons.FOLDER, Ui.ButtonStyle.SECONDARY, () -> openFolder(local)));
            buttons.add(button("Repair", Icons.TARGET, Ui.ButtonStyle.SECONDARY, () -> recheck(local, server)));
        }
        for (JButton b : buttons) {
            if (b.getText().equals("Repair")) {
                b.setToolTipText("Compare every tracked file with the server and fix differences");
            }
        }
        buttons.add(button("History", Icons.CHAT, Ui.ButtonStyle.GHOST,
                () -> HistoryDialog.show(this, server != null ? server : local, local.getVersion())));

        JCheckBox icon = new JCheckBox("Use the pack image as the CurseForge icon",
                ConfigManager.getInstance().getConfig().usesPackIcon(local.getModpackId()));
        icon.setFont(Theme.small());
        icon.setForeground(Theme.TEXT_FAINT);
        icon.setOpaque(false);
        icon.setFocusPainted(false);
        icon.setToolTipText("Reapplied after every update. Uncheck to keep an icon you picked yourself.");
        icon.setBorder(Ui.pad(0, 0, 0, 8));
        icon.addActionListener(e -> setUsePackIcon(local, icon.isSelected()));

        return detail(line, buttons, icon);
    }

    private List<TileSpec> addTiles() {
        if (state != State.READY) {
            return statusTiles();
        }
        List<ModpackInfo> available = new ArrayList<>();
        for (ModpackInfo pack : serverPacks.values()) {
            if (!installedPacks.containsKey(pack.getModpackId())) {
                available.add(pack);
            }
        }
        available.sort(PackOrder.byRecent());
        if (available.isEmpty()) {
            return List.of(TileSpec.icon("All caught up", "Nothing new on the server", null, null, Icons.CHECK,
                    () -> detail(text(serverPacks.isEmpty()
                            ? "No modpacks on the server yet."
                            : "Everything on the server is already installed."), List.of()), null));
        }
        List<TileSpec> tiles = new ArrayList<>();
        for (ModpackInfo server : available) {
            Runnable primary = () -> {
                if (Boolean.TRUE.equals(templates.get(server.getModpackId()))) {
                    createInstance(server);
                } else {
                    useExistingFolder(server);
                }
            };
            tiles.add(TileSpec.pack(PackText.nameOf(server), PackText.metaOf(server),
                    "v" + PackText.safe(server.getVersion()), null, server, null,
                    () -> addDetail(server), primary));
        }
        return tiles;
    }

    private JComponent addDetail(ModpackInfo server) {
        String description = server.getDescription();
        JLabel text = text(description != null && !description.isBlank()
                ? description
                : "Downloads the pack and makes the CurseForge profile for you.");

        JButton create = button("Set up for me", Icons.DOWN, Ui.ButtonStyle.PRIMARY, () -> createInstance(server));
        JButton existing = button("Use my folder", Icons.FOLDER, Ui.ButtonStyle.SECONDARY,
                () -> useExistingFolder(server));
        existing.setToolTipText("Point at a CurseForge profile you already made");

        Boolean known = templates.get(server.getModpackId());
        create.setEnabled(Boolean.TRUE.equals(known));
        create.setToolTipText(known == null ? "Checking for one-click setup" : known
                ? "Creates the CurseForge profile and downloads the modpack"
                : "No one-click setup published for this pack yet, use Use my folder");
        if (known == null) {
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return InstanceFactory.hasTemplate(server);
                }

                @Override
                protected void done() {
                    boolean available;
                    try {
                        available = get();
                    } catch (Exception e) {
                        available = false;
                    }
                    templates.put(server.getModpackId(), available);
                    if (ADD.equals(dashboard.channel()) && !dashboard.inPage()) {
                        dashboard.refreshDetail();
                    }
                }
            }.execute();
        }
        return detail(text, List.of(create, existing));
    }

    private List<TileSpec> settingsTiles() {
        Config config = ConfigManager.getInstance().getConfig();
        String path = config.getCurseforge_path();
        boolean hasPath = path != null && !path.isBlank() && Files.isDirectory(Path.of(path));
        String shortPath = hasPath ? shortenPath(path) : "Not set";

        TileSpec folder = TileSpec.icon("CurseForge folder", shortPath, hasPath ? "Found" : "Missing",
                hasPath ? null : Theme.WARNING, Icons.FOLDER, () -> folderDetail(path, hasPath), this::browseInstances);

        String host = config.getEndpoint() == null || config.getEndpoint().isBlank()
                ? "No server set" : hostOf(config.getEndpoint());
        String chip = connected == null ? "Checking" : connected ? "Connected" : "Offline";
        Color chipColor = connected == null ? Theme.WARNING : connected ? null : Theme.DANGER;
        TileSpec server = TileSpec.icon("Server", host + ", bucket " + PackText.safe(config.getBucketName()),
                chip, chipColor, Icons.WIFI, () -> detail(text("Server address and the keys from your admin."),
                        List.of(button("Edit", Icons.SETTINGS, Ui.ButtonStyle.PRIMARY, this::openServerSettings),
                                button("Reconnect", Icons.RECHECK, Ui.ButtonStyle.SECONDARY, this::onReconnect))),
                this::openServerSettings);
        return List.of(folder, server);
    }

    private JComponent folderDetail(String path, boolean hasPath) {
        String status;
        if (!hasPath) {
            status = "Pick the folder that holds every CurseForge profile.";
        } else {
            File[] folders = new File(path).listFiles(File::isDirectory);
            int count = folders == null ? 0 : folders.length;
            status = (count == 1 ? "1 instance found." : count + " instances found.")
                    + " Packs are installed and updated here.";
        }
        return detail(text(status), List.of(
                button("Browse", Icons.FOLDER, Ui.ButtonStyle.PRIMARY, this::browseInstances),
                button("Detect", Icons.TARGET, Ui.ButtonStyle.SECONDARY, this::detectInstances)));
    }

    private void browseInstances() {
        Choosers.chooseDirectory(this, "Select your CurseForge Instances folder", Choosers.instancesRoot(),
                picked -> {
                    if (picked != null) {
                        saveInstancesPath(picked);
                    }
                });
    }

    private void detectInstances() {
        Optional<Path> found = CurseForgeLocator.detect();
        if (found.isPresent()) {
            saveInstancesPath(found.get());
        } else {
            Dialogs.warn(this, "Not found", "The CurseForge folder was not found automatically. Use Browse.");
        }
    }

    private void saveInstancesPath(Path path) {
        Config config = ConfigManager.getInstance().getConfig();
        config.setCurseforge_path(path.toString());
        ConfigManager.getInstance().saveConfig();
        PackArtwork.invalidate();
        render(false);
        refresh();
    }

    // -------------------------------------------------------------- detail ui

    private static JLabel text(String value) {
        Font font = Theme.font(Font.PLAIN, 13);
        JLabel label = Ui.label(value, font, Theme.TEXT_DIM);
        if (label.getFontMetrics(font).stringWidth(value) > 380) {
            label.setText("<html><body style='width:380px'>" + Ui.escape(value) + "</body></html>");
        }
        label.setAlignmentX(0f);
        label.setMaximumSize(label.getPreferredSize());
        return label;
    }

    private static JButton button(String text, String icon, Ui.ButtonStyle style, Runnable action) {
        JButton button = Ui.button(text, icon, style, 24);
        button.setFont(Theme.font(Font.BOLD, 13));
        button.addActionListener(e -> action.run());
        return button;
    }

    /** Text on the left, buttons beside it, optional extra line underneath. */
    private static JComponent detail(JComponent text, List<JButton> buttons, JComponent... extra) {
        JPanel row = Ui.row();
        row.setAlignmentX(0f);
        row.add(text);
        row.add(Ui.strut(Theme.GAP_XL));
        for (int i = 0; i < buttons.size(); i++) {
            if (i > 0) {
                row.add(Ui.strut(Theme.GAP_S));
            }
            row.add(buttons.get(i));
        }
        row.add(Ui.glue());

        JPanel column = Ui.column();
        column.add(Ui.strut(6));
        column.add(row);
        for (JComponent component : extra) {
            component.setAlignmentX(0f);
            column.add(Ui.strut(Theme.GAP_S));
            column.add(component);
        }
        column.add(Ui.glue());
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(column, BorderLayout.CENTER);
        return wrap;
    }

    private static String shorten(String value, int max) {
        String single = value.replaceAll("\\s+", " ").trim();
        return single.length() <= max ? single : single.substring(0, max - 3).trim() + "...";
    }

    private static String shortenPath(String path) {
        String home = System.getProperty("user.home");
        return home != null && path.startsWith(home) ? "~" + path.substring(home.length()) : path;
    }

    private static String hostOf(String endpoint) {
        try {
            String host = URI.create(endpoint.trim()).getHost();
            return host == null ? endpoint : host;
        } catch (Exception e) {
            return endpoint;
        }
    }

    // ---------------------------------------------------------------- actions


    public void update(ModpackInfo local, ModpackInfo server) {
        runSync(local, server, "Checking " + PackText.nameOf(local),
                "Update to " + server.getVersion(), true, null);
    }

    public void updateAndPlay(ModpackInfo local, ModpackInfo server) {
        runSync(local, server, "Checking " + PackText.nameOf(local), "Update and play", true, () -> play(local));
    }

    public void recheck(ModpackInfo local, ModpackInfo server) {
        runSync(local, server, "Checking " + PackText.nameOf(local), "Repair files", false, null);
    }

    private void runSync(ModpackInfo local, ModpackInfo server, String title,
                         String confirmLabel, boolean isUpdate, Runnable then) {
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
                    if (report.isClean() && then != null) {
                        then.run();
                        return;
                    }
                    if (report.isClean()) {
                        Dialogs.info(this, "Nothing to do",
                                PackText.nameOf(local) + " already matches version "
                                        + report.targetVersion() + ".");
                        return;
                    }
                    if (!SyncReportDialog.show(this, PackText.nameOf(local), report, confirmLabel)) {
                        return;
                    }
                    applyPlan(local, server, report, isUpdate, then);
                },
                error -> Dialogs.error(this, "Check failed", Dialogs.readable(error)));
    }

    private void applyPlan(ModpackInfo local, ModpackInfo server, SyncReport report, boolean isUpdate,
                           Runnable then) {
        TaskDialog.run(this, isUpdate ? "Updating" : "Repairing", true, progress -> {
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
            Sounds.play(Sounds.Effect.SUCCESS);
            if (then != null) {
                then.run();
                return;
            }
            Dialogs.info(this, isUpdate ? "Updated" : "Repaired",
                    PackText.nameOf(local) + " now matches version " + applied.targetVersion() + ".");
        }, error -> {
            refresh();
            Dialogs.error(this, "Could not finish", Dialogs.readable(error));
        }, this::refresh);
    }

    public void play(ModpackInfo local) {
        String id = InstanceFactory.instanceId(ModpackSync.localRoot(local));
        if (id == null) {
            Dialogs.warn(this, "Can't start this pack",
                    "This pack has no CurseForge profile yet. Open CurseForge once so it finds the pack, then try again.");
            return;
        }
        URI link = URI.create("curseforge://launch-game?instanceId=" + id + "&gameId=" + CURSEFORGE_MINECRAFT);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder(List.of("rundll32", "url.dll,FileProtocolHandler", link.toString())).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder(List.of("open", link.toString())).start();
                } else {
                    Desktop.getDesktop().browse(link);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Dialogs.error(MainFrame.this, "Could not start the game",
                            "CurseForge needs to be installed to start packs.\n\n" + Dialogs.readable(e));
                }
            }
        }.execute();
    }

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

    public void createInstance(ModpackInfo server) {
        Path instancesRoot = Choosers.instancesRoot();
        if (instancesRoot == null) {
            Dialogs.warn(this, "No instances folder",
                    "Set your CurseForge instances folder in Settings first.");
            return;
        }

        String suggested = sanitizeFolderName(PackText.nameOf(server));
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

    public void useExistingFolder(ModpackInfo server) {
        String title = "Select the instance folder for " + PackText.nameOf(server);
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
            TaskDialog.run(this, "Preparing " + PackText.nameOf(server),
                    progress -> ModpackSync.plan(local, server, progress),
                    report -> {
                        if (report == null) {
                            return;
                        }
                        if (!report.isClean()
                                && !SyncReportDialog.show(this, PackText.nameOf(server),
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
        TaskDialog.run(this, "Adding " + PackText.nameOf(server), true, progress -> {
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
            Sounds.play(Sounds.Effect.SUCCESS);
            Dialogs.info(this, "Ready",
                    PackText.nameOf(server) + " is installed. Launch it from CurseForge.");
        }, error -> {
            refresh();
            Dialogs.error(this, "Could not add the modpack", Dialogs.readable(error));
        }, this::refresh);
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
            return "This instance is already tracked. Use Repair on it instead.";
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
        dashboard.setConnection(null, "Connecting");
        new SwingWorker<ConnectionStatus, Void>() {
            @Override
            protected ConnectionStatus doInBackground() {
                return B2ClientProvider.checkConnection();
            }

            @Override
            protected void done() {
                try {
                    ConnectionStatus status = get();
                    updateConnection(status.isConnected,
                            status.isConnected ? "Connected" : status.message);
                } catch (Exception e) {
                    updateConnection(false, "Offline");
                }
            }
        }.execute();
    }

    /**
     * Keeps the app current without asking: if a newer release exists at startup it is
     * downloaded and the app restarts into it. A failed check or download is only logged so the
     * current version keeps working.
     */
    private void checkSoftwareUpdate() {
        // A staged update is installed by Main before the window opens; one still present here
        // means that hand-off failed, so don't try again until the next launch.
        if (SoftwareUpdater.isUpdateCheckSkipped() || SoftwareUpdater.hasStagedUpdate()) {
            return;
        }
        new SwingWorker<SoftwareUpdater.Release, Void>() {
            @Override
            protected SoftwareUpdater.Release doInBackground() {
                return SoftwareUpdater.findUpdate();
            }

            @Override
            protected void done() {
                SoftwareUpdater.Release release;
                try {
                    release = get();
                } catch (Exception e) {
                    release = null;
                }
                if (release != null) {
                    installSoftwareUpdate(release);
                }
            }
        }.execute();
    }

    private void installSoftwareUpdate(SoftwareUpdater.Release release) {
        TaskDialog.run(this, "Updating to " + release.version(), progress -> {
            SoftwareUpdater.stage(release, progress);
            return null;
        }, ignored -> restartForUpdate(),
                error -> System.err.println("Automatic update failed: " + Dialogs.readable(error)));
    }

    private void restartForUpdate() {
        if (SoftwareUpdater.restartToInstall()) {
            dispose();
            System.exit(0);
        } else {
            System.err.println("Could not restart into the staged update; staying on the current version.");
        }
    }

    private static List<Image> appIcons() {
        List<Image> icons = List.of(Icons.appIcon(16), Icons.appIcon(32), Icons.appIcon(64), Icons.appIcon(128), Icons.appIcon(256));
        // The Dock on macOS (and the taskbar where supported) ignores window icons.
        if (java.awt.Taskbar.isTaskbarSupported()
                && java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
            java.awt.Taskbar.getTaskbar().setIconImage(Icons.appIcon(512));
        }
        return icons;
    }

    private void updateConnection(boolean isConnected, String text) {
        connected = isConnected;
        dashboard.setConnection(isConnected, text);
        if (SETTINGS.equals(channel) && !dashboard.inPage()) {
            render(false);
        }
    }
}
