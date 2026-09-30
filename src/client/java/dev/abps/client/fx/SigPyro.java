package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Pyromancer: fire in every form. Flames, embers, smoke, scorched ground and a falling star. Gold into red. */
final class SigPyro {

    private SigPyro() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> fireball(c);
            case 2 -> nova(c);
            case 3 -> blazeDash(c);
            case 4 -> meteor(c);
            case 6 -> inferno(c);
            default -> {
            }
        }
    }

    private static void flame(Vec3 at, float s0, float s1, int life, int col, Vec3 v) {
        sp("flame", at).size(s0, s1).life(life).colors(lighten(col, 0.8f), mix(col, 0x8A1000, 0.6f)).vel(v).drag(0.94f).flicker(0.35f).startRoll((float) (gauss() * 0.12)).envelope(0.05f, 0.5f, 0.95f);
    }

    private static void fireball(Ctx c) {
        Vec3 h = c.hand();
        bloom(h.add(c.look.scale(0.4)), 1.3f, 8, c.c1);
        sp("rays", h.add(c.look.scale(0.5))).size(0.4f, 1.8f).life(8).colors(WHITE, c.c1).spin(0.1f).envelope(0.05f, 0.3f, 0.9f);
        ringFlat(h.add(c.look.scale(0.6)), c.look, 0.2, 1.5, 8, c.c1, "shockwave");
        // A cone of flame thrown from the hand
        for (int k = 0; k < n(14); k++) {
            Vec3 d = c.look.add(gauss() * 0.16, gauss() * 0.16, gauss() * 0.16).normalize();
            flame(h.add(d.scale(0.4)), 0.5f + (float) rnd() * 0.4f, 0.2f, 12 + (int) (rnd() * 6), c.c1, d.scale(0.25 + rnd() * 0.2));
        }
        for (int k = 0; k < n(16); k++) c.skin.mote(h, c.look.scale(0.3 + rnd() * 0.3).add(gauss() * 0.1, gauss() * 0.1, gauss() * 0.1), 0.12f, 20, c.c1, 0f);
        gather(c, h, 2.4, 4, 3, c.c1);
    }

    private static void nova(Ctx c) {
        Vec3 p = c.live();
        double reach = 7;
        bloom(p.add(0, 1, 0), 3.0f, 10, c.c1);
        sp("rays", p.add(0, 1, 0)).size(1.0f, 5.5f).life(12).colors(WHITE, c.c1).spin(0.05f).envelope(0.05f, 0.3f, 0.9f);
        // Three rings of fire, each faster than the last, and a dome of embers over the top
        for (int k = 0; k < 3; k++) {
            int delay = k * 2;
            at(delay, () -> c.skin.ring(p.add(0, 0.15, 0), new Vec3(0, 1, 0), 0.5, reach * (1.0 + delay * 0.03), 14, c.c1));
        }
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.3, reach + 1, 12, WHITE, "shockwave");
        c.skin.ground(p, 4.5f, c.c1, 60);
        for (int k = 0; k < n(50); k++) {
            Vec3 d = rndDir();
            Vec3 v = new Vec3(d.x, Math.abs(d.y) * 0.8 + 0.1, d.z).normalize().scale(0.22 + rnd() * 0.25);
            c.skin.mote(p.add(0, 0.8, 0), v, 0.2f, 28, c.c1, 0.1f);
        }
        for (int k = 0; k < n(14); k++) {
            double a = rnd() * Math.PI * 2, r = rnd() * 3;
            flame(p.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1.2f, 0.4f, 16, c.c1, new Vec3(Math.cos(a) * 0.12, 0.14, Math.sin(a) * 0.12));
        }
        during(0, 12, t -> c.skin.column(p, 1.2, 5 * ease((t + 1) / 5.0), c.c1, t));
    }

    private static void blazeDash(Ctx c) {
        Vec3 p = c.live();
        Vec3 back = c.flat().scale(-1);
        bloom(p.add(0, 1, 0), 1.6f, 8, c.c1);
        // The path the dash burned into the ground, still burning, with a wall of heat at the start
        for (int k = 0; k < 12; k++) {
            Vec3 at = p.add(back.scale(0.8 + k * 0.85));
            int delay = k / 2;
            at(delay, () -> {
                for (int q = 0; q < 3; q++) {
                    flame(at.add(gauss() * 0.4, 0.05, gauss() * 0.4), 0.7f, 0.2f, 20 + (int) (rnd() * 12), c.c1, new Vec3(0, 0.06 + rnd() * 0.05, 0));
                }
                sp("glow", at.add(0, 0.3, 0)).size(1.1f, 0.5f).life(16).colors(lighten(c.c1, 0.5f), c.c2).envelope(0.1f, 0.5f, 0.7f);
                if (rnd() < 0.6) c.skin.body(at.add(0, 0.5, 0), new Vec3(0, 0.05, 0), 1.0f, 22, c.c1, 0.5f);
                c.skin.mote(at.add(0, 0.3, 0), new Vec3(gauss() * 0.05, 0.1 + rnd() * 0.1, gauss() * 0.05), 0.12f, 24, c.c1, 0f);
            });
        }
        for (int k = 0; k < n(12); k++) {
            Vec3 d = c.flat().add(gauss() * 0.3, gauss() * 0.15, gauss() * 0.3).normalize();
            flame(p.add(0, 1, 0), 0.6f, 0.2f, 12, c.c1, d.scale(-0.35 - rnd() * 0.2));
        }
        ringFlat(p.add(0, 1, 0), c.flat(), 0.3, 2.4, 9, c.c1, "shockwave");
    }

    private static void meteor(Ctx c) {
        Vec3 g = c.aim;
        Vec3 from = g.add(9, 46, -7);
        int flight = 28;
        // Warning: the mark burns into the ground and grows brighter as the rock falls
        sp("sigil", g.add(0, 0.05, 0)).size(6.5f, 6.5f).life(flight + 10).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(0.04f).envelope(0.15f, 0.8f, 0.85f);
        ringFlat(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 6.5, 6.6, flight, c.c2, "ring");
        during(0, flight, t -> {
            double f = ease(Math.pow((t + 1) / (double) flight, 1.6));
            Vec3 pos = from.lerp(g, f);
            Vec3 dir = g.subtract(from).normalize();
            // The rock: a dark core in a shroud of flame, dragging a long burning tail
            sp("glow", pos).size(3.2f, 2.4f).life(3).colors(lighten(c.c1, 0.6f), c.c1).envelope(0.1f, 0.5f, 0.95f);
            sp("glow", pos).size(1.7f, 1.3f).life(3).colors(WHITE, lighten(c.c1, 0.7f));
            sp("debris", pos).size(1.3f, 1.3f).life(3).colors(0x3A2A20, 0x201810).spin(0.2f).startRoll((float) (rnd() * 6.28)).bright();
            for (int k = 0; k < n(5); k++) {
                Vec3 off = new Vec3(gauss() * 0.9, gauss() * 0.9, gauss() * 0.9);
                flame(pos.add(off), 1.5f, 0.6f, 9, c.c1, dir.scale(-0.4).add(off.scale(0.03)));
            }
            for (int k = 0; k < n(3); k++) c.skin.body(pos.subtract(dir.scale(1 + rnd() * 3)), dir.scale(-0.05), 1.8f, 22, c.c1, 0.6f);
            c.skin.mote(pos.subtract(dir.scale(rnd() * 2)), dir.scale(-0.2).add(gauss() * 0.06, gauss() * 0.06, gauss() * 0.06), 0.18f, 22, c.c1, 0f);
        });
        // Impact
        at(flight, () -> {
            bloom(g.add(0, 1, 0), 6.0f, 14, c.c1);
            sp("rays", g.add(0, 1.5, 0)).size(2.0f, 11f).life(16).colors(WHITE, c.c1).spin(0.04f).envelope(0.05f, 0.3f, 0.95f);
            ringFlat(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 13, 18, c.c1, "shockwave");
            ringFlat(g.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.4, 9, 15, WHITE, "shockwave");
            c.skin.ring(g.add(0, 0.18, 0), new Vec3(0, 1, 0), 0.4, 7.5, 16, c.c1);
            c.skin.ground(g, 7f, c.c1, 100);
            for (int k = 0; k < n(60); k++) {
                Vec3 d = rndDir();
                Vec3 v = new Vec3(d.x, Math.abs(d.y) * 0.9 + 0.2, d.z).normalize().scale(0.25 + rnd() * 0.5);
                c.skin.mote(g.add(0, 0.6, 0), v, 0.24f, 34, c.c1, 0.4f);
            }
            for (int k = 0; k < n(24); k++) {
                double a = rnd() * Math.PI * 2, r = rnd() * 5;
                flame(g.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1.6f, 0.5f, 22 + (int) (rnd() * 10), c.c1, new Vec3(Math.cos(a) * 0.1, 0.16 + rnd() * 0.1, Math.sin(a) * 0.1));
            }
            // The mushroom of smoke climbing off the crater
            during(0, 30, t -> {
                for (int k = 0; k < n(3); k++) {
                    double h = 1 + t * 0.35 + rnd() * 2;
                    double spread = 1.2 + Math.min(3.5, h * 0.5);
                    double a = rnd() * Math.PI * 2;
                    c.skin.body(g.add(Math.cos(a) * spread * rnd(), h, Math.sin(a) * spread * rnd()), new Vec3(0, 0.06, 0), 2.2f, 30, c.c1, 0.8f);
                }
                if (t < 12) c.skin.column(g, 2.0, 8 * ease((t + 1) / 5.0), c.c1, t);
            });
        });
    }

    private static void inferno(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 4.0f, 12, c.c1);
        sp("rays", p.add(0, 1.2, 0)).size(1.5f, 8f).life(16).colors(WHITE, c.c1).spin(0.04f).envelope(0.05f, 0.3f, 0.9f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 10, 16, c.c1, "shockwave");
        sp("sigil", p.add(0, 0.05, 0)).size(8.5f, 8.5f).life(125).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(0.04f).flicker(0.15f).envelope(0.1f, 0.85f, 0.9f);
        // The whole six seconds: a spinning column of fire around the caster, a ring of flame at the edge, embers spiralling up
        during(0, 124, t -> {
            Vec3 b = c.live();
            for (int k = 0; k < n(3); k++) {
                double h = rnd() * 9, a = t * 0.35 + h * 0.8 + k * 2.1, r = 1.8 + h * 0.18;
                flame(b.add(Math.cos(a) * r, h, Math.sin(a) * r), 1.5f, 0.6f, 9, c.c1, new Vec3(-Math.sin(a) * 0.16, 0.12, Math.cos(a) * 0.16));
            }
            c.skin.column(b, 1.4, 8, c.c1, t);
            for (int k = 0; k < n(2); k++) {
                double a = rnd() * Math.PI * 2;
                flame(b.add(Math.cos(a) * 8, 0.1, Math.sin(a) * 8), 1.1f, 0.4f, 12, c.c1, new Vec3(0, 0.09 + rnd() * 0.05, 0));
            }
            if (t % 10 == 0) c.skin.ring(b.add(0, 0.15, 0), new Vec3(0, 1, 0), 1.0, 8.5, 14, c.c1);
            if (t % 3 == 0) c.skin.mote(b.add(gauss() * 6, 0.2, gauss() * 6), new Vec3(gauss() * 0.04, 0.22 + rnd() * 0.15, gauss() * 0.04), 0.16f, 34, c.c1, 0f);
            if (t % 4 == 0) c.skin.body(b.add(gauss() * 1.5, 8.5 + rnd() * 2, gauss() * 1.5), new Vec3(0, 0.05, 0), 2.4f, 30, c.c1, 0.7f);
        });
    }
}
