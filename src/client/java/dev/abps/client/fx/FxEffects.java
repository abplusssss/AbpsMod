package dev.abps.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The actual look of every effect. Each one is built from several layered sprites (a hot white core, a colored halo,
 * sparks and flares) because a single sprite never looks like light; the layers adding up is what makes it glow.
 * Sizes are half-widths in blocks, lifetimes are in ticks.
 */
final class FxEffects {

    private static final int WHITE = 0xFFFFFF;
    private static final Map<Integer, WaveEmitter> WAVES = new HashMap<>();

    private FxEffects() {
    }

    // ------------------------------------------------------------------ helpers

    private static double rnd() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static double gauss() {
        return ThreadLocalRandom.current().nextGaussian();
    }

    /** Scales a piece count by the quality setting. */
    private static int n(int base) {
        return Math.max(1, Math.round(base * FxSystem.density()));
    }

    static int lighten(int c, float k) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static FxParticle make(SpriteSet set, SingleQuadParticle.Layer layer, double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || set == null) return null;
        if (FxParticle.alive() > FxSystem.maxParticles()) return null;
        FxParticle p = new FxParticle(level, x, y, z, set.first(), layer);
        Minecraft.getInstance().particleEngine.add(p);
        return p;
    }

    /** A soft glowing ball of light. */
    private static FxParticle glow(double x, double y, double z, float s0, float s1, int life, int c0, int c1) {
        FxParticle p = make(FxSprites.glow, FxLayers.ADDITIVE, x, y, z);
        if (p != null) p.size(s0, s1).life(life).colors(c0, c1);
        return p;
    }

    /** A four-point star, for sparks and glints. */
    private static FxParticle spark(double x, double y, double z, float s0, float s1, int life, int c0, int c1) {
        FxParticle p = make(FxSprites.spark, FxLayers.ADDITIVE, x, y, z);
        if (p != null) p.size(s0, s1).life(life).colors(c0, c1).startRoll((float) (rnd() * Math.PI)).spin((float) (gauss() * 0.08));
        return p;
    }

    /** A star with a long horizontal streak, for the hot flash of an impact. */
    private static FxParticle flare(double x, double y, double z, float s0, float s1, int life, int c0, int c1) {
        FxParticle p = make(FxSprites.flare, FxLayers.ADDITIVE, x, y, z);
        if (p != null) p.size(s0, s1).life(life).colors(c0, c1).envelope(0.08f, 0.2f, 1f);
        return p;
    }

    /** Soft cloud drawn with normal blending. */
    private static FxParticle smoke(double x, double y, double z, float s0, float s1, int life, int c0, int c1, float peak) {
        FxParticle p = make(FxSprites.smoke, FxLayers.NORMAL, x, y, z);
        if (p != null) p.size(s0, s1).life(life).colors(c0, c1).envelope(0.15f, 0.45f, peak).startRoll((float) (rnd() * 6.28)).spin((float) (gauss() * 0.02));
        return p;
    }

    /** A ring sprite lying flat, facing along the normal, growing from radius r0 to r1. */
    private static FxParticle ringSprite(Vec3 c, Vec3 normal, double r0, double r1, int life, int c0, int c1) {
        FxParticle p = make(FxSprites.ring, FxLayers.ADDITIVE, c.x, c.y, c.z);
        if (p != null) p.size((float) (r0 / 0.86), (float) (r1 / 0.86)).life(life).colors(c0, c1).facing(normal.x, normal.y, normal.z).envelope(0.05f, 0.25f, 1f);
        return p;
    }

    private static Vec3 vec(double[] d, int at) {
        return new Vec3(d[at], d[at + 1], d[at + 2]);
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ one-off effects

    static void shard(double[] d, int[] i) {
        int col = i[1];
        float size = (float) d[6];
        FxParticle p = spark(d[0], d[1], d[2], size * 2.6f, size * 0.4f, i[0], lighten(col, 0.6f), col);
        if (p != null) p.vel(d[3], d[4], d[5]).grav((float) (d[7] / 0.04)).drag(0.97f);
    }

    static void burst(double[] d, int[] i) {
        int col = i[2], life = i[1];
        float size = (float) d[4];
        double x = d[0], y = d[1], z = d[2];
        int count = n(Math.min(i[0], 24));
        for (int k = 0; k < count; k++) {
            Vec3 dir = new Vec3(gauss(), Math.abs(gauss()) * 0.9 + 0.2, gauss()).normalize();
            double sp = d[3] * (0.6 + rnd() * 0.9) * 1.5;
            FxParticle p = spark(x, y, z, size * (float) (1.8 + rnd() * 1.2), 0.05f, (int) (life * (0.8 + rnd() * 0.6)), lighten(col, 0.65f), col);
            if (p != null) p.vel(dir.x * sp, dir.y * sp, dir.z * sp).drag(0.90f).grav(0.12f);
        }
        flare(x, y, z, 0.12f, size * 9 + 0.5f, 7, WHITE, col);
        for (int k = 0; k < 2; k++) glow(x, y, z, size * 3f, size * 7f + 0.3f, life, lighten(col, 0.3f), col);
    }

    static void ring(double[] d, int[] i) {
        Vec3 c = vec(d, 0), normal = vec(d, 3);
        double r0 = d[6], r1 = d[7];
        int life = Math.max(6, i[1]), col = i[2];
        ringSprite(c, normal, r0, r1, life, lighten(col, 0.4f), col);
        if (d[8] >= 0.10) ringSprite(c, normal, r0 * 0.9, r1 * 0.82, Math.max(5, life - 2), WHITE, lighten(col, 0.3f));
        // A big, near-static ring on the ground is a magic circle: put the runes on it and let it turn slowly
        if (Math.abs(r1 - r0) < 0.01 && r0 >= 2.5 && d[8] >= 0.11 && normal.y > 0.9) {
            FxParticle s = make(FxSprites.sigil, FxLayers.ADDITIVE, c.x, c.y + 0.02, c.z);
            if (s != null) s.size((float) (r0 / 0.94), (float) (r0 / 0.94)).life(life + 6).colors(lighten(col, 0.25f), col)
                    .facing(0, 1, 0).spin(0.018f).envelope(0.12f, 0.5f, 1f);
        }
        // Sparks riding the edge of the ring
        Vec3 helper = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = normal.cross(helper).normalize();
        Vec3 v = normal.cross(u).normalize();
        int count = n(Math.min(i[0], 16));
        double phase = rnd() * Math.PI * 2;
        for (int k = 0; k < count; k++) {
            double a = phase + Math.PI * 2 * k / count;
            Vec3 radial = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 pos = c.add(radial.scale(r0));
            double sp = (r1 - r0) / life;
            FxParticle p = spark(pos.x, pos.y, pos.z, 0.16f + (float) d[8] * 0.8f, 0.04f, life, WHITE, col);
            if (p != null) p.vel(radial.x * sp, radial.y * sp, radial.z * sp).drag(1f);
        }
    }

    /** A straight beam: a chain of glows with a white core, flares at both ends and a few sparks flying off. */
    private static void beamSeg(Vec3 a, Vec3 b, float thick, int life, int col, boolean flares) {
        Vec3 dv = b.subtract(a);
        double len = dv.length();
        if (len < 0.05) return;
        float sz = Math.max(0.14f, thick * 1.7f);
        int count = (int) Math.min(90, Math.max(2, len / (sz * 0.55)));
        count = Math.max(2, Math.round(count * Math.min(1.4f, FxSystem.density())));
        for (int k = 0; k < count; k++) {
            double t = k / (double) (count - 1);
            Vec3 pos = a.add(dv.scale(t));
            FxParticle g = glow(pos.x, pos.y, pos.z, sz, sz * 0.4f, life, lighten(col, 0.25f), col);
            if (g != null) g.envelope(0.04f, 0.1f, 0.9f);
            FxParticle core = glow(pos.x, pos.y, pos.z, sz * 0.55f, sz * 0.2f, Math.max(2, life - 1), WHITE, lighten(col, 0.6f));
            if (core != null) core.envelope(0.04f, 0.1f, 1f);
        }
        if (flares) {
            flare(a.x, a.y, a.z, 0.1f, sz * 3.2f + 0.3f, 6, WHITE, col);
            flare(b.x, b.y, b.z, 0.1f, sz * 3.6f + 0.4f, 7, WHITE, col);
        }
        for (int k = 0; k < n(4); k++) {
            Vec3 pos = a.add(dv.scale(rnd()));
            FxParticle p = spark(pos.x, pos.y, pos.z, sz * 1.1f, 0.04f, life + 6, WHITE, col);
            if (p != null) p.vel(gauss() * 0.03, gauss() * 0.03 + 0.01, gauss() * 0.03).drag(0.93f);
        }
    }

    static void beam(double[] d, int[] i) {
        beamSeg(vec(d, 0), vec(d, 3), (float) d[6], i[0], i[1], true);
    }

    static void zigzag(double[] d, int[] i) {
        Vec3 a = vec(d, 0), b = vec(d, 3);
        int pieces = i[0];
        double jitter = d[6];
        Vec3 prev = a;
        for (int k = 1; k <= pieces; k++) {
            Vec3 next = a.lerp(b, (double) k / pieces);
            if (k < pieces) next = next.add((rnd() - 0.5) * jitter, (rnd() - 0.5) * jitter, (rnd() - 0.5) * jitter);
            beamSeg(prev, next, (float) d[7], i[1], i[2], false);
            if (k < pieces) flare(next.x, next.y, next.z, 0.08f, (float) d[7] * 3f + 0.25f, 5, WHITE, i[2]);
            prev = next;
        }
        flare(a.x, a.y, a.z, 0.1f, (float) d[7] * 4f + 0.35f, 6, WHITE, i[2]);
        flare(b.x, b.y, b.z, 0.1f, (float) d[7] * 5f + 0.5f, 8, WHITE, i[2]);
    }

    static void flash(double[] d, int[] i) {
        double x = d[0], y = d[1], z = d[2];
        float size = (float) d[3];
        int life = Math.max(6, i[0]), col = i[1];
        flare(x, y, z, 0.2f, size * 2.4f + 0.5f, life, WHITE, lighten(col, 0.3f));
        glow(x, y, z, size * 1.2f, size * 2.4f, life + 4, lighten(col, 0.4f), col);
        FxParticle r = make(FxSprites.ring, FxLayers.ADDITIVE, x, y, z);
        if (r != null) r.size(0.3f, size * 1.6f).life(8).colors(WHITE, col).envelope(0.05f, 0.2f, 1f);
        for (int k = 0; k < n(6); k++) {
            Vec3 dir = new Vec3(gauss(), gauss(), gauss()).normalize().scale(0.25 + rnd() * 0.2);
            FxParticle p = spark(x, y, z, 0.2f, 0.04f, 10, WHITE, col);
            if (p != null) p.vel(dir.x, dir.y, dir.z).drag(0.88f);
        }
    }

    static void sphere(double[] d, int[] i) {
        Vec3 c = vec(d, 0);
        double r0 = d[3], r1 = d[4];
        float size = (float) d[5];
        int life = Math.max(4, i[1]), col = i[2];
        int count = n(Math.min(i[0], 60));
        double golden = Math.PI * (3 - Math.sqrt(5));
        double speed = (r1 - r0) / life;
        for (int k = 0; k < count; k++) {
            double y = 1 - (k + 0.5) * 2.0 / count;
            double rr = Math.sqrt(Math.max(0, 1 - y * y));
            double a = golden * k;
            Vec3 dir = new Vec3(Math.cos(a) * rr, y, Math.sin(a) * rr);
            Vec3 pos = c.add(dir.scale(r0));
            FxParticle p = spark(pos.x, pos.y, pos.z, size * 3f, size * 0.5f, life, lighten(col, 0.5f), col);
            if (p != null) p.vel(dir.x * speed, dir.y * speed, dir.z * speed).drag(1f);
        }
        glow(c.x, c.y, c.z, (float) r0 + 0.3f, (float) r1 * 0.8f + 0.3f, life, lighten(col, 0.2f), col).envelope(0.1f, 0.3f, 0.35f);
    }

    static void particles(double[] d, int[] i) {
        double x = d[0], y = d[1], z = d[2];
        int count = n(Math.min(Math.max(1, i[0]), 10));
        int col = i[1], style = i[2];
        float size = (float) d[7];
        double speed = Math.max(0.015, d[6] * 0.7);
        for (int k = 0; k < count; k++) {
            double px = x + gauss() * d[3] * 0.6, py = y + gauss() * d[4] * 0.6, pz = z + gauss() * d[5] * 0.6;
            Vec3 dir = new Vec3(gauss(), gauss() * 0.6 + 0.3, gauss()).normalize().scale(speed * (0.5 + rnd()));
            if (style == 1) {
                FxParticle p = smoke(px, py, pz, 0.22f * size, 0.6f * size, 18 + (int) (rnd() * 12), lighten(col, 0.15f), col, 0.55f);
                if (p != null) p.vel(dir.x * 0.5, dir.y * 0.5 + 0.012, dir.z * 0.5).drag(0.94f);
            } else if (style == 2) {
                FxParticle p = make(FxSprites.shard, FxLayers.NORMAL, px, py, pz);
                if (p != null) p.size(0.12f * size, 0.04f).life(14 + (int) (rnd() * 8)).colors(col, mix(col, 0x202020, 0.5f))
                        .vel(dir.x, dir.y + 0.04, dir.z).grav(0.5f).drag(0.95f).spin((float) (gauss() * 0.2)).startRoll((float) (rnd() * 6.28)).envelope(0.02f, 0.6f, 1f);
            } else {
                FxParticle p = spark(px, py, pz, 0.26f * size, 0.04f, 10 + (int) (rnd() * 8), lighten(col, 0.5f), col);
                if (p != null) p.vel(dir.x, dir.y, dir.z).drag(0.92f).grav(0.05f);
            }
        }
    }

    // ------------------------------------------------------------------ effects that play out over time

    static void pillar(double[] d, int[] i) {
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
                    ringSprite(base.add(0, 0.05, 0), new Vec3(0, 1, 0), 0.3, radius * 3 + 1, 12, WHITE, col);
                    flare(base.x, base.y + 0.3, base.z, 0.2f, (float) radius * 6 + 1.2f, 9, WHITE, col);
                }
                int count = n(3 + (int) (h * 0.9));
                for (int k = 0; k < count; k++) {
                    double y = base.y + rnd() * h;
                    double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * rad * 0.8;
                    FxParticle p = glow(base.x + Math.cos(a) * r, y, base.z + Math.sin(a) * r, (float) (rad * 1.6 + 0.25), (float) (rad + 0.12), 5,
                            lighten(col, 0.4f), col);
                    if (p != null) p.vel(0, 0.02, 0);
                }
                for (double y = 0; y < h; y += 1.0) {
                    glow(base.x, base.y + y, base.z, (float) (rad * 0.8 + 0.12), (float) (rad * 0.4 + 0.06), 3, WHITE, lighten(col, 0.5f));
                }
                if (age % 2 == 0) {
                    FxParticle s = spark(base.x + gauss() * rad * 0.4, base.y + 0.2, base.z + gauss() * rad * 0.4, 0.2f, 0.04f, 16, WHITE, col);
                    if (s != null) s.vel(0, 0.12 + rnd() * 0.15, 0).drag(0.97f);
                }
                if (age == grow) flare(base.x, base.y + h, base.z, 0.1f, (float) radius * 5 + 1f, 8, WHITE, col);
                age++;
                return age < total;
            }
        });
    }

    static void jaws(double[] d, int[] i) {
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
                        for (int s = 0; s <= samples; s++) {
                            double f = s / (double) Math.max(1, samples);
                            Vec3 pos = base.add(tip.scale(h * f));
                            float sz = (float) (0.30 * (1 - 0.7 * f) + 0.06);
                            glow(pos.x, pos.y, pos.z, sz * 1.5f, sz * 0.6f, 4, lighten(col, 0.5f), col);
                            if (s == samples) spark(pos.x, pos.y, pos.z, 0.3f, 0.05f, 6, WHITE, col);
                        }
                    }
                }
                if (age == 12) {
                    ringSprite(c.add(0, 0.1, 0), new Vec3(0, 1, 0), radius * 0.4, radius * 1.4, 12, WHITE, col);
                    flare(c.x, c.y + 0.5, c.z, 0.2f, (float) radius * 1.5f + 1f, 10, WHITE, col);
                }
                age++;
                return age < 34;
            }
        });
    }

    static void slash(double[] d, int[] i) {
        Vec3 c = vec(d, 0);
        double baseAngle = Math.atan2(d[5], d[3]);
        double radius = d[6], arc = d[7];
        float thick = (float) d[8];
        int col = i[0];
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                for (int s = 0; s < 3; s++) {
                    double prog = (age + s / 3.0) / 10.0;
                    double a = baseAngle - arc / 2 + arc * prog;
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 pos = c.add(dir.scale(radius));
                    float sz = thick * 3.2f + 0.28f;
                    FxParticle g = glow(pos.x, pos.y, pos.z, sz, sz * 0.4f, 6, WHITE, col);
                    if (g != null) g.envelope(0.05f, 0.15f, 1f);
                    FxParticle h = glow(pos.x, pos.y, pos.z, sz * 1.6f, sz * 0.5f, 7, lighten(col, 0.2f), col);
                    if (h != null) h.envelope(0.05f, 0.2f, 0.6f);
                    if (s == 0) {
                        Vec3 tangent = new Vec3(-dir.z, 0, dir.x);
                        FxParticle sp = spark(pos.x, pos.y, pos.z, 0.2f, 0.04f, 10, WHITE, col);
                        if (sp != null) sp.vel(tangent.x * 0.12 + gauss() * 0.02, gauss() * 0.02, tangent.z * 0.12 + gauss() * 0.02).drag(0.9f);
                    }
                }
                if (age == 6) flare(c.x + Math.cos(baseAngle) * radius, c.y, c.z + Math.sin(baseAngle) * radius, 0.1f, thick * 6f + 0.6f, 6, WHITE, col);
                age++;
                return age < 10;
            }
        });
    }

    static void orbit(double[] d, int[] i) {
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
                    double x = p.x + Math.cos(a) * radius, y = p.y + height + bob, z = p.z + Math.sin(a) * radius;
                    glow(x, y, z, Math.max(0.15f, size * 3.2f), Math.max(0.1f, size * 1.6f), 3, WHITE, lighten(col, 0.3f));
                    if (age % 2 == 0) {
                        FxParticle t = glow(x, y, z, Math.max(0.1f, size * 2f), 0.03f, 9, col, mix(col, 0x000000, 0.4f));
                        if (t != null) t.envelope(0.05f, 0.1f, 0.7f);
                    }
                }
                age++;
                return age < ticks;
            }
        });
    }

    static void vortex(double[] d, int[] i) {
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
                    double x = c.x + Math.cos(a) * r, y = c.y + 0.1 + frac * 3.0, z = c.z + Math.sin(a) * r;
                    glow(x, y, z, Math.max(0.16f, size * 3.4f), Math.max(0.1f, size * 1.6f), 4, lighten(col, 0.4f), col);
                    if ((age + k) % 4 == 0) {
                        FxParticle s = spark(x, y, z, 0.16f, 0.03f, 12, WHITE, col);
                        if (s != null) s.vel(gauss() * 0.02, 0.03, gauss() * 0.02).drag(0.95f);
                    }
                }
                if (age % 12 == 0) ringSprite(c.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, radius * 1.3, 14, lighten(col, 0.3f), col);
                age++;
                return age < ticks;
            }
        });
    }

    static void fins(double[] d, int[] i) {
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
                    for (int s = 0; s < 4; s++) {
                        double f = s / 3.0;
                        Vec3 fp = p.add(0, f * 0.9, 0).subtract(tangent.scale(f * 0.35));
                        float sz = (float) (0.30 * (1 - f * 0.65) + 0.05);
                        glow(fp.x, fp.y, fp.z, sz * 1.5f, sz * 0.7f, 3, lighten(col, 0.35f), col);
                    }
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 w = p.subtract(tangent.scale(0.7)).add(radial.scale(side * 0.35));
                        glow(w.x, c.y + 0.05, w.z, 0.28f, 0.05f, 7, lighten(col, 0.5f), col).envelope(0.05f, 0.1f, 0.6f);
                    }
                    if (age % 2 == 0) {
                        FxParticle s = spark(p.x, p.y + 0.9, p.z, 0.18f, 0.03f, 10, WHITE, col);
                        if (s != null) s.vel(gauss() * 0.02, 0.05, gauss() * 0.02).grav(0.1f).drag(0.94f);
                    }
                }
                age++;
                return age < ticks;
            }
        });
    }

    static void helix(double[] d, int[] i) {
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
                    FxParticle p = spark(base.x + Math.cos(a) * radius, base.y + t * height, base.z + Math.sin(a) * radius,
                            size * 3.2f, size * 0.6f, life, lighten(col, 0.5f), col);
                    if (p != null) p.vel(0, 0.02, 0);
                }
                age++;
                return age < 9;
            }
        });
    }

    static void trail(double[] d, int[] i) {
        float size = (float) d[0];
        int entityId = i[0], ticks = i[1], col = i[2];
        FxSystem.add(new FxSystem.Emitter() {
            int age;

            @Override
            public boolean tick(ClientLevel level) {
                Entity e = level.getEntity(entityId);
                if (e == null || e.isRemoved()) return false;
                Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
                FxParticle g = glow(p.x, p.y, p.z, size * 3.5f + 0.1f, 0.04f, 10, lighten(col, 0.4f), col);
                if (g != null) g.envelope(0.05f, 0.1f, 0.85f);
                glow(p.x, p.y, p.z, size * 1.6f + 0.05f, 0.02f, 6, WHITE, lighten(col, 0.5f));
                if (rnd() < 0.35) {
                    FxParticle s = spark(p.x + gauss() * 0.1, p.y + gauss() * 0.1, p.z + gauss() * 0.1, 0.16f, 0.03f, 12, WHITE, col);
                    if (s != null) s.vel(gauss() * 0.02, gauss() * 0.02, gauss() * 0.02).drag(0.93f);
                }
                age++;
                return age < ticks;
            }
        });
    }

    // ------------------------------------------------------------------ the tsunami

    static void wave(double[] d, int[] i) {
        WaveEmitter w = new WaveEmitter(vec(d, 0), new Vec3(d[3], 0, d[5]), d[6], i[0], i[1]);
        WAVES.put(i[2], w);
        FxSystem.add(w);
    }

    static void waveStop(int[] i) {
        WaveEmitter w = WAVES.remove(i[0]);
        if (w != null) w.stopping = true;
    }

    /**
     * A wall of water sweeping along the ground: a body of blue cloud with bright sheen, a leaning white crest with
     * spray flying off it, and foam at the foot. The server only says where it starts and when it ends; this moves
     * at the same half a block per tick the server moves the real hitbox.
     */
    private static final class WaveEmitter implements FxSystem.Emitter {
        final Vec3 origin, dir, lateral;
        final double height;
        final int columns, col;
        int age;
        boolean stopping;

        WaveEmitter(Vec3 origin, Vec3 dir, double height, int columns, int col) {
            this.origin = origin;
            this.dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
            this.lateral = new Vec3(-this.dir.z, 0, this.dir.x);
            this.height = height;
            this.columns = columns;
            this.col = col;
        }

        @Override
        public boolean tick(ClientLevel level) {
            Vec3 center = origin.add(dir.scale(age * 0.5));
            if (stopping || age > 260) {
                crash(center);
                return false;
            }
            double growth = Math.min(1.0, (age / 2 + 2) / 6.0);
            int deep = mix(col, 0x0D47A1, 0.45f);
            for (int c = 0; c < columns; c++) {
                double lo = c - (columns - 1) / 2.0;
                Vec3 at = center.add(lateral.scale(lo));
                double edge = 1.0 - Math.pow(Math.abs(lo) / (columns / 2.0 + 0.5), 2) * 0.45;
                double h = Math.max(0.3, height * growth * edge * (0.82 + 0.18 * Math.sin(age * 0.3 + c * 0.8)));
                // Body
                double y = at.y + rnd() * h;
                FxParticle b = smoke(at.x + gauss() * 0.3, y, at.z + gauss() * 0.6, 1.1f, 1.7f, 6, lighten(deep, 0.15f), lighten(col, 0.25f), 0.55f);
                if (b != null) b.vel(dir.x * 0.1, 0, dir.z * 0.1);
                // Sheen
                glow(at.x + gauss() * 0.3, at.y + rnd() * h, at.z + gauss() * 0.5, 1.0f, 0.6f, 4, lighten(col, 0.5f), col).envelope(0.1f, 0.3f, 0.5f);
                // Crest
                Vec3 top = at.add(dir.scale(0.9));
                FxParticle f = smoke(top.x, at.y + h, top.z, 0.9f, 1.5f, 8, WHITE, lighten(col, 0.7f), 0.7f);
                if (f != null) f.vel(dir.x * 0.16, 0.04, dir.z * 0.16);
                if (rnd() < 0.45) {
                    FxParticle s = spark(top.x, at.y + h, top.z, 0.22f, 0.04f, 12, WHITE, lighten(col, 0.5f));
                    if (s != null) s.vel(dir.x * 0.32 + gauss() * 0.05, 0.10 + rnd() * 0.1, dir.z * 0.32 + gauss() * 0.05).grav(0.35f).drag(0.96f);
                }
                // Foam at the foot
                if (rnd() < 0.5) smoke(at.x + gauss() * 0.4, at.y + 0.15, at.z + gauss() * 0.4, 1.3f, 2.0f, 10, WHITE, lighten(col, 0.6f), 0.6f);
            }
            if (age % 10 == 0) ringSprite(center.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, columns * 0.55, 12, lighten(col, 0.3f), col);
            age++;
            return true;
        }

        private void crash(Vec3 at) {
            ringSprite(at.add(0, 0.1, 0), new Vec3(0, 1, 0), 1, Math.max(6, height * 1.8), 16, WHITE, col);
            ringSprite(at.add(0, 0.1, 0), new Vec3(0, 1, 0), 1, Math.max(4, height * 1.2), 12, lighten(col, 0.5f), col);
            flare(at.x, at.y + 1.5, at.z, 0.3f, (float) height * 1.6f, 12, WHITE, col);
            for (int k = 0; k < n(34); k++) {
                double a = rnd() * Math.PI * 2, sp = 0.15 + rnd() * 0.3;
                FxParticle s = spark(at.x, at.y + 0.5, at.z, 0.26f, 0.04f, 20, WHITE, col);
                if (s != null) s.vel(Math.cos(a) * sp, 0.2 + rnd() * 0.3, Math.sin(a) * sp).grav(0.5f).drag(0.96f);
            }
            for (int k = 0; k < n(18); k++) {
                double a = rnd() * Math.PI * 2, r = rnd() * 3;
                FxParticle s = smoke(at.x + Math.cos(a) * r, at.y + 0.4 + rnd() * 2, at.z + Math.sin(a) * r, 1.4f, 3.0f, 16, WHITE, lighten(col, 0.6f), 0.6f);
                if (s != null) s.vel(Math.cos(a) * 0.05, 0.04, Math.sin(a) * 0.05);
            }
        }
    }
}
