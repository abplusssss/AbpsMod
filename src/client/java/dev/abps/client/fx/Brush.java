package dev.abps.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;

/**
 * The shared vocabulary every hand-made ability effect is written in. Everything here is a smooth stroke (a
 * {@link Ribbon}) or soft light and smoke. There are no picture sprites (no bats, hearts, skulls or runes), so
 * effects read as shapes of light and ink instead of stickers.
 */
final class Brush {

    private Brush() {
    }

    static final Vec3 UP = new Vec3(0, 1, 0);

    /** How a stroke is painted: streaky light in a color, or dark ink with a glowing rim. */
    record Paint(boolean ink, int color, int rim, float bright) {
        static Paint light(int c) {
            return new Paint(false, c, WHITE, 1f);
        }

        static Paint light(int c, float bright) {
            return new Paint(false, c, WHITE, bright);
        }

        static Paint ink(int ink, int rim) {
            return new Paint(true, ink, rim, 1f);
        }

        Paint bright(float b) {
            return new Paint(ink, color, rim, b);
        }

        /** The color this paint glows with: the rim for ink, the color for light. */
        int glow() {
            return ink ? rim : color;
        }

        Ribbon.Builder on(Ribbon.Path path) {
            Ribbon.Builder b = Ribbon.along(path);
            if (ink) b.ink(color, rim);
            else b.energy(color);
            return b.brightness(bright);
        }
    }

    // ------------------------------------------------------------------ geometry helpers

    static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    /** A side direction for a stroke that runs along tangent at p, turned so its face looks at the camera. */
    static Vec3 faceCam(Vec3 p, Vec3 tangent) {
        Vec3 view = camera().subtract(p);
        Vec3 s = tangent.cross(view);
        if (s.lengthSqr() < 1.0e-6) s = tangent.cross(UP);
        if (s.lengthSqr() < 1.0e-6) s = new Vec3(1, 0, 0);
        return s.normalize();
    }

    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** A straight stroke from a to b that keeps turning its face toward the camera, so it never shows edge-on. */
    static Ribbon.Path lineCam(Vec3 a, Vec3 b) {
        Vec3 dv = b.subtract(a);
        Vec3 d = dv.lengthSqr() < 1e-8 ? UP : dv.normalize();
        return Ribbon.curve((s, time) -> a.add(dv.scale(s)), (s, time) -> faceCam(a.add(dv.scale(s)), d));
    }

    /** A crystal spike growing from base to tip: sharp at the tip, widest near the base, with a bright core. */
    static void spike(Vec3 base, Vec3 tip, float width, int life, Paint p) {
        p.on(lineCam(tip, base)).width(width).time(life, 2).hold(0.75f).tailChase(0f).sparks(0).segments(10).play();
        Paint.light(WHITE, 0.8f).on(lineCam(tip, base.lerp(tip, 0.25))).width(width * 0.35f).time(life - 2, 2).hold(0.6f).tailChase(0f).sparks(0)
                .segments(8).play();
    }

    // ------------------------------------------------------------------ strokes

    /** One crescent cut: a main stroke and a fainter echo just inside it. */
    static void crescent(Vec3 center, Vec3 mid, Vec3 normal, double radius, double span, float width, int life, Paint p) {
        p.on(Ribbon.arc(center, mid, normal, radius, span, 0.55, 1.1)).width(width).time(life, 3).hold(0.3f).sparks(p.ink ? 0 : 1).play();
        Paint echo = p.ink ? Paint.light(p.rim, 0.45f) : Paint.light(darken(p.color, 0.2f), 0.55f);
        echo.on(Ribbon.arc(center, mid, normal, radius * (p.ink ? 1.04 : 0.82), span * 0.88, 0.45, 1.05)).width(width * (p.ink ? 1.3f : 0.55f))
                .time(life - 2, 4).hold(0.2f).sparks(0).segments(24).play();
    }

    /** A slash in front of a point, leaning toward the viewer so it never shows edge-on. side is +1 or -1 for the cut direction. */
    static void cut(Vec3 at, Vec3 facing, double radius, float width, int life, int side, Paint p) {
        Vec3 mid = flat(facing);
        Vec3 right = new Vec3(-mid.z, 0, mid.x);
        double pitch = 0.75 + rnd() * 0.25;
        double roll = side * (0.35 + rnd() * 0.35);
        Vec3 normal = rotAbout(rotAbout(UP, right, -pitch), mid, roll);
        crescent(at, mid, normal, radius, 3.4, width, life, p);
    }

    /** A ring that draws itself around a center: two half strokes chasing each other. */
    static void ring(Vec3 c, Vec3 n, double r, float width, int life, double spin, Paint p) {
        Vec3[] uv = axes(n);
        for (int k = 0; k < 2; k++) {
            Vec3 mid = uv[0].scale(Math.cos(k * Math.PI)).add(uv[1].scale(Math.sin(k * Math.PI)));
            p.on(Ribbon.arc(c, mid, n, r, Math.PI * 1.05, spin, 1.0)).width(width).time(life, Math.max(3, life / 4)).hold(0.55f).tailChase(0.3f)
                    .sparks(0).play();
        }
    }

    /** A ring that blasts outward from r0 to r1, with a soft shockwave glow under it. */
    static void shock(Vec3 c, Vec3 n, double r0, double r1, float width, int life, Paint p) {
        Vec3[] uv = axes(n);
        double grow = r1 / Math.max(0.05, r0);
        double start = rnd() * Math.PI * 2;
        for (int k = 0; k < 3; k++) {
            double a = start + k * Math.PI * 2 / 3;
            Vec3 mid = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            p.on(Ribbon.arc(c, mid, n, r0, Math.PI * 0.9, 0.5, grow)).width(width).time(life, 2).hold(0.25f).tailChase(0.25f).sparks(0).segments(20).play();
        }
        ringFlat(c, n, r0, r1 * 1.05, life + 2, p.glow(), "shockwave").envelope(0.05f, 0.25f, 0.55f);
    }

    /**
     * A magic circle on the ground: an outer ring and an inner ring turning opposite ways, with a star of straight
     * strokes between them. Lies flat, so it is seen from above as a full circle.
     */
    static void circle(Vec3 c, double r, int life, int points, Paint outer, Paint inner) {
        Vec3 at = c.add(0, 0.06, 0);
        ring(at, UP, r, (float) Math.max(0.12, r * 0.07), life, 0.9, outer);
        ring(at, UP, r * 0.62, (float) Math.max(0.08, r * 0.045), life, -1.3, inner);
        double start = rnd() * Math.PI * 2;
        int step = points % 2 == 1 ? 2 : 1;
        for (int k = 0; k < points; k++) {
            double a0 = start + Math.PI * 2 * k / points, a1 = start + Math.PI * 2 * ((k + step + (points % 2 == 0 ? 1 : 0)) % points) / points;
            Vec3 p0 = at.add(Math.cos(a0) * r * 0.62, 0.01, Math.sin(a0) * r * 0.62), p1 = at.add(Math.cos(a1) * r * 0.62, 0.01, Math.sin(a1) * r * 0.62);
            Vec3 side = p0.add(p1).scale(0.5).subtract(at);
            int delay = 2 + k;
            Signatures.at(delay, () -> inner.on(Ribbon.line(p0, p1, side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side)).width((float) Math.max(0.06, r * 0.03))
                    .time(Math.max(6, life - delay), 4).hold(0.6f).tailChase(0.2f).sparks(0).segments(16).play());
        }
        ringFlat(at, UP, r * 0.2, r * 1.05, life, outer.glow(), "ring").envelope(0.1f, 0.6f, 0.35f);
    }

    /** Straight strokes bursting out from a point in random directions, like the spikes of an impact. */
    static void rays(Vec3 c, int count, double len, float width, int life, Paint p) {
        for (int k = 0; k < n(count); k++) {
            Vec3 d = rndDir();
            Vec3 a = c.add(d.scale(len * 0.15)), b = c.add(d.scale(len * (0.6 + rnd() * 0.4)));
            p.on(lineCam(a, b)).width(width).time(life, 2).hold(0.1f).tailChase(1f).sparks(0).segments(10).play();
        }
    }

    /** Rays that only go up and out, for things hitting the ground. */
    static void raysUp(Vec3 c, int count, double len, float width, int life, Paint p) {
        for (int k = 0; k < n(count); k++) {
            Vec3 d = rndUp();
            Vec3 a = c.add(d.scale(len * 0.1)), b = c.add(d.scale(len * (0.6 + rnd() * 0.4)));
            p.on(lineCam(a, b)).width(width).time(life, 2).hold(0.1f).tailChase(1f).sparks(0).segments(10).play();
        }
    }

    /** Curved strokes spiralling in to a point, to show power gathering there. */
    static void converge(Vec3 c, double radius, int count, int life, float width, Paint p) {
        for (int k = 0; k < n(count); k++) {
            Vec3 d = rndDir();
            Vec3 bend = rndDir().cross(d).normalize();
            double r0 = radius * (0.7 + rnd() * 0.3);
            p.on(Ribbon.curve((s, time) -> {
                double t = Math.min(1, s * (0.4 + time));
                double rr = r0 * (1 - t);
                return c.add(d.scale(rr)).add(bend.scale(Math.sin(t * Math.PI) * r0 * 0.35));
            }, (s, time) -> faceCam(c.add(d.scale(r0 * (1 - s))), d))).width(width).time(life, life - 2).hold(0.1f).tailChase(1f).sparks(0).segments(14).play();
        }
    }

    /** Lightning: a jagged stroke from a to b with a thin white heart. The jags shift a little every tick. */
    static void bolt(Vec3 a, Vec3 b, float width, int life, double jag, Paint p) {
        Vec3 dv = b.subtract(a);
        double len = dv.length();
        if (len < 0.1) return;
        Vec3 dir = dv.scale(1 / len);
        Vec3[] uv = axes(dir);
        int knots = Math.max(4, (int) (len / 1.1));
        double[] ox = new double[knots + 1], oy = new double[knots + 1];
        for (int k = 1; k < knots; k++) {
            ox[k] = gauss() * jag;
            oy[k] = gauss() * jag;
        }
        Ribbon.Curve pos = (s, time) -> {
            double f = s * knots;
            int k = Math.min(knots - 1, (int) f);
            double t = f - k;
            double x = ox[k] + (ox[k + 1] - ox[k]) * t, y = oy[k] + (oy[k + 1] - oy[k]) * t;
            double flick = Math.sin(time * 40 + k) * jag * 0.12;
            return a.add(dv.scale(s)).add(uv[0].scale(x + flick)).add(uv[1].scale(y));
        };
        Ribbon.Curve side = (s, time) -> faceCam(a.add(dv.scale(s)), dir);
        p.on(Ribbon.curve(pos, side)).width(width).time(life, 2).hold(0.4f).tailChase(0.2f).sparks(0).segments(Math.min(80, knots * 6)).play();
        Paint.light(WHITE, 0.9f).on(Ribbon.curve(pos, side)).width(width * 0.4f).time(Math.max(4, life - 2), 2).hold(0.3f).tailChase(0.2f).sparks(0)
                .segments(Math.min(80, knots * 6)).play();
    }

    /** A live stroke between two moving points that sags and ripples, like a chain or a stream of blood. */
    static void tether(Supplier<Vec3> a, Supplier<Vec3> b, float width, int life, double sag, double wave, Paint p) {
        double phase = rnd() * 6.28;
        Ribbon.Curve pos = (s, time) -> {
            Vec3 pa = a.get(), pb = b.get();
            Vec3 mid = pa.lerp(pb, s);
            Vec3 d = pb.subtract(pa);
            Vec3 side = d.cross(UP);
            side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
            double hump = Math.sin(Math.PI * s);
            return mid.add(0, -sag * hump, 0).add(side.scale(Math.sin(s * 9 + time * 30 + phase) * wave * hump));
        };
        p.on(Ribbon.curve(pos, (s, time) -> {
            Vec3 pa = a.get(), pb = b.get();
            return faceCam(pa.lerp(pb, s), pb.subtract(pa).lengthSqr() < 1e-6 ? UP : pb.subtract(pa).normalize());
        })).width(width).time(life, Math.max(2, life / 5)).hold(0.6f).tailChase(0.3f).sparks(0).segments(30).play();
    }

    /** A stroke that winds up around a (possibly moving) point, like a spiral of energy around a body. */
    static void helix(Supplier<Vec3> base, double r, double h, double turns, float width, int life, double phase, Paint p) {
        p.on(Ribbon.curve((s, time) -> {
            double a = phase + s * turns * Math.PI * 2 + time * 3;
            return base.get().add(Math.cos(a) * r, s * h, Math.sin(a) * r);
        }, (s, time) -> {
            double a = phase + s * turns * Math.PI * 2 + time * 3;
            return new Vec3(Math.cos(a), 0, Math.sin(a));
        })).width(width).time(life, Math.max(3, life / 3)).hold(0.3f).tailChase(0.7f).sparks(0).play();
    }

    /** A flying stroke from a to b: a streak that races there in ticks, with a bright head. onArrive runs when it lands. */
    static void comet(Vec3 a, Vec3 b, int ticks, float width, Paint p, Runnable onArrive) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6) dir = UP;
        Vec3 d = dir.normalize();
        int life = ticks + 6;
        p.on(lineCam(a, b)).width(width).time(life, ticks).hold(0f).tailChase(1f).sparks(p.ink ? 0 : 1).play();
        Signatures.during(0, ticks, t -> {
            Vec3 h = a.lerp(b, 1 - Math.pow(1 - Math.min(1, (t + 1) / (double) ticks), 2));
            sp("glow", h).size(width * 2.4f, width * 1.4f).life(3).colors(WHITE, p.glow()).envelope(0.05f, 0.4f, 0.9f);
            if (t == ticks - 1 && onArrive != null) onArrive.run();
        });
    }

    /** A column of light or ink: strands spiralling up and a straight core. */
    static void pillar(Vec3 base, double r, double h, int life, int strands, Paint p) {
        for (int k = 0; k < strands; k++) {
            double ph = Math.PI * 2 * k / strands;
            p.on(Ribbon.spiral(base, r, r * 0.6, h, 1.2, ph, 2.5)).width((float) Math.max(0.12, r * 0.28)).time(life, Math.max(3, life / 3)).hold(0.4f)
                    .tailChase(0.6f).sparks(0).segments(30).play();
        }
        Vec3 top = base.add(0, h, 0);
        Paint.light(p.glow(), 0.8f).on(lineCam(base, top)).width((float) Math.max(0.15, r * 0.5))
                .time(life, Math.max(2, life / 4)).hold(0.5f).tailChase(0.5f).sparks(0).segments(20).play();
    }

    /** A sphere of strokes: three great circles at different tilts, growing from r0 to r1. */
    static void dome(Vec3 c, double r0, double r1, float width, int life, Paint p) {
        for (int k = 0; k < 3; k++) {
            Vec3 n = rotAbout(rotY(new Vec3(0, 0, 1), k * Math.PI / 3 + rnd()), new Vec3(1, 0, 0), k * 0.6);
            Vec3[] uv = axes(n);
            p.on(Ribbon.arc(c, uv[0], n, r0, Math.PI * 1.9, 1.2 * (k % 2 == 0 ? 1 : -1), r1 / Math.max(0.05, r0))).width(width).time(life, Math.max(3, life / 4))
                    .hold(0.3f).tailChase(0.4f).sparks(0).segments(30).play();
        }
    }

    // ------------------------------------------------------------------ soft pieces

    static void glow(Vec3 at, float size, int life, int col) {
        bloom(at, size, life, col);
    }

    /** A soft cloud of smoke or mist. alpha is how solid it is. */
    static void mist(Vec3 at, double spread, int count, int col, float alpha, float size, int life) {
        for (int k = 0; k < n(count); k++) {
            Vec3 p = at.add(gauss() * spread, gauss() * spread * 0.5, gauss() * spread);
            sp("smoke", p).size(size * 0.6f, size * 1.4f).life(life + (int) (rnd() * 8)).colors(lighten(col, 0.2f), col)
                    .vel(gauss() * 0.01, 0.01 + rnd() * 0.01, gauss() * 0.01).spin((float) (gauss() * 0.03)).envelope(0.15f, 0.5f, alpha);
        }
    }

    /** Tiny hot specks thrown out of a point. */
    static void embers(Vec3 at, int count, double speed, int col) {
        for (int k = 0; k < n(count); k++) {
            sp("ember", at).size(0.05f, 0.01f).life(10 + (int) (rnd() * 12)).vel(rndDir().scale(speed * (0.4 + rnd()))).drag(0.88f).grav(0.004f)
                    .colors(lighten(col, 0.6f), col).envelope(0.05f, 0.4f, 1f);
        }
    }

    /** Thin bright sparks that streak in the direction they fly. */
    static void sparks(Vec3 at, int count, double speed, int col) {
        for (int k = 0; k < n(count); k++) {
            Vec3 v = rndDir().scale(speed * (0.5 + rnd()));
            sp("streak", at).size(0.25f, 0.05f).life(6 + (int) (rnd() * 6)).vel(v).axial().drag(0.85f).colors(WHITE, col).envelope(0.05f, 0.3f, 1f);
        }
    }

    /** A flat soft disk of light on the ground, for areas that stay a while. */
    static void pool(Vec3 c, double r, int life, int col, float alpha) {
        sp("glow", c.add(0, 0.05, 0)).size((float) r * 1.2f, (float) r * 1.25f).life(life).colors(col, darken(col, 0.3f)).facing(0, 1, 0)
                .envelope(0.1f, 0.75f, alpha);
    }
}
