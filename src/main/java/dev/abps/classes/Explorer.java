package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.FakeBlocks;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Gatherer. Travel and treasure: run far, fall safely, and see chests and spawners through walls. */
public final class Explorer extends AttributeClass {

    static final int CUE_PING = 11;
    private static final int RADIUS = 32, LIMIT = 60;

    @Override public String id() { return "explorer"; }
    @Override public String name() { return "Explorer"; }
    @Override public String color() { return "#FFCA28"; }
    @Override public String color2() { return "#26A69A"; }
    @Override public Item icon() { return Items.COMPASS; }
    @Override public String symbol() { return "✧"; }
    @Override public String tagline() { return "Somewhere out there is treasure."; }
    @Override public String mastery() { return "Treasure Sense also finds ancient debris and diamond ore."; }
    @Override public Role role() { return Role.GATHERER; }

    private double speed(int lvl) { return lerp(lvl, 0.10, 0.25); }
    private double safeFall(int lvl) { return lerp(lvl, 4, 12); }
    private double senseTime(int lvl) { return lerp(lvl, 20, 40); }
    private double expeditionTime(int lvl) { return lerp(lvl, 60, 120); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Move " + pct(speed(lvl)) + " faster",
                "Fall " + num(safeFall(lvl)) + " more blocks before it hurts",
                "Get hungry 35% slower",
                "See in the dark while underground (below y 40)",
                "+1 Luck (better chest and fishing loot)");
    }

    @Override
    public List<String> negatives() {
        return List.of("Gatherer: deal 25% less damage to players", "Take 15% more damage from monsters");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Treasure Sense";
            case 2 -> "Grapple Leap";
            case 3 -> "Scout";
            case 4 -> "Campfire";
            default -> "Expedition";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Chests, barrels, spawners and vaults within " + RADIUS + " blocks glow through walls for " + num(senseTime(lvl)) + "s. Only you see it.";
            case 2 -> "Launch yourself where you look. No fall damage for a few seconds after.";
            case 3 -> "Every mob within 48 blocks glows for 12s, so you see what's waiting before you walk in.";
            case 4 -> "Make camp: you and everyone near you get Regeneration II for 8s and fill some hunger.";
            default -> "For " + num(expeditionTime(lvl)) + "s: Speed II, Haste II, Night Vision, +3 Luck and no hunger.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 45;
            case 2 -> 8;
            case 3 -> 30;
            default -> 50;
        };
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:paper", "minecraft:compass", "minecraft:spyglass", "minecraft:recovery_compass"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{32, 4, 2, 1};
    }

    // ---- passives ----

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MOVEMENT_SPEED, "explorer_speed", speed(d.level), Mods.MULT);
        Mods.set(p, Attributes.SAFE_FALL_DISTANCE, "explorer_fall", safeFall(d.level), Mods.ADD);
        Mods.set(p, Attributes.LUCK, "explorer_luck", 1, Mods.ADD);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (d.tickCount % 20 == 0 && p.getY() < 40) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 15, 0, true, false, false));
        if (d.buff("expedition")) p.getFoodData().setFoodLevel(20);
    }

    @Override
    public double hungerMultiplier() {
        return 0.65;
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL) && now() < d.noFallUntil) return 0;
        return source.getEntity() instanceof net.minecraft.world.entity.monster.Enemy ? 1.15 : 1;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        clearHighlights(p, d);
    }

    // ---- abilities ----

    private static boolean treasure(BlockState s, boolean mastered) {
        if (s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.SPAWNER) || s.is(Blocks.TRIAL_SPAWNER)
                || s.is(Blocks.VAULT) || s.is(Blocks.SUSPICIOUS_SAND) || s.is(Blocks.SUSPICIOUS_GRAVEL)) return true;
        return mastered && (s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE));
    }

    private static int color(BlockState s) {
        if (s.is(Blocks.SPAWNER) || s.is(Blocks.TRIAL_SPAWNER)) return 0xFF5252;
        if (s.is(Blocks.VAULT)) return 0x80DEEA;
        if (s.is(Blocks.ANCIENT_DEBRIS)) return 0x8D6E63;
        if (s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return 0x4DD0E1;
        if (s.is(Blocks.SUSPICIOUS_SAND) || s.is(Blocks.SUSPICIOUS_GRAVEL)) return 0xFFE082;
        return 0xFFCA28;
    }

    private void clearHighlights(ServerPlayer p, PlayerData d) {
        if (d.highlights.isEmpty()) return;
        FakeBlocks.hide(p, d.highlights.values().stream().mapToInt(Integer::intValue).toArray());
        d.highlights.clear();
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        clearHighlights(p, d);
        ServerLevel level = level(p);
        BlockPos c = p.blockPosition();
        int minY = Math.max(level.getMinY(), c.getY() - 16), maxY = Math.min(level.getMaxY(), c.getY() + 16);
        boolean m = mastered(d);
        int found = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        outer:
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = minY; y <= maxY; y++) {
                    at.set(c.getX() + x, y, c.getZ() + z);
                    BlockState s = level.getBlockState(at);
                    if (!treasure(s, m)) continue;
                    d.highlights.put(at.asLong(), FakeBlocks.show(p, at.immutable(), s, color(s)));
                    if (++found >= LIMIT) break outer;
                }
            }
        }
        if (found == 0) {
            fail(p, "No treasure within " + RADIUS + " blocks.");
            return false;
        }
        Tasks.later((long) (senseTime(d.level) * 20), () -> clearHighlights(p, d));
        used(p, 1);
        Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
        dev.abps.AbpsMod.service().actionBar(p, gradient("<bold>✦ Treasure Sense</bold>") + " <gray>found <white>" + found + "</white>.");
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        Vec3 look = p.getLookAngle();
        used(p, 2);
        Targets.velocity(p, look.scale(1.8).add(0, 0.55, 0));
        d.noFallUntil = now() + 5000;
        Fx.sound(level(p), p, SoundEvents.GOAT_LONG_JUMP, 1f, 1f);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48), e -> e != p && e.isAlive());
        if (mobs.isEmpty()) {
            fail(p, "Nothing alive within 48 blocks.");
            return false;
        }
        used(p, 3);
        for (LivingEntity e : mobs) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false));
        Fx.sound(level, p, SoundEvents.BELL_RESONATE, 1f, 1.6f);
        dev.abps.AbpsMod.service().actionBar(p, gradient("<bold>✦ Scout</bold>") + " <gray>spotted <white>" + mobs.size() + "</white>.");
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        used(p, 4);
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(8))) {
            o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
            o.getFoodData().setFoodLevel(Math.min(20, o.getFoodData().getFoodLevel() + 6));
        }
        Fx.sound(level, p, SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.6f, 0.6f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        int ticks = (int) (expeditionTime(d.level) * 20);
        d.setBuff("expedition", ticks * 50L);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0));
        p.addEffect(new MobEffectInstance(MobEffects.LUCK, ticks, 2));
        used(p, ULTIMATE);
        Fx.sound(level(p), p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        return true;
    }

    /** Gatherer: the ultimate fills as you travel and dig. */
    @Override
    public double gatherCharge(BlockState state) {
        return 0.002;
    }
}
