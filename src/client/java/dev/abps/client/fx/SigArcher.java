package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Archer: clean green and teal light. Streaks, reticles and one enormous beam. */
final class SigArcher {

    private SigArcher() {
    }

    static final int GREEN = 0x9CEB5C, TEAL = 0x1DE9B6, ROPE = 0xE6DCD2;
    static final Paint LEAF = Paint.light(GREEN, 1.2f);
    static final Paint SEA = Paint.light(TEAL, 1.2f);
    static final Paint CORE = Paint.light(0xF0FFF8, 1.0f);

    static final int CUE_GRAPPLE = 11, CUE_BEAM = 12;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> volley(c);
            case 2 -> mark(c);
            case 3 -> Brush.shock(c.hand(), c.look, 0.1, 0.9, 0.06f, 8, CORE);
            case 4 -> storm(c);
            case 6 -> charge(c);
            case CUE_GRAPPLE -> grapple(c);
            case CUE_BEAM -> beam(c.pos, c.aim);
            default -> {
            }
        }
    }

    /** A crosshair: a ring with four ticks pointing in, around a point, facing the camera. */
    static void reticle(Supplier<Vec3> at, double r, int life) {
        Vec3 p0 = at.get();
        Vec3 n = Brush.camera().subtract(p0).normalize();
        Vec3[] uv = axes(n);
        SEA.on((s, time, out) -> {
            double a = s * Math.PI * 2 + time * 3;
            Vec3 radial = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            out[0] = at.get().add(radial.scale(r));
            out[1] = uv[0].scale(-Math.sin(a)).add(uv[1].scale(Math.cos(a)));
            out[2] = radial;
        }).width(0.07f).time(life, 4).hold(0.8f).tailChase(0.2f).sparks(0).segments(24).play();
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            Vec3 d = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            LEAF.on(Ribbon.curve((s, t) -> at.get().add(d.scale(r * (1.5 - s * 0.6))), (s, t) -> Brush.faceCam(at.get(), d)))
                    .width(0.06f).time(life, 3).hold(0.8f).tailChase(0f).sparks(0).segments(6).play();
        }
    }

    /** Volley: a fan of green streaks leaves the bow, one for each arrow. */
    private static void volley(Ctx c) {
        int count = Math.max(3, c.ticks);
        Vec3 from = c.hand();
        Brush.shock(from, c.look, 0.1, 1.2, 0.08f, 8, CORE);
        for (int i = 0; i < count; i++) {
            Vec3 dir = rotY(c.look, Math.toRadians((i - (count - 1) / 2.0) * 5));
            Vec3 to = from.add(dir.scale(18));
            Brush.comet(from, to, 4, 0.1f, i % 2 == 0 ? LEAF : SEA, null);
        }
        Brush.sparks(from, 6, 0.3, GREEN);
    }

    /** Mark: a thin line of light to the target, then a turning reticle that stays on it for the whole mark. */
    private static void mark(Ctx c) {
        Entity t = c.target();
        Supplier<Vec3> head = () -> t == null || t.isRemoved() ? c.focus().add(0, 1.2, 0) : t.position().add(0, t.getBbHeight() + 0.6, 0);
        Supplier<Vec3> mid = () -> t == null || t.isRemoved() ? c.focus() : t.position().add(0, t.getBbHeight() * 0.5, 0);
        Brush.comet(c.hand(), mid.get(), 4, 0.06f, CORE, () -> {
            Brush.glow(mid.get(), 1f, 8, TEAL);
            Brush.shock(mid.get(), Brush.camera().subtract(mid.get()).normalize(), 0.2, 1.6, 0.1f, 10, SEA);
        });
        int life = Math.max(60, c.ticks);
        for (int k = 0; k * 40 < life; k++) at(4 + k * 40, () -> reticle(mid, 0.9, 44));
        during(4, life, s -> {
            if (s % 20 == 0) Brush.ring(head.get(), Brush.UP, 0.3, 0.05f, 22, 3, LEAF);
        });
    }

    /** Arrow Storm: a green circle on the ground and streaks of light pouring down into it. */
    private static void storm(Ctx c) {
        Vec3 g = c.aim;
        int life = Math.max(20, c.ticks);
        Brush.circle(g, 4.5, life + 10, 8, LEAF, SEA);
        during(0, life, t -> {
            if (t % 2 != 0) return;
            for (int k = 0; k < 3; k++) {
                double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 4.5;
                Vec3 land = g.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
                Vec3 top = land.add(0, 12, 0);
                Brush.comet(top, land, 4, 0.08f, k == 0 ? CORE : LEAF, () -> {
                    Brush.ring(land, Brush.UP, 0.5, 0.05f, 8, 1, SEA);
                    Brush.sparks(land.add(0, 0.1, 0), 2, 0.2, GREEN);
                });
            }
        });
    }

    /** Sky Piercer, charging: light gathers in rings that close on a point in front of the bow. */
    private static void charge(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> tip = () -> {
            if (e == null || e.isRemoved()) return c.hand();
            return e.getEyePosition().add(e.getLookAngle().scale(1.3));
        };
        int life = Math.max(10, c.ticks);
        during(0, life, t -> {
            Vec3 p = tip.get();
            if (t % 4 == 0) {
                Vec3 n = e == null ? c.look : e.getLookAngle();
                Brush.ring(p, n, 1.6 - t * 0.06, 0.07f, 6, 2, t % 8 == 0 ? SEA : LEAF);
            }
            if (t % 3 == 0) Brush.converge(p, 1.6, 2, 6, 0.04f, CORE);
            sp("glow", p).size(0.2f + t * 0.025f, 0.2f + t * 0.03f).life(3).colors(WHITE, TEAL).envelope(0.1f, 0.5f, 0.9f);
        });
    }

    /** Sky Piercer, firing: an enormous beam with rings riding along it and a blast where it ends. */
    static void beam(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 dir = d.scale(1 / len);
        SEA.on(Brush.lineCam(from, to)).width(0.9f).time(18, 2).hold(0.4f).tailChase(0.5f).sparks(0).play();
        CORE.on(Brush.lineCam(from, to)).width(0.35f).time(16, 2).hold(0.4f).tailChase(0.5f).sparks(0).play();
        LEAF.on(Ribbon.curve((s, t) -> from.add(d.scale(s)).add(axes(dir)[0].scale(Math.cos(s * len * 1.4 + t * 10) * 0.7))
                .add(axes(dir)[1].scale(Math.sin(s * len * 1.4 + t * 10) * 0.7)), (s, t) -> Brush.faceCam(from.add(d.scale(s)), dir)))
                .width(0.1f).time(16, 3).hold(0.3f).tailChase(0.6f).sparks(0).play();
        for (double along = 3; along < len; along += 4) {
            Vec3 p = from.add(dir.scale(along));
            int delay = (int) (along / 12);
            at(delay, () -> Brush.shock(p, dir, 0.4, 1.9, 0.09f, 9, SEA));
        }
        at(2, () -> {
            Brush.glow(to, 2.6f, 12, TEAL);
            Brush.rays(to, 12, 3, 0.12f, 10, LEAF);
            Brush.shock(to, dir, 0.4, 3.2, 0.2f, 12, CORE);
        });
    }

    /** The rope arrow landing: a sagging line pulls tight between you and the hook. */
    private static void grapple(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> me = () -> e == null || e.isRemoved() ? c.pos : e.getEyePosition().add(0, -0.3, 0);
        Vec3 hook = c.aim;
        Paint rope = Paint.light(ROPE, 0.9f);
        Brush.tether(me, () -> hook, 0.05f, 14, 0.2, 0.02, rope);
        Brush.ring(hook, Brush.camera().subtract(hook).normalize(), 0.5, 0.06f, 10, 2, rope);
        Brush.sparks(hook, 6, 0.25, ROPE);
    }
}
