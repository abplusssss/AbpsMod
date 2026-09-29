package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.mixin.AbstractArrowAccessor;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Archer extends AttributeClass {

    @Override public String id() { return "archer"; }
    @Override public String name() { return "Archer"; }
    @Override public String color() { return "#9CCC65"; }
    @Override public String color2() { return "#00BFA5"; }
    @Override public Item icon() { return Items.BOW; }
    @Override public String symbol() { return "➶"; }
    @Override public String tagline() { return "Kill them before they get close."; }
    @Override public String mastery() { return "Your arrows pierce through 1 extra target."; }

    private double arrowDmg(int lvl) { return lerp(lvl, 1.20, 1.40); }
    private double arrowSpeed(int lvl) { return lerp(lvl, 0.15, 0.30); }
    private double saveChance(int lvl) { return lerp(lvl, 0.25, 0.50); }
    private double critBonus(int lvl) { return lerp(lvl, 0.15, 0.30); }
    private double headshot(int lvl) { return lerp(lvl, 0.30, 0.60); }
    private double markBonus(int lvl) { return lerp(lvl, 0.35, 0.50); }
    private double longshot(int lvl) { return lerp(lvl, 0.20, 0.40); }
    private int volleyCount(int lvl) { return lvl >= 20 ? 9 : lvl >= 10 ? 7 : 5; }
    private int stormWaves(int lvl) { return (int) Math.round(lerp(lvl, 16, 24)); }
    private double beamDamage(int lvl) { return lerp(lvl, 12, 16); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Arrows deal " + mult(arrowDmg(lvl)) + " damage and fly " + pct(arrowSpeed(lvl)) + " faster",
                "Headshots deal +" + pct(headshot(lvl)) + " damage",
                "Fully charged shots deal +" + pct(critBonus(lvl)) + " damage",
                "Longshot: arrows deal +" + pct(longshot(lvl)) + " damage to targets 20+ blocks away",
                pct(saveChance(lvl)) + " chance to not use an arrow with a bow",
                "Hitting a target with an arrow gives you Speed I for 2s",
                "+10% walk speed while holding a bow or crossbow");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Deal 25% less melee damage",
                "Take 20% more melee damage");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Volley";
            case 2 -> "Hunter's Mark";
            case 3 -> "Grapple Arrow";
            case 4 -> "Arrow Storm";
            default -> "Sky Piercer";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Fire " + volleyCount(lvl) + " arrows in a spread. Needs a bow or crossbow.";
            case 2 -> "Mark what you look at. It glows and takes +" + pct(markBonus(lvl)) + " damage from you for 12s.";
            case 3 -> "Shoot a rope arrow. When it lands you get pulled to it. No fall damage.";
            case 4 -> "Arrows rain down where you look for " + num(stormWaves(lvl) / 4.0) + "s.";
            default -> "Charge for 1 second, then fire a beam of light 60 blocks forward. It pierces everything for "
                    + num(beamDamage(lvl)) + " damage and knocks them back.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 14;
            case 2 -> 30;
            case 3 -> 12;
            default -> 150;
        };
    }

    private static boolean holdingBow(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return s.is(Items.BOW) || s.is(Items.CROSSBOW);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        Mods.toggle(p, holdingBow(p), Attributes.MOVEMENT_SPEED, "archer_bow", 0.1, Mods.MULT);
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        double m = 1;
        if (hit.projectile() instanceof AbstractArrow arrow) {
            m *= arrowDmg(d.level);
            if (arrow.isCritArrow()) m *= 1 + critBonus(d.level);
            // Arrow above the neck counts as a headshot
            if (arrow.getY() >= victim.getEyeY() - 0.35) {
                m *= 1 + headshot(d.level);
                AbpsMod.service().actionBar(p, gradient("<bold>HEADSHOT!</bold>"));
                Fx.sound(level(p), p, SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 1.8f);
                Fx.burst(level(p), ParticleTypes.CRIT, victim.getEyePosition(), 12, 0.2, 0.3);
            }
            if (victim.distanceToSqr(p) >= 400) m *= 1 + longshot(d.level);
        }
        if (hit.melee()) m *= 0.75;
        if (d.buff("mark") && victim.getUUID().equals(d.markTarget)) m *= 1 + markBonus(d.level);
        return m;
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (hit.projectile() instanceof AbstractArrow) p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0));
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return source.getDirectEntity() instanceof LivingEntity && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypeTags.IS_EXPLOSION) ? 1.2 : 1;
    }

    private static void pierce(AbstractArrow a, int extra) {
        ((AbstractArrowAccessor) a).abps$setPierceLevel((byte) Math.min(127, a.getPierceLevel() + extra));
    }

    @Override
    public void onShoot(ServerPlayer p, PlayerData d, AbstractArrow arrow) {
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1 + arrowSpeed(d.level)));
        if (mastered(d)) pierce(arrow, 1);
        // Chance to get the arrow back (only for bows, not in creative)
        if (p.isCreative() || arrow.pickup != AbstractArrow.Pickup.ALLOWED || !p.getMainHandItem().is(Items.BOW)) return;
        if (rand() < saveChance(d.level)) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY; // no free arrow dupes
            if (!p.getInventory().add(new ItemStack(Items.ARROW))) p.spawnAtLocation(level(p), new ItemStack(Items.ARROW));
        }
    }

    private Arrow abilityArrow(ServerPlayer p, Vec3 from) {
        Arrow a = from == null ? new Arrow(p.level(), p, new ItemStack(Items.ARROW), p.getMainHandItem())
                : new Arrow(p.level(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
        if (from != null) a.setOwner(p);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        a.addTag("abps_ability");
        return a;
    }

    @Override
    public boolean onProjectileHit(ServerPlayer p, PlayerData d, Entity projectile, HitResult hit) {
        if (!projectile.entityTags().contains("abps_grapple")) return false;
        Vec3 to = hit.getLocation();
        projectile.discard();
        Vec3 pull = to.subtract(p.position());
        double dist = pull.length();
        if (dist < 1.5) return true;
        pull = pull.normalize().scale(Math.min(3.0, 0.6 + dist * 0.12)).add(0, 0.45, 0);
        Targets.velocity(p, pull);
        d.noFallUntil = now() + 4000;
        ServerLevel level = level(p);
        Fx.line(level, Fx.dust(0xD7CCC8, 0.6f), p.getEyePosition(), to, 0.5);
        Fx.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_1, 1f, 1.3f);
        return true;
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        if (!holdingBow(p)) {
            fail(p, "Hold a bow or crossbow to use Volley.");
            return false;
        }
        int n = volleyCount(d.level);
        Vec3 look = p.getLookAngle();
        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians((i - (n - 1) / 2.0) * 5);
            Vec3 dir = look.yRot((float) angle);
            Arrow a = abilityArrow(p, null);
            a.shoot(dir.x, dir.y, dir.z, 2.8f, 0f);
            a.setCritArrow(true);
            if (mastered(d)) pierce(a, 1);
            p.level().addFreshEntity(a);
        }
        ServerLevel level = level(p);
        Fx.sound(level, p, SoundEvents.ARROW_SHOOT, 1f, 0.8f);
        Fx.sound(level, p, SoundEvents.CROSSBOW_SHOOT, 1f, 1.2f);
        Fx.burst(level, ParticleTypes.CRIT, p.getEyePosition().add(look), 12, 0.3, 0.2);
        used(p, 1);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 48);
        if (t == null) {
            fail(p, "Look at a mob or player to mark them.");
            return false;
        }
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0));
        d.markTarget = t.getUUID();
        d.setBuff("mark", 12_000);
        ServerLevel level = level(p);
        Tasks.repeat(24, 10, step -> {
            if (!t.isAlive()) return;
            Fx.ring(level, Fx.dust(0x00BFA5, 0.8f), t.position().add(0, t.getBbHeight() + 0.4, 0), 0.5, 10);
        });
        Fx.line(level, ParticleTypes.END_ROD, p.getEyePosition(), t.getEyePosition(), 1.2);
        Fx.sound(level, p, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.6f);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        Arrow a = abilityArrow(p, null);
        Vec3 look = p.getLookAngle();
        a.shoot(look.x, look.y, look.z, 3.2f, 0f);
        a.setBaseDamage(0.5);
        a.addTag("abps_grapple");
        a.setGlowingTag(true);
        p.level().addFreshEntity(a);
        Fx.sound(level(p), p, SoundEvents.CROSSBOW_LOADING_END.value(), 1f, 1.4f);
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        Vec3 center = Targets.aimPoint(p, 40);
        ServerLevel level = level(p);
        Fx.sound(level, center, SoundEvents.CROSSBOW_SHOOT, 1.5f, 0.6f);
        Tasks.repeat(stormWaves(d.level), 5, step -> {
            if (p.isRemoved()) return;
            Fx.ring(level, Fx.dust(0x9CCC65, 1f), center, 4.5, 30);
            for (int i = 0; i < 5; i++) {
                double ang = rand() * Math.PI * 2, r = Math.sqrt(rand()) * 4.5;
                Vec3 from = center.add(Math.cos(ang) * r, 14, Math.sin(ang) * r);
                Arrow arrow = abilityArrow(p, from);
                arrow.shoot(0, -1, 0, 2.2f, 2f);
                arrow.setCritArrow(true);
                level.addFreshEntity(arrow);
            }
        });
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Sky Piercer ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3, false, false, false));
        Fx.sound(level, p, SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
        double dmg = beamDamage(d.level);
        Tasks.repeat(21, 1, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getLookAngle();
            if (step < 20) {
                // Light gathering in front of the archer
                Vec3 tip = eye.add(look.scale(1.2));
                double r = 1.5 - step * 0.07;
                for (int i = 0; i < 6; i++) {
                    double a = step * 0.5 + i * Math.PI / 3;
                    level.sendParticles(ParticleTypes.END_ROD, tip.x + Math.cos(a) * r, tip.y + Math.sin(a) * r, tip.z, 1, 0, 0, 0, 0);
                }
                return;
            }
            Vec3 end = eye.add(look.scale(60));
            HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (block.getType() == HitResult.Type.BLOCK) end = block.getLocation();
            AABB area = new AABB(eye, end).inflate(1.2);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, en -> Targets.isEnemy(p, en))) {
                if (e.getBoundingBox().inflate(0.6).clip(eye, end).isEmpty()) continue;
                Targets.damage(e, dmg, p);
                Targets.velocity(e, look.scale(1.2).add(0, 0.3, 0));
                Fx.burst(level, ParticleTypes.END_ROD, e.getEyePosition(), 10, 0.3, 0.2);
            }
            Fx.line(level, ParticleTypes.END_ROD, eye, end, 0.4);
            Fx.line(level, Fx.dust(0x00BFA5, 1.5f), eye, end, 0.3);
            Fx.burst(level, ParticleTypes.EXPLOSION, end, 1, 0, 0);
            Fx.sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.4f);
            Fx.shakeNear(level, eye, 10, 6, 0.6f);
        });
        used(p, 5);
        return true;
    }
}
