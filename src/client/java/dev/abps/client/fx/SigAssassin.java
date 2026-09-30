package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Assassin: ink-black smoke, violet blades, afterimages and claw marks. */
final class SigAssassin {

    private SigAssassin() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> vanish(c);
            case 2 -> dash(c);
            case 3 -> smokeBomb(c);
            case 4 -> shadowstep(c);
            case 6 -> cuts(c);
            default -> {
            }
        }
    }

    private static void vanish(Ctx c) {
        Vec3 p = c.live();
        // The body comes apart into black smoke that pours upward, while shards spiral away
        for (int k = 0; k < n(26); k++) {
            Vec3 at = p.add(gauss() * 0.35, rnd() * 1.9, gauss() * 0.35);
            c.skin.body(at, new Vec3(gauss() * 0.04, 0.03 + rnd() * 0.06, gauss() * 0.04), 0.9f + (float) rnd() * 0.8f, 26 + (int) (rnd() * 12), c.c2, 1f);
        }
        during(0, 16, t -> {
            for (int k = 0; k < 3; k++) {
                double a = t * 0.5 + k * 2.09;
                double h = t / 16.0 * 2.2;
                Vec3 pos = c.live().add(Math.cos(a) * (0.8 - h * 0.15), h, Math.sin(a) * (0.8 - h * 0.15));
                c.skin.mote(pos, new Vec3(-Math.sin(a) * 0.06, 0.05, Math.cos(a) * 0.06), 0.12f, 14, c.c1, 0f);
            }
        });
        bloom(p.add(0, 1, 0), 1.1f, 7, c.c1);
        c.skin.ground(p, 2.4f, c.c2, 50);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.3, 3.2, 12, c.c1, "shockwave");
    }

    private static void dash(Ctx c) {
        Vec3 dir = c.flat();
        Vec3 start = c.pos;
        double len = 9;
        // Afterimages: dark shapes left where the assassin was, plus violet speed lines
        for (int k = 0; k < 6; k++) {
            Vec3 at = start.add(dir.scale(k * len / 6.0)).add(0, 1.0, 0);
            int delay = k;
            at(delay, () -> {
                for (int q = 0; q < n(4); q++) c.skin.body(at.add(gauss() * 0.2, gauss() * 0.5, gauss() * 0.2), dir.scale(-0.02), 0.9f, 16, c.c2, 1f);
                sp("glow", at).size(1.0f, 0.3f).life(6).colors(lighten(c.c1, 0.4f), c.c2).envelope(0.1f, 0.4f, 0.7f);
            });
        }
        for (int k = 0; k < n(8); k++) {
            Vec3 off = new Vec3(gauss() * 0.4, 0.2 + rnd() * 1.7, gauss() * 0.4).add(c.right().scale(gauss() * 0.3));
            Vec3 a = start.add(off);
            sp("streak", a.add(dir.scale(len * 0.5))).size((float) (len * 0.55), 0.2f).life(9).colors(WHITE, c.c1).vel(dir.scale(0.001)).axial().drag(1f).envelope(0.05f, 0.4f, 0.8f);
        }
        ringFlat(start.add(0, 1, 0), dir, 0.2, 2.0, 8, c.c1, "ring");
        ringFlat(start.add(dir.scale(len)).add(0, 1, 0), dir, 1.6, 0.2, 8, c.c1, "ring");
        for (int k = 0; k < n(10); k++) c.skin.mote(start.add(0, 0.3, 0), dir.scale(-0.2 - rnd() * 0.2).add(gauss() * 0.05, rnd() * 0.1, gauss() * 0.05), 0.14f, 12, c.c1, 0.2f);
    }

    private static void smokeBomb(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 0.8, 0), 1.6f, 8, c.c1);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 6.0, 14, c.c1, "shockwave");
        // A wall of black smoke rolling out to five blocks and hanging there
        during(0, 10, t -> {
            for (int k = 0; k < n(9); k++) {
                double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 4.6;
                Vec3 at = c.pos.add(Math.cos(a) * r * 0.4, 0.4 + rnd() * 2.2, Math.sin(a) * r * 0.4);
                Vec3 out = new Vec3(Math.cos(a), 0.02, Math.sin(a)).scale(r * 0.05);
                sp("smoke", at).size(1.2f, 3.4f).life(60 + (int) (rnd() * 30)).colors(mix(c.c2, 0x120018, 0.6f), 0x050008).vel(out).drag(0.9f).envelope(0.1f, 0.65f, 0.85f).spin((float) gauss() * 0.02f);
            }
        });
        during(4, 50, t -> {
            double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 4.2;
            sp("glow", c.pos.add(Math.cos(a) * r, 0.5 + rnd() * 2, Math.sin(a) * r)).size(0.16f, 0.03f).life(14).colors(lighten(c.c1, 0.4f), c.c1).flicker(0.5f).vel(0, 0.01, 0);
        });
        c.skin.ground(c.pos, 5f, c.c2, 90);
    }

    private static void shadowstep(Ctx c) {
        // Arrival: a ring of black light opens behind the target and the assassin steps out of it
        Vec3 p = c.live();
        Vec3 back = c.look.scale(-0.8);
        ringFlat(p.add(0, 1, 0).add(back), c.look, 0.2, 1.7, 8, c.c1, "shockwave");
        ringFlat(p.add(0, 1, 0).add(back), c.look, 0.1, 1.2, 10, WHITE, "ring");
        for (int k = 0; k < n(22); k++) {
            Vec3 d = rndDir();
            c.skin.body(p.add(0, 1, 0).add(d.scale(0.4)), d.scale(0.12), 0.9f, 22, c.c2, 1f);
        }
        for (int k = 0; k < n(14); k++) c.skin.mote(p.add(0, 1, 0), rndDir().scale(0.3), 0.16f, 14, c.c1, 0f);
        bloom(p.add(0, 1, 0), 1.3f, 8, c.c1);
        // Where the blade lands
        Vec3 f = c.focus();
        at(2, () -> {
            sp("claw", f).size(1.8f, 2.4f).life(10).colors(WHITE, c.c1).facing(-c.look.x, -c.look.y, -c.look.z).startRoll(0.7f).envelope(0.05f, 0.35f, 1f);
            bloom(f, 0.9f, 7, c.c1);
        });
        streakBetween(p.add(0, 1, 0), f, c);
    }

    private static void streakBetween(Vec3 a, Vec3 b, Ctx c) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 0.01) return;
        Vec3 d = dir.normalize();
        for (int k = 0; k < n(5); k++) {
            Vec3 off = new Vec3(gauss() * 0.2, gauss() * 0.2, gauss() * 0.2);
            sp("streak", a.lerp(b, 0.5).add(off)).size((float) (dir.length() * 0.5), 0.2f).life(7).colors(WHITE, c.c1).vel(d.scale(0.001)).axial().drag(1f).envelope(0.05f, 0.4f, 0.8f);
        }
    }

    private static void cuts(Ctx c) {
        Vec3 p = c.live();
        gather(c, p.add(0, 1, 0), 5, 8, 4, c.c1);
        // The world goes dark: a sphere of black smoke, a violet sigil, and blades cutting everywhere inside it
        at(6, () -> {
            bloom(p.add(0, 1, 0), 2.6f, 10, c.c1);
            ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 13, 16, c.c1, "shockwave");
            sp("sigil", p.add(0, 0.06, 0)).size(12.5f, 12.5f).life(60).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(-0.04f).envelope(0.1f, 0.7f, 0.9f);
            for (int k = 0; k < n(30); k++) {
                double a = rnd() * Math.PI * 2, r = 4 + rnd() * 7;
                sp("smoke", p.add(Math.cos(a) * r, 0.5 + rnd() * 5, Math.sin(a) * r)).size(2.0f, 4.5f).life(50).colors(0x1A0A26, 0x000000).envelope(0.15f, 0.7f, 0.8f).spin((float) gauss() * 0.02f);
            }
        });
        during(8, 48, t -> {
            for (int k = 0; k < n(3); k++) {
                double a = rnd() * Math.PI * 2, r = 1 + rnd() * 10;
                Vec3 at = p.add(Math.cos(a) * r, 0.8 + rnd() * 4.0, Math.sin(a) * r);
                Vec3 face = c.eye().subtract(at);
                Vec3 n = face.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : face.normalize();
                if (rnd() < 0.6) {
                    sp("claw", at).size(1.2f + (float) rnd() * 1.6f, 2.6f).life(6).colors(WHITE, c.c1).facing(n.x, n.y, n.z).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.4f, 1f);
                } else {
                    sp("slash", at).size(1.6f + (float) rnd() * 2f, 2.6f).life(6).colors(WHITE, c.c1).facing(n.x, n.y, n.z).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.4f, 1f);
                }
                sp("flare", at).size(0.1f, 1.2f).life(4).colors(WHITE, c.c1);
            }
        });
    }
}
