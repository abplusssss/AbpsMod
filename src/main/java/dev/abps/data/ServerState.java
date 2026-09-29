package dev.abps.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-wide data: the spawn point, combat log bans and the leaderboard. */
public final class ServerState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Entry(UUID id, String name, String classId, int level, long reached) {
    }

    private static final class Saved {
        PlayerData.Loc spawn;
        Map<UUID, Long> bans = new HashMap<>();
        Map<UUID, Entry> leaderboard = new HashMap<>();
    }

    private final Path file;
    private Saved data = new Saved();
    private boolean dirty;

    public ServerState(Path root) {
        this.file = root.resolve("state.json");
        if (Files.exists(file)) {
            try {
                Saved read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<Saved>() {
                }.getType());
                if (read != null) data = read;
            } catch (Exception e) {
                AbpsMod.LOGGER.error("Could not read state.json: {}", e.getMessage());
            }
        }
        if (data.bans == null) data.bans = new HashMap<>();
        if (data.leaderboard == null) data.leaderboard = new HashMap<>();
    }

    // ---- Spawn ----
    public PlayerData.Loc spawn() {
        return data.spawn;
    }

    public void setSpawn(PlayerData.Loc loc) {
        data.spawn = loc;
        dirty = true;
    }

    // ---- Combat log bans ----
    public long banUntil(UUID id) {
        Long until = data.bans.get(id);
        if (until == null) return 0;
        if (until <= System.currentTimeMillis()) {
            data.bans.remove(id);
            dirty = true;
            return 0;
        }
        return until;
    }

    public void ban(UUID id, long until) {
        data.bans.put(id, until);
        dirty = true;
        saveNow(); // don't lose a ban to a crash
    }

    public boolean unban(UUID id) {
        boolean had = data.bans.remove(id) != null;
        if (had) dirty = true;
        return had;
    }

    // ---- Leaderboard ----
    public void updateLeaderboard(UUID id, String name, String classId, int level) {
        // Fake players from other mods (like "[Minecraft]") can't be real names, so keep them off the board
        if (classId == null || name.startsWith("[")) {
            if (data.leaderboard.remove(id) != null) dirty = true;
            return;
        }
        Entry old = data.leaderboard.get(id);
        if (old != null && classId.equals(old.classId()) && old.level() == level && old.name().equals(name)) return;
        long reached = old != null && old.level() == level ? old.reached() : System.currentTimeMillis();
        data.leaderboard.put(id, new Entry(id, name, classId, level, reached));
        dirty = true;
    }

    /** Highest level first. Ties go to whoever got there first. */
    public List<Entry> top(String classId, int limit) {
        List<Entry> list = new ArrayList<>();
        for (Entry e : data.leaderboard.values()) {
            if (e.name().startsWith("[")) continue;
            if (classId == null || classId.equals(e.classId())) list.add(e);
        }
        list.sort(Comparator.comparingInt(Entry::level).reversed().thenComparingLong(Entry::reached));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    public int count(String classId) {
        int n = 0;
        for (Entry e : data.leaderboard.values()) if (classId.equals(e.classId())) n++;
        return n;
    }

    // ---- Saving ----
    public void saveIfDirty() {
        if (dirty) saveNow();
    }

    public void saveNow() {
        dirty = false;
        DataStore.write(file, GSON.toJson(data));
    }
}
