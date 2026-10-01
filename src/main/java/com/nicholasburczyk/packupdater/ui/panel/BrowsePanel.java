package com.nicholasburczyk.packupdater.ui.panel;

import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;
import com.nicholasburczyk.packupdater.ui.component.PackArtwork;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public final class BrowsePanel extends JPanel {

    private static final int ART = 58;

    public interface Actions {
        void createInstance(ModpackInfo server);

        void useExistingFolder(ModpackInfo server);
    }

    public BrowsePanel(List<ModpackInfo> available, int installedCount, Actions actions) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);

        JPanel content = Ui.column();
        content.setBorder(Ui.pad(Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL));

        content.add(Ui.label("Add a modpack", Theme.display(), Theme.TEXT));
        content.add(Ui.strut(Theme.GAP_S));
        content.add(Ui.wrapped("Set up for me creates the CurseForge profile and downloads "
                + "everything. You do not need to touch CurseForge first.", Theme.TEXT_DIM));
        content.add(Ui.strut(Theme.GAP_XL));

        if (available.isEmpty()) {
            content.add(emptyState(installedCount));
        } else {
            int index = 0;
            for (ModpackInfo pack : available) {
                if (index > 0) {
                    content.add(Ui.strut(Theme.GAP_S));
                }
                content.add(buildRow(pack, actions));
                index++;
            }
        }

        content.add(Ui.glue());
        add(Ui.scroll(content), BorderLayout.CENTER);
    }

    private JPanel emptyState(int installedCount) {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new BorderLayout(Theme.GAP_M, 0));
        card.setBorder(Ui.pad(Theme.GAP_L));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        card.add(new JLabel(Icons.of(Icons.OK_CIRCLE, 20, Theme.SUCCESS)), BorderLayout.WEST);
        card.add(Ui.wrapped(installedCount == 0
                ? "No modpacks on the server yet."
                : "Everything on the server is already installed.", Theme.TEXT_DIM), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRow(ModpackInfo pack, Actions actions) {
        Ui.RoundPanel row = new Ui.RoundPanel(Theme.SURFACE, Theme.BORDER, Theme.RADIUS);
        row.setLayout(new BorderLayout(Theme.GAP_M, 0));
        row.setBorder(Ui.pad(Theme.GAP_M, Theme.GAP_M, Theme.GAP_M, Theme.GAP_M));
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setFill(Theme.SURFACE_ALT);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setFill(Theme.SURFACE);
            }
        });

        JLabel art = new JLabel(Icons.of(Icons.PACKAGE, ART - 20, Theme.TEXT_FAINT));
        art.setHorizontalAlignment(JLabel.CENTER);
        Ui.fixSize(art, ART, ART);
        loadArtwork(pack, art);
        JPanel artWrap = new JPanel(new BorderLayout());
        artWrap.setOpaque(false);
        artWrap.add(art, BorderLayout.NORTH);
        row.add(artWrap, BorderLayout.WEST);

        JPanel text = Ui.column();
        text.add(Ui.label(PackPanel.nameOf(pack), Theme.heading(), Theme.TEXT));
        text.add(Ui.strut(Theme.GAP_XS));
        text.add(Ui.faint(PackPanel.metaOf(pack)));
        if (pack.getDescription() != null && !pack.getDescription().isBlank()) {
            text.add(Ui.strut(Theme.GAP_XS));
            text.add(Ui.wrapped(pack.getDescription(), Theme.TEXT_FAINT));
        }
        text.add(Ui.strut(Theme.GAP_S));
        JPanel chips = Ui.row(0, Ui.chip("v" + pack.getVersion(), Theme.ACCENT), Ui.glue());
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(chips);
        row.add(text, BorderLayout.CENTER);

        JPanel buttons = Ui.column();
        JButton create = Ui.button("Set up for me", Icons.MAGIC, Ui.ButtonStyle.PRIMARY);
        create.setToolTipText("Creates the CurseForge profile and downloads the modpack");
        create.addActionListener(e -> actions.createInstance(pack));
        create.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JButton existing = Ui.button("Use my folder", Icons.FOLDER, Ui.ButtonStyle.SECONDARY);
        existing.setToolTipText("Point at a CurseForge profile you already made");
        existing.addActionListener(e -> actions.useExistingFolder(pack));
        existing.setAlignmentX(Component.RIGHT_ALIGNMENT);

        buttons.add(create);
        buttons.add(Ui.strut(Theme.GAP_XS));
        buttons.add(existing);
        buttons.add(Ui.glue());
        row.add(buttons, BorderLayout.EAST);

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static void loadArtwork(ModpackInfo pack, JLabel target) {
        Icon cached = PackArtwork.lookup(pack, false, ART);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }
        new SwingWorker<Icon, Void>() {
            @Override
            protected Icon doInBackground() {
                return PackArtwork.load(pack, false, ART);
            }

            @Override
            protected void done() {
                try {
                    Icon icon = get();
                    if (icon != null) {
                        target.setIcon(icon);
                    }
                } catch (Exception e) {
                    target.setIcon(Icons.of(Icons.PACKAGE, ART - 20, Theme.TEXT_FAINT));
                }
            }
        }.execute();
    }
}
