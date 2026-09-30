package dev.abps.util;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom effects built out of glowing block displays instead of vanilla particles.
 * Everything is a short-lived display entity that animates itself with transformation interpolation,
 * so it looks smooth for every player and needs no client mod. Each effect cleans itself up.
 */
public final class Vfx {

    /** Full bright concrete colors, used to pick a block that matches a class color. */
    private static final int[] PALETTE = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    private static final BlockState[] BLOCKS = {
            Blocks.CONCRETE.white().defaultBlockState(), Blocks.CONCRETE.orange().defaultBlockState(), Blocks.CONCRETE.magenta().defaultBlockState(),
            Blocks.CONCRETE.lightBlue().defaultBlockState(), Blocks.CONCRETE.yellow().defaultBlockState(), Blocks.CONCRETE.lime().defaultBlockState(),
            Blocks.CONCRETE.pink().defaultBlockState(), Blocks.CONCRETE.gray().defaultBlockState(), Blocks.CONCRETE.lightGray().defaultBlockState(),
            Blocks.CONCRETE.cyan().defaultBlockState(), Blocks.CONCRETE.purple().defaultBlockState(), Blocks.CONCRETE.blue().defaultBlockState(),
            Blocks.CONCRETE.brown().defaultBlockState(), Blocks.CONCRETE.green().defaultBlockState(), Blocks.CONCRETE.red().defaultBlockState(),
            Blocks.CONCRETE.black().defaultBlockState()};

    public static final BlockState WHITE = BLOCKS[0];

    private Vfx() {
    }

    /** The concrete block closest to a color. */
    public static BlockState tint(int rgb) {
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int dr = ((rgb >> 16) & 0xFF) - ((PALETTE[i] >> 16) & 0xFF);
            int dg = ((rgb >> 8) & 0xFF) - ((PALETTE[i] >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (PALETTE[i] & 0xFF);
            long dist = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return BLOCKS[best];
    }

    // ---------------- Building blocks ----------------

    /** A transformation that keeps the middle of the box on the entity, then shifts it by offset. */
    private static Transformation centered(Vector3f offset, Quaternionf rot, Vector3f scale) {
        Vector3f t = new Vector3f(scale).mul(0.5f).rotate(rot).negate().add(offset);
        return new Transformation(t, new Quaternionf(rot), new Vector3f(scale), new Quaternionf());
    }

    /** A transformation that keeps the middle of the bottom face on the entity, so things can grow up out of the ground. */
    private static Transformation based(Vector3f offset, Quaternionf rot, Vector3f scale) {
        Vector3f t = new Vector3f(scale.x * 0.5f, 0, scale.z * 0.5f).rotate(rot).negate().add(offset);
        return new Transformation(t, new Quaternionf(rot), new Vector3f(scale), new Quaternionf());
    }

    private static Display.BlockDisplay make(ServerLevel level, Vec3 at, BlockState state, Transformation start, int glowRgb) {
        Display.BlockDisplay d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        d.setPos(at.x, at.y, at.z);
        d.setBlockState(state);
        d.addTag("abps_fx");
        d.setBrightnessOverride(Brightness.FULL_BRIGHT);
        d.setSilent(true);
        if (glowRgb >= 0) {
            d.setGlowingTag(true);
            d.setGlowColorOverride(glowRgb & 0xFFFFFF);
        }
        d.setTransformation(start);
        level.addFreshEntity(d);
        return d;
    }

    /** Moves a display to a new transformation smoothly over duration ticks, starting after delay ticks. */
    private static void animate(Display.BlockDisplay d, int delay, int duration, Transformation to) {
        Tasks.later(delay + 1, () -> {
            if (d.isRemoved()) return;
            d.setTransformationInterpolationDelay(0);
            d.setTransformationInterpolationDuration(duration);
            d.setTransformation(to);
        });
    }

    private static void remove(Display.BlockDisplay d, int afterTicks) {
        Tasks.later(afterTicks + 2, d::discard);
    }

    private static Quaternionf randomRotation() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Quaternionf().rotateXYZ(r.nextFloat() * 6.28f, r.nextFloat() * 6.28f, r.nextFloat() * 6.28f);
    }

    private static Vector3f v(Vec3 a) {
        return new Vector3f((float) a.x, (float) a.y, (float) a.z);
    }

    // ---------------- Effects ----------------

    /** One tumbling chunk that flies out, falls a little and shrinks away. Velocity is blocks per tick. */
    public static void shard(ServerLevel level, Vec3 at, BlockState state, float size, Vec3 vel, double gravity, int life, int glowRgb) {
        Quaternionf q0 = randomRotation();
        Quaternionf q1 = new Quaternionf(q0).rotateAxis(3.0f + ThreadLocalRandom.current().nextFloat() * 3f, 0.3f, 1f, 0.2f);
        Display.BlockDisplay d = make(level, at, state, centered(new Vector3f(), q0, new Vector3f(size)), glowRgb);
        Vector3f off = new Vector3f((float) (vel.x * life), (float) (vel.y * life - gravity * life * life / 2.0), (float) (vel.z * life));
        animate(d, 0, life, centered(off, q1, new Vector3f(size * 0.08f)));
        remove(d, life);
    }

    /** A burst of shards flying out in every direction, leaning upward. */
    public static void burst(ServerLevel level, Vec3 at, BlockState state, int count, double speed, float size, int life, int glowRgb) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) * 0.9 + 0.2, r.nextGaussian()).normalize();
            double sp = speed * (0.5 + r.nextDouble() * 0.7);
            shard(level, at, state, size * (0.6f + r.nextFloat() * 0.8f), dir.scale(sp), 0.004, life, glowRgb);
        }
    }

    /**
     * A ring that grows from r0 to r1 while it thins out. The normal says which way the ring faces,
     * (0,1,0) is flat on the ground.
     */
    public static void ring(ServerLevel level, Vec3 center, Vec3 normal, double r0, double r1, int segments, BlockState state,
                            float thick, int life, int glowRgb) {
        Vector3f n = v(normal.normalize());
        Vector3f helper = Math.abs(n.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(n).cross(helper).normalize();
        Vector3f w = new Vector3f(n).cross(u).normalize();
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2 * i / segments;
            Vector3f radial = new Vector3f(u).mul((float) Math.cos(a)).add(new Vector3f(w).mul((float) Math.sin(a)));
            Vector3f tangent = new Vector3f(u).mul((float) -Math.sin(a)).add(new Vector3f(w).mul((float) Math.cos(a)));
            Vec3 p0 = center.add(radial.x * r0, radial.y * r0, radial.z * r0);
            Vector3f off = new Vector3f(radial).mul((float) (r1 - r0));
            float len0 = (float) Math.max(0.2, Math.PI * 2 * r0 / segments * 1.3);
            float len1 = (float) Math.max(0.2, Math.PI * 2 * r1 / segments * 1.3);
            Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), tangent);
            Display.BlockDisplay d = make(level, p0, state, centered(new Vector3f(), rot, new Vector3f(len0, thick, thick)), glowRgb);
            animate(d, 0, life, centered(off, rot, new Vector3f(len1, thick * 0.2f, thick * 0.2f)));
            remove(d, life);
        }
    }

    public static void groundRing(ServerLevel level, Vec3 center, double r0, double r1, int segments, BlockState state, float thick, int life, int glowRgb) {
        ring(level, center.add(0, 0.08, 0), new Vec3(0, 1, 0), r0, r1, segments, state, thick, life, glowRgb);
    }

    /** A glowing line between two points that flashes thick and thins to nothing. */
    public static void beam(ServerLevel level, Vec3 a, Vec3 b, float thick, BlockState state, int life, int glowRgb) {
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 0.05) return;
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), v(dir.normalize()));
        Vec3 mid = a.add(dir.scale(0.5));
        Display.BlockDisplay d = make(level, mid, state, centered(new Vector3f(), rot, new Vector3f((float) len, thick, thick)), glowRgb);
        animate(d, 0, life, centered(new Vector3f(), rot, new Vector3f((float) len, 0.01f, 0.01f)));
        remove(d, life);
    }

    /** A beam that follows a jagged path, like lightning or a crack in the world. */
    public static void zigzag(ServerLevel level, Vec3 a, Vec3 b, int pieces, double jitter, float thick, BlockState state, int life, int glowRgb) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 prev = a;
        for (int i = 1; i <= pieces; i++) {
            Vec3 next = a.lerp(b, (double) i / pieces);
            if (i < pieces) next = next.add((r.nextDouble() - 0.5) * jitter, (r.nextDouble() - 0.5) * jitter, (r.nextDouble() - 0.5) * jitter);
            beam(level, prev, next, thick, state, life, glowRgb);
            prev = next;
        }
    }

    /** A column that shoots up out of the ground, holds, then narrows away. */
    public static void pillar(ServerLevel level, Vec3 base, double radius, double height, BlockState state, int grow, int hold, int shrink, int glowRgb) {
        float d2 = (float) (radius * 2);
        Quaternionf id = new Quaternionf();
        Display.BlockDisplay d = make(level, base, state, based(new Vector3f(), id, new Vector3f(d2, 0.05f, d2)), glowRgb);
        animate(d, 0, grow, based(new Vector3f(), id, new Vector3f(d2, (float) height, d2)));
        animate(d, grow + hold, shrink, based(new Vector3f(), id, new Vector3f(0.02f, (float) height, 0.02f)));
        remove(d, grow + hold + shrink);
    }

    /** Ring of tall teeth that burst out of the ground and snap shut toward the middle. */
    public static void jaws(ServerLevel level, Vec3 center, double radius, int teeth, double height, BlockState state, int glowRgb) {
        for (int i = 0; i < teeth; i++) {
            double a = Math.PI * 2 * i / teeth;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = center.add(radial.scale(radius));
            // Tilt axis is the tangent, so the top of the tooth leans toward or away from the middle
            Vector3f axis = new Vector3f((float) -radial.z, 0, (float) radial.x);
            Quaternionf open = new Quaternionf().rotateAxis(-0.35f, axis);
            Quaternionf shut = new Quaternionf().rotateAxis(0.75f, axis);
            float w = 0.55f;
            float h = (float) (height * (0.75 + ThreadLocalRandom.current().nextDouble() * 0.5));
            Display.BlockDisplay d = make(level, at, state, based(new Vector3f(), open, new Vector3f(w, 0.05f, w)), glowRgb);
            animate(d, 0, 4, based(new Vector3f(), open, new Vector3f(w, h, w)));
            animate(d, 8, 4, based(new Vector3f(), shut, new Vector3f(w * 0.8f, h, w * 0.8f)));
            animate(d, 24, 10, based(new Vector3f(), shut, new Vector3f(0.02f, h, 0.02f)));
            remove(d, 34);
        }
    }

    /** Crescent slash that sweeps out in front of you. dir is the way you are facing. */
    public static void slash(ServerLevel level, Vec3 center, Vec3 dir, double radius, double arcRadians, float thick, BlockState state, int glowRgb) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        double baseAngle = Math.atan2(flat.z, flat.x);
        int pieces = 9;
        for (int i = 0; i < pieces; i++) {
            double t = (double) i / (pieces - 1);
            double a = baseAngle - arcRadians / 2 + arcRadians * t;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a));
            Vec3 at = center.add(radial.scale(radius));
            float len = (float) (radius * arcRadians / pieces * 1.5);
            Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), v(tangent));
            int delay = i; // sweeps around like a blade
            Tasks.later(delay, () -> {
                Display.BlockDisplay d = make(level, at, state, centered(new Vector3f(), rot, new Vector3f(len, thick * 0.4f, thick * 0.4f)), glowRgb);
                animate(d, 0, 2, centered(new Vector3f(), rot, new Vector3f(len, thick, thick * 0.5f)));
                animate(d, 3, 6, centered(v(radial.scale(0.6)), rot, new Vector3f(len, 0.01f, 0.01f)));
                remove(d, 9);
            });
        }
    }

    /** Shards that circle an entity for a while. */
    public static void orbit(ServerLevel level, Entity anchor, int count, double radius, double height, BlockState state, float size,
                             int ticks, double turnsPerSecond, int glowRgb) {
        List<Display.BlockDisplay> parts = new ArrayList<>();
        Vec3 c = anchor.position();
        for (int i = 0; i < count; i++) {
            Quaternionf rot = randomRotation();
            Display.BlockDisplay d = make(level, c, state, centered(new Vector3f(), rot, new Vector3f(size)), glowRgb);
            d.setPosRotInterpolationDuration(2);
            parts.add(d);
        }
        int steps = Math.max(1, ticks / 2);
        Tasks.repeat(steps, 2, step -> {
            if (anchor.isRemoved()) return;
            Vec3 p = anchor.position();
            double spin = step * 2 / 20.0 * turnsPerSecond * Math.PI * 2;
            for (int i = 0; i < parts.size(); i++) {
                double a = spin + Math.PI * 2 * i / parts.size();
                double bob = Math.sin(step * 0.35 + i) * 0.25;
                parts.get(i).setPos(p.x + Math.cos(a) * radius, p.y + height + bob, p.z + Math.sin(a) * radius);
            }
        });
        Tasks.later(ticks + 2, () -> parts.forEach(Display::discard));
    }

    /** Shards circling a fixed point, for things like whirlpools. Height rises the further out you go. */
    public static void vortex(ServerLevel level, Vec3 center, double radius, int count, BlockState state, float size, int ticks,
                              double turnsPerSecond, int glowRgb) {
        List<Display.BlockDisplay> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Display.BlockDisplay d = make(level, center, state, centered(new Vector3f(), randomRotation(), new Vector3f(size)), glowRgb);
            d.setPosRotInterpolationDuration(2);
            parts.add(d);
        }
        int steps = Math.max(1, ticks / 2);
        Tasks.repeat(steps, 2, step -> {
            double spin = step * 2 / 20.0 * turnsPerSecond * Math.PI * 2;
            for (int i = 0; i < parts.size(); i++) {
                double frac = (double) i / parts.size();
                double a = spin * (1.4 - frac * 0.6) + Math.PI * 2 * i / 3.0;
                double r = radius * (0.25 + 0.75 * frac);
                parts.get(i).setPos(center.x + Math.cos(a) * r, center.y + 0.1 + frac * 2.4, center.z + Math.sin(a) * r);
            }
        });
        Tasks.later(ticks + 2, () -> parts.forEach(Display::discard));
    }

    /** Flat triangular fins that circle a point at the water surface, like sharks circling prey. */
    public static void fins(ServerLevel level, Vec3 center, double radius, int count, BlockState state, int ticks, double turnsPerSecond, int glowRgb) {
        List<Display.BlockDisplay> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Display.BlockDisplay d = make(level, center, state, centered(new Vector3f(), new Quaternionf(), new Vector3f(0.08f, 0.02f, 0.02f)), glowRgb);
            d.setPosRotInterpolationDuration(2);
            parts.add(d);
        }
        Tasks.later(1, () -> parts.forEach(d -> {
            d.setTransformationInterpolationDelay(0);
            d.setTransformationInterpolationDuration(6);
            d.setTransformation(fin(0.6f, 0.9f, new Quaternionf()));
        }));
        int steps = Math.max(1, ticks / 2);
        Tasks.repeat(steps, 2, step -> {
            double spin = step * 2 / 20.0 * turnsPerSecond * Math.PI * 2;
            for (int i = 0; i < parts.size(); i++) {
                double a = spin + Math.PI * 2 * i / parts.size();
                Display.BlockDisplay d = parts.get(i);
                d.setPos(center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius);
                // Face along the direction of travel
                Quaternionf yaw = new Quaternionf().rotateY((float) (-a - Math.PI / 2));
                d.setTransformation(fin(0.6f, 0.9f, yaw));
                d.setTransformationInterpolationDelay(0);
                d.setTransformationInterpolationDuration(2);
            }
        });
        Tasks.later(ticks + 2, () -> parts.forEach(Display::discard));
    }

    /** A slanted slab that reads as a dorsal fin. */
    private static Transformation fin(float length, float height, Quaternionf yaw) {
        Quaternionf lean = new Quaternionf(yaw).rotateZ(-0.55f);
        return based(new Vector3f(), lean, new Vector3f(length * 0.35f, height, 0.06f));
    }

    /**
     * A wall of water that can be moved every couple of ticks. Made of one column and one leaning foam crest per
     * block of width, with the height rippling as it travels.
     */
    public static final class Wave {
        private final Vec3 dir, lateral;
        private final int columns;
        private final double height;
        private final List<Display.BlockDisplay> body = new ArrayList<>();
        private final List<Display.BlockDisplay> crest = new ArrayList<>();
        private final Quaternionf yaw;

        public Wave(ServerLevel level, Vec3 origin, Vec3 dir, int columns, double height, BlockState bodyState, BlockState crestState, int glowRgb) {
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            this.dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
            this.lateral = new Vec3(-this.dir.z, 0, this.dir.x);
            this.columns = columns;
            this.height = height;
            this.yaw = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), v(this.dir));
            for (int i = 0; i < columns; i++) {
                Display.BlockDisplay b = make(level, origin, bodyState, based(new Vector3f(), yaw, new Vector3f(1.2f, 0.1f, 2.4f)), glowRgb);
                b.setPosRotInterpolationDuration(2);
                body.add(b);
                Display.BlockDisplay c = make(level, origin, crestState, based(new Vector3f(), yaw, new Vector3f(1.2f, 0.1f, 1.4f)), -1);
                c.setPosRotInterpolationDuration(2);
                crest.add(c);
            }
        }

        /** Moves the wall so its middle is at center. growth runs 0 to 1 and scales the height, phase makes it ripple. */
        public void update(Vec3 center, double growth, int phase) {
            for (int i = 0; i < columns; i++) {
                double lo = i - (columns - 1) / 2.0;
                Vec3 at = center.add(lateral.scale(lo));
                // Taller in the middle, lower at the edges, and rippling
                double edge = 1.0 - Math.pow(Math.abs(lo) / (columns / 2.0 + 0.5), 2) * 0.45;
                double h = Math.max(0.1, height * growth * edge * (0.82 + 0.18 * Math.sin(phase * 0.6 + i * 0.8)));
                Display.BlockDisplay b = body.get(i);
                b.setPos(at.x, at.y, at.z);
                b.setTransformation(based(new Vector3f(), yaw, new Vector3f(1.2f, (float) h, 2.4f)));
                b.setTransformationInterpolationDelay(0);
                b.setTransformationInterpolationDuration(2);

                Display.BlockDisplay c = crest.get(i);
                Vec3 top = at.add(dir.scale(0.9));
                c.setPos(top.x, top.y + h - 0.35, top.z);
                Quaternionf lean = new Quaternionf(yaw).rotateX(0.75f);
                c.setTransformation(based(new Vector3f(), lean, new Vector3f(1.2f, (float) Math.max(0.1, 0.9 * growth), 1.4f)));
                c.setTransformationInterpolationDelay(0);
                c.setTransformationInterpolationDuration(2);
            }
        }

        /** Lets the wave collapse and removes it. */
        public void collapse() {
            for (Display.BlockDisplay d : body) {
                d.setTransformation(based(new Vector3f(), yaw, new Vector3f(1.2f, 0.05f, 2.4f)));
                d.setTransformationInterpolationDelay(0);
                d.setTransformationInterpolationDuration(8);
                remove(d, 8);
            }
            for (Display.BlockDisplay d : crest) {
                d.setTransformation(based(new Vector3f(), yaw, new Vector3f(1.2f, 0.02f, 1.4f)));
                d.setTransformationInterpolationDelay(0);
                d.setTransformationInterpolationDuration(6);
                remove(d, 6);
            }
        }
    }
}
