package dev.abps.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.abps.AbpsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Saves each player as world/abpsmod/players/UUID.json.
 * Writes happen on one background thread, in order, and go through a temp file
 * so a crash can't leave a broken file.
 */
public final class DataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path folder;
    private final Path legacyFolder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AbpsMod-Save");
        t.setDaemon(true);
        return t;
    });

    public DataStore(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("abpsmod");
        this.folder = root.resolve("players");
        // Where the old Paper plugin kept its backups
        this.legacyFolder = server.getServerDirectory().resolve("plugins").resolve("RandomAttributes").resolve("players");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            AbpsMod.LOGGER.error("Could not create data folder {}", folder, e);
        }
    }

    public Path root() {
        return folder.getParent();
    }

    public PlayerData get(ServerPlayer p) {
        PlayerData d = cache.get(p.getUUID());
        if (d == null) d = load(p);
        return d;
    }

    /** Safe from any thread. Null if the player isn't loaded. */
    public PlayerData peek(UUID id) {
        return cache.get(id);
    }

    public PlayerData load(ServerPlayer p) {
        UUID id = p.getUUID();
        PlayerData d = read(id);
        if (d == null) {
            d = readLegacy(id);
            if (d != null) AbpsMod.LOGGER.info("Imported old plugin data for {}", p.getName().getString());
        }
        if (d == null) d = new PlayerData();
        d.initRuntime();
        d.name = p.getName().getString();
        cache.put(id, d);
        return d;
    }

    private PlayerData read(UUID id) {
        Path f = folder.resolve(id + ".json");
        if (!Files.exists(f)) return null;
        try {
            return GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), PlayerData.class);
        } catch (Exception e) {
            AbpsMod.LOGGER.error("Could not read {}: {}", f.getFileName(), e.getMessage());
            return null;
        }
    }

    /** Reads the simple YAML backup files the Paper plugin wrote. */
    private PlayerData readLegacy(UUID id) {
        Path f = legacyFolder.resolve(id + ".yml");
        if (!Files.exists(f)) return null;
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            PlayerData d = new PlayerData();
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim().replace("'", "").replace("\"", "");
                switch (key) {
                    case "class" -> d.classId = value.isEmpty() ? null : value;
                    case "level" -> d.level = Integer.parseInt(value);
                    case "rerolls" -> d.rerolls = Integer.parseInt(value);
                    case "abilities-used" -> d.abilitiesUsed = Integer.parseInt(value);
                    case "hud" -> d.hud = Boolean.parseBoolean(value);
                    case "sidebar" -> d.sidebar = Boolean.parseBoolean(value);
                    default -> {
                    }
                }
            }
            return d.classId == null ? null : d;
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not import {}: {}", f.getFileName(), e.getMessage());
            return null;
        }
    }

    public void save(ServerPlayer p, PlayerData d) {
        d.savedAt = System.currentTimeMillis();
        d.name = p.getName().getString();
        String json = GSON.toJson(d);
        Path f = folder.resolve(p.getUUID() + ".json");
        if (!writer.isShutdown()) writer.execute(() -> write(f, json));
        else write(f, json);
    }

    static void write(Path f, String text) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AbpsMod.LOGGER.warn("Could not save {}: {}", f.getFileName(), e.getMessage());
        }
    }

    public void unload(UUID id) {
        cache.remove(id);
    }

    public Collection<PlayerData> loaded() {
        return cache.values();
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) AbpsMod.LOGGER.warn("Some saves took too long.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
