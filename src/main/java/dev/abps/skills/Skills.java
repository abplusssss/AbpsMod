package dev.abps.skills;

import dev.abps.AbpsMod;
import dev.abps.classes.AttributeClass;
import dev.abps.data.PlayerData;
import dev.abps.skills.SkillTree.Kind;
import dev.abps.skills.SkillTree.Node;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/** Applies a player's skill tree: buying nodes, respec, and every node's effect on stats, damage and cooldowns. */
public final class Skills {

    private Skills() {
    }

    public static List<Node> tree(AttributeClass c) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= 5; i++) names.add(i == 5 && c.abilityCount() < 5 ? "" : c.abilityName(i));
        return SkillTree.build(c.abilityCount(), names, c.adminOnly() ? "pvp" : c.role().id);
    }

    /**
     * Points earned at this level. They grow evenly with level so that reaching max level gives exactly enough to take
     * every node in the tree.
     */
    public static int earned(PlayerData d) {
        AttributeClass c = AbpsMod.service().cls(d);
        int total = 0;
        if (c != null) for (Node n : tree(c)) if (n.kind() != Kind.ROOT) total++;
        if (total == 0) total = 30;
        int max = AbpsMod.config().maxLevel;
        int lv = Math.max(1, Math.min(max, d.level));
        return (int) Math.ceil((lv - 1) * (double) total / (max - 1));
    }

    /** Points earned but not spent yet. */
    public static int points(PlayerData d) {
        return Math.max(0, earned(d) - d.skills.size());
    }

    public static boolean owns(PlayerData d, String id) {
        return d.skills.contains(id);
    }

    private static double sum(PlayerData d, AttributeClass c, Kind k) {
        if (c == null || d.skills.isEmpty()) return 0;
        return SkillTree.sum(tree(c), d.skills, k);
    }

    // ------------------------------------------------------------------ buying

    public static void buy(ServerPlayer p, String id) {
        var sv = AbpsMod.service();
        PlayerData d = sv.data(p);
        AttributeClass c = sv.cls(d);
        if (c == null) return;
        List<Node> t = tree(c);
        Node n = SkillTree.find(t, id);
        if (n == null || n.kind() == Kind.ROOT) return;
        if (owns(d, id)) {
            sv.actionBar(p, "<gray>You already have " + n.name() + ".");
            return;
        }
        if (!SkillTree.available(n, d.skills)) {
            sv.actionBar(p, "<red>Take the skill before it first.");
            return;
        }
        if (points(d) <= 0) {
            sv.actionBar(p, "<red>No skill points left. <gray>Buy one with <yellow>!Upgrade</yellow> or the Upgrade button.");
            return;
        }
        d.skills.add(id);
        AbpsMod.data().save(p, d);
        sv.reapply(p);
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.3f);
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.8f);
        sv.actionBar(p, c.gradient("<bold>✦ " + n.name() + "</bold>") + " <gray>learned. " + (points(d) > 0 ? points(d) + " point" + (points(d) == 1 ? "" : "s") + " left." : ""));
        if (n.kind() == Kind.ABILITY) {
            sv.send(p, "<aqua>New ability: " + c.gradient("<bold>" + c.abilityName((int) n.value()) + "</bold>") + " <gray>(" + sv.keyName(p, (int) n.value()) + ")");
        }
    }

    /** Takes every node back so the points can be spent again. Free, but not in a fight or a dungeon. */
    public static void respec(ServerPlayer p) {
        var sv = AbpsMod.service();
        PlayerData d = sv.data(p);
        if (d.inCombat()) {
            sv.send(p, "<red>You can't reset your skills in combat.");
            return;
        }
        if (dev.abps.dungeon.Dungeons.runOf(p) != null) {
            sv.send(p, "<red>You can't reset your skills in a dungeon.");
            return;
        }
        if (d.skills.isEmpty()) {
            sv.send(p, "<gray>You haven't spent any skill points yet.");
            return;
        }
        int back = d.skills.size();
        d.skills.clear();
        AbpsMod.data().save(p, d);
        sv.reapply(p);
        sv.send(p, "<green>Skills reset. <gray>You have <white>" + back + "</white> points to spend again.");
    }

    /** Spends points on the ability trunk first (for players coming from the old level system, or given levels by an admin). */
    public static void autoAllocate(PlayerData d, AttributeClass c) {
        if (c == null) return;
        List<Node> t = tree(c);
        boolean changed = true;
        while (points(d) > 0 && changed) {
            changed = false;
            for (Node n : t) {
                if (n.kind() == Kind.ABILITY && SkillTree.available(n, d.skills)) {
                    d.skills.add(n.id());
                    changed = true;
                    break;
                }
            }
        }
    }

    /** After the level goes down (admin command), drops the newest nodes until they fit. */
    public static void trim(PlayerData d) {
        while (d.skills.size() > earned(d)) d.skills.removeLast();
    }

    // ------------------------------------------------------------------ effects

    /** Stat nodes, re-added every time the player's stats are rebuilt. */
    public static void apply(ServerPlayer p, PlayerData d, AttributeClass c) {
        double hp = sum(d, c, Kind.HEALTH), kb = sum(d, c, Kind.KNOCKBACK), speed = sum(d, c, Kind.SPEED), as = sum(d, c, Kind.ATTACK_SPEED),
                luck = sum(d, c, Kind.LUCK), brk = sum(d, c, Kind.BREAK_SPEED);
        if (hp > 0) Mods.set(p, Attributes.MAX_HEALTH, "skill_hp", hp, Mods.ADD);
        if (kb > 0) Mods.set(p, Attributes.KNOCKBACK_RESISTANCE, "skill_kb", kb, Mods.ADD);
        if (speed > 0) Mods.set(p, Attributes.MOVEMENT_SPEED, "skill_speed", speed, Mods.MULT);
        if (as > 0) Mods.set(p, Attributes.ATTACK_SPEED, "skill_as", as, Mods.MULT);
        if (luck > 0) Mods.set(p, Attributes.LUCK, "skill_luck", luck, Mods.ADD);
        if (brk > 0) Mods.set(p, Attributes.BLOCK_BREAK_SPEED, "skill_break", brk, Mods.MULT);
    }

    /** Damage multiplier for a hit this player deals. */
    public static double outgoing(ServerPlayer p, PlayerData d, AttributeClass c, LivingEntity victim) {
        if (d.skills.isEmpty()) return 1;
        double m = 1 + sum(d, c, Kind.DAMAGE);
        if (victim instanceof ServerPlayer) m += sum(d, c, Kind.PVP_DAMAGE);
        else m += sum(d, c, Kind.MOB_DAMAGE);
        if (victim.getHealth() < victim.getMaxHealth() * 0.35f) m += sum(d, c, Kind.EXECUTE);
        if (p.getHealth() < p.getMaxHealth() * 0.5f) m += sum(d, c, Kind.BERSERK);
        return m;
    }

    /** Damage multiplier for damage this player takes. */
    public static double incoming(PlayerData d, AttributeClass c) {
        return Math.max(0.5, 1 - sum(d, c, Kind.RESIST));
    }

    public static double cooldownMult(PlayerData d, AttributeClass c) {
        return Math.max(0.5, 1 - sum(d, c, Kind.COOLDOWN));
    }

    public static double ultChargeMult(PlayerData d, AttributeClass c) {
        return 1 + sum(d, c, Kind.ULT_CHARGE);
    }

    public static double gatherMult(PlayerData d, AttributeClass c) {
        return (1 + sum(d, c, Kind.GATHER)) * ultChargeMult(d, c);
    }

    public static double ultLockMult(PlayerData d, AttributeClass c) {
        return Math.max(0.2, 1 - sum(d, c, Kind.ULT_LOCK));
    }

    /** How charged the ultimate starts right after using it. */
    public static double overflow(PlayerData d, AttributeClass c) {
        return Math.min(0.5, sum(d, c, Kind.OVERFLOW));
    }

    public static void afterHit(ServerPlayer p, PlayerData d, AttributeClass c, float dealt) {
        double steal = sum(d, c, Kind.LIFESTEAL);
        if (steal > 0 && dealt > 0) p.heal((float) (dealt * steal));
    }

    public static void onKill(ServerPlayer p, PlayerData d, AttributeClass c) {
        double cut = sum(d, c, Kind.RECHARGE) * 1000;
        if (cut <= 0) return;
        for (int i = 1; i < d.cooldownEnd.length; i++) if (d.cooldownEnd[i] > 0) d.cooldownEnd[i] -= (long) cut;
        AbpsMod.service().sync(p, true);
    }

    /** Second Wind. Runs every 5 ticks. */
    public static void tick(ServerPlayer p, PlayerData d, AttributeClass c) {
        double cd = sum(d, c, Kind.SECOND_WIND);
        if (cd <= 0 || d.buff("skill_second_wind") || p.getHealth() >= p.getMaxHealth() * 0.3f) return;
        d.setBuff("skill_second_wind", (long) (cd * 1000));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        AbpsMod.service().actionBar(p, "<#69F0AE><bold>Second Wind!</bold>");
    }

    /** Undying. Returns false (and saves the player) when it can. */
    public static boolean allowDeath(ServerPlayer p, PlayerData d, AttributeClass c) {
        double cd = sum(d, c, Kind.UNDYING);
        if (cd <= 0 || d.buff("skill_undying")) return true;
        d.setBuff("skill_undying", (long) (cd * 1000));
        p.setHealth(2);
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 2));
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.TOTEM_USE, 0.8f, 1.3f);
        AbpsMod.service().actionBar(p, "<#69F0AE><bold>Undying!</bold> <gray>You refused to fall.");
        return false;
    }
}
