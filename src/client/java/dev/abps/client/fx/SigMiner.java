package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Miner: amber and gold light, rock debris and dust, sonar pulses through stone. */
final class SigMiner {

    private SigMiner() {
    }

    static final int AMBER = 0xFFC107, GOLD = 0xFFE066, DUST = 0x6D5D4B;
    static final Paint LAMP = Paint.light(AMBER, 1.25f);
    static final Paint GLINT = Paint.light(GOLD, 1.0f);
    static final Paint CRACK = Paint.ink(0x1C140C, 0xFFB300);

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> excavate(c);
            case 2 -> sense(c);
            case 3 -> tunnel(c);
            case 4 -> rush(c);
            case 6 -> shatter(c);
            default -> {
            }
        }
    }

    static void debris(Vec3 at, int count, double speed) {
        for (int k = 0; k < n(count); k++) {
            sp("debris", at.add(gauss() * 0.3, gauss() * 0.3, gauss() * 0.3)).size(0.12f, 0.08f).life(16 + (int) (rnd() * 12))
                    .vel(rndUp().scale(speed * (0.5 + rnd()))).grav(0.05f).drag(0.96f).colors(0x9E9E9E, 0x5D4037).spin((float) (gauss() * 0.3))
                    .envelope(0.02f, 0.8f, 1f);
        }
    }

    /** Excavate: the outline of the 3x3x3 cube flashes gold, then breaks apart into rubble and dust. */
    private static void excavate(Ctx c) {
        Vec3 m = c.aim;
        Brush.comet(c.hand(), m, 3, 0.08f, GLINT, null);
        double h = 1.5;
        int[][] corners = {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}, {-1, 1, -1}, {1, 1, -1}, {1, 1, 1}, {-1, 1, 1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            Vec3 a = m.add(corners[e[0]][0] * h, corners[e[0]][1] * h, corners[e[0]][2] * h);
            Vec3 b = m.add(corners[e[1]][0] * h, corners[e[1]][1] * h, corners[e[1]][2] * h);
            LAMP.on(Brush.lineCam(a, b)).width(0.07f).time(12, 2).hold(0.4f).tailChase(0.4f).sparks(0).segments(8).play();
        }
        at(3, () -> {
            Brush.glow(m, 1.6f, 9, AMBER);
            Brush.rays(m, 10, 2.4, 0.09f, 8, LAMP);
            debris(m, 18, 0.3);
            Brush.mist(m, 0.9, 10, DUST, 0.5f, 1.1f, 26);
        });
    }

    /** Ore Sense: rings of sonar roll out through the ground around you, three in a row. */
    private static void sense(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        for (int k = 0; k < 3; k++) {
            int kk = k;
            at(k * 6, () -> {
                Brush.shock(g, Brush.UP, 0.5, 12, 0.18f - kk * 0.03f, 18, kk == 0 ? LAMP : GLINT.bright(0.7f));
                Brush.dome(c.chest(), 0.5, 6 + kk * 2, 0.08f, 16, GLINT.bright(0.6f));
            });
        }
        Brush.circle(c.pos, 3, 30, 6, LAMP, GLINT);
        Brush.glow(c.eye(), 0.8f, 10, AMBER);
    }

    /** Tunnel Bore: a drill of light rings bores forward one slice a tick, throwing sparks and rubble. */
    private static void tunnel(Ctx c) {
        Vec3 from = c.chest(), to = c.aim;
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 dir = d.scale(1 / len);
        int steps = Math.max(1, c.ticks);
        during(0, steps, t -> {
            Vec3 p = from.add(dir.scale(Math.min(len, (t + 1) * len / steps)));
            Brush.ring(p, dir, 1.6, 0.12f, 8, t % 2 == 0 ? 2.5 : -2.5, t % 2 == 0 ? LAMP : GLINT);
            Brush.sparks(p, 4, 0.35, AMBER);
            if (t % 2 == 0) debris(p, 4, 0.2);
            if (t % 3 == 0) Brush.mist(p, 0.6, 2, DUST, 0.4f, 0.9f, 18);
        });
        LAMP.on(Brush.lineCam(from, to)).width(0.12f).time(steps + 8, steps).hold(0.2f).tailChase(1f).sparks(0).play();
    }

    /** Gold Rush: a fountain of gold light spirals up you, and glitter hangs around you while it lasts. */
    private static void rush(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        int life = Math.max(60, c.ticks);
        Brush.helix(feet, 0.9, 2.4, 2, 0.14f, 18, 0, LAMP);
        Brush.helix(feet, 0.9, 2.4, 2, 0.08f, 18, Math.PI, GLINT);
        Brush.glow(c.chest(), 1.4f, 10, GOLD);
        Brush.raysUp(c.pos.add(0, 0.2, 0), 8, 2.6, 0.08f, 9, GLINT);
        during(10, life, t -> {
            if (t % 3 == 0) {
                Vec3 p = feet.get().add(gauss() * 0.5, 0.3 + rnd() * 1.8, gauss() * 0.5);
                sp("spark", p).size(0.07f, 0.02f).life(14).vel(0, 0.01, 0).colors(WHITE, GOLD).flicker(0.6f).envelope(0.1f, 0.4f, 1f);
            }
            if (t % 40 == 0) Brush.ring(feet.get().add(0, 0.06, 0), Brush.UP, 0.9, 0.06f, 20, 1.5, GLINT);
        });
    }

    /** Earthshatter: a glowing crack tears 14 blocks forward and the ground bursts along it. */
    private static void shatter(Ctx c) {
        Vec3 dir = c.flat(), side = c.right();
        Vec3 start = c.pos.add(0, 0.05, 0);
        Brush.glow(c.chest(), 1.8f, 10, AMBER);
        Brush.shock(start, Brush.UP, 0.4, 4, 0.25f, 12, LAMP);
        CRACK.on(Ribbon.curve((s, t) -> start.add(dir.scale(s * 14.5)).add(side.scale(Math.sin(s * 23) * 0.25)), (s, t) -> side))
                .width(0.3f).time(34, 14).hold(0.6f).tailChase(0.2f).sparks(0).play();
        LAMP.on(Ribbon.curve((s, t) -> start.add(dir.scale(s * 14.5)).add(side.scale(Math.sin(s * 23) * 0.25)).add(0, 0.02, 0), (s, t) -> side))
                .width(0.1f).time(30, 14).hold(0.5f).tailChase(0.2f).sparks(0).play();
        for (int i = 0; i < 14; i++) {
            int ii = i;
            at(i, () -> {
                Vec3 g = start.add(dir.scale(ii + 1));
                Brush.shock(g, Brush.UP, 0.2, 1.8, 0.1f, 8, LAMP);
                debris(g, 6, 0.3);
                Brush.raysUp(g, 3, 1.6, 0.07f, 7, GLINT);
                if (ii % 2 == 0) Brush.mist(g, 0.5, 2, DUST, 0.45f, 1f, 22);
            });
        }
    }
}
