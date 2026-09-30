package dev.abps.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A ribbon: one continuous textured stroke laid along a path, like a sword trail or a brush stroke. It is made of
 * overlapping pieces that each show their own slice of one long texture, so it reads as a single smooth shape
 * instead of a pile of sprites. It sweeps in head first, can spin and grow, then dissolves from the tail.
 */
public final class Ribbon implements FxSystem.Emitter {

    /** Where the ribbon runs. s goes from 0 (tail end of the path) to 1 (head end). time goes 0 to 1 over its life. */
    public interface Path {
        /** Fills in the point, the direction the stroke runs, and the side its bright outer edge faces. */
        void at(double s, double time, Vec3[] out);
    }

    public enum Style {
        /** Streaky light with a white-hot edge. */
        ENERGY,
        /** A dark brush stroke with a glowing rim, like ink. */
        INK
    }

    private final Path path;
    private final Style style;
    private final int color, rim;
    private final float width;
    private final int life, sweep;
    private final float hold, tailChase, brightness;
    private final int sparks;
    private int segments;
    private final List<RibbonQuad> body = new ArrayList<>(), edge = new ArrayList<>(), under = new ArrayList<>();
    private SpriteSet bodySet, edgeSet, underSet;
    private int age;

    private Ribbon(Builder b) {
        this.path = b.path;
        this.style = b.style;
        this.color = b.color;
        this.rim = b.rim;
        this.width = b.width;
        this.life = Math.max(4, b.life);
        this.sweep = Math.max(1, Math.min(b.sweep, this.life - 2));
        this.hold = b.hold;
        this.tailChase = b.tailChase;
        this.brightness = b.brightness;
        this.sparks = b.sparks;
        this.segments = b.segments;
    }

    public static Builder along(Path path) {
        return new Builder(path);
    }

    // ---- Ready-made paths ----

    /**
     * A flat arc around center. mid points at the middle of the swing, normal is the swing plane's normal, and the
     * stroke sweeps from -span/2 to +span/2 (right-handed about normal). spin turns the whole arc over its life
     * and grow scales the radius by the end.
     */
    public static Path arc(Vec3 center, Vec3 mid, Vec3 normal, double radius, double span, double spin, double grow) {
        Vec3 n = normal.normalize();
        Vec3 e1 = mid.subtract(n.scale(mid.dot(n))).normalize();
        Vec3 e2 = n.cross(e1).normalize();
        return (s, time, out) -> {
            double eased = 1 - Math.pow(1 - time, 3);
            double a = -span / 2 + s * span + spin * eased;
            double r = radius * (1 + (grow - 1) * eased);
            Vec3 radial = e1.scale(Math.cos(a)).add(e2.scale(Math.sin(a)));
            out[0] = center.add(radial.scale(r));
            out[1] = e1.scale(-Math.sin(a)).add(e2.scale(Math.cos(a)));
            out[2] = radial;
        };
    }

    /** A spiral climbing from base: turns times around, from radius r0 to r1, over height h. */
    public static Path spiral(Vec3 base, double r0, double r1, double h, double turns, double startAngle, double spin) {
        return (s, time, out) -> {
            double a = startAngle + s * turns * Math.PI * 2 + spin * time;
            double r = r0 + (r1 - r0) * s;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            out[0] = base.add(radial.scale(r)).add(0, s * h, 0);
            Vec3 around = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(r * turns * Math.PI * 2);
            out[1] = around.add(radial.scale(r1 - r0)).add(0, h, 0).normalize();
            out[2] = radial;
        };
    }

    /** A straight stroke from a to b, its bright edge facing side. Good for dashes and thrusts. */
    public static Path line(Vec3 a, Vec3 b, Vec3 side) {
        Vec3 dir = b.subtract(a);
        Vec3 t = dir.normalize();
        Vec3 sd = side.subtract(t.scale(side.dot(t))).normalize();
        return (s, time, out) -> {
            out[0] = a.add(dir.scale(s));
            out[1] = t;
            out[2] = sd;
        };
    }

    // ---- Running ----

    private static float taper(double u) {
        // Needle-thin at the tail, fullest just past the middle, a short sharp point at the head
        double body = Math.pow(Math.sin(Math.PI * Math.pow(Math.max(0, Math.min(1, u)), 0.8)), 0.65);
        return (float) Math.max(0.04, body * (0.55 + 0.45 * u));
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static final Vec3[] PT = new Vec3[3];

    @Override
    public boolean tick(ClientLevel level) {
        if (age == 0 && !start(level)) return false;
        double time = Math.min(1, (age + 1) / (double) life);
        double head = 1 - Math.pow(1 - Math.min(1, (age + 1) / (double) sweep), 2);
        double dissolveFrom = sweep + hold * (life - sweep);
        double d = Math.max(0, Math.min(1, (age + 1 - dissolveFrom) / Math.max(1, life - dissolveFrom)));
        double tail = tailChase * ease(d) * head;
        float fade = (float) ((1 - ease((d - 0.7) / 0.3)) * Math.min(1, (age + 1) / 2.0));
        int frame = Math.min(4, (int) (d * 5));

        // Lay the pieces along the visible part of the path
        int n = segments;
        Vec3[] pos = new Vec3[n], tan = new Vec3[n], side = new Vec3[n];
        double length = 0;
        for (int k = 0; k < n; k++) {
            double s = tail + (head - tail) * (k + 0.5) / n;
            path.at(s, time, PT);
            pos[k] = PT[0];
            tan[k] = PT[1];
            side[k] = PT[2];
            if (k > 0) length += pos[k].distanceTo(pos[k - 1]);
        }
        length = Math.max(0.05, length * n / Math.max(1, n - 1));
        double spacing = length / n;
        for (int k = 0; k < n; k++) {
            double u = (k + 0.5) / n;
            float size = width; // every piece the same size so the texture lines up; the crescent shape is in the texture
            float du = (float) (size / length);
            // Pieces overlap; split the light between them so the ribbon doesn't glow brighter where they stack
            float share = (float) Math.min(1.0, spacing / (2 * size));
            Quaternionf q = basis(tan[k], side[k]);
            float bodyGain, edgeGain;
            if (style == Style.ENERGY) {
                bodyGain = share * 1.35f * brightness * fade;
                edgeGain = share * 1.6f * brightness * fade;
            } else {
                bodyGain = Math.min(1f, share * 4.5f) * fade;
                edgeGain = share * 3.2f * brightness * fade;
            }
            RibbonQuad a = body.get(k), b = edge.get(k);
            a.key(pos[k].x, pos[k].y, pos[k].z, q, size, (float) u - du, (float) u + du, bodyGain, age == 0);
            b.key(pos[k].x, pos[k].y, pos[k].z, q, size, (float) u - du, (float) u + du, edgeGain, age == 0);
            if (age == 0 || d > 0) {
                a.frame(bodySet.get(frame, 4));
                b.frame(edgeSet.get(frame, 4));
            }
            if (!under.isEmpty()) {
                // A faint tint under the light so the stroke keeps its color against a bright sky
                RibbonQuad c = under.get(k);
                c.key(pos[k].x, pos[k].y, pos[k].z, q, size, (float) u - du, (float) u + du, Math.min(1f, share * 2.4f) * 0.45f * fade, age == 0);
                if (age == 0 || d > 0) c.frame(underSet.get(frame, 4));
            }
        }

        // A few tiny embers thrown off the head while it sweeps, and flecks from the tail as it breaks up
        if (sparks > 0 && FxParticle.alive() < FxSystem.maxParticles()) {
            if (age < sweep) {
                for (int i = 0; i < sparks; i++) {
                    Vec3 h = pos[n - 1 - (int) (FxKit.rnd() * Math.min(4, n))];
                    Vec3 v = tan[n - 1].scale(FxKit.rnd(0.05, 0.16)).add(side[n - 1].scale(FxKit.rnd(0.02, 0.1))).add(FxKit.rndDir().scale(0.03));
                    FxKit.sp("ember", h).size(0.035f, 0.01f).life(8 + (int) (FxKit.rnd() * 8)).vel(v).drag(0.86f).grav(0.004f)
                            .colors(FxKit.lighten(style == Style.INK ? rim : color, 0.6f), style == Style.INK ? rim : color).envelope(0.05f, 0.4f, 1f);
                }
            } else if (d > 0 && d < 0.8 && age % 2 == 0) {
                Vec3 t = pos[(int) (FxKit.rnd() * n)];
                FxKit.sp(style == Style.INK ? "smoke" : "spark", t).size(0.03f, 0.005f).life(10).vel(FxKit.rndUp().scale(0.03)).drag(0.9f)
                        .colors(style == Style.INK ? color : FxKit.lighten(color, 0.4f), color).envelope(0.1f, 0.3f, 0.8f);
            }
        }
        age++;
        return age < life;
    }

    private boolean start(ClientLevel level) {
        // Enough pieces that neighbours always overlap, even on long strokes, or the ribbon shows gaps
        double longest = Math.max(pathLength(0), pathLength(1));
        segments = (int) Math.max(segments, Math.min(140, Math.ceil(longest / (width * 0.8))));
        String bodyName = style == Style.ENERGY ? "ribbon" : "ink";
        bodySet = FxSprites.get(bodyName);
        edgeSet = FxSprites.get("ribboncore");
        if (bodySet == null || edgeSet == null) return false;
        int edgeColor = style == Style.ENERGY ? FxKit.lighten(color, 0.75f) : rim;
        underSet = style == Style.ENERGY ? FxSprites.get("ribbonmask") : null;
        for (int k = 0; k < segments; k++) {
            RibbonQuad a = new RibbonQuad(level, 0, 0, 0, bodySet.get(0, 4), FxSprites.layer(bodyName), color, life + 1);
            RibbonQuad b = new RibbonQuad(level, 0, 0, 0, edgeSet.get(0, 4), FxLayers.ADDITIVE, edgeColor, life + 1);
            body.add(a);
            edge.add(b);
            Minecraft.getInstance().particleEngine.add(a);
            Minecraft.getInstance().particleEngine.add(b);
            if (underSet != null) {
                RibbonQuad c = new RibbonQuad(level, 0, 0, 0, underSet.get(0, 4), FxLayers.NORMAL, FxKit.darken(color, 0.35f), life + 1);
                under.add(c);
                Minecraft.getInstance().particleEngine.add(c);
            }
        }
        return true;
    }

    private double pathLength(double time) {
        double len = 0;
        Vec3 prev = null;
        for (int k = 0; k <= 32; k++) {
            path.at(k / 32.0, time, PT);
            if (prev != null) len += prev.distanceTo(PT[0]);
            prev = PT[0];
        }
        return len;
    }

    /** Local x runs along the stroke, local y toward the bright edge (the top of the texture), z is the face. */
    private static Quaternionf basis(Vec3 tangent, Vec3 side) {
        Vector3f x = new Vector3f((float) tangent.x, (float) tangent.y, (float) tangent.z).normalize();
        Vector3f y = new Vector3f((float) side.x, (float) side.y, (float) side.z);
        y.sub(new Vector3f(x).mul(y.dot(x)));
        if (y.lengthSquared() < 1e-6f) y.set(0, 1, 0);
        y.normalize();
        Vector3f z = new Vector3f(x).cross(y).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    public static final class Builder {
        private final Path path;
        private Style style = Style.ENERGY;
        private int color = 0xFF4060, rim = 0xFFFFFF;
        private float width = 0.4f;
        private int life = 20, sweep = 4;
        private float hold = 0.25f, tailChase = 0.5f, brightness = 1f;
        private int sparks = 1;
        private int segments = 36;

        private Builder(Path path) {
            this.path = path;
        }

        /** Streaky light in this color. */
        public Builder energy(int color) {
            this.style = Style.ENERGY;
            this.color = color;
            return this;
        }

        /** A brush stroke in ink color with a glowing rim. */
        public Builder ink(int ink, int rim) {
            this.style = Style.INK;
            this.color = ink;
            this.rim = rim;
            return this;
        }

        /** Half the width of the stroke at its thickest, in blocks. */
        public Builder width(float w) {
            this.width = w;
            return this;
        }

        /** Total ticks, and how many of them the head takes to sweep in. */
        public Builder time(int life, int sweep) {
            this.life = life;
            this.sweep = sweep;
            return this;
        }

        /** Share of the time after the sweep that it stays whole before dissolving (0 to 1). */
        public Builder hold(float h) {
            this.hold = h;
            return this;
        }

        /** How far the tail catches up toward the head as it fades, 0 to 1. */
        public Builder tailChase(float t) {
            this.tailChase = t;
            return this;
        }

        public Builder brightness(float b) {
            this.brightness = b;
            return this;
        }

        /** Embers thrown off the head per tick while it sweeps. */
        public Builder sparks(int n) {
            this.sparks = n;
            return this;
        }

        /** Pieces the ribbon is made of. More is smoother on long strokes. */
        public Builder segments(int n) {
            this.segments = Math.max(6, Math.min(80, n));
            return this;
        }

        public void play() {
            if (!FxSystem.ready || !FxSprites.loaded()) return;
            FxSystem.add(new Ribbon(this));
        }
    }
}
