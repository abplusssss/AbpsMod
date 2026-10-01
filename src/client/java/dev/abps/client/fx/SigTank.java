package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Tank: cool steel blue and white light. Shield domes, chains, and stomps that crack the ground. */
final class SigTank {

    private SigTank() {
    }

    static final int STEEL = 0x90CAF9, BLUE = 0x42A5F5, WHITE_BLUE = 0xE3F2FD;
    static final Paint SHIELD = Paint.light(STEEL, 1.1f);
    static final Paint CORE = Paint.light(BLUE, 1.2f);
    static final Paint SHINE = Paint.light(WHITE_BLUE, 0.8f);

    static final int CUE_BLOCK = 11, CUE_THORNS = 12, CUE_PULL = 13, CUE_BASH = 14, CUE_STOMP = 15;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> shield(c, 1.6, Math.max(40, c.ticks), false);
            case 2 -> taunt(c);
            case 3 -> charge(c);
            case 4 -> shield(c, 1.3, Math.max(40, c.ticks), true);
            case 6 -> colossus(c);
            case CUE_BLOCK -> {
                Brush.glow(c.chest(), 0.8f, 8, STEEL);
                Brush.shock(c.chest(), c.look, 0.2, 1.1, 0.08f, 8, SHINE);
            }
            case CUE_THORNS -> thorns(c);
            case CUE_PULL -> pull(c);
            case CUE_BASH -> bash(c);
            case CUE_STOMP -> stomp(c);
            default -> {
            }
        }
    }

    static Supplier<Vec3> feet(Ctx c) {
        Entity e = c.caster();
        return () -> e == null || e.isRemoved() ? c.pos : e.position();
    }

    /** A dome of light around the body that stays for the whole buff, shimmering now and then. */
    private static void shield(Ctx c, double r, int life, boolean hard) {
        Supplier<Vec3> feet = feet(c);
        Brush.glow(c.chest(), 1.4f, 10, BLUE);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.3, 3, 0.2f, 12, CORE);
        Brush.circle(c.pos, 2.2, 30, 6, CORE, SHINE);
        for (int round = 0; round * 30 < life; round++) {
            at(round * 30, () -> {
                for (int k = 0; k < 3; k++) {
                    int kk = k;
                    double tilt = k * Math.PI / 3;
                    (hard && k == 0 ? CORE : SHIELD).on((s, time, out) -> {
                        Vec3 cc = feet.get().add(0, 1.0, 0);
                        Vec3 n = rotY(new Vec3(Math.sin(0.9), Math.cos(0.9), 0), tilt + time * 1.2 * (kk % 2 == 0 ? 1 : -1));
                        Vec3[] uv = axes(n);
                        double a = s * Math.PI * 2;
                        Vec3 radial = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
                        out[0] = cc.add(radial.scale(r));
                        out[1] = uv[0].scale(-Math.sin(a)).add(uv[1].scale(Math.cos(a)));
                        out[2] = radial;
                    }).width(hard ? 0.09f : 0.06f).time(34, 6).hold(0.7f).tailChase(0.2f).sparks(0).segments(28).play();
                }
            });
        }
        during(10, life, t -> {
            if (t % 6 == 0) {
                Vec3 p = feet.get().add(0, 1, 0).add(rndDir().scale(r));
                sp("spark", p).size(0.09f, 0.02f).life(10).colors(WHITE, STEEL).envelope(0.1f, 0.4f, 1f);
            }
        });
    }

    /** Taunt: a blast of light pulls the area toward you and a circle is stamped under your feet. */
    private static void taunt(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        Brush.glow(c.chest(), 1.8f, 10, BLUE);
        Brush.shock(g, Brush.UP, 8, 0.6, 0.3f, 14, CORE);
        Brush.circle(c.pos, 8, 30, 8, CORE, SHINE);
        Brush.converge(c.chest(), 5, 10, 8, 0.07f, SHINE);
    }

    /** A chain of light from each pulled enemy to you. */
    private static void pull(Ctx c) {
        Entity t = c.target();
        Supplier<Vec3> from = () -> t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.55, 0);
        Supplier<Vec3> to = () -> feet(c).get().add(0, 1, 0);
        Brush.tether(from, to, 0.08f, 14, 0.25, 0.04, CORE);
        Brush.tether(from, to, 0.035f, 12, 0.22, 0.03, SHINE);
    }

    /** Shield Bash: you charge behind a wedge of light. */
    private static void charge(Ctx c) {
        Supplier<Vec3> feet = feet(c);
        Vec3 dir = c.flat();
        Entity e = c.caster();
        during(0, 10, t -> {
            Vec3 p = feet.get().add(0, 1, 0);
            Vec3 v = e == null ? dir : e.getDeltaMovement();
            if (v.horizontalDistanceSqr() < 0.04) return;
            Vec3 front = p.add(Brush.flat(v).scale(0.9));
            if (t % 2 == 0) Brush.ring(front, Brush.flat(v), 0.9, 0.08f, 6, 2, SHIELD);
            SHINE.on(Brush.lineCam(p.subtract(Brush.flat(v).scale(2)), p)).width(0.25f).time(8, 2).hold(0f).tailChase(1f).sparks(0).segments(10).play();
        });
    }

    private static void bash(Ctx c) {
        Entity t = c.target();
        Vec3 p = t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.55, 0);
        Brush.glow(p, 1.8f, 10, BLUE);
        Brush.shock(p, c.look, 0.3, 2.6, 0.2f, 11, CORE);
        Brush.rays(p, 10, 2.2, 0.1f, 9, SHINE);
        Brush.shock((t == null ? p.add(0, -0.9, 0) : t.position()).add(0, 0.08, 0), Brush.UP, 0.4, 3.5, 0.16f, 11, SHIELD);
        Brush.sparks(p, 12, 0.4, STEEL);
    }

    private static void thorns(Ctx c) {
        Entity t = c.target();
        Vec3 p = t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.55, 0);
        Brush.rays(p, 5, 1.1, 0.06f, 7, CORE);
        Brush.sparks(p, 5, 0.25, STEEL);
    }

    /** Colossus: a column of steel light as you grow, and a circle that marks your ground. */
    private static void colossus(Ctx c) {
        Brush.pillar(c.pos, 1.2, 9, 26, 4, CORE);
        Brush.circle(c.pos, 6, 40, 6, CORE, SHINE);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.5, 7, 0.4f, 16, SHIELD);
        Brush.glow(c.chest(), 2.4f, 14, BLUE);
    }

    /** One giant stomp: blast rings, cracks and flying rubble. */
    private static void stomp(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        Brush.shock(g, Brush.UP, 1, 7, 0.4f, 14, CORE);
        Brush.shock(g.add(0, 0.2, 0), Brush.UP, 0.6, 5.5, 0.16f, 11, SHINE);
        SigBerserker.cracks(g, 5, 7);
        SigMiner.debris(g.add(0, 0.3, 0), 16, 0.35);
        Brush.mist(g.add(0, 0.3, 0), 2, 8, 0x5A5A60, 0.45f, 1.2f, 24);
    }
}
