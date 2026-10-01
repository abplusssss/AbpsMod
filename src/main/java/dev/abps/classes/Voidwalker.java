package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import dev.abps.util.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/** The void: blinks, black holes and stepping out of the world for a moment. */
public final class Voidwalker extends AttributeClass {

    static final int CUE_TEAR = 11;
    private static final int VIOLET = 0xB388FF, DEEP = 0x311B92;

    @Override public String id() { return "voidwalker"; }
    @Override public String name() { return "Voidwalker"; }
    @Override public String color() { return "#B388FF"; }
    @Override public String color2() { return "#7C4DFF"; }
    @Override public Item icon() { return Items.ENDER_EYE; }
    @Override public String symbol() { return "◈"; }
    @Override public String tagline() { return "Space bends where you walk."; }
    @Override public String mastery() { return "Killing an enemy resets Blink."; }

    private double fallCut(int lvl) { return lerp(lvl, 0.50, 0.80); }
    private double tearChance(int lvl) { return lerp(lvl, 0.15, 0.25); }
    private double boltDamage(int lvl) { return lerp(lvl, 7, 10); }
    private double wellTick(int lvl) { return lerp(lvl, 1.0, 1.5); }
    private double wellBurst(int lvl) { return lerp(lvl, 3, 4); }
    private double phaseTime(int lvl) { return lerp(lvl, 2.5, 3.5); }
    private double horizonTick(int lvl) { return lerp(lvl, 1.5, 2.0); }
    private double horizonEnd(int lvl) { return lerp(lvl, 8, 10); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Take " + pct(fallCut(lvl)) + " less fall damage",
                pct(tearChance(lvl)) + " chance for melee hits to tear space, pulling the target to you for 2 more damage",
                "Endermen leave you alone",
                "Deal +15% damage in the End");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Take 20% more damage from arrows and other projectiles",
                "Walk speed x0.9 in water");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Blink";
            case 2 -> "Void Bolt";
            case 3 -> "Gravity Well";
            case 4 -> "Phase Shift";
            default -> "Event Horizon";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Teleport up to 10 blocks where you look. The rift you leave behind slows enemies near it.";
            case 2 -> "Fire a bolt of void that goes through enemies in a line for " + num(boltDamage(lvl)) + " damage each.";
            case 3 -> "Open a small black hole where you look for 3s. It pulls enemies within 7 blocks in, deals " + num(wellTick(lvl) * 2)
                    + " damage a second, then bursts for " + num(wellBurst(lvl)) + ".";
            case 4 -> "Step half out of the world for " + num(phaseTime(lvl)) + "s: take 70% less damage, turn invisible and move faster.";
            default -> "Tear open a singularity where you look. For 4s it drags in everything within 10 blocks for " + num(horizonTick(lvl) * 2)
                    + " damage a second, then collapses for " + num(horizonEnd(lvl)) + ".";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 8;
            case 2 -> 10;
            case 3 -> 26;
            default -> 40;
        };
    }

    @Override
    protected double groundAimRange(int idx) {
        return idx == 3 ? 18 : idx == ULTIMATE ? 24 : 0;
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return switch (idx) {
            case 3 -> 60;
            case 4 -> (int) (phaseTime(d.level) * 20);
            case ULTIMATE -> 80;
            default -> 0;
        };
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:ender_pearl", "minecraft:chorus_fruit", "minecraft:shulker_shell", "minecraft:dragon_breath"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{24, 32, 6, 2};
    }

    // ---- Passives ----

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        dev.abps.util.Mods.toggle(p, p.isInWater(), net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "void_water", -0.1,
                dev.abps.util.Mods.MULT);
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        double m = 1;
        if (source.is(DamageTypeTags.IS_FALL)) m *= 1 - fallCut(d.level);
        if (source.is(DamageTypeTags.IS_PROJECTILE)) m *= 1.2;
        if (d.buff("phase")) m *= 0.3;
        return m;
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        return p.level().dimension() == Level.END ? 1.15 : 1;
    }

    @Override
    public boolean ignoredBy(ServerPlayer p, PlayerData d, Mob mob) {
        return mob.getType() == net.minecraft.world.entity.EntityTypes.ENDERMAN;
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee() || rand() >= tearChance(d.level)) return;
        Vec3 pull = p.position().subtract(victim.position());
        if (pull.lengthSqr() > 1.5) Targets.velocity(victim, pull.normalize().scale(0.6).add(0, 0.15, 0));
        Tasks.later(2, () -> {
            if (victim.isAlive() && !p.isRemoved()) Targets.damage(victim, 2, p);
        });
        Fx.sound(level(p), victim, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.6f);
        if (!cue(p, CUE_TEAR, p.position(), victim.position().add(0, victim.getBbHeight() * 0.5, 0), p, victim, 0)) {
            Vfx.ring(level(p), victim.position().add(0, 1, 0), p.getLookAngle(), 0.2, 1.2, 12, Vfx.tint(VIOLET), 0.06f, 8, VIOLET);
        }
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        if (mastered(d)) d.cooldownEnd[1] = 0;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        dev.abps.util.Mods.remove(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "void_water");
    }

    // ---- Abilities ----

    /** The furthest open spot along the look line, up to range, where the player fits. */
    private static Vec3 blinkSpot(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        HitResult hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) - 0.6 : range;
        for (double r = reach; r > 1; r -= 0.5) {
            Vec3 feet = eye.add(look.scale(r)).subtract(0, p.getEyeHeight(), 0);
            if (p.level().noCollision(p, p.getBoundingBox().move(feet.subtract(p.position())))) return feet;
            Vec3 up = feet.add(0, 1, 0);
            if (p.level().noCollision(p, p.getBoundingBox().move(up.subtract(p.position())))) return up;
        }
        return null;
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        Vec3 to = blinkSpot(p, 10);
        if (to == null || to.distanceToSqr(p.position()) < 4) {
            fail(p, "No room to blink there.");
            return false;
        }
        ServerLevel level = level(p);
        Vec3 from = p.position();
        castAim = to;
        used(p, 1);
        p.teleportTo(level, to.x, to.y, to.z, Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
        p.fallDistance = 0;
        Fx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.2f);
        Fx.sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        // The rift left behind slows enemies near it for a couple of seconds
        Tasks.repeat(4, 10, step -> {
            for (LivingEntity e : Targets.enemiesNear(p, from, 2.5)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1));
        });
        Vfx.ring(level, from.add(0, 1, 0), p.getLookAngle(), 0.2, 1.4, 14, Vfx.tint(VIOLET), 0.08f, 12, VIOLET);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 eye = p.getEyePosition();
        Vec3 end = Targets.aimPoint(p, 22);
        Vec3 seg = end.subtract(eye);
        double len = seg.length();
        Vec3 dir = seg.scale(1 / Math.max(0.01, len));
        double dmg = boltDamage(d.level);
        castAim = end;
        used(p, 2);
        Fx.sound(level, p, SoundEvents.SHULKER_SHOOT, 1f, 0.6f);
        for (LivingEntity e : Targets.enemiesNear(p, eye.add(seg.scale(0.5)), len / 2 + 2)) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double along = c.subtract(eye).dot(dir);
            if (along < 0 || along > len) continue;
            if (eye.add(dir.scale(along)).distanceTo(c) > 1.3 + e.getBbWidth() * 0.5) continue;
            int delay = (int) Math.max(1, along / 4);
            Tasks.later(delay, () -> {
                if (!e.isAlive() || p.isRemoved()) return;
                Targets.damage(e, dmg, p);
                Fx.sound(level, e, SoundEvents.SHULKER_BULLET_HIT, 0.8f, 0.7f);
            });
        }
        Vfx.beam(level, eye, end, 0.1f, Vfx.tint(VIOLET), 8, VIOLET);
        return true;
    }

    /** Pulls everything within radius of at in, for count half-seconds, then bursts. */
    private void singularity(ServerPlayer p, Vec3 at, double radius, int halfSeconds, double tick, double burst, double pull) {
        ServerLevel level = level(p);
        Tasks.repeat(halfSeconds * 2, 5, step -> {
            if (p.isRemoved()) return;
            for (LivingEntity e : Targets.enemiesNear(p, at, radius)) {
                Vec3 v = at.add(0, 0.6, 0).subtract(e.position());
                double dist = v.length();
                if (dist > 0.6) Targets.velocity(e, e.getDeltaMovement().scale(0.4).add(v.scale(pull / dist)));
                if (step % 2 == 0) Targets.damage(e, tick, p);
            }
            if (step % 4 == 0) Fx.sound(level, at, SoundEvents.PORTAL_AMBIENT, 0.7f, 0.5f + step * 0.03f);
            if (step % 2 == 0) Vfx.vortex(level, at.add(0, 0.6, 0), radius * 0.6, 10, Vfx.tint(DEEP), 0.12f, 10, -1.2, VIOLET);
        });
        Tasks.later(halfSeconds * 10L + 1, () -> {
            if (p.isRemoved()) return;
            for (LivingEntity e : Targets.enemiesNear(p, at, radius * 0.6)) {
                Targets.damage(e, burst, p);
                Targets.pushAway(at, e, 0.9, 0.5);
            }
            Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
            Fx.shakeNear(level, at, 10, 6, 0.4f);
            Vfx.sphere(level, at.add(0, 0.6, 0), 0.3, radius * 0.6, 24, Vfx.tint(VIOLET), 0.16f, 12, VIOLET);
        });
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        Vec3 at = Targets.groundPoint(p, 18);
        used(p, 3);
        Fx.sound(level(p), at, SoundEvents.END_PORTAL_FRAME_FILL, 1f, 0.5f);
        singularity(p, at, 7, 3, wellTick(d.level), wellBurst(d.level), 0.3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        int ticks = (int) (phaseTime(d.level) * 20);
        d.setBuff("phase", ticks * 50L);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, true, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, true, false, true));
        used(p, 4);
        Fx.sound(level(p), p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        Vec3 at = Targets.groundPoint(p, 24);
        if (Targets.enemiesNear(p, at, 10).isEmpty()) {
            fail(p, "No enemies within 10 blocks of where you look.");
            return false;
        }
        used(p, ULTIMATE);
        Fx.sound(level(p), at, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
        Fx.sound(level(p), at, SoundEvents.END_PORTAL_SPAWN, 0.6f, 1.4f);
        singularity(p, at, 10, 4, horizonTick(d.level), horizonEnd(d.level), 0.4);
        return true;
    }
}
