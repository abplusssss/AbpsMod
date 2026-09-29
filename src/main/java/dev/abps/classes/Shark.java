package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Everything underwater: endless breath, fast digging, a torpedo dash and a whirlpool. */
public final class Shark extends AttributeClass {

    private static final int SCENT_RADIUS = 28;
    private static final int WOUNDED_MARK_TICKS = 80;

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
                "Hits on prey under 40% health in water mark it with Glowing");
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
            case 2 -> "Blood Scent";
            case 3 -> "Maelstrom";
            case 4 -> "Feeding Frenzy";
            default -> "Apex Breach";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Needs water. Rocket forward like a torpedo, dealing " + num(dashDamage(lvl)) + " damage and flinging aside everything you hit.";
            case 2 -> "Sense every enemy within " + SCENT_RADIUS + " blocks for " + num(scentTime(lvl))
                    + "s. They glow through walls, wounded prey are tracked with a blood trail and you get Speed.";
            case 3 -> "Open a whirlpool where you look for 5s. Enemies within 8 blocks are spun in and take "
                    + num(whirlDamage(lvl)) + " damage every half second.";
            case 4 -> "For " + num(frenzyTime(lvl)) + "s get Strength, Speed and Haste, and heal " + pct(frenzySteal(lvl)) + " of the damage you deal.";
            default -> "Leap out of the water, then crash down. The shockwave hits everything within 10 blocks for "
                    + num(breachDamage(lvl)) + " damage (+50% to enemies in water), launches and slows them, and starts a Frenzy.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 12;
            case 2 -> 35;
            case 3 -> 45;
            default -> 90;
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
        return wet(p) ? 1 + wetDamage(d.level) : 0.85;
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
            Fx.burst(level, ParticleTypes.BUBBLE_POP, body, 10, 0.5, 0.05);
            Fx.burst(level, ParticleTypes.SPLASH, body, 8, 0.5, 0.1);
            Fx.burst(level, Fx.dust(rgb2(), 1.1f), body, 6, 0.4, 0);
            for (LivingEntity e : Targets.enemiesNear(p, body, 2.2)) {
                if (!hit.add(e.getUUID())) continue;
                Targets.damage(e, dmg, p);
                Vec3 side = e.position().subtract(p.position());
                Targets.velocity(e, dir.scale(0.6).add(side.normalize().scale(0.5)).add(0, 0.35, 0));
                Fx.burst(level, ParticleTypes.SWEEP_ATTACK, e.position().add(0, e.getBbHeight() / 2, 0), 1, 0, 0);
                Fx.sound(level, e, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.7f);
            }
        });
        Fx.screen(p, Fx.TINT, rgb(), 12, 0.12f);
        used(p, 1);
        return true;
    }

    // ---- Ability 2: Blood Scent ----
    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        List<LivingEntity> prey = Targets.enemiesNear(p, p.position(), SCENT_RADIUS);
        if (prey.isEmpty()) {
            fail(p, "The water is empty. No prey within " + SCENT_RADIUS + " blocks.");
            return false;
        }
        int ticks = (int) (scentTime(d.level) * 20);
        int wounded = 0;
        for (LivingEntity e : prey) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            if (e.getHealth() < e.getMaxHealth() * 0.6f) {
                wounded++;
                Fx.line(level, Fx.dust(0xB71C1C, 1f), p.getEyePosition(), e.getEyePosition(), 0.9);
                Fx.burst(level, Fx.dust(0xB71C1C, 1.3f), e.position().add(0, e.getBbHeight() / 2, 0), 12, 0.4, 0);
            }
        }
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks / 2, 0));
        Fx.sound(level, p, SoundEvents.WARDEN_HEARTBEAT, 1f, 0.8f);
        Fx.sound(level, p, SoundEvents.CONDUIT_ACTIVATE, 1f, 1.4f);
        Tasks.repeat(8, 2, step -> Fx.ring(level, ParticleTypes.BUBBLE_POP, p.position().add(0, 0.8, 0), 2 + step * 3.5, 30 + step * 10));
        used(p, 2);
        dev.abps.AbpsMod.service().actionBar(p, gradient("<bold>≋ Blood Scent</bold>") + " <gray>found <white>" + prey.size()
                + "</white> prey, <red>" + wounded + "</red> wounded.");
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
        Tasks.repeat(20, 5, i -> {
            if (p.isRemoved()) return;
            double turn = i * 0.9;
            Fx.spiral(level, ParticleTypes.BUBBLE_POP, center, 8, 3.5, 60, turn);
            Fx.spiral(level, ParticleTypes.SPLASH, center, 6, 3, 40, turn + Math.PI);
            Fx.ring(level, ParticleTypes.NAUTILUS, center.add(0, 0.5, 0), 7.5, 24);
            if (i % 4 == 0) Fx.sound(level, center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1f, 0.7f);
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

    // ---- Ability 4: Feeding Frenzy ----
    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        startFrenzy(p, d, frenzyTime(d.level));
        used(p, 4);
        return true;
    }

    private void startFrenzy(ServerPlayer p, PlayerData d, double seconds) {
        long ms = (long) (seconds * 1000);
        int ticks = (int) (ms / 50);
        d.setBuff("frenzy", ms);
        p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0));
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
        ServerLevel level = level(p);
        Fx.burst(level, Fx.dust(0xB71C1C, 1.4f), p.position().add(0, 1, 0), 50, 0.7, 0);
        Fx.burst(level, ParticleTypes.BUBBLE_POP, p.position().add(0, 1, 0), 40, 0.7, 0.2);
        Fx.spiral(level, Fx.dust(rgb(), 1.2f), p.position(), 1.2, 2.5, 40, 0);
        Fx.sound(level, p, SoundEvents.WARDEN_ROAR, 0.5f, 1.6f);
        Fx.sound(level, p, SoundEvents.DOLPHIN_ATTACK, 1f, 0.5f);
        Fx.screen(p, Fx.TINT, 0xB71C1C, 20, 0.18f);
    }

    // ---- Ultimate: Apex Breach ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
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
            Fx.burst(level, ParticleTypes.BUBBLE_POP, at.add(0, 1, 0), 6, 0.4, 0.05);
            Fx.burst(level, Fx.dust(rgb(), 1.2f), at.add(0, 1, 0), 4, 0.4, 0);
            if (i == 10) {
                Targets.velocity(p, new Vec3(drift.x * 0.4, -2.4, drift.z * 0.4));
                Fx.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_3, 1f, 0.6f);
            }
            if ((i >= 12 && (p.onGround() || p.isInWater())) || i == 59) {
                landed[0] = true;
                shockwave(p, d, p.position());
            }
        });
        used(p, 5);
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
        Tasks.repeat(6, 2, s -> Fx.ring(level, ParticleTypes.SPLASH, at, 2 + s * 1.6, 40 + s * 8));
        Fx.burst(level, ParticleTypes.EXPLOSION, at, 3, 0.8, 0);
        Fx.burst(level, ParticleTypes.NAUTILUS, at.add(0, 1, 0), 80, 2, 0.4);
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 0.7f);
        Fx.sound(level, at, SoundEvents.GENERIC_SPLASH, 2f, 0.4f);
        Fx.shakeNear(level, at, 14, 12, 0.9f);
        startFrenzy(p, d, 8);
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        Mods.remove(p, Attributes.BLOCK_BREAK_SPEED, "shark_float");
        Mods.remove(p, Attributes.MOVEMENT_SPEED, "shark_dry");
    }
}
