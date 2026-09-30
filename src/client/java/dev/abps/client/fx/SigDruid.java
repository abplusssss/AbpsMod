package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Druid: growth. Leaves, petals, pollen, thorns, roots and a great tree. Green. */
final class SigDruid {

    private SigDruid() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> rejuvenate(c);
            case 2 -> brambles(c);
            case 3 -> wolves(c);
            case 4 -> wrath(c);
            case 6 -> worldTree(c);
            default -> {
            }
        }
    }

    private static void rejuvenate(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 1.6f, 9, c.c1);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 10.0, 18, c.c1, "shockwave");
        c.skin.ring(p.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.4, 9.0, 18, c.c1);
        c.skin.ground(p, 4.5f, c.c1, 40);
        // Life rising off the ground: petals and glowing pollen drifting up around the caster, and hearts
        during(0, 30, t -> {
            Vec3 b = c.live();
            for (int k = 0; k < n(4); k++) {
                double a = rnd() * Math.PI * 2, r = 0.4 + rnd() * 3.0;
                c.skin.mote(b.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), new Vec3(0, 0.05 + rnd() * 0.05, 0), 0.14f, 26, c.c1, 0f);
            }
            if (t % 5 == 0) {
                double a = rnd() * Math.PI * 2, r = 0.6 + rnd() * 1.2;
                sp("heart", b.add(Math.cos(a) * r, 0.8, Math.sin(a) * r)).size(0.28f, 0.2f).life(26).colors(0xFFFFFF, 0xFF8AB0).vel(0, 0.05, 0).envelope(0.15f, 0.5f, 0.9f);
            }
        });
    }

    private static void brambles(Ctx c) {
        Vec3 p = c.live();
        double radius = 7;
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, radius + 1, 14, c.c1, "shockwave");
        sp("sigil", p.add(0, 0.05, 0)).size((float) radius, (float) radius).life(100).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(0.02f).envelope(0.1f, 0.8f, 0.8f);
        // Thorns pushing up out of the ground, outer ring first
        for (int k = 0; k < n(70); k++) {
            double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * radius;
            Vec3 at = p.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
            Vec3 lean = new Vec3(Math.cos(a) * 0.25 + gauss() * 0.1, 1.0, Math.sin(a) * 0.25 + gauss() * 0.1);
            float len = 0.7f + (float) rnd() * 1.1f;
            int delay = (int) (r / radius * 8);
            at(delay, () -> sp("thorn", at.add(lean.normalize().scale(len * 0.5))).size(0.05f, len).life(70 + (int) (rnd() * 30))
                    .colors(mix(c.c2, 0x5A4020, 0.5f), mix(c.c2, 0x3A2A10, 0.5f)).axis(lean.x, lean.y, lean.z).envelope(0.1f, 0.85f, 1f).bright());
        }
        during(0, 50, t -> {
            for (int k = 0; k < n(3); k++) {
                double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * radius;
                c.skin.mote(p.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), new Vec3(0, 0.03, 0), 0.14f, 24, c.c1, 0f);
            }
            if (t % 4 == 0) c.skin.body(p.add(gauss() * radius * 0.5, 0.2, gauss() * radius * 0.5), new Vec3(0, 0.02, 0), 1.6f, 30, c.c1, 0.6f);
        });
    }

    private static void wolves(Ctx c) {
        Vec3 p = c.live();
        sp("sigil", p.add(0, 0.05, 0)).size(4.5f, 4.5f).life(50).colors(0xFFFFFF, c.c1).facing(0, 1, 0).spin(0.05f).envelope(0.1f, 0.7f, 0.9f);
        // Spirit lights appear in a circle, one for each pack member, and rise as pale columns
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + 0.3;
            Vec3 at = p.add(Math.cos(a) * 3.0, 0, Math.sin(a) * 3.0);
            int delay = k * 2;
            at(delay, () -> {
                bloom(at.add(0, 0.8, 0), 1.0f, 8, c.c1);
                ringFlat(at.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.2, 1.8, 10, WHITE, "ring");
                during(0, 10, t -> c.skin.column(at, 0.5, 2.6 * ease((t + 1) / 5.0), c.c1, t));
                for (int q = 0; q < n(10); q++) c.skin.mote(at.add(0, 0.5, 0), rndUp().scale(0.14), 0.16f, 20, c.c1, 0.1f);
            });
        }
        // The howl: rings of sound rolling out from the caster
        for (int k = 0; k < 3; k++) at(6 + k * 4, () -> ringFlat(c.eye().add(c.look.scale(0.8)), c.look, 0.3, 8.0, 14, c.c1, "ring"));
        during(0, 24, t -> {
            for (int k = 0; k < n(2); k++) c.skin.mote(p.add(gauss() * 3, 0.2, gauss() * 3), new Vec3(0, 0.09, 0), 0.14f, 24, c.c1, 0f);
        });
    }

    private static void wrath(Ctx c) {
        Vec3 p = c.live();
        double radius = 8;
        bloom(p.add(0, 0.6, 0), 2.4f, 10, c.c1);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, radius + 2, 16, c.c1, "shockwave");
        c.skin.ground(p, 6f, c.c1, 60);
        add("crack", p, radius);
        // Roots and thorns erupt in waves, racing outward from the caster
        for (int wave = 0; wave < 4; wave++) {
            double r = 2 + wave * 2;
            int count = n(10 + wave * 6);
            int delay = wave * 3;
            for (int k = 0; k < count; k++) {
                double a = Math.PI * 2 * k / count + wave;
                Vec3 at = p.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                Vec3 lean = new Vec3(-Math.cos(a) * 0.35, 1.0, -Math.sin(a) * 0.35);
                float len = 1.6f + (float) rnd() * 1.6f;
                at(delay, () -> {
                    sp("thorn", at.add(lean.normalize().scale(len * 0.5))).size(0.05f, len).life(40).colors(mix(c.c2, 0x5A4020, 0.5f), mix(c.c2, 0x3A2A10, 0.5f)).axis(lean.x, lean.y, lean.z).envelope(0.1f, 0.8f, 1f).bright();
                    for (int q = 0; q < 3; q++) c.skin.mote(at.add(0, 0.2, 0), rndUp().scale(0.12), 0.16f, 18, c.c1, 0.3f);
                });
            }
        }
        during(0, 20, t -> {
            for (int k = 0; k < n(6); k++) {
                double a = rnd() * Math.PI * 2, r = rnd() * radius;
                c.skin.mote(p.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), new Vec3(gauss() * 0.03, 0.2 + rnd() * 0.15, gauss() * 0.03), 0.18f, 24, c.c1, 0.1f);
            }
        });
    }

    private static void add(String sprite, Vec3 p, double radius) {
        sp(sprite, p.add(0, 0.04, 0)).size((float) radius, (float) radius).life(90).colors(WHITE, WHITE).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.75f, 1f);
    }

    private static final int BARK = 0x2A1B10, LEAF = 0x7CFF4A, LEAF_DEEP = 0x2E7D32, BLOSSOM = 0xFFB3D1;

    /**
     * The World Tree, drawn with ribbons. A trunk of bark strands braids up out of the ground, branches arc out from
     * the top, roots sweep across the ground, and a ring of light marks where it heals. It lasts 10 seconds.
     */
    private static void worldTree(Ctx c) {
        Vec3 p = c.live();
        double radius = 8, trunkH = 5.5;
        int life = 200;
        Vec3 up = new Vec3(0, 1, 0);
        bloom(p.add(0, 0.6, 0), 1.6f, 10, LEAF);

        // Trunk: five strands of bark that start wide at the roots and braid tight as they rise
        for (int k = 0; k < 5; k++) {
            double start = Math.PI * 2 * k / 5;
            Ribbon.along(Ribbon.spiral(p, 1.2, 0.35, trunkH, 0.55, start, 0.25)).ink(BARK, LEAF).width(0.62f)
                    .time(life, 16).hold(0.9f).tailChase(0f).sparks(0).segments(30).play();
        }
        // A soft green light inside the trunk
        for (Vec3 side : new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)}) {
            Ribbon.along(Ribbon.line(p, p.add(0, trunkH + 0.5, 0), side)).energy(LEAF_DEEP).width(0.5f)
                    .time(life, 16).hold(0.9f).tailChase(0f).brightness(0.35f).sparks(0).segments(16).play();
        }

        // Branches: arcs curling up and out from the top of the trunk, drawn once the trunk is up
        Signatures.at(12, () -> {
            for (int k = 0; k < 7; k++) {
                double a = Math.PI * 2 * k / 7 + rnd() * 0.4;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 center = p.add(0, trunkH - 0.6, 0).add(out.scale(2.0));
                Vec3 normal = out.cross(up).normalize();
                // The arc starts at the trunk (pointing back at it) and curls up and over
                Ribbon.along(Ribbon.arc(center, out.scale(-1).add(0, 0.5, 0), normal, 2.1, 2.2, 0.15, 1.0)).ink(BARK, LEAF)
                        .width(0.3f).time(life - 12, 10).hold(0.9f).tailChase(0f).sparks(0).segments(22).play();
            }
            // Canopy: brush strokes of foliage swirling round the crown at different heights and tilts,
            // with one soft ring of leaf light through the middle
            for (int k = 0; k < 11; k++) {
                double h = trunkH + 0.4 + (k % 3) * 0.8 + rnd() * 0.3;
                double r = 3.4 - (k % 3) * 0.8;
                Vec3 tiltAxis = new Vec3(Math.cos(k * 1.7), 0, Math.sin(k * 1.7));
                Vec3 normal = FxKit.rotAbout(up, tiltAxis, (rnd() - 0.5) * 0.7);
                Vec3 mid = new Vec3(Math.cos(k * 2.4), 0, Math.sin(k * 2.4));
                Ribbon.along(Ribbon.arc(p.add(0, h, 0), mid, normal, r, 2.6, (k % 2 == 0 ? 1 : -1) * 0.5, 1.0))
                        .ink(k % 4 == 3 ? 0x5C2340 : LEAF_DEEP, k % 4 == 3 ? BLOSSOM : LEAF).width(0.95f).time(life - 12 - k, 10).hold(0.92f)
                        .tailChase(0f).sparks(0).play();
            }
            Ribbon.along(Ribbon.arc(p.add(0, trunkH + 1.2, 0), new Vec3(1, 0, 0), up, 2.6, Math.PI * 2, 1.2, 1.0)).energy(LEAF)
                    .width(0.45f).time(life - 12, 12).hold(0.9f).tailChase(0f).brightness(0.45f).sparks(0).play();
        });

        // Roots: dark strokes sweeping out across the ground
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6 + rnd() * 0.4;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 from = p.add(out.scale(0.6)).add(0, 0.06, 0), to = p.add(out.scale(2.4 + rnd() * 1.6)).add(0, 0.06, 0);
            Ribbon.along(Ribbon.line(from, to, out.cross(up))).ink(BARK, LEAF).width(0.26f).time(life, 14).hold(0.9f)
                    .tailChase(0f).sparks(0).segments(18).play();
        }

        // The healing area: a ring of light on the ground that pulses every two seconds
        during(0, life, t -> {
            if (t % 40 == 0) {
                Ribbon.along(Ribbon.arc(p.add(0, 0.1, 0), new Vec3(0, 0, 1), up, radius, Math.PI * 2, 0.5, 1.0)).energy(LEAF)
                        .width(0.35f).time(40, 10).hold(0.5f).tailChase(0f).brightness(0.8f).sparks(0).segments(64).play();
                Ribbon.along(Ribbon.arc(p.add(0, 0.12, 0), new Vec3(0, 0, 1), up, 0.8, Math.PI * 2, 1.2, radius / 0.8)).energy(LEAF_DEEP)
                        .width(0.4f).time(22, 2).hold(0.1f).tailChase(0f).brightness(0.7f).sparks(0).segments(48).play();
            }
            // A few leaves and blossoms drifting down from the crown, not a blizzard
            if (t > 14 && t % 3 == 0) {
                double a = rnd() * Math.PI * 2, r = 0.5 + rnd() * 3.5;
                c.skin.mote(p.add(Math.cos(a) * r, trunkH + 1 + rnd() * 1.5, Math.sin(a) * r), new Vec3(gauss() * 0.01, -0.03, gauss() * 0.01),
                        0.16f, 50, rnd() < 0.3 ? BLOSSOM : LEAF, 0.02f);
            }
            if (t % 10 == 0) {
                double a = rnd() * Math.PI * 2, r = rnd() * radius;
                sp("glow", p.add(Math.cos(a) * r, 0.5 + rnd() * 3, Math.sin(a) * r)).size(0.12f, 0.03f).life(24).colors(0xFFFFA0, LEAF)
                        .flicker(0.5f).vel(0, 0.015, 0);
            }
        });
    }
}
