package dev.abps.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bigger effects put together from the pieces in {@link Vfx}. Abilities call these with two colors so every class
 * looks different without each ability having to build its own effect from scratch.
 */
public final class Fancy {

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private Fancy() {
    }

    /** A glowing magic circle on the ground: two rings, spokes between them and small beacons on the rim. */
    public static void sigil(ServerLevel level, Vec3 center, double radius, int spokes, int c1, int c2, int life) {
        BlockState b1 = Vfx.tint(c1), b2 = Vfx.tint(c2);
        Vec3 flat = center.add(0, 0.08, 0);
        Vfx.ring(level, flat, UP, radius, radius, 28, b1, 0.12f, life, c1);
        Vfx.ring(level, flat, UP, radius * 0.55, radius * 0.55, 20, b2, 0.09f, life, c2);
        for (int i = 0; i < spokes; i++) {
            double a = Math.PI * 2 * i / spokes;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.beam(level, flat.add(dir.scale(radius * 0.2)), flat.add(dir.scale(radius)), 0.08f, b2, life, c2);
            if (i % 2 == 0) Vfx.pillar(level, flat.add(dir.scale(radius)), 0.07, 1.4, b1, 4, Math.max(2, life / 2), 6, c1);
        }
    }

    /** A hit: bright flash, two crossed rings, and a spray of shards. */
    public static void impact(ServerLevel level, Vec3 at, float scale, int c1, int c2) {
        Vfx.flash(level, at, 1.6f * scale, Vfx.WHITE, 6, c1);
        Vfx.burst(level, at, Vfx.tint(c2), Math.max(6, (int) (10 * scale)), 0.22, 0.16f, 14, c2);
        Vfx.ring(level, at, UP, 0.3, 1.8 * scale, 12, Vfx.tint(c1), 0.08f, 8, c1);
        Vfx.ring(level, at, new Vec3(1, 0, 0), 0.3, 1.5 * scale, 12, Vfx.tint(c2), 0.07f, 8, c2);
    }

    /** A bolt of lightning: a thick colored core, a thin white heart, two side branches and a flash where it lands. */
    public static void lightning(ServerLevel level, Vec3 from, Vec3 to, int c1, int c2) {
        BlockState b1 = Vfx.tint(c1);
        Vfx.zigzag(level, from, to, 7, 1.6, 0.18f, b1, 8, c1);
        Vfx.zigzag(level, from, to, 7, 1.0, 0.07f, Vfx.WHITE, 6, c2);
        for (int i = 0; i < 2; i++) {
            Vec3 start = from.lerp(to, 0.35 + 0.3 * i);
            Vec3 end = start.add((Math.random() - 0.5) * 6, -2.5 - Math.random() * 2, (Math.random() - 0.5) * 6);
            Vfx.zigzag(level, start, end, 3, 0.8, 0.09f, b1, 6, c1);
        }
        impact(level, to, 1.2f, c1, c2);
    }

    /** A straight beam with a colored shell, a white core and a flash at each end. */
    public static void laser(ServerLevel level, Vec3 from, Vec3 to, float thick, int c1, int c2, int life) {
        Vfx.beam(level, from, to, thick, Vfx.tint(c1), life, c1);
        Vfx.beam(level, from, to, thick * 0.4f, Vfx.WHITE, Math.max(2, life - 2), c2);
        Vfx.flash(level, from, 0.9f + thick * 2, Vfx.tint(c2), 5, c2);
        Vfx.flash(level, to, 1.1f + thick * 3, Vfx.tint(c2), 6, c1);
    }

    /** Rings and shards wrapped around something that is held in place. */
    public static void chains(ServerLevel level, Entity target, int ticks, int c1, int c2) {
        Vec3 at = target.position();
        for (int i = 0; i < 3; i++) {
            Vfx.ring(level, at.add(0, 0.4 + i * (target.getBbHeight() / 3), 0), UP, 0.9, 0.7, 14, Vfx.tint(i % 2 == 0 ? c1 : c2), 0.09f, 14, i % 2 == 0 ? c1 : c2);
        }
        Vfx.orbit(level, target, 6, 0.85, target.getBbHeight() * 0.5, Vfx.tint(c1), 0.13f, Math.min(ticks, 400), 1.1, c1);
        Vfx.orbit(level, target, 4, 0.6, target.getBbHeight() * 0.8, Vfx.tint(c2), 0.11f, Math.min(ticks, 400), -1.4, c2);
    }

    /** Shards circling something for a while. Good for buffs. */
    public static void aura(ServerLevel level, Entity e, int ticks, int c1, int c2) {
        int t = Math.min(ticks, 400);
        Vfx.orbit(level, e, 4, 0.9, 0.7, Vfx.tint(c1), 0.12f, t, 1.2, c1);
        Vfx.orbit(level, e, 3, 1.2, 1.5, Vfx.tint(c2), 0.1f, t, -1.0, c2);
    }

    /** A tall spinning column that pulses rings at its base. */
    public static void tornado(ServerLevel level, Vec3 base, double radius, double height, int ticks, BlockState state, int glow) {
        Vfx.vortex(level, base, radius * 1.8, 26, state, 0.22f, ticks, 1.1, glow);
        Vfx.vortex(level, base, radius, 14, Vfx.WHITE, 0.14f, ticks, -1.5, glow);
        for (int t = 0; t < ticks; t += 12) {
            Vec3 at = base;
            Tasks.later(t, () -> Vfx.groundRing(level, at, 0.5, radius * 1.6, 20, state, 0.1f, 10, glow));
        }
        Vfx.pillar(level, base, 0.25, height, state, 8, Math.max(2, ticks - 16), 8, glow);
    }
}
