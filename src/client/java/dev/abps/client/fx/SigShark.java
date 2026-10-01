package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/**
 * Shark: deep blue and cyan water drawn as strokes. Teeth that snap shut, fins that cut circles through the
 * surface, whirlpools, and walls of water with a curling white crest. Blood red when it frenzies.
 */
final class SigShark {

    private SigShark() {
    }

    static final int DEEP = 0x0277BD, CYAN = 0x4DD0E1, FOAM = 0xE6FBFF, BLOOD = 0xD50000;
    static final Paint SEA = Paint.light(DEEP, 1.2f);
    static final Paint WATER = Paint.light(CYAN, 1.2f);
    static final Paint WHITE_WATER = Paint.light(FOAM, 0.9f);
    static final Paint TEETH = Paint.light(0xF4FBFF, 1.3f);
    static final Paint RED = Paint.ink(0x2A0004, 0xFF3A3A);

    static final int CUE_FRENZY_HIT = 12, CUE_FRENZY = 13, CUE_LEAP = 14, CUE_FEED = 15, CUE_BITE = 16, CUE_MARK = 17, CUE_BREACH = 18;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> riptide(c);
            case 2 -> cannon(c);
            case 3 -> maelstrom(c);
            case 4 -> breach(c);
            case 5 -> wave(c.pos.add(c.flat().scale(3)), c.flat(), 17, 6.5, 32);
            case 6 -> leviathan(c);
            case CUE_FRENZY_HIT -> Brush.rays(mid(c.target(), c.aim).get(), 4, 1, 0.06f, 7, RED);
            case CUE_FRENZY -> frenzy(c);
            case CUE_LEAP -> splash(c.pos, 2.5);
            case CUE_FEED -> Brush.tether(mid(c.target(), c.aim), mid(c.caster(), c.pos.add(0, 1, 0)), 0.1f, 12, 0.3, 0.12, RED);
            case CUE_BITE -> bite(c);
            case CUE_MARK -> mark(c);
            case CUE_BREACH -> slam(c.pos);
            default -> {
            }
        }
    }

    static Supplier<Vec3> mid(Entity e, Vec3 fallback) {
        return () -> e == null || e.isRemoved() ? fallback : e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    // ------------------------------------------------------------------ pieces

    /**
     * A ring of teeth that rises out of the ground leaning outward, then snaps shut toward the middle. Each tooth
     * is a sharp stroke drawn from its tip down to its root.
     */
    static void jaws(Vec3 c, double radius, double height, int teeth, int delay) {
        for (int k = 0; k < teeth; k++) {
            double a = Math.PI * 2 * k / teeth + rnd() * 0.1;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 root = c.add(radial.scale(radius));
            double h = height * (0.75 + rnd() * 0.5);
            Ribbon.Curve pos = (s, t) -> {
                // Open (leaning out) for the first third, then bite down past upright
                double bite = t < 0.3 ? -0.35 : Math.min(0.85, -0.35 + (t - 0.3) * 4.5);
                Vec3 dir = new Vec3(0, Math.cos(bite), 0).add(radial.scale(-Math.sin(bite)));
                double grow = Math.min(1, t * 5);
                return root.add(dir.scale(h * grow * (1 - s)));
            };
            Ribbon.Curve side = (s, t) -> Brush.faceCam(root, Brush.UP);
            int d = delay + (k % 2);
            at(d, () -> {
                TEETH.on(Ribbon.curve(pos, side)).width((float) (0.14 + height * 0.05)).time(30, 2).hold(0.7f).tailChase(0f).sparks(0).segments(10).play();
                WATER.on(Ribbon.curve(pos, side)).width((float) (0.24 + height * 0.07)).time(30, 2).hold(0.6f).tailChase(0f).sparks(0).segments(10)
                        .brightness(0.5f).play();
            });
        }
        // The bite lands: a ring of water blows out and white water is thrown up
        at(delay + 10, () -> {
            Brush.shock(c.add(0, 0.1, 0), Brush.UP, radius * 0.3, radius * 1.4, (float) (0.15 + radius * 0.03), 12, WATER);
            Brush.raysUp(c.add(0, 0.2, 0), 8, height * 0.9, 0.08f, 9, WHITE_WATER);
            Brush.glow(c.add(0, 0.5, 0), (float) radius * 0.5f, 9, CYAN);
        });
    }

    /** Fins cutting circles around a center for a while, each with a white wake behind it. */
    static void fins(Vec3 c, double radius, int count, int life, double turnsPerSecond) {
        for (int k = 0; k < count; k++) {
            double a0 = Math.PI * 2 * k / count;
            double turns = turnsPerSecond * life / 20.0;
            // The fin: a stroke from its tip back down to the water, swept back as it moves
            SEA.on(Ribbon.curve((s, t) -> {
                double a = a0 + t * turns * Math.PI * 2;
                Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a)), tangent = new Vec3(-radial.z, 0, radial.x);
                double up = 1 - s;
                return c.add(radial.scale(radius)).add(0, 0.1 + up * 1.2, 0).subtract(tangent.scale(up * up * 0.6));
            }, (s, t) -> {
                double a = a0 + t * turns * Math.PI * 2;
                return new Vec3(Math.cos(a), 0, Math.sin(a));
            })).width(0.32f).time(life, 4).hold(0.85f).tailChase(0f).sparks(0).segments(10).play();
            // The wake: an arc of white water on the surface that follows the fin
            WHITE_WATER.on((s, time, out) -> {
                double a = a0 + time * turns * Math.PI * 2 - (1 - s) * 1.1;
                Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                out[0] = c.add(radial.scale(radius)).add(0, 0.08, 0);
                out[1] = new Vec3(-radial.z, 0, radial.x);
                out[2] = radial;
            }).width(0.2f).time(life, 4).hold(0.85f).tailChase(0f).sparks(0).segments(18).play();
        }
    }

    /** A whirlpool on the surface: water strokes spiralling in toward the middle, for life ticks. */
    static void whirl(Vec3 g, double radius, int life) {
        Brush.ring(g.add(0, 0.12, 0), Brush.UP, radius, 0.16f, life, -0.8, SEA);
        Brush.pool(g, radius, life, 0x0A3A5A, 0.35f);
        during(0, life, t -> {
            if (t % 3 != 0) return;
            double a0 = rnd() * Math.PI * 2, r0 = radius * (0.6 + rnd() * 0.4);
            Ribbon.Curve pos = (s, time) -> {
                double f = Math.min(1, s * 0.8 + time * 0.4);
                double a = a0 - f * 4;
                return g.add(Math.cos(a) * r0 * (1 - f * 0.85), 0.14, Math.sin(a) * r0 * (1 - f * 0.85));
            };
            ((t / 3) % 2 == 0 ? WATER : WHITE_WATER).on(Ribbon.curve(pos, (s, time) -> {
                double f = Math.min(1, s * 0.8 + time * 0.4);
                double a = a0 - f * 4;
                return new Vec3(Math.cos(a), 0, Math.sin(a));
            })).width((float) (0.12 + radius * 0.02)).time(18, 8).hold(0.2f).tailChase(1f).sparks(0).segments(20).play();
        });
    }

    /** Water thrown up out of a point: a ring, strokes of spray and a little white water. */
    static void splash(Vec3 g, double r) {
        Brush.shock(g.add(0, 0.08, 0), Brush.UP, 0.3, r, 0.14f, 10, WATER);
        Brush.raysUp(g.add(0, 0.1, 0), 7, r * 0.8, 0.07f, 9, WHITE_WATER);
    }

    /**
     * A wall of water rolling forward: stacked strands of water from deep blue at the bottom to white at the crest,
     * which curls forward. It moves one block every two ticks for steps blocks, then crashes.
     */
    static void wave(Vec3 origin, Vec3 fwd, double width, double height, int steps) {
        Vec3 lateral = new Vec3(-fwd.z, 0, fwd.x);
        int life = steps * 2;
        int strands = 7;
        Brush.Paint line = Paint.light(CYAN, 0.6f);
        line.on(Ribbon.line(origin.add(0, 0.1, 0), origin.add(fwd.scale(steps)).add(0, 0.1, 0), lateral)).width(0.12f).time(life, 8).hold(0.7f).sparks(0).play();
        for (int k = 0; k < strands; k++) {
            double f = (k + 0.5) / strands;
            Paint p = f > 0.8 ? WHITE_WATER : f > 0.45 ? WATER : SEA;
            double phase = rnd() * 6.28;
            p.on(Ribbon.curve((s, t) -> {
                double grow = Math.min(1, t * steps / 6.0);
                double y = f * height * grow;
                double curl = Math.pow(f, 3) * height * 0.35 * grow;
                double across = (s - 0.5) * width;
                double edge = 1 - Math.pow(Math.abs(s - 0.5) * 2, 3);
                return origin.add(fwd.scale(t * steps + curl)).add(lateral.scale(across)).add(0, y * edge + Math.sin(s * 9 + t * 25 + phase) * 0.15, 0);
            }, (s, t) -> new Vec3(0, 1, 0))).width((float) (height / strands * 0.95)).time(life, 2).hold(0.9f).tailChase(0f).sparks(0).segments(40).play();
        }
        during(0, life, t -> {
            if (t % 3 != 0) return;
            Vec3 crest = origin.add(fwd.scale(t / 2.0 + height * 0.3)).add(lateral.scale((rnd() - 0.5) * width)).add(0, height * Math.min(1, t / 12.0), 0);
            sp("streak", crest).size(0.5f, 0.08f).life(10).vel(fwd.scale(0.25).add(0, 0.2, 0)).axial().grav(0.03f).colors(WHITE, CYAN).envelope(0.05f, 0.4f, 0.9f);
        });
        at(life, () -> {
            Vec3 end = origin.add(fwd.scale(steps));
            Brush.shock(end.add(0, 0.1, 0), Brush.UP, 1, height * 1.8, 0.4f, 16, WATER);
            Brush.shock(end.add(0, 0.3, 0), Brush.UP, 0.6, height * 1.2, 0.18f, 12, WHITE_WATER);
            Brush.raysUp(end.add(0, 0.5, 0), 14, height, 0.1f, 12, WHITE_WATER);
        });
    }

    // ------------------------------------------------------------------ abilities

    /** Riptide: you torpedo forward through a tunnel of rings with water spiralling around you. */
    private static void riptide(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> body = mid(e, c.chest());
        Vec3 dir = c.look;
        during(0, 10, t -> {
            Vec3 p = body.get();
            if (t % 2 == 0) Brush.ring(p.add(dir.scale(1.4)), dir, 1.2, 0.08f, 8, 3, t % 4 == 0 ? WATER : WHITE_WATER);
            SEA.on(Brush.lineCam(p.subtract(dir.scale(3)), p)).width(0.35f).time(8, 2).hold(0f).tailChase(1f).sparks(0).segments(12).play();
            Vec3[] uv = axes(dir);
            double a = t * 1.3;
            WATER.on(Ribbon.curve((s, time) -> p.subtract(dir.scale(s * 2.5)).add(uv[0].scale(Math.cos(a + s * 6) * 0.6)).add(uv[1].scale(Math.sin(a + s * 6) * 0.6)),
                    (s, time) -> Brush.faceCam(p, dir))).width(0.07f).time(7, 2).hold(0f).tailChase(1f).sparks(0).segments(12).play();
        });
    }

    /** A bite during Riptide: small jaws snap on the target and it bleeds. */
    private static void bite(Ctx c) {
        Entity t = c.target();
        Vec3 g = t == null || t.isRemoved() ? c.aim.add(0, -0.9, 0) : t.position();
        jaws(g, 0.9, 1.3, 8, 0);
        at(4, () -> Brush.rays(g.add(0, 0.9, 0), 6, 1.2, 0.07f, 8, RED));
    }

    /** Hydro Cannon: a thick jet of water with a white core, a spiral around it and pressure rings along it. */
    private static void cannon(Ctx c) {
        Vec3 from = c.hand(), to = c.aim;
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 dir = d.scale(1 / len);
        Vec3[] uv = axes(dir);
        SEA.on(Brush.lineCam(from, to)).width(0.55f).time(14, 3).hold(0.4f).tailChase(0.6f).sparks(0).play();
        WHITE_WATER.on(Brush.lineCam(from, to)).width(0.22f).time(12, 3).hold(0.4f).tailChase(0.6f).sparks(0).play();
        WATER.on(Ribbon.curve((s, t) -> from.add(d.scale(s)).add(uv[0].scale(Math.cos(s * len * 1.6 + t * 12) * 0.45)).add(uv[1].scale(Math.sin(s * len * 1.6 + t * 12) * 0.45)),
                (s, t) -> Brush.faceCam(from.add(d.scale(s)), dir))).width(0.08f).time(12, 3).hold(0.3f).tailChase(0.6f).sparks(0).play();
        for (double along = 2; along < len; along += 3) {
            Vec3 p = from.add(dir.scale(along));
            at((int) (along / 10), () -> Brush.shock(p, dir, 0.3, 1.5, 0.07f, 8, WATER));
        }
        Brush.shock(from, dir, 0.2, 1.3, 0.1f, 8, WHITE_WATER);
        at(3, () -> {
            Brush.glow(to, 1.4f, 8, CYAN);
            Brush.rays(to, 10, 2.2, 0.09f, 9, WHITE_WATER);
            Brush.shock(to, dir, 0.3, 2.4, 0.16f, 10, WATER);
        });
    }

    /** The scent of blood on a cannon hit: a red ring closes round the target. */
    private static void mark(Ctx c) {
        Supplier<Vec3> m = mid(c.target(), c.aim);
        Brush.ring(m.get(), Brush.UP, 1.1, 0.08f, 24, 2.5, RED);
        Brush.rays(m.get(), 5, 1.2, 0.06f, 7, WHITE_WATER);
    }

    /** Maelstrom: a whirlpool with three fins circling it, which finally snaps shut as giant jaws. */
    private static void maelstrom(Ctx c) {
        Vec3 g = c.aim;
        int life = Math.max(40, c.ticks);
        whirl(g, 8, life);
        fins(g.add(0, 0.05, 0), 6, 3, life, 0.55);
        Brush.circle(g, 8, life, 6, SEA, WATER);
        at(life - 10, () -> jaws(g, 3.5, 2.4, 10, 0));
    }

    /** Apex Breach: you burst out of the water in a column of spray. The landing is a cue. */
    private static void breach(Ctx c) {
        splash(c.pos, 3);
        Brush.pillar(c.pos, 0.8, 4, 14, 3, WATER);
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        during(0, 24, t -> {
            if (t % 3 == 0) Brush.ring(feet.get().add(0, 0.6, 0), Brush.UP, 1.3, 0.08f, 8, 2, t % 6 == 0 ? WATER : WHITE_WATER);
        });
    }

    /** The breach lands: huge jaws erupt around you and the water blows out in rings. */
    private static void slam(Vec3 at) {
        jaws(at, 6.5, 3.6, 16, 0);
        jaws(at, 3.6, 2.4, 10, 2);
        for (int k = 0; k < 3; k++) {
            int kk = k;
            at(10 + k * 3, () -> Brush.shock(at.add(0, 0.1 + kk * 0.1, 0), Brush.UP, 1 + kk, 11 + kk * 2, 0.3f - kk * 0.06f, 14, kk % 2 == 0 ? WATER : WHITE_WATER));
        }
        at(10, () -> Brush.pillar(at, 1.6, 9, 18, 4, WATER));
    }

    /** Frenzy: a red stream winds around you for as long as it lasts. */
    private static void frenzy(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        int life = Math.max(40, c.ticks);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.4, 4.5, 0.2f, 12, RED);
        during(0, life, t -> {
            if (t % 20 == 0) Brush.helix(feet, 0.7, 2.0, 1.4, 0.08f, 18, t * 0.7, RED);
        });
    }

    /** Leviathan's Wrath: fins circle and the sea drags inward, giant jaws erupt, then three tidal waves roll out. */
    private static void leviathan(Ctx c) {
        Vec3 g = c.pos;
        int gather = Math.max(20, c.ticks);
        whirl(g, 15, gather + 12);
        fins(g.add(0, 0.05, 0), 10, 5, gather + 12, 0.35);
        Brush.circle(g, 14, gather + 30, 8, SEA, WATER);
        for (int k = 0; k < gather / 8; k++) at(k * 8, () -> Brush.shock(g.add(0, 0.1, 0), Brush.UP, 22, 2, 0.2f, 12, WATER.bright(0.6f)));
        at(gather, () -> {
            jaws(g, 14, 10, 28, 0);
            jaws(g, 7.5, 6.5, 16, 2);
            Brush.pillar(g, 2.5, 14, 22, 5, WATER);
        });
        double yaw = Math.atan2(-c.look.x, c.look.z);
        at(gather + 14, () -> {
            for (int i = 0; i < 3; i++) {
                double a = Math.PI * 2 * i / 3 + yaw;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                wave(g.add(dir.scale(3)), dir, 13, 5, 16);
            }
        });
    }
}
