package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import dev.abps.util.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The blade: fast dashes, crescent cuts that fly, parries, and a storm of blades. */
public final class Samurai extends AttributeClass {

    static final int CUE_CRIT = 11, CUE_PARRY = 12, CUE_IAIDO_HIT = 13, CUE_COUNTER = 14, CUE_CUT = 15, CUE_FINAL = 16;
    private static final int STEEL = 0xECEFF1, RED = 0xD50000;

    /** {last sword hit time in ms, hits in the current combo}. */
    private final Map<UUID, long[]> combo = new HashMap<>();

    @Override public String id() { return "samurai"; }
    @Override public String name() { return "Samurai"; }
    @Override public String color() { return "#ECEFF1"; }
    @Override public String color2() { return "#D50000"; }
    @Override public Item icon() { return Items.IRON_SWORD; }
    @Override public String symbol() { return "⛩"; }
    @Override public String tagline() { return "One cut is all it takes."; }
    @Override public String mastery() { return "Kills reset Iaido."; }

    private double swordBonus(int lvl) { return lerp(lvl, 1.10, 1.20); }
    private double critCut(int lvl) { return lerp(lvl, 3, 5); }
    private double parryChance(int lvl) { return lerp(lvl, 0.12, 0.20); }
    private double iaido(int lvl) { return lerp(lvl, 9, 12); }
    private double crescent(int lvl) { return lerp(lvl, 7, 10); }
    private double counter(int lvl) { return lerp(lvl, 8, 11); }
    private double flurry(int lvl) { return lerp(lvl, 3.5, 4.5); }
    private double finale(int lvl) { return lerp(lvl, 8, 10); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Swords deal " + mult(swordBonus(lvl)) + " damage, sweeps x1.5",
                "Every 3rd sword hit in a row is a critical cut for " + num(critCut(lvl)) + " more damage",
                pct(parryChance(lvl)) + " chance to parry a melee hit and take no damage");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Bows and crossbows deal 30% less damage",
                "Take 10% more damage from explosions");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Iaido";
            case 2 -> "Crescent Moon";
            case 3 -> "Counter Stance";
            case 4 -> "Focus";
            default -> "Blade Storm";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Dash 7 blocks in one draw of the blade. A beat later every enemy you passed takes " + num(iaido(lvl)) + " damage.";
            case 2 -> "Swing a crescent of steel that flies 14 blocks. Enemies it passes take " + num(crescent(lvl)) + " damage and are knocked back.";
            case 3 -> "Hold a guard for 1.5s. The next melee hit on you is parried, and you appear behind the attacker and cut for " + num(counter(lvl)) + ".";
            case 4 -> "For 8s you deal 20% more damage, attack 30% faster and every 2nd sword hit is a critical cut.";
            default -> "Vanish and cut up to 5 enemies within 10 blocks three times each for " + num(flurry(lvl)) + ", then finish with one cut through all of them for "
                    + num(finale(lvl)) + ".";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 10;
            case 2 -> 12;
            case 3 -> 20;
            default -> 40;
        };
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return switch (idx) {
            case 3 -> 30;
            case 4 -> 160;
            default -> 0;
        };
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:iron_ingot", "minecraft:bamboo", "minecraft:diamond", "minecraft:netherite_scrap"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{32, 48, 10, 3};
    }

    // ---- Passives ----

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        double m = 1;
        if (hit.projectile() != null && (hit.weapon().is(Items.BOW) || hit.weapon().is(Items.CROSSBOW))) m *= 0.7;
        if (hit.melee() && hit.weapon().is(ItemTags.SWORDS)) m *= swordBonus(d.level);
        if (hit.sweep()) m *= 1.5;
        if (d.buff("focus")) m *= 1.2;
        return m;
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return source.is(DamageTypeTags.IS_EXPLOSION) ? 1.1 : 1;
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        Mods.toggle(p, d.buff("focus"), Attributes.ATTACK_SPEED, "samurai_focus", 0.3, Mods.MULT);
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee() || hit.sweep() || !hit.weapon().is(ItemTags.SWORDS)) return;
        long[] c = combo.computeIfAbsent(p.getUUID(), k -> new long[2]);
        long now = now();
        c[1] = now - c[0] <= 2000 ? c[1] + 1 : 1;
        c[0] = now;
        int need = d.buff("focus") ? 2 : 3;
        if (c[1] < need) return;
        c[1] = 0;
        double dmg = critCut(d.level);
        Tasks.later(2, () -> {
            if (victim.isAlive() && !p.isRemoved()) Targets.damage(victim, dmg, p);
        });
        Fx.sound(level(p), victim, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.3f);
        if (!cue(p, CUE_CRIT, p.position(), victim.position().add(0, victim.getBbHeight() * 0.55, 0), p, victim, 0)) {
            Vfx.slash(level(p), victim.position(), p.getLookAngle(), 1.4, 2.4, 0.08f, Vfx.tint(STEEL), STEEL);
        }
    }

    @Override
    public boolean allowDamage(ServerPlayer p, PlayerData d, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return true;
        if (attacker.distanceToSqr(p) > 25 || source.is(DamageTypeTags.IS_PROJECTILE)) return true;
        if (d.buff("counter")) {
            d.setBuff("counter", 0);
            counterCut(p, d, attacker);
            return false;
        }
        if (rand() >= parryChance(d.level)) return true;
        Fx.sound(level(p), p, SoundEvents.SHIELD_BLOCK.value(), 1f, 1.4f);
        Fx.sound(level(p), p, SoundEvents.ANVIL_LAND, 0.3f, 2f);
        AbpsMod.service().actionBar(p, gradient("<bold>⛩ Parried!"));
        if (!cue(p, CUE_PARRY, p.position().add(0, 1.2, 0), attacker.position().add(0, attacker.getBbHeight() * 0.55, 0), p, attacker, 0)) {
            Vfx.flash(level(p), p.position().add(0, 1.2, 0), 0.9f, Vfx.WHITE, 5, STEEL);
        }
        return false;
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        if (mastered(d)) d.cooldownEnd[1] = 0;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        combo.remove(p.getUUID());
        Mods.remove(p, Attributes.ATTACK_SPEED, "samurai_focus");
    }

    // ---- Abilities ----

    /** Where to stand to be right behind an entity, facing it. */
    private static Vec3 behind(LivingEntity t) {
        Vec3 f = Vec3.directionFromRotation(0, t.getYRot());
        return t.position().subtract(f.scale(1.3));
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
    }

    private void counterCut(ServerPlayer p, PlayerData d, LivingEntity attacker) {
        ServerLevel level = level(p);
        Vec3 from = p.position();
        Vec3 to = behind(attacker);
        if (level.noCollision(p, p.getBoundingBox().move(to.subtract(p.position())))) {
            p.teleportTo(level, to.x, to.y, to.z, Set.<Relative>of(), yawTo(to, attacker.position()), p.getXRot(), false);
        }
        double dmg = counter(d.level);
        Tasks.later(2, () -> {
            if (attacker.isAlive() && !p.isRemoved()) Targets.damage(attacker, dmg, p);
        });
        Fx.sound(level, p, SoundEvents.SHIELD_BLOCK.value(), 1f, 1.2f);
        Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        AbpsMod.service().actionBar(p, gradient("<bold>⛩ Counter!"));
        if (!cue(p, CUE_COUNTER, from.add(0, 1.2, 0), attacker.position().add(0, attacker.getBbHeight() * 0.55, 0), p, attacker, 0)) {
            Vfx.slash(level, attacker.position(), p.getLookAngle(), 1.8, 2.8, 0.1f, Vfx.tint(RED), RED);
        }
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 start = p.position();
        Vec3 fdir = dir;
        double dmg = iaido(d.level);
        castAim = start.add(dir.scale(7));
        used(p, 1);
        Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.6f);
        Tasks.repeat(4, 1, step -> {
            if (p.isRemoved()) return;
            Targets.velocity(p, fdir.scale(1.75).add(0, Math.min(0.05, p.getDeltaMovement().y), 0));
            p.fallDistance = 0;
        });
        // The cut lands a beat after you have passed
        Tasks.later(9, () -> {
            if (p.isRemoved()) return;
            Vec3 end = p.position();
            Vec3 seg = end.subtract(start);
            double len = Math.max(0.5, seg.length());
            Vec3 sd = seg.scale(1 / len);
            for (LivingEntity e : Targets.enemiesNear(p, start.add(seg.scale(0.5)), len / 2 + 2)) {
                Vec3 c = e.position();
                double along = Math.max(0, Math.min(len, c.subtract(start).dot(sd)));
                if (start.add(sd.scale(along)).distanceTo(c) > 2.0) continue;
                Targets.damage(e, dmg, p);
                if (!cue(p, CUE_IAIDO_HIT, start, e.position().add(0, e.getBbHeight() * 0.55, 0), p, e, 0)) {
                    Vfx.slash(level, e.position(), sd, 1.5, 2.6, 0.08f, Vfx.tint(STEEL), STEEL);
                }
            }
            Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.6f);
        });
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 start = p.position().add(0, 1.0, 0);
        Vec3 fdir = dir;
        double dmg = crescent(d.level);
        Set<LivingEntity> hit = new HashSet<>();
        used(p, 2);
        Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        Fx.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.6f, 1.6f);
        Tasks.repeat(7, 1, step -> {
            if (p.isRemoved()) return;
            Vec3 at = start.add(fdir.scale(2 * (step + 1)));
            for (LivingEntity e : Targets.enemiesNear(p, at, 2.3)) {
                if (!hit.add(e)) continue;
                Targets.damage(e, dmg, p);
                Targets.pushAway(at.subtract(fdir), e, 0.9, 0.3);
            }
            Vfx.slash(level, at.subtract(0, 1, 0), fdir, 1.8, 2.6, 0.07f, Vfx.tint(STEEL), STEEL);
        });
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        d.setBuff("counter", 1500);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, true, false, true));
        used(p, 3);
        Fx.sound(level(p), p, SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 1.4f);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        d.setBuff("focus", 8000);
        used(p, 4);
        Fx.sound(level(p), p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 2f);
        Fx.sound(level(p), p, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 0.5f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        List<LivingEntity> near = Targets.enemiesNear(p, p.position(), 10);
        if (near.isEmpty()) {
            fail(p, "No enemies within 10 blocks.");
            return false;
        }
        near.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        List<LivingEntity> targets = new ArrayList<>(near.subList(0, Math.min(5, near.size())));
        ServerLevel level = level(p);
        Vec3 home = p.position();
        float yaw = p.getYRot(), pitch = p.getXRot();
        double cut = flurry(d.level), last = finale(d.level);
        int per = 8;
        used(p, ULTIMATE);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, targets.size() * per + 14, 3, true, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, targets.size() * per + 10, 0, true, false, false));
        Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity t = targets.get(i);
            Tasks.later((long) i * per + 2, () -> {
                if (p.isRemoved() || !t.isAlive()) return;
                Vec3 to = behind(t);
                if (level.noCollision(p, p.getBoundingBox().move(to.subtract(p.position())))) {
                    p.teleportTo(level, to.x, to.y, to.z, Set.<Relative>of(), yawTo(to, t.position()), 0, false);
                }
            });
            for (int k = 0; k < 3; k++) {
                int kk = k;
                Tasks.later((long) i * per + 3 + k * 2L, () -> {
                    if (p.isRemoved() || !t.isAlive()) return;
                    Targets.damage(t, cut, p);
                    Fx.sound(level, t, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.2f + kk * 0.2f);
                    if (!cue(p, CUE_CUT, p.position().add(0, 1.2, 0), t.position().add(0, t.getBbHeight() * 0.55, 0), p, t, kk)) {
                        Vfx.slash(level, t.position(), p.getLookAngle(), 1.5, 2.8, 0.08f, Vfx.tint(kk == 2 ? RED : STEEL), kk == 2 ? RED : STEEL);
                    }
                });
            }
        }
        Tasks.later((long) targets.size() * per + 6, () -> {
            if (p.isRemoved()) return;
            p.teleportTo(level, home.x, home.y, home.z, Set.<Relative>of(), yaw, pitch, false);
            Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.5f);
            Fx.sound(level, p, SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 1.8f);
            for (LivingEntity t : targets) {
                if (!t.isAlive()) continue;
                Targets.damage(t, last, p);
                if (!cue(p, CUE_FINAL, home.add(0, 1.2, 0), t.position().add(0, t.getBbHeight() * 0.55, 0), p, t, 0)) {
                    Vfx.slash(level, t.position(), new Vec3(1, 0, 0), 2.2, 3.2, 0.12f, Vfx.tint(RED), RED);
                }
            }
        });
        return true;
    }
}
