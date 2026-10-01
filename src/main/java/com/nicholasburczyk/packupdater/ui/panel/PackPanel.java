package com.nicholasburczyk.packupdater.ui.panel;

import com.nicholasburczyk.packupdater.core.ModpackSync;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;
import com.nicholasburczyk.packupdater.ui.component.ChangelogView;
import com.nicholasburczyk.packupdater.ui.component.PackArtwork;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class PackPanel extends JPanel {

    private static final int ART = 104;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    public interface Actions {
        void update(ModpackInfo local, ModpackInfo server);

        void recheck(ModpackInfo local, ModpackInfo server);

        void openFolder(ModpackInfo local);
    }

    public PackPanel(ModpackInfo local, ModpackInfo server, Actions actions) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);

        int pending = ModpackSync.countNewerVersions(server, local);

        JPanel content = Ui.column();
        content.setBorder(Ui.pad(Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL));
        content.add(buildHero(local, server, pending, actions));
        content.add(Ui.strut(Theme.GAP_L));
        content.add(buildFacts(local, server));
        content.add(Ui.strut(Theme.GAP_XL));

        JPanel head = Ui.row(Theme.GAP_S, Ui.sectionLabel("Version history"),
                Ui.faint("click an entry for the file list"), Ui.glue());
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(head);
        content.add(Ui.strut(Theme.GAP_M));
        content.add(ChangelogView.of(server != null ? server : local, local.getVersion()));
        content.add(Ui.glue());

        add(Ui.scroll(content), BorderLayout.CENTER);
    }

    private JPanel buildHero(ModpackInfo local, ModpackInfo server, int pending, Actions actions) {
        Ui.RoundPanel hero = new Ui.RoundPanel(Theme.SURFACE, Theme.BORDER, Theme.RADIUS);
        hero.setLayout(new BorderLayout(Theme.GAP_L, 0));
        hero.setBorder(Ui.pad(Theme.GAP_L, Theme.GAP_L, Theme.GAP_L, Theme.GAP_L));

        JLabel art = new JLabel(Icons.of(Icons.PACKAGE, ART - 34, Theme.TEXT_FAINT));
        art.setHorizontalAlignment(JLabel.CENTER);
        art.setVerticalAlignment(JLabel.CENTER);
        Ui.fixSize(art, ART, ART);
        loadArtwork(local, art);
        JPanel artWrap = new JPanel(new BorderLayout());
        artWrap.setOpaque(false);
        artWrap.add(art, BorderLayout.NORTH);
        hero.add(artWrap, BorderLayout.WEST);

        JPanel text = Ui.column();
        text.add(Ui.label(nameOf(local), Theme.display(), Theme.TEXT));
        text.add(Ui.strut(Theme.GAP_XS));
        text.add(Ui.label(metaOf(local), Theme.small(), Theme.TEXT_DIM));

        if (local.getDescription() != null && !local.getDescription().isBlank()) {
            text.add(Ui.strut(Theme.GAP_S));
            text.add(Ui.wrapped(local.getDescription(), Theme.TEXT_FAINT));
        }

        text.add(Ui.strut(Theme.GAP_M));
        JPanel chips = Ui.row();
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.add(Ui.chip("v" + safe(local.getVersion()), Theme.TEXT_FAINT));
        chips.add(Ui.strut(Theme.GAP_XS));
        chips.add(pending > 0
                ? Ui.chip(pending == 1 ? "1 update available" : pending + " updates available", Theme.WARNING)
                : Ui.chip("Up to date", Theme.SUCCESS));
        chips.add(Ui.glue());
        text.add(chips);

        text.add(Ui.strut(Theme.GAP_L));
        JPanel buttons = Ui.row();
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (pending > 0 && server != null) {
            JButton update = Ui.button("Update to " + server.getVersion(),
                    Icons.DOWNLOAD, Ui.ButtonStyle.PRIMARY);
            update.addActionListener(e -> actions.update(local, server));
            buttons.add(update);
            buttons.add(Ui.strut(Theme.GAP_S));
        }
        JButton recheck = Ui.button("Re-check files", Icons.RECHECK, Ui.ButtonStyle.SECONDARY);
        recheck.setToolTipText("Compare every tracked file against the server and repair differences");
        recheck.addActionListener(e -> actions.recheck(local, server));
        buttons.add(recheck);
        buttons.add(Ui.strut(Theme.GAP_S));
        JButton open = Ui.button("Open folder", Icons.FOLDER, Ui.ButtonStyle.SECONDARY);
        open.addActionListener(e -> actions.openFolder(local));
        buttons.add(open);
        buttons.add(Ui.glue());
        Ui.capHeight(buttons, 38);
        text.add(buttons);

        hero.add(text, BorderLayout.CENTER);
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, hero.getPreferredSize().height));
        return hero;
    }

    private JPanel buildFacts(ModpackInfo local, ModpackInfo server) {
        JPanel row = Ui.row();
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(fact("Installed", "v" + safe(local.getVersion()), Theme.TEXT));
        row.add(Ui.strut(Theme.GAP_S));
        row.add(fact("Latest", server == null ? "unknown" : "v" + safe(server.getVersion()),
                server != null && !safe(server.getVersion()).equals(safe(local.getVersion()))
                        ? Theme.WARNING : Theme.TEXT));
        row.add(Ui.strut(Theme.GAP_S));
        row.add(fact("Updated", formatDate(local.getLastUpdated()), Theme.TEXT));
        row.add(Ui.strut(Theme.GAP_S));
        row.add(fact("Tracked", countTracked(local) + " folders", Theme.TEXT));
        row.add(Ui.glue());
        Ui.capHeight(row, 66);
        return row;
    }

    private JPanel fact(String label, String value, Color valueColor) {
        Ui.RoundPanel card = new Ui.RoundPanel(Theme.SURFACE, Theme.BORDER, Theme.RADIUS);
        card.setLayout(new BorderLayout());
        card.setBorder(Ui.pad(Theme.GAP_S + 2, Theme.GAP_M, Theme.GAP_S + 2, Theme.GAP_M));
        JPanel stack = Ui.column();
        stack.add(Ui.faint(label.toUpperCase()));
        stack.add(Ui.strut(3));
        stack.add(Ui.label(value, Theme.font(Font.BOLD, 13), valueColor));
        card.add(stack, BorderLayout.CENTER);
        Ui.fixSize(card, 148, 62);
        return card;
    }

    private static int countTracked(ModpackInfo pack) {
        return pack.getFolders() == null ? 0 : pack.getFolders().size();
    }

    private static String formatDate(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "never";
        }
        try {
            return ZonedDateTime.parse(timestamp).format(DATE);
        } catch (Exception e) {
            return timestamp.length() > 10 ? timestamp.substring(0, 10) : timestamp;
        }
    }

    private static void loadArtwork(ModpackInfo pack, JLabel target) {
        Icon cached = PackArtwork.lookup(pack, true, ART);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }
        new SwingWorker<Icon, Void>() {
            @Override
            protected Icon doInBackground() {
                return PackArtwork.load(pack, true, ART);
            }

            @Override
            protected void done() {
                try {
                    Icon icon = get();
                    if (icon != null) {
                        target.setIcon(icon);
                    }
                } catch (Exception e) {
                    target.setIcon(Icons.of(Icons.PACKAGE, ART - 34, Theme.TEXT_FAINT));
                }
            }
        }.execute();
    }

    public static String nameOf(ModpackInfo pack) {
        String name = pack.getDisplayName();
        if (name == null || name.isBlank()) {
            name = pack.getModpackId();
        }
        return name == null || name.isBlank() ? String.valueOf(pack.getRoot()) : name;
    }

    public static String metaOf(ModpackInfo pack) {
        StringBuilder meta = new StringBuilder();
        if (pack.getMinecraftVersion() != null && !pack.getMinecraftVersion().isBlank()) {
            meta.append("Minecraft ").append(pack.getMinecraftVersion());
        }
        if (pack.getModLoader() != null && !pack.getModLoader().isBlank()) {
            if (meta.length() > 0) {
                meta.append("   ·   ");
            }
            meta.append(pack.getModLoader());
            if (pack.getModLoaderVersion() != null && !pack.getModLoaderVersion().isBlank()) {
                meta.append(' ').append(pack.getModLoaderVersion());
            }
        }
        return meta.length() == 0 ? safe(pack.getRoot()) : meta.toString();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
