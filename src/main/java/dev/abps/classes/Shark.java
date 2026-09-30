package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import dev.abps.util.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything underwater: endless breath, fast digging, a torpedo dash, a water cannon, a whirlpool, jaws and a tsunami. */
public final class Shark extends AttributeClass {

    private static final int CANNON_RANGE = 26;
    private static final int WOUNDED_MARK_TICKS = 80;
    /** Prey marked by Blood Scent, mapped to when the mark runs out. Marked prey take extra damage from sharks. */
    private static final Map<UUID, Long> MARKS = new HashMap<>();

    @Override public String id() { return "shark"; }
    @Override public String name() { return "Shark"; }
    @Override public String color() { return "#0288D1"; }
    @Override public String color2() { return "#4DD0E1"; }
    @Override public Item icon() { return Items.NAUTILUS_SHELL; }
    @Override public String symbol() { return "≋"; }
    @Override public String tagline() { return "The ocean is your hunting ground."; }
    @Override public String mastery() { return "Guardians, drowned and other sea mobs ignore you."; }

    private double digBonus(int lvl) { return lerp(lvl, 1.0, 3.0); }
    private double wetDamage(int lvl) { return lerp(lvl, 0.15, 0.35); }
    private double dashDamage(int lvl) { return lerp(lvl, 6, 10); }
    private double scentTime(int lvl) { return lerp(lvl, 8, 14); }
    private double whirlDamage(int lvl) { return lerp(lvl, 2, 3.5); }
    private double frenzyTime(int lvl) { return lerp(lvl, 10, 16); }
    private double frenzySteal(int lvl) { return lerp(lvl, 0.25, 0.40); }
    private double breachDamage(int lvl) { return lerp(lvl, 12, 18); }
    private double cannonDamage(int lvl) { return lerp(lvl, 12, 18); }
    private double tsunamiDamage(int lvl) { return lerp(lvl, 18, 28); }

    private static boolean wet(ServerPlayer p) {
        return p.isInWaterOrRain();
    }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Infinite breath underwater. Drowning can't hurt you",
                "Break blocks underwater at " + mult(1 + digBonus(lvl)) + " speed, even while floating",
                "Swim like a dolphin: full water movement and Dolphin's Grace",
                "Night vision while underwater",
                "+" + pct(wetDamage(lvl)) + " damage while in water or rain",
                "Hits on prey under 40% health in water mark it with Glowing",
                "Regenerate 1/2 heart every 3s while in water",
                "Sneak underwater to sense mobs within 16 blocks (Electroreception)",
                "Leaving the water fast launches you into a dolphin leap with no fall damage");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Move 15% slower away from water and rain",
                "Deal 15% less damage away from water and rain",
                "Take 25% more fire damage");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Riptide";
            case 2 -> "Hydro Cannon";
            case 3 -> "Maelstrom";
            case 4 -> "Apex Breach";
            default -> "Tsunami";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Needs water. Rocket forward like a torpedo, biting everything you hit for " + num(dashDamage(lvl))
                    + " damage and leaving it bleeding. Every enemy you hit cuts 1.5s off this cooldown.";
            case 2 -> "Fire a piercing jet of water " + CANNON_RANGE + " blocks. Everything in the line takes " + num(cannonDamage(lvl))
                    + " damage, is blasted back, glows, and is marked for " + num(scentTime(lvl)) + "s. Marked prey take +25% damage from you.";
            case 3 -> "Open a whirlpool where you look for 5s while fins circle it. Enemies within 8 blocks are spun in and take "
                    + num(whirlDamage(lvl)) + " damage every half second. When it ends it implodes for double damage.";
            case 4 -> "Leap out of the water, then crash down as a giant set of jaws snaps shut. The shockwave hits everything within 10 blocks for "
                    + num(breachDamage(lvl)) + " damage (+50% to enemies in water), launches and slows them, and starts a Frenzy: Strength, Speed, Haste and "
                    + pct(frenzySteal(lvl)) + " lifesteal for " + num(frenzyTime(lvl)) + "s. Kills add 2s.";
            default -> "Summon a tsunami. A wall of water rises in front of you and thunders 32 blocks forward, hitting everything for "
                    + num(tsunamiDamage(lvl)) + " damage and carrying it along before it crashes.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 12;
            case 2 -> 22;
            case 3 -> 45;
            default -> 50;
        };
    }

    // ---- Passives ----
    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.SUBMERGED_MINING_SPEED, "shark_dig", 0.8 + digBonus(d.level), Mods.ADD);
        Mods.set(p, Attributes.WATER_MOVEMENT_EFFICIENCY, "shark_swim", 1.0, Mods.ADD);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        boolean under = p.isUnderWater();
        boolean inWater = p.isInWater();
        if (under && p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(p.getMaxAirSupply());
        if (inWater) p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, false));
        if (under) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));

        // Vanilla mines 5x slower when not standing on something. Sharks don't care.
        Mods.toggle(p, under && !p.onGround(), Attributes.BLOCK_BREAK_SPEED, "shark_float", 4, Mods.MULT);
        Mods.toggle(p, !wet(p), Attributes.MOVEMENT_SPEED, "shark_dry", -0.15, Mods.MULT);

        // Ocean renewal
        if (inWater && d.tickCount % 12 == 0 && p.getHealth() < maxHp(p)) heal(p, 1);

        // Electroreception: sneak underwater to feel every mob nearby
        if (under && p.isShiftKeyDown() && d.tickCount % 4 == 0) {
            for (LivingEntity e : Targets.enemiesNear(p, p.position(), 16)) {
                if (!(e instanceof net.minecraft.server.level.ServerPlayer)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
            }
        }

        // Dolphin leap: shooting out of the water gives an extra boost
        if (inWater) {
            d.setBuff("shark_wet", 450);
        } else if (d.buff("shark_wet") && !d.buff("shark_leap") && !p.onGround() && p.getDeltaMovement().y > 0.15) {
            d.setBuff("shark_leap", 2500);
            Vec3 look = p.getLookAngle();
            Targets.velocity(p, p.getDeltaMovement().add(look.x * 0.5, 0.75, look.z * 0.5));
            d.noFallUntil = now() + 3000;
            ServerLevel lv = level(p);
            Vfx.groundRing(lv, p.position(), 0.5, 3, 20, Vfx.tint(rgb2()), 0.12f, 10, rgb2());
            Vfx.burst(lv, p.position().add(0, 0.5, 0), Vfx.tint(rgb()), 14, 0.2, 0.16f, 16, rgb());
            Fx.sound(lv, p, SoundEvents.DOLPHIN_JUMP, 1f, 1f);
        }

        if (d.buff("frenzy") && d.tickCount % 2 == 0) {
            ServerLevel level = level(p);
            Fx.burst(level, Fx.dust(0xB71C1C, 1f), p.position().add(0, 1, 0), 3, 0.4, 0);
            if (inWater) Fx.burst(level, ParticleTypes.BUBBLE_POP, p.position().add(0, 1, 0), 3, 0.4, 0.05);
        }
    }

    @Override
    public boolean allowDamage(ServerPlayer p, PlayerData d, DamageSource source) {
        return !source.is(DamageTypes.DROWN);
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return source.is(DamageTypeTags.IS_FIRE) ? 1.25 : 1;
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        double m = wet(p) ? 1 + wetDamage(d.level) : 0.85;
        Long mark = MARKS.get(victim.getUUID());
        if (mark != null) {
            if (mark > now()) m *= 1.25;
            else MARKS.remove(victim.getUUID());
        }
        return m;
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (d.buff("frenzy")) {
            heal(p, dealt * frenzySteal(d.level));
            Fx.burst(level(p), Fx.dust(0xB71C1C, 0.9f), victim.position().add(0, victim.getBbHeight() / 2, 0), 6, 0.3, 0);
        }
        if (victim.isInWater() && victim.isAlive() && victim.getHealth() < victim.getMaxHealth() * 0.4f) {
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, WOUNDED_MARK_TICKS, 0));
        }
    }

    @Override
    public void onBlockAttack(ServerPlayer p, PlayerData d, BlockState state) {
        Mods.toggle(p, p.isUnderWater() && !p.onGround(), Attributes.BLOCK_BREAK_SPEED, "shark_float", 4, Mods.MULT);
    }

    @Override
    public boolean ignoredBy(ServerPlayer p, PlayerData d, Mob mob) {
        if (!mastered(d)) return false;
        EntityType<?> t = mob.getType();
        return t == EntityTypes.GUARDIAN || t == EntityTypes.ELDER_GUARDIAN || t == EntityTypes.DROWNED;
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        MARKS.remove(victim.getUUID());
        if (!d.buff("frenzy")) return;
        long left = d.buffLeft("frenzy");
        long add = Math.min(2000, Math.max(0, 30_000 - left));
        if (add <= 0) return;
        d.setBuff("frenzy", left + add);
        int ticks = (int) ((left + add) / 50);
        p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0));
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
        heal(p, 4);
        ServerLevel level = level(p);
        Vec3 from = victim.position().add(0, victim.getBbHeight() / 2, 0);
        Vfx.burst(level, from, Vfx.tint(0xB71C1C), 16, 0.18, 0.16f, 16, 0xB71C1C);
        Vfx.beam(level, from, p.getEyePosition(), 0.12f, Vfx.tint(0xB71C1C), 8, 0xFF1744);
        dev.abps.AbpsMod.service().actionBar(p, gradient("<bold>≋ Blood in the water</bold>") + " <gray>+2s Frenzy");
    }

    // ---- Ability 1: Riptide ----
    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        if (!p.isInWater()) {
            fail(p, "Riptide needs water. Get in the water first.");
            return false;
        }
        ServerLevel level = level(p);
        Vec3 dir = p.getLookAngle().normalize();
        double dmg = dashDamage(d.level);
        Set<UUID> hit = new HashSet<>();
        d.noFallUntil = now() + 3000;
        Fx.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_3, 1f, 0.9f);
        Fx.sound(level, p, SoundEvents.DOLPHIN_JUMP, 1f, 0.7f);
        Tasks.repeat(9, 1, i -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Targets.velocity(p, dir.scale(1.7));
            Vec3 body = p.position().add(0, p.getBbHeight() / 2, 0);
            // A tunnel of rings and a streak behind you
            if (i % 2 == 0) Vfx.ring(level, body.add(dir.scale(1.5)), dir, 0.5, 2.4, 16, Vfx.tint(rgb2()), 0.1f, 8, rgb2());
            Vfx.beam(level, body.subtract(dir.scale(4)), body, 0.28f, Vfx.tint(rgb()), 7, rgb());
            Fx.burst(level, ParticleTypes.BUBBLE_POP, body, 6, 0.4, 0.05);
            for (LivingEntity e : Targets.enemiesNear(p, body, 2.2)) {
                if (!hit.add(e.getUUID())) continue;
                Targets.damage(e, dmg, p);
                Vec3 side = e.position().subtract(p.position());
                Targets.velocity(e, dir.scale(0.6).add(side.normalize().scale(0.5)).add(0, 0.35, 0));
                // A bite: teeth snap shut around it, then it bleeds
                Vec3 mid = e.position();
                Vfx.jaws(level, mid, 1.1, 8, 1.3, Vfx.WHITE, 0xB71C1C);
                Vfx.burst(level, mid.add(0, e.getBbHeight() / 2, 0), Vfx.tint(0xB71C1C), 12, 0.2, 0.14f, 16, 0xB71C1C);
                Fx.sound(level, e, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.7f);
                Tasks.repeat(3, 20, b -> {
                    if (e.isAlive()) Targets.damage(e, 2, p);
                });
            }
        });
        // Every enemy bitten shaves time off the cooldown (the cooldown itself is set after this returns)
        Tasks.later(12, () -> {
            if (hit.isEmpty() || p.isRemoved()) return;
            d.cooldownEnd[1] = Math.max(now(), d.cooldownEnd[1] - 1500L * hit.size());
            dev.abps.AbpsMod.service().sync(p, true);
        });
        Fx.screen(p, Fx.TINT, rgb(), 12, 0.12f);
        used(p, 1);
        return true;
    }

    // ---- Ability 2: Hydro Cannon ----
    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        Vec3 end = Targets.aimPoint(p, CANNON_RANGE);
        double len = Math.max(2, end.distanceTo(eye));
        Vec3 muzzle = eye.add(look.scale(1.0)).add(0, -0.3, 0);
        Vec3 tip = eye.add(look.scale(len));
        double dmg = cannonDamage(d.level);
        long markUntil = now() + (long) (scentTime(d.level) * 1000);
        if (MARKS.size() > 200) MARKS.values().removeIf(t -> t < now());
        int hits = 0;
        for (LivingEntity e : Targets.enemiesNear(p, eye.add(look.scale(len / 2)), len / 2 + 2)) {
            Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > len + 1) continue;
            double off = rel.subtract(look.scale(along)).length();
            if (off > 1.7) continue;
            hits++;
            Targets.damage(e, dmg, p);
            Targets.velocity(e, look.scale(1.5).add(0, 0.35, 0));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (scentTime(d.level) * 20), 0));
            MARKS.put(e.getUUID(), markUntil);
            Vec3 core = e.position().add(0, e.getBbHeight() / 2, 0);
            Vfx.ring(level, core, new Vec3(0, 1, 0), 2.0, 0.5, 14, Vfx.tint(0xFF1744), 0.09f, 20, 0xFF1744);
            Vfx.burst(level, core, Vfx.tint(rgb2()), 12, 0.2, 0.14f, 14, rgb2());
        }
        // The jet: a thick core, a white heart, and pressure rings all the way down the line
        Vfx.beam(level, muzzle, tip, 0.55f, Vfx.tint(rgb()), 9, rgb());
        Vfx.beam(level, muzzle, tip, 0.22f, Vfx.WHITE, 6, rgb2());
        for (double t = 1.5; t < len; t += 2.5) {
            Vfx.ring(level, eye.add(look.scale(t)), look, 0.3, 1.6, 12, Vfx.tint(rgb2()), 0.09f, 8, rgb2());
        }
        Vfx.burst(level, tip, Vfx.tint(rgb2()), 20, 0.26, 0.2f, 16, rgb2());
        Vfx.burst(level, muzzle, Vfx.WHITE, 10, 0.18, 0.12f, 12, rgb());
        Targets.velocity(p, p.getDeltaMovement().add(look.scale(-0.5)));
        Fx.sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        Fx.sound(level, p, SoundEvents.GENERIC_SPLASH, 1.5f, 0.6f);
        Fx.shakeNear(level, p.position(), 8, 4, 0.3f);
        used(p, 2);
        if (hits > 0) {
            dev.abps.AbpsMod.service().actionBar(p, gradient("<bold>≋ Hydro Cannon</bold>") + " <gray>marked <white>" + hits + "</white> prey.");
        }
        return true;
    }

    // ---- Ability 3: Maelstrom ----
    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 center = Targets.aimPoint(p, 14);
        double dmg = whirlDamage(d.level);
        Fx.sound(level, center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.5f, 0.6f);
        Fx.sound(level, center, SoundEvents.GENERIC_SPLASH, 1.5f, 0.5f);
        // Shark fins and a swirl of water chunks circle the whole time
        Vfx.fins(level, center, 6, 3, net.minecraft.world.level.block.Blocks.CONCRETE.blue().defaultBlockState(), 104, 0.55, 0x0288D1);
        Vfx.vortex(level, center, 8, 24, Vfx.tint(rgb2()), 0.2f, 104, 1.1, rgb2());
        Vfx.vortex(level, center, 6, 12, Vfx.WHITE, 0.14f, 104, -0.8, rgb());
        Tasks.later(105, () -> implode(p, d, center));
        Tasks.repeat(20, 5, i -> {
            if (p.isRemoved()) return;
            if (i % 4 == 0) {
                Vfx.groundRing(level, center, 8.5, 1, 30, Vfx.tint(rgb()), 0.12f, 16, rgb());
                Fx.sound(level, center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1f, 0.7f);
            }
            for (LivingEntity e : Targets.enemiesNear(p, center, 8)) {
                Vec3 to = center.subtract(e.position());
                Vec3 flat = new Vec3(to.x, 0, to.z);
                if (flat.lengthSqr() < 0.04) flat = new Vec3(0.1, 0, 0);
                Vec3 pull = flat.normalize().scale(0.3);
                Vec3 spin = new Vec3(-flat.z, 0, flat.x).normalize().scale(0.45);
                Targets.velocity(e, new Vec3(pull.x + spin.x, 0.08, pull.z + spin.z));
                if (i % 2 == 0) Targets.damage(e, dmg, p);
            }
            Fx.shakeNear(level, center, 10, 3, 0.15f);
        });
        used(p, 3);
        return true;
    }

    /** The whirlpool collapses: a last, harder hit and a ring of teeth. */
    private void implode(ServerPlayer p, PlayerData d, Vec3 center) {
        if (p.isRemoved()) return;
        ServerLevel level = level(p);
        for (LivingEntity e : Targets.enemiesNear(p, center, 8)) {
            Targets.damage(e, whirlDamage(d.level) * 2, p);
            Targets.pushAway(center, e, 0.5, 0.6);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
        }
        Vfx.jaws(level, center, 3.5, 10, 2.4, Vfx.WHITE, rgb());
        Vfx.groundRing(level, center, 1, 9, 36, Vfx.tint(rgb2()), 0.2f, 12, rgb2());
        Vfx.pillar(level, center, 1.2, 7, Vfx.tint(rgb2()), 3, 5, 8, rgb2());
        Vfx.burst(level, center.add(0, 0.5, 0), Vfx.tint(rgb()), 26, 0.22, 0.2f, 18, rgb());
        Fx.sound(level, center, SoundEvents.GENERIC_SPLASH, 2f, 0.4f);
        Fx.sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
        Fx.shakeNear(level, center, 12, 8, 0.6f);
    }

    private void startFrenzy(ServerPlayer p, PlayerData d, double seconds) {
        long ms = (long) (seconds * 1000);
        int ticks = (int) (ms / 50);
        d.setBuff("frenzy", ms);
        p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0));
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
        ServerLevel level = level(p);
        Vec3 at = p.position();
        Vfx.burst(level, at.add(0, 1, 0), Vfx.tint(0xB71C1C), 28, 0.24, 0.18f, 20, 0xB71C1C);
        Vfx.groundRing(level, at, 0.5, 4.5, 26, Vfx.tint(0xB71C1C), 0.14f, 12, 0xFF1744);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4;
            Vfx.zigzag(level, at.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5), at.add(0, 1.4, 0), 4, 0.4, 0.07f, Vfx.tint(0xB71C1C), 12, 0xFF1744);
        }
        Fx.sound(level, p, SoundEvents.WARDEN_ROAR, 0.5f, 1.6f);
        Fx.sound(level, p, SoundEvents.DOLPHIN_ATTACK, 1f, 0.5f);
        Fx.screen(p, Fx.TINT, 0xB71C1C, 20, 0.18f);
    }

    // ---- Ability 4: Apex Breach ----
    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() > 0.01) flat = flat.normalize().scale(0.8);
        d.noFallUntil = now() + 8000;
        Targets.velocity(p, new Vec3(flat.x, 1.5, flat.z));
        Fx.sound(level, p, SoundEvents.DOLPHIN_JUMP, 1f, 0.5f);
        Fx.sound(level, p, SoundEvents.GENERIC_SPLASH, 1.5f, 0.6f);
        Fx.burst(level, ParticleTypes.SPLASH, p.position(), 60, 1.2, 0.3);
        Fx.screen(p, Fx.SHAKE, 0, 10, 0.4f);
        boolean[] landed = {false};
        final Vec3 drift = flat;
        Tasks.repeat(60, 1, i -> {
            if (landed[0] || p.isRemoved() || !p.isAlive()) return;
            Vec3 at = p.position();
            Fx.burst(level, ParticleTypes.BUBBLE_POP, at.add(0, 1, 0), 4, 0.4, 0.05);
            // A stack of rings follows you up and down
            if (i % 2 == 0) Vfx.groundRing(level, at.add(0, 0.6, 0), 0.5, 2.6, 18, Vfx.tint(i % 4 == 0 ? rgb() : rgb2()), 0.1f, 8, rgb2());
            if (i == 10) {
                Targets.velocity(p, new Vec3(drift.x * 0.4, -2.4, drift.z * 0.4));
                Fx.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_3, 1f, 0.6f);
            }
            if ((i >= 12 && (p.onGround() || p.isInWater())) || i == 59) {
                landed[0] = true;
                shockwave(p, d, p.position());
            }
        });
        used(p, 4);
        return true;
    }

    private void shockwave(ServerPlayer p, PlayerData d, Vec3 at) {
        ServerLevel level = level(p);
        double dmg = breachDamage(d.level);
        for (LivingEntity e : Targets.enemiesNear(p, at, 10)) {
            Targets.damage(e, e.isInWater() ? dmg * 1.5 : dmg, p);
            Targets.pushAway(at, e, 1.4, 0.8);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            Fx.burst(level, ParticleTypes.SPLASH, e.position().add(0, 1, 0), 20, 0.4, 0.2);
        }
        // Giant jaws slam shut around the impact, then the water blows outward
        Vfx.jaws(level, at, 6.5, 16, 3.6, Vfx.WHITE, rgb());
        Vfx.jaws(level, at, 3.6, 10, 2.4, Vfx.WHITE, rgb2());
        Tasks.repeat(4, 3, s -> Vfx.groundRing(level, at, 1 + s, 11 + s * 2, 44, Vfx.tint(s % 2 == 0 ? rgb() : rgb2()), 0.22f, 14, rgb2()));
        Vfx.pillar(level, at, 1.6, 10, Vfx.tint(rgb2()), 3, 6, 10, rgb2());
        Vfx.burst(level, at.add(0, 0.5, 0), Vfx.tint(rgb()), 40, 0.32, 0.24f, 22, rgb());
        Fx.burst(level, ParticleTypes.EXPLOSION, at, 2, 0.8, 0);
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 0.7f);
        Fx.sound(level, at, SoundEvents.GENERIC_SPLASH, 2f, 0.4f);
        Fx.shakeNear(level, at, 14, 12, 0.9f);
        startFrenzy(p, d, frenzyTime(d.level));
    }

    // ---- Ultimate: Tsunami ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 0.01) {
            fail(p, "Look forward, not straight up or down, to call the tsunami.");
            return false;
        }
        Vec3 fwd = dir.normalize();
        Vec3 lateral = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 origin = p.position().add(fwd.scale(3));
        double dmg = tsunamiDamage(d.level);
        int cols = 17;
        double half = cols / 2.0;
        int steps = 32; // one step every 2 ticks, one block per step
        Set<UUID> struck = new HashSet<>();
        Vfx.Wave wave = new Vfx.Wave(level, origin, fwd, cols, 6.5, net.minecraft.world.level.block.Blocks.STAINED_GLASS.lightBlue().defaultBlockState(),
                Vfx.WHITE, rgb());
        Fx.sound(level, p, SoundEvents.TRIDENT_THUNDER, 1.5f, 0.6f);
        Fx.sound(level, p, SoundEvents.WARDEN_EMERGE, 1.2f, 1.4f);
        Fx.sound(level, p, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.6f, 0.5f);
        Fx.screen(p, Fx.TINT, rgb(), 30, 0.2f);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 2));
        // A ground line and a rumble telegraph where the wave will run
        Vfx.beam(level, origin.add(0, 0.1, 0), origin.add(fwd.scale(steps)).add(0, 0.1, 0), 0.12f, Vfx.tint(rgb2()), 24, rgb2());
        used(p, 5);
        Tasks.repeat(steps + 6, 2, step -> {
            if (p.isRemoved()) {
                wave.collapse();
                return;
            }
            if (step >= steps) {
                if (step == steps) finishTsunami(level, wave, origin.add(fwd.scale(steps)), fwd, half);
                return;
            }
            Vec3 center = origin.add(fwd.scale(step));
            double growth = Math.min(1.0, (step + 2) / 6.0);
            wave.update(center, growth, step);
            // Whoever is inside the wall gets hit once, then carried along on the front of it
            for (LivingEntity e : Targets.enemiesNear(p, center.add(0, 2, 0), half + 3)) {
                Vec3 rel = e.position().subtract(center);
                double f = rel.dot(fwd), l = rel.dot(lateral);
                if (Math.abs(f) > 3.2 || Math.abs(l) > half + 0.6 || rel.y < -2 || rel.y > 7) continue;
                if (struck.add(e.getUUID())) {
                    Targets.damage(e, dmg, p);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                    Fx.sound(level, e, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f, 0.7f);
                    Vfx.burst(level, e.position().add(0, e.getBbHeight() / 2, 0), Vfx.WHITE, 12, 0.24, 0.16f, 14, rgb2());
                }
                Targets.velocity(e, fwd.scale(1.1).add(0, 0.28, 0));
            }
            if (step % 3 == 0) {
                Vfx.burst(level, center.add(fwd.scale(1.2)).add(0, 3, 0).add(lateral.scale((rand() - 0.5) * cols)), Vfx.WHITE, 8, 0.2, 0.2f, 14, rgb2());
                Fx.sound(level, center, SoundEvents.GENERIC_SPLASH, 1.5f, 0.5f);
            }
            if (step % 4 == 0) Fx.shakeNear(level, center, 16, 4, 0.5f);
            Fx.burst(level, ParticleTypes.SPLASH, center.add(0, 2, 0), 30, half * 0.5, 1.5, 0.5, 0.1);
        });
        return true;
    }

    /** The wave hits the end of its run and comes crashing down. */
    private void finishTsunami(ServerLevel level, Vfx.Wave wave, Vec3 at, Vec3 fwd, double half) {
        wave.collapse();
        Vfx.groundRing(level, at, 1, 12, 44, Vfx.tint(rgb2()), 0.25f, 14, rgb2());
        Vfx.groundRing(level, at, 1, 8, 32, Vfx.WHITE, 0.2f, 12, rgb());
        Vfx.burst(level, at.add(0, 2, 0), Vfx.WHITE, 46, 0.3, 0.26f, 24, rgb2());
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 0.6f);
        Fx.sound(level, at, SoundEvents.GENERIC_SPLASH, 2f, 0.4f);
        Fx.shakeNear(level, at, 20, 12, 0.9f);
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        Mods.remove(p, Attributes.BLOCK_BREAK_SPEED, "shark_float");
        Mods.remove(p, Attributes.MOVEMENT_SPEED, "shark_dry");
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        dev.abps.util.Vfx.groundRing(level, at, 0.5, ult ? 9 : 3.6, ult ? 34 : 20, dev.abps.util.Vfx.tint(0x4DD0E1), 0.12f, 12, 0x4DD0E1);
        dev.abps.util.Vfx.fins(level, at.add(0, 0.1, 0), ult ? 4.5 : 2.4, ult ? 5 : 3, net.minecraft.world.level.block.Blocks.CONCRETE.blue().defaultBlockState(), ult ? 70 : 32, 0.5, 0x0288D1);
    }
}
