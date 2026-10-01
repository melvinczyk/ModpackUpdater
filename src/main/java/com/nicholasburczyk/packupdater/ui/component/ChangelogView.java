package com.nicholasburczyk.packupdater.ui.component;

import com.nicholasburczyk.packupdater.core.VersionUtil;
import com.nicholasburczyk.packupdater.model.ChangeOperation;
import com.nicholasburczyk.packupdater.model.ChangelogEntry;
import com.nicholasburczyk.packupdater.model.ModpackInfo;
import com.nicholasburczyk.packupdater.ui.Animator;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChangelogView {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final int MAX_LISTED = 60;

    private ChangelogView() {
    }

    public static JPanel of(ModpackInfo pack, String installedVersion) {
        JPanel column = Ui.column();

        List<ChangelogEntry> entries = sorted(pack);
        if (entries.isEmpty()) {
            JPanel empty = Ui.card(Theme.SURFACE);
            empty.setLayout(new BorderLayout());
            empty.setBorder(Ui.pad(Theme.GAP_L));
            empty.add(Ui.dim("No versions published yet."), BorderLayout.CENTER);
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            column.add(empty);
            return column;
        }

        int index = 0;
        for (ChangelogEntry entry : entries) {
            if (index > 0) {
                column.add(Ui.strut(Theme.GAP_S));
            }
            boolean newer = installedVersion != null
                    && VersionUtil.isGreater(entry.getVersion(), installedVersion);
            column.add(new EntryCard(entry, installedVersion, index == 0, newer));
            index++;
        }
        return column;
    }

    private static List<ChangelogEntry> sorted(ModpackInfo pack) {
        List<ChangelogEntry> entries = new ArrayList<>();
        if (pack.getChangelog() != null) {
            for (ChangelogEntry entry : pack.getChangelog()) {
                if (entry != null && entry.getVersion() != null) {
                    entries.add(entry);
                }
            }
        }
        entries.sort(Comparator.comparing(ChangelogEntry::getVersion, VersionUtil::compare).reversed());
        return entries;
    }

    private static final class EntryCard extends Ui.RoundPanel {

        private final JPanel details;
        private final JLabel chevron;
        private boolean open;

        EntryCard(ChangelogEntry entry, String installedVersion, boolean latest, boolean newer) {
            super(Theme.SURFACE, newer ? Theme.ACCENT : Theme.BORDER, Theme.RADIUS);
            setLayout(new BorderLayout());
            setBorder(Ui.pad(Theme.GAP_M, Theme.GAP_M, Theme.GAP_M, Theme.GAP_M));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel body = Ui.column();

            chevron = new JLabel(Icons.of(Icons.CHEVRON, 14, Theme.TEXT_FAINT));

            JPanel header = Ui.row();
            header.add(chevron);
            header.add(Ui.strut(Theme.GAP_S));
            header.add(Ui.label(entry.getVersion(), Theme.font(Font.BOLD, 14),
                    newer ? Theme.ACCENT : Theme.TEXT));
            header.add(Ui.strut(Theme.GAP_S));
            if (latest) {
                header.add(Ui.chip("Latest", Theme.SUCCESS));
                header.add(Ui.strut(Theme.GAP_XS));
            }
            if (entry.getVersion().equals(installedVersion)) {
                header.add(Ui.chip("Installed", Theme.TEXT_FAINT));
            }
            header.add(Ui.glue());
            header.add(Ui.faint(formatDate(entry.getTimestamp())));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(header);

            if (entry.getMessage() != null && !entry.getMessage().isBlank()) {
                body.add(Ui.strut(Theme.GAP_S));
                JPanel messageRow = Ui.row();
                messageRow.add(Ui.strut(22));
                messageRow.add(Ui.wrapped(entry.getMessage(), Theme.TEXT_DIM));
                messageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(messageRow);
            }

            List<ChangeOperation> operations = entry.getOperations();
            int added = count(operations, "Added");
            int modified = count(operations, "Modified");
            int removed = count(operations, "Deleted") + count(operations, "Removed");

            if (added + modified + removed > 0) {
                JPanel counts = Ui.row();
                counts.add(Ui.strut(22));
                if (added > 0) {
                    counts.add(Ui.chip(added + " added", Theme.SUCCESS));
                    counts.add(Ui.strut(Theme.GAP_XS));
                }
                if (modified > 0) {
                    counts.add(Ui.chip(modified + " changed", Theme.WARNING));
                    counts.add(Ui.strut(Theme.GAP_XS));
                }
                if (removed > 0) {
                    counts.add(Ui.chip(removed + " removed", Theme.DANGER));
                }
                counts.add(Ui.glue());
                counts.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(Ui.strut(Theme.GAP_S));
                body.add(counts);
            }

            details = buildDetails(operations);
            details.setVisible(false);
            body.add(details);

            add(body, BorderLayout.CENTER);

            MouseAdapter toggle = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggle();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setFill(Theme.SURFACE_ALT);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setFill(Theme.SURFACE);
                }
            };
            addMouseListener(toggle);
        }

        private JPanel buildDetails(List<ChangeOperation> operations) {
            JPanel panel = Ui.column();
            if (operations == null || operations.isEmpty()) {
                return panel;
            }
            panel.add(Ui.strut(Theme.GAP_M));
            panel.add(Ui.divider());
            panel.add(Ui.strut(Theme.GAP_S));

            int shown = 0;
            for (ChangeOperation operation : operations) {
                if (operation == null || operation.getPath() == null) {
                    continue;
                }
                if (shown >= MAX_LISTED) {
                    JPanel more = Ui.row();
                    more.add(Ui.strut(22));
                    more.add(Ui.faint("and " + (operations.size() - shown) + " more"));
                    more.add(Ui.glue());
                    more.setAlignmentX(Component.LEFT_ALIGNMENT);
                    panel.add(more);
                    break;
                }
                panel.add(operationRow(operation));
                panel.add(Ui.strut(3));
                shown++;
            }
            return panel;
        }

        private JPanel operationRow(ChangeOperation operation) {
            String type = operation.getType() == null ? "" : operation.getType();
            Color color;
            String icon;
            if (type.equalsIgnoreCase("Added")) {
                color = Theme.SUCCESS;
                icon = Icons.FILE_NEW;
            } else if (type.equalsIgnoreCase("Modified")) {
                color = Theme.WARNING;
                icon = Icons.FILE_DIFF;
            } else {
                color = Theme.DANGER;
                icon = Icons.TRASH;
            }

            JPanel row = Ui.row();
            row.add(Ui.strut(22));
            row.add(new JLabel(Icons.of(icon, 12, color)));
            row.add(Ui.strut(Theme.GAP_S));
            JLabel path = Ui.label(operation.getPath(), Theme.tiny(), Theme.TEXT_DIM);
            row.add(path);
            row.add(Ui.glue());
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
            return row;
        }

        private void toggle() {
            if (details.getComponentCount() == 0) {
                return;
            }
            open = !open;
            details.setVisible(open);
            chevron.setIcon(Icons.of(open ? Icons.CHEVRON_DOWN : Icons.CHEVRON, 14,
                    open ? Theme.TEXT : Theme.TEXT_FAINT));
            revalidate();
            Animator.tween(Animator.FAST, progress -> repaint());
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static int count(List<ChangeOperation> operations, String type) {
        if (operations == null) {
            return 0;
        }
        int total = 0;
        for (ChangeOperation operation : operations) {
            if (operation != null && type.equalsIgnoreCase(operation.getType())) {
                total++;
            }
        }
        return total;
    }

    private static String formatDate(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "";
        }
        try {
            return ZonedDateTime.parse(timestamp).format(DATE);
        } catch (Exception e) {
            return timestamp.length() > 10 ? timestamp.substring(0, 10) : timestamp;
        }
    }
}
