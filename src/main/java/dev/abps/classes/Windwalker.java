package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.Service;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Windwalker extends AttributeClass {

    @Override public String id() { return "windwalker"; }
    @Override public String name() { return "Windwalker"; }
    @Override public String color() { return "#E0F7FA"; }
    @Override public String color2() { return "#29B6F6"; }
    @Override public Item icon() { return Items.FEATHER; }
    @Override public String symbol() { return "☁"; }
    @Override public String tagline() { return "The sky is your playground."; }
    @Override public String mastery() { return "Double jump twice before landing."; }

    private double jumpCooldown(int lvl) { return lerp(lvl, 4, 1.5); }
    private double jump(int lvl) { return lerp(lvl, 0.08, 0.15); }
    private double dodge(int lvl) { return lerp(lvl, 0.12, 0.25); }
    private double updraftDamage(int lvl) { return lerp(lvl, 3, 5); }
    private double gustDamage(int lvl) { return lerp(lvl, 5, 8); }
    private double tailwindTime(int lvl) { return lerp(lvl, 8, 12); }
    private double tornadoDamage(int lvl) { return lerp(lvl, 2, 3.5); }
    private double boltDamage(int lvl) { return lerp(lvl, 5, 7); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Double jump (press jump in the air) every " + num(jumpCooldown(lvl)) + "s",
                "You never take fall damage",
                pct(dodge(lvl)) + " chance to dodge melee hits and arrows",
                "Your arrows fly straight with no drop for 2s",
                "Your melee hits knock enemies back further",
                "+" + pct(jump(lvl)) + " jump power");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "-1 heart max health",
                "Walk speed x0.85 while wearing a diamond or netherite chestplate");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Updraft";
            case 2 -> "Gust";
            case 3 -> "Tailwind";
            case 4 -> "Tornado";
            default -> "Eye of the Storm";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Launch high into the air. Enemies near you get thrown up and take " + num(updraftDamage(lvl)) + " damage.";
            case 2 -> "Blast enemies in front of you away and deal " + num(gustDamage(lvl)) + " damage.";
            case 3 -> "Get Speed II and Jump Boost II for " + num(tailwindTime(lvl)) + "s.";
            case 4 -> "Make a tornado where you look for 5s. It pulls enemies in, lifts them and deals " + num(tornadoDamage(lvl)) + " damage every half second.";
            default -> "Rise into the sky. For 6s lightning strikes an enemy within 14 blocks every second for "
                    + num(boltDamage(lvl)) + " damage. You take no fall damage.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 20;
            case 2 -> 20;
            case 3 -> 30;
            default -> 180;
        };
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MAX_HEALTH, "wind_hp", -2, Mods.ADD);
        Mods.set(p, Attributes.JUMP_STRENGTH, "wind_jump", jump(d.level), Mods.MULT);
    }

    private static boolean survival(ServerPlayer p) {
        GameType gm = p.gameMode();
        return gm == GameType.SURVIVAL || gm == GameType.ADVENTURE;
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        ItemStack chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        boolean heavy = chest.is(Items.DIAMOND_CHESTPLATE) || chest.is(Items.NETHERITE_CHESTPLATE);
        Mods.toggle(p, heavy, Attributes.MOVEMENT_SPEED, "wind_heavy", -0.15, Mods.MULT);
        if (p.onGround()) d.airJumps = 0;

        // Players with the mod send their jump press. Everyone else double jumps with the fly toggle.
        if (Service.hasMod(p) || !survival(p)) {
            if (d.managedFlight && survival(p)) {
                p.getAbilities().mayfly = false;
                p.onUpdateAbilities();
            }
            d.managedFlight = false;
            return;
        }
        if (!d.managedFlight && p.getAbilities().mayfly) return; // another mod gave flight
        if (!p.getAbilities().mayfly && p.onGround() && now() >= d.doubleJumpReady) {
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
            d.managedFlight = true;
        }
    }

    @Override
    public void onAirJump(ServerPlayer p, PlayerData d) {
        if (!survival(p) || now() < d.doubleJumpReady) return;
        int maxJumps = mastered(d) ? 2 : 1;
        if (d.airJumps >= maxJumps) return;
        d.airJumps++;
        if (d.airJumps >= maxJumps) {
            d.doubleJumpReady = now() + (long) (jumpCooldown(d.level) * 1000);
            if (d.managedFlight) p.getAbilities().mayfly = false;
        }
        Vec3 v = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (v.lengthSqr() > 0.01) v = v.normalize().scale(0.5);
        Targets.velocity(p, new Vec3(v.x, 0.8, v.z));
        p.resetFallDistance();
        d.noFallUntil = now() + 4000;
        ServerLevel level = level(p);
        Fx.ring(level, ParticleTypes.CLOUD, p.position(), 0.7, 12);
        Fx.burst(level, ParticleTypes.SMALL_GUST, p.position(), 1, 0, 0);
        dev.abps.util.Vfx.groundRing(level, p.position(), 0.4, 2.4, 16, dev.abps.util.Vfx.tint(0xE0F7FA), 0.09f, 8, 0x29B6F6);
        dev.abps.util.Vfx.sphere(level, p.position().add(0, 0.3, 0), 0.3, 1.8, 12, dev.abps.util.Vfx.tint(0xE0F7FA), 0.12f, 8, 0x29B6F6);
        Fx.sound(level, p, SoundEvents.BREEZE_JUMP, 0.8f, 1.2f);
    }

    @Override
    public boolean allowDamage(ServerPlayer p, PlayerData d, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FALL)) return false;
        boolean dodgeable = source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof LivingEntity;
        if (!dodgeable || source.is(DamageTypeTags.IS_EXPLOSION) || rand() >= dodge(d.level)) return true;
        ServerLevel level = level(p);
        Fx.burst(level, ParticleTypes.CLOUD, p.position().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.02);
        Fx.sound(level, p, SoundEvents.BREEZE_DEFLECT, 0.8f, 1.4f);
        AbpsMod.service().actionBar(p, gradient("<bold>Dodged!"));
        return false;
    }

    @Override
    public void onShoot(ServerPlayer p, PlayerData d, AbstractArrow arrow) {
        arrow.setNoGravity(true);
        Tasks.later(40, () -> {
            if (!arrow.isRemoved()) arrow.setNoGravity(false);
        });
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee()) return;
        Vec3 push = new Vec3(victim.getX() - p.getX(), 0, victim.getZ() - p.getZ());
        if (push.lengthSqr() < 0.01) return;
        Vec3 add = push.normalize().scale(0.6).add(0, 0.15, 0);
        // Wait a tick so vanilla knockback is applied first
        Tasks.later(1, () -> {
            if (victim.isAlive()) Targets.velocity(victim, victim.getDeltaMovement().add(add));
        });
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        Vec3 c = p.position();
        ServerLevel level = level(p);
        for (LivingEntity e : Targets.enemiesNear(p, c, 4)) {
            Targets.damage(e, updraftDamage(d.level), p);
            Targets.velocity(e, new Vec3(e.getDeltaMovement().x, 1.0, e.getDeltaMovement().z));
        }
        Targets.velocity(p, new Vec3(p.getDeltaMovement().x, 1.4, p.getDeltaMovement().z));
        Fx.burst(level, ParticleTypes.GUST, c, 1, 0, 0);
        Fx.spiral(level, ParticleTypes.CLOUD, c, 2, 4, 40, 0);
        dev.abps.util.Fancy.tornado(level, c, 1.8, 6, 30, dev.abps.util.Vfx.tint(0xE0F7FA), 0x29B6F6);
        dev.abps.util.Vfx.groundRing(level, c, 0.5, 4.5, 24, dev.abps.util.Vfx.tint(0x29B6F6), 0.14f, 10, 0x29B6F6);
        Fx.sound(level, c, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 1f);
        used(p, 1);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double dmg = gustDamage(d.level);
        ServerLevel level = level(p);
        for (LivingEntity e : Targets.enemiesNear(p, p.position(), 10)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            if (to.lengthSqr() < 0.01 || look.dot(to.normalize()) < 0.55) continue;
            Targets.damage(e, dmg, p);
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            Targets.velocity(e, flat.scale(1.8).add(0, 0.55, 0));
        }
        for (int i = 1; i <= 7; i++) Fx.burst(level, ParticleTypes.GUST, eye.add(look.scale(i * 1.3)), 1, 0, 0);
        dev.abps.util.Vfx.slash(level, eye.add(look.scale(1.5)), look, 3.5, 2.4, 0.16f, dev.abps.util.Vfx.tint(0xE0F7FA), 0x29B6F6);
        dev.abps.util.Vfx.slash(level, eye.add(look.scale(3)), look, 5.5, 1.8, 0.14f, dev.abps.util.Vfx.WHITE, 0x29B6F6);
        dev.abps.util.Fancy.laser(level, eye.add(look), eye.add(look.scale(10)), 0.3f, 0xE0F7FA, 0xFFFFFF, 6);
        Fx.sound(level, p, SoundEvents.BREEZE_SHOOT, 1f, 0.8f);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        int ticks = (int) (tailwindTime(d.level) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ticks, 1));
        ServerLevel level = level(p);
        dev.abps.util.Fancy.aura(level, p, ticks, 0xE0F7FA, 0x29B6F6);
        dev.abps.util.Vfx.trail(level, p, Math.min(ticks, 400), dev.abps.util.Vfx.tint(0xE0F7FA), 0.16f, 0x29B6F6);
        Tasks.repeat(ticks / 5, 5, step -> {
            if (!p.isRemoved()) Fx.burst(level, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0.01);
        });
        Fx.sound(level, p, SoundEvents.BREEZE_IDLE_AIR, 1f, 1.4f);
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        Vec3 center = Targets.aimPoint(p, 30);
        double dmg = tornadoDamage(d.level);
        ServerLevel level = level(p);
        dev.abps.util.Fancy.tornado(level, center, 3, 8, 104, dev.abps.util.Vfx.tint(0xE0F7FA), 0x29B6F6);
        dev.abps.util.Fancy.sigil(level, center, 6, 8, 0x29B6F6, 0xE0F7FA, 104);
        Tasks.repeat(20, 5, step -> {
            if (p.isRemoved()) return;
            // Spinning column of wind
            Fx.spiral(level, ParticleTypes.CLOUD, center, 2.5, 6, 30, step * 0.9);
            Fx.spiral(level, Fx.dust(0x29B6F6, 1f), center, 2, 5, 20, -step * 0.9);
            boolean hitTick = step % 2 == 0;
            for (LivingEntity e : Targets.enemiesNear(p, center, 6)) {
                Vec3 pull = new Vec3(center.x - e.getX(), 0, center.z - e.getZ());
                Vec3 v = pull.lengthSqr() > 0.01 ? pull.normalize().scale(0.35) : Vec3.ZERO;
                Targets.velocity(e, new Vec3(v.x, step == 19 ? 1.2 : 0.25, v.z));
                if (hitTick) Targets.damage(e, dmg, p);
            }
            if (step % 4 == 0) Fx.sound(level, center, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.6f);
        });
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Eye of the Storm ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Targets.velocity(p, new Vec3(0, 1.3, 0));
        Fx.sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 1.4f);
        dev.abps.util.Fancy.aura(level, p, 130, 0x29B6F6, 0xE0F7FA);
        dev.abps.util.Fancy.tornado(level, p.position(), 3, 8, 120, dev.abps.util.Vfx.tint(0xE0F7FA), 0x29B6F6);
        double dmg = boltDamage(d.level);
        Tasks.repeat(24, 5, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Fx.ring(level, Fx.dust(0x29B6F6, 1.2f), p.position(), 1.5, 16);
            Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, p.position().add(0, 1, 0), 8, 0.6, 0.1);
            if (step % 4 != 0 || step == 0) return;
            List<LivingEntity> near = Targets.enemiesNear(p, p.position(), 14);
            if (near.isEmpty()) return;
            LivingEntity t = near.get((int) (rand() * near.size()));
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(t.getX(), t.getY(), t.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            Targets.damage(t, dmg, p);
            t.igniteForSeconds(2);
            Fx.line(level, ParticleTypes.ELECTRIC_SPARK, p.position().add(0, 1, 0), t.position().add(0, 1, 0), 0.4);
            dev.abps.util.Fancy.lightning(level, t.position().add(0, 18, 0), t.position().add(0, 0.5, 0), 0x29B6F6, 0xFFFFFF);
        });
        used(p, ULTIMATE);
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        if (d.managedFlight && survival(p)) {
            p.getAbilities().flying = false;
            p.getAbilities().mayfly = false;
            p.onUpdateAbilities();
        }
        d.managedFlight = false;
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        int rings = ult ? 8 : 4;
        for (int i = 0; i < rings; i++) {
            final int h = i;
            dev.abps.util.Tasks.later(i * 2L, () -> {
                dev.abps.util.Vfx.groundRing(level, at.add(0, h * 0.6, 0), 0.6, ult ? 4.5 : 2.6, 22, dev.abps.util.Vfx.tint(0xE0F7FA), 0.09f, 10, 0x29B6F6);
            });
        }
        dev.abps.util.Vfx.vortex(level, at, ult ? 4 : 2, ult ? 16 : 8, net.minecraft.world.level.block.Blocks.CONCRETE.white().defaultBlockState(), 0.12f, ult ? 60 : 24, 1.8, 0xE0F7FA);
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:feather", "minecraft:phantom_membrane", "minecraft:breeze_rod", "minecraft:shulker_shell"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{40, 14, 10, 3};
    }
}
