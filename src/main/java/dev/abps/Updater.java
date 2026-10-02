package dev.abps;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the mod up to date on servers and clients. It reads a small version file from the project's GitHub
 * (dist/version.json), and when there is a newer build for this Minecraft version it downloads the jar, checks
 * its SHA-256, and puts it in place of the running one for the next start.
 *
 * <p>Linux servers swap the jar right away, since a running jar there can be replaced. Windows locks the jar
 * while the game runs, so the new one waits next to it as a .pending file and a small helper finishes the swap a
 * few seconds after the game closes. The helper only renames and deletes those two files.
 */
public final class Updater {

    public static final String DEFAULT_URL = "https://raw.githubusercontent.com/abplusssss/AbpsMod/main/dist/version.json";

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean HOOKED = new AtomicBoolean();
    /** The version that has been downloaded and will run after a restart, or null. */
    private static volatile String ready;
    private static volatile String lastError;

    private Updater() {
    }

    public static String current() {
        return container().map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
    }

    public static String ready() {
        return ready;
    }

    public static String lastError() {
        return lastError;
    }

    private static Optional<ModContainer> container() {
        return FabricLoader.getInstance().getModContainer(AbpsMod.MOD_ID);
    }

    /** The jar the mod was loaded from, or null when running from a development folder. */
    private static Path runningJar() {
        Optional<ModContainer> c = container();
        if (c.isEmpty()) return null;
        for (Path p : c.get().getOrigin().getPaths()) {
            if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) return p;
        }
        return null;
    }

    /** 1 if a is newer than b, -1 if older, 0 if the same. Compares numbers dot by dot (2.10.0 is newer than 2.9.3). */
    static int compare(String a, String b) {
        String[] x = a.split("[.+-]"), y = b.split("[.+-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q ? 1 : -1;
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Checks for an update in the background. onDone runs (on the same background thread) with a short message for people. */
    public static void checkAsync(String url, java.util.function.Consumer<String> onDone) {
        if (!RUNNING.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                String msg = check(url == null || url.isBlank() ? DEFAULT_URL : url);
                if (msg != null && onDone != null) onDone.accept(msg);
            } catch (Exception e) {
                lastError = e.toString();
                AbpsMod.LOGGER.warn("AbpsMod update check failed: {}", e.toString());
            } finally {
                RUNNING.set(false);
            }
        }, "AbpsMod updater");
        t.setDaemon(true);
        t.start();
    }

    /** Returns a message when an update was installed, or null when there was nothing to do. */
    private static String check(String url) throws Exception {
        Path jar = runningJar();
        if (jar == null) return null; // development: nothing to replace
        cleanup(jar);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("version file returned HTTP " + res.statusCode());
        JsonObject v = JsonParser.parseString(res.body()).getAsJsonObject();
        String version = v.get("version").getAsString();
        String mc = v.has("minecraft") ? v.get("minecraft").getAsString() : null;
        String gameVersion = FabricLoader.getInstance().getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        if (mc != null && !mc.equals(gameVersion)) return null; // built for another Minecraft version
        String have = ready != null ? ready : current();
        if (compare(version, have) <= 0) return null;

        // Download next to the running jar, check it, then put it in place
        Path dir = jar.getParent();
        Path pending = dir.resolve("AbpsMod-" + version + ".jar.pending");
        try (InputStream in = http.send(HttpRequest.newBuilder(URI.create(v.get("url").getAsString())).timeout(Duration.ofMinutes(2)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream()).body()) {
            Files.copy(in, pending, StandardCopyOption.REPLACE_EXISTING);
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(pending)));
        if (!sha.equalsIgnoreCase(v.get("sha256").getAsString())) {
            Files.deleteIfExists(pending);
            throw new IllegalStateException("downloaded jar failed its checksum");
        }
        install(jar, pending, dir.resolve("AbpsMod-" + version + ".jar"));
        ready = version;
        AbpsMod.LOGGER.info("AbpsMod {} downloaded (running {}); it starts on the next restart", version, current());
        return "AbpsMod " + version + " is downloaded. Restart to use it (you are on " + current() + ").";
    }

    /**
     * Puts the new jar in place when the game closes. The running jar is never touched while the game runs, since
     * the loader may still read classes from it.
     */
    private static void install(Path running, Path pending, Path target) {
        if (!HOOKED.compareAndSet(false, true)) return;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> finish(running, pending, target), "AbpsMod update swap"));
    }

    private static void finish(Path running, Path pending, Path target) {
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            if (!windows) {
                // Nothing is locked here: swap right now, as the game shuts down
                Files.deleteIfExists(running);
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            // Windows keeps the jar locked until the process is gone, so a tiny helper does it a few seconds later
            String cmd = "ping -n 6 127.0.0.1 >nul & del /f /q \"" + running + "\" & if not exist \"" + running + "\" move /y \""
                    + pending + "\" \"" + target + "\"";
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not finish the AbpsMod update: {}", e.toString());
        }
    }

    /**
     * Runs at start: if a downloaded update is still waiting (the helper could not run last time), try again when the
     * game closes. Removes leftovers from finished updates.
     */
    private static void cleanup(Path running) {
        try (var files = Files.list(running.getParent())) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString();
                if (n.startsWith("AbpsMod-") && n.endsWith(".jar.old")) Files.deleteIfExists(p);
                if (n.startsWith("AbpsMod-") && n.endsWith(".jar.pending")) {
                    String v = n.substring("AbpsMod-".length(), n.length() - ".jar.pending".length());
                    if (compare(v, current()) > 0) {
                        ready = v;
                        install(running, p, p.resolveSibling("AbpsMod-" + v + ".jar"));
                    } else {
                        Files.deleteIfExists(p);
                    }
                }
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.debug("Update cleanup: {}", e.toString());
        }
    }
}
