package com.nicholasburczyk.packupdater.ui.dialog;

import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Dimension;

public final class Dialogs {

    private Dialogs() {
    }

    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, body(message), title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, body(message), title, JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, body(message), title, JOptionPane.ERROR_MESSAGE);
    }

    public static void error(Component parent, String title, Throwable error) {
        error(parent, title, readable(error));
    }

    public static boolean confirm(Component parent, String title, String message, String confirmLabel) {
        Object[] options = {confirmLabel, "Cancel"};
        int choice = JOptionPane.showOptionDialog(parent, body(message), title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    public static String prompt(Component parent, String title, String message, String initial) {
        Object answer = JOptionPane.showInputDialog(parent, body(message), title,
                JOptionPane.QUESTION_MESSAGE, null, null, initial);
        return answer == null ? null : answer.toString();
    }

    public static String readable(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return message;
    }

    private static JComponent body(String message) {
        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.body());
        area.setForeground(Theme.TEXT);
        area.setOpaque(false);
        area.setBorder(Ui.pad(0));
        area.setColumns(42);
        area.setRows(Math.min(14, Math.max(2, message.length() / 52 + message.split("\n").length)));

        if (area.getRows() <= 6) {
            return area;
        }
        JScrollPane scroll = Ui.scroll(area);
        scroll.setPreferredSize(new Dimension(430, 210));
        return scroll;
    }
}
