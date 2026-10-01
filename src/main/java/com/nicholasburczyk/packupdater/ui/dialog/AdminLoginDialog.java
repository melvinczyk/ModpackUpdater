package com.nicholasburczyk.packupdater.ui.dialog;

import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.Arrays;

public final class AdminLoginDialog extends JDialog {

    private static final String USERNAME = "admin";
    private static final char[] PASSWORD = "goon4lyfe".toCharArray();

    private final JTextField userField = Ui.field("Username");
    private final JPasswordField passwordField = Ui.passwordField("Password");
    private final JLabel error = Ui.faint(" ");
    private boolean authenticated;

    private AdminLoginDialog(Window owner) {
        super(owner, "Admin sign in", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = Ui.column();
        content.setOpaque(true);
        content.setBackground(Theme.BG);
        content.setBorder(Ui.pad(Theme.GAP_L));

        JPanel header = Ui.row(Theme.GAP_S,
                new JLabel(Icons.of(Icons.ADMIN, 18, Theme.ACCENT)),
                Ui.label("Admin sign in", Theme.font(Font.BOLD, 15), Theme.TEXT),
                Ui.glue());
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(header);

        JLabel intro = Ui.wrapped("For publishing new versions.", Theme.TEXT_DIM);
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Ui.strut(Theme.GAP_S));
        content.add(intro);

        Ui.capHeight(userField, 38);
        Ui.capHeight(passwordField, 38);
        userField.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        error.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(Ui.strut(Theme.GAP_L));
        content.add(userField);
        content.add(Ui.strut(Theme.GAP_S));
        content.add(passwordField);
        content.add(Ui.strut(Theme.GAP_S));
        content.add(error);

        JButton cancel = Ui.button("Cancel", null, Ui.ButtonStyle.SECONDARY);
        cancel.addActionListener(e -> dispose());
        JButton signIn = Ui.button("Sign in", Icons.CHECK, Ui.ButtonStyle.PRIMARY);
        signIn.addActionListener(e -> attempt());
        passwordField.addActionListener(e -> attempt());

        JPanel footer = Ui.row(Theme.GAP_S, Ui.glue(), cancel, signIn);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Ui.strut(Theme.GAP_L));
        content.add(footer);

        setContentPane(content);
        pack();
        setSize(new Dimension(400, getPreferredSize().height));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(signIn);
    }

    public static boolean authenticate(Window owner) {
        AdminLoginDialog dialog = new AdminLoginDialog(owner);
        dialog.setVisible(true);
        return dialog.authenticated;
    }

    private void attempt() {
        char[] entered = passwordField.getPassword();
        boolean ok = USERNAME.equals(userField.getText().trim()) && Arrays.equals(PASSWORD, entered);
        Arrays.fill(entered, '\0');
        if (ok) {
            authenticated = true;
            dispose();
            return;
        }
        error.setText("That username or password is not right.");
        error.setForeground(Theme.DANGER);
        passwordField.setText("");
    }
}
