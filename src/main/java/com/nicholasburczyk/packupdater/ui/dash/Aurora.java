package com.nicholasburczyk.packupdater.ui.dash;

import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;

/**
 * Minimal aurora: two soft green curtains drifting slowly across the backdrop.
 *
 * <p>Each curtain is painted and blurred once into a small seamless texture created by the
 * screen's {@link GraphicsConfiguration}. Those images never change, so Java2D keeps them on the
 * graphics card and a frame is only a handful of GPU blits at scrolling offsets. The clock ticks
 * at a low rate, slows down on busy pages and nearly stops while the window is in the background.
 */
final class Aurora {

    private static final int FRAME_MILLIS = 50;
    private static final int CALM_FRAME_MILLIS = 120;
    private static final int IDLE_FRAME_MILLIS = 1000;
    private static final int TEX_W = 512;
    private static final int TEX_H = 200;

    /** A seamless curtain; {@code waves} must be whole so the texture tiles. Speed is widths per minute. */
    private record Curtain(double y, double amp, int waves, double thick, Color color, double alpha,
                           double phase, double speed, double widthScale) {
    }

    private static final Curtain[] CURTAINS = {
            new Curtain(.42, .12, 1, .34, new Color(30, 210, 140), .45, 0.0, 1.2, 1.7),
            new Curtain(.50, .10, 2, .28, new Color(120, 255, 70), .55, 2.1, -1.8, 1.35),
    };

    private final Runnable repaint;
    private final Timer timer;
    private final long origin = System.nanoTime();
    private BufferedImage[] textures;
    private boolean calm;
    private boolean active = true;

    Aurora(Runnable repaint) {
        this.repaint = repaint;
        this.timer = new Timer(FRAME_MILLIS, e -> this.repaint.run());
    }

    void start() {
        timer.start();
    }

    void stop() {
        timer.stop();
    }

    /** Fewer frames while a busy page (admin tables, settings form) is showing. */
    void setCalm(boolean value) {
        calm = value;
        updateRate();
    }

    /** Almost no frames while another window has focus. */
    void setWindowActive(boolean value) {
        active = value;
        updateRate();
    }

    private void updateRate() {
        timer.setDelay(!active ? IDLE_FRAME_MILLIS : calm ? CALM_FRAME_MILLIS : FRAME_MILLIS);
    }

    void paint(Graphics2D g, int w, int h, GraphicsConfiguration gc) {
        if (textures == null) {
            textures = build(gc);
        }
        double t = (System.nanoTime() - origin) / 1e9;
        Composite old = g.getComposite();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < CURTAINS.length; i++) {
            Curtain c = CURTAINS[i];
            double tileW = w * c.widthScale;
            double offset = ((t / 60.0) * c.speed / c.widthScale % 1.0) * tileW;
            if (offset > 0) {
                offset -= tileW;
            }
            // Two incommensurate frequencies so the sway never visibly repeats.
            double sway = Math.sin(t * 0.071 + c.phase) * 0.6 + Math.sin(t * 0.0437 + c.phase * 2.3) * 0.4;
            double lh = h * (1.0 + 0.08 * sway);
            double y = (h - lh) / 2 + sway * h * 0.04;
            float alpha = (float) (0.7 + 0.3 * Math.sin(t * 0.19 + c.phase * 1.7));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            for (double x = offset; x < w; x += tileW) {
                g.drawImage(textures[i], (int) Math.floor(x), (int) y, (int) Math.ceil(tileW) + 1, (int) lh, null);
            }
        }
        g.setComposite(old);
    }

    private static BufferedImage[] build(GraphicsConfiguration gc) {
        BufferedImage[] built = new BufferedImage[CURTAINS.length];
        for (int i = 0; i < CURTAINS.length; i++) {
            BufferedImage raw = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB_PRE);
            paintCurtain(raw, CURTAINS[i]);
            BufferedImage soft = blurWrapped(raw, 21, 13);
            BufferedImage managed = gc != null
                    ? gc.createCompatibleImage(TEX_W, TEX_H, Transparency.TRANSLUCENT)
                    : new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = managed.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(soft, 0, 0, null);
            g.dispose();
            built[i] = managed;
        }
        return built;
    }

    /** Bright lower edge fading upwards, following a wave that repeats exactly once per texture width. */
    private static void paintCurtain(BufferedImage image, Curtain c) {
        Graphics2D g = image.createGraphics();
        int steps = 128;
        double strip = (double) TEX_W / steps;
        for (int i = 0; i < steps; i++) {
            double u = (i + 0.5) / steps * Math.PI * 2;
            double y = TEX_H * (c.y + c.amp * Math.sin(c.waves * u + c.phase) + 0.03 * Math.sin(3 * u + c.phase));
            double shimmer = 0.55 + 0.45 * Math.sin(5 * u + c.phase);
            double curtain = TEX_H * c.thick * (0.65 + 0.35 * Math.sin(2 * u + c.phase));
            float top = (float) (y - curtain);
            float bottom = (float) (y + curtain * 0.3);
            g.setPaint(new LinearGradientPaint(0f, top, 0f, bottom,
                    new float[]{0f, 0.7f, 0.9f, 1f},
                    new Color[]{alpha(c.color, 0), alpha(c.color, c.alpha * shimmer * 0.45),
                            alpha(mixWhite(c.color), c.alpha * shimmer), alpha(c.color, 0)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fillRect((int) Math.floor(i * strip), Math.round(top), (int) Math.ceil(strip) + 1,
                    Math.round(bottom - top));
        }
        g.dispose();
    }

    /** Box blur that wraps horizontally so the texture still tiles without a seam. */
    private static BufferedImage blurWrapped(BufferedImage source, int tapsX, int tapsY) {
        int pad = tapsX;
        BufferedImage wide = new BufferedImage(TEX_W + pad * 2, TEX_H, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = wide.createGraphics();
        g.drawImage(source, pad - TEX_W, 0, null);
        g.drawImage(source, pad, 0, null);
        g.drawImage(source, pad + TEX_W, 0, null);
        g.dispose();
        BufferedImage blurred = kernel(1, tapsY).filter(kernel(tapsX, 1).filter(wide, null), null);
        return blurred.getSubimage(pad, 0, TEX_W, TEX_H);
    }

    private static ConvolveOp kernel(int width, int height) {
        float[] values = new float[width * height];
        Arrays.fill(values, 1f / values.length);
        return new ConvolveOp(new Kernel(width, height, values), ConvolveOp.EDGE_ZERO_FILL, null);
    }

    private static Color mixWhite(Color color) {
        return new Color((color.getRed() + 255) / 2, (color.getGreen() + 255) / 2, (color.getBlue() + 255) / 2);
    }

    private static Color alpha(Color color, double alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) Math.max(0, Math.min(255, Math.round(alpha * 255))));
    }
}
