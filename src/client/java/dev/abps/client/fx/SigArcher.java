package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Archer: arrows made of light, target reticles, ropes and a piercing beam. Green and teal. */
final class SigArcher {

    private SigArcher() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> volley(c);
            case 2 -> mark(c);
            case 3 -> grapple(c);
            case 4 -> storm(c);
            case 6 -> skyPiercer(c);
            default -> {
            }
        }
    }

    /** An arrow of light that leaves a fading streak behind it. */
    private static void arrow(Vec3 from, Vec3 dir, double speed, int life, int col, float size) {
        sp("arrow", from).size(size, size).life(life).colors(WHITE, col).vel(dir.scale(speed)).drag(1f).axial().envelope(0.02f, 0.85f, 1f)
                .motion((p, age) -> sp("streak", p.px(), p.py(), p.pz()).size(size * 1.7f, size * 0.2f).life(7).colors(col, darken(col, 0.5f))
                        .vel(dir.scale(0.001)).axial().drag(1f).envelope(0.05f, 0.3f, 0.7f));
    }

    private static void volley(Ctx c) {
        Vec3 hand = c.hand();
        bloom(hand, 0.8f, 7, c.c1);
        ringFlat(hand.add(c.look.scale(0.4)), c.look, 0.2, 1.4, 9, c.c2, "ring");
        ringFlat(hand.add(c.look.scale(0.9)), c.look, 0.1, 1.0, 8, WHITE, "ring");
        int arrows = 9;
        for (int k = 0; k < arrows; k++) {
            double yaw = (k - (arrows - 1) / 2.0) * 0.1;
            Vec3 dir = rotY(c.look, yaw).add(0, (k % 2 == 0 ? 0.02 : -0.02), 0).normalize();
            arrow(hand.add(dir.scale(0.5)), dir, 1.7, 15, k % 3 == 0 ? c.c2 : c.c1, 0.9f);
        }
        for (int k = 0; k < n(12); k++) c.skin.mote(hand, c.look.scale(0.25).add(rndDir().scale(0.12)), 0.12f, 12, c.c1, 0f);
    }

    private static void mark(Ctx c) {
        Vec3 f = c.focus();
        Vec3 toward = c.eye().subtract(f);
        toward = toward.lengthSqr() < 1.0e-4 ? c.look.scale(-1) : toward.normalize();
        Vec3 n = toward;
        Vec3[] uv = axes(n);
        beamLine(c.hand(), f, 0.03f, 4, c.c2);
        during(0, 12, t -> {
            double r = 3.4 - 2.4 * ease(t / 11.0);
            ringFlat(f, n, r, r * 0.95, 2, c.c1, "ring");
            ringFlat(f, n, r * 0.55, r * 0.5, 2, c.c2, "ring");
            for (int k = 0; k < 4; k++) {
                double a = t * 0.32 + k * Math.PI / 2;
                Vec3 pos = f.add(uv[0].scale(Math.cos(a) * r)).add(uv[1].scale(Math.sin(a) * r));
                Vec3 inward = f.subtract(pos).normalize();
                sp("streak", pos).size(0.7f, 0.3f).life(3).colors(WHITE, c.c1).vel(inward.scale(0.001)).axial().drag(1f);
            }
        });
        at(12, () -> {
            bloom(f, 1.5f, 10, c.c1);
            ringFlat(f, n, 0.4, 3.0, 10, WHITE, "shockwave");
            sp("rays", f).size(0.6f, 2.6f).life(10).colors(WHITE, c.c1).startRoll((float) rnd() * 3f).spin(0.05f).envelope(0.05f, 0.3f, 0.9f);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + Math.PI / 4;
                Vec3 d = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
                sp("streak", f.add(d.scale(0.6))).size(1.5f, 0.3f).life(9).colors(WHITE, c.c2).vel(d.scale(0.18)).axial().drag(0.9f);
            }
        });
        // A small arrow spinning over the marked target's head while the mark lasts
        Entity tgt = c.target();
        if (tgt != null) {
            FxSystem.add(new FxSystem.Emitter() {
                int age;

                @Override
                public boolean tick(net.minecraft.client.multiplayer.ClientLevel level) {
                    if (tgt.isRemoved()) return false;
                    Vec3 p = tgt.position().add(0, tgt.getBbHeight() + 0.7 + Math.sin(age * 0.25) * 0.1, 0);
                    sp("arrow", p).size(0.45f, 0.45f).life(3).colors(WHITE, c.c1).axis(0, -1, 0).envelope(0.1f, 0.5f, 0.9f);
                    if (age % 2 == 0) sp("ring", p.add(0, -0.3, 0)).size(0.4f, 0.4f).life(3).colors(c.c1, c.c2).facing(0, 1, 0).envelope(0.1f, 0.5f, 0.8f);
                    age++;
                    return age < 240;
                }
            });
        }
    }

    private static void grapple(Ctx c) {
        Vec3 hand = c.hand();
        Vec3 tip = c.aim;
        double len = tip.distanceTo(hand);
        Vec3 dir = tip.subtract(hand).normalize();
        bloom(hand, 0.6f, 6, c.c1);
        int flight = Math.max(2, (int) Math.ceil(len / 2.4));
        during(0, flight, t -> {
            Vec3 head = hand.add(dir.scale(Math.min(len, (t + 1) * 2.4)));
            sp("arrow", head).size(0.8f, 0.8f).life(3).colors(WHITE, c.c1).vel(dir.scale(0.001)).axial().drag(1f);
            sp("glow", head).size(0.5f, 0.15f).life(4).colors(WHITE, c.c2);
        });
        // The rope: a chain of beads with a sag that pulls tight
        during(flight, 22, t -> {
            double slack = Math.max(0, 1.0 - t / 12.0) * Math.min(3.0, len * 0.08);
            Vec3 h = c.hand();
            int beads = (int) Math.min(40, Math.max(6, len * 1.2));
            for (int k = 0; k <= beads; k++) {
                double f = k / (double) beads;
                Vec3 pos = h.lerp(tip, f).add(0, -Math.sin(f * Math.PI) * slack, 0);
                sp("glow", pos).size(0.13f, 0.08f).life(2).colors(WHITE, c.c1);
                if (k % 4 == 0) sp("spark", pos).size(0.22f, 0.05f).life(3).colors(WHITE, c.c2).spin(0.2f);
            }
            if (t > 3) c.skin.along(c.chest(), dir, 0.35f, c.c1);
        });
        at(flight, () -> {
            bloom(tip, 1.1f, 9, c.c1);
            ringFlat(tip, dir.scale(-1), 0.2, 1.8, 9, c.c2, "shockwave");
            for (int k = 0; k < n(14); k++) {
                Vec3 d = rndDir().scale(0.2 + rnd() * 0.15);
                sp("debris", tip).size(0.1f, 0.06f).life(18).colors(0xA09070, 0x706040).vel(d.x, Math.abs(d.y) + 0.1, d.z).grav(0.6f).spin(0.2f).bright().envelope(0.02f, 0.7f, 1f);
            }
        });
    }

    private static void storm(Ctx c) {
        Vec3 g = c.aim;
        // The circle on the ground
        sp("sigil", g.add(0, 0.05, 0)).size(5.2f, 5.2f).life(90).colors(lighten(c.c1, 0.3f), c.c1).facing(0, 1, 0).spin(0.03f).envelope(0.1f, 0.75f, 0.9f);
        ringFlat(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 6.5, 12, c.c2, "shockwave");
        gather(c, c.hand(), 8, 8, 3, c.c1);
        // A shaft of light where the arrows will come from
        during(3, 10, t -> {
            double h = 26 * ease((t + 1) / 8.0);
            for (double y = 0; y < h; y += 1.4) sp("glow", g.add(0, y, 0)).size(0.7f, 0.3f).life(3).colors(WHITE, c.c1).envelope(0.1f, 0.4f, 0.55f);
        });
        during(6, 80, t -> {
            for (int k = 0; k < n(4); k++) {
                double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 5.0;
                Vec3 from = g.add(Math.cos(a) * r, 22 + rnd() * 6, Math.sin(a) * r);
                arrow(from, new Vec3(0.05, -1, 0.02).normalize(), 1.35, 20, k % 2 == 0 ? c.c1 : c.c2, 0.85f);
            }
            if (t % 5 == 0) ringFlat(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 4.5, 4.9, 6, c.c1, "ring");
            if (t % 2 == 0) {
                double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 5.0;
                bloom(g.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 0.5f, 5, c.c1);
            }
        });
    }

    private static void skyPiercer(Ctx c) {
        // One second of gathering into a glowing orb at the hand
        gather(c, c.hand(), 7, 20, 4, c.c1);
        during(0, 21, t -> {
            Vec3 h = c.hand();
            float s = 0.2f + t * 0.05f;
            sp("glow", h).size(s * 2f, s * 1.3f).life(2).colors(lighten(c.c1, 0.5f), c.c1).envelope(0.1f, 0.5f, 0.9f);
            sp("glow", h).size(s, s * 0.7f).life(2).colors(WHITE, WHITE);
            sp("rays", h).size(s * 1.6f, s * 2f).life(2).colors(WHITE, c.c1).startRoll(t * 0.2f).envelope(0.1f, 0.5f, 0.8f);
        });
        Vec3 dir = c.look;
        at(21, () -> {
            Vec3 h = c.hand();
            Vec3 end = h.add(dir.scale(60));
            bloom(h, 2.4f, 12, c.c1);
            ringFlat(h.add(dir.scale(0.5)), dir, 0.3, 4.0, 12, WHITE, "shockwave");
            during(0, 16, t -> {
                Vec3 a = c.hand();
                Vec3 b = a.add(dir.scale(60));
                if (t % 2 == 0) {
                    beamLine(a, b, 0.42f * (1f - t / 24f), 4, c.c1);
                    beamLine(a, b, 0.16f, 3, WHITE);
                }
                Vec3[] uv = axes(dir);
                for (int k = 0; k < 48; k++) {
                    double f = k * 1.25 + (t * 1.7 % 1.25);
                    double ang = f * 0.75 - t * 0.9;
                    Vec3 pos = a.add(dir.scale(f)).add(uv[0].scale(Math.cos(ang) * 0.85)).add(uv[1].scale(Math.sin(ang) * 0.85));
                    sp("streak", pos).size(0.9f, 0.2f).life(3).colors(WHITE, c.c2).vel(dir.scale(0.001)).axial().drag(1f).envelope(0.1f, 0.4f, 0.8f);
                }
                if (t % 3 == 0) {
                    for (int k = 1; k < 10; k++) ringFlat(a.add(dir.scale(k * 6.0 + t)), dir, 0.3, 2.2, 6, c.c1, "ring");
                }
                if (t == 0) {
                    bloom(b, 3.0f, 12, c.c1);
                    sp("rays", b).size(1f, 6f).life(12).colors(WHITE, c.c1).spin(0.03f).envelope(0.05f, 0.3f, 0.9f);
                }
            });
        });
    }
}
