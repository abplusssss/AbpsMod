package dev.abps.classes;

import dev.abps.AbpsMod;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Pyromancer extends AttributeClass {

    private static final String FIREBALL = "abps_fireball";

    @Override public String id() { return "pyromancer"; }
    @Override public String name() { return "Pyromancer"; }
    @Override public String color() { return "#FFD600"; }
    @Override public String color2() { return "#FF3D00"; }
    @Override public Item icon() { return Items.BLAZE_POWDER; }
    @Override public String symbol() { return "✹"; }
    @Override public String tagline() { return "Fire is your friend. Water is not."; }
    @Override public String mastery() { return "Dropping under 30% health sets off a free Flame Nova (once a minute)."; }

    private double fireResist(int lvl) { return lerp(lvl, 0.70, 1.00); }
    private double lavaResist(int lvl) { return lerp(lvl, 0.40, 0.70); }
    private double igniteChance(int lvl) { return lerp(lvl, 0.50, 0.80); }
    private double burnBonus(int lvl) { return lerp(lvl, 0.25, 0.50); }
    private double netherBonus(int lvl) { return lerp(lvl, 0.15, 0.25); }
    private double fireballDamage(int lvl) { return lerp(lvl, 10, 15); }
    private double novaDamage(int lvl) { return lerp(lvl, 10, 15); }
    private double popDamage(int lvl) { return lerp(lvl, 5, 9); }
    private double dashDamage(int lvl) { return lerp(lvl, 6, 9); }
    private double meteorDamage(int lvl) { return lerp(lvl, 20, 28); }
    private double infernoDamage(int lvl) { return lerp(lvl, 5, 8); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Take " + pct(fireResist(lvl)) + " less fire damage and " + pct(lavaResist(lvl)) + " less lava damage",
                pct(igniteChance(lvl)) + " chance to set targets on fire when you hit them",
                "Deal +" + pct(burnBonus(lvl)) + " damage to burning targets",
                "Heat aura: enemies within 4 blocks of you catch fire",
                "Burning enemies explode when they die for " + num(popDamage(lvl)) + " damage",
                "Your arrows are on fire and you burn for half as long",
                "Deal +" + pct(netherBonus(lvl)) + " damage in the Nether");
    }

    @Override
    public List<String> negatives() {
        return List.of("Water and rain hurt you (they can't kill you)");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Fireball";
            case 2 -> "Flame Nova";
            case 3 -> "Blaze Dash";
            case 4 -> "Meteor";
            default -> "Inferno";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Shoot a fireball for " + num(fireballDamage(lvl)) + " damage. It bursts and burns enemies near where it lands. It never sets blocks on fire.";
            case 2 -> "Burn all enemies within 7 blocks for " + num(novaDamage(lvl)) + " damage and set them on fire.";
            case 3 -> "Dash forward in a trail of fire. Enemies you pass take " + num(dashDamage(lvl)) + " damage and burn.";
            case 4 -> "Call a meteor where you look. After 1.5s it hits everything within 6 blocks for " + num(meteorDamage(lvl)) + " damage. It does not break blocks.";
            default -> "Become a living firestorm for 6s. Everything within 8 blocks burns and takes "
                    + num(infernoDamage(lvl)) + " damage every second. You can't be hurt by fire.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 4;
            case 2 -> 22;
            case 3 -> 10;
            default -> 60;
        };
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.BURNING_TIME, "pyro_burn", -0.5, Mods.MULT);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (d.tickCount % 8 == 0) dev.abps.util.Vfx.groundRing(level(p), p.position(), 3.8, 4.0, 14, dev.abps.util.Vfx.tint(0xFF6D00), 0.06f, 10, 0xFF6D00);
        if (d.tickCount % 8 != 0) return; // every 2 seconds
        ServerLevel level = level(p);
        for (LivingEntity e : Targets.enemiesNear(p, p.position(), 4)) {
            if (e.getRemainingFireTicks() < 40) e.igniteForSeconds(3);
        }
        GameType gm = p.gameMode();
        if (gm != GameType.SURVIVAL && gm != GameType.ADVENTURE) return;
        if (!p.isInWaterOrRain() || d.buff("inferno")) return;
        if (p.getHealth() > 2) {
            p.hurtServer(level, p.damageSources().magic(), 1f);
            AbpsMod.service().actionBar(p, "<aqua>The water burns you!");
        }
        Fx.burst(level, ParticleTypes.SMOKE, p.position().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.01);
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        if (d.buff("inferno") && source.is(DamageTypeTags.IS_FIRE)) return 0;
        if (source.is(DamageTypes.LAVA)) return 1 - lavaResist(d.level);
        if (source.is(DamageTypeTags.IS_FIRE)) return 1 - fireResist(d.level);
        return 1;
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        double m = 1;
        if (hit.melee() && victim.getRemainingFireTicks() > 0) m *= 1 + burnBonus(d.level);
        if (p.level().dimension() == Level.NETHER) m *= 1 + netherBonus(d.level);
        return m;
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (hit.melee() && rand() < igniteChance(d.level)) victim.igniteForSeconds(4);
    }

    @Override
    public void onShoot(ServerPlayer p, PlayerData d, AbstractArrow arrow) {
        arrow.igniteForSeconds(100);
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        if (victim.getRemainingFireTicks() <= 0) return;
        Vec3 at = victim.position().add(0, 0.5, 0);
        double dmg = popDamage(d.level);
        ServerLevel level = level(p);
        Tasks.later(1, () -> {
            if (p.isRemoved()) return;
            for (LivingEntity le : Targets.enemiesNear(p, at, 3)) {
                Targets.damage(le, dmg, p);
                le.igniteForSeconds(3);
            }
            Fx.burst(level, ParticleTypes.EXPLOSION, at, 1, 0, 0);
            Fx.burst(level, ParticleTypes.FLAME, at, 30, 0.6, 0.12);
            Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.5f);
        });
    }

    @Override
    public boolean onProjectileHit(ServerPlayer p, PlayerData d, Entity projectile, HitResult hit) {
        if (!projectile.entityTags().contains(FIREBALL)) return false;
        ServerLevel level = level(p);
        Vec3 at = hit.getLocation();
        double dmg = fireballDamage(d.level);
        LivingEntity direct = hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && Targets.isEnemy(p, le) ? le : null;
        if (direct != null) {
            Targets.damage(direct, dmg, p);
            direct.igniteForSeconds(5);
        }
        for (LivingEntity le : Targets.enemiesNear(p, at, 3)) {
            if (le == direct) continue;
            Targets.damage(le, dmg * 0.5, p);
            le.igniteForSeconds(3);
        }
        Fx.burst(level, ParticleTypes.FLAME, at, 40, 0.8, 0.08);
        dev.abps.util.Fancy.impact(level, at, 2f, 0xFF6D00, 0xFFD600);
        dev.abps.util.Vfx.sphere(level, at, 0.3, 3, 20, dev.abps.util.Vfx.tint(0xFF6D00), 0.2f, 12, 0xFF6D00);
        Fx.burst(level, ParticleTypes.LAVA, at, 6, 0.5, 0);
        Fx.burst(level, ParticleTypes.EXPLOSION, at, 1, 0, 0);
        Fx.sound(level, at, SoundEvents.BLAZE_HURT, 0.7f, 1.4f);
        projectile.discard();
        return true; // no fire on blocks, no normal fireball damage
    }

    @Override
    public void afterDamaged(ServerPlayer p, PlayerData d, DamageSource source, float taken) {
        if (!mastered(d) || now() < d.masteryReady) return;
        float hp = p.getHealth();
        if (hp <= 0 || hp >= maxHp(p) * 0.3) return;
        d.masteryReady = now() + 60_000;
        nova(p, d);
        AbpsMod.service().actionBar(p, gradient("<bold>FLAME NOVA!</bold>") + " <gray>(ready again in 1m)");
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        Vec3 look = p.getLookAngle();
        SmallFireball fb = new SmallFireball(p.level(), p, look.scale(1.6));
        fb.setPos(p.getX() + look.x, p.getEyeY() - 0.1 + look.y, p.getZ() + look.z);
        fb.addTag(FIREBALL);
        fb.addTag("abps_ability");
        p.level().addFreshEntity(fb);
        Fx.sound(level(p), p, SoundEvents.BLAZE_SHOOT, 1f, 1f);
        dev.abps.util.Vfx.trail(level(p), fb, 40, dev.abps.util.Vfx.tint(0xFF9800), 0.24f, 0xFF6D00);
        dev.abps.util.Vfx.flash(level(p), p.getEyePosition().add(look), 1.3f, dev.abps.util.Vfx.tint(0xFFD600), 6, 0xFF6D00);
        used(p, 1);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        nova(p, d);
        used(p, 2);
        return true;
    }

    private void nova(ServerPlayer p, PlayerData d) {
        Vec3 c = p.position();
        ServerLevel level = level(p);
        double dmg = novaDamage(d.level);
        for (LivingEntity e : Targets.enemiesNear(p, c, 7)) {
            Targets.damage(e, dmg, p);
            e.igniteForSeconds(6);
            Targets.pushAway(c, e, 0.5, 0.3);
        }
        Tasks.repeat(6, 1, step -> {
            Fx.ring(level, ParticleTypes.FLAME, c, 1 + step * 1.2, 10 + step * 10);
            if (step % 2 == 0) Fx.ring(level, Fx.dust(0xFF3D00, 1.3f), c.add(0, 0.3, 0), 1 + step, 8 + step * 6);
        });
        Fx.burst(level, ParticleTypes.LAVA, c, 12, 2, 0.3, 2, 0);
        dev.abps.util.Fancy.sigil(level, c, 7, 10, 0xFF6D00, 0xFFD600, 24);
        dev.abps.util.Vfx.sphere(level, c.add(0, 1, 0), 0.5, 7, 46, dev.abps.util.Vfx.tint(0xFF6D00), 0.24f, 14, 0xFF3D00);
        Fx.sound(level, c, SoundEvents.FIRECHARGE_USE, 1f, 0.7f);
        Fx.sound(level, c, SoundEvents.BLAZE_SHOOT, 0.8f, 0.6f);
        Fx.shakeNear(level, c, 10, 5, 0.4f);
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        Vec3 dir = p.getLookAngle().scale(1.7);
        dir = new Vec3(dir.x, Math.max(0.1, Math.min(0.45, dir.y)), dir.z);
        Targets.velocity(p, dir);
        d.noFallUntil = now() + 3000;
        java.util.Set<java.util.UUID> burned = new java.util.HashSet<>();
        final Vec3 ndir = dir.normalize();
        dev.abps.util.Vfx.trail(level(p), p, 14, dev.abps.util.Vfx.tint(0xFF9800), 0.3f, 0xFF6D00);
        ServerLevel level = level(p);
        Tasks.repeat(12, 1, step -> {
            if (p.isRemoved()) return;
            Vec3 at = p.position();
            Fx.burst(level, ParticleTypes.FLAME, at.add(0, 0.3, 0), 8, 0.3, 0.2, 0.3, 0.02);
            Fx.burst(level, Fx.dust(0xFFD600, 1f), at.add(0, 0.8, 0), 3, 0.2, 0);
            if (step % 2 == 0) dev.abps.util.Vfx.ring(level, at.add(0, 1, 0), ndir, 0.4, 2.0, 14, dev.abps.util.Vfx.tint(0xFF6D00), 0.1f, 8, 0xFF6D00);
            for (LivingEntity e : Targets.enemiesNear(p, at, 1.8)) {
                if (!burned.add(e.getUUID())) continue;
                Targets.damage(e, dashDamage(d.level), p);
                e.igniteForSeconds(5);
            }
        });
        Fx.sound(level, p, SoundEvents.BLAZE_SHOOT, 1f, 1.4f);
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        Vec3 target = Targets.aimPoint(p, 40);
        double dmg = meteorDamage(d.level);
        ServerLevel level = level(p);
        Fx.sound(level, target, SoundEvents.GHAST_WARN, 1.5f, 0.6f);
        dev.abps.util.Fancy.sigil(level, target, 6, 12, 0xFF3D00, 0xFFD600, 34);
        // The meteor falls for 30 ticks, then hits
        Tasks.repeat(31, 1, step -> {
            if (step < 30) {
                double h = 20 - step * (20 / 30.0);
                Vec3 at = target.add(0, h, 0);
                Fx.burst(level, ParticleTypes.FLAME, at, 14, 0.4, 0.02);
                Fx.burst(level, ParticleTypes.LARGE_SMOKE, at, 4, 0.3, 0.01);
                Fx.burst(level, Fx.dust(0xFF3D00, 2f), at, 6, 0.5, 0);
                dev.abps.util.Vfx.beam(level, target.add(0, h + 4, 0), target.add(0, h, 0), 1.1f, dev.abps.util.Vfx.tint(0xFF6D00), 3, 0xFF3D00);
                dev.abps.util.Vfx.flash(level, at, 2.2f, dev.abps.util.Vfx.tint(0xFFD600), 3, 0xFFD600);
                if (step % 5 == 0) Fx.ring(level, ParticleTypes.FLAME, target, 6, 34);
                return;
            }
            if (p.isRemoved()) return;
            for (LivingEntity e : Targets.enemiesNear(p, target, 6)) {
                Targets.damage(e, dmg, p);
                e.igniteForSeconds(7);
                Targets.pushAway(target, e, 1.1, 0.6);
            }
            Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, target, 1, 0, 0);
            dev.abps.util.Fancy.impact(level, target.add(0, 1, 0), 4f, 0xFF6D00, 0xFFD600);
            dev.abps.util.Vfx.sphere(level, target.add(0, 1, 0), 0.5, 8, 60, dev.abps.util.Vfx.tint(0xFF6D00), 0.3f, 20, 0xFF3D00);
            dev.abps.util.Vfx.jaws(level, target, 6, 18, 3, dev.abps.util.Vfx.tint(0xFF3D00), 0xFFD600);
            dev.abps.util.Vfx.pillar(level, target, 2, 14, dev.abps.util.Vfx.tint(0xFF6D00), 3, 6, 10, 0xFF3D00);
            Fx.burst(level, ParticleTypes.LAVA, target, 30, 2.5, 0.5, 2.5, 0);
            Fx.burst(level, ParticleTypes.FLAME, target, 100, 2.5, 0.8, 2.5, 0.15);
            Fx.sound(level, target, SoundEvents.GENERIC_EXPLODE, 2f, 0.6f);
            Fx.shakeNear(level, target, 20, 12, 1f);
        });
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Inferno ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        d.setBuff("inferno", 6000);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 160, 0));
        ServerLevel level = level(p);
        double dmg = infernoDamage(d.level);
        Fx.sound(level, p, SoundEvents.BLAZE_AMBIENT, 1.5f, 0.5f);
        Fx.screen(p, Fx.TINT, 0xFF6F00, 120, 0.18f);
        dev.abps.util.Fancy.aura(level, p, 120, 0xFF3D00, 0xFFD600);
        Tasks.repeat(24, 5, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3 c = p.position();
            // Two flame spirals spinning around the player
            Fx.spiral(level, ParticleTypes.FLAME, c, 2.5, 3.5, 30, step * 0.6);
            Fx.spiral(level, Fx.dust(0xFF3D00, 1.4f), c, 4, 2, 24, -step * 0.6);
            if (step == 0) {
                dev.abps.util.Fancy.sigil(level, c, 8, 12, 0xFF3D00, 0xFFD600, 120);
                dev.abps.util.Fancy.tornado(level, c, 3, 9, 110, dev.abps.util.Vfx.tint(0xFF6D00), 0xFF3D00);
            }
            Fx.ring(level, ParticleTypes.FLAME, c, 8, 40);
            if (step % 4 != 0) return;
            for (LivingEntity e : Targets.enemiesNear(p, c, 8)) {
                Targets.damage(e, dmg, p);
                e.igniteForSeconds(4);
            }
            Fx.burst(level, ParticleTypes.LAVA, c, 8, 3, 0.3, 3, 0);
            Fx.sound(level, c, SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
        });
        used(p, ULTIMATE);
        return true;
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        dev.abps.util.Vfx.pillar(level, at, ult ? 1.4 : 0.5, ult ? 9 : 4, net.minecraft.world.level.block.Blocks.CONCRETE.orange().defaultBlockState(), 4, 8, 8, 0xFF6D00);
        dev.abps.util.Vfx.burst(level, at.add(0, 0.5, 0), net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState(), ult ? 36 : 14, 0.22, 0.2f, 20, 0xFF3D00);
        dev.abps.util.Vfx.vortex(level, at, ult ? 5 : 2.5, ult ? 18 : 8, net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState(), 0.16f, ult ? 60 : 24, 2.0, 0xFFD600);
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:coal", "minecraft:blaze_powder", "minecraft:blaze_rod", "minecraft:lava_bucket"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{40, 24, 12, 3};
    }
}
