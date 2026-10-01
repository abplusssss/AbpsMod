package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Pyromancer: orange fire light with a gold core, real flames, embers and dark smoke. */
final class SigPyro {

    private SigPyro() {
    }

    static final int ORANGE = 0xFF7A1A, GOLD = 0xFFD24A, RED = 0xFF3D00;
    static final Paint FIRE = Paint.light(ORANGE, 1.3f);
    static final Paint HOT = Paint.light(GOLD, 1.1f);
    static final Paint CHAR = Paint.ink(0x1A0800, 0xFF6A00);

    static final int CUE_IMPACT = 11, CUE_TRAIL = 12, CUE_POP = 13, CUE_NOVA = 14;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> {
                Brush.glow(c.hand(), 0.9f, 6, GOLD);
                Brush.shock(c.hand(), c.look, 0.1, 1.0, 0.08f, 7, HOT);
            }
            case 3 -> dash(c);
            case 4 -> meteor(c);
            case 6 -> inferno(c);
            case CUE_IMPACT -> blast(c.pos, 1f);
            case CUE_TRAIL -> trail(c);
            case CUE_POP -> blast(c.pos.add(0, 0.5, 0), 0.8f);
            case CUE_NOVA -> nova(c);
            default -> {
            }
        }
    }

    /** A few real flames licking up from a point. */
    static void flames(Vec3 at, double spread, int count, float size) {
        for (int k = 0; k < n(count); k++) {
            sp("flame", at.add(gauss() * spread, rnd() * spread * 0.5, gauss() * spread)).size(size, size * 0.4f).life(10 + (int) (rnd() * 8))
                    .vel(gauss() * 0.01, 0.04 + rnd() * 0.04, gauss() * 0.01).colors(GOLD, RED).envelope(0.1f, 0.5f, 1f);
        }
    }

    static void smoke(Vec3 at, double spread, int count) {
        Brush.mist(at, spread, count, 0x2A2420, 0.55f, 1.2f, 30);
    }

    /** A fire explosion: a flash, a burst ring facing you, flames, embers and smoke. */
    static void blast(Vec3 at, float s) {
        Brush.glow(at, 2.0f * s, 10, ORANGE);
        Brush.shock(at, Brush.camera().subtract(at).normalize(), 0.3, 2.6 * s, 0.2f * s, 11, FIRE);
        Brush.rays(at, 10, 2.2 * s, 0.1f * s, 9, HOT);
        flames(at, 0.6 * s, 10, 0.5f * s);
        Brush.embers(at, 18, 0.3 * s, ORANGE);
        smoke(at.add(0, 0.3, 0), 0.6 * s, 6);
    }

    /** The fireball in flight: a hot head and a streak of fire behind it, for as long as it exists. */
    private static void trail(Ctx c) {
        int id = c.casterId;
        during(0, 80, t -> {
            Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
            if (e == null || e.isRemoved()) return;
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 v = e.getDeltaMovement();
            sp("glow", p).size(0.6f, 0.4f).life(3).colors(WHITE, ORANGE).envelope(0.05f, 0.4f, 1f);
            flames(p, 0.1, 1, 0.35f);
            if (v.lengthSqr() > 0.01 && t % 2 == 0) {
                FIRE.on(Brush.lineCam(p.subtract(v.normalize().scale(1.8)), p)).width(0.22f).time(7, 2).hold(0f).tailChase(1f).sparks(1).segments(10).play();
            }
            if (t % 3 == 0) smoke(p, 0.1, 1);
        });
    }

    /** Flame Nova: a ring of fire rolls out 7 blocks, a burning circle and a dome of flame light. */
    private static void nova(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        Brush.glow(g.add(0, 1, 0), 2.4f, 12, ORANGE);
        Brush.shock(g, Brush.UP, 0.5, 7.5, 0.4f, 14, FIRE);
        Brush.shock(g.add(0, 0.3, 0), Brush.UP, 0.3, 6.5, 0.16f, 11, HOT);
        Brush.dome(g.add(0, 1, 0), 0.5, 3.5, 0.14f, 12, FIRE);
        Brush.circle(c.pos, 7, 26, 7, CHAR, FIRE);
        Brush.raysUp(g, 10, 2.8, 0.1f, 10, HOT);
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            Vec3 p = g.add(Math.cos(a) * 4, 0, Math.sin(a) * 4);
            at(3, () -> flames(p, 0.4, 3, 0.7f));
        }
        Brush.embers(g.add(0, 1, 0), 24, 0.35, ORANGE);
    }

    /** Blaze Dash: you leave a ribbon of fire behind, with rings blowing off it. */
    private static void dash(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        during(0, 12, t -> {
            Vec3 p = feet.get().add(0, 0.9, 0);
            Vec3 v = e == null ? c.look : e.getDeltaMovement();
            if (v.lengthSqr() < 0.02) return;
            Vec3 d = v.normalize();
            FIRE.on(Brush.lineCam(p.subtract(d.scale(2.2)), p)).width(0.32f).time(9, 2).hold(0.1f).tailChase(1f).sparks(2).segments(12).play();
            if (t % 3 == 0) Brush.ring(p, d, 0.9, 0.08f, 8, 2, HOT);
            flames(p.add(0, -0.6, 0), 0.3, 2, 0.6f);
        });
    }

    /** Meteor: a warning circle burns into the ground while a blazing rock falls on it, then a huge blast. */
    private static void meteor(Ctx c) {
        Vec3 g = c.aim;
        int fall = Math.max(10, c.ticks);
        Brush.circle(g, 6, fall + 10, 8, CHAR, FIRE);
        Vec3 start = g.add(c.flat().scale(-6)).add(0, 22, 0);
        during(0, fall, t -> {
            double f = (t + 1) / (double) fall;
            Vec3 p = start.lerp(g.add(0, 0.8, 0), f * f);
            Vec3 next = start.lerp(g.add(0, 0.8, 0), Math.min(1, (f + 0.05) * (f + 0.05)));
            Vec3 d = next.subtract(p).lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : next.subtract(p).normalize();
            sp("glow", p).size(1.6f, 1.2f).life(3).colors(WHITE, ORANGE).envelope(0.05f, 0.5f, 1f);
            FIRE.on(Brush.lineCam(p.subtract(d.scale(5)), p)).width(0.9f).time(6, 2).hold(0f).tailChase(1f).sparks(2).segments(14).play();
            CHAR.on(Brush.lineCam(p.subtract(d.scale(3.5)), p)).width(0.45f).time(6, 2).hold(0f).tailChase(1f).sparks(0).segments(10).play();
            flames(p, 0.5, 2, 0.9f);
            if (t % 2 == 0) smoke(p.subtract(d.scale(2)), 0.6, 2);
        });
        at(fall, () -> {
            Vec3 h = g.add(0, 0.08, 0);
            blast(h.add(0, 1, 0), 2.2f);
            Brush.shock(h, Brush.UP, 1, 8, 0.6f, 16, FIRE);
            Brush.shock(h.add(0, 0.3, 0), Brush.UP, 0.5, 6, 0.2f, 13, HOT);
            Brush.pillar(g, 1.6, 12, 22, 4, FIRE);
            SigBerserker.cracks(h, 6, 10);
            SigMiner.debris(h.add(0, 0.5, 0), 20, 0.45);
            smoke(h.add(0, 1, 0), 2.5, 16);
        });
    }

    /** Inferno: a ring of fire around you, spirals of flame climbing you and pulses rolling out every second. */
    private static void inferno(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        int life = Math.max(60, c.ticks);
        Brush.glow(c.chest(), 2.6f, 12, ORANGE);
        Brush.pillar(c.pos, 1.0, 7, 20, 4, FIRE);
        during(0, life, t -> {
            Vec3 b = feet.get();
            if (t % 8 == 0) Brush.helix(() -> feet.get(), 1.3, 3.2, 1.5, 0.16f, 14, t * 0.8, t % 16 == 0 ? FIRE : HOT);
            if (t % 20 == 0) {
                Brush.shock(b.add(0, 0.08, 0), Brush.UP, 0.6, 8, 0.25f, 14, FIRE);
                Brush.ring(b.add(0, 0.08, 0), Brush.UP, 8, 0.16f, 22, 0.8, HOT);
            }
            if (t % 2 == 0) {
                double a = rnd() * Math.PI * 2, r = 1 + rnd() * 7;
                flames(b.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 0.2, 1, 0.8f);
            }
            if (t % 6 == 0) Brush.embers(b.add(0, 1, 0), 3, 0.2, ORANGE);
        });
    }
}
