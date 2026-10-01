package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Cryomancer: pale blue light, deep blue edges, crystal spikes and snow. */
final class SigCryo {

    private SigCryo() {
    }

    static final int ICE = 0xA8ECFF, DEEP = 0x2F6BFF, PALE = 0xE6FAFF;
    static final Paint FROST = Paint.light(ICE, 1.25f);
    static final Paint BLUE = Paint.light(DEEP, 1.1f);
    static final Paint SNOW = Paint.light(PALE, 0.8f);

    static final int CUE_CHILL = 12, CUE_SHATTER = 14, CUE_FREEZE = 15;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> lance(c);
            case 2 -> spikes(c);
            case 3 -> nova(c);
            case 4 -> blizzard(c);
            case 6 -> zero(c);
            case CUE_CHILL -> chill(c);
            case CUE_FREEZE -> prison(c);
            case CUE_SHATTER -> shatter(c);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    static Supplier<Vec3> mid(Entity e, Vec3 fallback) {
        return () -> e == null || e.isRemoved() ? fallback : e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Sparkling flecks of ice that hang and drift. */
    static void glints(Vec3 at, double spread, int count) {
        for (int k = 0; k < n(count); k++) {
            sp("spark", at.add(gauss() * spread, gauss() * spread, gauss() * spread)).size(0.09f, 0.02f).life(14 + (int) (rnd() * 14))
                    .vel(gauss() * 0.01, rnd() * 0.01, gauss() * 0.01).colors(WHITE, ICE).flicker(0.6f).envelope(0.1f, 0.4f, 1f);
        }
    }

    /** A cluster of spikes bursting up out of the ground at a point, leaning outward. */
    static void cluster(Vec3 ground, double height, int count, int life, Vec3 lean) {
        for (int k = 0; k < count; k++) {
            Vec3 tilt = rndUp().add(lean.scale(0.6)).normalize();
            double h = height * (0.6 + rnd() * 0.5);
            Vec3 base = ground.add(gauss() * 0.25, 0, gauss() * 0.25);
            Brush.spike(base, base.add(tilt.scale(h)), (float) (0.16 + h * 0.08), life, k % 3 == 0 ? BLUE : FROST);
        }
    }

    /** Frost breaking off a hit point: rays of ice, a sharp ring and glints. */
    static void impact(Vec3 at, float scale) {
        Brush.glow(at, 1.2f * scale, 8, ICE);
        Brush.rays(at, 9, 1.8 * scale, 0.09f * scale, 8, FROST);
        Brush.shock(at, Brush.camera().subtract(at).normalize(), 0.2, 1.7 * scale, 0.12f * scale, 9, SNOW);
        glints(at, 0.6 * scale, 10);
    }

    // ------------------------------------------------------------------ abilities

    /** Frost Lance: a spear of ice spins out of the hand and shatters on the target. */
    private static void lance(Ctx c) {
        Vec3 from = c.hand(), to = c.focus();
        int ticks = (int) Math.max(2, Math.min(8, from.distanceTo(to) / 4));
        Brush.glow(from, 0.6f, 6, ICE);
        Brush.converge(from, 1.2, 5, 5, 0.05f, SNOW);
        Brush.comet(from, to, ticks, 0.24f, FROST, () -> impact(mid(c.target(), to).get(), 1f));
        Brush.comet(from, to, ticks, 0.09f, SNOW, null);
        // A thin spiral of frost wound around the flight path
        Vec3 d = to.subtract(from);
        Vec3[] uv = axes(d.normalize());
        BLUE.on(Ribbon.curve((s, t) -> from.add(d.scale(s)).add(uv[0].scale(Math.cos(s * 14) * 0.18)).add(uv[1].scale(Math.sin(s * 14) * 0.18)),
                (s, t) -> Brush.faceCam(from.add(d.scale(s)), d.normalize()))).width(0.05f).time(ticks + 8, ticks).hold(0.1f).tailChase(1f).sparks(0).play();
    }

    /** Glacial Spikes: a crack races along the ground and spikes burst out of it. */
    private static void spikes(Ctx c) {
        Vec3 dir = c.flat(), side = c.right();
        Vec3 start = c.pos.add(0, 0.05, 0);
        BLUE.on(Ribbon.line(start, start.add(dir.scale(10.5)), side)).width(0.18f).time(26, 10).hold(0.6f).tailChase(0.3f).sparks(0).play();
        for (int step = 0; step < 10; step++) {
            int s = step;
            at(step, () -> {
                Vec3 g = start.add(dir.scale(s + 1));
                cluster(g, 1.5 + s * 0.05, 3, 24, side.scale(rnd() < 0.5 ? 1 : -1));
                Brush.glow(g.add(0, 0.3, 0), 0.6f, 6, ICE);
                glints(g.add(0, 0.6, 0), 0.4, 3);
            });
        }
    }

    /** Frost Nova: a ring of frost blasts out and a crown of spikes rises around you. */
    private static void nova(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        Brush.glow(c.chest(), 2f, 10, ICE);
        Brush.shock(g, Brush.UP, 0.5, 6.5, 0.34f, 14, FROST);
        Brush.shock(g.add(0, 0.2, 0), Brush.UP, 0.3, 5.5, 0.14f, 11, SNOW);
        Brush.dome(c.chest(), 0.4, 2.6, 0.12f, 12, SNOW);
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI * 2 / 16 + rnd() * 0.2;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            double r = 2.5 + (k % 2) * 1.6;
            at(2 + (int) r, () -> cluster(g.add(out.scale(r)), 1.3, 2, 30, out));
        }
        glints(c.chest(), 2.5, 24);
    }

    /** Blizzard: a storm of wind bands and snow spins over the target area for 6 seconds. */
    private static void blizzard(Ctx c) {
        Vec3 g = c.aim;
        int life = Math.max(60, c.ticks);
        Brush.circle(g, 6, life, 6, FROST, BLUE);
        Brush.pool(g, 6, life, 0x1E5AA8, 0.25f);
        during(0, life, t -> {
            if (t % 5 == 0) {
                // Bands of wind circling the storm at different heights
                for (int k = 0; k < 2; k++) {
                    double y = 0.4 + rnd() * 3.6, r = 2.5 + rnd() * 3.5;
                    Vec3 mid = rotY(new Vec3(1, 0, 0), rnd() * Math.PI * 2);
                    Paint p = rnd() < 0.3 ? FROST : SNOW;
                    p.on(Ribbon.arc(g.add(0, y, 0), mid, Brush.UP, r, 1.6, 2.2, 0.9)).width(0.14f).time(16, 4).hold(0.2f).tailChase(0.8f).sparks(0)
                            .segments(16).play();
                }
            }
            // Snow driving down and around
            for (int k = 0; k < n(5); k++) {
                double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 6;
                Vec3 p = g.add(Math.cos(a) * r, 4 + rnd() * 2, Math.sin(a) * r);
                Vec3 v = new Vec3(-Math.sin(a) * 0.12, -0.22, Math.cos(a) * 0.12);
                sp("streak", p).size(0.16f, 0.05f).life(18).vel(v).axial().colors(WHITE, PALE).envelope(0.1f, 0.6f, 0.9f);
            }
            if (t % 20 == 0) Brush.shock(g.add(0, 0.1, 0), Brush.UP, 1, 6, 0.12f, 14, SNOW.bright(0.5f));
        });
    }

    /** Absolute Zero: the air freezes in a huge wave, spikes erupt in rings, and a pillar of frost rises. */
    private static void zero(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        Brush.converge(c.chest(), 4, 12, 8, 0.08f, SNOW);
        at(7, () -> {
            Brush.glow(c.chest(), 3f, 14, ICE);
            Brush.pillar(c.pos, 1.0, 9, 26, 4, FROST);
            Brush.shock(g, Brush.UP, 1, 12, 0.55f, 18, FROST);
            Brush.shock(g.add(0, 0.25, 0), Brush.UP, 0.6, 10, 0.2f, 14, SNOW);
            Brush.circle(g, 12, 60, 8, FROST, BLUE);
            Brush.dome(c.chest(), 1, 5, 0.18f, 16, SNOW);
            for (int ring = 0; ring < 3; ring++) {
                int rr = ring;
                at(rr * 3, () -> {
                    int count = 10 + rr * 6;
                    for (int k = 0; k < count; k++) {
                        double a = k * Math.PI * 2 / count + rr * 0.3;
                        Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                        cluster(g.add(out.scale(3.5 + rr * 3)), 1.6 + rr * 0.4, 2, 50, out);
                    }
                });
            }
            glints(c.chest(), 5, 40);
        });
    }

    // ------------------------------------------------------------------ cues

    private static void chill(Ctx c) {
        Vec3 p = mid(c.target(), c.aim).get();
        Brush.ring(p.add(0, -0.5, 0), Brush.UP, 0.7, 0.07f, 12, 2, SNOW);
        glints(p, 0.4, 6);
    }

    /** An ice prison: spikes close in around the body and hold until the freeze ends. */
    private static void prison(Ctx c) {
        Entity t = c.target();
        Supplier<Vec3> feet = () -> t == null || t.isRemoved() ? c.aim.add(0, -0.9, 0) : t.position();
        float h = t == null ? 1.9f : t.getBbHeight();
        float w = t == null ? 0.6f : t.getBbWidth();
        int life = Math.max(20, c.ticks);
        for (int round = 0; round * 24 < life; round++) {
            int last = Math.min(28, life - round * 24 + 4);
            at(round * 24, () -> {
                Vec3 f = feet.get();
                for (int k = 0; k < 7; k++) {
                    double a = k * Math.PI * 2 / 7 + rnd() * 0.3;
                    Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 base = f.add(out.scale(w * 0.9 + 0.35));
                    Vec3 tip = f.add(out.scale(w * 0.25)).add(0, h * (0.8 + rnd() * 0.4), 0);
                    Brush.spike(base, tip, 0.2f, last, k % 2 == 0 ? FROST : BLUE);
                }
                Brush.glow(f.add(0, h * 0.5, 0), 1.0f, 10, ICE);
            });
        }
        Brush.ring(feet.get().add(0, 0.06, 0), Brush.UP, w + 0.6, 0.1f, life, 0.6, FROST);
        glints(feet.get().add(0, h * 0.5, 0), 0.5, 8);
    }

    private static void shatter(Ctx c) {
        Vec3 p = mid(c.target(), c.aim).get();
        impact(p, 1.6f);
        Brush.raysUp(p.add(0, -0.8, 0), 8, 2.4, 0.14f, 10, BLUE);
        Brush.embers(p, 16, 0.25, PALE);
    }
}
