package dev.abps.data;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {

    /** A saved place in a world. */
    public record Loc(String dimension, double x, double y, double z, float yaw, float pitch) {
    }

    // ---- Saved ----
    public String name = "";
    public String classId;
    public int level = 1;
    public int rerolls = 0;
    public int abilitiesUsed = 0;
    public boolean hud = true;
    public boolean sidebar = true;
    public double ultCharge = 0;
    public Map<String, Loc> homes = new LinkedHashMap<>();
    public Loc back;
    public long savedAt;

    // ---- Runtime only ----
    public transient long[] cooldownEnd = new long[7];
    public transient boolean[] readyNotified = new boolean[7];
    public transient Map<String, Long> buffs = new HashMap<>();
    public transient UUID lastHit;
    public transient long lastHitTime;
    public transient UUID lastMelee;
    public transient long lastMeleeTime;
    public transient long lastUndeadHit;
    public transient long noFallUntil;
    public transient boolean leaping;
    public transient long leapStart;
    public transient boolean vanished;
    public transient UUID markTarget;
    public transient long doubleJumpReady;
    public transient int airJumps;
    /** Windwalker: we gave "may fly" for double jumping (players without the mod). */
    public transient boolean managedFlight;
    public transient long masteryReady;
    public transient int stacks;
    public transient long stacksUntil;
    public transient long empoweredUntil;
    public transient Vec3 zone;
    public transient String zoneDim;
    public transient long zoneUntil;
    public transient long ultLockUntil;
    public transient boolean ultReadyNotified;
    public transient long combatUntil;
    public transient List<Entity> displays = new ArrayList<>();
    /** Miner Ore Sense outlines: block position to fake entity id. */
    public transient Map<Long, Integer> highlights = new HashMap<>();
    /** Minion id to the time it expires. */
    public transient Map<UUID, Long> minions = new LinkedHashMap<>();
    public transient int tickCount;
    public transient boolean rolling;
    public transient boolean noCooldown;
    public transient boolean debug;
    public transient boolean hasClientMod;
    /** Built on the server thread, read by chat decoration. */
    public transient volatile Component chatTag;
    // Vanilla-client double tap on F
    public transient int tapTicksLeft;
    public transient boolean tapSneak;
    // Teleport warmup
    public transient Runnable pendingTeleport;
    public transient int teleportTicksLeft;
    public transient Vec3 teleportStart;
    public transient long teleportReadyAt;
    /** Last sync sent to the client, so we only send changes. */
    public transient int lastSyncHash;

    /** Gson skips transient fields, so fill them in after loading. */
    public void initRuntime() {
        if (cooldownEnd == null) cooldownEnd = new long[7];
        if (readyNotified == null) readyNotified = new boolean[7];
        if (buffs == null) buffs = new HashMap<>();
        if (displays == null) displays = new ArrayList<>();
        if (minions == null) minions = new LinkedHashMap<>();
        if (homes == null) homes = new LinkedHashMap<>();
        if (highlights == null) highlights = new HashMap<>();
    }

    public boolean buff(String key) {
        Long end = buffs.get(key);
        return end != null && end > System.currentTimeMillis();
    }

    public long buffLeft(String key) {
        Long end = buffs.get(key);
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    public void setBuff(String key, long ms) {
        buffs.put(key, System.currentTimeMillis() + ms);
    }

    public long cooldownLeft(int idx) {
        return Math.max(0, cooldownEnd[idx] - System.currentTimeMillis());
    }

    public boolean inCombat() {
        return combatUntil > System.currentTimeMillis();
    }

    /** Clears everything that only matters while playing a class. */
    public void resetRuntime() {
        for (int i = 0; i < cooldownEnd.length; i++) {
            cooldownEnd[i] = 0;
            readyNotified[i] = true;
        }
        for (Entity e : displays) e.discard();
        displays.clear();
        buffs.clear();
        lastHit = null;
        lastMelee = null;
        leaping = false;
        vanished = false;
        markTarget = null;
        doubleJumpReady = 0;
        airJumps = 0;
        masteryReady = 0;
        stacks = 0;
        stacksUntil = 0;
        empoweredUntil = 0;
        zone = null;
        zoneUntil = 0;
        minions.clear();
    }
}
