package dev.abps.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings that only affect this player's screen. Saved in config/abpsmod-client.json. */
public final class ClientPrefs {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientPrefs instance;

    public boolean hudOnRight = false;
    public boolean screenShake = true;
    public boolean screenTint = true;
    public boolean showBanners = true;
    public float hudScale = 1.0f;
    /** Where the HUD panel sits, or -1 for the default corner. Set by dragging it in the HUD editor. */
    public int hudX = -1;
    public int hudY = -1;
    /** Detail of the glowing effects: 0 low, 1 normal, 2 high. */
    public int fxQuality = 1;

    public static ClientPrefs get() {
        if (instance == null) load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("abpsmod-client.json");
    }

    public static void load() {
        try {
            if (Files.exists(file())) instance = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), ClientPrefs.class);
        } catch (Exception ignored) {
            // bad file, use defaults
        }
        if (instance == null) instance = new ClientPrefs();
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // not important enough to crash over
        }
    }
}
