package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Gatherer. Water: nets full of fish and treasure, fast swimming and breathing underwater. */
public final class Angler extends AttributeClass {

    static final int CUE_NET = 11, CUE_PULL = 12;
    private static final int SEA = 0x4FC3F7, DEEP = 0x0277BD;

    @Override public String id() { return "angler"; }
    @Override public String name() { return "Angler"; }
    @Override public String color() { return "#4FC3F7"; }
    @Override public String color2() { return "#0277BD"; }
    @Override public Item icon() { return Items.FISHING_ROD; }
    @Override public String symbol() { return "≈"; }
    @Override public String tagline() { return "The sea gives to those who ask."; }
    @Override public String mastery() { return "Nets can pull up a Heart of the Sea or a Trident."; }
    @Override public Role role() { return Role.GATHERER; }

    private double luck(int lvl) { return lerp(lvl, 1, 4); }
    private double swim(int lvl) { return lerp(lvl, 0.3, 0.8); }
    private int netRolls(int lvl) { return (int) Math.round(lerp(lvl, 2, 5)); }
    private double blessTime(int lvl) { return lerp(lvl, 40, 70); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "+" + num(luck(lvl)) + " Luck (better fishing loot)",
                "Breathe underwater",
                "Swim " + pct(swim(lvl)) + " faster",
                "Fishing rods never break",
                "Extra XP from everything you catch with a net");
    }

    @Override
    public List<String> negatives() {
        return List.of("Gatherer: deal 25% less damage to players", "Take 20% more fire damage");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Cast Net";
            case 2 -> "Tidal Pull";
            case 3 -> "Riptide Dash";
            case 4 -> "Sea's Blessing";
            default -> "Deep Haul";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Throw a net into water you look at (up to 16 blocks) and haul up " + netRolls(lvl) + " catches: fish, junk or treasure.";
            case 2 -> "Pull every dropped item and every mob within 12 blocks toward you.";
            case 3 -> "Dash forward. Much further when you're in water or rain.";
            case 4 -> "Conduit Power, Dolphin's Grace and +3 Luck for " + num(blessTime(lvl)) + "s.";
            default -> "A huge net: 12 catches with much better odds of treasure.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 12;
            case 2 -> 15;
            case 3 -> 8;
            default -> 90;
        };
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected double groundAimRange(int idx) {
        return idx == 1 || idx == ULTIMATE ? 16 : 0;
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:cod", "minecraft:salmon", "minecraft:prismarine_crystals", "minecraft:nautilus_shell"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{32, 32, 16, 2};
    }

    // ---- loot ----

    private record Catch(Item item, int weight, int min, int max) {
    }

    private static final Catch[] FISH = {
            new Catch(Items.COD, 50, 1, 3), new Catch(Items.SALMON, 30, 1, 2), new Catch(Items.TROPICAL_FISH, 6, 1, 1),
            new Catch(Items.PUFFERFISH, 6, 1, 1)};
    private static final Catch[] JUNK = {
            new Catch(Items.KELP, 10, 2, 5), new Catch(Items.LILY_PAD, 8, 1, 2), new Catch(Items.INK_SAC, 8, 1, 3),
            new Catch(Items.BONE, 8, 1, 3), new Catch(Items.STRING, 8, 1, 3), new Catch(Items.LEATHER, 6, 1, 2), new Catch(Items.BOWL, 4, 1, 1),
            new Catch(Items.STICK, 4, 1, 3)};
    private static final Catch[] TREASURE = {
            new Catch(Items.NAME_TAG, 6, 1, 1), new Catch(Items.SADDLE, 6, 1, 1), new Catch(Items.NAUTILUS_SHELL, 8, 1, 1),
            new Catch(Items.PRISMARINE_CRYSTALS, 10, 2, 5), new Catch(Items.PRISMARINE_SHARD, 10, 2, 5), new Catch(Items.EMERALD, 8, 1, 3),
            new Catch(Items.GOLD_INGOT, 8, 1, 3), new Catch(Items.DIAMOND, 3, 1, 1), new Catch(Items.EXPERIENCE_BOTTLE, 8, 2, 4),
            new Catch(Items.GLOW_INK_SAC, 6, 1, 3)};

    private static ItemStack pick(Catch[] table) {
        int total = 0;
        for (Catch c : table) total += c.weight;
        int r = (int) (rand() * total);
        for (Catch c : table) {
            r -= c.weight;
            if (r < 0) return new ItemStack(c.item, c.min + (int) (rand() * (c.max - c.min + 1)));
        }
        return new ItemStack(Items.COD);
    }

    /** One catch. Luck pushes the odds from fish and junk toward treasure. */
    private ItemStack roll(ServerPlayer p, PlayerData d, double treasureBoost) {
        double luck = Mods.value(p, Attributes.LUCK);
        double treasure = 0.06 + luck * 0.025 + treasureBoost, junk = Math.max(0.03, 0.12 - luck * 0.02);
        if (mastered(d) && rand() < 0.01 + treasureBoost * 0.05) return new ItemStack(rand() < 0.5 ? Items.HEART_OF_THE_SEA : Items.TRIDENT);
        double r = rand();
        if (r < treasure) return pick(TREASURE);
        if (r < treasure + junk) return pick(JUNK);
        return pick(FISH);
    }

    /** Throws the catches out of the water at the target toward the player. */
    private void haul(ServerPlayer p, PlayerData d, Vec3 water, int rolls, double boost) {
        ServerLevel level = level(p);
        for (int k = 0; k < rolls; k++) {
            ItemStack s = roll(p, d, boost);
            if (s.isEmpty()) continue;
            int delay = 6 + k * 2;
            Tasks.later(delay, () -> {
                if (p.isRemoved()) return;
                ItemEntity e = new ItemEntity(level, water.x, water.y + 0.5, water.z, s);
                Vec3 v = p.position().subtract(water);
                e.setDeltaMovement(v.x * 0.1, Math.sqrt(Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)) * 0.08 + 0.3, v.z * 0.1);
                level.addFreshEntity(e);
                Fx.sound(level, water, SoundEvents.GENERIC_SPLASH, 0.6f, 1.2f);
            });
        }
        ExperienceOrb.award(level, p.position(), rolls * 2);
        // Gatherer: hauling fills the ultimate
        dev.abps.AbpsMod.service().addGatherCharge(p, d, rolls * 0.025);
    }

    private BlockHitResult waterHit(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        return p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, p));
    }

    private boolean isWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    // ---- passives ----

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.LUCK, "angler_luck", luck(d.level), Mods.ADD);
        Mods.set(p, Attributes.WATER_MOVEMENT_EFFICIENCY, "angler_swim", swim(d.level), Mods.ADD);
        Mods.set(p, Attributes.OXYGEN_BONUS, "angler_air", 1024, Mods.ADD);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (p.isUnderWater() && p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(p.getMaxAirSupply());
    }

    @Override
    public boolean saveDurability(ServerPlayer p, PlayerData d, ItemStack stack) {
        return stack.is(Items.FISHING_ROD);
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return source.is(DamageTypeTags.IS_FIRE) ? 1.2 : 1;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
    }

    // ---- abilities ----

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        BlockHitResult hit = waterHit(p, 16);
        ServerLevel level = level(p);
        if (hit.getType() != HitResult.Type.BLOCK || !isWater(level, hit.getBlockPos())) {
            fail(p, "Look at water within 16 blocks.");
            return false;
        }
        Vec3 water = Vec3.atBottomCenterOf(hit.getBlockPos()).add(0, 0.9, 0);
        castAim = water;
        used(p, 1);
        Fx.sound(level, p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.8f);
        haul(p, d, water, netRolls(d.level), 0);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        AABB box = p.getBoundingBox().inflate(12);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        List<LivingEntity> mobs = Targets.enemiesNear(p, p.position(), 12);
        if (items.isEmpty() && mobs.isEmpty()) {
            fail(p, "Nothing to pull within 12 blocks.");
            return false;
        }
        used(p, 2);
        for (Entity e : items) {
            Vec3 v = p.position().add(0, 0.5, 0).subtract(e.position());
            e.setDeltaMovement(v.scale(0.18).add(0, 0.15, 0));
        }
        for (LivingEntity e : mobs) {
            Vec3 v = p.position().subtract(e.position());
            Targets.velocity(e, v.normalize().scale(Math.min(1.4, v.length() * 0.14)).add(0, 0.25, 0));
        }
        Fx.sound(level, p, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, 1.2f);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        boolean wet = p.isInWaterOrRain();
        Vec3 look = p.getLookAngle();
        double power = wet ? 2.4 : 1.2;
        used(p, 3);
        Targets.velocity(p, look.scale(power).add(0, wet ? 0.1 : 0.35, 0));
        d.noFallUntil = now() + 3000;
        Fx.sound(level(p), p, SoundEvents.DOLPHIN_JUMP, 1f, 1f);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        int ticks = (int) (blessTime(d.level) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, ticks, 0));
        p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, ticks, 0));
        p.addEffect(new MobEffectInstance(MobEffects.LUCK, ticks, 2));
        used(p, 4);
        Fx.sound(level(p), p, SoundEvents.BEACON_ACTIVATE, 1f, 1.4f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        BlockHitResult hit = waterHit(p, 16);
        ServerLevel level = level(p);
        if (hit.getType() != HitResult.Type.BLOCK || !isWater(level, hit.getBlockPos())) {
            fail(p, "Look at water within 16 blocks.");
            return false;
        }
        Vec3 water = Vec3.atBottomCenterOf(hit.getBlockPos()).add(0, 0.9, 0);
        castAim = water;
        used(p, ULTIMATE);
        Fx.sound(level, p, SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 1.4f);
        haul(p, d, water, 12, 0.25);
        return true;
    }
}
