package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Assassin: black ink strokes with a pale violet rim, quick cuts and puffs of shadow. */
final class SigAssassin {

    private SigAssassin() {
    }

    static final int LILAC = 0xD7C4FF, PURPLE = 0x7C4DFF;
    static final Paint INK = Paint.ink(0x0C0A12, 0xE6DAFF);
    static final Paint HALO = Paint.light(0xC9B8F0, 0.5f);
    static final Paint EDGE = Paint.light(LILAC, 1.0f);

    static final int CUE_UNVANISH = 11, CUE_CUT = 12;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> vanish(c);
            case 2 -> dash(c);
            case 3 -> smoke(c);
            case 4 -> step(c);
            case 6 -> ult(c);
            case CUE_UNVANISH -> puff(c.pos.add(0, 1, 0), 1f);
            case CUE_CUT -> cuts(c);
            default -> {
            }
        }
    }

    /** A burst of shadow: dark smoke balls, a ring of ink and a few short ink spikes. */
    static void puff(Vec3 at, float s) {
        Brush.mist(at.add(0, -0.4, 0), 0.35 * s, 9, 0x140F1E, 0.8f, 0.9f * s, 18);
        Brush.rays(at, 6, 1.4 * s, 0.07f * s, 8, INK);
        Brush.shock(at.add(0, -0.9, 0), Brush.UP, 0.2, 1.6 * s, 0.12f * s, 10, INK);
    }

    /** An ink crescent with a pale halo, leaning toward the viewer. */
    static void slash(Vec3 at, Vec3 facing, double r, float w, int side) {
        Vec3 view = Brush.camera().subtract(at);
        Vec3 n = view.lengthSqr() < 1e-6 ? Brush.UP : view.normalize();
        Vec3 mid = Brush.flat(facing);
        n = rotAbout(n, mid, side * (0.3 + rnd() * 0.5));
        Vec3 in = mid.subtract(n.scale(mid.dot(n)));
        if (in.lengthSqr() < 1e-6) in = axes(n)[0];
        Vec3 m = in.normalize();
        Vec3 center = at.subtract(m.scale(r * 0.55));
        HALO.on(Ribbon.arc(center, m, n, r * 1.03, 2.6, 0.6, 1.08)).width(w * 2.0f).time(14, 3).hold(0.3f).sparks(0).segments(26).play();
        INK.on(Ribbon.arc(center, m, n, r, 2.6, 0.6, 1.08)).width(w * 1.6f).time(14, 3).hold(0.3f).sparks(0).play();
    }

    /** Vanish: shadow bursts out and an ink spiral closes in around you as you disappear. */
    private static void vanish(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        puff(c.chest(), 1.3f);
        INK.on((s, time, out) -> {
            double a = s * Math.PI * 4 + time * 6;
            double r = 1.1 * (1 - time * 0.8);
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            out[0] = feet.get().add(radial.scale(r)).add(0, s * 2.1, 0);
            out[1] = new Vec3(-Math.sin(a), 0.3, Math.cos(a)).normalize();
            out[2] = radial;
        }).width(0.16f).time(18, 5).hold(0.2f).tailChase(1f).sparks(0).play();
        Brush.glow(c.chest(), 0.9f, 8, PURPLE);
    }

    /** Shadow Dash: a cut opens in front of you and a streak of ink follows you as you go. */
    private static void dash(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        slash(c.chest().add(c.flat().scale(1.2)), c.flat(), 1.8, 0.3f, 1);
        during(0, 10, t -> {
            Vec3 p = feet.get().add(0, 1, 0);
            Vec3 v = e == null ? c.look : e.getDeltaMovement();
            if (v.lengthSqr() < 0.01) return;
            Vec3 back = p.subtract(v.normalize().scale(1.8));
            INK.on(Brush.lineCam(back, p)).width(0.22f).time(10, 2).hold(0.1f).tailChase(1f).sparks(0).segments(10).play();
            if (t % 2 == 0) EDGE.on(Brush.lineCam(back.add(0, 0.5, 0), p.add(0, 0.5, 0))).width(0.04f).time(8, 2).hold(0f).tailChase(1f).sparks(0).segments(8).play();
        });
    }

    /** Smoke Bomb: a cloud of black smoke rolls out over 5 blocks with an ink ring at its edge. */
    private static void smoke(Ctx c) {
        Vec3 g = c.pos;
        Brush.shock(g.add(0, 0.1, 0), Brush.UP, 0.3, 5.2, 0.3f, 14, INK);
        Brush.circle(g, 5, 30, 5, INK, EDGE);
        // Rolling bands of smoke spreading out across the circle
        for (int k = 0; k < 10; k++) {
            double a0 = k * Math.PI / 5 + rnd() * 0.3, y = 0.3 + rnd() * 1.4;
            Paint.ink(0x1E1826, 0x3A3046).on(Ribbon.curve((s, t) -> {
                double r = 0.5 + (s * 0.6 + t * 0.5) * 4.2;
                double a = a0 + s * 1.2;
                return g.add(Math.cos(a) * r, y + Math.sin(s * 6 + t * 3) * 0.25, Math.sin(a) * r);
            }, (s, t) -> Brush.faceCam(g, Brush.UP))).width(0.45f).time(50, 14).hold(0.5f).tailChase(0.6f).sparks(0).segments(20).play();
        }
        Brush.mist(g.add(0, 0.6, 0), 2.5, 18, 0x18141E, 0.8f, 1.4f, 50);
        puff(c.chest(), 1f);
    }

    /** Shadowstep: a thin ink line from where you were to behind the target, and a cut waiting at its back. */
    private static void step(Ctx c) {
        Vec3 was = c.aim.add(0, 1, 0), now = c.chest();
        INK.on(Brush.lineCam(was, now)).width(0.18f).time(12, 2).hold(0.2f).tailChase(1f).sparks(0).play();
        puff(was, 0.8f);
        puff(now, 0.8f);
        Entity t = c.target();
        if (t != null) at(2, () -> slash(t.position().add(0, t.getBbHeight() * 0.55, 0), c.look, 1.3, 0.24f, -1));
    }

    private static void ult(Ctx c) {
        puff(c.chest(), 1.5f);
        Brush.circle(c.pos, 12, 40, 5, INK, EDGE);
    }

    /** One strike of the ultimate: three crossing ink cuts on the target. */
    private static void cuts(Ctx c) {
        Entity t = c.target();
        Vec3 at = t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.55, 0);
        for (int k = 0; k < 3; k++) {
            int kk = k;
            at(k, () -> slash(at, rotY(c.look, kk * 2.1 + c.ticks), 1.3, 0.24f, kk % 2 == 0 ? 1 : -1));
        }
        Brush.glow(at, 0.9f, 6, PURPLE);
        sp("spark", at).size(0.5f, 0.05f).life(5).colors(WHITE, LILAC).envelope(0.05f, 0.3f, 1f);
    }
}
