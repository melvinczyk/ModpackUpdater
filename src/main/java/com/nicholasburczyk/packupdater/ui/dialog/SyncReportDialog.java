package com.nicholasburczyk.packupdater.ui.dialog;

import com.nicholasburczyk.packupdater.core.SyncReport;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.List;

public final class SyncReportDialog extends JDialog {

    private boolean confirmed;

    private SyncReportDialog(Window owner, String packName, SyncReport report, String actionLabel) {
        super(owner, "Review changes", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = Ui.column();
        content.setOpaque(true);
        content.setBackground(Theme.BG);
        content.setBorder(Ui.pad(Theme.GAP_L, Theme.GAP_L, Theme.GAP_L, Theme.GAP_L));

        JLabel heading = Ui.label(packName, Theme.font(Font.BOLD, 16), Theme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        String versionLine = report.isVersionChange()
                ? "Version " + report.currentVersion() + " to " + report.targetVersion()
                : "Staying on version " + report.targetVersion();
        JLabel subheading = Ui.dim(versionLine);
        subheading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel summary = buildSummary(report);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);

        Ui.addAll(content, Theme.GAP_S, heading, subheading);
        content.add(Ui.strut(Theme.GAP_M));
        content.add(summary);

        int extras = report.count(SyncReport.Kind.EXTRA);
        if (extras > 0) {
            JPanel notice = Ui.card(Theme.alpha(Theme.WARNING, 28));
            notice.setLayout(new BorderLayout(Theme.GAP_S, 0));
            notice.setBorder(Ui.pad(Theme.GAP_M));
            notice.add(new JLabel(Icons.of(Icons.WARNING, 16, Theme.WARNING)), BorderLayout.WEST);
            notice.add(Ui.wrapped(extras + (extras == 1 ? " file is" : " files are")
                    + " not part of this modpack version and will be deleted. Worlds, screenshots, "
                    + "and anything outside the tracked folders are left alone.", Theme.TEXT), BorderLayout.CENTER);
            notice.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(Ui.strut(Theme.GAP_M));
            content.add(notice);
        }

        JScrollPane scroll = Ui.scroll(buildList(report));
        scroll.setPreferredSize(new Dimension(560, 260));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.BORDER));
        content.add(Ui.strut(Theme.GAP_M));
        content.add(scroll);

        JLabel footnote = Ui.faint(report.bytesToDownload() > 0
                ? Ui.formatBytes(report.bytesToDownload()) + " to download"
                : "Nothing to download");
        footnote.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Ui.strut(Theme.GAP_S));
        content.add(footnote);

        JButton cancel = Ui.button("Cancel", null, Ui.ButtonStyle.SECONDARY);
        cancel.addActionListener(e -> dispose());
        JButton apply = Ui.button(actionLabel, Icons.CHECK, Ui.ButtonStyle.PRIMARY);
        apply.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        JPanel footer = Ui.row(Theme.GAP_S, Ui.glue(), cancel, apply);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Ui.strut(Theme.GAP_L));
        content.add(footer);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(apply);
    }

    public static boolean show(Window owner, String packName, SyncReport report, String actionLabel) {
        SyncReportDialog dialog = new SyncReportDialog(owner, packName, report, actionLabel);
        dialog.setVisible(true);
        return dialog.confirmed;
    }

    private static JPanel buildSummary(SyncReport report) {
        JPanel row = Ui.row();
        row.add(tile(report.count(SyncReport.Kind.MISSING), "Missing", Theme.ACCENT));
        row.add(Ui.strut(Theme.GAP_S));
        row.add(tile(report.count(SyncReport.Kind.OUTDATED), "Changed", Theme.WARNING));
        row.add(Ui.strut(Theme.GAP_S));
        row.add(tile(report.count(SyncReport.Kind.EXTRA), "Unexpected", Theme.DANGER));
        row.add(Ui.glue());
        return row;
    }

    private static JPanel tile(int value, String label, Color color) {
        JPanel card = Ui.card(Theme.SURFACE);
        card.setLayout(new BorderLayout());
        card.setBorder(Ui.pad(Theme.GAP_S, Theme.GAP_M, Theme.GAP_S, Theme.GAP_M));
        JPanel stack = Ui.column();
        JLabel number = Ui.label(String.valueOf(value), Theme.font(Font.BOLD, 19), color);
        number.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel caption = Ui.faint(label);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(number);
        stack.add(caption);
        card.add(stack, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(108, 58));
        card.setMaximumSize(new Dimension(108, 58));
        return card;
    }

    private static JList<SyncReport.Item> buildList(SyncReport report) {
        DefaultListModel<SyncReport.Item> model = new DefaultListModel<>();
        addGroup(model, report.itemsOf(SyncReport.Kind.MISSING));
        addGroup(model, report.itemsOf(SyncReport.Kind.OUTDATED));
        addGroup(model, report.itemsOf(SyncReport.Kind.EXTRA));

        JList<SyncReport.Item> list = new JList<>(model);
        list.setBackground(Theme.SURFACE);
        list.setFixedCellHeight(26);
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(source, value, index, false, false);
                SyncReport.Item item = (SyncReport.Item) value;
                setFont(Theme.small());
                setBorder(Ui.pad(4, Theme.GAP_M, 4, Theme.GAP_M));
                setBackground(index % 2 == 0 ? Theme.SURFACE : Theme.SURFACE_ALT);
                setForeground(Theme.TEXT_DIM);
                setIcon(iconFor(item.kind()));
                String size = item.size() > 0 ? "   " + Ui.formatBytes(item.size()) : "";
                setText(item.path() + size);
                return this;
            }
        });
        return list;
    }

    private static void addGroup(DefaultListModel<SyncReport.Item> model, List<SyncReport.Item> items) {
        for (SyncReport.Item item : items) {
            model.addElement(item);
        }
    }

    private static javax.swing.Icon iconFor(SyncReport.Kind kind) {
        return switch (kind) {
            case MISSING -> Icons.of(Icons.DOWNLOAD, 13, Theme.ACCENT);
            case OUTDATED -> Icons.of(Icons.FILE_DIFF, 13, Theme.WARNING);
            case EXTRA -> Icons.of(Icons.TRASH, 13, Theme.DANGER);
        };
    }
}
