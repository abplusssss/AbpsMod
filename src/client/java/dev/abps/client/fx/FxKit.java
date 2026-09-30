package dev.abps.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Small building blocks shared by every effect: spawning sprites, colors, random directions and a few composites. */
final class FxKit {

    static final int WHITE = 0xFFFFFF;
    private static final RandomSource RS = RandomSource.create();

    private FxKit() {
    }

    static double rnd() {
        return ThreadLocalRandom.current().nextDouble();
    }

    static double rnd(double lo, double hi) {
        return lo + rnd() * (hi - lo);
    }

    static double gauss() {
        return ThreadLocalRandom.current().nextGaussian();
    }

    /** Scales a piece count by the quality setting. */
    static int n(int base) {
        return Math.max(1, Math.round(base * FxSystem.density()));
    }

    static int lighten(int c, float k) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    static int darken(int c, float k) {
        int r = (int) (((c >> 16) & 0xFF) * (1 - k)), g = (int) (((c >> 8) & 0xFF) * (1 - k)), b = (int) ((c & 0xFF) * (1 - k));
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static Vec3 vec(double[] d, int at) {
        return new Vec3(d[at], d[at + 1], d[at + 2]);
    }

    static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Turns a vector about the vertical axis. */
    static Vec3 rotY(Vec3 v, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    /** A uniformly random direction. */
    static Vec3 rndDir() {
        return new Vec3(gauss(), gauss(), gauss()).normalize();
    }

    /** A random direction that leans upward, for things thrown out of the ground. */
    static Vec3 rndUp() {
        return new Vec3(gauss(), Math.abs(gauss()) * 0.9 + 0.25, gauss()).normalize();
    }

    /** Two vectors that, with the normal, make a set of axes. */
    static Vec3[] axes(Vec3 normal) {
        Vec3 n = normal.normalize();
        Vec3 helper = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = n.cross(helper).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    /**
     * Makes one sprite. The texture is picked at random from the sprite's variants. Never returns null: if the game
     * already has too many effect particles the sprite is created and thrown away, so callers can chain freely.
     */
    static FxParticle sp(String name, double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        SpriteSet set = FxSprites.get(name);
        if (level == null || set == null) throw new IllegalStateException("effect sprite " + name + " is not loaded");
        FxParticle p = new FxParticle(level, x, y, z, set.get(RS), FxSprites.layer(name));
        if (FxParticle.alive() > FxSystem.maxParticles() + 1) {
            p.remove();
            return p;
        }
        Minecraft.getInstance().particleEngine.add(p);
        return p;
    }

    /** Like sp, but never dropped for being over the particle limit. For the structural pieces of a big effect, like the water surface of a wave. */
    static FxParticle spForce(String name, double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        SpriteSet set = FxSprites.get(name);
        if (level == null || set == null) throw new IllegalStateException("effect sprite " + name + " is not loaded");
        FxParticle p = new FxParticle(level, x, y, z, set.get(RS), FxSprites.layer(name));
        Minecraft.getInstance().particleEngine.add(p);
        return p;
    }

    static FxParticle sp(String name, Vec3 at) {
        return sp(name, at.x, at.y, at.z);
    }

    /** A burst of light: soft halo, hot white core and a streaked flare. */
    static void bloom(Vec3 p, float size, int life, int col) {
        sp("glow", p).size(size * 1.2f, size * 2.4f).life(life + 4).colors(lighten(col, 0.4f), col).envelope(0.1f, 0.35f, 0.9f);
        sp("glow", p).size(size * 0.5f, size * 0.9f).life(life).colors(WHITE, lighten(col, 0.5f)).envelope(0.05f, 0.3f, 1f);
        sp("flare", p).size(0.2f, size * 2.4f + 0.4f).life(life).colors(WHITE, lighten(col, 0.3f)).envelope(0.08f, 0.2f, 1f);
    }

    /** A flat ring expanding from radius r0 to r1. sprite is "ring" (thin line) or "shockwave" (thick, with a trailing glow). */
    static FxParticle ringFlat(Vec3 c, Vec3 normal, double r0, double r1, int life, int col, String sprite) {
        return sp(sprite, c).size((float) (r0 / 0.86), (float) (r1 / 0.86)).life(life).colors(lighten(col, 0.4f), col)
                .facing(normal.x, normal.y, normal.z).envelope(0.05f, 0.25f, 1f);
    }

    /** A straight beam: a chain of overlapping glows with a white core. */
    static void beamLine(Vec3 a, Vec3 b, float thick, int life, int col) {
        Vec3 dv = b.subtract(a);
        double len = dv.length();
        if (len < 0.05) return;
        float sz = Math.max(0.14f, thick * 1.7f);
        int count = (int) Math.min(90, Math.max(2, len / (sz * 0.55)));
        count = Math.max(2, Math.round(count * Math.min(1.4f, FxSystem.density())));
        for (int k = 0; k < count; k++) {
            Vec3 pos = a.add(dv.scale(k / (double) (count - 1)));
            sp("glow", pos).size(sz, sz * 0.4f).life(life).colors(lighten(col, 0.25f), col).envelope(0.04f, 0.1f, 0.9f);
            sp("glow", pos).size(sz * 0.55f, sz * 0.2f).life(Math.max(2, life - 1)).colors(WHITE, lighten(col, 0.6f)).envelope(0.04f, 0.1f, 1f);
        }
    }

    /** A tapering line of light: several short streaks laid end to end, brighter at the head. Good for arrows and slashes in flight. */
    static void streakLine(Vec3 from, Vec3 to, float size, int life, int col) {
        Vec3 dv = to.subtract(from);
        double len = dv.length();
        if (len < 0.05) return;
        Vec3 dir = dv.scale(1 / len);
        int count = Math.max(1, (int) (len / (size * 1.6)));
        for (int k = 0; k < count; k++) {
            Vec3 pos = from.add(dv.scale((k + 0.5) / count));
            double t = (k + 0.5) / count;
            sp("streak", pos).size(size * (0.5f + (float) t * 0.7f), size * 0.2f).life(life).colors(mix(col, WHITE, (float) t * 0.7f), col)
                    .vel(dir.x * 0.001, dir.y * 0.001, dir.z * 0.001).axial().envelope(0.05f, 0.3f, (float) (0.4 + 0.6 * t));
        }
    }
}
