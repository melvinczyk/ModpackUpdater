package com.nicholasburczyk.packupdater.ui;

import javax.swing.Timer;
import java.util.function.DoubleConsumer;

public final class Animator {

    public static final int FAST = 130;
    public static final int NORMAL = 200;
    public static final int SLOW = 320;

    private static final int FRAME_MILLIS = 16;

    public interface Handle {
        void stop();
    }

    private Animator() {
    }

    public static Handle tween(int durationMillis, DoubleConsumer onFrame) {
        return tween(durationMillis, 0, onFrame, null);
    }

    public static Handle tween(int durationMillis, int delayMillis, DoubleConsumer onFrame, Runnable onDone) {
        long start = System.nanoTime() + delayMillis * 1_000_000L;
        Timer timer = new Timer(FRAME_MILLIS, null);
        timer.addActionListener(event -> {
            long now = System.nanoTime();
            if (now < start) {
                return;
            }
            double elapsed = (now - start) / 1_000_000.0;
            double raw = durationMillis <= 0 ? 1 : Math.min(1, elapsed / durationMillis);
            onFrame.accept(easeOutCubic(raw));
            if (raw >= 1) {
                timer.stop();
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
        timer.setInitialDelay(delayMillis);
        timer.start();
        onFrame.accept(0);
        return timer::stop;
    }

    public static double easeOutCubic(double t) {
        double inverse = 1 - t;
        return 1 - inverse * inverse * inverse;
    }

    public static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
