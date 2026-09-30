package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Miner: stone, dust, gems and gold. Amber and orange. */
final class SigMiner {

    private SigMiner() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> excavate(c);
            case 2 -> oreSense(c);
            case 3 -> bore(c);
            case 4 -> goldRush(c);
            case 6 -> earthshatter(c);
            default -> {
            }
        }
    }

    /** The twelve edges of a box, drawn as lines of light. */
    private static void wireBox(Vec3 center, double half, int life, int col) {
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Vec3 v = center.add(x * half, y * half, z * half);
                    if (x < 0) beamLine(v, center.add(half, y * half, z * half), 0.04f, life, col);
                    if (y < 0) beamLine(v, center.add(x * half, half, z * half), 0.04f, life, col);
                    if (z < 0) beamLine(v, center.add(x * half, y * half, half), 0.04f, life, col);
                }
            }
        }
    }

    private static void excavate(Ctx c) {
        Vec3 f = c.aim;
        Vec3 hand = c.hand();
        bloom(hand, 0.6f, 6, c.c1);
        // A frame of light closes on the block, then the rock bursts apart
        during(0, 6, t -> wireBox(f, 1.5 + (5 - t) * 0.25, 2, t % 2 == 0 ? c.c1 : c.c2));
        at(6, () -> {
            bloom(f, 2.4f, 9, c.c1);
            sp("rays", f).size(0.6f, 3.2f).life(9).colors(WHITE, c.c1).spin(0.05f).envelope(0.05f, 0.3f, 0.9f);
            ringFlat(f, new Vec3(0, 1, 0), 0.4, 4.0, 10, c.c1, "shockwave");
            for (int k = 0; k < n(30); k++) c.skin.mote(f.add(rndDir().scale(1.2)), rndUp().scale(0.16 + rnd() * 0.22), 0.2f, 24, c.c1, 0.6f);
            for (int k = 0; k < n(10); k++) c.skin.body(f.add(rndDir().scale(1.3)), rndDir().scale(0.05), 1.5f, 24, c.c1, 0.7f);
        });
    }

    private static void oreSense(Ctx c) {
        Vec3 p = c.live().add(0, 1, 0);
        double reach = 24;
        bloom(p, 1.4f, 8, c.c1);
        // A pulse of gold rolling out through the rock in every direction, and a radar arm sweeping round
        for (int k = 0; k < 3; k++) {
            int delay = k * 3;
            at(delay, () -> {
                ringFlat(p, new Vec3(0, 1, 0), 0.5, reach, 22, c.c1, "shockwave");
                ringFlat(p, new Vec3(1, 0, 0), 0.5, reach, 22, c.c2, "ring");
                ringFlat(p, new Vec3(0, 0, 1), 0.5, reach, 22, c.c2, "ring");
            });
        }
        during(0, 24, t -> {
            double a = t * 0.4;
            Vec3 tip = c.live().add(Math.cos(a) * 12, 1, Math.sin(a) * 12);
            beamLine(c.live().add(0, 1, 0), tip, 0.05f, 3, c.c1);
            sp("flare", tip).size(0.1f, 1.2f).life(3).colors(WHITE, c.c1);
            for (int k = 0; k < n(6); k++) {
                double b = rnd() * Math.PI * 2, r = 3 + rnd() * 18;
                sp("spark", c.live().add(Math.cos(b) * r, 1 + gauss() * 3, Math.sin(b) * r)).size(0.35f, 0.05f).life(12).colors(WHITE, c.c1).spin(0.15f).envelope(0.05f, 0.5f, 1f);
            }
        });
    }

    private static void bore(Ctx c) {
        Vec3 dir = c.look;
        Vec3 start = c.eye();
        double depth = Math.max(6, Math.min(26, start.distanceTo(c.aim)));
        int steps = (int) Math.ceil(depth / 2.0);
        Vec3[] uv = axes(dir);
        bloom(c.hand(), 0.7f, 6, c.c1);
        // A drill head chewing forward: turning rings of light, sparks and rubble thrown back down the shaft
        during(0, steps, t -> {
            Vec3 head = start.add(dir.scale(Math.min(depth, (t + 1) * 2.0 + 1.0)));
            for (int k = 0; k < 3; k++) {
                double a = t * 0.9 + k * 2.09;
                Vec3 pos = head.add(uv[0].scale(Math.cos(a) * 1.1)).add(uv[1].scale(Math.sin(a) * 1.1));
                sp("shard", pos).size(0.35f, 0.2f).life(4).colors(WHITE, c.c1).spin(0.3f).startRoll((float) a);
            }
            ringFlat(head, dir, 0.4, 1.9, 5, c.c1, "ring");
            bloom(head, 0.9f, 5, c.c2);
            for (int k = 0; k < n(6); k++) c.skin.mote(head, dir.scale(-0.2 - rnd() * 0.2).add(gauss() * 0.08, gauss() * 0.08, gauss() * 0.08), 0.18f, 18, c.c1, 0.5f);
            c.skin.body(head.subtract(dir.scale(1.5)), dir.scale(-0.05), 1.3f, 24, c.c1, 0.7f);
        });
    }

    private static void goldRush(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 2.0f, 9, c.c1);
        sp("rays", p.add(0, 1.2, 0)).size(0.8f, 3.6f).life(12).colors(WHITE, c.c1).spin(0.04f).envelope(0.05f, 0.4f, 0.85f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 5.0, 14, c.c1, "shockwave");
        // Gold rains upward off the caster: sparkles, tumbling nuggets and slow warm light
        during(0, 34, t -> {
            Vec3 b = c.live();
            for (int k = 0; k < n(4); k++) {
                double a = rnd() * Math.PI * 2, r = rnd() * 1.6;
                Vec3 at = b.add(Math.cos(a) * r, 0.1 + rnd() * 0.5, Math.sin(a) * r);
                if (rnd() < 0.5) {
                    sp("shard", at).size(0.22f, 0.1f).life(26).colors(lighten(c.c1, 0.6f), c.c2).vel(0, 0.11 + rnd() * 0.06, 0).spin((float) gauss() * 0.3f).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.6f, 1f);
                } else {
                    sp("spark", at).size(0.3f, 0.04f).life(24).colors(WHITE, c.c1).vel(0, 0.09 + rnd() * 0.07, 0).spin(0.12f).envelope(0.05f, 0.5f, 1f);
                }
            }
            if (t % 6 == 0) ringFlat(b.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.3, 2.4, 10, c.c2, "ring");
        });
    }

    private static void earthshatter(Ctx c) {
        Vec3 dir = c.flat();
        Vec3 p = c.live();
        bloom(p.add(0, 0.5, 0), 2.4f, 9, c.c1);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 6.0, 12, c.c1, "shockwave");
        // A fault line tears across the ground and a stone spike erupts every step along it
        for (int k = 0; k < 8; k++) {
            double d = 2.0 + k * 1.75;
            Vec3 at = p.add(dir.scale(d));
            int delay = k * 2;
            at(delay, () -> {
                c.skin.ground(at, 2.2f, c.c1, 50);
                float len = 2.2f + (float) rnd() * 1.8f;
                for (int q = 0; q < 3; q++) {
                    Vec3 base = at.add(c.right().scale((q - 1) * 0.7)).add(dir.scale(gauss() * 0.4));
                    Vec3 lean = new Vec3(gauss() * 0.15, 1.0, gauss() * 0.15);
                    sp("shard", base.add(lean.normalize().scale(len * 0.5))).size(0.1f, len * 0.5f).life(46).colors(0xB8A488, 0x807058).axis(lean.x, lean.y, lean.z).upright()
                            .envelope(0.05f, 0.8f, 1f).bright();
                }
                bloom(at.add(0, 0.5, 0), 1.2f, 6, c.c1);
                for (int q = 0; q < n(16); q++) c.skin.mote(at.add(0, 0.2, 0), rndUp().scale(0.14 + rnd() * 0.2), 0.2f, 22, c.c1, 0.7f);
                for (int q = 0; q < n(3); q++) c.skin.body(at.add(gauss() * 0.6, 0.3, gauss() * 0.6), new Vec3(0, 0.07, 0), 1.6f, 26, c.c1, 0.8f);
                ringFlat(at.add(0, 0.12, 0), new Vec3(0, 1, 0), 0.3, 3.0, 8, c.c2, "shockwave");
            });
        }
        // Molten gold showing through the cracks
        for (int k = 0; k < 6; k++) {
            int delay = k * 2;
            Vec3 at = p.add(dir.scale(2 + k * 2.4));
            at(delay, () -> sp("crackglow", at.add(0, 0.06, 0)).size(3.0f, 3.1f).life(50).colors(WHITE, c.c2).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).flicker(0.3f).envelope(0.05f, 0.5f, 0.9f));
        }
    }
}
