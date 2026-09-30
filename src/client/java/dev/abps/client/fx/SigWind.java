package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Windwalker: air. Pointed wind lines, spinning discs, white cloud, tornados and lightning. White and sky blue. */
final class SigWind {

    private SigWind() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> updraft(c);
            case 2 -> gust(c);
            case 3 -> tailwind(c);
            case 4 -> tornado(c);
            case 6 -> storm(c);
            default -> {
            }
        }
    }

    private static void updraft(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 0.6, 0), 2.2f, 9, c.c2);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 7, 14, c.c2, "shockwave");
        // A column of air punching upward: spinning discs stacked up it, and pale streaks racing overhead
        for (int k = 0; k < 6; k++) {
            int idx = k;
            at(k, () -> sp("swirl", p.add(0, 0.2 + idx * 0.9, 0)).size(1.4f + idx * 0.25f, 2.4f + idx * 0.45f).life(12).colors(WHITE, c.c2).facing(0, 1, 0).spin(0.3f).envelope(0.1f, 0.4f, 0.85f));
        }
        during(0, 14, t -> {
            for (int k = 0; k < n(6); k++) {
                double a = rnd() * Math.PI * 2, r = 0.4 + rnd() * 1.6;
                sp("streak", p.add(Math.cos(a) * r, rnd() * 2, Math.sin(a) * r)).size(2.6f, 0.5f).life(8).colors(WHITE, c.c2).vel(0, 0.7, 0).axis(0, 1, 0).drag(1f).envelope(0.05f, 0.4f, 0.85f);
            }
            c.skin.column(p, 1.2, 6 * ease((t + 1) / 6.0), c.c2, t);
        });
        for (int k = 0; k < n(14); k++) {
            double a = rnd() * Math.PI * 2;
            c.skin.body(p.add(Math.cos(a) * 1.2, 0.3, Math.sin(a) * 1.2), new Vec3(Math.cos(a) * 0.18, 0.04, Math.sin(a) * 0.18), 1.2f, 20, c.c2, 0.7f);
        }
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

    private static void tailwind(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 1.6f, 8, c.c2);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 4, 12, c.c2, "shockwave");
        sp("swirl", p.add(0, 0.15, 0)).size(1.4f, 3.4f).life(14).colors(WHITE, c.c2).facing(0, 1, 0).spin(0.35f).envelope(0.1f, 0.4f, 0.85f);
        // Speed lines streaming back from the runner and a little whirl at the feet, for as long as the boost is fresh
        Vec3 dir = c.flat();
        during(0, 40, t -> {
            Vec3 b = c.live();
            Vec3 back = dir.scale(-1);
            for (int k = 0; k < n(3); k++) {
                Vec3 off = c.right().scale(gauss() * 0.5).add(0, 0.2 + rnd() * 1.6, 0);
                sp("streak", b.add(off).add(back.scale(1.5))).size(2.4f, 0.4f).life(7).colors(WHITE, c.c2).vel(back.scale(0.05)).axis(dir.x, 0, dir.z).drag(0.95f).envelope(0.05f, 0.4f, 0.8f);
            }
            if (t % 3 == 0) sp("swirl", b.add(0, 0.12, 0)).size(0.8f, 1.5f).life(6).colors(WHITE, c.c2).facing(0, 1, 0).spin(0.5f).envelope(0.1f, 0.4f, 0.7f);
            if (t % 4 == 0) c.skin.body(b.add(gauss() * 0.3, 0.2, gauss() * 0.3), back.scale(0.05), 0.7f, 12, c.c2, 0.5f);
        });
    }

    private static void tornado(Ctx c) {
        Vec3 g = c.aim;
        double height = 13;
        bloom(g.add(0, 0.5, 0), 2.2f, 9, c.c2);
        ringFlat(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 9, 16, c.c2, "shockwave");
        sp("swirl", g.add(0, 0.12, 0)).size(5.5f, 5.5f).life(104).colors(WHITE, c.c2).facing(0, 1, 0).spin(0.2f).envelope(0.08f, 0.85f, 0.8f);
        // A funnel: wind lines wrapping a cone that widens with height, dirt and cloud dragged up the middle
        during(0, 100, t -> {
            double grow = Math.min(1.0, t / 10.0);
            for (int k = 0; k < n(14); k++) {
                double f = rnd();
                double y = f * height * grow;
                double r = 0.7 + f * 3.4;
                double a = t * 0.55 + f * 9.0 + k * 0.9;
                Vec3 at = g.add(Math.cos(a) * r, y, Math.sin(a) * r);
                sp("streak", at).size(1.5f + (float) f * 1.5f, 0.5f).life(4).colors(WHITE, mix(c.c2, WHITE, 0.4f)).vel(-Math.sin(a) * 0.6, 0.06, Math.cos(a) * 0.6).axial().drag(1f).envelope(0.1f, 0.5f, 0.85f);
            }
            for (int k = 0; k < n(2); k++) {
                double f = rnd(), a = t * 0.4 + k * 3.1;
                c.skin.body(g.add(Math.cos(a) * (0.5 + f * 2.2), f * height * grow, Math.sin(a) * (0.5 + f * 2.2)), new Vec3(0, 0.04, 0), (float) (1.0 + f * 1.8), 14, c.c2, 0.7f);
            }
            if (t % 3 == 0) {
                double a = rnd() * Math.PI * 2;
                sp("debris", g.add(Math.cos(a) * 1.5, 0.3, Math.sin(a) * 1.5)).size(0.16f, 0.1f).life(24).colors(0x907050, 0x604830).vel(-Math.sin(a) * 0.4, 0.2, Math.cos(a) * 0.4).grav(0.15f).drag(0.98f).spin(0.3f).bright().envelope(0.05f, 0.75f, 1f);
            }
            if (t % 14 == 0) ringFlat(g.add(0, 0.12, 0), new Vec3(0, 1, 0), 0.5, 7, 12, c.c2, "ring");
        });
    }

    private static void storm(Ctx c) {
        Vec3 p = c.live();
        double reach = 14;
        bloom(p.add(0, 1, 0), 3.4f, 12, c.c2);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 16, 18, c.c2, "shockwave");
        // The caster climbs into a ring of storm: a huge slow disc of wind on the ground, cloud overhead and bolts falling all around
        sp("swirl", p.add(0, 0.14, 0)).size((float) reach, (float) reach).life(130).colors(WHITE, c.c2).facing(0, 1, 0).spin(0.09f).envelope(0.08f, 0.85f, 0.75f);
        sp("swirl", p.add(0, 0.18, 0)).size((float) reach * 0.55f, (float) reach * 0.55f).life(130).colors(lighten(c.c2, 0.5f), c.c2).facing(0, 1, 0).spin(-0.14f).envelope(0.08f, 0.85f, 0.6f);
        during(0, 124, t -> {
            Vec3 b = c.live();
            double a = t * 0.05;
            for (int k = 0; k < n(3); k++) {
                double ang = a * 3 + k * 2.09 + rnd() * 0.5;
                double r = 6 + rnd() * (reach - 6);
                c.skin.body(b.add(Math.cos(ang) * r, 9 + rnd() * 3, Math.sin(ang) * r), new Vec3(-Math.sin(ang) * 0.08, 0, Math.cos(ang) * 0.08), 4.0f, 30, mix(c.c2, 0x404858, 0.5f), 0.7f);
            }
            for (int k = 0; k < n(6); k++) {
                double ang = rnd() * Math.PI * 2, r = 3 + rnd() * (reach - 3);
                Vec3 at = b.add(Math.cos(ang) * r, 0.4 + rnd() * 4, Math.sin(ang) * r);
                sp("streak", at).size(2.0f, 0.5f).life(4).colors(WHITE, c.c2).vel(-Math.sin(ang) * 0.7, 0.02, Math.cos(ang) * 0.7).axial().drag(1f).envelope(0.1f, 0.5f, 0.8f);
            }
            if (t % 20 == 0) ringFlat(b.add(0, 0.14, 0), new Vec3(0, 1, 0), 1, reach + 2, 16, c.c2, "ring");
            if (t % 12 == 4) {
                double ang = rnd() * Math.PI * 2, r = 2 + rnd() * (reach - 2);
                Vec3 g = b.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                sp("bolt", g.add(0, 15, 0)).size(9f, 9f).life(5).colors(WHITE, c.c2).axis(0, 1, 0).upright().envelope(0.05f, 0.4f, 1f);
                sp("bolt", g.add(0, 15, 0)).size(9f, 9f).life(3).colors(WHITE, WHITE).axis(0, 1, 0).upright().envelope(0.05f, 0.4f, 1f);
                bloom(g.add(0, 0.5, 0), 2.2f, 8, c.c2);
                ringFlat(g.add(0, 0.12, 0), new Vec3(0, 1, 0), 0.3, 4.0, 10, WHITE, "shockwave");
                for (int k = 0; k < n(12); k++) c.skin.mote(g.add(0, 0.3, 0), rndUp().scale(0.25), 0.16f, 14, c.c2, 0.2f);
            }
        });
    }
}
