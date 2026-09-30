package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.net.Net;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

public final class Assassin extends AttributeClass {

    /** Players hidden by Vanish. Clients with the mod stop drawing them completely. */
    private static final Set<UUID> VANISHED = new HashSet<>();

    @Override public String id() { return "assassin"; }
    @Override public String name() { return "Assassin"; }
    @Override public String color() { return "#B388FF"; }
    @Override public String color2() { return "#4A148C"; }
    @Override public Item icon() { return Items.IRON_SWORD; }
    @Override public String symbol() { return "✦"; }
    @Override public String tagline() { return "Strike from the shadows."; }
    @Override public String mastery() { return "Backstabs give the target Poison II for 3s."; }

    private double speed(int lvl) { return lerp(lvl, 0.12, 0.25); }
    private double backstab(int lvl) { return lerp(lvl, 1.30, 1.60); }
    private double crit(int lvl) { return lerp(lvl, 0.15, 0.30); }
    private double opener(int lvl) { return lerp(lvl, 0.25, 0.45); }
    private double execute(int lvl) { return lerp(lvl, 0.20, 0.40); }
    private double vanishTime(int lvl) { return lerp(lvl, 5, 8); }
    private double cutDamage(int lvl) { return lerp(lvl, 6, 8); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Walk speed " + mult(1 + speed(lvl)),
                "Hits from behind deal " + mult(backstab(lvl)) + " damage",
                "Critical hits deal +" + pct(crit(lvl)) + " damage",
                "Your first hit on a full health target deals +" + pct(opener(lvl)) + " damage",
                "Execute: +" + pct(execute(lvl)) + " damage to targets under 30% health",
                "Mobs lose track of you when you sneak more than 6 blocks away",
                "Kills reset your Shadow Dash cooldown");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "-2 hearts max health",
                "Your armor is 15% weaker");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Vanish";
            case 2 -> "Shadow Dash";
            case 3 -> "Smoke Bomb";
            case 4 -> "Shadowstep";
            default -> "Thousand Cuts";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Vanish for " + num(vanishTime(lvl)) + "s. Your first hit out of it deals x1.6 damage.";
            case 2 -> "Dash forward fast. Your next hit in 3s deals x1.5 damage. No fall damage.";
            case 3 -> "Throw smoke. Enemies within 5 blocks are blinded for 4s and slowed. You get Speed II.";
            case 4 -> "Teleport behind what you look at (24 blocks). Your next hit in 3s deals x2 damage.";
            default -> "Blink between enemies within 12 blocks, striking 5 times for " + num(cutDamage(lvl))
                    + " damage each. The same enemy can only be hit 3 times.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 45;
            case 2 -> 10;
            case 3 -> 30;
            default -> 90;
        };
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MOVEMENT_SPEED, "assassin_speed", speed(d.level), Mods.MULT);
        Mods.set(p, Attributes.MAX_HEALTH, "assassin_hp", -4, Mods.ADD);
        Mods.set(p, Attributes.ARMOR, "assassin_armor", -0.15, Mods.MULT);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (d.vanished && !d.buff("vanish")) unvanish(p, d, "<gray>You are visible again.");
    }

    @Override
    public boolean ignoredBy(ServerPlayer p, PlayerData d, Mob mob) {
        return d.vanished || (p.isShiftKeyDown() && mob.distanceToSqr(p) > 36);
    }

    private static boolean behind(ServerPlayer attacker, LivingEntity victim) {
        Vec3 facing = new Vec3(victim.getLookAngle().x, 0, victim.getLookAngle().z);
        Vec3 toVictim = new Vec3(victim.getX() - attacker.getX(), 0, victim.getZ() - attacker.getZ());
        if (facing.lengthSqr() < 0.01 || toVictim.lengthSqr() < 0.01) return false;
        return facing.normalize().dot(toVictim.normalize()) > 0.5;
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        if (!hit.melee()) return 1;
        double m = 1;
        ServerLevel level = level(p);
        if (behind(p, victim)) {
            m *= backstab(d.level);
            if (mastered(d)) victim.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
            Fx.burst(level, ParticleTypes.CRIT, victim.getEyePosition(), 8, 0.2, 0.2);
            dev.abps.util.Vfx.slash(level, victim.position().add(0, 1, 0), p.getLookAngle(), 1.6, 2.4, 0.09f, dev.abps.util.Vfx.WHITE, 0x4A148C);
            Fx.burst(level, Fx.dust(0x4A148C, 1f), victim.position().add(0, 1, 0), 8, 0.3, 0);
        }
        // Same check vanilla uses for a critical hit
        if (p.fallDistance > 0 && !p.onGround() && !p.isInWater() && !p.isSprinting()) m *= 1 + crit(d.level);
        float vMax = victim.getMaxHealth();
        if (victim.getHealth() >= vMax - 0.01) m *= 1 + opener(d.level);
        else if (victim.getHealth() < vMax * 0.3) m *= 1 + execute(d.level);
        if (d.vanished) m *= 1.6;
        else if (d.buff("shadowstep")) {
            m *= 2.0;
            d.buffs.remove("shadowstep");
        } else if (now() < d.empoweredUntil) {
            m *= 1.5;
            d.empoweredUntil = 0;
        }
        // A backstab opener from stealth is the big hit, but capped so it can't delete a full health player
        return Math.min(m, 2.5);
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (d.vanished) unvanish(p, d, "<gray>You attacked and became visible.");
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        if (d.level < AbpsMod.config().unlockLevel(2) || d.cooldownEnd[2] == 0) return;
        d.cooldownEnd[2] = 0;
        AbpsMod.service().actionBar(p, gradient("<bold>Shadow Dash</bold>") + " <gray>was reset!");
    }

    // ---- Vanish sync ----
    public static Set<UUID> vanished() {
        return VANISHED;
    }

    public static void broadcastVanish() {
        List<UUID> list = new ArrayList<>(VANISHED);
        for (ServerPlayer other : AbpsMod.server().getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(other, Net.VanishPayload.TYPE)) ServerPlayNetworking.send(other, new Net.VanishPayload(list));
        }
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        int ticks = (int) (vanishTime(d.level) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        d.vanished = true;
        d.setBuff("vanish", ticks * 50L);
        VANISHED.add(p.getUUID());
        broadcastVanish();
        // Mobs forget about you
        for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) {
            mob.setTarget(null);
        }
        ServerLevel level = level(p);
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
        Fx.burst(level, Fx.dust(0x4A148C, 1.4f), p.position().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0);
        dev.abps.util.Fancy.impact(level, p.position().add(0, 1, 0), 1.6f, 0x4A148C, 0x311B92);
        dev.abps.util.Vfx.sphere(level, p.position().add(0, 1, 0), 0.3, 3, 24, dev.abps.util.Vfx.tint(0x4A148C), 0.18f, 14, 0x4A148C);
        Fx.sound(level, p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1f);
        Fx.screen(p, Fx.TINT, 0x311B92, ticks, 0.2f);
        used(p, 1);
        return true;
    }

    public void unvanish(ServerPlayer p, PlayerData d, String message) {
        if (!d.vanished) return;
        d.vanished = false;
        d.buffs.remove("vanish");
        VANISHED.remove(p.getUUID());
        broadcastVanish();
        p.removeEffect(MobEffects.INVISIBILITY);
        Fx.burst(level(p), ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
        dev.abps.util.Vfx.sphere(level(p), p.position().add(0, 1, 0), 3, 0.3, 20, dev.abps.util.Vfx.tint(0x4A148C), 0.16f, 12, 0x4A148C);
        if (message != null) AbpsMod.service().actionBar(p, message);
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        Vec3 dir = p.getLookAngle().scale(1.9);
        dir = new Vec3(dir.x, Math.max(0.15, Math.min(0.5, dir.y)), dir.z);
        Targets.velocity(p, dir);
        d.noFallUntil = now() + 3000;
        d.empoweredUntil = now() + 3000;
        ServerLevel level = level(p);
        Tasks.repeat(8, 1, step -> Fx.burst(level, Fx.dust(0x4A148C, 1.2f), p.position().add(0, 1, 0), 4, 0.2, 0.4, 0.2, 0));
        dev.abps.util.Vfx.trail(level, p, 10, dev.abps.util.Vfx.tint(0x4A148C), 0.32f, 0xB388FF);
        dev.abps.util.Vfx.slash(level, p.position().add(0, 1, 0), p.getLookAngle(), 2.2, 2.6, 0.12f, dev.abps.util.Vfx.WHITE, 0xB388FF);
        Fx.sound(level, p, SoundEvents.PHANTOM_FLAP, 1f, 1.4f);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        Vec3 c = p.position();
        ServerLevel level = level(p);
        for (LivingEntity e : Targets.enemiesNear(p, c, 5)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            if (e instanceof Mob mob && mob.getTarget() == p) mob.setTarget(null);
        }
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1));
        Fx.burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.add(0, 1, 0), 80, 2.5, 1, 2.5, 0.01);
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, c.add(0, 1, 0), 50, 2, 0.8, 2, 0.02);
        dev.abps.util.Vfx.sphere(level, c.add(0, 1.2, 0), 0.5, 5, 40, dev.abps.util.Vfx.tint(0x424242), 0.3f, 24, -1);
        dev.abps.util.Fancy.sigil(level, c, 5, 8, 0x4A148C, 0x311B92, 24);
        Fx.sound(level, c, SoundEvents.GENERIC_EXTINGUISH_FIRE, 1f, 0.6f);
        used(p, 3);
        return true;
    }

    /** Puts the player just behind the target, facing it. */
    private boolean blinkBehind(ServerPlayer p, LivingEntity t) {
        Vec3 back = new Vec3(t.getLookAngle().x, 0, t.getLookAngle().z);
        if (back.lengthSqr() < 0.01) back = new Vec3(0, 0, 1);
        Vec3 to = t.position().subtract(back.normalize().scale(1.3));
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(to);
        if (!p.level().getBlockState(feet).getCollisionShape(p.level(), feet).isEmpty()
                || !p.level().getBlockState(feet.above()).getCollisionShape(p.level(), feet.above()).isEmpty()) to = t.position();
        Vec3 face = t.position().subtract(to);
        float yaw = (float) (Math.toDegrees(Math.atan2(-face.x, face.z)));
        ServerLevel level = level(p);
        Fx.burst(level, ParticleTypes.PORTAL, p.position().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.5);
        dev.abps.util.Fancy.impact(level, p.position().add(0, 1, 0), 1.4f, 0x4A148C, 0xB388FF);
        dev.abps.util.Vfx.beam(level, p.position().add(0, 1, 0), to.add(0, 1, 0), 0.18f, dev.abps.util.Vfx.tint(0xB388FF), 10, 0xB388FF);
        p.teleportTo(level, to.x, to.y, to.z, Set.<Relative>of(), yaw, p.getXRot(), false);
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, to.add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        dev.abps.util.Fancy.impact(level, to.add(0, 1, 0), 1.4f, 0x4A148C, 0xB388FF);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 24);
        if (t == null) {
            noTarget(p, 24);
            return false;
        }
        blinkBehind(p, t);
        d.setBuff("shadowstep", 3000);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1));
        Fx.sound(level(p), p, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.8f);
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Thousand Cuts ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        List<LivingEntity> targets = Targets.enemiesNear(p, p.position(), 12);
        if (targets.isEmpty()) {
            fail(p, "No enemies within 12 blocks.");
            return false;
        }
        ServerLevel level = level(p);
        double dmg = cutDamage(d.level);
        Map<UUID, Integer> hits = new HashMap<>();
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
        d.noFallUntil = now() + 4000;
        Tasks.repeat(5, 5, step -> {
            if (p.isRemoved() || !p.isAlive()) return;
            // Pick the next living target that hasn't been hit 3 times yet
            LivingEntity pick = null;
            for (int i = 0; i < targets.size(); i++) {
                LivingEntity t = targets.get((step + i) % targets.size());
                if (t.isAlive() && hits.getOrDefault(t.getUUID(), 0) < 3) {
                    pick = t;
                    break;
                }
            }
            if (pick == null) return;
            hits.merge(pick.getUUID(), 1, Integer::sum);
            blinkBehind(p, pick);
            Targets.damage(pick, dmg, p);
            pick.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
            Fx.burst(level, ParticleTypes.SWEEP_ATTACK, pick.position().add(0, 1, 0), 3, 0.4, 0);
            Vec3 pc = pick.position().add(0, 1, 0);
            for (int k = 0; k < 3; k++) {
                dev.abps.util.Vfx.slash(level, pc, new Vec3(Math.cos(k * 2.1 + step), 0, Math.sin(k * 2.1 + step)), 1.8, 3.0, 0.1f, dev.abps.util.Vfx.WHITE, 0xB388FF);
            }
            dev.abps.util.Fancy.impact(level, pc, 1.3f, 0x4A148C, 0xB388FF);
            Fx.burst(level, Fx.dust(0xB388FF, 1.2f), pick.position().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0);
            Fx.sound(level, pick, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f + step * 0.1f);
        });
        Fx.sound(level, p, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        used(p, ULTIMATE);
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        unvanish(p, d, null);
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        net.minecraft.world.phys.Vec3 look = p.getLookAngle();
        dev.abps.util.Vfx.slash(level, at.add(0, 1.3, 0), look, 2.4, 2.4, 0.09f, net.minecraft.world.level.block.Blocks.CONCRETE.white().defaultBlockState(), rgb());
        dev.abps.util.Vfx.slash(level, at.add(0, 0.9, 0), look.yRot(0.4f), 2.4, 2.4, 0.09f, net.minecraft.world.level.block.Blocks.CONCRETE.purple().defaultBlockState(), rgb2());
        dev.abps.util.Vfx.burst(level, at.add(0, 1, 0), net.minecraft.world.level.block.Blocks.CONCRETE.black().defaultBlockState(), ult ? 30 : 12, 0.2, 0.14f, 14, rgb2());
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:spider_eye", "minecraft:phantom_membrane", "minecraft:ender_pearl", "minecraft:ender_eye"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{32, 12, 10, 4};
    }
}
