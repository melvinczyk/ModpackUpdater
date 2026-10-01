package com.nicholasburczyk.packupdater.ui.dialog;

import com.nicholasburczyk.packupdater.core.Progress;
import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class TaskDialog<T> extends JDialog implements Progress {

    public interface Work<T> {
        T run(Progress progress) throws Exception;
    }

    private final JLabel messageLabel = Ui.label("Working...", Theme.body(), Theme.TEXT);
    private final JLabel detailLabel = Ui.faint(" ");
    private final JProgressBar bar = new JProgressBar();
    private volatile boolean cancelled;

    private TaskDialog(Window owner, String title, boolean cancellable) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(360, 6));
        bar.setBorderPainted(false);

        JPanel content = Ui.column();
        content.setBorder(Ui.pad(Theme.GAP_L, Theme.GAP_L, Theme.GAP_L, Theme.GAP_L));
        content.setBackground(Theme.SURFACE);
        content.setOpaque(true);

        JPanel header = Ui.row(Theme.GAP_S,
                new JLabel(Icons.of(Icons.REFRESH, 16, Theme.ACCENT)),
                messageLabel,
                Ui.glue());
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        Ui.addAll(content, Theme.GAP_M, header, bar, detailLabel);

        if (cancellable) {
            JButton cancel = Ui.button("Cancel", null, Ui.ButtonStyle.SECONDARY);
            cancel.addActionListener(e -> {
                cancelled = true;
                cancel.setEnabled(false);
                messageLabel.setText("Finishing current file...");
            });
            JPanel footer = Ui.row(0, Ui.glue(), cancel);
            footer.setAlignmentX(Component.LEFT_ALIGNMENT);
            Ui.addAll(content, Theme.GAP_M, footer);
        }

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    public static <T> void run(Window owner, String title, Work<T> work,
                               Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        run(owner, title, true, work, onSuccess, onFailure);
    }

    public static <T> void run(Window owner, String title, boolean cancellable, Work<T> work,
                               Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        TaskDialog<T> dialog = new TaskDialog<>(owner, title, cancellable);

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.run(dialog);
            }

            @Override
            protected void done() {
                dialog.dispose();
                if (dialog.isCancelled()) {
                    return;
                }
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    if (onFailure != null) {
                        onFailure.accept(e.getCause() == null ? e : e.getCause());
                    }
                }
            }
        };

        worker.execute();
        dialog.setVisible(true);
    }

    @Override
    public void message(String text) {
        SwingUtilities.invokeLater(() -> messageLabel.setText(text));
    }

    @Override
    public void step(int done, int total) {
        SwingUtilities.invokeLater(() -> {
            if (total <= 0) {
                bar.setIndeterminate(true);
                detailLabel.setText(" ");
                return;
            }
            bar.setIndeterminate(false);
            bar.setMinimum(0);
            bar.setMaximum(total);
            bar.setValue(Math.min(done, total));
            detailLabel.setText(done + " of " + total);
        });
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    public static JLabel bold(String text) {
        return Ui.label(text, Theme.font(Font.BOLD, 13), Theme.TEXT);
    }
}
