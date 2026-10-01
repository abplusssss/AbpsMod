package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ice: slows and freezes, then shatters what it froze. */
public final class Cryomancer extends AttributeClass {

    // Cues the client draws (see SigCryo)
    static final int CUE_CHILL = 12, CUE_SHATTER = 14, CUE_FREEZE = 15;

    private static final int ICE = 0x80DEEA, DEEP = 0x2962FF;
    private final Map<UUID, int[]> hitCounts = new HashMap<>();

    @Override public String id() { return "cryomancer"; }
    @Override public String name() { return "Cryomancer"; }
    @Override public String color() { return "#B3E5FC"; }
    @Override public String color2() { return "#2962FF"; }
    @Override public Item icon() { return Items.BLUE_ICE; }
    @Override public String symbol() { return "❄"; }
    @Override public String tagline() { return "Slow them down. Then shatter them."; }
    @Override public String mastery() { return "Every 4th melee hit on the same enemy freezes it solid for 1s."; }

    private double chillChance(int lvl) { return lerp(lvl, 0.25, 0.40); }
    private double coldBonus(int lvl) { return lerp(lvl, 0.10, 0.20); }
    private double lanceDamage(int lvl) { return lerp(lvl, 8, 11); }
    private double spikeDamage(int lvl) { return lerp(lvl, 6, 9); }
    private double novaDamage(int lvl) { return lerp(lvl, 5, 8); }
    private double novaRoot(int lvl) { return lerp(lvl, 1.5, 2.0); }
    private double blizzardTick(int lvl) { return lerp(lvl, 0.8, 1.2); }
    private double zeroHit(int lvl) { return lerp(lvl, 8, 10); }
    private double zeroShatter(int lvl) { return lerp(lvl, 10, 14); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                pct(chillChance(lvl)) + " chance for melee hits to chill the target (Slowness I for 2s)",
                "Deal +" + pct(coldBonus(lvl)) + " damage to slowed or frozen enemies",
                "Enemies that hit you in melee are chilled",
                "Immune to freezing and to powder snow",
                "Walk speed x1.15 on ice and snow");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Take 30% more fire and lava damage",
                "Deal 15% less damage in the Nether");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Frost Lance";
            case 2 -> "Glacial Spikes";
            case 3 -> "Frost Nova";
            case 4 -> "Blizzard";
            default -> "Absolute Zero";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Throw a lance of ice at the enemy you look at for " + num(lanceDamage(lvl)) + " damage and Slowness II for 3s.";
            case 2 -> "Ice spikes burst out of the ground in a line 10 blocks long. Enemies on it take " + num(spikeDamage(lvl)) + " damage and are stuck for 1s.";
            case 3 -> "Freeze every enemy within 6 blocks for " + num(novaRoot(lvl)) + "s and deal " + num(novaDamage(lvl)) + " damage.";
            case 4 -> "Call a blizzard where you look for 6s. Enemies inside take " + num(blizzardTick(lvl) * 2) + " damage a second and are slowed.";
            default -> "Freeze every enemy within 12 blocks for 3s and deal " + num(zeroHit(lvl)) + " damage. When the ice breaks it shatters for "
                    + num(zeroShatter(lvl)) + " more.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 10;
            case 2 -> 18;
            case 3 -> 22;
            default -> 40;
        };
    }

    @Override
    protected double groundAimRange(int idx) {
        return idx == 4 ? 30 : 0;
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return switch (idx) {
            case 3 -> (int) (novaRoot(d.level) * 20);
            case 4 -> 120;
            case ULTIMATE -> 60;
            default -> 0;
        };
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:snowball", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:heart_of_the_sea"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{40, 24, 12, 1};
    }

    // ---- Passives ----

    private static boolean onIce(ServerPlayer p) {
        BlockState under = p.level().getBlockState(BlockPos.containing(p.position().add(0, -0.2, 0)));
        BlockState at = p.level().getBlockState(p.blockPosition());
        return under.is(BlockTags.ICE) || under.is(Blocks.SNOW_BLOCK) || under.is(Blocks.POWDER_SNOW) || at.is(Blocks.SNOW);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        Mods.toggle(p, onIce(p), Attributes.MOVEMENT_SPEED, "cryo_ice", 0.15, Mods.MULT);
        if (p.getTicksFrozen() > 0) p.setTicksFrozen(0);
    }

    private static boolean cold(LivingEntity e) {
        return e.hasEffect(MobEffects.SLOWNESS) || e.getTicksFrozen() > 0 || Targets.isRooted(e);
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        double m = cold(victim) ? 1 + coldBonus(d.level) : 1;
        if (p.level().dimension() == Level.NETHER) m *= 0.85;
        return m;
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        if (source.is(DamageTypes.FREEZE)) return 0;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA)) return 1.3;
        return 1;
    }

    private void chill(ServerPlayer p, LivingEntity t) {
        t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
        if (!cue(p, CUE_CHILL, p.position(), t.position().add(0, t.getBbHeight() * 0.5, 0), p, t, 0)) {
            Vfx.ring(level(p), t.position().add(0, 0.3, 0), new Vec3(0, 1, 0), 0.4, 1.0, 12, Vfx.tint(ICE), 0.06f, 8, ICE);
        }
    }

    /** Holds an enemy in ice for a while. */
    private void freeze(ServerPlayer p, LivingEntity t, int ticks) {
        Targets.root(t, ticks);
        t.setTicksFrozen(Math.max(t.getTicksFrozen(), 140));
        if (!cue(p, CUE_FREEZE, p.position(), t.position().add(0, t.getBbHeight() * 0.5, 0), p, t, ticks)) {
            Vfx.pillar(level(p), t.position(), 0.6, t.getBbHeight() + 0.4, Vfx.tint(ICE), 3, Math.max(2, ticks - 6), 3, ICE);
        }
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee()) return;
        if (rand() < chillChance(d.level)) chill(p, victim);
        if (!mastered(d)) return;
        int[] c = hitCounts.computeIfAbsent(p.getUUID(), k -> new int[2]);
        if (c[0] != victim.getId()) {
            c[0] = victim.getId();
            c[1] = 0;
        }
        if (++c[1] >= 4) {
            c[1] = 0;
            freeze(p, victim, 20);
        }
    }

    @Override
    public void afterDamaged(ServerPlayer p, PlayerData d, DamageSource source, float taken) {
        if (source.getDirectEntity() instanceof LivingEntity attacker && Targets.isEnemy(p, attacker) && attacker.distanceToSqr(p) < 16) {
            chill(p, attacker);
        }
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        hitCounts.remove(p.getUUID());
        Mods.remove(p, Attributes.MOVEMENT_SPEED, "cryo_ice");
    }

    // ---- Abilities ----

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 28);
        if (t == null) {
            noTarget(p, 28);
            return false;
        }
        ServerLevel level = level(p);
        castTarget = t;
        used(p, 1);
        double dmg = lanceDamage(d.level);
        // The lance takes a moment to fly, so the hit lands when the effect arrives
        int flight = (int) Math.max(2, Math.min(8, p.distanceTo(t) / 4));
        dev.abps.util.Tasks.later(flight, () -> {
            if (!t.isAlive() || p.isRemoved()) return;
            Targets.damage(t, dmg, p);
            t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            t.setTicksFrozen(Math.max(t.getTicksFrozen(), 100));
            Fx.sound(level, t, SoundEvents.GLASS_BREAK, 0.8f, 1.4f);
        });
        Vfx.beam(level, p.getEyePosition(), t.position().add(0, t.getBbHeight() * 0.5, 0), 0.1f, Vfx.tint(ICE), 8, ICE);
        Fx.sound(level, p, SoundEvents.TRIDENT_THROW.value(), 0.8f, 1.6f);
        Fx.sound(level, p, SoundEvents.PLAYER_HURT_FREEZE, 0.6f, 1.5f);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 start = p.position();
        double dmg = spikeDamage(d.level);
        used(p, 2);
        Fx.sound(level, p, SoundEvents.GLASS_BREAK, 1f, 0.6f);
        Vec3 fdir = dir;
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        // The spikes run outward one step every tick, hitting what they reach
        dev.abps.util.Tasks.repeat(10, 1, step -> {
            if (p.isRemoved()) return;
            Vec3 at = start.add(fdir.scale(step + 1));
            for (LivingEntity e : Targets.enemiesNear(p, at, 1.8)) {
                if (!hit.add(e)) continue;
                Targets.damage(e, dmg, p);
                Targets.root(e, 20);
                Targets.velocity(e, new Vec3(0, 0.35, 0));
            }
            if (step % 3 == 0) Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.7f, 0.8f + step * 0.05f);
            Vfx.pillar(level, at, 0.25, 1.4, Vfx.tint(ICE), 2, 8, 4, ICE);
        });
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        List<LivingEntity> targets = Targets.enemiesNear(p, p.position(), 6);
        if (targets.isEmpty()) {
            fail(p, "No enemies within 6 blocks.");
            return false;
        }
        ServerLevel level = level(p);
        int ticks = (int) (novaRoot(d.level) * 20);
        used(p, 3);
        for (LivingEntity t : targets) {
            Targets.damage(t, novaDamage(d.level), p);
            freeze(p, t, ticks);
        }
        Vfx.groundRing(level, p.position(), 0.5, 6.5, 26, Vfx.tint(ICE), 0.14f, 12, ICE);
        Fx.sound(level, p, SoundEvents.GLASS_BREAK, 1f, 0.5f);
        Fx.sound(level, p, SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.7f);
        Fx.shakeNear(level, p.position(), 8, 4, 0.3f);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 at = Targets.groundPoint(p, 30);
        double dmg = blizzardTick(d.level);
        used(p, 4);
        Fx.sound(level, at, SoundEvents.ELYTRA_FLYING, 0.6f, 1.6f);
        dev.abps.util.Tasks.repeat(12, 10, step -> {
            if (p.isRemoved()) return;
            for (LivingEntity e : Targets.enemiesNear(p, at, 6)) {
                if (Math.abs(e.getY() - at.y) > 5) continue;
                Targets.damage(e, dmg, p);
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1));
                e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 20, e.getTicksFrozen() + 25));
            }
            if (step % 2 == 0) Fx.sound(level, at, SoundEvents.POWDER_SNOW_STEP, 1f, 0.6f);
            Vfx.vortex(level, at, 5.5, 10, Vfx.tint(0xE1F5FE), 0.12f, 10, 0.8, ICE);
        });
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        List<LivingEntity> targets = Targets.enemiesNear(p, p.position(), 12);
        if (targets.isEmpty()) {
            fail(p, "No enemies within 12 blocks.");
            return false;
        }
        ServerLevel level = level(p);
        double shatter = zeroShatter(d.level);
        used(p, ULTIMATE);
        for (LivingEntity t : targets) {
            Targets.damage(t, zeroHit(d.level), p);
            freeze(p, t, 60);
        }
        Fx.sound(level, p, SoundEvents.GLASS_BREAK, 1f, 0.4f);
        Fx.sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
        Fx.shakeNear(level, p.position(), 14, 8, 0.5f);
        Vfx.groundRing(level, p.position(), 1, 12, 36, Vfx.tint(ICE), 0.2f, 18, ICE);
        dev.abps.util.Tasks.later(60, () -> {
            if (p.isRemoved()) return;
            for (LivingEntity t : targets) {
                if (!t.isAlive()) continue;
                Targets.damage(t, shatter, p);
                if (!cue(p, CUE_SHATTER, p.position(), t.position().add(0, t.getBbHeight() * 0.5, 0), p, t, 0)) {
                    Vfx.burst(level, t.position().add(0, 1, 0), Vfx.tint(ICE), 14, 0.25, 0.14f, 16, ICE);
                }
                Fx.sound(level, t, SoundEvents.GLASS_BREAK, 1f, 0.8f);
            }
        });
        return true;
    }
}
