import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Makes the textures for ribbon effects (slashes and sweeping trails). A ribbon is one long strip drawn along a
 * curve, so these are wide strips: u runs along the stroke (0 = tail, 1 = head), v runs across it (0 = outer edge).
 *
 *   ribbon      streaky energy, light stored in RGB for additive blending, tinted at runtime
 *   ribboncore  the thin white-hot line along the outer edge
 *   ink         a brush stroke with ragged bristle edges, stored in alpha for normal blending
 *
 * Each comes in 5 frames that eat away more and more of the stroke, so a ribbon dissolves instead of just fading.
 * Run from the repository root:  java tools/GenRibbonTextures.java
 */
public class GenRibbonTextures {

    static final int W = 256, H = 64, FRAMES = 5;
    static final double[] ERODE = {0.0, 0.14, 0.27, 0.4, 0.55};
    static final Path TEX = Path.of("src/client/resources/assets/abpsmod/textures/particle");
    static final Path JSON = Path.of("src/client/resources/assets/abpsmod/particles");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(TEX);
        Files.createDirectories(JSON);
        for (int frame = 0; frame < FRAMES; frame++) {
            final int f = frame;
            write("ribbon", f, (u, v) -> light(ribbon(u, v, f)));
            write("ribboncore", f, (u, v) -> light(core(u, v, f)));
            write("ink", f, (u, v) -> ink(u, v, f));
            write("ribbonmask", f, (u, v) -> mask(ribbon(u, v, f)));
        }
        for (String n : new String[]{"ribbon", "ribboncore", "ink", "ribbonmask"}) {
            StringBuilder sb = new StringBuilder("{\n  \"textures\": [\n");
            for (int f = 0; f < FRAMES; f++) {
                sb.append("    \"abpsmod:fx_").append(n).append('_').append(f).append('"').append(f < FRAMES - 1 ? ",\n" : "\n");
            }
            sb.append("  ]\n}\n");
            Files.writeString(JSON.resolve("fx_" + n + ".json"), sb.toString());
        }
        System.out.println("Wrote ribbon, ribboncore and ink textures");
    }

    interface Pixel {
        int argb(double u, double v);
    }

    static void write(String name, int frame, Pixel px) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, px.argb((x + 0.5) / W, (y + 0.5) / H));
            }
        }
        ImageIO.write(img, "png", new File(TEX.resolve("fx_" + name + "_" + frame + ".png").toString()));
    }

    // ---- The three strokes ----

    /** Streaks of energy flowing along the stroke. Brightest near the outer edge, thin at the tail. */
    static double ribbon(double u, double v, int frame) {
        double edge = warp(u, 1) * 0.05;
        double inner = 0.14 + thickness(u);
        double body = smooth(0.0, 0.1, v - edge) * smooth(inner, inner - 0.2, v);
        // Ridged noise stretched along the stroke gives thin bright fibres, like flowing liquid light
        double n = fbm(u * 2.6, v * 16 + warp(u, 2) * 5, 3);
        double ridge = 1 - Math.abs(2 * n - 1);
        double streak = 0.12 + 1.3 * Math.pow(ridge, 3.2) + 0.25 * fbm(u * 9, v * 40, 2);
        double rim = Math.exp(-sq((v - 0.2 - edge) / 0.09)) * 0.8;
        double i = (body * streak * 0.8 + rim) * along(u);
        return i * erosion(u, v, frame);
    }

    /** The hot line along the outer edge. */
    static double core(double u, double v, int frame) {
        double edge = warp(u, 1) * 0.05;
        double line = Math.exp(-sq((v - 0.2 - edge) / 0.035));
        double flicker = 0.7 + 0.3 * fbm(u * 9, v * 6, 2);
        return line * flicker * along(u) * erosion(u, v, frame);
    }

    /** A dry brush stroke: solid in the middle, bristle streaks and ragged edges, gaps near the tail. */
    static int ink(double u, double v, int frame) {
        double outer = 0.16 + (fbm(u * 10, 3.1, 2) - 0.5) * 0.08;
        double inner = outer + thickness(u) + (fbm(u * 7, 8.7, 2) - 0.5) * 0.08;
        double a = smooth(outer, outer + 0.03, v) * smooth(inner, inner - 0.08, v);
        double bristle = fbm(u * 1.5, v * 70, 2);
        a *= 0.72 + 0.28 * bristle;
        // Dry-brush gaps: the stroke breaks up into bristle lines at the tail
        double dry = smooth(0.05, 0.55, u + (bristle - 0.5) * 0.5);
        a *= dry;
        a *= smooth(0.0, 0.06, u) * smooth(1.0, 0.94, u);
        a *= erosion(u, v, frame);
        int alpha = clamp((int) Math.round(a * 255));
        int shade = clamp(200 + (int) (bristle * 55)); // near white, tinted dark at runtime
        return (alpha << 24) | (shade << 16) | (shade << 8) | shade;
    }

    /** How far the stroke reaches in from the outer edge: a crescent, needle thin at both ends, fullest past the middle. */
    static double thickness(double u) {
        double b = Math.pow(Math.sin(Math.PI * Math.pow(Math.max(0, Math.min(1, u)), 0.8)), 0.65);
        return 0.05 + 0.75 * b * (0.55 + 0.45 * u);
    }

    /** Thin at the tail, full at the head, with a short soft point at the very front. */
    static double along(double u) {
        return smooth(0.0, 0.45, u) * smooth(1.0, 0.93, u) * (0.6 + 0.4 * u);
    }

    /** Eats the stroke from the tail forward, breaking it into noisy pieces. */
    static double erosion(double u, double v, int frame) {
        double t = ERODE[frame];
        if (t <= 0) return 1;
        double n = fbm(u * 7, v * 9, 3) * 0.7 + u * 0.3; // the tail goes first
        return smooth(t, t + 0.12, n);
    }

    /** The same shape as a light texture, but in alpha, so it can tint what is behind it (for daylight). */
    static int mask(double i) {
        int a = clamp((int) Math.round(Math.pow(Math.min(1, i), 0.8) * 255));
        return (a << 24) | 0xFFFFFF;
    }

    static int light(double i) {
        int c = clamp((int) Math.round(Math.min(1, i) * 255));
        return 0xFF000000 | (c << 16) | (c << 8) | c;
    }

    // ---- Noise ----

    static double hash(int x, int y) {
        long h = x * 374761393L + y * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    static double noise(double x, double y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        double a = hash(x0, y0), b = hash(x0 + 1, y0), c = hash(x0, y0 + 1), d = hash(x0 + 1, y0 + 1);
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
    }

    static double fbm(double x, double y, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += noise(x, y) * amp;
            norm += amp;
            x *= 2.03;
            y *= 2.03;
            amp *= 0.5;
        }
        return sum / norm;
    }

    static double warp(double u, int seed) {
        return fbm(u * 4 + seed * 17.3, seed * 5.1, 2) - 0.5;
    }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double sq(double x) {
        return x * x;
    }

    static int clamp(int c) {
        return Math.max(0, Math.min(255, c));
    }
}
