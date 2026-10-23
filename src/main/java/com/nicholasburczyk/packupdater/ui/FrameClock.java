package com.nicholasburczyk.packupdater.ui;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FrameClock {

    private static final Object LOCK = new Object();
    private static final List<FrameClock> ACTIVE = new ArrayList<>();
    private static Thread thread;

    private final Runnable action;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean running;
    private long period;
    private long due;

    public FrameClock(int millis, Runnable action) {
        this.action = action;
        this.period = millis * 1_000_000L;
    }

    public void start() {
        synchronized (LOCK) {
            if (running) {
                return;
            }
            running = true;
            due = System.nanoTime() + period;
            ACTIVE.add(this);
            wake();
        }
    }

    public void stop() {
        synchronized (LOCK) {
            running = false;
            ACTIVE.remove(this);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setDelay(int millis) {
        synchronized (LOCK) {
            period = millis * 1_000_000L;
            due = Math.min(due, System.nanoTime() + period);
            if (running) {
                wake();
            }
        }
    }

    private void fire() {
        if (!queued.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            queued.set(false);
            if (running) {
                action.run();
            }
        });
    }

    private static void wake() {
        if (thread == null) {
            thread = new Thread(FrameClock::loop, "frame-clock");
            thread.setDaemon(true);
            thread.start();
        } else {
            LOCK.notifyAll();
            thread.interrupt();
        }
    }

    private static void loop() {
        while (true) {
            long wait;
            synchronized (LOCK) {
                while (ACTIVE.isEmpty()) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                long now = System.nanoTime();
                long next = Long.MAX_VALUE;
                for (FrameClock clock : ACTIVE) {
                    if (now >= clock.due) {
                        clock.fire();
                        clock.due += clock.period;
                        if (clock.due <= now) {
                            clock.due = now + clock.period;
                        }
                    }
                    next = Math.min(next, clock.due);
                }
                wait = next - System.nanoTime();
            }
            if (wait <= 0) {
                continue;
            }
            long millis = wait / 1_000_000L;
            if (millis > 1 && millis % 10 == 0) {
                millis--;
            }
            try {
                Thread.sleep(millis, millis == wait / 1_000_000L ? (int) (wait % 1_000_000L) : 0);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
