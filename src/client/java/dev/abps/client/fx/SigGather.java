package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/**
 * The four gatherer attributes: Harvester (green and gold), Lumberjack (bark and leaf), Angler (sea blue) and
 * Explorer (gold and teal). Strokes and soft light only, like every other class.
 */
final class SigGather {

    private SigGather() {
    }

    static final int HARVESTER = 19, LUMBERJACK = 20, ANGLER = 21, EXPLORER = 22;

    static void play(Ctx c) {
        switch (c.theme) {
            case HARVESTER -> harvester(c);
            case LUMBERJACK -> lumberjack(c);
            case ANGLER -> angler(c);
            case EXPLORER -> explorer(c);
            default -> {
            }
        }
    }

    static Supplier<Vec3> feet(Ctx c) {
        Entity e = c.caster();
        return () -> e == null || e.isRemoved() ? c.pos : e.position();
    }

    // ------------------------------------------------------------------ Harvester

    static final int GREEN = 0x9CCC65, WHEAT = 0xFFD54F, SPROUT = 0xC5E1A5;
    static final Paint LEAFLIGHT = Paint.light(GREEN, 1.2f), GRAIN = Paint.light(WHEAT, 1.2f), SOFT = Paint.light(SPROUT, 0.8f);

    private static void harvester(Ctx c) {
        switch (c.slot) {
            case 1 -> {
                // Reap: two golden sweeps all the way round at waist height
                double r = Math.max(2, c.ticks) + 0.5;
                Vec3 waist = c.pos.add(0, 0.6, 0);
                GRAIN.on(Ribbon.arc(waist, c.flat(), Brush.UP, r, Math.PI * 2, 3, 1)).width(0.5f).time(12, 8).hold(0.2f).tailChase(0.8f).sparks(2).play();
                LEAFLIGHT.on(Ribbon.arc(waist.add(0, 0.2, 0), c.flat().scale(-1), Brush.UP, r * 0.8, Math.PI * 2, -3, 1)).width(0.3f).time(12, 8).hold(0.2f)
                        .tailChase(0.8f).sparks(0).play();
                Brush.raysUp(c.pos.add(0, 0.2, 0), 10, r * 0.6, 0.07f, 10, SOFT);
            }
            case 2 -> {
                // Growth Pulse: a green wave across the ground and sprouts of light coming up behind it
                double r = Math.max(3, c.ticks);
                Brush.shock(c.pos.add(0, 0.06, 0), Brush.UP, 0.4, r, 0.3f, 16, LEAFLIGHT);
                Brush.glow(c.chest(), 1.4f, 10, GREEN);
                for (int k = 0; k < 14; k++) {
                    double a = rnd() * Math.PI * 2, d = rnd() * r;
                    Vec3 g = c.pos.add(Math.cos(a) * d, 0.05, Math.sin(a) * d);
                    at((int) (d * 2), () -> Brush.helix(() -> g, 0.12, 0.9, 1.2, 0.05f, 12, rnd() * 6, SOFT));
                }
            }
            case 3 -> {
                // Herd Call: rings of sound rolling out from you
                for (int k = 0; k < 3; k++) {
                    int d = k * 4;
                    at(d, () -> Brush.shock(c.chest(), Brush.camera().subtract(c.chest()).normalize(), 0.4, 3.5, 0.14f, 12, GRAIN));
                }
                Brush.shock(c.pos.add(0, 0.06, 0), Brush.UP, 0.5, 20, 0.2f, 24, Paint.light(WHEAT, 0.6f));
            }
            case 4 -> {
                // Harvest Feast: warm light rising round you and everyone near
                Brush.helix(feet(c), 0.7, 2.2, 2, 0.1f, 22, 0, GRAIN);
                Brush.helix(feet(c), 0.7, 2.2, 2, 0.1f, 22, Math.PI, LEAFLIGHT);
                Brush.circle(c.pos, 3, 26, 6, GRAIN, LEAFLIGHT);
                Brush.glow(c.chest(), 1.6f, 14, WHEAT);
            }
            case 6 -> {
                // Season of Plenty: a great circle of grain and leaf on the ground for as long as it lasts
                int life = Math.max(60, c.ticks);
                Brush.circle(c.pos, 12, life, 10, GRAIN, LEAFLIGHT);
                Brush.pool(c.pos, 12, life, 0x33691E, 0.2f);
                Brush.pillar(c.pos, 1, 6, 30, 4, GRAIN);
                Brush.shock(c.pos.add(0, 0.06, 0), Brush.UP, 0.6, 12, 0.35f, 18, LEAFLIGHT);
            }
            case 11 -> {
                // One crop pushed a stage: a little green twist of light out of it
                Vec3 g = c.pos.add(0, 0.1, 0);
                Brush.helix(() -> g, 0.15, 0.7, 1, 0.05f, 10, rnd() * 6, SOFT);
                Brush.glow(g.add(0, 0.3, 0), 0.35f, 6, GREEN);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Lumberjack

    static final int BARK = 0x8D6E63, LEAF = 0xAED581, SAP = 0xFFCC80;
    static final Paint WOOD = Paint.ink(0x2E1F17, BARK), FOLIAGE = Paint.light(LEAF, 1.1f), AMBER = Paint.light(SAP, 1.1f);

    private static void lumberjack(Ctx c) {
        switch (c.slot) {
            case 1 -> {
                // Timber: a heavy chop into the trunk with chips flying off it
                Vec3 at = c.aim;
                Brush.cut(at, c.look, 1.0, 0.4f, 9, 1, AMBER);
                Brush.rays(at, 8, 1.4, 0.08f, 8, Paint.light(BARK, 1f));
                Brush.glow(at, 0.7f, 6, SAP);
            }
            case 11 -> {
                // A tree coming down: a dark sweep from its top toward the ground and leaves bursting off
                Vec3 base = c.pos, top = c.aim;
                double h = Math.max(2, top.y - base.y);
                Vec3 side = c.look.cross(Brush.UP);
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                WOOD.on(Ribbon.arc(base, Brush.UP, side, h, Math.PI * 0.5, 1.2, 1)).width(0.6f).time(16, 10).hold(0.3f).tailChase(0.7f).sparks(0).play();
                Brush.mist(top, 1.4, 12, LEAF, 0.6f, 1.0f, 20);
                Brush.shock(base.add(0, 0.06, 0), Brush.UP, 0.4, 3, 0.25f, 12, Paint.light(BARK, 1f));
                for (int k = 0; k < 6; k++) {
                    Vec3 p = top.add(gauss() * 1.2, gauss() * 0.8, gauss() * 1.2);
                    at(k, () -> Brush.comet(p, p.add(gauss() * 0.8, -2.5, gauss() * 0.8), 10, 0.12f, FOLIAGE, null));
                }
            }
            case 2 -> {
                // Sapling Storm: green shoots of light all around
                Brush.circle(c.pos, 5, 24, 6, FOLIAGE, AMBER);
                Brush.raysUp(c.pos.add(0, 0.1, 0), 16, 2.5, 0.08f, 14, FOLIAGE);
            }
            case 3 -> {
                // Woodsman's Vigor: bark and leaf winding round you while it lasts
                int life = Math.max(20, Math.min(60, c.ticks));
                Brush.helix(feet(c), 0.75, 2.2, 2.5, 0.12f, life, 0, Paint.light(BARK, 1.1f));
                Brush.helix(feet(c), 0.75, 2.2, 2.5, 0.09f, life, Math.PI, FOLIAGE);
                Brush.glow(c.chest(), 1.2f, 10, SAP);
            }
            case 4 -> {
                // Trunk Slam: a dark ring and splinters across the ground
                Brush.shock(c.pos.add(0, 0.06, 0), Brush.UP, 0.4, 4.5, 0.4f, 12, WOOD);
                Brush.raysUp(c.pos.add(0, 0.1, 0), 10, 2.2, 0.1f, 9, AMBER);
                Brush.glow(c.pos.add(0, 0.4, 0), 1.2f, 8, SAP);
            }
            case 6 -> {
                Brush.circle(c.pos, 14, 50, 8, FOLIAGE, AMBER);
                Brush.shock(c.pos.add(0, 0.06, 0), Brush.UP, 0.8, 14, 0.4f, 20, WOOD);
                Brush.pillar(c.pos, 1, 7, 30, 4, FOLIAGE);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Angler

    static final int SEA = 0x4FC3F7, DEEP = 0x0277BD, FOAM = 0xE1F5FE;
    static final Paint WAVE = Paint.light(SEA, 1.2f), ABYSS = Paint.light(DEEP, 1.1f), SPRAY = Paint.light(FOAM, 0.9f);

    /** Rings spreading on the water, one after another. */
    static void ripples(Vec3 at, int count, double r) {
        for (int k = 0; k < count; k++) {
            int d = k * 4;
            at(d, () -> Brush.shock(at.add(0, 0.05, 0), Brush.UP, 0.2, r, 0.12f, 16, d % 8 == 0 ? WAVE : SPRAY));
        }
    }

    private static void angler(Ctx c) {
        switch (c.slot) {
            case 1 -> {
                // Cast Net: the net flies out and lands with ripples and a splash
                Vec3 from = c.hand(), to = c.aim;
                Brush.comet(from, to, 8, 0.25f, WAVE, () -> {
                    ripples(to, 4, 2.5);
                    Brush.raysUp(to, 8, 1.2, 0.07f, 9, SPRAY);
                });
                Brush.comet(from, to, 8, 0.1f, SPRAY, null);
            }
            case 2 -> {
                // Tidal Pull: currents spiralling in to you
                Brush.converge(c.chest(), 10, 12, 14, 0.1f, WAVE);
                Brush.shock(c.pos.add(0, 0.06, 0), Brush.UP, 12, 1, 0.2f, 14, ABYSS);
            }
            case 3 -> {
                // Riptide Dash: a spiral of water left behind you
                Vec3 start = c.chest(), end = start.add(c.look.scale(5));
                Vec3 d = end.subtract(start);
                Vec3[] uv = axes(c.look);
                WAVE.on(Ribbon.curve((s, t) -> start.add(d.scale(s)).add(uv[0].scale(Math.cos(s * 12) * 0.5)).add(uv[1].scale(Math.sin(s * 12) * 0.5)),
                        (s, t) -> Brush.faceCam(start.add(d.scale(s)), c.look))).width(0.2f).time(12, 6).hold(0.2f).tailChase(1f).sparks(0).play();
                Brush.ring(start, c.look, 0.9, 0.14f, 10, 2, SPRAY);
            }
            case 4 -> {
                Brush.helix(feet(c), 0.7, 2.2, 2, 0.1f, 24, 0, WAVE);
                Brush.helix(feet(c), 0.7, 2.2, 2, 0.08f, 24, Math.PI, SPRAY);
                Brush.dome(c.chest(), 0.4, 2.2, 0.1f, 14, ABYSS);
            }
            case 6 -> {
                // Deep Haul: the sea heaves up where the net goes in
                Vec3 to = c.aim;
                Brush.comet(c.hand(), to, 10, 0.4f, WAVE, () -> {
                    ripples(to, 6, 5);
                    Brush.pillar(to, 1.2, 5, 26, 5, WAVE);
                    Brush.raysUp(to, 14, 3, 0.1f, 12, SPRAY);
                });
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Explorer

    static final int GOLD = 0xFFCA28, TEAL = 0x26A69A, PAPER = 0xFFF3E0;
    static final Paint COMPASS = Paint.light(GOLD, 1.2f), TRAIL = Paint.light(TEAL, 1.1f), MAP = Paint.light(PAPER, 0.8f);

    private static void explorer(Ctx c) {
        switch (c.slot) {
            case 1 -> {
                // Treasure Sense: a compass rose on the ground and a faint ring sweeping out 32 blocks
                Vec3 g = c.pos.add(0, 0.06, 0);
                Brush.ring(g, Brush.UP, 2.4, 0.12f, 30, 1, COMPASS);
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI * 2 * k / 8;
                    double len = k % 2 == 0 ? 3.4 : 2.2;
                    Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
                    (k % 2 == 0 ? COMPASS : TRAIL).on(Ribbon.line(g, g.add(o.scale(len)), new Vec3(-o.z, 0, o.x))).width(0.18f).time(30, 6).hold(0.7f)
                            .tailChase(0f).sparks(0).segments(10).play();
                }
                Brush.shock(g, Brush.UP, 1, 32, 0.25f, 30, Paint.light(GOLD, 0.5f));
            }
            case 2 -> {
                // Grapple Leap: a line shot out ahead, and a ring where you took off
                Vec3 from = c.hand(), to = from.add(c.look.scale(9));
                Brush.comet(from, to, 5, 0.12f, TRAIL, null);
                Brush.ring(c.pos.add(0, 0.06, 0), Brush.UP, 1.2, 0.14f, 10, 2, COMPASS);
            }
            case 3 -> {
                // Scout: a lens at your eye and a fan of light ahead
                Brush.ring(c.eye().add(c.look.scale(0.8)), c.look, 0.35, 0.06f, 14, 2, COMPASS);
                Vec3 eye = c.eye();
                for (int k = -3; k <= 3; k++) {
                    Vec3 d = rotY(c.flat(), k * 0.25);
                    MAP.on(Brush.lineCam(eye.add(d.scale(1.2)), eye.add(d.scale(14)))).width(0.08f).time(12, 6).hold(0.1f).tailChase(1f).sparks(0)
                            .segments(12).play();
                }
            }
            case 4 -> {
                // Campfire: a small fire of strokes and a warm circle
                Brush.fire(c.pos.add(c.flat().scale(1.2)), 0.15, 4, 0.5f, 0xFF8F00, 0xFFE082);
                Brush.circle(c.pos, 3, 30, 5, COMPASS, TRAIL);
                Brush.glow(c.pos.add(c.flat().scale(1.2)).add(0, 0.4, 0), 1.2f, 20, 0xFFB74D);
            }
            case 6 -> {
                Brush.helix(feet(c), 0.8, 2.4, 2.5, 0.12f, 26, 0, COMPASS);
                Brush.helix(feet(c), 0.8, 2.4, 2.5, 0.1f, 26, Math.PI, TRAIL);
                Brush.raysUp(c.pos.add(0, 0.2, 0), 12, 3, 0.08f, 12, MAP);
                Brush.shock(c.pos.add(0, 0.06, 0), Brush.UP, 0.5, 6, 0.3f, 14, COMPASS);
            }
            default -> {
            }
        }
    }
}
