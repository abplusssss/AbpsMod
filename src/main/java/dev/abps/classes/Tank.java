package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Tank extends AttributeClass {

    @Override public String id() { return "tank"; }
    @Override public String name() { return "Tank"; }
    @Override public String color() { return "#B0BEC5"; }
    @Override public String color2() { return "#42A5F5"; }
    @Override public Item icon() { return Items.SHIELD; }
    @Override public String symbol() { return "⛨"; }
    @Override public String tagline() { return "Hard to move. Harder to kill."; }
    @Override public String mastery() { return "Take 40% less damage while under 30% health."; }

    private double bonusHp(int lvl) { return Math.round(lerp(lvl, 6, 12)); }
    private double kb(int lvl) { return lerp(lvl, 0.60, 1.00); }
    private double reduction(int lvl) { return lerp(lvl, 0.12, 0.20); }
    private double thorns(int lvl) { return lerp(lvl, 0.25, 0.45); }
    private double fortifyTime(int lvl) { return lerp(lvl, 10, 14); }
    private double bashDamage(int lvl) { return lerp(lvl, 7, 10); }
    private double unbreakableTime(int lvl) { return lerp(lvl, 6, 9); }
    private double stompDamage(int lvl) { return lerp(lvl, 2, 3); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "+" + num(bonusHp(lvl) / 2) + " hearts max health",
                "Take " + pct(reduction(lvl)) + " less damage from everything",
                "+" + pct(kb(lvl)) + " knockback resistance",
                "Reflect " + pct(thorns(lvl)) + " of melee damage back at attackers",
                "Blocking a hit with a shield heals you 1 heart",
                "Immune to Slowness",
                "Explosions can't knock you back");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Walk speed x0.95",
                "Attack speed x0.95",
                "Too heavy to fly with an Elytra");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Fortify";
            case 2 -> "Challenge";
            case 3 -> "Shield Bash";
            case 4 -> "Unbreakable";
            default -> "Colossus";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Get Resistance II and " + (lvl >= 15 ? "8" : "4") + " hearts of Absorption for " + num(fortifyTime(lvl)) + "s.";
            case 2 -> "Pull enemies within 8 blocks to you and make mobs attack you. Gain up to 8 hearts of Absorption.";
            case 3 -> "Charge forward. The first enemy you hit takes " + num(bashDamage(lvl)) + " damage, gets knocked back and can't move for 2s.";
            case 4 -> "For " + num(unbreakableTime(lvl)) + "s take 70% less damage, can't be knocked back and reflect 50% of melee damage.";
            default -> "Grow into a giant for 10s. You can't be knocked back, and every second you stomp the ground for "
                    + num(stompDamage(lvl)) + " damage, knocking enemies away.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 28;
            case 2 -> 22;
            case 3 -> 11;
            default -> 70;
        };
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MAX_HEALTH, "tank_hp", bonusHp(d.level), Mods.ADD);
        Mods.set(p, Attributes.KNOCKBACK_RESISTANCE, "tank_kb", kb(d.level), Mods.ADD);
        Mods.set(p, Attributes.MOVEMENT_SPEED, "tank_speed", -0.05, Mods.MULT);
        Mods.set(p, Attributes.ATTACK_SPEED, "tank_atkspeed", -0.05, Mods.MULT);
        Mods.set(p, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "tank_blast", 1.0, Mods.ADD);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        boolean unbreak = d.buff("unbreakable") || d.buff("colossus");
        Mods.toggle(p, unbreak, Attributes.KNOCKBACK_RESISTANCE, "tank_unbreak", 1.0, Mods.ADD);
        boolean giant = d.buff("colossus");
        Mods.toggle(p, giant, Attributes.SCALE, "tank_colossus", 0.6, Mods.MULT);
        Mods.toggle(p, giant, Attributes.ENTITY_INTERACTION_RANGE, "tank_colossus", 1.5, Mods.ADD);
        if (unbreak && d.tickCount % 2 == 0) {
            fallbackOnly(() -> Fx.burst(level(p), ParticleTypes.ENCHANTED_HIT, p.position().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.05));
        }
    }

    @Override
    public boolean immuneTo(Holder<MobEffect> effect) {
        return effect.equals(MobEffects.SLOWNESS);
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE)) return 1;
        double m = 1 - reduction(d.level);
        if (mastered(d) && p.getHealth() < maxHp(p) * 0.3) m *= 0.6;
        if (d.buff("unbreakable")) m *= 0.3;
        return m;
    }

    @Override
    public void afterDamaged(ServerPlayer p, PlayerData d, DamageSource source, float taken) {
        // Shield block heal
        if (p.isBlocking() && !d.buff("blockheal")) {
            d.setBuff("blockheal", 1000);
            heal(p, 2);
            if (!cue(p, 11, p.position(), p.position().add(0, 1, 0), p, null, 0))
                Fx.burst(level(p), ParticleTypes.HEART, p.getEyePosition().add(0, 0.4, 0), 2, 0.2, 0);
        }
        // Thorns (the abilityDamage check stops two tanks reflecting forever)
        if (Targets.abilityDamage || !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == p) return;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return;
        double reflect = taken * (d.buff("unbreakable") ? Math.max(0.5, thorns(d.level)) : thorns(d.level));
        if (reflect < 0.5) return;
        Targets.damage(attacker, reflect, p);
        if (cue(p, 12, p.position().add(0, 1, 0), attacker.position().add(0, attacker.getBbHeight() * 0.55, 0), p, attacker, 0)) return;
        Fx.burst(level(p), ParticleTypes.CRIT, attacker.position().add(0, 1, 0), 6, 0.3, 0.1);
        dev.abps.util.Fancy.impact(level(p), attacker.position().add(0, 1, 0), 0.8f, 0x42A5F5, 0xFFFFFF);
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        int ticks = (int) (fortifyTime(d.level) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, d.level >= 15 ? 3 : 1));
        ServerLevel level = level(p);
        Fx.sound(level, p, SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 0.7f);
        Fx.sound(level, p, SoundEvents.ANVIL_PLACE, 0.5f, 1.4f);
        Fx.spiral(level, Fx.dust(0x90CAF9, 1.2f), p.position(), 1, 2.2, 36, 0);
        Fx.burst(level, ParticleTypes.ENCHANTED_HIT, p.position().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.1);
        dev.abps.util.Vfx.sphere(level, p.position().add(0, 1, 0), 1.9, 1.9, 30, dev.abps.util.Vfx.tint(0x90CAF9), 0.18f, 40, 0x90CAF9);
        dev.abps.util.Vfx.sphere(level, p.position().add(0, 1, 0), 0.5, 2.6, 24, dev.abps.util.Vfx.tint(0xFFFFFF), 0.16f, 12, 0x90CAF9);
        dev.abps.util.Fancy.aura(level, p, ticks, 0x90CAF9, 0xFFFFFF);
        used(p, 1);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        Vec3 c = p.position();
        List<LivingEntity> near = Targets.enemiesNear(p, c, 12);
        if (near.isEmpty()) {
            fail(p, "No enemies nearby.");
            return false;
        }
        int pulled = 0;
        ServerLevel level = level(p);
        for (LivingEntity e : near) {
            if (e instanceof Mob mob && e instanceof Enemy) mob.setTarget(p);
            if (e.distanceToSqr(p) > 64) continue;
            Vec3 pull = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
            if (pull.lengthSqr() > 0.01) Targets.velocity(e, pull.normalize().scale(0.9).add(0, 0.35, 0));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
            cue(p, 13, c.add(0, 1, 0), e.position().add(0, e.getBbHeight() * 0.55, 0), p, e, 0);
            Fx.line(level, Fx.dust(0x42A5F5, 0.8f), e.position().add(0, 1, 0), c.add(0, 1, 0), 0.5);
            dev.abps.util.Fancy.laser(level, e.position().add(0, 1, 0), c.add(0, 1, 0), 0.08f, 0x42A5F5, 0xFFFFFF, 10);
            pulled++;
        }
        // 1 enemy = 2 hearts, 2-3 = 4, 4-5 = 6, 6+ = 8
        if (pulled > 0) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, Math.min(3, pulled / 2)));
        Fx.ring(level, ParticleTypes.CRIT, c, 7, 40);
        dev.abps.util.Fancy.sigil(level, c, 8, 10, 0x42A5F5, 0xB0BEC5, 30);
        dev.abps.util.Vfx.flash(level, c.add(0, 1, 0), 2f, dev.abps.util.Vfx.WHITE, 6, 0x42A5F5);
        Fx.sound(level, c, SoundEvents.IRON_GOLEM_HURT, 1f, 0.5f);
        Fx.sound(level, c, SoundEvents.RAID_HORN, 0.6f, 1.6f);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (dir.lengthSqr() < 0.01) {
            fail(p, "Look forward to charge.");
            return false;
        }
        Vec3 fdir = dir.normalize();
        Targets.velocity(p, fdir.scale(1.5).add(0, 0.15, 0));
        dev.abps.util.Vfx.trail(level(p), p, 10, dev.abps.util.Vfx.tint(0x90CAF9), 0.36f, 0x42A5F5);
        double dmg = bashDamage(d.level);
        boolean[] hit = {false};
        ServerLevel level = level(p);
        Tasks.repeat(10, 1, step -> {
            if (hit[0] || p.isRemoved()) return;
            for (LivingEntity e : Targets.enemiesNear(p, p.position(), 1.8)) {
                hit[0] = true;
                Targets.damage(e, dmg, p);
                Targets.root(e, 40);
                Targets.velocity(e, fdir.scale(1.2).add(0, 0.35, 0));
                cue(p, 14, p.position().add(0, 1, 0), e.position().add(0, e.getBbHeight() * 0.55, 0), p, e, 0);
                Fx.burst(level, ParticleTypes.EXPLOSION, e.position().add(0, 1, 0), 1, 0, 0);
                dev.abps.util.Fancy.impact(level, e.position().add(0, 1, 0), 2f, 0x42A5F5, 0xFFFFFF);
                dev.abps.util.Vfx.groundRing(level, e.position(), 0.5, 3.5, 20, dev.abps.util.Vfx.tint(0x90CAF9), 0.16f, 10, 0x42A5F5);
                Fx.sound(level, e, SoundEvents.SHIELD_BLOCK.value(), 1f, 0.6f);
                Fx.shakeNear(level, e.position(), 6, 5, 0.6f);
                Targets.velocity(p, Vec3.ZERO);
                break;
            }
            if (!hit[0]) Fx.burst(level, ParticleTypes.CLOUD, p.position(), 2, 0.2, 0.1, 0.2, 0);
        });
        Fx.sound(level, p, SoundEvents.RAVAGER_STEP, 1f, 0.8f);
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        d.setBuff("unbreakable", (long) (unbreakableTime(d.level) * 1000));
        ServerLevel level = level(p);
        Fx.sound(level, p, SoundEvents.ANVIL_USE, 1f, 0.5f);
        Fx.sound(level, p, SoundEvents.TOTEM_USE, 0.6f, 0.6f);
        Fx.ring(level, ParticleTypes.ENCHANTED_HIT, p.position(), 2, 30);
        dev.abps.util.Vfx.sphere(level, p.position().add(0, 1, 0), 2.0, 2.0, 36, dev.abps.util.Vfx.tint(0x90CAF9), 0.2f, 30, 0x90CAF9);
        dev.abps.util.Fancy.aura(level, p, (int) (unbreakableTime(d.level) * 20), 0x90CAF9, 0xFFFFFF);
        Fx.screen(p, Fx.TINT, 0x90CAF9, (int) (unbreakableTime(d.level) * 20), 0.1f);
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Colossus ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        d.setBuff("colossus", 10_000);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));
        ServerLevel level = level(p);
        Fx.sound(level, p, SoundEvents.WARDEN_EMERGE, 1f, 1.2f);
        dev.abps.util.Fancy.sigil(level, p.position(), 6, 12, 0x42A5F5, 0xB0BEC5, 40);
        dev.abps.util.Vfx.pillar(level, p.position(), 1.2, 12, dev.abps.util.Vfx.tint(0x90CAF9), 6, 10, 10, 0x42A5F5);
        Fx.shakeNear(level, p.position(), 16, 10, 0.8f);
        double dmg = stompDamage(d.level);
        Tasks.schedule(20, 20, 10, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3 c = p.position();
            for (LivingEntity e : Targets.enemiesNear(p, c, 6)) {
                Targets.damage(e, dmg, p);
                Targets.pushAway(c, e, 1.0, 0.45);
            }
            cue(p, 15, c, c.add(0, 1, 0), p, null, step);
            Fx.burst(level, Fx.block(level.getBlockState(BlockPos.containing(c).below())), c, 60, 2.5, 0.1, 2.5, 0.2);
            dev.abps.util.Vfx.groundRing(level, c, 1, 7, 30, dev.abps.util.Vfx.tint(0xB0BEC5), 0.3f, 12, 0x42A5F5);
            dev.abps.util.Vfx.jaws(level, c, 5, 12, 1.8, dev.abps.util.Vfx.tint(0x78909C), 0x42A5F5);
            Tasks.repeat(3, 1, s -> Fx.ring(level, ParticleTypes.CLOUD, c, 1.5 + s * 1.6, 24));
            Fx.sound(level, c, SoundEvents.WARDEN_STEP, 2f, 0.6f);
            Fx.shakeNear(level, c, 14, 6, 0.6f);
        });
        used(p, ULTIMATE);
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        Mods.remove(p, Attributes.SCALE, "tank_colossus");
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return switch (idx) {
            case 1 -> (int) (fortifyTime(d.level) * 20);
            case 4 -> (int) (unbreakableTime(d.level) * 20);
            case ULTIMATE -> 200;
            default -> 0;
        };
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        dev.abps.util.Vfx.groundRing(level, at, 1, ult ? 10 : 5, ult ? 40 : 24, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), ult ? 0.5f : 0.35f, 12, rgb());
        dev.abps.util.Vfx.jaws(level, at, ult ? 5 : 2.2, ult ? 14 : 6, ult ? 2.8 : 1.4, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), rgb());
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:iron_ingot", "minecraft:iron_block", "minecraft:diamond", "minecraft:netherite_scrap"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{40, 12, 10, 4};
    }
}
