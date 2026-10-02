package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Overlord (operators only): gold and white light with black ink edges. Everything is big and loud. */
final class SigOverlord {

    private SigOverlord() {
    }

    static final int THEME = 18;
    static final int GOLD = 0xFFD54F, PALE = 0xFFF8E1, INK = 0x0A0806;
    static final Paint SUN = Paint.light(GOLD, 1.4f);
    static final Paint WHITE_LIGHT = Paint.light(PALE, 1.2f);
    static final Paint SHADOW = Paint.ink(INK, GOLD);

    static final int CUE_DEATH = 11;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> smite(c);
            case 2 -> annihilate(c);
            case 3 -> warp(c);
            case 4 -> timeStop(c);
            case 6 -> judgment(c);
            case CUE_DEATH -> death(c);
            default -> {
            }
        }
    }

    /** A bolt of light from the sky onto a point, a crown of rays and a gold ring rolling out. */
    static void bolt(Vec3 sky, Vec3 ground, float scale) {
        Brush.bolt(sky, ground, 0.5f * scale, 10, 0.9, SUN);
        Brush.bolt(sky.add(gauss() * 0.6, 0, gauss() * 0.6), ground, 0.22f * scale, 8, 1.2, WHITE_LIGHT);
        at(2, () -> {
            Brush.glow(ground.add(0, 0.6, 0), 2.2f * scale, 12, GOLD);
            Brush.shock(ground.add(0, 0.08, 0), Brush.UP, 0.4, 4.5 * scale, 0.32f * scale, 14, SUN);
            Brush.raysUp(ground.add(0, 0.1, 0), 10, 3.5 * scale, 0.1f * scale, 10, WHITE_LIGHT);
            Brush.sparks(ground.add(0, 0.5, 0), 14, 0.4, GOLD);
        });
    }

    private static void smite(Ctx c) {
        Vec3 g = c.focus();
        Entity t = c.target();
        Vec3 ground = t == null || t.isRemoved() ? g : t.position();
        Brush.converge(c.hand(), 1.4, 6, 6, 0.06f, WHITE_LIGHT);
        bolt(ground.add(gauss() * 1.5, 20, gauss() * 1.5), ground, 1f);
    }

    /** One killed target: a bolt and a dark ring closing over it. */
    private static void death(Ctx c) {
        Vec3 ground = c.aim;
        bolt(c.pos, ground, 0.8f);
        Brush.ring(ground.add(0, 0.06, 0), Brush.UP, 1.3, 0.16f, 18, -1.4, SHADOW);
    }

    /** A dome of gold and black blasting out over 24 blocks. */
    private static void annihilate(Ctx c) {
        Vec3 g = c.pos.add(0, 0.1, 0);
        Brush.glow(c.chest(), 3f, 14, GOLD);
        Brush.dome(c.chest(), 0.5, 6, 0.3f, 16, SUN);
        Brush.shock(g, Brush.UP, 1, 24, 0.6f, 22, SUN);
        at(3, () -> Brush.shock(g, Brush.UP, 0.8, 20, 0.9f, 22, SHADOW));
        at(6, () -> Brush.shock(g.add(0, 0.3, 0), Brush.UP, 0.6, 16, 0.25f, 18, WHITE_LIGHT));
        Brush.circle(c.pos, 6, 30, 9, SUN, SHADOW);
        Brush.raysUp(g, 18, 7, 0.14f, 14, SUN);
    }

    /** Warp: a ring folds shut where you were and opens where you land, with a streak between them. */
    private static void warp(Ctx c) {
        Vec3 from = c.pos.add(0, 1, 0), to = c.aim.add(0, 1, 0);
        Vec3 n = to.subtract(from).lengthSqr() < 1e-4 ? c.look : to.subtract(from).normalize();
        Brush.ring(from, n, 1.2, 0.18f, 14, 2, SHADOW);
        Brush.comet(from, to, 6, 0.35f, SUN, () -> {
            Brush.ring(to, n, 1.4, 0.2f, 18, -2, SUN);
            Brush.glow(to, 1.8f, 10, GOLD);
            Brush.shock(c.aim.add(0, 0.08, 0), Brush.UP, 0.3, 3, 0.22f, 12, WHITE_LIGHT);
        });
    }

    /** Time Stop: a huge clock face on the ground with its hands sweeping round, and a pale dome over everything. */
    private static void timeStop(Ctx c) {
        int life = Math.max(40, c.ticks);
        Vec3 g = c.pos;
        Brush.circle(g, 12, life, 12, SUN, WHITE_LIGHT);
        Brush.dome(c.chest(), 1, 14, 0.22f, 26, Paint.light(PALE, 0.5f));
        Brush.pool(g, 12, life, 0x302A20, 0.3f);
        // Twelve tick marks round the clock
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2 * k / 12;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 a0 = g.add(o.scale(10.6)).add(0, 0.08, 0), a1 = g.add(o.scale(11.6)).add(0, 0.08, 0);
            SHADOW.on(Ribbon.line(a0, a1, new Vec3(-o.z, 0, o.x))).width(0.35f).time(life, 4).hold(0.9f).tailChase(0f).sparks(0).segments(6).play();
        }
        // Two hands that keep turning
        for (int h = 0; h < 2; h++) {
            double len = h == 0 ? 9 : 6, speed = h == 0 ? 0.6 : 0.15;
            Paint p = h == 0 ? SUN : SHADOW;
            p.on(Ribbon.curve((s, t) -> {
                double a = t * life * speed * 0.2;
                return g.add(Math.cos(a) * len * s, 0.1, Math.sin(a) * len * s);
            }, (s, t) -> Brush.UP)).width(0.4f).time(life, 3).hold(0.95f).tailChase(0f).sparks(0).segments(12).play();
        }
    }

    /** Judgment: pillars of light all around and a giant gold circle under you. */
    private static void judgment(Ctx c) {
        Vec3 g = c.pos;
        Brush.pillar(g, 2, 30, 50, 6, SUN);
        Brush.circle(g, 10, 60, 11, SUN, SHADOW);
        Brush.shock(g.add(0, 0.1, 0), Brush.UP, 1, 30, 0.7f, 26, SUN);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            Vec3 at = g.add(Math.cos(a) * 9, 0, Math.sin(a) * 9);
            at(k * 2, () -> bolt(at.add(0, 22, 0), at, 0.8f));
        }
        Brush.glow(c.chest(), 4f, 20, GOLD);
    }
}
