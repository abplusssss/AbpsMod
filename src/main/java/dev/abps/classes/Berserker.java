package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Berserker extends AttributeClass {

    private static final int MAX_FURY = 5;

    @Override public String id() { return "berserker"; }
    @Override public String name() { return "Berserker"; }
    @Override public String color() { return "#FF5252"; }
    @Override public String color2() { return "#FFAB00"; }
    @Override public Item icon() { return Items.IRON_AXE; }
    @Override public String symbol() { return "⚔"; }
    @Override public String tagline() { return "The lower your health, the harder you hit."; }
    @Override public String mastery() { return "Survive a killing blow once every 5 minutes and go into Rage."; }

    private double axe(int lvl) { return lerp(lvl, 1.15, 1.35); }
    private double lowHp(int lvl) { return lerp(lvl, 0.25, 0.50); }
    private double furyPer(int lvl) { return lerp(lvl, 0.03, 0.06); }
    private double rageTime(int lvl) { return lerp(lvl, 8, 12); }
    private double slamDamage(int lvl) { return lerp(lvl, 6, 10); }
    private double stunChance(int lvl) { return lerp(lvl, 0.15, 0.30); }
    private double spinDamage(int lvl) { return lerp(lvl, 3, 5); }
    private double cleave(int lvl) { return lerp(lvl, 12, 16); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Axes deal " + mult(axe(lvl)) + " damage",
                "+" + pct(lowHp(lvl)) + " melee damage when under 50% health",
                "Fury: each hit gives +" + pct(furyPer(lvl)) + " damage for 5s (stacks " + MAX_FURY + " times)",
                "Reaching max Fury heals you 2 hearts",
                pct(stunChance(lvl)) + " chance for axe hits to stun (Slowness IV for 1s)",
                "Kills give you Speed II for 4s");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "You can't block with shields",
                "Take 15% more damage from arrows and projectiles",
                "Hunger drains 25% faster");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Rage";
            case 2 -> "Leap Slam";
            case 3 -> "Whirlwind";
            case 4 -> "Warcry";
            default -> "Executioner";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "+35% melee damage, +20% speed and +15% attack speed for " + num(rageTime(lvl)) + "s.";
            case 2 -> "Leap forward and smash the ground for " + num(slamDamage(lvl)) + " damage and slow enemies. No fall damage.";
            case 3 -> "Spin for 3s, hitting everything within 3.5 blocks for " + num(spinDamage(lvl)) + " damage every half second.";
            case 4 -> "Get Strength II and Resistance I for 10s. Enemies within 10 blocks get Weakness and Slowness.";
            default -> "Leap onto the enemy you look at (20 blocks) and cleave for " + num(cleave(lvl))
                    + " damage plus 25% of their missing health. Enemies next to them take half.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 45;
            case 2 -> 20;
            case 3 -> 25;
            default -> 180;
        };
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        boolean rage = d.buff("rage");
        Mods.toggle(p, rage, Attributes.ATTACK_DAMAGE, "berserker_rage", 0.35, Mods.MULT);
        Mods.toggle(p, rage, Attributes.MOVEMENT_SPEED, "berserker_rage", 0.20, Mods.MULT);
        Mods.toggle(p, rage, Attributes.ATTACK_SPEED, "berserker_rage", 0.15, Mods.MULT);
        ServerLevel level = level(p);
        if (rage && d.tickCount % 2 == 0) {
            Fx.burst(level, ParticleTypes.ANGRY_VILLAGER, p.position().add(0, 2.1, 0), 1, 0.2, 0);
            Fx.burst(level, Fx.dust(0xFF1744, 1f), p.position().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0);
        }
        if (d.stacks > 0 && now() > d.stacksUntil) d.stacks = 0;

        if (d.leaping) {
            long since = now() - d.leapStart;
            if (since > 5000) d.leaping = false;
            else if (since > 400 && p.onGround()) {
                d.leaping = false;
                d.noFallUntil = now() + 500;
                slam(p, d, slamDamage(d.level), 4.5);
            }
        }
    }

    private void slam(ServerPlayer p, PlayerData d, double dmg, double radius) {
        ServerLevel level = level(p);
        Vec3 c = p.position();
        for (LivingEntity e : Targets.enemiesNear(p, c, radius)) {
            Targets.damage(e, dmg, p);
            Targets.pushAway(c, e, 0.9, 0.45);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
        }
        Fx.burst(level, ParticleTypes.EXPLOSION, c, 3, 1, 0.2, 1, 0);
        Fx.burst(level, Fx.block(level.getBlockState(BlockPos.containing(c).below())), c, 50, radius / 2, 0.1, radius / 2, 0.2);
        // A shockwave ring spreading out
        Tasks.repeat(4, 1, step -> Fx.ring(level, ParticleTypes.CLOUD, c, 1 + step * radius / 4, 18 + step * 6));
        Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.2f);
        Fx.shakeNear(level, c, 12, 8, 0.8f);
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        if (!hit.melee()) return 1;
        double m = 1;
        if (hit.weapon().is(ItemTags.AXES)) m *= axe(d.level);
        if (p.getHealth() < maxHp(p) * 0.5) m *= 1 + lowHp(d.level);
        if (d.stacks > 0 && now() <= d.stacksUntil) m *= 1 + furyPer(d.level) * d.stacks;
        return m;
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee()) return;
        if (hit.weapon().is(ItemTags.AXES) && rand() < stunChance(d.level)) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3));
            Fx.burst(level(p), ParticleTypes.CRIT, victim.getEyePosition(), 10, 0.3, 0.1);
        }
        if (now() > d.stacksUntil) d.stacks = 0;
        int before = d.stacks;
        d.stacks = Math.min(MAX_FURY, d.stacks + 1);
        d.stacksUntil = now() + 5000;
        if (before == MAX_FURY - 1 && d.stacks == MAX_FURY) {
            heal(p, 4);
            Fx.burst(level(p), ParticleTypes.HEART, p.getEyePosition().add(0, 0.5, 0), 3, 0.3, 0);
            Fx.sound(level(p), p, SoundEvents.RAVAGER_ROAR, 0.4f, 1.8f);
        }
        AbpsMod.service().actionBar(p, gradient("<bold>Fury " + "▮".repeat(d.stacks) + "</bold>") + "<dark_gray>" + "▯".repeat(MAX_FURY - d.stacks));
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return source.is(DamageTypeTags.IS_PROJECTILE) ? 1.15 : 1;
    }

    @Override
    public double hungerMultiplier() {
        return 1.25;
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1));
    }

    @Override
    public boolean allowDeath(ServerPlayer p, PlayerData d, DamageSource source) {
        if (!mastered(d) || now() < d.masteryReady) return true;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true; // void and /kill
        d.masteryReady = now() + 300_000;
        p.setHealth(Math.min(maxHp(p), 8));
        d.setBuff("rage", 8000);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
        ServerLevel level = level(p);
        Fx.burst(level, ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        Fx.sound(level, p, SoundEvents.TOTEM_USE, 0.8f, 1.3f);
        Fx.screen(p, Fx.FLASH, 0xFF5252, 12, 0.5f);
        AbpsMod.service().banner(p, gradient("<bold>UNDYING RAGE</bold>"), "<gray>Ready again in 5 minutes", rgb(), 40);
        return false;
    }

    @Override
    public boolean allowUseItem(ServerPlayer p, PlayerData d, ItemStack stack) {
        if (!stack.is(Items.SHIELD)) return true;
        AbpsMod.service().actionBar(p, "<red>Berserkers don't block.");
        return false;
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        d.setBuff("rage", (long) (rageTime(d.level) * 1000));
        ServerLevel level = level(p);
        Fx.sound(level, p, SoundEvents.RAVAGER_ROAR, 0.8f, 1.3f);
        Fx.burst(level, Fx.dust(0xFF1744, 1.5f), p.position().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0);
        Fx.screen(p, Fx.TINT, 0xFF1744, (int) (rageTime(d.level) * 20), 0.12f);
        used(p, 1);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        dir = dir.lengthSqr() < 0.01 ? Vec3.ZERO : dir.normalize().scale(1.1);
        Targets.velocity(p, dir.add(0, 0.9, 0));
        d.leaping = true;
        d.leapStart = now();
        d.noFallUntil = now() + 5000;
        Fx.sound(level(p), p, SoundEvents.GOAT_LONG_JUMP, 1f, 0.8f);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        double dmg = spinDamage(d.level);
        ServerLevel level = level(p);
        Tasks.repeat(6, 10, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3 c = p.position();
            for (LivingEntity e : Targets.enemiesNear(p, c, 3.5)) {
                Targets.damage(e, dmg, p);
                Targets.pushAway(c, e, 0.45, 0.2);
            }
            Fx.ring(level, ParticleTypes.SWEEP_ATTACK, c.add(0, 0.8, 0), 2.2, 8);
            Fx.ring(level, Fx.dust(0xFFAB00, 1f), c.add(0, 0.5, 0), 3.2, 20);
            Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f + step * 0.1f);
        });
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        Vec3 c = p.position();
        p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));
        for (LivingEntity e : Targets.enemiesNear(p, c, 10)) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 0));
        }
        ServerLevel level = level(p);
        Tasks.repeat(5, 2, step -> Fx.ring(level, Fx.dust(0xFF5252, 1.4f), c, 2 + step * 2, 20 + step * 8));
        Fx.sound(level, c, SoundEvents.RAID_HORN, 1.5f, 1f);
        Fx.sound(level, c, SoundEvents.RAVAGER_ROAR, 1f, 0.8f);
        Fx.shakeNear(level, c, 14, 10, 0.5f);
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Executioner ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 20);
        if (t == null) {
            noTarget(p, 20);
            return false;
        }
        ServerLevel level = level(p);
        // Jump high toward the target
        Vec3 to = t.position().subtract(p.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double dist = flat.length();
        Vec3 dir = dist < 0.1 ? Vec3.ZERO : flat.normalize().scale(Math.min(2.2, 0.3 + dist * 0.14));
        Targets.velocity(p, dir.add(0, 1.0, 0));
        d.noFallUntil = now() + 5000;
        Fx.sound(level, p, SoundEvents.RAVAGER_ROAR, 1f, 0.6f);
        long start = now();
        Tasks.repeat(60, 1, step -> {
            if (p.isRemoved() || !t.isAlive()) return;
            Fx.burst(level, Fx.dust(0xFF1744, 1.2f), p.position().add(0, 1, 0), 3, 0.2, 0);
            boolean close = p.distanceToSqr(t) < 3.5 * 3.5;
            if (!(close || (step > 8 && p.onGround()) || step == 59)) return;
            if (d.buffs.containsKey("exec_done_" + start)) return;
            d.buffs.put("exec_done_" + start, now() + 5000);
            double missing = Math.max(0, t.getMaxHealth() - t.getHealth());
            double dmg = cleave(d.level) + Math.min(12, missing * 0.25);
            Targets.damage(t, dmg, p);
            for (LivingEntity e : Targets.enemiesNear(p, t.position(), 3)) {
                if (e != t) Targets.damage(e, dmg * 0.5, p);
            }
            Vec3 c = t.position();
            Fx.burst(level, ParticleTypes.SWEEP_ATTACK, c.add(0, 1, 0), 6, 0.8, 0.1);
            Fx.burst(level, ParticleTypes.EXPLOSION, c.add(0, 0.5, 0), 2, 0.5, 0);
            Fx.burst(level, Fx.dust(0x8B0000, 2f), c.add(0, 1, 0), 60, 0.8, 0.8, 0.8, 0);
            Fx.sound(level, c, SoundEvents.ANVIL_LAND, 1f, 0.5f);
            Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
            Fx.shakeNear(level, c, 12, 10, 1f);
        });
        used(p, 5);
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        d.leaping = false;
        d.stacks = 0;
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        net.minecraft.world.phys.Vec3 look = p.getLookAngle();
        dev.abps.util.Vfx.slash(level, at.add(0, 1.1, 0), look, 2.6, 2.6, 0.16f, net.minecraft.world.level.block.Blocks.CONCRETE.red().defaultBlockState(), 0xFF1744);
        dev.abps.util.Vfx.slash(level, at.add(0, 0.6, 0), look, 2.2, 2.2, 0.12f, net.minecraft.world.level.block.Blocks.CONCRETE.orange().defaultBlockState(), 0xFF6D00);
    }
}
