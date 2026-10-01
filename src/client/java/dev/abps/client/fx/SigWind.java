package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Windwalker: air. White and sky blue strokes of wind, funnels, and lightning out of a storm. */
final class SigWind {

    private SigWind() {
    }

    static final int SKY = 0x29B6F6, CLOUD = 0xE0F7FA;
    static final Paint WIND = Paint.light(0xBFEFFF, 0.9f);
    static final Paint BLUE = Paint.light(SKY, 1.2f);
    static final Paint BOLT = Paint.light(0x9BE3FF, 1.4f);

    static final int CUE_BOLT = 11, CUE_JUMP = 12;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> updraft(c);
            case 2 -> gust(c);
            case 3 -> tailwind(c);
            case 4 -> tornado(c);
            case 6 -> storm(c);
            case CUE_BOLT -> strike(c.target() == null ? c.aim : c.target().position());
            case CUE_JUMP -> {
                Brush.shock(c.pos.add(0, 0.05, 0), Brush.UP, 0.3, 2.2, 0.12f, 9, WIND);
                Brush.ring(c.pos.add(0, 0.3, 0), Brush.UP, 0.7, 0.07f, 8, 3, BLUE);
            }
            default -> {
            }
        }
    }

    static Supplier<Vec3> feet(Ctx c) {
        Entity e = c.caster();
        return () -> e == null || e.isRemoved() ? c.pos : e.position();
    }

    /** A spinning funnel of wind strokes: radius widens with height. Spawns a few strokes each call. */
    static void funnel(Vec3 g, double r0, double r1, double h, int count, double phase) {
        for (int k = 0; k < count; k++) {
            double y = rnd() * h, f = y / h;
            double r = r0 + (r1 - r0) * f;
            Vec3 mid = rotY(new Vec3(1, 0, 0), phase + rnd() * Math.PI * 2);
            (k % 2 == 0 ? BLUE : WIND).on(Ribbon.arc(g.add(0, y, 0), mid, Brush.UP, r, 2.2, 2.6, 1.0)).width((float) (0.18 + f * 0.22)).time(12, 3)
                    .hold(0.2f).tailChase(0.8f).sparks(0).segments(16).play();
        }
    }

    /** Lightning out of the sky onto a point, with a blast where it lands. */
    static void strike(Vec3 g) {
        Brush.bolt(g.add(gauss() * 1.5, 16, gauss() * 1.5), g.add(0, 0.2, 0), 0.32f, 9, 1.0, BOLT);
        at(1, () -> {
            Brush.glow(g.add(0, 0.6, 0), 2.2f, 9, SKY);
            Brush.shock(g.add(0, 0.08, 0), Brush.UP, 0.3, 3.6, 0.2f, 11, BLUE);
            Brush.raysUp(g.add(0, 0.2, 0), 6, 2.2, 0.07f, 8, BOLT);
            Brush.sparks(g.add(0, 0.4, 0), 10, 0.4, SKY);
        });
    }

    /** Updraft: a blast ring on the ground and a funnel of wind that throws you up. */
    private static void updraft(Ctx c) {
        Vec3 g = c.pos;
        Brush.shock(g.add(0, 0.08, 0), Brush.UP, 0.4, 4.5, 0.28f, 12, BLUE);
        Brush.glow(g.add(0, 0.6, 0), 1.6f, 8, SKY);
        during(0, 14, t -> {
            if (t % 2 == 0) funnel(g, 0.6, 2.2, 6 * Math.min(1, (t + 2) / 6.0), 3, t * 0.7);
        });
        Brush.mist(g.add(0, 0.3, 0), 1.2, 8, 0xDDEEF5, 0.35f, 1.2f, 20);
    }

    private static void gust(Ctx c) {
        Vec3 h = c.eye().add(c.look.scale(0.5));
        Vec3[] uv = axes(c.look);
        Vec3 look = c.look.normalize();
        Vec3 eye = c.eye();
        // "Up" for the blast: the world up, flattened against the aim, so the blades stay level wherever you look
        Vec3 up = new Vec3(0, 1, 0).subtract(look.scale(look.y));
        up = up.lengthSqr() < 0.01 ? uv[1] : up.normalize();
        Vec3 side = look.cross(up).normalize();
        final Vec3 fUp = up, fSide = side;
        bloom(h, 0.8f, 6, c.c2);

        // Three arcs of wind, one after another, facing you like a wave front and flying 10 blocks as they widen.
        // Their ends trail behind the middle, so each reads as a curved gust pushing forward.
        for (int k = 0; k < 3; k++) {
            final double roll = (k - 1) * 0.55; // each one turned a little around the aim
            final boolean middle = k == 1;
            Signatures.at(k * 2, () -> {
                Ribbon.Curve radial = (s, time) -> {
                    double a = Math.PI / 2 + roll + (s - 0.5) * 3.0;
                    return fSide.scale(Math.cos(a)).add(fUp.scale(Math.sin(a)));
                };
                Ribbon.Curve pos = (s, time) -> {
                    double eased = 1 - Math.pow(1 - time, 2);
                    double r = 0.9 + 2.4 * eased;
                    double bow = Math.pow(Math.abs(s - 0.5) * 2, 2) * r * 0.45;
                    return eye.add(look.scale(1.2 + 9.5 * eased - bow)).add(radial.at(s, time).scale(r));
                };
                Ribbon.along(Ribbon.curve(pos, radial)).energy(middle ? WHITE : c.c2).width(0.55f).time(13, 2).hold(0.35f).tailChase(0f)
                        .brightness(middle ? 1.2f : 1.5f).sparks(0).segments(26).play();
            });
        }
        // Corkscrew streams of air spiralling along the blast
        for (int k = 0; k < 4; k++) {
            final double start = Math.PI / 2 * k;
            Ribbon.Curve pos = (s, time) -> {
                double a = start + s * Math.PI * 2 * 1.2 + time * 3.0;
                double r = 0.25 + 1.1 * s;
                return eye.add(look.scale(0.8 + 9 * s)).add(fSide.scale(Math.cos(a) * r)).add(fUp.scale(Math.sin(a) * r));
            };
            Ribbon.Curve edge = (s, time) -> {
                double a = start + s * Math.PI * 2 * 1.2 + time * 3.0;
                return fSide.scale(Math.cos(a)).add(fUp.scale(Math.sin(a)));
            };
            Ribbon.along(Ribbon.curve(pos, edge)).energy(k % 2 == 0 ? c.c2 : lighten(c.c2, 0.5f)).width(0.22f).time(14, 5).hold(0.2f)
                    .tailChase(0.7f).brightness(1.2f).sparks(0).segments(36).play();
        }
        // A couple of puffs of cloud pushed along, not a wall of them
        for (int k = 0; k < 2; k++) c.skin.body(eye.add(look.scale(3 + k * 3)), look.scale(0.35), 1.0f, 12, c.c2, 0.45f);
    }

    /** Tailwind: a whirl at your feet and wind lines streaming off you for as long as it lasts. */
    private static void tailwind(Ctx c) {
        Supplier<Vec3> feet = feet(c);
        Entity e = c.caster();
        int life = Math.max(40, c.ticks);
        Brush.shock(c.pos.add(0, 0.08, 0), Brush.UP, 0.3, 3, 0.18f, 10, BLUE);
        Brush.helix(feet, 0.8, 2.0, 1.6, 0.09f, 16, 0, WIND);
        during(0, life, t -> {
            if (e == null) return;
            Vec3 v = e.getDeltaMovement();
            if (v.horizontalDistanceSqr() < 0.01 || t % 2 != 0) return;
            Vec3 d = Brush.flat(v);
            Vec3 p = e.position().add(gauss() * 0.35, 0.3 + rnd() * 1.5, gauss() * 0.35);
            WIND.on(Brush.lineCam(p.subtract(d.scale(2.2)), p)).width(0.05f).time(8, 2).hold(0f).tailChase(1f).sparks(0).segments(8).play();
        });
    }

    /** Tornado: a tall funnel of wind strokes spinning over a ring on the ground, dragging dust up. */
    private static void tornado(Ctx c) {
        Vec3 g = c.aim;
        int life = Math.max(40, c.ticks);
        Brush.circle(g, 6, life, 6, BLUE, WIND);
        Brush.shock(g.add(0, 0.08, 0), Brush.UP, 0.5, 6, 0.25f, 12, BLUE);
        during(0, life, t -> {
            double grow = Math.min(1, t / 10.0);
            funnel(g, 0.7, 3.8, 12 * grow, 5, t * 0.5);
            if (t % 2 == 0) BLUE.on(Ribbon.spiral(g, 0.5, 3.4, 11 * grow, 1.4, t * 0.6, 4)).width(0.14f).time(10, 4).hold(0.2f).tailChase(0.8f).sparks(0).play();
            if (t % 3 == 0) SigMiner.debris(g.add(gauss() * 1.2, 0.3, gauss() * 1.2), 1, 0.3);
            if (t % 8 == 0) Brush.mist(g.add(0, 0.5 + rnd() * 6, 0), 1.2, 2, 0xD0E4EC, 0.35f, 1.6f, 20);
        });
    }

    /** Eye of the Storm: a huge slow ring of wind around you and storm cloud above; the server sends each bolt. */
    private static void storm(Ctx c) {
        Supplier<Vec3> feet = feet(c);
        int life = Math.max(60, c.ticks);
        Brush.glow(c.chest(), 2.4f, 12, SKY);
        Brush.shock(c.pos.add(0, 0.08, 0), Brush.UP, 0.6, 14, 0.4f, 16, BLUE);
        during(0, life, t -> {
            Vec3 b = feet.get();
            if (t % 6 == 0) {
                Vec3 mid = rotY(new Vec3(1, 0, 0), t * 0.2);
                WIND.on(Ribbon.arc(b.add(0, 0.6 + rnd() * 3, 0), mid, Brush.UP, 7 + rnd() * 6, 2.2, 1.4, 1.0)).width(0.2f).time(16, 4).hold(0.3f)
                        .tailChase(0.7f).sparks(0).play();
            }
            if (t % 5 == 0) {
                double a = rnd() * Math.PI * 2, r = 4 + rnd() * 9;
                Vec3 p = b.add(Math.cos(a) * r, 10 + rnd() * 2, Math.sin(a) * r);
                sp("smoke", p).size(2.5f, 3.5f).life(30).colors(0x5A6470, 0x3A4048).vel(-Math.sin(a) * 0.08, 0, Math.cos(a) * 0.08)
                        .envelope(0.15f, 0.6f, 0.6f);
            }
            if (t % 20 == 0) Brush.ring(b.add(0, 0.1, 0), Brush.UP, 14, 0.12f, 18, 0.6, BLUE.bright(0.6f));
        });
    }
}
