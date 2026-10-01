package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Samurai: brush strokes of black ink with a white edge, and a few in blood red. Clean, fast cuts. */
final class SigSamurai {

    private SigSamurai() {
    }

    static final int STEEL = 0xF2F4F8, RED = 0xFF1744;
    static final Paint INK = Paint.ink(0x0A0A0E, 0xFFFFFF);
    static final Paint RED_INK = Paint.ink(0x1A0004, 0xFF2A4A);
    static final Paint HALO = Paint.light(0xE8E8F0, 0.55f);
    static final Paint EDGE = Paint.light(STEEL, 1.1f);

    static final int CUE_CRIT = 11, CUE_PARRY = 12, CUE_IAIDO_HIT = 13, CUE_COUNTER = 14, CUE_CUT = 15, CUE_FINAL = 16;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> iaido(c);
            case 2 -> crescent(c);
            case 3 -> stance(c);
            case 4 -> focus(c);
            case 6 -> flurry(c);
            case CUE_CRIT -> cut(target(c), c.look, 1.3, 0.3f, rnd() < 0.5 ? 1 : -1, false);
            case CUE_PARRY -> parry(c);
            case CUE_IAIDO_HIT -> cross(target(c), c.look, 1.4, false);
            case CUE_COUNTER -> counter(c);
            case CUE_CUT -> cut(target(c), rotY(c.look, gauss() * 0.9), 1.4, 0.32f, c.ticks % 2 == 0 ? 1 : -1, c.ticks == 2);
            case CUE_FINAL -> finale(c);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    static Vec3 target(Ctx c) {
        Entity t = c.target();
        return t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.55, 0);
    }

    /** One brush stroke crescent: a soft white halo behind dark ink with a white edge. */
    static void stroke(Vec3 center, Vec3 mid, Vec3 normal, double r, double span, float w, int life, boolean red) {
        HALO.on(Ribbon.arc(center, mid, normal, r * 1.02, span, 0.6, 1.08)).width(w * 2.1f).time(life, 3).hold(0.3f).sparks(0).segments(28).play();
        (red ? RED_INK : INK).on(Ribbon.arc(center, mid, normal, r, span, 0.6, 1.08)).width(w * 1.7f).time(life, 3).hold(0.3f).sparks(0).play();
    }

    /** A cut across a point at a random tilt, leaning toward the viewer so it is never edge-on. */
    static void cut(Vec3 at, Vec3 facing, double r, float w, int side, boolean red) {
        Vec3 view = Brush.camera().subtract(at);
        Vec3 mid = Brush.flat(facing);
        Vec3 n = view.lengthSqr() < 1e-6 ? Brush.UP : view.normalize();
        n = rotAbout(n, mid, side * (0.3 + rnd() * 0.5));
        Vec3 inPlane = mid.subtract(n.scale(mid.dot(n)));
        if (inPlane.lengthSqr() < 1e-6) inPlane = axes(n)[0];
        stroke(at.subtract(inPlane.normalize().scale(r * 0.55)), inPlane.normalize(), n, r, 2.6, w, 14, red);
        if (red) Brush.glow(at, 0.8f, 6, RED);
        sp("spark", at).size(0.4f, 0.05f).life(5).colors(WHITE, STEEL).envelope(0.05f, 0.3f, 1f);
    }

    /** Two quick straight strokes crossing over a point. */
    static void cross(Vec3 at, Vec3 facing, double r, boolean red) {
        Vec3 view = Brush.camera().subtract(at);
        Vec3 n = view.lengthSqr() < 1e-6 ? Brush.UP : view.normalize();
        Vec3[] uv = axes(n);
        for (int k = 0; k < 2; k++) {
            double a = (k == 0 ? 0.8 : -0.8) + gauss() * 0.15;
            Vec3 d = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            Vec3 p0 = at.subtract(d.scale(r)), p1 = at.add(d.scale(r));
            int kk = k;
            at(k * 2, () -> {
                HALO.on(Brush.lineCam(p0, p1)).width(0.42f).time(12, 2).hold(0.3f).tailChase(0.6f).sparks(0).segments(14).play();
                (red || kk == 1 ? RED_INK : INK).on(Brush.lineCam(p0, p1)).width(0.34f).time(12, 2).hold(0.3f).tailChase(0.6f).sparks(0).segments(14).play();
            });
        }
        Brush.glow(at, 0.8f, 6, red ? RED : STEEL);
    }

    // ------------------------------------------------------------------ abilities

    /** Iaido: one long stroke of ink along the dash, then a white flash where the blade was drawn. */
    private static void iaido(Ctx c) {
        Vec3 dir = c.flat();
        Vec3 a = c.pos.add(0, 1.0, 0), b = c.aim.add(0, 1.0, 0);
        HALO.on(Brush.lineCam(a, b)).width(0.6f).time(16, 3).hold(0.5f).tailChase(0.8f).sparks(0).play();
        INK.on(Brush.lineCam(a, b)).width(0.45f).time(16, 3).hold(0.5f).tailChase(0.8f).sparks(0).play();
        // Speed lines along the path
        for (int k = 0; k < 5; k++) {
            Vec3 off = new Vec3(gauss() * 0.4, gauss() * 0.5, gauss() * 0.4);
            EDGE.on(Brush.lineCam(a.add(off), b.add(off))).width(0.04f).time(8, 2).hold(0f).tailChase(1f).sparks(0).segments(12).play();
        }
        at(9, () -> {
            EDGE.on(Brush.lineCam(a.add(dir.scale(-0.5)), b.add(dir.scale(0.5)))).width(0.12f).time(10, 2).hold(0.2f).tailChase(1f).sparks(0).play();
            Brush.glow(b, 0.9f, 6, STEEL);
        });
    }

    /** Crescent Moon: a huge crescent of ink flies forward and fades out at the end of its flight. */
    private static void crescent(Ctx c) {
        Vec3 dir = c.flat();
        Vec3 start = c.pos.add(0, 1.0, 0);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        // The swing plane stands upright, tilted a little, and the whole stroke rides forward over its life
        Vec3 normal = rotAbout(side, dir, 0.35).normalize();
        Vec3 up = dir.cross(normal).normalize();
        double r = 2.8, span = 2.7;
        int life = 16;
        for (int layer = 0; layer < 2; layer++) {
            boolean halo = layer == 0;
            Paint p = halo ? HALO : INK;
            p.on((s, time, out) -> {
                Vec3 center = start.add(dir.scale(14 * Math.min(1, time * 1.15) - r * 0.6));
                double a = -span / 2 + s * span;
                Vec3 radial = dir.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                out[0] = center.add(radial.scale(halo ? r * 1.03 : r));
                out[1] = dir.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
                out[2] = radial;
            }).width(halo ? 1.0f : 0.8f).time(life, 2).hold(0.65f).tailChase(0.2f).sparks(0).segments(30).play();
        }
        Brush.cut(start, dir, 1.6, 0.3f, 10, 1, INK);
        during(0, 12, t -> {
            Vec3 p = start.add(dir.scale(14 * Math.min(1, (t + 1) / (life / 1.15))));
            sp("streak", p.add(up.scale(gauss() * 1.2))).size(0.5f, 0.06f).life(6).vel(dir.scale(0.05)).axial().colors(WHITE, STEEL).envelope(0.05f, 0.3f, 0.8f);
        });
    }

    /** Counter Stance: a still ring of ink in front of you, waiting. */
    private static void stance(Ctx c) {
        Vec3 front = c.chest().add(c.flat().scale(0.8)).add(0, 0.2, 0);
        int life = Math.max(20, c.ticks);
        Brush.ring(front, c.flat(), 0.8, 0.12f, life, 0.4, INK);
        Brush.ring(c.pos.add(0, 0.06, 0), Brush.UP, 1.4, 0.08f, life, -0.4, INK);
        sp("spark", front).size(0.5f, 0.05f).life(6).colors(WHITE, STEEL).envelope(0.05f, 0.3f, 1f);
    }

    /** Focus: a quiet ring at your feet and a thin white wind spiralling up you every so often. */
    private static void focus(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        int life = Math.max(60, c.ticks);
        Brush.ring(c.pos.add(0, 0.06, 0), Brush.UP, 1.6, 0.14f, 20, 1, INK);
        Brush.helix(feet, 0.7, 2.1, 1.5, 0.08f, 16, 0, EDGE);
        Brush.glow(c.eye(), 0.5f, 10, RED);
        during(10, life, t -> {
            if (t % 25 == 0) Brush.helix(feet, 0.6, 2.0, 1.2, 0.05f, 16, t * 0.5, EDGE);
        });
    }

    /** Thousand Cuts: you vanish in a burst of ink. The server sends every cut and the final stroke as cues. */
    private static void flurry(Ctx c) {
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.3, 3, 0.2f, 10, INK);
        Brush.rays(c.chest(), 10, 2, 0.08f, 8, INK);
        Brush.glow(c.chest(), 1.4f, 8, RED);
        for (int k = 0; k < 8; k++) {
            Vec3 p = c.chest().add(gauss() * 0.4, gauss() * 0.6, gauss() * 0.4);
            sp("smoke", p).size(0.4f, 0.9f).life(16).colors(0x15151A, 0x050508).vel(gauss() * 0.02, 0.02, gauss() * 0.02).envelope(0.05f, 0.4f, 0.7f);
        }
    }

    // ------------------------------------------------------------------ cues

    private static void parry(Ctx c) {
        Vec3 at = c.pos;
        Brush.sparks(at, 12, 0.35, STEEL);
        Brush.glow(at, 0.8f, 5, STEEL);
        Brush.shock(at, c.look, 0.1, 1.0, 0.07f, 7, EDGE);
    }

    private static void counter(Ctx c) {
        Vec3 t = target(c);
        Brush.comet(c.pos, t, 2, 0.1f, EDGE, null);
        at(2, () -> {
            cut(t, c.look, 1.8, 0.42f, 1, true);
            Brush.rays(t, 6, 1.6, 0.07f, 7, RED_INK);
        });
    }

    private static void finale(Ctx c) {
        Vec3 t = target(c);
        Vec3 side = Brush.flat(c.look);
        Vec3 normal = Brush.UP;
        stroke(t.subtract(side.scale(1.4)), side, normal, 2.6, 2.4, 0.5f, 18, true);
        cross(t, c.look, 1.6, true);
        Brush.rays(t, 10, 2.4, 0.09f, 10, RED_INK);
        Brush.glow(t, 1.6f, 10, RED);
    }
}
