package com.nicholasburczyk.packupdater.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class SoftwareUpdater {

    public static final String INSTALL_FLAG = "--install-update";
    public static final String SKIP_FLAG = "--skip-update-check";

    private static final String FAILED_MARKER = "install-failed";
    private static final String RELEASE_URL =
            "https://api.github.com/repos/melvinczyk/ModpackUpdater/releases/latest";
    private static final String JAR_NAME = "PackUpdater.jar";
    private static final String USER_AGENT = "GroidPackUpdater";
    private static final String DEV_VERSION = "0.0.0-dev";
    private static final int COPY_ATTEMPTS = 40;
    private static final long COPY_RETRY_MILLIS = 250;

    public record Release(String version, String downloadUrl, String notes) {
    }

    private SoftwareUpdater() {
    }

    public static String currentVersion() {
        Path jar = AppPaths.runningJar();
        if (jar != null) {
            String fromJar = versionOf(jar);
            if (fromJar != null) {
                return fromJar;
            }
        }
        String fromRuntime = SoftwareUpdater.class.getPackage().getImplementationVersion();
        return fromRuntime != null ? fromRuntime : DEV_VERSION;
    }

    public static boolean canSelfUpdate() {
        return AppPaths.isRunningFromJar();
    }

    public static Release findUpdate() {
        if (!canSelfUpdate()) {
            return null;
        }
        try {
            HttpResponse<String> response = client().send(
                    HttpRequest.newBuilder(URI.create(RELEASE_URL))
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", USER_AGENT)
                            .timeout(Duration.ofSeconds(20))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Release check returned HTTP " + response.statusCode());
                return null;
            }

            JSONObject release = new JSONObject(response.body());
            String remoteVersion = VersionUtil.normalize(release.optString("tag_name", ""));
            if (remoteVersion.isBlank()) {
                return null;
            }
            if (!VersionUtil.isGreater(remoteVersion, currentVersion())) {
                return null;
            }

            String downloadUrl = findJarAsset(release.optJSONArray("assets"));
            if (downloadUrl == null) {
                System.err.println("Release " + remoteVersion + " has no jar asset");
                return null;
            }
            return new Release(remoteVersion, downloadUrl, release.optString("body", ""));
        } catch (Exception e) {
            System.err.println("Could not check for updates: " + e.getMessage());
            return null;
        }
    }

    public static void stage(Release release, Progress progress) throws IOException {
        Path stagingDir = AppPaths.stagedUpdateDir();
        Files.createDirectories(stagingDir);
        Path target = stagingDir.resolve(JAR_NAME);
        Path temp = stagingDir.resolve(JAR_NAME + ".part");
        Files.deleteIfExists(temp);

        progress.message("Contacting download server");
        try {
            HttpResponse<InputStream> response = client().send(
                    HttpRequest.newBuilder(URI.create(release.downloadUrl()))
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "application/octet-stream")
                            .timeout(Duration.ofMinutes(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("Download failed with HTTP " + response.statusCode());
            }

            long expected = response.headers().firstValueAsLong("content-length").orElse(-1);
            progress.message("Downloading version " + release.version());

            byte[] buffer = new byte[65536];
            long written = 0;
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(temp)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (progress.isCancelled()) {
                        throw new IOException("Download cancelled");
                    }
                    out.write(buffer, 0, read);
                    written += read;
                    if (expected > 0) {
                        progress.step((int) (written / 1024), (int) (expected / 1024));
                    }
                }
            }

            if (expected > 0 && written != expected) {
                throw new IOException("Download incomplete: got " + written + " of " + expected + " bytes");
            }

            progress.message("Verifying download");
            verifyJar(temp, release.version());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(stagingDir.resolve(FAILED_MARKER));
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            throw new IOException(e.getMessage(), e);
        }
    }

    public static boolean hasStagedUpdate() {
        Path stagingDir = AppPaths.stagedUpdateDir();
        if (Files.exists(stagingDir.resolve(FAILED_MARKER))) {
            return false;
        }
        Path staged = stagingDir.resolve(JAR_NAME);
        if (!Files.isRegularFile(staged)) {
            return false;
        }
        String stagedVersion = versionOf(staged);
        return stagedVersion != null && VersionUtil.isGreater(stagedVersion, currentVersion());
    }

    public static boolean handleStartup(String[] args) {
        int flagIndex = indexOf(args, INSTALL_FLAG);
        if (flagIndex >= 0 && flagIndex + 2 < args.length) {
            runInstaller(Path.of(args[flagIndex + 1]), Path.of(args[flagIndex + 2]));
            return true;
        }
        if (indexOf(args, SKIP_FLAG) >= 0) {
            return false;
        }
        if (hasStagedUpdate()) {
            return handOffToStagedJar();
        }
        discardStagedUpdate();
        return false;
    }

    public static boolean restartToInstall() {
        if (!hasStagedUpdate()) {
            return false;
        }
        return handOffToStagedJar();
    }

    private static boolean handOffToStagedJar() {
        Path target = AppPaths.runningJar();
        Path staged = AppPaths.stagedUpdateDir().resolve(JAR_NAME);
        if (target == null) {
            return false;
        }
        try {
            new ProcessBuilder(
                    AppPaths.javaExecutable().toString(),
                    "-cp", staged.toString(),
                    "com.nicholasburczyk.packupdater.Main",
                    INSTALL_FLAG, target.toString(), staged.toString())
                    .directory(AppPaths.appDir().toFile())
                    .inheritIO()
                    .start();
            return true;
        } catch (IOException e) {
            System.err.println("Could not start the updater process: " + e.getMessage());
            return false;
        }
    }

    private static void runInstaller(Path target, Path staged) {
        if (!Files.isRegularFile(staged)) {
            System.err.println("No staged update at " + staged);
            markStagingFailed(staged.getParent());
            relaunch(target, true);
            return;
        }

        boolean copied = false;
        for (int attempt = 0; attempt < COPY_ATTEMPTS && !copied; attempt++) {
            try {
                Files.copy(staged, target, StandardCopyOption.REPLACE_EXISTING);
                copied = true;
            } catch (IOException e) {
                sleep(COPY_RETRY_MILLIS);
            }
        }

        if (!copied) {
            System.err.println("Could not replace " + target + "; the previous version is unchanged.");
            markStagingFailed(staged.getParent());
        }
        relaunch(target, !copied);
    }

    private static void relaunch(Path target, boolean skipUpdateCheck) {
        try {
            List<String> command = new ArrayList<>(List.of(
                    AppPaths.javaExecutable().toString(), "-jar", target.toString()));
            if (skipUpdateCheck) {
                command.add(SKIP_FLAG);
            }
            new ProcessBuilder(command)
                    .directory(target.getParent() == null
                            ? AppPaths.appDir().toFile()
                            : target.getParent().toFile())
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            System.err.println("Could not relaunch: " + e.getMessage());
        }
    }

    private static void markStagingFailed(Path stagingDir) {
        if (stagingDir == null) {
            return;
        }
        try {
            Files.createDirectories(stagingDir);
            Files.writeString(stagingDir.resolve(FAILED_MARKER),
                    "The staged update could not be installed.");
        } catch (IOException e) {
            System.err.println("Could not record the failed update: " + e.getMessage());
        }
    }

    private static void discardStagedUpdate() {
        Path stagingDir = AppPaths.stagedUpdateDir();
        if (!Files.isDirectory(stagingDir)) {
            return;
        }
        try (var entries = Files.list(stagingDir)) {
            List<Path> paths = new ArrayList<>(entries.toList());
            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    return;
                }
            }
            Files.deleteIfExists(stagingDir);
        } catch (IOException e) {
            System.err.println("Could not clear staged update: " + e.getMessage());
        }
    }

    private static void verifyJar(Path jar, String expectedVersion) throws IOException {
        if (Files.size(jar) < 1024) {
            throw new IOException("Downloaded file is too small to be the application");
        }
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                throw new IOException("Downloaded jar has no manifest");
            }
            String mainClass = manifest.getMainAttributes().getValue("Main-Class");
            if (mainClass == null || mainClass.isBlank()) {
                throw new IOException("Downloaded jar has no Main-Class");
            }
            String version = manifest.getMainAttributes().getValue("Implementation-Version");
            if (version == null || !VersionUtil.isGreater(version, currentVersion())) {
                throw new IOException("Downloaded jar reports version " + version
                        + ", which is not newer than " + currentVersion());
            }
            if (!VersionUtil.normalize(version).equals(VersionUtil.normalize(expectedVersion))) {
                System.err.println("Release tag " + expectedVersion + " ships jar version " + version);
            }
        }
    }

    private static String versionOf(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return null;
            }
            return manifest.getMainAttributes().getValue("Implementation-Version");
        } catch (IOException e) {
            return null;
        }
    }

    private static String findJarAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        String fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "");
            String url = asset.optString("browser_download_url", null);
            if (url == null || !name.endsWith(".jar")) {
                continue;
            }
            if (JAR_NAME.equalsIgnoreCase(name)) {
                return url;
            }
            if (fallback == null) {
                fallback = url;
            }
        }
        return fallback;
    }

    private static HttpClient client() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static int indexOf(String[] args, String flag) {
        if (args == null) {
            return -1;
        }
        for (int i = 0; i < args.length; i++) {
            if (flag.equals(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
