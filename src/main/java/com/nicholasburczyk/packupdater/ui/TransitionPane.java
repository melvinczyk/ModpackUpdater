package com.nicholasburczyk.packupdater.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class TransitionPane extends JPanel {

    private static final int RISE = 10;

    private JComponent current;
    private float alpha = 1f;
    private float rise;
    private BufferedImage buffer;
    private Animator.Handle running;

    public TransitionPane() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);
    }

    public void show(JComponent next) {
        if (next == current) {
            return;
        }
        if (running != null) {
            running.stop();
        }

        removeAll();
        current = next;
        add(next, BorderLayout.CENTER);
        revalidate();

        alpha = 0f;
        rise = RISE;
        running = Animator.tween(Animator.NORMAL, 0, progress -> {
            alpha = (float) progress;
            rise = (float) Animator.lerp(RISE, 0, progress);
            repaint();
        }, () -> {
            alpha = 1f;
            rise = 0;
            buffer = null;
            repaint();
        });
    }

    public JComponent current() {
        return current;
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (alpha >= 1f && rise == 0) {
            super.paintChildren(g);
            return;
        }
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (buffer == null || buffer.getWidth() != width || buffer.getHeight() != height) {
            buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D offscreen = buffer.createGraphics();
        offscreen.setComposite(AlphaComposite.Clear);
        offscreen.fillRect(0, 0, width, height);
        offscreen.setComposite(AlphaComposite.SrcOver);
        super.paintChildren(offscreen);
        offscreen.dispose();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.max(0f, Math.min(1f, alpha))));
        g2.drawImage(buffer, 0, Math.round(rise), null);
        g2.dispose();
    }
}
