package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import dev.abps.util.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Time: rewinds, stops and speeds things up. */
public final class Chronomancer extends AttributeClass {

    static final int CUE_DEJAVU = 11, CUE_RELEASE = 12, CUE_STOPPED = 13;
    private static final int GOLD = 0xFFE082, VIOLET = 0x7E57C2;

    /** Where a player was, a few times a second, for Rewind. */
    private record Snapshot(Vec3 pos, float health, ResourceKey<Level> dim, float yaw, float pitch) {
    }

    private final Map<UUID, ArrayDeque<Snapshot>> history = new HashMap<>();
    /** Damage taken recently, as {time in ms, amount}, for Deja Vu. */
    private final Map<UUID, ArrayDeque<double[]>> recentDamage = new HashMap<>();

    @Override public String id() { return "chronomancer"; }
    @Override public String name() { return "Chronomancer"; }
    @Override public String color() { return "#FFE082"; }
    @Override public String color2() { return "#7E57C2"; }
    @Override public Item icon() { return Items.CLOCK; }
    @Override public String symbol() { return "⌛"; }
    @Override public String tagline() { return "Every second belongs to you."; }
    @Override public String mastery() { return "Deja Vu comes back every 60s and also clears your harmful effects."; }

    private double cdCut(int lvl) { return lerp(lvl, 0.08, 0.15); }
    private double attackSpeed(int lvl) { return lerp(lvl, 0.10, 0.20); }
    private int effectSpeed(int lvl) { return (int) Math.round(lerp(lvl, 2, 3)); }
    private double dejaVuCooldown(int lvl) { return lerp(lvl, 120, 75); }
    private double boltDamage(int lvl) { return lerp(lvl, 7, 10); }
    private double stasisTime(int lvl) { return lerp(lvl, 4, 6); }
    private double surgeTime(int lvl) { return lerp(lvl, 6, 9); }
    private double stopBase(int lvl) { return lerp(lvl, 8, 10); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Ability cooldowns are " + pct(cdCut(lvl)) + " shorter",
                "Attack speed x" + num(1 + attackSpeed(lvl)),
                "Harmful effects on you wear off " + pct(effectSpeed(lvl) / 5.0) + " faster",
                "Deja Vu: dropping under 30% health gives back the damage you took in the last 3s (every " + num(dejaVuCooldown(lvl)) + "s)");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "2 fewer hearts",
                "You get hungry 25% faster");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Time Bolt";
            case 2 -> "Rewind";
            case 3 -> "Stasis Field";
            case 4 -> "Haste Surge";
            default -> "Time Stop";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Fire a bolt of stopped time at the enemy you look at for " + num(boltDamage(lvl)) + " damage and Slowness II for 2s.";
            case 2 -> "Go back to where you were 3 seconds ago, with the health you had then if it was more.";
            case 3 -> "Freeze time in a 5 block circle where you look for " + num(stasisTime(lvl))
                    + "s. Enemies and arrows inside crawl. You and teammates inside move faster.";
            case 4 -> "For " + num(surgeTime(lvl)) + "s you get Speed II and Haste II, and your other cooldowns run twice as fast.";
            default -> "Stop time for every enemy within 14 blocks for 3s. When it starts again they take " + num(stopBase(lvl))
                    + " damage plus half of all the damage they took while stopped.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 8;
            case 2 -> 20;
            case 3 -> 30;
            default -> 45;
        };
    }

    @Override
    public double cooldownMultiplier(int level) {
        return 1 - cdCut(level);
    }

    @Override
    public double hungerMultiplier() {
        return 1.25;
    }

    @Override
    protected double groundAimRange(int idx) {
        return idx == 3 ? 20 : 0;
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return switch (idx) {
            case 3 -> (int) (stasisTime(d.level) * 20);
            case 4 -> (int) (surgeTime(d.level) * 20);
            case ULTIMATE -> 60;
            default -> 0;
        };
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:amethyst_shard", "minecraft:clock", "minecraft:echo_shard", "minecraft:recovery_compass"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{32, 6, 8, 1};
    }

    // ---- Passives ----

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MAX_HEALTH, "chrono_hp", -4, Mods.ADD);
        Mods.set(p, Attributes.ATTACK_SPEED, "chrono_speed", attackSpeed(d.level), Mods.MULT);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        // Remember where we were for Rewind (every 5 ticks, 3 seconds back)
        ArrayDeque<Snapshot> h = history.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        h.addLast(new Snapshot(p.position(), p.getHealth(), p.level().dimension(), p.getYRot(), p.getXRot()));
        while (h.size() > 13) h.removeFirst();

        // Harmful effects run down faster
        int extra = effectSpeed(d.level);
        List<MobEffectInstance> bad = new ArrayList<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            if (!e.getEffect().value().isBeneficial() && !e.isInfiniteDuration() && e.getDuration() > extra + 2) bad.add(e);
        }
        for (MobEffectInstance e : bad) {
            Holder<MobEffect> type = e.getEffect();
            p.removeEffect(type);
            p.addEffect(new MobEffectInstance(type, e.getDuration() - extra, e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon()));
        }

        // Haste Surge: other cooldowns run twice as fast
        if (d.buff("chrono_surge")) {
            long now = now();
            for (int k = 1; k <= 4; k++) if (d.cooldownEnd[k] > now) d.cooldownEnd[k] -= 250;
        }
    }

    @Override
    public void afterDamaged(ServerPlayer p, PlayerData d, DamageSource source, float taken) {
        if (taken <= 0) return;
        ArrayDeque<double[]> q = recentDamage.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        long now = now();
        q.addLast(new double[]{now, taken});
        while (!q.isEmpty() && now - q.peekFirst()[0] > 3000) q.removeFirst();
        if (p.getHealth() > p.getMaxHealth() * 0.3 || !p.isAlive() || d.buff("dejavu_cd")) return;
        double back = 0;
        for (double[] e : q) back += e[1];
        q.clear();
        heal(p, back);
        d.setBuff("dejavu_cd", (long) ((mastered(d) ? 60 : dejaVuCooldown(d.level)) * 1000));
        if (mastered(d)) {
            for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects())) if (!e.getEffect().value().isBeneficial()) p.removeEffect(e.getEffect());
        }
        AbpsMod.service().actionBar(p, gradient("<bold>⌛ Deja Vu</bold>") + " <gray>gave back " + num(back / 2) + " hearts");
        Fx.sound(level(p), p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
        if (!cue(p, CUE_DEJAVU, p.position(), p.position().add(0, 1, 0), p, null, 0)) {
            Vfx.helix(level(p), p.position(), 0.8, 2.2, 2, 14, Vfx.tint(GOLD), 0.1f, 14, GOLD);
        }
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        history.remove(p.getUUID());
        recentDamage.remove(p.getUUID());
        Mods.remove(p, Attributes.MAX_HEALTH, "chrono_hp");
        Mods.remove(p, Attributes.ATTACK_SPEED, "chrono_speed");
    }

    // ---- Abilities ----

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 26);
        if (t == null) {
            noTarget(p, 26);
            return false;
        }
        ServerLevel level = level(p);
        castTarget = t;
        used(p, 1);
        double dmg = boltDamage(d.level);
        int flight = (int) Math.max(2, Math.min(7, p.distanceTo(t) / 4.5));
        Tasks.later(flight, () -> {
            if (!t.isAlive() || p.isRemoved()) return;
            Targets.damage(t, dmg, p);
            t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
            Fx.sound(level, t, SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 1.3f);
        });
        Vfx.beam(level, p.getEyePosition(), t.position().add(0, t.getBbHeight() * 0.5, 0), 0.08f, Vfx.tint(GOLD), 8, GOLD);
        Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.7f);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ArrayDeque<Snapshot> h = history.get(p.getUUID());
        Snapshot back = h == null ? null : h.peekFirst();
        if (back == null || back.dim() != p.level().dimension() || back.pos().distanceToSqr(p.position()) < 1) {
            fail(p, "Nothing to rewind to yet.");
            return false;
        }
        ServerLevel level = level(p);
        Vec3 from = p.position();
        // The effect is drawn along the way back, so the signature starts where you are and aims at where you land
        castAim = back.pos();
        used(p, 2);
        p.teleportTo(level, back.pos().x, back.pos().y, back.pos().z, java.util.Set.of(), back.yaw(), back.pitch(), true);
        p.fallDistance = 0;
        if (back.health() > p.getHealth()) p.setHealth(Math.min(p.getMaxHealth(), back.health()));
        h.clear();
        Fx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.6f);
        Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
        Vfx.helix(level, back.pos(), 0.7, 2.2, 2, 12, Vfx.tint(VIOLET), 0.1f, 12, VIOLET);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 at = Targets.groundPoint(p, 20);
        int ticks = (int) (stasisTime(d.level) * 20);
        used(p, 3);
        Fx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        Tasks.repeat(ticks / 5, 5, step -> {
            if (p.isRemoved()) return;
            for (LivingEntity e : Targets.enemiesNear(p, at, 5)) {
                if (Math.abs(e.getY() - at.y) > 4) continue;
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 3));
                e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 12, 1));
            }
            for (ServerPlayer ally : level.players()) {
                if (ally == p || !Targets.isEnemy(p, ally)) {
                    if (ally.position().distanceToSqr(at) < 25) ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 12, 0, true, false, true));
                }
            }
            // Arrows and other shots crawl through the field
            for (Projectile pr : level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(5, 4, 5))) {
                if (pr.getOwner() == p) continue;
                Vec3 v = pr.getDeltaMovement();
                if (v.lengthSqr() > 0.01) Targets.velocity(pr, v.scale(0.35));
            }
            if (step % 8 == 0) Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.5f);
            if (step % 2 == 0) Vfx.groundRing(level, at, 4.8, 5.0, 24, Vfx.tint(GOLD), 0.08f, 10, GOLD);
        });
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        int ticks = (int) (surgeTime(d.level) * 20);
        d.setBuff("chrono_surge", ticks * 50L);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
        ServerLevel level = level(p);
        used(p, 4);
        Fx.sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.8f);
        dev.abps.util.Fancy.aura(level, p, ticks, GOLD, VIOLET);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        List<LivingEntity> targets = Targets.enemiesNear(p, p.position(), 14);
        if (targets.isEmpty()) {
            fail(p, "No enemies within 14 blocks.");
            return false;
        }
        ServerLevel level = level(p);
        double base = stopBase(d.level);
        Map<LivingEntity, Float> before = new HashMap<>();
        used(p, ULTIMATE);
        for (LivingEntity t : targets) {
            before.put(t, t.getHealth());
            Targets.root(t, 60);
            Targets.velocity(t, Vec3.ZERO);
            t.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 4));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 2));
            cue(p, CUE_STOPPED, p.position(), t.position().add(0, t.getBbHeight() * 0.5, 0), p, t, 60);
        }
        Fx.sound(level, p, SoundEvents.BELL_BLOCK, 1f, 0.5f);
        Fx.sound(level, p, SoundEvents.BEACON_DEACTIVATE, 1f, 0.6f);
        // Keep them hanging still in the air while time is stopped
        Tasks.repeat(12, 5, step -> {
            for (LivingEntity t : targets) if (t.isAlive()) Targets.velocity(t, new Vec3(0, 0, 0));
        });
        Tasks.later(60, () -> {
            if (p.isRemoved()) return;
            for (Map.Entry<LivingEntity, Float> e : before.entrySet()) {
                LivingEntity t = e.getKey();
                if (!t.isAlive()) continue;
                float lost = Math.max(0, e.getValue() - t.getHealth());
                Targets.damage(t, base + lost * 0.5, p);
                if (!cue(p, CUE_RELEASE, p.position(), t.position().add(0, t.getBbHeight() * 0.5, 0), p, t, 0)) {
                    Vfx.flash(level, t.position().add(0, 1, 0), 1.6f, Vfx.tint(GOLD), 8, GOLD);
                }
            }
            Fx.sound(level, p, SoundEvents.BELL_RESONATE, 1f, 1.2f);
        });
        return true;
    }

}
