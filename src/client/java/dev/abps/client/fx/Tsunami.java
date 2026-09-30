package dev.abps.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

import static dev.abps.client.fx.FxKit.*;

/**
 * A tsunami built as a real three dimensional surface. The cross section of a breaking wave (a long back slope, a
 * rounded crest, a lip that curls forward over a hollow barrel, and a foamy toe) is worked out as a curve; every
 * block of the wave's width gets its own copy of that curve with a slightly different height and curl, and the curve
 * is tiled with water-textured quads that are turned to lie along it. Seen from the front you look into the barrel
 * under the lip, from behind you see the back slope rise. Spray, foam and mist come off the lip and toe.
 */
final class Tsunami {

    private static final Map<Integer, Wave> WAVES = new HashMap<>();

    private Tsunami() {
    }

    static void start(double[] d, int[] i) {
        Wave w = new Wave(vec(d, 0), new Vec3(d[3], 0, d[5]), d[6], i[0], i[1]);
        WAVES.put(i[2], w);
        FxSystem.add(w);
    }

    static void stop(int id) {
        Wave w = WAVES.remove(id);
        if (w != null) w.stopping = true;
    }

    /** Points along the wave's cross section, from the far back of the slope, over the crest and into the barrel. */
    private static final class Profile {
        final double[] f = new double[48];
        final double[] y = new double[48];
        /** 0 for the outside of the wave, 1 for the inside of the barrel. */
        final int[] side = new int[48];
        int n;
        int tip;
        int top;

        void add(double pf, double py, int s) {
            f[n] = pf;
            y[n] = py;
            side[n] = s;
            n++;
        }
    }

    /**
     * @param curl 0 is a plain rising swell, 1 a fully curled barrel, above 1 the lip falls and the tube collapses
     * @param step degrees between samples on the curved parts
     */
    private static Profile profile(double curl, double step) {
        Profile p = new Profile();
        double fc = 0.30 + 0.10 * Math.min(1, curl), yc = 0.44, r = 0.44, ro = r + 0.11;
        double thetaEnd = Math.toRadians(Mth.lerp(curl, 140, 22));
        double thetaStart = Math.toRadians(165);
        // Back slope: a smooth S-curve from far behind up to where the outer arc begins
        double sx = fc + ro * Math.cos(thetaStart), sy = yc + ro * Math.sin(thetaStart);
        double[] px = {-2.4, -1.3, -0.8, sx};
        double[] py = {0.0, 0.03, 0.30, sy};
        for (int k = 0; k <= 6; k++) {
            double t = k / 6.0, u = 1 - t;
            p.add(u * u * u * px[0] + 3 * u * u * t * px[1] + 3 * u * t * t * px[2] + t * t * t * px[3],
                    u * u * u * py[0] + 3 * u * u * t * py[1] + 3 * u * t * t * py[2] + t * t * t * py[3], 0);
        }
        // Outer arc over the top to the lip
        double stepRad = Math.toRadians(step);
        if (thetaEnd < thetaStart) {
            int steps = Math.max(1, (int) Math.ceil((thetaStart - thetaEnd) / stepRad));
            for (int k = 1; k <= steps; k++) {
                double th = thetaStart + (thetaEnd - thetaStart) * k / steps;
                p.add(fc + ro * Math.cos(th), yc + ro * Math.sin(th), 0);
                if (Math.abs(th - Math.PI / 2) < stepRad) p.top = p.n - 1;
            }
        }
        p.tip = p.n - 1;
        // Inside of the barrel: back from the lip, up over the ceiling, down the far wall, to the floor
        double end = Math.toRadians(270);
        int steps = Math.max(2, (int) Math.ceil((end - thetaEnd) / stepRad));
        for (int k = 0; k <= steps; k++) {
            double th = thetaEnd + (end - thetaEnd) * k / steps;
            p.add(fc + r * Math.cos(th), Math.max(0, yc + r * Math.sin(th)), 1);
        }
        // The toe: water spilling out along the ground ahead of the wave
        p.add(fc + 0.30, 0.0, 1);
        p.add(fc + 0.62, 0.0, 1);
        return p;
    }

    private static final class Wave implements FxSystem.Emitter {
        final Vec3 origin, dir, lateral;
        final double height;
        final int columns, col;
        final int deep, mid, light;
        int age;
        int collapse = -1;
        boolean stopping;

        Wave(Vec3 origin, Vec3 dir, double height, int columns, int col) {
            this.origin = origin;
            this.dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
            this.lateral = new Vec3(-this.dir.z, 0, this.dir.x);
            this.height = height;
            this.columns = columns;
            this.col = col;
            this.deep = mix(col, 0x021c3a, 0.62f);
            this.mid = mix(col, 0x0f6fb0, 0.35f);
            this.light = lighten(mix(col, 0x35c6e8, 0.4f), 0.45f);
        }

        int surfaceColor(Profile p, int k, double heightNorm) {
            double h = Math.max(0, Math.min(1, heightNorm));
            if (p.side[k] == 0) {
                double t = k / (double) Math.max(1, p.tip);
                int c = mix(mid, light, (float) (Math.pow(h, 1.4) * 0.8));
                return mix(c, 0xE8FBFF, (float) Math.max(0, (t - 0.72) * 1.6));
            }
            int c = mix(deep, mid, (float) Math.pow(h, 0.9));
            c = mix(c, light, (float) Math.max(0, (h - 0.55) * 1.7));
            int since = k - p.tip;
            return mix(c, 0xE8FBFF, since <= 2 ? 0.55f - since * 0.15f : 0f);
        }

        @Override
        public boolean tick(ClientLevel level) {
            boolean collapsing = collapse >= 0;
            if ((stopping || age > 260) && !collapsing) {
                collapse = 0;
                collapsing = true;
            }
            double cf = collapsing ? Math.min(1.0, collapse / 9.0) : 0;
            Vec3 center = origin.add(dir.scale(age * 0.5));
            double growth = Math.min(1.0, (age / 2.0 + 2.0) / 7.0) * (1.0 - 0.9 * cf * cf);
            double curlBase = Math.min(1.0, age / 20.0) * 0.92 + cf * 0.62;
            boolean fine = FxSystem.density() >= 1f;
            double latStep = fine ? 1.0 : 1.5;
            int cols = (int) Math.max(3, Math.round(columns / latStep));
            double angleStep = fine ? 21 : 30;

            if (age % 2 == 0) emitSurface(center, growth, curlBase, cols, latStep, angleStep, cf);
            emitSpray(center, growth, curlBase, cols, latStep, cf);

            if (age == 0) {
                ringFlat(origin.add(0, 0.08, 0), new Vec3(0, 1, 0), 1, columns * 0.55, 14, lighten(col, 0.5f), "shockwave");
                for (int k = 0; k < n(40); k++) {
                    Vec3 at = origin.add(lateral.scale((rnd() - 0.5) * columns)).add(dir.scale(rnd() * 2));
                    FxParticle f = sp("foam", at.x, at.y + 0.3, at.z);
                    if (f != null) f.size(0.9f, 2.4f).life(18).colors(0xFFFFFF, 0xBFEFFF).vel(gauss() * 0.05, 0.10 + rnd() * 0.15, gauss() * 0.05).envelope(0.1f, 0.5f, 0.85f);
                }
            }
            if (collapsing) {
                collapse++;
                if (collapse >= 10) {
                    crash(center);
                    return false;
                }
            }
            age++;
            return true;
        }

        /** One copy of the wave's water surface, valid for a few ticks and replaced by the next. */
        private void emitSurface(Vec3 center, double growth, double curlBase, int cols, double latStep, double angleStep, double cf) {
            Vec3 up = new Vec3(0, 1, 0);
            Vector3f lat = new Vector3f((float) lateral.x, 0, (float) lateral.z);
            double half = (cols - 1) / 2.0;
            for (int j = 0; j < cols; j++) {
                double lo = (j - half) * latStep;
                double edge = 1.0 - 0.55 * Math.pow(lo / (columns / 2.0 + 0.5), 2);
                double h = height * growth * edge * (0.86 + 0.14 * Math.sin(age * 0.23 + j * 0.9));
                if (h < 0.25) continue;
                double curl = Mth.clamp(curlBase * (0.78 + 0.22 * Math.sin(j * 0.75 + age * 0.05)), 0, 1.7);
                Profile pr = profile(curl, angleStep);
                double lead = -0.03 * lo * lo;
                Vec3 col0 = center.add(lateral.scale(lo)).add(dir.scale(lead));
                double fscale = 0.72;
                for (int k = 0; k < pr.n; k++) {
                    int k0 = Math.max(0, k - 1), k1 = Math.min(pr.n - 1, k + 1);
                    double tf = (pr.f[k1] - pr.f[k0]) * fscale, ty = pr.y[k1] - pr.y[k0];
                    double tl = Math.hypot(tf, ty);
                    if (tl < 1.0e-6) continue;
                    double seg = tl * h * 0.5;
                    Vector3f tan = new Vector3f((float) (dir.x * tf / tl), (float) (ty / tl), (float) (dir.z * tf / tl));
                    Vector3f nrm = new Vector3f(lat).cross(tan).normalize();
                    Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(lat, tan, nrm));
                    Vec3 at = col0.add(dir.scale(pr.f[k] * h * fscale)).add(up.scale(pr.y[k] * h));
                    float size = (float) (Math.max(latStep * 0.95, Math.min(seg * 0.9, 2.0)) * 0.78 + 0.35);
                    int c = surfaceColor(pr, k, pr.y[k]);
                    FxParticle w = spForce("watersheet", at.x, at.y, at.z);
                    if (w != null) {
                        w.size(size, size).life(4).colors(c, c).vel(dir.x * 0.5, 0, dir.z * 0.5).drag(1f).orient(q)
                                .envelope(0.34f, 0.55f, pr.side[k] == 1 ? 0.9f : 0.82f).bright();
                    }
                    // Bright glints crawling up the inside of the barrel
                    if (pr.side[k] == 1 && k > pr.tip + 1 && rnd() < 0.10) {
                        FxParticle s = sp("streak", at.x, at.y, at.z);
                        if (s != null) {
                            s.size(0.8f, 0.6f).life(5).colors(0xFFFFFF, light).vel(dir.x * 0.5 + tan.x * 0.25, tan.y * 0.25, dir.z * 0.5 + tan.z * 0.25)
                                    .drag(1f).axial().envelope(0.2f, 0.4f, 0.55f);
                        }
                    }
                }
            }
        }

        /** Foam, spray and mist every tick, so the lip looks like it is really tearing water off. */
        private void emitSpray(Vec3 center, double growth, double curlBase, int cols, double latStep, double cf) {
            double half = (cols - 1) / 2.0;
            for (int j = 0; j < cols; j++) {
                double lo = (j - half) * latStep;
                double edge = 1.0 - 0.55 * Math.pow(lo / (columns / 2.0 + 0.5), 2);
                double h = height * growth * edge * (0.86 + 0.14 * Math.sin(age * 0.23 + j * 0.9));
                if (h < 0.4) continue;
                double curl = Mth.clamp(curlBase * (0.78 + 0.22 * Math.sin(j * 0.75 + age * 0.05)), 0, 1.7);
                double lead = -0.03 * lo * lo;
                Vec3 col0 = center.add(lateral.scale(lo)).add(dir.scale(lead));
                double fc = 0.30 + 0.10 * Math.min(1, curl);
                double thetaEnd = Math.toRadians(Mth.lerp(curl, 140, 22));
                double ro = 0.55;
                double lf = (fc + ro * Math.cos(thetaEnd)) * h * 0.72, ly = (0.44 + ro * Math.sin(thetaEnd)) * h;
                Vec3 lip = col0.add(dir.scale(lf)).add(0, ly, 0);
                Vec3 crest = col0.add(dir.scale((fc + 0.02) * h * 0.72)).add(0, (0.44 + ro) * h, 0);
                double fall = 1 + cf * 1.6;

                // Foam torn off the lip
                if (rnd() < 0.75) {
                    FxParticle f = sp("foam", lip.x + gauss() * 0.3, lip.y + gauss() * 0.15, lip.z + gauss() * 0.3);
                    if (f != null) f.size(0.55f, 1.5f).life(9).colors(0xFFFFFF, 0xCFF4FF).vel(dir.x * 0.5 + gauss() * 0.03, 0.03 - cf * 0.05, dir.z * 0.5 + gauss() * 0.03)
                            .drag(0.94f).envelope(0.12f, 0.5f, 0.9f).spin((float) gauss() * 0.05f);
                }
                // Foam laid along the crest
                if (rnd() < 0.55) {
                    FxParticle f = sp("foam", crest.x + gauss() * 0.4, crest.y + 0.1, crest.z + gauss() * 0.4);
                    if (f != null) f.size(0.7f, 1.6f).life(8).colors(0xFFFFFF, 0xD8F7FF).vel(dir.x * 0.5, 0.02, dir.z * 0.5).drag(1f).envelope(0.15f, 0.5f, 0.75f);
                }
                // Drops thrown forward and up
                int drops = (rnd() < 0.6 ? 1 : 0) + (cf > 0 ? 2 : 0);
                for (int k = 0; k < drops; k++) {
                    FxParticle s = sp("droplet", lip.x + gauss() * 0.3, lip.y, lip.z + gauss() * 0.3);
                    if (s != null) {
                        double fw = 0.55 + rnd() * 0.35 * fall;
                        s.size(0.16f + (float) rnd() * 0.1f, 0.12f).life(14 + (int) (rnd() * 10)).colors(0xFFFFFF, 0xBFEFFF)
                                .vel(dir.x * fw + gauss() * 0.05, 0.04 + rnd() * 0.26, dir.z * fw + gauss() * 0.05).grav(0.6f).drag(0.985f)
                                .envelope(0.03f, 0.7f, 0.95f).startRoll((float) (rnd() * 0.5));
                    }
                }
                // A glint or two on the lip
                if (rnd() < 0.25) {
                    FxParticle s = sp("spark", lip.x, lip.y, lip.z);
                    if (s != null) s.size(0.5f, 0.05f).life(6).colors(0xFFFFFF, light).vel(dir.x * 0.45, 0, dir.z * 0.45).drag(1f).spin(0.15f);
                }
                // Boiling white water at the toe and mist above it
                Vec3 toe = col0.add(dir.scale((fc + 0.55) * h * 0.72)).add(0, 0.1, 0);
                if (rnd() < 0.6) {
                    FxParticle f = sp("foam", toe.x + gauss() * 0.4, toe.y + rnd() * 0.5, toe.z + gauss() * 0.4);
                    if (f != null) f.size(1.0f, 2.4f).life(12).colors(0xFFFFFF, 0xBFEFFF).vel(dir.x * 0.3, 0.02, dir.z * 0.3).drag(0.96f).envelope(0.15f, 0.5f, 0.85f);
                }
                if (rnd() < 0.3) {
                    Vec3 m = col0.add(dir.scale(h * 0.5)).add(0, h * (0.3 + rnd() * 0.5), 0);
                    FxParticle f = sp("smoke", m.x + gauss() * 0.4, m.y, m.z + gauss() * 0.4);
                    if (f != null) f.size(1.5f, 3.4f).life(16).colors(0xE6F8FF, 0xFFFFFF).vel(dir.x * 0.3, 0.02, dir.z * 0.3).envelope(0.2f, 0.4f, 0.35f);
                }
                // Wet trail left on the ground behind
                if (age % 3 == 0 && rnd() < 0.5) {
                    Vec3 tr = col0.subtract(dir.scale(h * (0.4 + rnd() * 1.4)));
                    FxParticle f = sp("foam", tr.x, origin.y + 0.15, tr.z);
                    if (f != null) f.size(0.9f, 1.8f).life(22).colors(0xFFFFFF, 0xBFEFFF).vel(0, 0, 0).envelope(0.15f, 0.4f, 0.55f).facing(0, 1, 0);
                }
            }
        }

        /** The wave hits the end of its run: a wall of white water blasts outward. */
        private void crash(Vec3 at) {
            Vec3 g = new Vec3(at.x, origin.y, at.z);
            ringFlat(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 1, Math.max(7, height * 2.0), 18, 0xFFFFFF, "shockwave");
            ringFlat(g.add(0, 0.12, 0), new Vec3(0, 1, 0), 1, Math.max(5, height * 1.3), 14, lighten(col, 0.5f), "ring");
            bloom(g.add(0, 1.6, 0), (float) height * 0.5f, 12, col);
            for (int k = 0; k < n(70); k++) {
                double a = rnd() * Math.PI * 2, sp = 0.12 + rnd() * 0.5;
                FxParticle s = sp("droplet", g.x, g.y + 0.5, g.z);
                if (s != null) s.size(0.18f + (float) rnd() * 0.12f, 0.12f).life(20 + (int) (rnd() * 14)).colors(0xFFFFFF, 0xBFEFFF)
                        .vel(Math.cos(a) * sp, 0.25 + rnd() * 0.55, Math.sin(a) * sp).grav(0.6f).drag(0.985f).envelope(0.03f, 0.7f, 0.95f);
            }
            for (int k = 0; k < n(40); k++) {
                double a = rnd() * Math.PI * 2, sp = 0.05 + rnd() * 0.25;
                FxParticle f = sp("foam", g.x + Math.cos(a) * rnd() * 3, g.y + 0.4 + rnd() * 1.2, g.z + Math.sin(a) * rnd() * 3);
                if (f != null) f.size(1.2f, 3.6f).life(16 + (int) (rnd() * 8)).colors(0xFFFFFF, 0xBFEFFF)
                        .vel(Math.cos(a) * sp, 0.03 + rnd() * 0.08, Math.sin(a) * sp).drag(0.95f).envelope(0.1f, 0.5f, 0.85f).spin((float) gauss() * 0.04f);
            }
            for (int k = 0; k < n(16); k++) {
                double a = rnd() * Math.PI * 2, r = rnd() * 4;
                FxParticle f = sp("smoke", g.x + Math.cos(a) * r, g.y + 0.6 + rnd() * 2.5, g.z + Math.sin(a) * r);
                if (f != null) f.size(2.0f, 4.6f).life(22).colors(0xE6F8FF, 0xFFFFFF).vel(Math.cos(a) * 0.06, 0.05, Math.sin(a) * 0.06).envelope(0.2f, 0.4f, 0.4f);
            }
        }
    }
}
