package dev.abps.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;

/**
 * The basic effect pieces the server can ask for (bursts, rings, beams, pillars and so on). Each one is drawn through
 * the {@link Skin} of the class that caused it, so a burst of fire, a burst of water and a burst of souls have
 * nothing in common but the timing. Sizes are half-widths in blocks, lifetimes are in ticks.
 */
final class FxEffects {

    private FxEffects() {
    }

    // ------------------------------------------------------------------ ribbon helpers

    /** A ribbon in the skin's style. */
    private static Ribbon.Builder stroke(Skin s, Ribbon.Path p, int col) {
        return s.stroke(Ribbon.along(p), col);
    }

    /** Any direction lying flat in the plane with this normal. */
    private static Vec3 inPlane(Vec3 normal) {
        return axes(normal)[0];
    }

    // ------------------------------------------------------------------ one-off effects

    static void shard(double[] d, int[] i, Skin s) {
        float size = (float) d[6];
        s.mote(new Vec3(d[0], d[1], d[2]), new Vec3(d[3], d[4], d[5]), size, i[0], i[1], (float) (d[7] / 0.04));
    }

    static void burst(double[] d, int[] i, Skin s) {
        int col = i[2], life = i[1];
        float size = (float) d[4];
        Vec3 at = new Vec3(d[0], d[1], d[2]);
        // A handful of pieces and one clean ring, instead of a cloud
        int count = n(Math.min(i[0], 10));
        for (int k = 0; k < count; k++) {
            Vec3 dir = rndUp();
            double sp = d[3] * (0.6 + rnd() * 0.9) * 1.5;
            s.mote(at, dir.scale(sp), size, (int) (life * (0.8 + rnd() * 0.6)), col, 0.5f);
        }
        s.flash(at, size * 4f + 0.3f, 6, col);
        double r = size * 7 + 0.7;
        Vec3 normal = rndDir();
        stroke(s, Ribbon.arc(at, inPlane(normal), normal, 0.25, Math.PI * 2, 1.4, r / 0.25), col)
                .width((float) Math.max(0.12, r * 0.18)).time(10, 2).hold(0.1f).tailChase(0f).sparks(0).segments(24).play();
    }

    static void ring(double[] d, int[] i, Skin s) {
        Vec3 c = vec(d, 0), normal = vec(d, 3).normalize();
        double r0 = Math.max(0.2, d[6]), r1 = Math.max(0.2, d[7]);
        int life = Math.max(8, i[1]), col = i[2];
        float w = (float) Math.max(0.2, Math.min(0.9, d[8] * 3 + r1 * 0.08));
        Vec3 mid = inPlane(normal);
        if (Math.abs(r1 - r0) < 0.01 && r0 >= 2.5 && d[8] >= 0.11 && normal.y > 0.9) {
            // A big ring that stays where it is marks an area on the ground: it draws itself round, then holds
            s.ground(c, (float) r0, col, life + 6);
            stroke(s, Ribbon.arc(c.add(0, 0.08, 0), mid, normal, r0, Math.PI * 2, 0.4, 1), col)
                    .width(w * 0.8f).time(life + 6, 8).hold(0.75f).tailChase(0f).sparks(0).segments(48).play();
        } else {
            // A shockwave: one stroke of light that spins out to the full size
            int segs = (int) Math.min(64, 24 + r1 * 4);
            stroke(s, Ribbon.arc(c, mid, normal, r0, Math.PI * 2, 0.9, r1 / r0), col)
                    .width(w).time(life, 2).hold(0.15f).tailChase(0f).brightness(1.2f).sparks(0).segments(segs).play();
        }
    }

    static void beam(double[] d, int[] i, Skin s) {
        line(vec(d, 0), vec(d, 3), (float) d[6], i[0], i[1], s, true);
    }

    /** A straight stroke of light. Drawn as two crossed ribbons so it never vanishes when seen edge-on. */
    private static void line(Vec3 a, Vec3 b, float thick, int life, int col, Skin s, boolean ends) {
        int c = s.tone(col);
        Vec3 dv = b.subtract(a);
        double len = dv.length();
        if (len < 0.05) return;
        Vec3 dir = dv.scale(1 / len);
        float w = Math.max(0.12f, thick * 2.4f);
        int segs = (int) Math.max(8, Math.min(60, len * 3));
        for (Vec3 side : axes(dir)) {
            stroke(s, Ribbon.line(a, b, side), c).width(w).time(Math.max(6, life + 3), 2).hold(0.3f).tailChase(0.85f)
                    .brightness(1.3f).sparks(0).segments(segs).play();
        }
        if (ends) {
            s.flash(a, w * 0.9f + 0.1f, 5, c);
            s.flash(b, w * 1.3f + 0.15f, 6, c);
        }
    }

    static void zigzag(double[] d, int[] i, Skin s) {
        Vec3 a = vec(d, 0), b = vec(d, 3);
        int pieces = i[0];
        double jitter = d[6];
        Vec3 prev = a;
        int c = s.tone(i[2]);
        for (int k = 1; k <= pieces; k++) {
            Vec3 next = a.lerp(b, (double) k / pieces);
            if (k < pieces) next = next.add((rnd() - 0.5) * jitter, (rnd() - 0.5) * jitter, (rnd() - 0.5) * jitter);
            Vec3 dir = next.subtract(prev);
            if (dir.lengthSqr() > 1.0e-4) {
                for (Vec3 side : axes(dir.normalize())) {
                    Ribbon.along(Ribbon.line(prev, next, side)).energy(c).width(Math.max(0.07f, (float) d[7] * 1.6f))
                            .time(Math.max(5, i[1]), 1).hold(0.4f).tailChase(0f).brightness(1.6f).sparks(0).segments(10).play();
                }
            }
            prev = next;
        }
        s.flash(a, (float) d[7] * 3f + 0.3f, 5, i[2]);
        s.flash(b, (float) d[7] * 4f + 0.5f, 7, i[2]);
    }

    static void flash(double[] d, int[] i, Skin s) {
        Vec3 at = vec(d, 0);
        float size = (float) d[3];
        s.flash(at, size * 1.1f + 0.25f, Math.max(6, i[0]), i[1]);
    }

    static void sphere(double[] d, int[] i, Skin s) {
        Vec3 c = vec(d, 0);
        double r0 = Math.max(0.2, d[3]), r1 = Math.max(0.2, d[4]);
        float size = (float) d[5];
        int life = Math.max(8, i[1]), col = s.tone(i[2]);
        // Three great circles on different tilts, spinning as they grow: reads as a sphere without filling it in
        for (int k = 0; k < 3; k++) {
            Vec3 normal = rndDir();
            stroke(s, Ribbon.arc(c, inPlane(normal), normal, r0, Math.PI * 2, 1.2 + k * 0.4, r1 / r0), col)
                    .width((float) Math.max(0.12, size * 2 + r1 * 0.06)).time(life, 3).hold(0.2f).tailChase(0f).sparks(0).segments(36).play();
        }
        sp("glow", c).size((float) r0 + 0.2f, (float) r1 * 0.6f + 0.2f).life(life).colors(lighten(col, 0.2f), col).envelope(0.1f, 0.3f, 0.25f);
    }

    static void particles(double[] d, int[] i, Skin s) {
        double x = d[0], y = d[1], z = d[2];
        int count = n(Math.min(Math.max(1, i[0]), 8));
        int col = i[1], style = i[2];
        float size = (float) d[7];
        double speed = Math.max(0.015, d[6] * 0.7);
        for (int k = 0; k < count; k++) {
            Vec3 at = new Vec3(x + gauss() * d[3] * 0.6, y + gauss() * d[4] * 0.6, z + gauss() * d[5] * 0.6);
            Vec3 dir = new Vec3(gauss(), gauss() * 0.6 + 0.3, gauss()).normalize().scale(speed * (0.5 + rnd()));
            if (style == 1) {
                s.body(at, dir.scale(0.5).add(0, 0.012, 0), 0.5f * size, 22, col, 0.7f);
            } else if (style == 2) {
                sp("debris", at).size(0.13f * size, 0.06f).life(16 + (int) (rnd() * 8)).colors(mix(col, 0x808080, 0.5f), mix(col, 0x404040, 0.5f))
                        .vel(dir.x, dir.y + 0.04, dir.z).grav(0.55f).drag(0.95f).spin((float) (gauss() * 0.2)).startRoll((float) (rnd() * 6.28)).envelope(0.02f, 0.7f, 1f).bright();
            } else {
                s.mote(at, dir, 0.12f * size, 12 + (int) (rnd() * 8), col, 0.1f);
            }
        }
    }

    // ------------------------------------------------------------------ effects that play out over time

    static void pillar(double[] d, int[] i, Skin s) {
        Vec3 base = vec(d, 0);
        double radius = Math.max(0.2, d[3]), height = d[4];
        int grow = Math.max(1, i[0]), hold = i[1], shrink = Math.max(1, i[2]), col = i[3];
        int total = grow + hold + shrink;
        float holdShare = (float) hold / Math.max(1, hold + shrink);
        Vec3 up = new Vec3(0, 1, 0);
        // A ring where it rises, a mark on the ground, then strands of light winding up around the column
        stroke(s, Ribbon.arc(base.add(0, 0.05, 0), new Vec3(1, 0, 0), up, 0.3, Math.PI * 2, 0.8, (radius * 3 + 1) / 0.3), col)
                .width((float) Math.max(0.25, radius * 0.5)).time(12, 2).hold(0.1f).tailChase(0f).sparks(0).segments(32).play();
        s.flash(base.add(0, 0.3, 0), (float) radius * 2 + 0.5f, 8, col);
        if (radius >= 0.8) s.ground(base, (float) (radius * 2.2), col, total + 6);
        int strands = radius >= 1 ? 4 : 3;
        for (int k = 0; k < strands; k++) {
            double start = Math.PI * 2 * k / strands;
            stroke(s, Ribbon.spiral(base, radius * 1.05, radius * 0.75, height, Math.max(0.8, height / 5), start, 3.0), col)
                    .width((float) Math.max(0.16, radius * 0.35)).time(total, grow).hold(holdShare).tailChase(0.2f).sparks(0)
                    .segments((int) Math.min(60, 20 + height * 2)).play();
        }
        // A thin bright core up the middle
        for (Vec3 side : new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)}) {
            Ribbon.along(Ribbon.line(base, base.add(0, height, 0), side)).energy(lighten(s.tone(col), 0.5f)).width((float) Math.max(0.1, radius * 0.4))
                    .time(total, grow).hold(holdShare).tailChase(0f).brightness(0.5f).sparks(0).segments(20).play();
        }
        Signatures.at(grow, () -> s.flash(base.add(0, height, 0), (float) radius * 2f + 0.4f, 8, col));
    }

    static void jaws(double[] d, int[] i, Skin s) {
        Vec3 c = vec(d, 0);
        double radius = d[3], height = d[4];
        int teeth = i[0], col = i[1];
        double[] hs = new double[teeth];
        for (int k = 0; k < teeth; k++) hs[k] = 0.75 + rnd() * 0.5;
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                if (age % 2 == 0) {
                    double tilt = age < 8 ? -0.35 : age < 12 ? -0.35 + 1.1 * (age - 8) / 4.0 : 0.75;
                    double grow = age < 4 ? (age + 1) / 4.0 : age < 24 ? 1.0 : 1.0 - (age - 24) / 10.0;
                    int samples = n(4);
                    for (int k = 0; k < teeth; k++) {
                        double a = Math.PI * 2 * k / teeth;
                        Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                        Vec3 base = c.add(radial.scale(radius));
                        Vec3 tip = new Vec3(0, Math.cos(tilt), 0).add(radial.scale(-Math.sin(tilt)));
                        double h = height * hs[k] * grow;
                        for (int q = 0; q <= samples; q++) {
                            double f = q / (double) Math.max(1, samples);
                            Vec3 pos = base.add(tip.scale(h * f));
                            float sz = (float) (0.30 * (1 - 0.7 * f) + 0.06);
                            sp("glow", pos).size(sz * 1.5f, sz * 0.6f).life(4).colors(lighten(col, 0.5f), col);
                            if (q == samples) sp("spark", pos).size(0.3f, 0.05f).life(6).colors(WHITE, col);
                        }
                        if (rnd() < 0.5) s.mote(base.add(tip.scale(h * rnd())), new Vec3(radial.x * 0.05, 0.08, radial.z * 0.05), 0.16f, 14, col, 0.5f);
                    }
                }
                if (age == 12) {
                    s.ring(c.add(0, 0.1, 0), new Vec3(0, 1, 0), radius * 0.4, radius * 1.4, 12, col);
                    s.flash(c.add(0, 0.5, 0), (float) radius * 0.7f + 0.5f, 10, col);
                    for (int k = 0; k < n(24); k++) {
                        double a = rnd() * 6.28;
                        s.mote(c.add(Math.cos(a) * radius * 0.5, 0.4, Math.sin(a) * radius * 0.5), new Vec3(Math.cos(a) * 0.3, 0.3 + rnd() * 0.3, Math.sin(a) * 0.3), 0.22f, 22, col, 0.5f);
                    }
                }
                age++;
                return age < 34;
            }
        });
    }

    static void slash(double[] d, int[] i, Skin s, int theme) {
        Vec3 c = vec(d, 0);
        double baseAngle = Math.atan2(d[5], d[3]);
        double radius = d[6], arc = d[7];
        float thick = (float) d[8];
        int col = s.tone(i[0]);
        Vec3 mid = new Vec3(Math.cos(baseAngle), 0, Math.sin(baseAngle));
        boolean claws = theme == Skin.ASSASSIN || theme == Skin.BERSERKER || theme == Skin.VAMPIRE;
        // A flat crescent at eye level is edge-on to the player and disappears, so the swing plane leans back
        // toward the caster (pitch) and rolls to one side (a diagonal cut), a little different every swing
        Vec3 side = new Vec3(-mid.z, 0, mid.x);
        double pitch = 0.75 + rnd() * 0.25;
        double roll = (rnd() < 0.5 ? -1 : 1) * (0.35 + rnd() * 0.35);
        Vec3 normal = FxKit.rotAbout(FxKit.rotAbout(new Vec3(0, 1, 0), side, -pitch), mid, roll);
        Vec3 center = c.add(0, 1.1, 0);
        float w = (float) Math.max(0.34, Math.max(radius * 0.3, thick * 2.6 + 0.2));
        if (theme == Skin.ASSASSIN) {
            // Ink crescent with a white rim, like a brush stroke, over a soft white halo
            Ribbon.along(Ribbon.arc(center, mid, normal, radius * 1.02, arc, 0.6, 1.08)).energy(0xE8E8F0)
                    .width(w * 1.35f).time(18, 3).hold(0.3f).brightness(0.55f).sparks(0).segments(28).play();
            Ribbon.along(Ribbon.arc(center, mid, normal, radius, arc, 0.6, 1.08)).ink(0x0C0A10, 0xFFFFFF)
                    .width(w * 1.1f).time(18, 3).hold(0.3f).sparks(0).play();
        } else {
            Ribbon.along(Ribbon.arc(center, mid, normal, radius, arc, 0.55, 1.1)).energy(col)
                    .width(w).time(17, 3).hold(0.3f).brightness(1.7f).sparks(1).play();
            // A fainter echo just inside it, a beat behind, for depth
            Ribbon.along(Ribbon.arc(center, mid, normal, radius * 0.8, arc * 0.85, 0.45, 1.05)).energy(FxKit.darken(col, 0.25f))
                    .width(w * 0.55f).time(14, 4).hold(0.2f).brightness(0.6f).sparks(0).segments(24).play();
        }
        if (claws) {
            // Three short rakes across the front, each a thin stroke
            for (int k = -1; k <= 1; k++) {
                Vec3 base = c.add(mid.scale(radius * 0.85)).add(side.scale(k * 0.28)).add(0, 1.0, 0);
                Vec3 a = base.add(side.scale(0.55)).add(0, 0.55, 0), b = base.add(side.scale(-0.55)).add(0, -0.45, 0);
                Ribbon.Builder r = Ribbon.along(Ribbon.line(a, b, mid.scale(-1))).width(0.13f).time(12, 2).hold(0.2f).tailChase(0.7f).sparks(0).segments(14);
                if (theme == Skin.ASSASSIN) r.ink(0x0C0A10, 0xFFFFFF); else r.energy(col);
                r.play();
            }
        }
    }

    static void orbit(double[] d, int[] i, Skin s) {
        double radius = d[0], height = d[1];
        float size = (float) d[2];
        double turns = d[3];
        int entityId = i[0], count = i[1], ticks = i[2], col = i[3];
        // Short arcs of light circling the entity, following it as it moves
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                Entity e = level.getEntity(entityId);
                if (e == null || e.isRemoved()) return false;
                if (age % 10 == 0) {
                    int arcs = Math.max(1, Math.min(count, 3));
                    for (int k = 0; k < arcs; k++) {
                        double offset = Math.PI * 2 * k / arcs + age * 0.2;
                        double spin = Math.signum(turns) * Math.PI * 1.2;
                        Ribbon.Path p = (t, time, out) -> {
                            Vec3 c = e.position().add(0, height, 0);
                            double a = offset - 0.9 + t * 1.8 + spin * time;
                            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                            out[0] = c.add(radial.scale(radius)).add(0, Math.sin(a * 2) * 0.15, 0);
                            out[1] = new Vec3(-Math.sin(a), 0, Math.cos(a));
                            out[2] = radial;
                        };
                        stroke(s, p, col).width(Math.max(0.12f, size * 1.4f)).time(14, 3).hold(0.3f).tailChase(0.4f).sparks(0).segments(16).play();
                    }
                }
                age++;
                return age < ticks;
            }
        });
    }

    static void vortex(double[] d, int[] i, Skin s) {
        Vec3 c = vec(d, 0);
        double radius = d[3];
        float size = (float) d[4];
        double turns = d[5];
        int ticks = i[1], col = i[2];
        // Strands of light winding up and inward, renewed every half second
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                if (age % 10 == 0) {
                    for (int k = 0; k < 3; k++) {
                        double start = Math.PI * 2 * k / 3 + age * 0.3;
                        stroke(s, Ribbon.spiral(c.add(0, 0.1, 0), radius, radius * 0.25, 3.0, 1.1, start, Math.signum(turns) * 4.0), col)
                                .width(Math.max(0.14f, size * 1.6f)).time(18, 6).hold(0.3f).tailChase(0.5f).sparks(0).segments(28).play();
                    }
                }
                if (age % 12 == 0) s.body(c.add(gauss() * radius * 0.4, 0.3, gauss() * radius * 0.4), new Vec3(0, 0.03, 0), (float) radius * 0.3f, 14, col, 0.4f);
                age++;
                return age < ticks;
            }
        });
    }

    static void fins(double[] d, int[] i, Skin s) {
        Vec3 c = vec(d, 0);
        double radius = d[3], turns = d[4];
        int count = i[0], ticks = i[1], col = i[2];
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                double spin = age / 20.0 * turns * Math.PI * 2;
                for (int k = 0; k < count; k++) {
                    double a = spin + Math.PI * 2 * k / count;
                    Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
                    Vec3 p = c.add(radial.scale(radius));
                    // The fin itself, leaning back, and the V-shaped wake behind it
                    for (int q = 0; q < 5; q++) {
                        double f = q / 4.0;
                        Vec3 fp = p.add(0, f * 1.1, 0).subtract(tangent.scale(f * 0.45));
                        float sz = (float) (0.34 * (1 - f * 0.65) + 0.05);
                        sp("glow", fp).size(sz * 1.5f, sz * 0.7f).life(3).colors(lighten(col, 0.35f), col);
                        sp("foam", fp).size(sz * 1.2f, sz * 1.2f).life(3).colors(0xFFFFFF, lighten(col, 0.6f)).envelope(0.1f, 0.5f, 0.7f);
                    }
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 w = p.subtract(tangent.scale(0.7)).add(radial.scale(side * 0.35));
                        sp("foam", w.x, c.y + 0.1, w.z).size(0.5f, 1.0f).life(8).colors(0xFFFFFF, lighten(col, 0.5f)).envelope(0.05f, 0.4f, 0.7f);
                    }
                    if (age % 2 == 0) s.mote(p.add(0, 1.0, 0), new Vec3(gauss() * 0.02, 0.06, gauss() * 0.02), 0.14f, 12, col, 0.5f);
                }
                age++;
                return age < ticks;
            }
        });
    }

    static void helix(double[] d, int[] i, Skin s) {
        Vec3 base = vec(d, 0);
        double radius = d[3], height = d[4], turns = d[5];
        float size = (float) d[6];
        int life = Math.max(12, i[1]), col = i[2];
        stroke(s, Ribbon.spiral(base, radius, radius, height, turns, rnd() * Math.PI * 2, 1.0), col)
                .width(Math.max(0.12f, size * 1.6f)).time(life, 8).hold(0.3f).tailChase(0.4f).sparks(1).segments((int) Math.min(60, 16 + turns * 12)).play();
    }

    static void trail(double[] d, int[] i, Skin s) {
        float size = (float) d[0];
        int entityId = i[0], ticks = i[1], col = i[2];
        // A ribbon streaming behind whatever is moving, one short piece a tick
        FxSystem.add(new FxSystem.Emitter() {
            int age;
            Vec3 last;

            @Override
            public boolean tick(ClientLevel level) {
                Entity e = level.getEntity(entityId);
                if (e == null || e.isRemoved()) return false;
                Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
                if (last != null && p.distanceToSqr(last) > 0.01) {
                    Vec3 dir = p.subtract(last).normalize();
                    stroke(s, Ribbon.line(last, p, axes(dir)[0]), col).width(size * 2.2f + 0.08f).time(9, 1).hold(0.2f).tailChase(0f)
                            .sparks(0).segments(8).play();
                }
                last = p;
                age++;
                return age < ticks;
            }
        });
    }
}
