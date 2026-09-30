package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Tank: armor and force. Shield rings, sparks, cracked stone and heavy shockwaves. Steel and blue. */
final class SigTank {

    private SigTank() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> fortify(c);
            case 2 -> challenge(c);
            case 3 -> bash(c);
            case 4 -> unbreakable(c);
            case 6 -> colossus(c);
            default -> {
            }
        }
    }

    /** Three rings tumbling round a point like a gyroscope, drawn each tick. */
    private static void gyro(Vec3 center, double r, double t, int c1, int c2) {
        for (int k = 0; k < 3; k++) {
            double a = t * (0.16 + k * 0.05) + k * 1.05;
            Vec3 n = new Vec3(Math.cos(a) * (k == 0 ? 1 : 0.3), Math.sin(a * 0.7 + k), Math.sin(a) * (k == 2 ? 1 : 0.5));
            if (n.lengthSqr() < 1.0e-3) n = new Vec3(0, 1, 0);
            ringFlat(center, n.normalize(), r + k * 0.12, r + k * 0.12, 2, k % 2 == 0 ? c1 : c2, "ring");
        }
    }

    private static void fortify(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 1.8f, 9, c.c2);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 5.0, 12, c.c2, "shockwave");
        c.skin.ring(p.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.3, 3.5, 12, c.c1);
        // Plates of light snap into place round the body and turn slowly
        during(0, 36, t -> {
            Vec3 b = c.live().add(0, 1.0, 0);
            gyro(b, 1.55 + Math.max(0, (8 - t) * 0.15), t, c.c2, c.c1);
            for (int k = 0; k < 6; k++) {
                double a = t * 0.12 + k * Math.PI / 3;
                Vec3 pos = b.add(Math.cos(a) * 1.3, Math.sin(a * 2 + k) * 0.7, Math.sin(a) * 1.3);
                sp("shard", pos).size(0.28f, 0.2f).life(3).colors(WHITE, c.c2).spin(0.2f).startRoll((float) a).envelope(0.1f, 0.5f, 0.95f);
            }
            if (t % 3 == 0) c.skin.mote(b.add(gauss() * 0.7, gauss() * 0.8, gauss() * 0.7), rndDir().scale(0.05), 0.1f, 12, c.c2, 0f);
        });
    }

    private static void challenge(Ctx c) {
        Vec3 p = c.live();
        double reach = 8;
        bloom(p.add(0, 1, 0), 2.0f, 9, c.c1);
        sp("rays", p.add(0, 1.2, 0)).size(0.8f, 3.4f).life(12).colors(WHITE, c.c2).spin(0.06f).envelope(0.05f, 0.3f, 0.85f);
        // Rings closing in on the caster: come here
        for (int k = 0; k < 4; k++) {
            int delay = k * 3;
            at(delay, () -> {
                ringFlat(c.live().add(0, 0.14, 0), new Vec3(0, 1, 0), reach + 2, 1.0, 12, c.c2, "shockwave");
                ringFlat(c.live().add(0, 0.18, 0), new Vec3(0, 1, 0), reach, 0.8, 10, WHITE, "ring");
            });
        }
        during(0, 14, t -> {
            Vec3 b = c.live().add(0, 1.0, 0);
            for (int k = 0; k < n(6); k++) {
                double a = rnd() * Math.PI * 2, r = 5 + rnd() * 4;
                Vec3 from = c.live().add(Math.cos(a) * r, 0.4 + rnd() * 1.6, Math.sin(a) * r);
                Vec3 in = b.subtract(from).normalize();
                sp("streak", from).size(2.2f, 0.5f).life(8).colors(WHITE, c.c2).vel(in.scale(0.7)).axial().drag(1f).envelope(0.05f, 0.5f, 0.95f);
            }
        });
        c.skin.ground(p, 4f, c.c2, 50);
    }

    private static void bash(Ctx c) {
        Vec3 p = c.live();
        Vec3 dir = c.flat();
        Vec3 f = c.focus();
        bloom(p.add(0, 1, 0).add(dir.scale(0.8)), 1.6f, 8, c.c2);
        // The charge: a spray of sparks and a slab of light travelling in front
        for (int k = 0; k < 8; k++) {
            int delay = k / 2;
            Vec3 at = p.add(dir.scale(k * 1.2)).add(0, 1, 0);
            at(delay, () -> {
                ringFlat(at, dir, 0.3, 1.6, 6, c.c2, "ring");
                sp("shockwave", at).size(1.1f, 1.4f).life(5).colors(WHITE, c.c2).facing(dir.x, dir.y, dir.z).envelope(0.1f, 0.4f, 0.95f);
                for (int q = 0; q < n(5); q++) c.skin.mote(at.add(0, -0.6, 0), dir.scale(-0.2 - rnd() * 0.2).add(gauss() * 0.08, rnd() * 0.15, gauss() * 0.08), 0.14f, 14, c.c2, 0.4f);
            });
        }
        // The blow
        at(5, () -> {
            bloom(f, 2.8f, 10, c.c2);
            sp("rays", f).size(1.0f, 4.5f).life(10).colors(WHITE, c.c2).spin(0.05f).envelope(0.05f, 0.3f, 0.9f);
            ringFlat(f, dir, 0.3, 5.0, 12, WHITE, "shockwave");
            ringFlat(f.add(0, -f.y + p.y + 0.1, 0), new Vec3(0, 1, 0), 0.3, 5.0, 12, c.c2, "shockwave");
            for (int k = 0; k < n(36); k++) c.skin.mote(f, rndDir().scale(0.2 + rnd() * 0.35), 0.18f, 20, c.c2, 0.4f);
            c.skin.ground(new Vec3(f.x, p.y, f.z), 3.5f, c.c2, 50);
        });
    }

    private static void unbreakable(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 2.4f, 10, c.c1);
        sp("rays", p.add(0, 1.2, 0)).size(1.0f, 4.0f).life(14).colors(WHITE, c.c2).spin(0.04f).envelope(0.05f, 0.4f, 0.85f);
        // Armor forming: rings shrink onto the body from all around and settle into a turning cage
        for (int k = 0; k < 5; k++) {
            int delay = k * 2;
            at(delay, () -> {
                Vec3 b = c.live().add(0, 1.0, 0);
                ringFlat(b, new Vec3(0, 1, 0), 3.2, 1.3, 10, c.c2, "shockwave");
                ringFlat(b, new Vec3(1, 0, 0), 3.0, 1.3, 10, c.c1, "ring");
                ringFlat(b, new Vec3(0, 0, 1), 2.8, 1.3, 10, c.c1, "ring");
            });
        }
        during(6, 40, t -> {
            Vec3 b = c.live().add(0, 1.0, 0);
            gyro(b, 1.6, t + 6, c.c2, c.c1);
            for (int k = 0; k < 4; k++) {
                double a = -t * 0.14 + k * Math.PI / 2;
                sp("shard", b.add(Math.cos(a) * 1.4, Math.sin(a * 3) * 0.8, Math.sin(a) * 1.4)).size(0.3f, 0.2f).life(3).colors(WHITE, c.c1).spin(0.25f).startRoll((float) a).envelope(0.1f, 0.5f, 0.95f);
            }
            if (t % 4 == 0) c.skin.mote(b.add(gauss() * 0.8, gauss(), gauss() * 0.8), rndDir().scale(0.04), 0.1f, 14, c.c1, 0f);
        });
        c.skin.ground(p, 3f, c.c2, 50);
    }

    private static void colossus(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 4.0f, 14, c.c2);
        sp("rays", p.add(0, 1.5, 0)).size(1.5f, 7f).life(16).colors(WHITE, c.c2).spin(0.04f).envelope(0.05f, 0.3f, 0.9f);
        // Growth: rings of light climbing the body from the feet to far above the head
        for (int k = 0; k < 14; k++) {
            int idx = k;
            at(k, () -> {
                Vec3 b = c.live();
                ringFlat(b.add(0, idx * 0.55, 0), new Vec3(0, 1, 0), 1.2 + idx * 0.05, 2.2 + idx * 0.08, 8, idx % 2 == 0 ? c.c2 : c.c1, "shockwave");
            });
        }
        during(0, 30, t -> c.skin.column(p, 1.5, 12 * ease((t + 1) / 10.0), c.c2, t));
        // Three quakes rolling out through the ground
        for (int k = 0; k < 3; k++) {
            int delay = 6 + k * 5;
            at(delay, () -> {
                Vec3 b = c.live();
                ringFlat(b.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.6, 14, 18, c.c2, "shockwave");
                ringFlat(b.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.4, 10, 15, WHITE, "ring");
                c.skin.ground(b, 6f + delay * 0.15f, c.c2, 70);
                for (int q = 0; q < n(24); q++) {
                    double a = rnd() * Math.PI * 2, sp = 0.15 + rnd() * 0.3;
                    c.skin.mote(b.add(Math.cos(a), 0.2, Math.sin(a)), new Vec3(Math.cos(a) * sp, 0.3 + rnd() * 0.35, Math.sin(a) * sp), 0.22f, 26, c.c2, 0.6f);
                }
                for (int q = 0; q < n(8); q++) {
                    double a = rnd() * Math.PI * 2, r = 2 + rnd() * 6;
                    c.skin.body(b.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), new Vec3(Math.cos(a) * 0.06, 0.06, Math.sin(a) * 0.06), 1.8f, 26, c.c2, 0.7f);
                }
            });
        }
    }
}
