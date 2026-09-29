package dev.abps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All settings, saved as config/abpsmod.json.
 * Missing values get filled in with defaults when the file is loaded.
 */
public final class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // ---- Levels ----
    public int maxLevel = 25;
    /** Level needed for abilities 1 to 4. */
    public int[] abilityUnlockLevels = {1, 5, 12, 20};
    /** 0.30 = cooldowns are 30% shorter at max level. */
    public double cooldownReductionAtMax = 0.30;
    public boolean rollOnFirstJoin = true;

    // ---- Prices ----
    public int rerollXpLevels = 30;
    public Map<String, Integer> rerollItems = new LinkedHashMap<>(Map.of("minecraft:diamond", 32));
    public boolean rerollNoRepeat = true;
    public int upgradeXpBase = 3;
    public int upgradeXpPerLevel = 1;
    public int upgradeXpMax = 25;
    /** Extra items when upgrading to level 5, 10, 15, 20 and 25. */
    public Map<String, Integer> upgradeMilestoneItems = new LinkedHashMap<>(Map.of("minecraft:diamond", 5));

    // ---- Ultimates ----
    /** Damage you must deal to players to fully charge your ultimate. */
    public double ultimateDamageToCharge = 60;
    /** Damage to mobs counts this much (0.2 = 20%). */
    public double ultimateMobDamageFactor = 0.2;
    /** Seconds after using an ultimate before it starts charging again. */
    public int ultimateLockoutSeconds = 120;

    // ---- Keys for players without the mod ----
    /** Players without the mod on their game can still use F / Shift+F / double-tap F. */
    public boolean vanillaSwapHandKeys = true;
    public int doubleTapMs = 250;

    // ---- Extras ----
    public boolean tabTags = true;
    public boolean chatTags = true;
    public boolean maxLevelAura = true;
    public boolean broadcastRolls = true;
    public boolean broadcastMaxLevel = true;
    public boolean milestoneFireworks = true;
    public boolean joinMessage = true;

    // ---- Teleports ----
    public int maxHomes = 3;
    public int teleportWarmupSeconds = 3;
    public int teleportCooldownSeconds = 10;
    public int tpaExpireSeconds = 60;

    // ---- Combat ----
    /** Seconds you stay "in combat" after hitting or being hit by a player. */
    public int combatTagSeconds = 15;
    /** Minutes a player can't join after leaving while in combat. 0 turns it off. */
    public int combatLogBanMinutes = 10;

    // ---- Loading ----
    private transient Path file;

    public static Config load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("abpsmod.json");
        Config cfg = new Config();
        if (Files.exists(path)) {
            try {
                Config read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Config.class);
                if (read != null) cfg = read;
            } catch (Exception e) {
                AbpsMod.LOGGER.error("Could not read abpsmod.json, using defaults. Error: {}", e.getMessage());
            }
        }
        cfg.file = path;
        cfg.fix();
        cfg.save();
        return cfg;
    }

    private void fix() {
        maxLevel = Math.max(2, maxLevel);
        if (abilityUnlockLevels == null || abilityUnlockLevels.length < 4) abilityUnlockLevels = new int[]{1, 5, 12, 20};
        abilityUnlockLevels[0] = 1;
        cooldownReductionAtMax = Math.max(0, Math.min(0.9, cooldownReductionAtMax));
        if (rerollItems == null) rerollItems = new LinkedHashMap<>();
        if (upgradeMilestoneItems == null) upgradeMilestoneItems = new LinkedHashMap<>();
        ultimateDamageToCharge = Math.max(1, ultimateDamageToCharge);
        doubleTapMs = Math.max(100, doubleTapMs);
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AbpsMod.LOGGER.warn("Could not save abpsmod.json: {}", e.getMessage());
        }
    }

    // ---- Helpers ----
    /** 0.0 at level 1, 1.0 at max level. */
    public double scale(int level) {
        int l = Math.max(1, Math.min(maxLevel, level));
        return (l - 1) / (double) (maxLevel - 1);
    }

    public int unlockLevel(int idx) {
        return idx < 1 || idx > 4 ? 1 : abilityUnlockLevels[idx - 1];
    }

    public Cost rerollCost() {
        return new Cost(rerollXpLevels, rerollItems);
    }

    /** Cost to go from currentLevel to currentLevel + 1. */
    public Cost upgradeCost(int currentLevel) {
        int xp = Math.min(upgradeXpMax, upgradeXpBase + upgradeXpPerLevel * currentLevel);
        boolean milestone = (currentLevel + 1) % 5 == 0;
        return new Cost(xp, milestone ? upgradeMilestoneItems : Map.of());
    }
}
