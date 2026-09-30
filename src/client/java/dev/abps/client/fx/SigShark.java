package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Shark: the sea. Spray, bubbles, foam, whirlpools and geysers. Deep blue and aqua. The tsunami itself is in {@link Tsunami}. */
final class SigShark {

    private SigShark() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> riptide(c);
            case 2 -> cannon(c);
            case 3 -> maelstrom(c);
            case 4 -> breach(c);
            case 5 -> tsunami(c);
            case 6 -> leviathan(c);
            default -> {
            }
        }
    }

    private static void splash(Vec3 at, double spread, double up, int count, Ctx c) {
        for (int k = 0; k < n(count); k++) {
            Vec3 d = new Vec3(gauss(), 0, gauss()).normalize().scale(spread * (0.4 + rnd() * 0.8));
            sp("droplet", at).size(0.2f, 0.12f).life(22 + (int) (rnd() * 12)).colors(WHITE, mix(c.c2, WHITE, 0.4f)).vel(d.x, up * (0.5 + rnd() * 0.8), d.z).grav(0.6f).drag(0.985f).envelope(0.03f, 0.75f, 0.95f);
        }
    }

    private static void riptide(Ctx c) {
        Vec3 p = c.live();
        Vec3 dir = c.flat();
        Vec3 back = dir.scale(-1);
        bloom(p.add(0, 1, 0), 1.6f, 8, c.c2);
        ringFlat(p.add(0, 1, 0), dir, 0.2, 2.6, 9, c.c2, "shockwave");
        ringFlat(p.add(0, 1, 0).add(dir.scale(0.8)), dir, 0.2, 1.8, 9, WHITE, "ring");
        // A cone of white water thrown out behind, with bubbles rising through it
        for (int k = 0; k < 10; k++) {
            Vec3 at = p.add(back.scale(0.5 + k * 0.9)).add(0, 0.9, 0);
            at(k / 2, () -> {
                for (int q = 0; q < 3; q++) {
                    sp("foam", at.add(gauss() * 0.5, gauss() * 0.4, gauss() * 0.5)).size(0.8f, 1.9f).life(16).colors(WHITE, mix(c.c2, WHITE, 0.5f)).vel(back.scale(0.02)).envelope(0.1f, 0.5f, 0.8f).spin((float) gauss() * 0.05f);
                }
                sp("bubble", at).size(0.25f, 0.4f).life(20).colors(WHITE, WHITE).vel(gauss() * 0.02, 0.05, gauss() * 0.02).envelope(0.1f, 0.6f, 0.9f);
            });
        }
        for (int k = 0; k < n(8); k++) {
            Vec3 off = new Vec3(gauss() * 0.4, gauss() * 0.5, gauss() * 0.4);
            sp("streak", p.add(0, 1, 0).add(off).add(back.scale(3))).size(3.2f, 0.4f).life(8).colors(WHITE, c.c2).vel(dir.scale(0.001)).axial().drag(1f).envelope(0.05f, 0.4f, 0.8f);
        }
        splash(p.add(0, 0.2, 0), 0.25, 0.3, 24, c);
    }

    private static void cannon(Ctx c) {
        Vec3 dir = c.look;
        Vec3 h = c.hand();
        double range = 30;
        Vec3[] uv = axes(dir);
        bloom(h, 1.4f, 8, c.c2);
        ringFlat(h.add(dir.scale(0.5)), dir, 0.2, 1.8, 9, WHITE, "shockwave");
        during(0, 12, t -> {
            Vec3 a = c.hand();
            Vec3 b = a.add(dir.scale(range));
            if (t % 2 == 0) {
                beamLine(a, b, 0.32f, 4, c.c1);
                beamLine(a, b, 0.14f, 3, WHITE);
            }
            // Water corkscrewing round the jet
            for (int k = 0; k < 40; k++) {
                double f = k * 0.78 + (t * 1.9 % 0.78);
                double ang = f * 1.1 - t * 1.0;
                Vec3 pos = a.add(dir.scale(f)).add(uv[0].scale(Math.cos(ang) * 0.75)).add(uv[1].scale(Math.sin(ang) * 0.75));
                if (k % 3 == 0) sp("droplet", pos).size(0.22f, 0.14f).life(4).colors(WHITE, c.c2).envelope(0.1f, 0.5f, 0.95f);
                else sp("foam", pos).size(0.35f, 0.5f).life(3).colors(WHITE, mix(c.c2, WHITE, 0.5f)).envelope(0.1f, 0.5f, 0.8f);
            }
            if (t % 3 == 0) for (int k = 1; k < 10; k++) ringFlat(a.add(dir.scale(k * 3.0 + t * 0.6)), dir, 0.3, 1.9, 6, c.c2, "ring");
            for (int k = 0; k < n(4); k++) sp("bubble", a.add(dir.scale(rnd() * range)).add(gauss() * 0.4, gauss() * 0.4, gauss() * 0.4)).size(0.2f, 0.3f).life(14).colors(WHITE, WHITE).vel(0, 0.04, 0).envelope(0.1f, 0.6f, 0.9f);
            if (t == 0) {
                Vec3 end = a.add(dir.scale(Math.min(range, a.distanceTo(c.aim) + 1)));
                bloom(end, 2.4f, 10, c.c2);
                ringFlat(end, dir.scale(-1), 0.3, 4.0, 12, WHITE, "shockwave");
                splash(end, 0.4, 0.5, 40, c);
                for (int k = 0; k < n(10); k++) sp("foam", end.add(gauss() * 0.6, gauss() * 0.6, gauss() * 0.6)).size(1.0f, 2.4f).life(16).colors(WHITE, mix(c.c2, WHITE, 0.5f)).vel(rndDir().scale(0.12)).envelope(0.1f, 0.5f, 0.8f);
            }
        });
    }

    private static void maelstrom(Ctx c) {
        Vec3 g = c.aim;
        double radius = 8;
        bloom(g.add(0, 0.5, 0), 2.0f, 9, c.c2);
        // A great turning disc of water on the ground, a dark eye in the middle, and water spiralling down into it
        sp("swirl", g.add(0, 0.12, 0)).size((float) radius, (float) radius).life(104).colors(lighten(c.c2, 0.4f), c.c1).facing(0, 1, 0).spin(-0.16f).envelope(0.08f, 0.85f, 0.85f);
        sp("swirl", g.add(0, 0.16, 0)).size((float) radius * 0.6f, (float) radius * 0.6f).life(104).colors(WHITE, c.c2).facing(0, 1, 0).spin(-0.24f).envelope(0.08f, 0.85f, 0.7f);
        sp("glow", g.add(0, 0.1, 0)).size(2.2f, 1.6f).life(104).colors(darken(c.c1, 0.5f), darken(c.c1, 0.7f)).envelope(0.1f, 0.85f, 0.6f);
        ringFlat(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, radius + 1, 14, c.c2, "shockwave");
        during(0, 100, t -> {
            for (int k = 0; k < n(6); k++) {
                double f = rnd();
                double r = radius * (1 - f * 0.9);
                double a = -(t * 0.32) + f * 5.0 + k * 1.05;
                Vec3 at = g.add(Math.cos(a) * r, 0.2 + (1 - f) * 0.25, Math.sin(a) * r);
                if (k % 2 == 0) sp("foam", at).size(0.7f, 1.1f).life(6).colors(WHITE, mix(c.c2, WHITE, 0.5f)).vel(-Math.sin(a) * 0.3, 0, Math.cos(a) * 0.3).drag(1f).envelope(0.1f, 0.5f, 0.8f).spin(0.05f);
                else sp("streak", at).size(1.3f, 0.5f).life(5).colors(WHITE, c.c2).vel(Math.sin(a) * 0.3, 0, -Math.cos(a) * 0.3).axial().drag(1f).envelope(0.1f, 0.5f, 0.85f);
            }
            if (t % 5 == 0) {
                double a = rnd() * Math.PI * 2, r = rnd() * radius * 0.7;
                sp("droplet", g.add(Math.cos(a) * r, 0.3, Math.sin(a) * r)).size(0.2f, 0.12f).life(20).colors(WHITE, c.c2).vel(gauss() * 0.04, 0.25 + rnd() * 0.15, gauss() * 0.04).grav(0.6f).envelope(0.03f, 0.75f, 0.95f);
            }
            if (t % 12 == 0) ringFlat(g.add(0, 0.14, 0), new Vec3(0, 1, 0), radius, 1.0, 14, c.c2, "ring");
        });
    }

    private static void breach(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 0.6, 0), 3.0f, 12, c.c2);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 11, 16, c.c1, "shockwave");
        ringFlat(p.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.4, 8, 13, WHITE, "shockwave");
        c.skin.ground(p, 6f, c.c2, 60);
        // A geyser and a crown of water where the shark comes down
        during(0, 16, t -> c.skin.column(p, 1.6, 9 * ease((t + 1) / 6.0), c.c2, t));
        for (int k = 0; k < n(50); k++) {
            double a = rnd() * Math.PI * 2, sp = 0.12 + rnd() * 0.3;
            sp("droplet", p.add(Math.cos(a) * 1.2, 0.3, Math.sin(a) * 1.2)).size(0.24f, 0.14f).life(26 + (int) (rnd() * 12)).colors(WHITE, mix(c.c2, WHITE, 0.4f))
                    .vel(Math.cos(a) * sp, 0.3 + rnd() * 0.5, Math.sin(a) * sp).grav(0.6f).drag(0.985f).envelope(0.03f, 0.75f, 0.95f);
        }
        for (int k = 0; k < n(20); k++) {
            double a = rnd() * Math.PI * 2, r = 1 + rnd() * 4;
            sp("foam", p.add(Math.cos(a) * r, 0.4, Math.sin(a) * r)).size(1.2f, 3.0f).life(20).colors(WHITE, mix(c.c2, WHITE, 0.5f)).vel(Math.cos(a) * 0.1, 0.06, Math.sin(a) * 0.1).drag(0.95f).envelope(0.1f, 0.5f, 0.85f);
        }
        splash(p, 0.5, 0.4, 30, c);
    }

    private static void tsunami(Ctx c) {
        Vec3 p = c.live();
        Vec3 dir = c.flat();
        // The sea is called: a spout of water rising round the caster and ripples racing out the way the wave will go
        bloom(p.add(0, 1, 0), 2.2f, 10, c.c2);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 9, 16, c.c1, "shockwave");
        sp("swirl", p.add(0, 0.14, 0)).size(4.5f, 4.5f).life(40).colors(lighten(c.c2, 0.4f), c.c1).facing(0, 1, 0).spin(0.18f).envelope(0.1f, 0.7f, 0.85f);
        during(0, 22, t -> c.skin.column(p, 1.3, 5 * ease((t + 1) / 8.0), c.c2, t));
        for (int k = 0; k < 6; k++) {
            int delay = k * 2;
            at(delay, () -> ringFlat(c.live().add(dir.scale(3.0 + delay * 0.8)).add(0, 0.1, 0), new Vec3(0, 1, 0), 1.0, 5.0, 12, c.c2, "ring"));
        }
        // A pressure wave leaving the hands
        for (int k = 0; k < 3; k++) at(k * 3, () -> ringFlat(c.eye().add(c.look.scale(1.0)), c.look, 0.3, 6.0, 12, WHITE, "ring"));
        splash(p.add(0, 0.3, 0), 0.3, 0.4, 24, c);
    }

    private static void leviathan(Ctx c) {
        Vec3 p = c.live();
        double reach = 22;
        bloom(p.add(0, 1, 0), 4.0f, 14, c.c2);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 26, 22, c.c1, "shockwave");
        // The sea drags inward: a colossal slow whirl, and rings that collapse onto the middle again and again
        sp("swirl", p.add(0, 0.14, 0)).size((float) reach, (float) reach).life(60).colors(lighten(c.c2, 0.3f), c.c1).facing(0, 1, 0).spin(-0.1f).envelope(0.1f, 0.85f, 0.85f);
        sp("swirl", p.add(0, 0.18, 0)).size((float) reach * 0.6f, (float) reach * 0.6f).life(60).colors(WHITE, c.c2).facing(0, 1, 0).spin(-0.16f).envelope(0.1f, 0.85f, 0.65f);
        for (int k = 0; k < 5; k++) {
            int delay = k * 8;
            at(delay, () -> {
                ringFlat(c.live().add(0, 0.16, 0), new Vec3(0, 1, 0), reach, 2.0, 16, c.c2, "shockwave");
                ringFlat(c.live().add(0, 0.2, 0), new Vec3(0, 1, 0), reach * 0.7, 1.5, 14, WHITE, "ring");
            });
        }
        during(0, 40, t -> {
            Vec3 b = c.live();
            for (int k = 0; k < n(8); k++) {
                double a = rnd() * Math.PI * 2, r = 6 + rnd() * (reach - 6);
                Vec3 at = b.add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
                Vec3 in = b.subtract(at).normalize();
                sp("foam", at).size(0.9f, 1.4f).life(16).colors(WHITE, mix(c.c2, WHITE, 0.5f)).vel(in.x * 0.6 - in.z * 0.25, 0.01, in.z * 0.6 + in.x * 0.25).drag(1f).envelope(0.1f, 0.5f, 0.8f);
                sp("streak", at.add(0, 0.3, 0)).size(2.0f, 0.5f).life(12).colors(WHITE, c.c2).vel(in.x * 0.6, 0, in.z * 0.6).axial().drag(1f).envelope(0.1f, 0.5f, 0.85f);
            }
            if (t % 4 == 0) {
                double a = rnd() * Math.PI * 2, r = 4 + rnd() * 12;
                c.skin.column(b.add(Math.cos(a) * r, 0, Math.sin(a) * r), 0.8, 4 + rnd() * 3, c.c2, t);
            }
        });
        // The jaws close: everything erupts
        at(40, () -> {
            Vec3 b = c.live();
            bloom(b.add(0, 1, 0), 6.0f, 16, c.c2);
            ringFlat(b.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 20, 20, WHITE, "shockwave");
            during(0, 20, t -> c.skin.column(b, 3.0, 16 * ease((t + 1) / 6.0), c.c2, t));
            splash(b, 0.8, 0.8, 70, c);
        });
    }
}
