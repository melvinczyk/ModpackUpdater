package com.nicholasburczyk.packupdater.ui.panel;

import com.nicholasburczyk.packupdater.ui.Icons;
import com.nicholasburczyk.packupdater.ui.Theme;
import com.nicholasburczyk.packupdater.ui.Ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;

public final class MessagePanel extends JPanel {

    public MessagePanel(String iconName, Color iconColor, String title, String detail,
                        String actionLabel, Runnable action) {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Theme.BG);

        JPanel stack = Ui.column();
        stack.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        JLabel icon = new JLabel(Icons.of(iconName, 30, iconColor));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(icon);
        stack.add(Ui.strut(Theme.GAP_M));

        JLabel heading = Ui.label(title, Theme.title(), Theme.TEXT);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(heading);
        stack.add(Ui.strut(Theme.GAP_S));

        JLabel text = Ui.label("<html><body style='width:360px;text-align:center'>"
                + Ui.escape(detail) + "</body></html>", Theme.small(), Theme.TEXT_DIM);
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(text);

        if (actionLabel != null && action != null) {
            stack.add(Ui.strut(Theme.GAP_L));
            JButton button = Ui.button(actionLabel, null, Ui.ButtonStyle.PRIMARY);
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.addActionListener(e -> action.run());
            stack.add(button);
        }

        add(stack);
    }
}
