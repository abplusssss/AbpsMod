package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Paladin: warm white and gold light. Pillars from the sky, halos, blessed circles and wings. */
final class SigPaladin {

    private SigPaladin() {
    }

    static final int GOLD = 0xFFC94A, CREAM = 0xFFF4D0, AMBER = 0xFF9E1B;
    static final Paint HOLY = Paint.light(GOLD, 1.3f);
    static final Paint BRIGHT = Paint.light(CREAM, 1.0f);
    static final Paint WARM = Paint.light(AMBER, 0.9f);

    static final int CUE_SMITE = 11, CUE_STRIKE = 12, CUE_CHARGE_HIT = 13, CUE_SAVED = 15;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> judgment(c);
            case 2 -> consecration(c);
            case 3 -> charge(c);
            case 4 -> aegis(c);
            case 6 -> wrath(c);
            case CUE_SMITE -> smite(c);
            case CUE_STRIKE -> strike(c.aim, 1f);
            case CUE_CHARGE_HIT -> chargeHit(c);
            case CUE_SAVED -> saved(c);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    static Supplier<Vec3> feet(Entity e, Vec3 fallback) {
        return () -> e == null || e.isRemoved() ? fallback : e.position();
    }

    /** A column of light coming down from the sky onto a point, landing with a flash and a ring. */
    static void beamDown(Vec3 ground, double height, float width, int delay) {
        Vec3 top = ground.add(0, height, 0);
        HOLY.on(Brush.lineCam(top, ground)).width(width).time(delay + 14, delay).hold(0.3f).tailChase(0.6f).sparks(0).play();
        BRIGHT.on(Brush.lineCam(top, ground)).width(width * 0.4f).time(delay + 12, delay).hold(0.3f).tailChase(0.6f).sparks(0).play();
        Brush.ring(top.add(0, -0.4, 0), Brush.UP, width * 3, 0.06f, delay + 10, 1, BRIGHT);
        at(delay, () -> strike(ground, width / 0.5f));
    }

    /** Where light lands: a flash, a ring on the ground, rays thrown up and motes of light. */
    static void strike(Vec3 ground, float scale) {
        Vec3 g = ground.add(0, 0.08, 0);
        Brush.glow(g.add(0, 0.6, 0), 1.6f * scale, 10, GOLD);
        Brush.shock(g, Brush.UP, 0.3, 2.8 * scale, 0.18f * scale, 12, HOLY);
        Brush.raysUp(g, 8, 2.2 * scale, 0.08f, 9, BRIGHT);
        motes(g.add(0, 0.5, 0), 1.2 * scale, 10);
    }

    /** Little lights that float slowly upward. */
    static void motes(Vec3 at, double spread, int count) {
        for (int k = 0; k < n(count); k++) {
            Vec3 p = at.add(gauss() * spread, rnd() * spread, gauss() * spread);
            sp("spark", p).size(0.07f, 0.03f).life(22 + (int) (rnd() * 16)).vel(gauss() * 0.005, 0.02 + rnd() * 0.02, gauss() * 0.005)
                    .colors(WHITE, GOLD).flicker(0.4f).envelope(0.15f, 0.5f, 1f);
        }
    }

    /** A halo ring floating over a head. */
    static void halo(Supplier<Vec3> feet, double height, int life) {
        HOLY.on((s, time, out) -> {
            Vec3 c = feet.get().add(0, height, 0);
            double a = s * Math.PI * 2 + time * 4;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            out[0] = c.add(radial.scale(0.38));
            out[1] = new Vec3(-Math.sin(a), 0, Math.cos(a));
            out[2] = radial;
        }).width(0.07f).time(life, 4).hold(0.8f).tailChase(0.2f).sparks(0).segments(20).play();
    }

    /** Two feathered wings of light behind someone, each a fan of curved strokes. */
    static void wings(Supplier<Vec3> feet, Vec3 back, int life) {
        Vec3 side = new Vec3(-back.z, 0, back.x);
        for (int w = -1; w <= 1; w += 2) {
            for (int f = 0; f < 4; f++) {
                double spread = 0.3 + f * 0.25;
                int ww = w, ff = f;
                (f == 0 ? HOLY : BRIGHT).on(Ribbon.curve((s, t) -> {
                    Vec3 root = feet.get().add(0, 1.4, 0).add(back.scale(0.3));
                    Vec3 out = side.scale(ww * (0.3 + s * (1.6 - ff * 0.15))).add(0, Math.sin(s * Math.PI) * 0.5 - s * spread, 0).add(back.scale(s * 0.4));
                    return root.add(out);
                }, (s, t) -> Brush.UP)).width(0.13f - f * 0.015f).time(life, 5).hold(0.6f).tailChase(0.2f).sparks(0).segments(14).play();
            }
        }
    }

    // ------------------------------------------------------------------ abilities

    /** Judgment: a pillar of light drops onto the target from high above. */
    private static void judgment(Ctx c) {
        Entity t = c.target();
        Vec3 g = t == null || t.isRemoved() ? c.focus().add(0, -0.9, 0) : t.position();
        Brush.glow(c.hand(), 0.7f, 8, GOLD);
        Brush.ring(g.add(0, 0.06, 0), Brush.UP, 1.4, 0.1f, 14, 1.5, BRIGHT);
        beamDown(g, 12, 0.55f, 6);
        halo(feet(t, g), (t == null ? 1.9 : t.getBbHeight()) + 0.35, 40);
    }

    /** Consecration: a blessed circle under you that glows for 6 seconds, with pulses of light and rising motes. */
    private static void consecration(Ctx c) {
        Vec3 g = c.pos;
        int life = Math.max(60, c.ticks);
        Brush.circle(g, 5, life, 6, HOLY, BRIGHT);
        Brush.pool(g, 5, life, 0x8A6A1A, 0.3f);
        Brush.shock(g.add(0, 0.1, 0), Brush.UP, 0.5, 5, 0.25f, 12, HOLY);
        during(0, life, t -> {
            if (t % 20 == 10) Brush.shock(g.add(0, 0.1, 0), Brush.UP, 0.5, 5, 0.1f, 14, BRIGHT.bright(0.6f));
            if (t % 3 == 0) {
                double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 5;
                motes(g.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 0.1, 1);
            }
        });
    }

    /** Divine Charge: you run in a shell of light with wings spread and a gold trail behind you. */
    private static void charge(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = feet(e, c.pos);
        Vec3 dir = c.flat();
        wings(feet, dir.scale(-1), 12);
        Brush.cut(c.pos.add(dir.scale(1.2)), dir, 1.8, 0.3f, 12, 1, HOLY);
        during(0, 8, t -> {
            Vec3 p = feet.get();
            WARM.on(Brush.lineCam(p.add(0, 0.5, 0).subtract(dir.scale(2.0)), p.add(0, 0.5, 0))).width(0.25f).time(10, 2).hold(0.1f).tailChase(1f)
                    .sparks(0).play();
            motes(p.add(0, 0.8, 0), 0.4, 2);
        });
    }

    /** Aegis: a dome of gold light settles over you, and a halo stays for as long as the shield lasts. */
    private static void aegis(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = feet(e, c.pos);
        int life = Math.max(60, c.ticks);
        Brush.dome(c.chest(), 2.4, 1.3, 0.12f, 20, HOLY);
        Brush.circle(c.pos, 8, 24, 6, HOLY, BRIGHT);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.5, 8, 0.22f, 14, BRIGHT);
        wings(feet, c.flat().scale(-1), 26);
        for (int k = 0; k * 50 < life; k++) at(k * 50, () -> halo(feet, 2.25, Math.min(54, life)));
        during(0, life, t -> {
            if (t % 8 == 0) motes(feet.get().add(0, 1, 0), 0.5, 1);
        });
    }

    /** Heaven's Wrath: a great circle opens in the sky over you; the server calls down each strike as a cue. */
    private static void wrath(Ctx c) {
        Vec3 sky = c.pos.add(0, 14, 0);
        Brush.glow(c.chest(), 2.4f, 14, GOLD);
        Brush.pillar(c.pos, 0.9, 6, 20, 3, HOLY);
        Brush.ring(sky, Brush.UP, 7, 0.3f, 70, 0.8, HOLY);
        Brush.ring(sky, Brush.UP, 4.5, 0.16f, 70, -1.2, BRIGHT);
        Brush.circle(c.pos, 12, 60, 8, HOLY, BRIGHT);
        wings(feet(c.caster(), c.pos), c.flat().scale(-1), 40);
    }

    // ------------------------------------------------------------------ cues

    private static void smite(Ctx c) {
        Entity t = c.target();
        Vec3 g = t == null || t.isRemoved() ? c.aim.add(0, -0.9, 0) : t.position();
        Brush.bolt(g.add(gauss() * 0.5, 7, gauss() * 0.5), g.add(0, 0.6, 0), 0.14f, 8, 0.35, HOLY);
        at(1, () -> {
            Brush.glow(g.add(0, 0.8, 0), 1.1f, 8, GOLD);
            Brush.ring(g.add(0, 0.08, 0), Brush.UP, 1.1, 0.08f, 10, 2, BRIGHT);
        });
    }

    private static void chargeHit(Ctx c) {
        Entity t = c.target();
        Vec3 p = t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.5, 0);
        Brush.glow(p, 1.3f, 8, GOLD);
        Brush.shock(p, c.look, 0.2, 1.8, 0.14f, 9, HOLY);
        Brush.rays(p, 8, 1.6, 0.08f, 8, BRIGHT);
    }

    private static void saved(Ctx c) {
        Supplier<Vec3> feet = feet(c.caster(), c.pos);
        Brush.pillar(c.pos, 0.8, 5, 26, 3, HOLY);
        wings(feet, c.flat().scale(-1), 30);
        halo(feet, 2.25, 60);
        Brush.dome(c.chest(), 0.5, 2, 0.12f, 18, BRIGHT);
    }
}
