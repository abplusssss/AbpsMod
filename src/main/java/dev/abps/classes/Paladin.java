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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Holy light: smites enemies, shields and heals you and your teammates. */
public final class Paladin extends AttributeClass {

    static final int CUE_SMITE = 11, CUE_STRIKE = 12, CUE_CHARGE_HIT = 13, CUE_SAVED = 15;
    private static final int GOLD = 0xFFD54F, WHITE = 0xFFF8E1;

    @Override public String id() { return "paladin"; }
    @Override public String name() { return "Paladin"; }
    @Override public String color() { return "#FFF59D"; }
    @Override public String color2() { return "#FFB300"; }
    @Override public Item icon() { return Items.GOLDEN_SWORD; }
    @Override public String symbol() { return "☀"; }
    @Override public String tagline() { return "Shield the weak. Judge the wicked."; }
    @Override public String mastery() { return "Once every 3 minutes, a killing blow leaves you at 1 heart with Resistance IV for 3s instead."; }

    private double undeadBonus(int lvl) { return lerp(lvl, 0.20, 0.35); }
    private double armorOfFaith(int lvl) { return lerp(lvl, 0.08, 0.15); }
    private double smiteDamage(int lvl) { return lerp(lvl, 2, 4); }
    private double judgment(int lvl) { return lerp(lvl, 8, 11); }
    private double consecrate(int lvl) { return lerp(lvl, 1.0, 1.5); }
    private double charge(int lvl) { return lerp(lvl, 6, 9); }
    private int aegisLevel(int lvl) { return lvl >= 13 ? 2 : 1; }
    private double wrath(int lvl) { return lerp(lvl, 3.5, 4.2); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Deal +" + pct(undeadBonus(lvl)) + " damage to undead",
                "Take " + pct(armorOfFaith(lvl)) + " less damage",
                "20% chance for melee hits to call down a smite for " + num(smiteDamage(lvl)) + " more damage",
                "Divine Grace: under 40% health you heal half a heart every second");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Walk speed x0.92",
                "Deal 10% less damage at night");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Judgment";
            case 2 -> "Consecration";
            case 3 -> "Divine Charge";
            case 4 -> "Aegis";
            default -> "Heaven's Wrath";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "A pillar of light strikes the enemy you look at for " + num(judgment(lvl)) + " damage (50% more on undead) and makes it glow for 4s.";
            case 2 -> "Bless the ground around you for 6s. Enemies on it take " + num(consecrate(lvl) * 2) + " damage a second, you and teammates heal 1 health a second.";
            case 3 -> "Charge 8 blocks forward. Enemies you run through take " + num(charge(lvl)) + " damage and are thrown back.";
            case 4 -> "You and teammates within 8 blocks get " + (aegisLevel(lvl) * 2 + 2) + " hearts of Absorption for 10s and Resistance I for 5s.";
            default -> "Six beams of light strike your enemies within 12 blocks over 3s. Each one deals " + num(wrath(lvl)) + " damage around where it lands.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 10;
            case 2 -> 24;
            case 3 -> 14;
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
            case 2 -> 120;
            case 4 -> 200;
            case ULTIMATE -> 60;
            default -> 0;
        };
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:gold_ingot", "minecraft:golden_apple", "minecraft:gold_block", "minecraft:enchanted_golden_apple"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{32, 6, 8, 1};
    }

    // ---- Passives ----

    private static boolean night(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return false;
        long t = Math.floorMod(p.level().getOverworldClockTime(), 24000L);
        return t >= 13000 && t < 23000;
    }

    private static boolean undead(LivingEntity e) {
        return Targets.UNDEAD.contains(e.getType());
    }

    /** You and every player near you who isn't an enemy (teammates). */
    private static List<ServerPlayer> allies(ServerPlayer p, Vec3 at, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer o : level(p).players()) {
            if ((o == p || !Targets.isEnemy(p, o)) && o.isAlive() && o.position().distanceToSqr(at) <= r * r) out.add(o);
        }
        return out;
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MOVEMENT_SPEED, "paladin_slow", -0.08, Mods.MULT);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (d.tickCount % 4 == 0 && p.getHealth() < p.getMaxHealth() * 0.4 && p.isAlive()) heal(p, 1);
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        double m = undead(victim) ? 1 + undeadBonus(d.level) : 1;
        if (night(p)) m *= 0.9;
        return m;
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return 1 - armorOfFaith(d.level);
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee() || rand() >= 0.2) return;
        double dmg = smiteDamage(d.level);
        Tasks.later(3, () -> {
            if (!victim.isAlive() || p.isRemoved()) return;
            Targets.damage(victim, dmg, p);
            Fx.sound(level(p), victim, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.8f);
        });
        if (!cue(p, CUE_SMITE, victim.position().add(0, 6, 0), victim.position().add(0, victim.getBbHeight() * 0.5, 0), p, victim, 0)) {
            Vfx.beam(level(p), victim.position().add(0, 5, 0), victim.position(), 0.08f, Vfx.tint(GOLD), 6, GOLD);
        }
    }

    @Override
    public boolean allowDeath(ServerPlayer p, PlayerData d, DamageSource source) {
        if (!mastered(d) || d.buff("paladin_saved")) return true;
        d.setBuff("paladin_saved", 180_000);
        p.setHealth(2);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 3));
        AbpsMod.service().actionBar(p, gradient("<bold>☀ The light holds you up!"));
        Fx.sound(level(p), p, SoundEvents.TOTEM_USE, 0.8f, 1.4f);
        cue(p, CUE_SAVED, p.position(), p.position().add(0, 1, 0), p, null, 0);
        return false;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        Mods.remove(p, Attributes.MOVEMENT_SPEED, "paladin_slow");
    }

    // ---- Abilities ----

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 24);
        if (t == null) {
            noTarget(p, 24);
            return false;
        }
        ServerLevel level = level(p);
        double dmg = judgment(d.level) * (undead(t) ? 1.5 : 1);
        castTarget = t;
        used(p, 1);
        Fx.sound(level, p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
        // The light comes down from above and lands a moment later
        Tasks.later(6, () -> {
            if (!t.isAlive() || p.isRemoved()) return;
            Targets.damage(t, dmg, p);
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0));
            Fx.sound(level, t, SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.8f);
            Vfx.pillar(level, t.position(), 0.5, 8, Vfx.tint(GOLD), 2, 4, 6, GOLD);
        });
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 at = p.position();
        double dmg = consecrate(d.level);
        used(p, 2);
        Fx.sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
        Tasks.repeat(12, 10, step -> {
            if (p.isRemoved()) return;
            for (LivingEntity e : Targets.enemiesNear(p, at, 5)) {
                if (Math.abs(e.getY() - at.y) < 3) Targets.damage(e, dmg, p);
            }
            for (ServerPlayer a : allies(p, at, 5)) heal(a, 0.5);
            if (step % 2 == 0) Vfx.groundRing(level, at, 4.8, 5, 24, Vfx.tint(GOLD), 0.08f, 10, GOLD);
        });
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        double dmg = charge(d.level);
        Set<LivingEntity> hit = new HashSet<>();
        Vec3 fdir = dir;
        used(p, 3);
        Fx.sound(level, p, SoundEvents.HORSE_GALLOP, 1f, 0.8f);
        Fx.sound(level, p, SoundEvents.BEACON_ACTIVATE, 0.6f, 2f);
        Tasks.repeat(6, 1, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Targets.velocity(p, fdir.scale(1.4).add(0, Math.min(0.1, p.getDeltaMovement().y), 0));
            p.fallDistance = 0;
            for (LivingEntity e : Targets.enemiesNear(p, p.position(), 2.2)) {
                if (!hit.add(e)) continue;
                Targets.damage(e, dmg, p);
                Targets.pushAway(p.position(), e, 1.3, 0.45);
                if (!cue(p, CUE_CHARGE_HIT, p.position(), e.position().add(0, e.getBbHeight() * 0.5, 0), p, e, 0)) {
                    dev.abps.util.Fancy.impact(level, e.position().add(0, 1, 0), 1.2f, GOLD, WHITE);
                }
                Fx.sound(level, e, SoundEvents.ANVIL_LAND, 0.5f, 1.6f);
            }
        });
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        int amp = aegisLevel(d.level);
        used(p, 4);
        for (ServerPlayer a : allies(p, p.position(), 8)) {
            a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, amp));
            a.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0));
            if (a != p) AbpsMod.service().actionBar(a, gradient("<bold>☀ " + p.getName().getString() + " shields you!"));
            dev.abps.util.Fancy.aura(level, a, 200, GOLD, WHITE);
        }
        Fx.sound(level, p, SoundEvents.BEACON_ACTIVATE, 1f, 1.2f);
        Fx.sound(level, p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1f, 1f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        if (Targets.enemiesNear(p, p.position(), 12).isEmpty()) {
            fail(p, "No enemies within 12 blocks.");
            return false;
        }
        ServerLevel level = level(p);
        double dmg = wrath(d.level);
        used(p, ULTIMATE);
        Fx.sound(level, p, SoundEvents.BEACON_ACTIVATE, 1f, 0.8f);
        Tasks.schedule(6, 9, 6, step -> {
            if (p.isRemoved()) return;
            List<LivingEntity> near = Targets.enemiesNear(p, p.position(), 12);
            if (near.isEmpty()) return;
            LivingEntity aim = near.get(step % near.size());
            Vec3 at = aim.position();
            for (LivingEntity e : Targets.enemiesNear(p, at, 2.5)) Targets.damage(e, dmg, p);
            Fx.sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 1.6f + step * 0.05f);
            if (!cue(p, CUE_STRIKE, at.add(0, 14, 0), at, p, aim, step)) {
                Vfx.pillar(level, at, 0.6, 12, Vfx.tint(GOLD), 2, 3, 6, GOLD);
                Vfx.groundRing(level, at, 0.4, 2.8, 16, Vfx.tint(WHITE), 0.1f, 10, WHITE);
            }
        });
        return true;
    }
}
