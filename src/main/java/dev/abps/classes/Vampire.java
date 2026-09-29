package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class Vampire extends AttributeClass {

    private static final DustParticleOptions BLOOD = Fx.dust(0x96001A, 1.3f);

    @Override public String id() { return "vampire"; }
    @Override public String name() { return "Vampire"; }
    @Override public String color() { return "#E53935"; }
    @Override public String color2() { return "#6A0DAD"; }
    @Override public Item icon() { return Items.REDSTONE; }
    @Override public String symbol() { return "❤"; }
    @Override public String tagline() { return "Drink their life. Own the night."; }
    @Override public String mastery() { return "Kills give you Strength I for 8s."; }

    private double lifesteal(int lvl) { return lerp(lvl, 0.30, 0.50); }
    private double sword(int lvl) { return lerp(lvl, 1.20, 1.35); }
    private double nightSpeed(int lvl) { return lerp(lvl, 0.30, 0.50); }
    private double killHeal(int lvl) { return lerp(lvl, 4, 8); }
    private double bindTime(int lvl) { return lerp(lvl, 2.5, 4.0); }
    private double bleedChance(int lvl) { return lerp(lvl, 0.25, 0.45); }
    private double burstDamage(int lvl) { return lerp(lvl, 5, 8); }
    private double moonTime(int lvl) { return lerp(lvl, 15, 20); }
    private double feastDrain(int lvl) { return lerp(lvl, 1.5, 2.2); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Heal " + pct(lifesteal(lvl)) + " of the damage you deal with weapons",
                "Lifesteal is 50% stronger at night",
                "Swords deal " + mult(sword(lvl)) + " damage",
                "Walk speed " + mult(1 + nightSpeed(lvl)) + " and night vision at night",
                "Kills heal you " + num(killHeal(lvl) / 2) + " hearts",
                pct(bleedChance(lvl)) + " chance for melee hits to make targets bleed (3 damage over 3s)",
                "Immune to Darkness and Blindness");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Walk speed x0.85 during the day",
                "Healing from food is 50% slower",
                "Take 30% more fire damage");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Blood Curse";
            case 2 -> "Blood Bind";
            case 3 -> "Sanguine Burst";
            case 4 -> "Blood Moon";
            default -> "Crimson Feast";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Your last target gets Darkness for 20s and Weakness for 8s.";
            case 2 -> "Freeze your last target (hit in the last 5s) for " + num(bindTime(lvl)) + "s, deal 3 hearts and heal that much.";
            case 3 -> "Blood explodes out of you. Enemies within 6 blocks take " + num(burstDamage(lvl)) + " damage and bleed. You heal 30% of it.";
            case 4 -> "For " + num(moonTime(lvl)) + "s it's always night for you, lifesteal is doubled and you deal +20% damage.";
            default -> "Blood tethers latch onto every enemy within 10 blocks for 6s, draining "
                    + num(feastDrain(lvl)) + " health from each twice a second and healing you.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 40;
            case 2 -> 35;
            case 3 -> 30;
            default -> 240;
        };
    }

    private static boolean isNight(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return false;
        long t = Math.floorMod(p.level().getOverworldClockTime(), 24000L);
        return t >= 13000 && t < 23000;
    }

    private static boolean nightLike(ServerPlayer p, PlayerData d) {
        return isNight(p) || d.buff("bloodmoon");
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        boolean overworld = p.level().dimension() == Level.OVERWORLD;
        boolean night = nightLike(p, d);
        Mods.toggle(p, night, Attributes.MOVEMENT_SPEED, "vamp_night", nightSpeed(d.level), Mods.MULT);
        Mods.toggle(p, overworld && !night, Attributes.MOVEMENT_SPEED, "vamp_day", -0.15, Mods.MULT);
        if (night) {
            MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 240) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
            }
        }
        if (d.buff("bloodmoon") && d.tickCount % 2 == 0) {
            Fx.burst(level(p), BLOOD, p.position().add(0, 1, 0), 5, 0.4, 0.6, 0.4, 0);
        }
    }

    @Override
    public boolean immuneTo(Holder<MobEffect> effect) {
        return effect.equals(MobEffects.DARKNESS) || effect.equals(MobEffects.BLINDNESS);
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        double m = hit.melee() && hit.weapon().is(ItemTags.SWORDS) ? sword(d.level) : 1;
        if (d.buff("bloodmoon")) m *= 1.2;
        return m;
    }

    private static boolean meleeWeapon(ItemStack s) {
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.TRIDENT) || s.is(Items.MACE) || s.is(ItemTags.SPEARS);
    }

    /** Hurts the target 1 damage a second for 3 seconds. */
    private void bleed(ServerPlayer p, LivingEntity t) {
        Tasks.schedule(20, 20, 3, step -> {
            if (!t.isAlive() || p.isRemoved()) return;
            Targets.damage(t, 1.0, p);
            Fx.burst((ServerLevel) t.level(), BLOOD, t.position().add(0, 1, 0), 8, 0.25, 0.4, 0.25, 0);
        });
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee() || !meleeWeapon(hit.weapon())) return;
        if (rand() < bleedChance(d.level)) bleed(p, victim);
        double ratio = lifesteal(d.level) * (nightLike(p, d) ? 1.5 : 1) * (d.buff("bloodmoon") ? 2 : 1);
        double amount = Math.min(dealt * ratio, d.buff("bloodmoon") ? 10.0 : 6.0);
        if (amount <= 0) return;
        heal(p, amount);
        ServerLevel level = level(p);
        Fx.burst(level, BLOOD, victim.position().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0);
        // A thin stream of blood flowing back to the vampire
        Fx.line(level, Fx.dust(0xC62828, 0.7f), victim.position().add(0, 1, 0), p.position().add(0, 1, 0), 0.5);
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        heal(p, killHeal(d.level));
        Fx.burst(level(p), BLOOD, p.position().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0);
        if (mastered(d)) p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0));
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return source.is(DamageTypeTags.IS_FIRE) ? 1.3 : 1;
    }

    @Override
    public double foodHealMultiplier(PlayerData d) {
        return 0.5;
    }

    private LivingEntity target(ServerPlayer p, UUID id, long time, long maxAgeMs) {
        if (id == null || now() - time > maxAgeMs) return null;
        Entity e = level(p).getEntity(id);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return null;
        if (le.distanceToSqr(p) > 32 * 32) return null;
        return le;
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        LivingEntity t = target(p, d.lastMelee, d.lastMeleeTime, 30_000);
        if (t == null) {
            noRecentTarget(p, 32);
            return false;
        }
        t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 20, 0));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 8, 0));
        if (!(t instanceof ServerPlayer)) t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 10, 0));
        ServerLevel level = level(p);
        Fx.spiral(level, BLOOD, t.position(), 0.8, 2.2, 30, 0);
        Fx.line(level, BLOOD, p.getEyePosition(), t.getEyePosition(), 0.4);
        Fx.sound(level, p, SoundEvents.WARDEN_HEARTBEAT, 1f, 1f);
        if (t instanceof ServerPlayer tp) {
            AbpsMod.service().actionBar(tp, gradient("<bold>A vampire cursed you!"));
            Fx.screen(tp, Fx.TINT, 0x5A0000, 60, 0.35f);
        }
        used(p, 1);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        LivingEntity t = target(p, d.lastMelee, d.lastMeleeTime, 5_000);
        if (t == null) {
            fail(p, "You need to hit someone in the last 5 seconds.");
            return false;
        }
        float before = t.getHealth();
        Targets.root(t, (int) (bindTime(d.level) * 20));
        Targets.damage(t, 6.0, p);
        heal(p, Math.max(0, before - t.getHealth()));
        ServerLevel level = level(p);
        // Blood chains around the target while it is frozen
        Tasks.repeat((int) (bindTime(d.level) * 4), 5, step -> {
            if (!t.isAlive()) return;
            Fx.ring(level, BLOOD, t.position().add(0, 0.3 + (step % 4) * 0.4, 0), 0.7, 12);
        });
        Fx.burst(level, BLOOD, t.position().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0);
        Fx.sound(level, t, SoundEvents.PHANTOM_BITE, 1f, 0.6f);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        List<LivingEntity> targets = Targets.enemiesNear(p, p.position(), 6);
        if (targets.isEmpty()) {
            fail(p, "No enemies nearby.");
            return false;
        }
        double total = 0;
        for (LivingEntity t : targets) {
            float before = t.getHealth();
            Targets.damage(t, burstDamage(d.level), p);
            total += Math.max(0, before - t.getHealth());
            bleed(p, t);
        }
        heal(p, total * 0.3);
        ServerLevel level = level(p);
        Tasks.repeat(5, 1, step -> Fx.ring(level, BLOOD, p.position().add(0, 0.4, 0), 1 + step * 1.25, 14 + step * 8));
        Fx.sound(level, p, SoundEvents.WARDEN_ATTACK_IMPACT, 1f, 0.8f);
        Fx.shakeNear(level, p.position(), 8, 5, 0.4f);
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        int ticks = (int) (moonTime(d.level) * 20);
        d.setBuff("bloodmoon", ticks * 50L);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks + 100, 0, true, false, false));
        ServerLevel level = level(p);
        Fx.sound(level, p, SoundEvents.WITHER_SPAWN, 0.5f, 1.4f);
        Fx.spiral(level, BLOOD, p.position(), 1.6, 3, 60, 0);
        Fx.screen(p, Fx.TINT, 0x8B0000, ticks, 0.18f);
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Crimson Feast ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        if (Targets.enemiesNear(p, p.position(), 10).isEmpty()) {
            fail(p, "No enemies within 10 blocks to feed on.");
            return false;
        }
        ServerLevel level = level(p);
        double drain = feastDrain(d.level);
        Fx.sound(level, p, SoundEvents.WARDEN_ROAR, 0.8f, 1.5f);
        Fx.screen(p, Fx.TINT, 0x8B0000, 120, 0.25f);
        Tasks.repeat(12, 10, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3 me = p.position().add(0, 1.2, 0);
            double healed = 0;
            for (LivingEntity t : Targets.enemiesNear(p, p.position(), 10)) {
                float before = t.getHealth();
                Targets.damage(t, drain, p);
                healed += Math.max(0, before - t.getHealth());
                Fx.line(level, BLOOD, t.position().add(0, 1, 0), me, 0.35);
                Fx.burst(level, BLOOD, t.position().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0);
            }
            heal(p, healed * 0.8);
            Fx.spiral(level, Fx.dust(0x6A0DAD, 1f), p.position(), 1.2, 2.4, 16, step * 0.8);
            if (step % 2 == 0) Fx.sound(level, p, SoundEvents.WARDEN_HEARTBEAT, 1f, 1.3f);
        });
        Fx.burst(level, ParticleTypes.SCULK_SOUL, p.position().add(0, 1, 0), 20, 0.5, 0.2);
        used(p, 5);
        return true;
    }
}
