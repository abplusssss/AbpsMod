package dev.abps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.abps.classes.AttributeClass;

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
    /** Level needed for abilities 1 to 5 (only attributes with a fifth ability use the last number). */
    public int[] abilityUnlockLevels = {1, 5, 12, 20, 16};
    /** 0.30 = cooldowns are 30% shorter at max level. */
    public double cooldownReductionAtMax = 0.30;
    public boolean rollOnFirstJoin = true;

    // ---- Prices ----
    public int rerollXpLevels = 30;
    public Map<String, Integer> rerollItems = new LinkedHashMap<>(Map.of("minecraft:netherite_ingot", 4));
    public boolean rerollNoRepeat = true;
    public int upgradeXpBase = 8;
    public int upgradeXpPerLevel = 2;
    public int upgradeXpMax = 40;
    /** Extra items when upgrading to level 5, 10, 15, 20 and 25, multiplied by (level / 5). */
    public Map<String, Integer> upgradeMilestoneItems = new LinkedHashMap<>(Map.of("minecraft:netherite_scrap", 1));
    /** Extra items for the very last upgrade to max level. */
    public Map<String, Integer> upgradeMaxLevelItems = new LinkedHashMap<>(Map.of("minecraft:netherite_ingot", 2, "minecraft:nether_star", 1));
    /** Multiplies how many of each themed item an upgrade needs. 1.0 = default, 0.5 = half as many. */
    public double upgradeItemScale = 1.0;
    /** Lets the mod replace old default prices once when the prices are reworked. */
    public int priceVersion = 0;

    // ---- Visual effects ----
    /** Turns all the custom glowing block effects off. */
    public boolean visualEffects = true;
    /** 1.0 = everything, 0.5 = about half of each effect. Lower this on busy servers. */
    public double effectDensity = 1.0;

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

    // ---- Player shops ----
    public boolean shopsEnabled = true;
    /** What it costs to open a shop. */
    public int shopCreateXpLevels = 0;
    public java.util.Map<String, Integer> shopCreateItems = new java.util.LinkedHashMap<>(java.util.Map.of(
            "minecraft:netherite_ingot", 1, "minecraft:diamond", 3));
    /** Most listings one shop can have. */
    public int shopMaxListings = 27;
    /** The item new listings are priced in unless the owner picks another. */
    public String shopDefaultCurrency = "minecraft:diamond";

    // ---- Daily rewards ----
    public boolean dailyRewardsEnabled = true;
    /** One reward per day of a 7 day streak. Missing a day starts the streak over. Each is a list of item id to count. */
    public java.util.List<java.util.Map<String, Integer>> dailyRewards = new java.util.ArrayList<>(java.util.List.of(
            java.util.Map.of("minecraft:iron_ingot", 16),
            java.util.Map.of("minecraft:gold_ingot", 12),
            java.util.Map.of("minecraft:diamond", 3),
            java.util.Map.of("minecraft:emerald", 16),
            java.util.Map.of("minecraft:diamond", 5, "minecraft:experience_bottle", 8),
            java.util.Map.of("minecraft:golden_apple", 1, "minecraft:diamond", 4),
            java.util.Map.of("minecraft:netherite_scrap", 1, "minecraft:diamond", 8)));

    public Cost shopCreateCost() {
        return new Cost(shopCreateXpLevels, shopCreateItems);
    }

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
        if (abilityUnlockLevels == null || abilityUnlockLevels.length < 4) abilityUnlockLevels = new int[]{1, 5, 12, 20, 16};
        if (abilityUnlockLevels.length < 5) {
            abilityUnlockLevels = java.util.Arrays.copyOf(abilityUnlockLevels, 5);
            abilityUnlockLevels[4] = 16;
        }
        abilityUnlockLevels[0] = 1;
        cooldownReductionAtMax = Math.max(0, Math.min(0.9, cooldownReductionAtMax));
        if (rerollItems == null) rerollItems = new LinkedHashMap<>();
        if (priceVersion < 2) {
            // Prices were reworked: netherite rerolls and themed, much more expensive upgrades. Applied once to old configs.
            rerollXpLevels = 30;
            rerollItems = new LinkedHashMap<>(Map.of("minecraft:netherite_ingot", 4));
            upgradeXpBase = 8;
            upgradeXpPerLevel = 2;
            upgradeXpMax = 40;
            upgradeMilestoneItems = new LinkedHashMap<>(Map.of("minecraft:netherite_scrap", 1));
            priceVersion = 2;
        }
        if (upgradeMilestoneItems == null) upgradeMilestoneItems = new LinkedHashMap<>();
        if (upgradeMaxLevelItems == null) upgradeMaxLevelItems = new LinkedHashMap<>(Map.of("minecraft:netherite_ingot", 2, "minecraft:nether_star", 1));
        upgradeItemScale = Math.max(0.1, upgradeItemScale);
        effectDensity = Math.max(0.1, Math.min(1.0, effectDensity));
        dev.abps.util.Vfx.configure(visualEffects, effectDensity);
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
        return idx < 1 || idx > 5 ? 1 : abilityUnlockLevels[idx - 1];
    }

    public Cost rerollCost() {
        return new Cost(rerollXpLevels, rerollItems);
    }

    /**
     * Cost to go from currentLevel to currentLevel + 1. The items come from the attribute's theme and get rarer
     * as the levels go up (levels 2-6, 7-12, 13-18 and 19-25 each use a different item).
     */
    public Cost upgradeCost(int currentLevel, AttributeClass cls) {
        int target = currentLevel + 1;
        int xp = Math.min(upgradeXpMax, upgradeXpBase + upgradeXpPerLevel * currentLevel);
        String[] ids = cls == null ? AttributeClass.DEFAULT_UPGRADE_ITEMS : cls.upgradeItems();
        int[] base = cls == null ? AttributeClass.DEFAULT_UPGRADE_COUNTS : cls.upgradeCounts();
        int tier = target <= 6 ? 0 : target <= 12 ? 1 : target <= 18 ? 2 : 3;
        int tierStart = new int[]{2, 7, 13, 19}[tier];
        Map<String, Integer> items = new LinkedHashMap<>();
        int count = (int) Math.max(1, Math.round(base[tier] * (1 + 0.12 * (target - tierStart)) * upgradeItemScale));
        items.put(ids[tier], count);
        if (target % 5 == 0) {
            for (Map.Entry<String, Integer> e : upgradeMilestoneItems.entrySet()) items.merge(e.getKey(), e.getValue() * (target / 5), Integer::sum);
        }
        if (target >= maxLevel) {
            for (Map.Entry<String, Integer> e : upgradeMaxLevelItems.entrySet()) items.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return new Cost(xp, items);
    }
}
