package com.nicholasburczyk.packupdater.ui.dash;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

final class Orb {

    private static final int SCALE = 2;
    private static final double HALO = 2.3;

    private final int radius;
    private final BufferedImage halo;
    private final BufferedImage body;
    private final BufferedImage swirl;
    private final BufferedImage gloss;

    Orb(int radius) {
        this.radius = radius;
        this.halo = buildHalo(radius);
        this.body = buildBody(radius);
        this.swirl = buildSwirl(radius);
        this.gloss = buildGloss(radius);
    }

    int radius() {
        return radius;
    }

    void paint(Graphics2D g2, double cx, double cy, double scale, double alpha, double t, double flash) {
        if (alpha <= 0.01 || scale <= 0.01) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double breath = Math.sin(t * 1.15);
        double r = radius * scale * (1 + 0.018 * breath + 0.05 * flash);

        double hr = r * HALO;
        blit(g, halo, cx - hr, cy - hr, hr * 2, alpha * clamp(0.62 + 0.18 * breath + 0.5 * flash));

        double tilt = Math.toRadians(-22);
        double spin = t * 0.42;
        double ringW = r * 1.62;
        double ringH = r * (0.2 + 0.16 * Math.sin(spin));
        ring(g, cx, cy, ringW, ringH, tilt, alpha, true);

        blit(g, body, cx - r, cy - r, r * 2, alpha);
        spun(g, swirl, cx, cy, r, t * 0.31, alpha * 0.85);
        spun(g, swirl, cx, cy, r * 0.82, -t * 0.19 + 1.7, alpha * 0.45);
        blit(g, gloss, cx - r, cy - r, r * 2, alpha);

        ring(g, cx, cy, ringW, ringH, tilt, alpha, false);
        g.dispose();
    }

    private static void ring(Graphics2D g2, double cx, double cy, double w, double h, double tilt, double alpha,
                             boolean back) {
        Graphics2D g = (Graphics2D) g2.create();
        g.rotate(tilt, cx, cy);
        Shape arc = new Arc2D.Double(cx - w, cy - h, w * 2, h * 2, back ? 0 : 180, 180, Arc2D.OPEN);
        float a = (float) clamp(alpha);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(120, 255, 90, back ? 18 : 30));
        g.draw(arc);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(back ? new Color(90, 200, 80, 110) : new Color(200, 255, 170, 190));
        g.draw(arc);
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(255, 255, 255, back ? 40 : 120));
        g.draw(new Arc2D.Double(cx - w, cy - h - 1, w * 2, h * 2, back ? 20 : 200, 140, Arc2D.OPEN));
        g.dispose();
    }

    private static void spun(Graphics2D g, BufferedImage image, double cx, double cy, double r, double angle,
                             double alpha) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(alpha)));
        AffineTransform at = AffineTransform.getTranslateInstance(cx, cy);
        at.rotate(angle);
        at.scale(r * 2 / image.getWidth(), r * 2 / image.getHeight());
        at.translate(-image.getWidth() / 2.0, -image.getHeight() / 2.0);
        g.drawImage(image, at, null);
        g.setComposite(old);
    }

    private static void blit(Graphics2D g, BufferedImage image, double x, double y, double size, double alpha) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(alpha)));
        AffineTransform at = AffineTransform.getTranslateInstance(x, y);
        at.scale(size / image.getWidth(), size / image.getHeight());
        g.drawImage(image, at, null);
        g.setComposite(old);
    }

    private static BufferedImage canvas(int size) {
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
    }

    private static Graphics2D pen(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static BufferedImage buildHalo(int radius) {
        int size = (int) Math.ceil(radius * HALO * 2 * SCALE);
        BufferedImage image = canvas(size);
        Graphics2D g = pen(image);
        float c = size / 2f;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(c, c), c,
                new float[]{0f, 0.3f, 0.45f, 0.7f, 1f},
                new Color[]{new Color(200, 255, 90, 170), new Color(120, 255, 60, 120), new Color(60, 220, 50, 60),
                        new Color(30, 160, 40, 18), new Color(20, 120, 30, 0)}));
        g.fillRect(0, 0, size, size);
        g.dispose();
        return image;
    }

    private static BufferedImage buildBody(int radius) {
        int size = radius * 2 * SCALE;
        BufferedImage image = canvas(size);
        Graphics2D g = pen(image);
        Ellipse2D sphere = new Ellipse2D.Double(0, 0, size, size);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(size * 0.5f, size * 0.66f), size * 0.62f,
                new float[]{0f, 0.28f, 0.62f, 1f},
                new Color[]{new Color(0xF2FF8C), new Color(0x9BF03A), new Color(0x2E9E1B), new Color(0x0A3510)}));
        g.fill(sphere);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(size * 0.5f, size * 0.5f), size * 0.5f,
                new float[]{0.62f, 0.9f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(2, 20, 6, 110), new Color(2, 16, 4, 200)}));
        g.fill(sphere);
        g.dispose();
        return image;
    }

    private static BufferedImage buildSwirl(int radius) {
        int size = radius * 2 * SCALE;
        BufferedImage image = canvas(size);
        Graphics2D g = pen(image);
        double c = size / 2.0;
        double[][] arcs = {{0.62, 20, 130}, {0.48, 170, 110}, {0.74, 230, 80}, {0.36, 300, 140}, {0.55, 80, 60}};
        for (double[] a : arcs) {
            double r = c * a[0];
            Arc2D arc = new Arc2D.Double(c - r, c - r, r * 2, r * 2, a[1], a[2], Arc2D.OPEN);
            for (int pass = 0; pass < 4; pass++) {
                g.setStroke(new BasicStroke((float) (size * (0.07 - pass * 0.016)), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g.setColor(new Color(225, 255, 160, 22 + pass * 26));
                g.draw(arc);
            }
        }
        g.setComposite(AlphaComposite.DstIn);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c, c), (float) c,
                new float[]{0f, 0.7f, 0.88f}, new Color[]{new Color(0, 0, 0, 255), new Color(0, 0, 0, 200),
                new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, size, size);
        g.dispose();
        return image;
    }

    private static BufferedImage buildGloss(int radius) {
        int size = radius * 2 * SCALE;
        BufferedImage image = canvas(size);
        Graphics2D g = pen(image);
        g.setPaint(new GradientPaint(0f, size * 0.05f, new Color(255, 255, 255, 215), 0f, size * 0.48f,
                new Color(255, 255, 255, 0)));
        g.fill(new Ellipse2D.Double(size * 0.17, size * 0.04, size * 0.66, size * 0.44));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(size * 0.36f, size * 0.2f), size * 0.08f,
                new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 220), new Color(255, 255, 255, 0)}));
        g.fillRect(0, 0, size, size);
        g.setStroke(new BasicStroke(size * 0.025f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(0f, size * 0.6f, new Color(200, 255, 140, 0), 0f, size,
                new Color(220, 255, 150, 170)));
        double inset = size * 0.04;
        g.draw(new Arc2D.Double(inset, inset, size - inset * 2, size - inset * 2, 200, 140, Arc2D.OPEN));
        g.setStroke(new BasicStroke(SCALE * 1f));
        g.setColor(new Color(170, 255, 140, 120));
        g.draw(new Ellipse2D.Double(SCALE * 0.5, SCALE * 0.5, size - SCALE, size - SCALE));
        g.dispose();
        return image;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
