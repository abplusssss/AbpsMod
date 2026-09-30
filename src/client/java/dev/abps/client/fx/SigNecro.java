package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Necromancer: souls, skulls, cold fog and rune circles. Teal over deep violet. */
final class SigNecro {

    private SigNecro() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> army(c);
            case 2 -> drain(c);
            case 3 -> sic(c);
            case 4 -> knight(c);
            case 6 -> damned(c);
            default -> {
            }
        }
    }

    /** A soul that follows a curved path from one place to another and fades out as it arrives. */
    private static void soul(Vec3 from, Vec3 to, Vec3 bend, int life, float size, int col, int col2) {
        FxParticle w = sp("wisp", from).size(size * 2.6f, size).life(life).colors(lighten(col, 0.5f), col2).drag(1f).envelope(0.1f, 0.6f, 1f);
        w.motion((p, age) -> {
            double t = Math.min(1.0, (age + 1) / (double) life);
            Vec3 pos = from.scale((1 - t) * (1 - t)).add(bend.scale(2 * (1 - t) * t)).add(to.scale(t * t));
            p.steer(pos.x, pos.y, pos.z);
        });
    }

    private static void army(Ctx c) {
        Vec3 p = c.live();
        sp("sigil", p.add(0, 0.05, 0)).size(5.6f, 5.6f).life(70).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(0.03f).envelope(0.1f, 0.75f, 0.95f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 7.0, 14, c.c1, "shockwave");
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            Vec3 at = p.add(Math.cos(a) * 3.6, 0, Math.sin(a) * 3.6);
            at(k, () -> {
                bloom(at.add(0, 0.5, 0), 0.9f, 7, c.c1);
                during(0, 14, t -> c.skin.column(at, 0.55, 3.2 * ease((t + 1) / 7.0), c.c1, t));
                sp("skull", at.add(0, 3.0, 0)).size(0.5f, 0.7f).life(16).colors(WHITE, c.c1).vel(0, 0.05, 0).envelope(0.15f, 0.5f, 0.9f);
                for (int q = 0; q < n(6); q++) c.skin.mote(at.add(0, 0.4, 0), rndUp().scale(0.12), 0.16f, 20, c.c1, 0f);
            });
        }
        during(0, 30, t -> {
            for (int k = 0; k < n(3); k++) {
                double a = rnd() * Math.PI * 2, r = 0.5 + rnd() * 4.5;
                c.skin.body(p.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), new Vec3(0, 0.03, 0), 1.6f, 30, c.c1, 0.6f);
            }
        });
        during(0, 26, t -> {
            double h = 8 * ease((t + 1) / 14.0);
            for (double y = 0; y < h; y += 1.0) sp("glow", p.add(0, y, 0)).size(0.7f, 0.3f).life(3).colors(WHITE, c.c1).envelope(0.1f, 0.4f, 0.55f);
        });
    }

    private static void drain(Ctx c) {
        Vec3 f = c.focus();
        bloom(f, 1.4f, 9, c.c1);
        sp("skull", f.add(0, 0.4, 0)).size(0.9f, 1.3f).life(16).colors(WHITE, c.c1).vel(0, 0.03, 0).envelope(0.15f, 0.5f, 0.9f);
        ringFlat(f, c.eye().subtract(f).lengthSqr() < 0.01 ? c.look : c.eye().subtract(f).normalize(), 0.3, 2.6, 12, c.c1, "shockwave");
        // Souls streaming out of the victim into the caster along bending paths
        during(0, 18, t -> {
            Vec3 dest = c.live().add(0, 1.2, 0);
            for (int k = 0; k < n(4); k++) {
                Vec3 bend = f.lerp(dest, 0.5).add(gauss() * 1.2, 0.8 + rnd() * 1.6, gauss() * 1.2);
                soul(f.add(gauss() * 0.3, gauss() * 0.4, gauss() * 0.3), dest, bend, 12 + (int) (rnd() * 6), 0.4f, c.c1, c.c2);
            }
            c.skin.along(f.lerp(dest, rnd()), dest.subtract(f).normalize(), 0.3f, c.c1);
        });
        at(12, () -> {
            Vec3 chest = c.live().add(0, 1.2, 0);
            bloom(chest, 1.0f, 8, c.c1);
            for (int k = 0; k < n(12); k++) c.skin.mote(chest, rndDir().scale(0.2), 0.14f, 16, c.c1, 0f);
            sp("heart", chest.add(0, 0.7, 0)).size(0.3f, 0.2f).life(24).colors(WHITE, c.c1).vel(0, 0.05, 0).envelope(0.15f, 0.5f, 0.9f);
        });
    }

    private static void sic(Ctx c) {
        Vec3 p = c.live();
        Vec3 f = c.focus();
        Vec3 dir = f.subtract(p.add(0, 1.2, 0));
        Vec3 d = dir.lengthSqr() < 0.01 ? c.look : dir.normalize();
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 6.0, 14, c.c1, "shockwave");
        c.skin.ring(p.add(0, 0.12, 0), new Vec3(0, 1, 0), 0.4, 5.0, 14, c.c1);
        // Pale arrows racing away toward the target, each with a ghost trail
        for (int k = 0; k < 6; k++) {
            Vec3 off = new Vec3(gauss() * 0.5, gauss() * 0.3, gauss() * 0.5);
            int delay = k;
            at(delay, () -> {
                Vec3 from = c.live().add(0, 1.4, 0).add(off);
                Vec3 to = f.add(off.scale(0.4));
                projectile(c, from, to, 2.2, 0.28f, c.c1, null);
                sp("arrow", from).size(0.9f, 0.9f).life(Math.max(4, (int) (from.distanceTo(to) / 2.2))).colors(WHITE, c.c1).vel(to.subtract(from).normalize().scale(2.2)).drag(1f).axial().envelope(0.05f, 0.85f, 1f);
            });
        }
        // The victim is branded with a rune
        at(4, () -> {
            sp("rune", f).size(0.9f, 1.4f).life(30).colors(WHITE, c.c1).spin(0.08f).envelope(0.1f, 0.6f, 1f);
            sp("skull", f.add(0, 1.3, 0)).size(0.5f, 0.6f).life(30).colors(WHITE, c.c1).envelope(0.15f, 0.6f, 1f).flicker(0.3f);
            bloom(f, 1.0f, 8, c.c1);
            ringFlat(f, d, 0.3, 2.0, 10, c.c1, "ring");
        });
    }

    private static void knight(Ctx c) {
        Vec3 p = c.live().add(c.flat().scale(4));
        sp("sigil", p.add(0, 0.05, 0)).size(6.5f, 6.5f).life(90).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(0.025f).envelope(0.1f, 0.8f, 0.95f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 8.5, 16, c.c1, "shockwave");
        c.skin.ground(p, 5f, c.c1, 90);
        // Three runic rings climbing a pillar of dark fire
        for (int k = 0; k < 4; k++) {
            int delay = k * 4;
            at(delay, () -> {
                for (int q = 0; q < 6; q++) {
                    int idx = q;
                    at(idx * 2, () -> ringFlat(p.add(0, 0.3 + idx * 1.6, 0), new Vec3(0, 1, 0), 1.6, 1.9, 8, c.c1, "ring"));
                }
            });
        }
        during(0, 34, t -> c.skin.column(p, 1.5, 11 * ease((t + 1) / 14.0), c.c1, t));
        // Lightning falls onto the summoning
        for (int k = 0; k < 3; k++) {
            int delay = 4 + k * 5;
            at(delay, () -> {
                double a = rnd() * Math.PI * 2, r = rnd() * 3;
                Vec3 g = p.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                sp("bolt", g.add(0, 12, 0)).size(6f, 6f).life(5).colors(WHITE, c.c1).axis(0, 1, 0).upright().envelope(0.05f, 0.4f, 1f);
                bloom(g.add(0, 0.4, 0), 1.6f, 7, c.c1);
            });
        }
        // Something enormous takes shape at the top
        at(14, () -> {
            sp("skull", p.add(0, 8.5, 0)).size(2.4f, 3.0f).life(40).colors(lighten(c.c1, 0.6f), c.c2).envelope(0.25f, 0.6f, 0.95f).flicker(0.2f);
            bloom(p.add(0, 8.5, 0), 3.0f, 14, c.c1);
        });
    }

    private static void damned(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 3.4f, 12, c.c1);
        sp("sigil", p.add(0, 0.05, 0)).size(11f, 11f).life(70).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(-0.03f).envelope(0.1f, 0.75f, 0.95f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 12, 18, c.c1, "shockwave");
        ringFlat(p.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.4, 9, 15, WHITE, "ring");
        // A wave of souls bursting out in every direction, skulls among them
        for (int k = 0; k < n(70); k++) {
            Vec3 d = rndDir();
            Vec3 v = new Vec3(d.x, d.y * 0.6 + 0.1, d.z).normalize().scale(0.25 + rnd() * 0.3);
            c.skin.mote(p.add(0, 1.2, 0), v, 0.24f, 30, c.c1, 0f);
        }
        for (int k = 0; k < 10; k++) {
            double a = Math.PI * 2 * k / 10;
            sp("skull", p.add(0, 1.4, 0)).size(0.6f, 1.1f).life(30).colors(WHITE, c.c1).vel(Math.cos(a) * 0.32, 0.03, Math.sin(a) * 0.32).drag(0.95f).envelope(0.1f, 0.5f, 0.95f).flicker(0.3f);
        }
        // Columns of souls climbing out of the ground all around, and a ring of falling runes overhead
        during(0, 44, t -> {
            for (int k = 0; k < n(3); k++) {
                double a = rnd() * Math.PI * 2, r = 2 + rnd() * 8;
                Vec3 at = p.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                c.skin.column(at, 0.4, 3 + rnd() * 3, c.c1, t);
            }
            double a = t * 0.3;
            for (int k = 0; k < 6; k++) {
                double b = a + k * Math.PI / 3;
                sp("rune", p.add(Math.cos(b) * 8, 9 - t * 0.05, Math.sin(b) * 8)).size(0.7f, 0.5f).life(6).colors(WHITE, c.c1).envelope(0.1f, 0.5f, 0.9f);
            }
            if (t % 10 == 0) ringFlat(p.add(0, 0.12, 0), new Vec3(0, 1, 0), 0.5, 10, 14, c.c1, "ring");
        });
    }
}
