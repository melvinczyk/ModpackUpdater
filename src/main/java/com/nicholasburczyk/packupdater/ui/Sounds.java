package com.nicholasburczyk.packupdater.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Sounds {

    public enum Effect {
        MOVE("move", 3), CHANNEL("channel", 2), SELECT("select", 2), BACK("back", 2),
        SUCCESS("success", 1), ERROR("error", 1), CLICK("click", 3), POPUP("popup", 2);

        private final String file;
        private final int voices;

        Effect(String file, int voices) {
            this.file = file;
            this.voices = voices;
        }
    }

    public static final String SILENT = "sounds.silent";
    private static final String DIR = "/com/nicholasburczyk/packupdater/sounds/";
    private static final long POPUP_QUIET_NANOS = 400_000_000L;
    private static final long MIN_GAP_NANOS = 45_000_000L;
    private static final float MUSIC_GAIN_DB = -4f;
    private static final float SILENT_DB = -60f;
    private static final double FADE_IN_SECONDS = 4;
    private static final Map<Effect, Clip[]> CLIPS = new EnumMap<>(Effect.class);
    private static final Map<Effect, Long> LAST = new EnumMap<>(Effect.class);
    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sound-effects");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean enabled = true;
    private static volatile boolean loaded;
    private static volatile long lastOutcome;
    private static Thread music;

    private Sounds() {
    }

    public static void init(boolean on, Runnable whenLoaded) {
        enabled = on;
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(Sounds::observe,
                java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.WINDOW_EVENT_MASK);
        PLAYER.execute(() -> {
            for (Effect effect : Effect.values()) {
                Clip[] voices = new Clip[effect.voices];
                for (int i = 0; i < voices.length; i++) {
                    voices[i] = load(effect.file);
                }
                CLIPS.put(effect, voices);
            }
            loaded = true;
            if (whenLoaded != null) {
                whenLoaded.run();
            }
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            PLAYER.execute(() -> CLIPS.values().forEach(voices -> {
                for (Clip clip : voices) {
                    if (clip != null) {
                        clip.stop();
                    }
                }
            }));
        }
        Thread current = music;
        if (current != null) {
            current.interrupt();
        }
    }

    private static void observe(java.awt.AWTEvent event) {
        if (event.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED
                && event.getSource() instanceof javax.swing.AbstractButton button
                && ((java.awt.event.MouseEvent) event).getButton() == java.awt.event.MouseEvent.BUTTON1
                && button.isEnabled() && button.getModel().isArmed() && button.getModel().isPressed()
                && button.getClientProperty(SILENT) == null) {
            play(Effect.CLICK);
        } else if (event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                && event.getSource() instanceof java.awt.Dialog
                && System.nanoTime() - lastOutcome > POPUP_QUIET_NANOS) {
            play(Effect.POPUP);
        }
    }

    public static void play(Effect effect) {
        if (!enabled || !loaded) {
            return;
        }
        long now = System.nanoTime();
        if (effect == Effect.SUCCESS || effect == Effect.ERROR) {
            lastOutcome = now;
        }
        PLAYER.execute(() -> {
            Long last = LAST.get(effect);
            if (last != null && now - last < MIN_GAP_NANOS) {
                return;
            }
            LAST.put(effect, now);
            Clip[] voices = CLIPS.get(effect);
            if (voices == null) {
                return;
            }
            Clip pick = null;
            for (Clip clip : voices) {
                if (clip != null && !clip.isRunning()) {
                    pick = clip;
                    break;
                }
            }
            if (pick == null) {
                pick = voices[0];
            }
            if (pick == null) {
                return;
            }
            pick.stop();
            pick.setFramePosition(0);
            pick.start();
        });
    }

    public static synchronized void startMusic(long delayMillis) {
        if (music != null) {
            return;
        }
        music = new Thread(() -> loopMusic(delayMillis), "music");
        music.setDaemon(true);
        music.setPriority(Thread.NORM_PRIORITY + 1);
        music.start();
    }

    private static byte[] bytes(String file) {
        try (InputStream raw = Sounds.class.getResourceAsStream(DIR + file)) {
            return raw == null ? null : raw.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private static Clip load(String name) {
        byte[] data = bytes(name + ".wav");
        if (data == null) {
            return null;
        }
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            return clip;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static void loopMusic(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ignored) {
        }
        byte[] data = bytes("music.mp3");
        if (data == null) {
            return;
        }
        long started = 0;
        while (true) {
            if (!enabled) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ignored) {
                }
                started = 0;
                continue;
            }
            try (AudioInputStream encoded = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
                AudioFormat source = encoded.getFormat();
                AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(), 16,
                        source.getChannels(), source.getChannels() * 2, source.getSampleRate(), false);
                try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded);
                     SourceDataLine line = AudioSystem.getSourceDataLine(pcm)) {
                    line.open(pcm, (int) (pcm.getFrameRate() * pcm.getFrameSize() / 2));
                    FloatControl gain = line.isControlSupported(FloatControl.Type.MASTER_GAIN)
                            ? (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN) : null;
                    if (started == 0) {
                        started = System.nanoTime();
                    }
                    line.start();
                    byte[] buffer = new byte[pcm.getFrameSize() * 2048];
                    int read;
                    while (enabled && (read = decoded.read(buffer, 0, buffer.length)) > 0) {
                        if (gain != null) {
                            double fade = Math.min(1, (System.nanoTime() - started) / 1e9 / FADE_IN_SECONDS);
                            float db = (float) (SILENT_DB + (MUSIC_GAIN_DB - SILENT_DB) * Math.sqrt(fade));
                            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
                        }
                        line.write(buffer, 0, read);
                        if (Thread.interrupted() && !enabled) {
                            break;
                        }
                    }
                    if (enabled) {
                        line.drain();
                    } else {
                        line.stop();
                        line.flush();
                    }
                }
            } catch (Exception | LinkageError e) {
                return;
            }
        }
    }
}
