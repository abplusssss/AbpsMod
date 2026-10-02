package dev.abps.dungeon;

import dev.abps.util.Mods;
import dev.abps.util.Tasks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a boss does. Every big attack is telegraphed first (a glowing ring or line on the ground shows where it
 * lands), then hits whoever is still standing there, so fights are about moving, not just trading hits.
 */
abstract class BossBrain {

    final Run run;
    final Run.Room room;
    final Mob boss;
    int ticks;
    boolean enraged;

    BossBrain(Run run, Run.Room room, EntityType<? extends Mob> type, double hp, double scale, String name) {
        this.run = run;
        this.room = room;
        Mob m = run.spawn(type, room.center().add(room.w * 0.25, 0, 0), 1, name, null);
        this.boss = m;
        if (m != null) {
            double max = hp * run.hpScale();
            Mods.setBase(m, Attributes.MAX_HEALTH, max);
            m.setHealth((float) max);
            Mods.setBase(m, Attributes.SCALE, scale);
            Mods.setBase(m, Attributes.KNOCKBACK_RESISTANCE, 1.0);
            m.addTag("abps_boss");
            room.mobs.add(m.getUUID());
        }
    }

    static BossBrain create(Run run, DungeonDef.Boss type, Run.Room room) {
        return switch (type) {
            case HOLLOW_KING -> new HollowKing(run, room);
            case GLACIAL_WARDEN -> new GlacialWarden(run, room);
            case INFERNAL_COLOSSUS -> new InfernalColossus(run, room);
        };
    }

    LivingEntity boss() {
        return boss;
    }

    boolean dead() {
        return boss == null || !boss.isAlive();
    }

    void remove() {
        if (boss != null && boss.isAlive()) boss.discard();
    }

    float dmg(double base) {
        return (float) (base * run.dmgScale());
    }

    ServerPlayer randomPlayer(List<ServerPlayer> ps) {
        return ps.get(run.rnd.nextInt(ps.size()));
    }

    void tick(List<ServerPlayer> ps) {
        if (dead() || ps.isEmpty()) return;
        ticks++;
        if (!enraged && boss.getHealth() < boss.getMaxHealth() * 0.5f) {
            enraged = true;
            boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 0));
            run.sound(SoundEvents.WITHER_SPAWN, 0.5f, 1.4f);
            run.bar("<red><bold>" + run.boss.title + " is enraged!");
            Dungeons.cue(run, Dungeons.CUE_ROAR, boss.position(), boss.position().add(0, 2, 0), boss, 0);
        }
        if (boss.getTarget() == null || !boss.getTarget().isAlive()) boss.setTarget(randomPlayer(ps));
        // Don't let the boss wander out of its room
        if (!room.inside(boss)) boss.teleportTo(room.center().x, room.center().y, room.center().z);
        think(ps);
    }

    abstract void think(List<ServerPlayer> ps);

    /** A ring that glows on the ground for warn ticks, then hurts everyone inside it. */
    void ringAttack(Vec3 center, double radius, int warn, float damage, Runnable extra) {
        Dungeons.cue(run, Dungeons.CUE_RING_WARN, center, center.add(radius, 0, 0), null, warn);
        Tasks.later(warn, () -> {
            if (dead() || run.state == Run.State.OVER) return;
            Dungeons.cue(run, Dungeons.CUE_RING_HIT, center, center.add(radius, 0, 0), null, 0);
            for (ServerPlayer p : run.online()) {
                if (p.position().distanceToSqr(center.x, p.getY(), center.z) <= radius * radius && Math.abs(p.getY() - center.y) < 3) {
                    p.hurtServer(run.level, run.level.damageSources().mobAttack(boss), damage);
                    if (extra != null) extra.run();
                }
            }
        });
    }

    /** A line on the ground from a to b that glows for warn ticks, then hurts everyone standing on it. */
    void lineAttack(Vec3 a, Vec3 b, double width, int warn, float damage, int cueHit) {
        Dungeons.cue(run, Dungeons.CUE_LINE_WARN, a, b, null, warn);
        Tasks.later(warn, () -> {
            if (dead() || run.state == Run.State.OVER) return;
            Dungeons.cue(run, cueHit, a, b, boss, 0);
            Vec3 d = b.subtract(a);
            double len = d.length();
            Vec3 dir = d.scale(1 / Math.max(0.01, len));
            for (ServerPlayer p : run.online()) {
                Vec3 rel = p.position().subtract(a);
                double along = rel.dot(dir);
                if (along < -0.5 || along > len + 0.5) continue;
                Vec3 off = rel.subtract(dir.scale(along));
                if (Math.hypot(off.x, off.z) <= width) p.hurtServer(run.level, run.level.damageSources().mobAttack(boss), damage);
            }
        });
    }

    /** A few undead or other helpers climb out around the boss. */
    void summon(EntityType<? extends Mob> type, int count) {
        for (int k = 0; k < count; k++) {
            double a = run.rnd.nextDouble() * Math.PI * 2;
            Vec3 at = boss.position().add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
            run.spawn(type, new Vec3(at.x, room.floor() + 1, at.z), 0.7, null, room);
        }
    }

    Vec3 ground(Vec3 v) {
        return new Vec3(v.x, room.floor() + 1, v.z);
    }

    // ------------------------------------------------------------------ the bosses

    /** A giant skeleton king: bone slams, soul lances, and the dead rising to help. */
    static final class HollowKing extends BossBrain {
        HollowKing(Run run, Run.Room room) {
            super(run, room, EntityTypes.WITHER_SKELETON, 220, 2.2, "<#64FFDA><bold>The Hollow King");
            if (boss != null) {
                boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
                boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                boss.setDropChance(EquipmentSlot.MAINHAND, 0);
                boss.setDropChance(EquipmentSlot.HEAD, 0);
            }
        }

        @Override
        void think(List<ServerPlayer> ps) {
            int pace = enraged ? 2 : 1;
            if (ticks % (140 / pace) == 60) {
                ServerPlayer t = randomPlayer(ps);
                ringAttack(ground(t.position()), enraged ? 5 : 4, 25, dmg(9), null);
                run.sound(SoundEvents.WITHER_SKELETON_AMBIENT, 1f, 0.5f);
            }
            if (ticks % (100 / pace) == 30) {
                ServerPlayer t = randomPlayer(ps);
                Vec3 from = ground(boss.position()), dir = ground(t.position()).subtract(from);
                Vec3 to = from.add(dir.normalize().scale(16));
                lineAttack(from, to, 1.4, 18, dmg(7), Dungeons.CUE_SOUL_LANCE);
                run.sound(SoundEvents.SOUL_ESCAPE.value(), 1f, 0.6f);
            }
            if (ticks % 280 == 200) {
                summon(run.rnd.nextBoolean() ? EntityTypes.SKELETON : EntityTypes.ZOMBIE, enraged ? 4 : 2 + run.partySize / 2);
                Dungeons.cue(run, Dungeons.CUE_ROAR, boss.position(), boss.position().add(0, 2, 0), boss, 0);
                run.sound(SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.6f);
            }
        }
    }

    /** A giant frozen archer: lines of ice spikes, a frost nova and blizzards that follow you. */
    static final class GlacialWarden extends BossBrain {
        GlacialWarden(Run run, Run.Room room) {
            super(run, room, EntityTypes.STRAY, 260, 2.4, "<#80DEEA><bold>The Glacial Warden");
        }

        @Override
        void think(List<ServerPlayer> ps) {
            int pace = enraged ? 2 : 1;
            if (ticks % (90 / pace) == 20) {
                ServerPlayer t = randomPlayer(ps);
                Vec3 from = ground(boss.position()), dir = ground(t.position()).subtract(from);
                Vec3 to = from.add(dir.normalize().scale(18));
                lineAttack(from, to, 1.3, 20, dmg(8), Dungeons.CUE_ICE_SPIKES);
                run.sound(SoundEvents.GLASS_BREAK, 1f, 0.5f);
            }
            if (ticks % (160 / pace) == 80) {
                ringAttack(ground(boss.position()), 6, 30, dmg(6), null);
                for (ServerPlayer p : ps) if (p.distanceToSqr(boss) < 49) p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                run.sound(SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.6f);
            }
            if (ticks % 240 == 150) {
                ServerPlayer t = randomPlayer(ps);
                Vec3 c = ground(t.position());
                Dungeons.cue(run, Dungeons.CUE_BLIZZARD, c, c.add(5, 0, 0), null, 100);
                Tasks.repeat(10, 10, step -> {
                    if (dead() || run.state == Run.State.OVER) return;
                    for (ServerPlayer p : run.online()) {
                        if (p.position().distanceToSqr(c.x, p.getY(), c.z) < 25) {
                            p.hurtServer(run.level, run.level.damageSources().freeze(), dmg(2));
                            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1));
                        }
                    }
                });
                if (enraged) summon(EntityTypes.STRAY, 2);
            }
        }
    }

    /** A burning ravager: meteors rain onto the room and waves of flame roll out from it. */
    static final class InfernalColossus extends BossBrain {
        InfernalColossus(Run run, Run.Room room) {
            super(run, room, EntityTypes.RAVAGER, 300, 1.4, "<#FF6D00><bold>The Infernal Colossus");
            if (boss != null) {
                boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 3600, 0, true, false));
                boss.setRemainingFireTicks(20 * 3600);
            }
        }

        @Override
        void think(List<ServerPlayer> ps) {
            int pace = enraged ? 2 : 1;
            if (boss.getRemainingFireTicks() < 100) boss.setRemainingFireTicks(20 * 3600);
            if (ticks % (120 / pace) == 40) {
                for (ServerPlayer p : ps) {
                    for (int k = 0; k < (enraged ? 2 : 1); k++) {
                        Vec3 at = ground(p.position().add(run.rnd.nextGaussian() * 1.5, 0, run.rnd.nextGaussian() * 1.5));
                        Dungeons.cue(run, Dungeons.CUE_METEOR, at, at.add(3, 0, 0), null, 30);
                        ringAttack(at, 3, 30, dmg(8), null);
                    }
                }
                run.sound(SoundEvents.GHAST_WARN, 0.8f, 0.6f);
            }
            if (ticks % (180 / pace) == 100) {
                // A wave of flame rolling outward: jump over it or get burned
                Vec3 c = ground(boss.position());
                Dungeons.cue(run, Dungeons.CUE_FLAME_WAVE, c, c.add(14, 0, 0), boss, 28);
                run.sound(SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
                Tasks.repeat(14, 2, step -> {
                    if (dead() || run.state == Run.State.OVER) return;
                    double r = 1 + step;
                    for (ServerPlayer p : run.online()) {
                        double dist = Math.sqrt(p.position().distanceToSqr(c.x, p.getY(), c.z));
                        if (Math.abs(dist - r) < 0.9 && p.getY() < c.y + 0.9) {
                            p.hurtServer(run.level, run.level.damageSources().inFire(), dmg(6));
                            p.igniteForSeconds(3);
                        }
                    }
                });
            }
            if (enraged && ticks % 20 == 0) {
                for (ServerPlayer p : ps) if (p.distanceToSqr(boss) < 16) p.hurtServer(run.level, run.level.damageSources().inFire(), dmg(2));
            }
        }
    }
}
