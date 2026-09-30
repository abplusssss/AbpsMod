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

    // ------------------------------------------------------------------ one-off effects

    static void shard(double[] d, int[] i, Skin s) {
        float size = (float) d[6];
        s.mote(new Vec3(d[0], d[1], d[2]), new Vec3(d[3], d[4], d[5]), size, i[0], i[1], (float) (d[7] / 0.04));
    }

    static void burst(double[] d, int[] i, Skin s) {
        int col = i[2], life = i[1];
        float size = (float) d[4];
        Vec3 at = new Vec3(d[0], d[1], d[2]);
        int count = n(Math.min(i[0], 24));
        for (int k = 0; k < count; k++) {
            Vec3 dir = rndUp();
            double sp = d[3] * (0.6 + rnd() * 0.9) * 1.5;
            s.mote(at, dir.scale(sp), size, (int) (life * (0.8 + rnd() * 0.6)), col, 0.5f);
        }
        s.flash(at, size * 6f + 0.4f, 7, col);
        for (int k = 0; k < 2; k++) s.body(at, rndDir().scale(0.02), size * 4f + 0.3f, life, col, 0.6f);
    }

    static void ring(double[] d, int[] i, Skin s) {
        Vec3 c = vec(d, 0), normal = vec(d, 3);
        double r0 = d[6], r1 = d[7];
        int life = Math.max(6, i[1]), col = i[2];
        if (Math.abs(r1 - r0) < 0.01 && r0 >= 2.5 && d[8] >= 0.11 && normal.y > 0.9) {
            // A big ring that stays where it is is a marking on the ground, not a shockwave
            s.ground(c, (float) r0, col, life + 6);
            s.ring(c, normal, r0, r1, life, col);
        } else {
            s.ring(c, normal, r0, r1, life, col);
            if (d[8] >= 0.10) ringFlat(c, normal, r0 * 0.9, r1 * 0.82, Math.max(5, life - 2), WHITE, "ring");
        }
    }

    static void beam(double[] d, int[] i, Skin s) {
        line(vec(d, 0), vec(d, 3), (float) d[6], i[0], i[1], s, true);
    }

    /** A straight line of light with the skin's decorations along it and a flash at each end. */
    private static void line(Vec3 a, Vec3 b, float thick, int life, int col, Skin s, boolean ends) {
        int c = s.tone(col);
        beamLine(a, b, thick, life, c);
        Vec3 dv = b.subtract(a);
        double len = dv.length();
        if (len < 0.05) return;
        Vec3 dir = dv.scale(1 / len);
        float sz = Math.max(0.14f, thick * 1.7f);
        int extras = Math.max(1, n((int) Math.min(9, len / 1.3)));
        for (int k = 0; k < extras; k++) s.along(a.add(dv.scale(rnd())), dir, sz, c);
        if (ends) {
            s.flash(a, sz * 2.2f + 0.2f, 6, c);
            s.flash(b, sz * 2.8f + 0.3f, 7, c);
        }
    }

    static void zigzag(double[] d, int[] i, Skin s) {
        Vec3 a = vec(d, 0), b = vec(d, 3);
        int pieces = i[0];
        double jitter = d[6];
        Vec3 prev = a;
        for (int k = 1; k <= pieces; k++) {
            Vec3 next = a.lerp(b, (double) k / pieces);
            if (k < pieces) next = next.add((rnd() - 0.5) * jitter, (rnd() - 0.5) * jitter, (rnd() - 0.5) * jitter);
            line(prev, next, (float) d[7], i[1], i[2], s, false);
            if (k < pieces) sp("flare", next).size(0.08f, (float) d[7] * 3f + 0.25f).life(5).colors(WHITE, s.tone(i[2]));
            prev = next;
        }
        s.flash(a, (float) d[7] * 3f + 0.4f, 6, i[2]);
        s.flash(b, (float) d[7] * 4.5f + 0.6f, 8, i[2]);
    }

    static void flash(double[] d, int[] i, Skin s) {
        Vec3 at = vec(d, 0);
        float size = (float) d[3];
        s.flash(at, size * 1.2f + 0.3f, Math.max(6, i[0]), i[1]);
    }

    static void sphere(double[] d, int[] i, Skin s) {
        Vec3 c = vec(d, 0);
        double r0 = d[3], r1 = d[4];
        float size = (float) d[5];
        int life = Math.max(4, i[1]), col = s.tone(i[2]);
        int count = n(Math.min(i[0], 60));
        double golden = Math.PI * (3 - Math.sqrt(5));
        double speed = (r1 - r0) / life;
        for (int k = 0; k < count; k++) {
            double y = 1 - (k + 0.5) * 2.0 / count;
            double rr = Math.sqrt(Math.max(0, 1 - y * y));
            double a = golden * k;
            Vec3 dir = new Vec3(Math.cos(a) * rr, y, Math.sin(a) * rr);
            s.mote(c.add(dir.scale(r0)), dir.scale(speed), size, life, col, 0f);
        }
        sp("glow", c).size((float) r0 + 0.3f, (float) r1 * 0.8f + 0.3f).life(life).colors(lighten(col, 0.2f), col).envelope(0.1f, 0.3f, 0.35f);
        for (int k = 0; k < n(3); k++) s.body(c.add(rndDir().scale(r0)), rndDir().scale(speed * 0.6), (float) (r1 * 0.35), life, col, 0.4f);
    }

    static void particles(double[] d, int[] i, Skin s) {
        double x = d[0], y = d[1], z = d[2];
        int count = n(Math.min(Math.max(1, i[0]), 10));
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
        double radius = d[3], height = d[4];
        int grow = Math.max(1, i[0]), hold = i[1], shrink = Math.max(1, i[2]), col = i[3];
        int total = grow + hold + shrink;
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                double h = height * (age < grow ? ease(age / (double) grow) : 1.0);
                double rad = radius * (age >= grow + hold ? 1.0 - (age - grow - hold) / (double) shrink : 1.0);
                if (age == 0) {
                    s.ring(base.add(0, 0.05, 0), new Vec3(0, 1, 0), 0.3, radius * 3 + 1, 12, col);
                    s.flash(base.add(0, 0.3, 0), (float) radius * 3 + 0.6f, 9, col);
                    if (radius >= 0.8) s.ground(base, (float) (radius * 2.2), col, total + 6);
                }
                s.column(base, Math.max(0.05, rad), h, col, age);
                if (age == grow) s.flash(base.add(0, h, 0), (float) radius * 2.5f + 0.5f, 8, col);
                age++;
                return age < total;
            }
        });
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
        boolean claws = theme == Skin.ASSASSIN || theme == Skin.BERSERKER || theme == Skin.VAMPIRE;
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                for (int q = 0; q < 3; q++) {
                    double prog = (age + q / 3.0) / 10.0;
                    double a = baseAngle - arc / 2 + arc * prog;
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 pos = c.add(dir.scale(radius));
                    float sz = thick * 3.2f + 0.28f;
                    sp("glow", pos).size(sz, sz * 0.4f).life(6).colors(WHITE, col).envelope(0.05f, 0.15f, 1f);
                    sp("glow", pos).size(sz * 1.6f, sz * 0.5f).life(7).colors(lighten(col, 0.2f), col).envelope(0.05f, 0.2f, 0.6f);
                    Vec3 tangent = new Vec3(-dir.z, 0, dir.x);
                    if (q == 0) s.slashPiece(pos, tangent, sz * 0.7f, col);
                }
                if (age == 2) {
                    Vec3 mid = new Vec3(Math.cos(baseAngle), 0, Math.sin(baseAngle));
                    if (claws) {
                        // Three claw marks tearing through the air in front
                        sp("claw", c.add(mid.scale(radius * 0.9)).add(0, 1.1, 0)).size((float) radius * 0.75f, (float) radius * 0.85f).life(9).colors(WHITE, col)
                                .facing(-mid.x, 0, -mid.z).startRoll((float) ((rnd() - 0.5) * 1.2 + 0.4)).envelope(0.08f, 0.35f, 1f);
                    } else {
                        // A crescent of light lying along the swing
                        sp("slash", c.add(0, 0.9, 0)).size((float) radius * 1.05f, (float) radius * 1.15f).life(8).colors(lighten(col, 0.5f), col)
                                .facing(0, 1, 0).startRoll((float) baseAngle).envelope(0.08f, 0.35f, 1f);
                    }
                }
                if (age == 6) s.flash(c.add(Math.cos(baseAngle) * radius, 0.3, Math.sin(baseAngle) * radius), thick * 3f + 0.4f, 6, col);
                age++;
                return age < 10;
            }
        });
    }

    static void orbit(double[] d, int[] i, Skin s) {
        double radius = d[0], height = d[1];
        float size = (float) d[2];
        double turns = d[3];
        int entityId = i[0], count = i[1], ticks = i[2], col = i[3];
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                Entity e = level.getEntity(entityId);
                if (e == null || e.isRemoved()) return false;
                Vec3 p = e.position();
                double spin = age / 20.0 * turns * Math.PI * 2;
                for (int k = 0; k < count; k++) {
                    double a = spin + Math.PI * 2 * k / count;
                    double bob = Math.sin(age * 0.35 + k) * 0.25;
                    Vec3 pos = new Vec3(p.x + Math.cos(a) * radius, p.y + height + bob, p.z + Math.sin(a) * radius);
                    Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(Math.signum(turns));
                    s.orbiter(pos, tangent, Math.max(0.1f, size * 1.2f), col);
                    if (age % 3 == k % 3) s.along(pos, tangent, Math.max(0.1f, size), col);
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
        int count = i[0], ticks = i[1], col = i[2];
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                double spin = age / 20.0 * turns * Math.PI * 2;
                int drawn = n(count);
                for (int k = 0; k < drawn; k++) {
                    double frac = k / (double) drawn;
                    double a = spin * (1.4 - frac * 0.6) + Math.PI * 2 * k / 3.0;
                    double r = radius * (0.25 + 0.75 * frac);
                    Vec3 pos = new Vec3(c.x + Math.cos(a) * r, c.y + 0.1 + frac * 3.0, c.z + Math.sin(a) * r);
                    Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(Math.signum(turns));
                    s.orbiter(pos, tangent, Math.max(0.1f, size * 1.2f), col);
                    if ((age + k) % 5 == 0) s.along(pos, tangent, size, col);
                }
                if (age % 12 == 0) s.ring(c.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, radius * 1.3, 14, col);
                if (age % 4 == 0) s.body(c.add(gauss() * radius * 0.5, 0.3, gauss() * radius * 0.5), new Vec3(0, 0.03, 0), (float) radius * 0.35f, 14, col, 0.5f);
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
        int pieces = i[0], life = i[1], col = i[2];
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                for (int k = 0; k < pieces; k++) {
                    double t = k / (double) Math.max(1, pieces - 1);
                    if ((int) (t * 8) != age) continue;
                    double a = t * turns * Math.PI * 2;
                    Vec3 pos = new Vec3(base.x + Math.cos(a) * radius, base.y + t * height, base.z + Math.sin(a) * radius);
                    s.mote(pos, new Vec3(0, 0.02, 0), size * 1.2f, life, col, 0f);
                }
                age++;
                return age < 9;
            }
        });
    }

    static void trail(double[] d, int[] i, Skin s) {
        float size = (float) d[0];
        int entityId = i[0], ticks = i[1], col = i[2];
        FxSystem.add(new FxSystem.Emitter() {
            int age;
            Vec3 last;

            @Override
            public boolean tick(ClientLevel level) {
                Entity e = level.getEntity(entityId);
                if (e == null || e.isRemoved()) return false;
                Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
                Vec3 dir = last == null ? new Vec3(0, 1, 0) : p.subtract(last);
                if (dir.lengthSqr() > 1.0e-6) dir = dir.normalize();
                last = p;
                sp("glow", p).size(size * 3.0f + 0.1f, 0.04f).life(8).colors(lighten(col, 0.4f), col).envelope(0.05f, 0.1f, 0.7f);
                s.along(p, dir, size * 2.2f + 0.1f, col);
                age++;
                return age < ticks;
            }
        });
    }
}
