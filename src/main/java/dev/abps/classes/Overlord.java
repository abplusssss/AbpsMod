package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Operators only. Never rolled, only given from the Admin tab or !GiveAttribute. Takes no damage, flies, and every
 * ability kills outright. If the player stops being an operator, the attribute is taken away on the next tick.
 */
public final class Overlord extends AttributeClass {

    /** Cues the client draws (SigOverlord). */
    static final int CUE_DEATH = 11;
    private static final int GOLD = 0xFFD54F, WHITE = 0xFFFFFF;

    @Override public String id() { return "overlord"; }
    @Override public String name() { return "Overlord"; }
    @Override public String color() { return "#FFD54F"; }
    @Override public String color2() { return "#FFFFFF"; }
    @Override public Item icon() { return Items.NETHER_STAR; }
    @Override public String symbol() { return "♛"; }
    @Override public String tagline() { return "The server bends to your will."; }
    @Override public String mastery() { return "Already as strong as it gets."; }

    @Override
    public boolean adminOnly() {
        return true;
    }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Operators only. You take no damage at all",
                "Fly any time (double-tap jump)",
                "Run twice as fast, see in the dark, hit very hard",
                "Mobs won't target you",
                "Your ultimate is always charged");
    }

    @Override
    public List<String> negatives() {
        return List.of("Taken away the moment you stop being an operator");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Smite";
            case 2 -> "Annihilate";
            case 3 -> "Warp";
            case 4 -> "Time Stop";
            default -> "Judgment";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "A bolt of light from the sky kills whatever you look at, up to 96 blocks away.";
            case 2 -> "Every enemy within 24 blocks dies at once.";
            case 3 -> "Teleport to wherever you look, up to 256 blocks.";
            case 4 -> "Everyone else within 48 blocks freezes in place for 8 seconds.";
            default -> "Every other player in this world and every hostile mob near them is struck down.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 1;
            default -> 10;
        };
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return idx == 4 ? 160 : 0;
    }

    // ---- Passives ----

    private static boolean allowed(ServerPlayer p) {
        return dev.abps.command.Commands.isAdmin(p.createCommandSourceStack());
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        dev.abps.util.Mods.set(p, Attributes.MOVEMENT_SPEED, "overlord_speed", 1.0, dev.abps.util.Mods.MULT);
        dev.abps.util.Mods.set(p, Attributes.ATTACK_DAMAGE, "overlord_dmg", 40, dev.abps.util.Mods.ADD);
        dev.abps.util.Mods.set(p, Attributes.KNOCKBACK_RESISTANCE, "overlord_kb", 1, dev.abps.util.Mods.ADD);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (!allowed(p)) {
            // Not an operator any more: the power goes away
            AbpsMod.service().send(p, "<red>Overlord is for operators only. You get a normal attribute instead.");
            AbpsMod.service().setAttribute(p, AbpsMod.service().randomClass("overlord"), 1);
            return;
        }
        d.ultCharge = 1;
        d.ultLockUntil = 0;
        if (!p.getAbilities().mayfly) {
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
        }
        if (d.tickCount % 20 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 30, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 30, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 30, 0, true, false, false));
        }
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
    }

    @Override
    public boolean allowDamage(ServerPlayer p, PlayerData d, DamageSource source) {
        return false;
    }

    @Override
    public boolean allowDeath(ServerPlayer p, PlayerData d, DamageSource source) {
        return false;
    }

    @Override
    public boolean ignoredBy(ServerPlayer p, PlayerData d, Mob mob) {
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        dev.abps.util.Mods.remove(p, Attributes.MOVEMENT_SPEED, "overlord_speed");
        dev.abps.util.Mods.remove(p, Attributes.ATTACK_DAMAGE, "overlord_dmg");
        dev.abps.util.Mods.remove(p, Attributes.KNOCKBACK_RESISTANCE, "overlord_kb");
        if (!p.isCreative() && !p.isSpectator()) {
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
    }

    // ---- Abilities ----

    /** Kills something outright, with the client's strike effect on it. */
    private void strike(ServerPlayer p, LivingEntity e) {
        if (!e.isAlive() || e == p) return;
        if (e instanceof ServerPlayer sp && sp.isSpectator()) return;
        Vec3 at = e.position();
        cue(p, CUE_DEATH, at.add(0, 18, 0), at, p, e, 0);
        ServerLevel level = (ServerLevel) e.level();
        e.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        if (e.isAlive()) e.kill(level);
        Fx.sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 96);
        if (t == null) {
            noTarget(p, 96);
            return false;
        }
        castTarget = t;
        used(p, 1);
        strike(p, t);
        Fx.sound(level(p), p, SoundEvents.TRIDENT_THUNDER.value(), 1f, 1.2f);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        List<LivingEntity> all = Targets.enemiesNear(p, p.position(), 24);
        used(p, 2);
        Fx.sound(level(p), p, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.6f);
        Fx.shakeNear(level(p), p.position(), 30, 12, 0.6f);
        // Rolls outward: closer ones die first
        for (LivingEntity e : all) {
            int delay = (int) Math.min(20, e.distanceTo(p) / 1.5);
            Tasks.later(delay, () -> strike(p, e));
        }
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        Vec3 eye = p.getEyePosition();
        HitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(256)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) {
            fail(p, "Look at a block within 256 blocks.");
            return false;
        }
        Vec3 to = hit.getLocation().subtract(p.getLookAngle().scale(0.8));
        // Stand on top of whatever was hit if there's room
        for (int up = 0; up < 4; up++) {
            Vec3 feet = to.add(0, up, 0);
            if (p.level().noCollision(p, p.getBoundingBox().move(feet.subtract(p.position())))) {
                to = feet;
                break;
            }
        }
        castAim = to;
        used(p, 3);
        Vec3 from = p.position();
        p.teleportTo(level(p), to.x, to.y, to.z, Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
        p.fallDistance = 0;
        Fx.sound(level(p), from, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
        Fx.sound(level(p), to, SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        List<LivingEntity> frozen = new ArrayList<>();
        for (Entity e : level.getEntities(p, new AABB(p.position(), p.position()).inflate(48))) {
            if (e instanceof LivingEntity le && le.isAlive() && !(le instanceof ServerPlayer sp && (sp.isSpectator() || allowed(sp)))) frozen.add(le);
        }
        used(p, 4);
        Fx.sound(level, p, SoundEvents.BELL_RESONATE, 1f, 0.5f);
        for (LivingEntity e : frozen) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 255, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 160, 255, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 255, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 160, 128, false, false, true)); // very high jump boost stops jumping
            Targets.root(e, 160);
            e.setDeltaMovement(Vec3.ZERO);
        }
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        List<LivingEntity> doomed = new ArrayList<>();
        for (ServerPlayer o : level.players()) {
            if (o == p || o.isSpectator()) continue;
            doomed.add(o);
            for (Mob m : level.getEntitiesOfClass(Mob.class, o.getBoundingBox().inflate(16), m -> m instanceof net.minecraft.world.entity.monster.Enemy)) {
                if (!doomed.contains(m)) doomed.add(m);
            }
        }
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m instanceof net.minecraft.world.entity.monster.Enemy)) {
            if (!doomed.contains(m)) doomed.add(m);
        }
        used(p, ULTIMATE);
        for (ServerPlayer o : level.players()) {
            Fx.sound(level, o, SoundEvents.WITHER_SPAWN, 0.8f, 0.6f);
            AbpsMod.service().banner(o, "<bold><gradient:#FFD54F:#FFFFFF>JUDGMENT</gradient></bold>", "<gray>" + p.getName().getString() + " has spoken", GOLD, 50);
        }
        for (int k = 0; k < doomed.size(); k++) {
            LivingEntity e = doomed.get(k);
            Tasks.later(20 + k % 20, () -> strike(p, e));
        }
        return true;
    }
}
