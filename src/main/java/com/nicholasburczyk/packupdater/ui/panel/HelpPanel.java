package com.nicholasburczyk.packupdater.ui.panel;

import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.MainFrame;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

public final class HelpPanel extends JPanel {

    public HelpPanel(MainFrame frame) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);

        JPanel content = Ui.column();
        content.setBorder(Ui.pad(Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL, Theme.GAP_XL));

        content.add(Ui.label("Help", Theme.display(), Theme.TEXT));
        content.add(Ui.strut(Theme.GAP_S));
        content.add(Ui.wrapped("Your modpack folders are kept identical to the server. "
                + "Worlds, screenshots and settings are never touched.", Theme.TEXT_DIM));
        content.add(Ui.strut(Theme.GAP_XL));

        content.add(definitions("Buttons", new String[][]{
                {"Update", "Downloads a newer version. Shows what changes first."},
                {"Re-check", "Repairs files that drifted from the server."},
                {"Open folder", "Opens the instance on your computer."},
        }));
        content.add(Ui.strut(Theme.GAP_M));

        content.add(bullets("What Re-check does", Icons.RECHECK, Theme.SUCCESS, new String[]{
                "Looks only at tracked folders such as mods and config.",
                "Downloads anything missing.",
                "Replaces anything that differs from the server.",
                "Deletes files you added inside tracked folders, since those break multiplayer.",
                "Leaves saves, screenshots and resource packs alone.",
                "Shows the full list and waits for you to confirm.",
        }));
        content.add(Ui.strut(Theme.GAP_M));

        content.add(definitions("Adding a modpack", new String[][]{
                {"1", "Note the Minecraft and mod loader versions on the pack."},
                {"2", "Make a CurseForge profile with those versions."},
                {"3", "Click Add modpack and pick that folder."},
        }));
        content.add(Ui.strut(Theme.GAP_M));

        content.add(definitions("Problems", new String[][]{
                {"Red dot", "Keys are wrong or you are offline. Check Settings."},
                {"Empty library", "Your instances folder is not set, or none of your "
                        + "instances are server modpacks."},
                {"macOS blocks it", "Right-click the jar, choose Open, then Open again."},
        }));

        content.add(Ui.glue());
        add(Ui.scroll(content), BorderLayout.CENTER);
    }

    private JPanel bullets(String title, String iconName, Color iconColor, String[] points) {
        JPanel card = card(title);
        for (String point : points) {
            JPanel row = Ui.row(Theme.GAP_S,
                    new JLabel(Icons.of(Icons.CHECK, 13, iconColor)),
                    Ui.wrapped(point, Theme.TEXT_DIM));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(row);
            card.add(Ui.strut(Theme.GAP_XS));
        }
        return card;
    }

    private JPanel definitions(String title, String[][] rows) {
        JPanel card = card(title);
        for (String[] row : rows) {
            JPanel line = Ui.row();
            line.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel term = Ui.label(row[0], Theme.font(java.awt.Font.BOLD, 12), Theme.ACCENT);
            term.setVerticalAlignment(JLabel.TOP);
            Ui.fixSize(term, 120, 22);

            line.add(term);
            line.add(Ui.strut(Theme.GAP_S));
            line.add(Ui.wrapped(row[1], Theme.TEXT_DIM));
            card.add(line);
            card.add(Ui.strut(Theme.GAP_S));
        }
        return card;
    }

    private JPanel card(String title) {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(Ui.pad(Theme.GAP_L));
        card.setMaximumSize(new Dimension(760, Integer.MAX_VALUE));
        card.add(Ui.heading(title));
        card.add(Ui.strut(Theme.GAP_M));
        return card;
    }
}
