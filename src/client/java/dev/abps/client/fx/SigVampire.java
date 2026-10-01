package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/**
 * Vampire: dark crimson ink with a hot red rim, streams of blood that follow whoever they connect, and violet mist.
 * No picture sprites anywhere.
 */
final class SigVampire {

    private SigVampire() {
    }

    static final int CRIMSON = 0xE53935, BLOOD = 0xB0001C, VIOLET = 0x6A0DAD, MIST = 0x2A0716;
    static final Paint INK = Paint.ink(0x1C0307, 0xFF4B4B);
    static final Paint INK_VIOLET = Paint.ink(0x14051E, 0xB46CFF);
    static final Paint LIGHT = Paint.light(CRIMSON, 1.3f);
    static final Paint THIN = Paint.light(0xFF6E6E, 0.9f);

    // Cues the server sends during abilities and from passives
    static final int CUE_DRAIN = 11, CUE_LIFESTEAL = 12, CUE_BLEED = 13, CUE_KILL = 14, CUE_BURST_HIT = 15;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> curse(c);
            case 2 -> bind(c);
            case 3 -> burst(c);
            case 4 -> moon(c);
            case 6 -> feast(c);
            case CUE_DRAIN -> drain(c, 10, 0.16f);
            case CUE_LIFESTEAL -> lifesteal(c);
            case CUE_BLEED -> bleed(c);
            case CUE_KILL -> kill(c);
            case CUE_BURST_HIT -> drain(c, 14, 0.13f);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static Supplier<Vec3> mid(Entity e, Vec3 fallback) {
        return () -> e == null || e.isRemoved() ? fallback : e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    static Supplier<Vec3> feet(Entity e, Vec3 fallback) {
        return () -> e == null || e.isRemoved() ? fallback : e.position();
    }

    /** Three tilted ink rings that close in around a body like a noose. */
    static void noose(Supplier<Vec3> center, double r, int life) {
        for (int k = 0; k < 3; k++) {
            double tilt = (k - 1) * 0.5;
            int kk = k;
            Ribbon.Path path = (s, time, out) -> {
                Vec3 cc = center.get().add(0, (kk - 1) * 0.45, 0);
                double rr = r * (1.25 - 0.35 * Math.min(1, time * 2));
                double a = kk * 2.1 + s * Math.PI * 1.7 + time * (kk % 2 == 0 ? 5 : -5);
                Vec3 n = new Vec3(Math.sin(tilt), Math.cos(tilt), 0);
                Vec3[] uv = axes(n);
                Vec3 radial = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
                out[0] = cc.add(radial.scale(rr));
                out[1] = uv[0].scale(-Math.sin(a)).add(uv[1].scale(Math.cos(a)));
                out[2] = radial;
            };
            INK.on(path).width(0.16f).time(life, 4).hold(0.6f).tailChase(0.4f).sparks(0).segments(24).play();
        }
    }

    /** A stream of blood flowing from one body into another, then a small pulse where it arrives. */
    static void stream(Supplier<Vec3> from, Supplier<Vec3> to, int life, float width) {
        Brush.tether(from, to, width, life, 0.35, 0.18, INK);
        Brush.tether(from, to, width * 0.45f, life - 2, 0.3, 0.1, THIN);
        at(life / 2, () -> {
            Vec3 p = to.get();
            Brush.glow(p, 0.7f, 6, CRIMSON);
            Brush.embers(p, 4, 0.06, CRIMSON);
        });
    }

    // ------------------------------------------------------------------ abilities

    /** Blood Curse: a lash of blood from the hand to the target, a crimson circle under them and a noose of ink. */
    private static void curse(Ctx c) {
        Entity t = c.target();
        Vec3 f = c.focus();
        Supplier<Vec3> tm = mid(t, f), tf = feet(t, f.add(0, -0.9, 0));
        Brush.glow(c.hand(), 0.6f, 6, CRIMSON);
        Brush.comet(c.hand(), f, 6, 0.3f, INK, null);
        Brush.comet(c.hand(), f, 6, 0.12f, THIN, () -> {
            Vec3 p = tm.get();
            Brush.glow(p, 1.5f, 10, CRIMSON);
            Brush.rays(p, 8, 2.0, 0.12f, 9, INK);
            Brush.ring(p, Brush.camera().subtract(p).normalize(), 1.1, 0.2f, 16, 2.0, INK);
            Brush.circle(tf.get(), 1.6, 40, 5, INK, INK_VIOLET);
            noose(tm, 0.85, 30);
        });
        // The curse lingers: a faint ink ring pulses at their feet
        during(10, 100, s -> {
            if (s % 25 != 0) return;
            Vec3 p = tf.get();
            Brush.ring(p.add(0, 0.05, 0), Brush.UP, 0.9, 0.08f, 18, 1.2, INK);
            
        });
    }

    /** Blood Bind: ink chains lock around the target while blood flows from them into you. */
    private static void bind(Ctx c) {
        Entity t = c.target();
        Vec3 f = c.focus();
        Supplier<Vec3> tm = mid(t, f), me = mid(c.caster(), c.chest());
        int hold = Math.max(40, c.ticks);
        Brush.glow(f, 1.2f, 8, CRIMSON);
        Brush.shock(feet(t, f.add(0, -0.9, 0)).get().add(0, 0.1, 0), Brush.UP, 0.4, 2.2, 0.16f, 10, INK);
        for (int k = 0; k < hold / 12; k++) at(k * 12, () -> noose(tm, 0.75, 16));
        at(4, () -> stream(tm, me, 16, 0.14f));
        at(4, () -> Brush.helix(feet(c.caster(), c.pos), 0.6, 2.0, 1.5, 0.1f, 18, rnd() * 6, THIN));
    }

    /** Sanguine Burst: blood rushes into you, then bursts out as a crimson shockwave and a ring of cuts. */
    private static void burst(Ctx c) {
        Vec3 chest = c.chest(), ground = c.pos.add(0, 0.1, 0);
        Brush.converge(chest, 2.4, 9, 7, 0.07f, THIN);
        at(6, () -> {
            Brush.glow(chest, 2.0f, 10, CRIMSON);
            Brush.shock(ground, Brush.UP, 0.6, 6.5, 0.32f, 14, INK);
            Brush.shock(ground.add(0, 0.25, 0), Brush.UP, 0.4, 5.5, 0.14f, 11, LIGHT);
            Brush.raysUp(chest, 12, 3.2, 0.12f, 9, INK);
            for (int k = 0; k < 4; k++) {
                Vec3 dir = rotY(c.flat(), k * Math.PI / 2 + 0.4);
                Brush.cut(c.pos.add(0, 0.2, 0), dir, 2.6, 0.32f, 14, k % 2 == 0 ? 1 : -1, INK);
            }
            Brush.embers(chest, 14, 0.25, CRIMSON);
        });
    }

    /** Blood Moon: a red moon rises over you, and crimson streams wind around you for as long as it lasts. */
    private static void moon(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> base = feet(e, c.pos);
        int life = Math.max(100, c.ticks);
        Brush.circle(c.pos, 2.4, 30, 6, INK, INK_VIOLET);
        Brush.pillar(c.pos, 0.7, 4.5, 18, 3, INK);
        // The moon: a disk of red light far above, ringed by a dark halo
        Vec3 moonAt = c.pos.add(c.flat().scale(-3)).add(0, 9, 0);
        sp("glow", moonAt).size(1.8f, 2.2f).life(life).colors(0xFF5A4A, BLOOD).envelope(0.05f, 0.9f, 0.9f);
        sp("glow", moonAt).size(3.6f, 4.0f).life(life).colors(BLOOD, darken(BLOOD, 0.5f)).envelope(0.05f, 0.9f, 0.35f);
        at(2, () -> Brush.ring(moonAt, Brush.camera().subtract(moonAt).normalize(), 2.0, 0.18f, 30, 1.0, INK));
        // Around you while it lasts
        during(10, life, s -> {
            if (s % 30 == 0) Brush.helix(base, 0.7, 2.1, 1.2, 0.09f, 26, s * 0.7, THIN);

        });
    }

    /** Crimson Feast: a huge blood circle, a column of ink on you; the server sends a drain cue for every victim tick. */
    private static void feast(Ctx c) {
        Brush.circle(c.pos, 10, 120, 8, INK, INK_VIOLET);
        Brush.pillar(c.pos, 1.0, 7, 30, 4, INK);
        Brush.glow(c.chest(), 2.4f, 14, CRIMSON);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 1, 10, 0.4f, 16, INK);
        Supplier<Vec3> base = feet(c.caster(), c.pos);
        during(0, 120, s -> {
            if (s % 20 == 0) Brush.helix(base, 1.0, 2.6, 1.6, 0.13f, 24, s * 0.5, INK);

        });
    }

    // ------------------------------------------------------------------ cues

    /** A stream of blood from a victim (target) back to the vampire (caster). */
    private static void drain(Ctx c, int life, float width) {
        Entity victim = c.target();
        Supplier<Vec3> from = mid(victim, c.aim), to = mid(c.caster(), c.pos.add(0, 1, 0));
        stream(from, to, life, width);
        Brush.glow(from.get(), 0.6f, 5, CRIMSON);
    }

    /** Lifesteal on a melee hit: a quick thin stream back to you and a little spray of blood. */
    private static void lifesteal(Ctx c) {
        Supplier<Vec3> from = mid(c.target(), c.aim), to = mid(c.caster(), c.pos.add(0, 1, 0));
        Brush.tether(from, to, 0.07f, 9, 0.25, 0.08, THIN);
        Brush.rays(from.get(), 4, 0.9, 0.06f, 6, INK);
    }

    private static void bleed(Ctx c) {
        Vec3 p = mid(c.target(), c.aim).get();
        Brush.rays(p, 3, 0.8, 0.06f, 7, INK);
        sp("ember", p).size(0.06f, 0.02f).life(14).vel(0, -0.02, 0).grav(0.01f).colors(CRIMSON, BLOOD).envelope(0.05f, 0.5f, 1f);
    }

    private static void kill(Ctx c) {
        Supplier<Vec3> base = feet(c.caster(), c.pos);
        Brush.helix(base, 0.7, 2.2, 1.8, 0.12f, 22, rnd() * 6, INK);
        Brush.helix(base, 0.7, 2.2, 1.8, 0.06f, 20, rnd() * 6 + 3, THIN);
        Brush.glow(c.pos.add(0, 1, 0), 1.0f, 8, CRIMSON);
    }
}
