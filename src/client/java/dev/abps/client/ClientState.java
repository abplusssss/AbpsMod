package dev.abps.client;

import dev.abps.net.Net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the client knows, sent by the server. */
public final class ClientState {

    private ClientState() {
    }

    public static Net.SyncPayload sync;
    public static long syncAt;
    public static final Map<String, Net.ClassInfo> catalog = new LinkedHashMap<>();
    public static Net.BoardPayload board;
    public static final Set<UUID> vanished = new HashSet<>();

    // Screen effects
    public static int shakeTicks, shakeTotal;
    public static float shakeStrength;
    public static int tintColor, tintTicks, tintTotal;
    public static float tintStrength;
    public static int flashColor, flashTicks, flashTotal;
    public static float flashStrength;

    // Banner in the middle of the screen
    public record Banner(String title, String subtitle, int color, int ticks) {
    }

    public static final List<Banner> banners = new ArrayList<>();
    public static int bannerAge;

    public static void reset() {
        sync = null;
        catalog.clear();
        board = null;
        vanished.clear();
        banners.clear();
        shakeTicks = tintTicks = flashTicks = 0;
    }

    public static Net.ClassInfo myClass() {
        return sync == null || sync.classId().isEmpty() ? null : catalog.get(sync.classId());
    }

    /** Cooldown left in ms for ability 1-4 (server uses index 1-4), counting down since the last sync. */
    public static long cooldownLeft(int idx) {
        if (sync == null) return 0;
        return Math.max(0, sync.cdLeft()[idx] - (System.currentTimeMillis() - syncAt));
    }

    public static long combatLeft() {
        if (sync == null) return 0;
        return Math.max(0, sync.combatLeft() - (System.currentTimeMillis() - syncAt));
    }

    public static long ultLockLeft() {
        if (sync == null) return 0;
        return Math.max(0, sync.ultLockLeft() - (System.currentTimeMillis() - syncAt));
    }

    public static boolean unlocked(int idx) {
        if (idx == 5) return true;
        return sync != null && sync.level() >= sync.unlock()[idx - 1];
    }

    public static void tick() {
        if (shakeTicks > 0) shakeTicks--;
        if (tintTicks > 0) tintTicks--;
        if (flashTicks > 0) flashTicks--;
        if (!banners.isEmpty()) {
            bannerAge++;
            if (bannerAge > banners.getFirst().ticks() + 20) {
                banners.removeFirst();
                bannerAge = 0;
            }
        }
    }
}
