package com.nicholasburczyk.packupdater.ui.dialog;

import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.ui.PackText;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;
import com.nicholasburczyk.packupdater.ui.component.ChangelogView;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;

/** Version history of a modpack; click an entry for its file list. */
public final class HistoryDialog extends JDialog {

    private HistoryDialog(Window owner, ModpackInfo pack, String installedVersion) {
        super(owner, "Version history", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, Theme.GAP_M));
        content.setOpaque(true);
        content.setBackground(Theme.BG);
        content.setBorder(Ui.pad(Theme.GAP_L));

        JPanel head = Ui.column();
        head.add(Ui.label(PackText.nameOf(pack), Theme.font(Font.BOLD, 18), Theme.TEXT));
        head.add(Ui.strut(Theme.GAP_XS));
        head.add(Ui.faint("Click a version to see which files changed."));
        content.add(head, BorderLayout.NORTH);

        JPanel list = Ui.column();
        list.add(ChangelogView.of(pack, installedVersion));
        list.add(Ui.glue());
        content.add(Ui.scroll(list), BorderLayout.CENTER);

        JButton close = Ui.button("Close", null, Ui.ButtonStyle.SECONDARY);
        close.addActionListener(e -> dispose());
        content.add(Ui.row(0, Ui.glue(), close), BorderLayout.SOUTH);

        setContentPane(content);
        setSize(new Dimension(620, 560));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(close);
    }

    public static void show(Window owner, ModpackInfo pack, String installedVersion) {
        new HistoryDialog(owner, pack, installedVersion).setVisible(true);
    }
}
