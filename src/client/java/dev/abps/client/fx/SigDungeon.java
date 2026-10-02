package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/**
 * Dungeons: mobs climbing out of the floor, rooms opening, loot, traps, and every boss attack. Boss attacks are
 * shown first as a warning on the ground (a ring or a lane in hot red that fills up), then as the hit itself, so
 * players can always see where not to stand. Only Brush strokes and soft light, no picture sprites.
 */
final class SigDungeon {

    private SigDungeon() {
    }

    /** The theme number the server sends dungeon cues with. */
    static final int THEME = 17;

    static final int SPAWN = 11, CLEAR = 12, LOOT = 13, WIN = 14, TRAP_MARK = 15, TRAP_BURST = 16, RING_WARN = 17, RING_HIT = 18,
            LINE_WARN = 19, SOUL_LANCE = 21, ICE_SPIKES = 22, METEOR = 23, BLIZZARD = 24, FLAME_WAVE = 25, ROAR = 26, GATE = 27;

    static final int DANGER = 0xFF3B30, HOT = 0xFF8A3D, GOLD = 0xFFD54F, FIRE = 0xFF6D00, FIRE_CORE = 0xFFD180;
    static final Paint WARN = Paint.light(DANGER, 1.25f);
    static final Paint WARN_SOFT = Paint.light(DANGER, 0.6f);
    static final Paint GILT = Paint.light(GOLD, 1.2f);
    static final Paint FLAME = Paint.light(FIRE, 1.3f);

    static void play(Ctx c) {
        switch (c.slot) {
            case SPAWN -> spawn(c);
            case CLEAR -> clear(c);
            case LOOT -> loot(c);
            case WIN -> win(c);
            case TRAP_MARK -> trapMark(c);
            case TRAP_BURST -> trapBurst(c);
            case RING_WARN -> ringWarn(c);
            case RING_HIT -> ringHit(c);
            case LINE_WARN -> lineWarn(c);
            case SOUL_LANCE -> soulLance(c);
            case ICE_SPIKES -> iceSpikes(c);
            case METEOR -> meteor(c);
            case BLIZZARD -> blizzard(c);
            case FLAME_WAVE -> flameWave(c);
            case ROAR -> roar(c);
            case GATE -> gate(c);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    /** A flat ring on the ground that grows from the middle out to r over life ticks, so you can see when it will go off. */
    static void filling(Vec3 c, double r, int life, Paint p) {
        for (int k = 0; k < 2; k++) {
            double a0 = k * Math.PI;
            p.on(Ribbon.curve((s, t) -> {
                double a = a0 + s * Math.PI * 1.05, rr = Math.max(0.15, r * t);
                return c.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            }, (s, t) -> {
                double a = a0 + s * Math.PI * 1.05;
                return new Vec3(Math.cos(a), 0, Math.sin(a));
            })).width(0.18f).time(life, 2).hold(0.95f).tailChase(0f).sparks(0).segments(24).play();
        }
    }

    /** A ring lying on the ground that stays drawn for life ticks. */
    static void groundRing(Vec3 c, double r, float width, int life, Paint p) {
        Vec3[] uv = axes(Brush.UP);
        for (int k = 0; k < 2; k++) {
            Vec3 mid = uv[0].scale(Math.cos(k * Math.PI)).add(uv[1].scale(Math.sin(k * Math.PI)));
            p.on(Ribbon.arc(c, mid, Brush.UP, r, Math.PI * 1.06, 0, 1)).width(width).time(life, Math.min(5, Math.max(2, life / 4))).hold(0.9f)
                    .tailChase(0f).sparks(0).play();
        }
    }

    /** To the right of a direction, level with the ground. */
    static Vec3 across(Vec3 dir) {
        Vec3 f = Brush.flat(dir);
        return new Vec3(-f.z, 0, f.x);
    }

    // ------------------------------------------------------------------ rooms

    /** A mob climbing out of the floor: an ink circle opens under it and threads of the dungeon's color pull in. */
    private static void spawn(Ctx c) {
        Vec3 g = c.pos.add(0, 0.05, 0);
        groundRing(g, 0.8, 0.22f, 14, Paint.ink(0x0C0A12, c.c1));
        Brush.converge(c.pos.add(0, 1, 0), 1.4, 4, 10, 0.06f, Paint.light(c.c1, 0.9f));
        Brush.mist(c.pos, 0.35, 6, 0x15121C, 0.5f, 0.8f, 18);
    }

    /** A room is cleared: a ring of light washes out from the middle and a streak flies to the door that just opened. */
    private static void clear(Ctx c) {
        Paint p = Paint.light(c.c1, 1.1f);
        Brush.shock(c.pos.add(0, 0.08, 0), Brush.UP, 0.5, 6, 0.28f, 16, p);
        Vec3 door = c.aim;
        Vec3 dir = door.subtract(c.pos);
        Vec3 normal = dir.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : Brush.flat(dir);
        Brush.comet(c.pos.add(0, 1, 0), door, 10, 0.3f, p, () -> {
            Brush.glow(door, 1.6f, 14, c.c1);
            Brush.ring(door, normal, 1.5, 0.14f, 22, 1.0, Paint.light(lighten(c.c1, 0.4f), 1f));
        });
    }

    /** Treasure: gold rays shooting up and a gold ring around the chest. */
    private static void loot(Ctx c) {
        Vec3 g = c.pos;
        Brush.raysUp(g, 10, 3, 0.1f, 14, GILT);
        Brush.ring(g.add(0, 0.06, 0), Brush.UP, 1.4, 0.16f, 24, 1.5, GILT);
        Brush.helix(() -> g, 0.6, 1.8, 1.5, 0.1f, 20, 0, Paint.light(lighten(GOLD, 0.3f), 1f));
        Brush.glow(g.add(0, 0.8, 0), 1.4f, 16, GOLD);
    }

    /** The boss is down: a pillar of light, a golden circle on the floor and a big ring rolling out. */
    private static void win(Ctx c) {
        Paint p = Paint.light(c.c1, 1.2f);
        Brush.pillar(c.pos, 1.2, 10, 50, 5, p);
        Brush.circle(c.pos, 4, 60, 7, GILT, p);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.5, 9, 0.38f, 20, GILT);
        at(10, () -> Brush.raysUp(c.pos.add(0, 0.2, 0), 14, 5, 0.12f, 18, GILT));
        at(18, () -> Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.4, 6, 0.22f, 16, p));
    }

    /** A floor tile about to go off: a red ring on it and a soft red glow, for as long as the warning lasts. */
    private static void trapMark(Ctx c) {
        int warn = Math.max(6, c.ticks);
        Vec3 g = c.pos.add(0, 0.04, 0);
        groundRing(g, 0.42, 0.14f, warn + 2, WARN);
        Brush.pool(g, 0.45, warn, DANGER, 0.35f);
    }

    /** The tile goes off: a red spike of light and a few sparks up out of it. */
    private static void trapBurst(Ctx c) {
        Vec3 g = c.pos;
        Brush.spike(g, g.add(gauss() * 0.1, 1.3 + rnd() * 0.4, gauss() * 0.1), 0.32f, 10, Paint.light(DANGER, 1.2f));
        Brush.raysUp(g.add(0, 0.1, 0), 4, 1.2, 0.07f, 7, Paint.light(HOT, 1f));
        Brush.glow(g.add(0, 0.3, 0), 0.7f, 6, HOT);
    }

    // ------------------------------------------------------------------ boss warnings and hits

    /** Where a ring attack lands: a red edge right away, and a fill growing out to it that reaches the edge when it hits. */
    private static void ringWarn(Ctx c) {
        int warn = Math.max(6, c.ticks);
        double r = Math.max(0.5, c.aim.distanceTo(c.pos));
        Vec3 g = c.pos.add(0, 0.06, 0);
        groundRing(g, r, 0.26f, warn + 2, WARN);
        filling(g.add(0, 0.01, 0), r, warn, Paint.light(c.c2, 0.8f));
        Brush.pool(g, r, warn, DANGER, 0.22f);
    }

    /** The ring goes off: a shockwave across it and rays out of the ground. */
    private static void ringHit(Ctx c) {
        double r = Math.max(0.5, c.aim.distanceTo(c.pos));
        Paint p = Paint.light(c.c2, 1.2f);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, r * 0.3, r * 1.1, 0.32f, 10, p);
        Brush.raysUp(c.pos.add(0, 0.1, 0), 8, Math.max(1.5, r * 0.6), 0.09f, 8, Paint.light(HOT, 1f));
        Brush.glow(c.pos.add(0, 0.5, 0), (float) (r * 0.6), 8, c.c2);
    }

    /** Where a line attack lands: two red edges along a lane, and a stroke down the middle that reaches the end when it hits. */
    private static void lineWarn(Ctx c) {
        int warn = Math.max(6, c.ticks);
        Vec3 a = c.pos.add(0, 0.06, 0), b = c.aim.add(0, 0.06, 0);
        if (b.distanceToSqr(a) < 0.25) return;
        Vec3 side = across(b.subtract(a));
        double half = 1.35;
        for (int k = -1; k <= 1; k += 2) {
            Vec3 o = side.scale(half * k);
            WARN.on(Ribbon.line(a.add(o), b.add(o), side)).width(0.24f).time(warn + 2, 4).hold(0.9f).tailChase(0f).sparks(0).play();
        }
        Paint.light(c.c2, 0.8f).on(Ribbon.line(a, b, side)).width(0.5f).time(warn + 1, warn).hold(0.95f).tailChase(0f).sparks(0).play();
        WARN_SOFT.on(Ribbon.line(a.add(side.scale(-half)), a.add(side.scale(half)), b.subtract(a))).width(0.2f).time(warn + 2, 3).hold(0.9f)
                .tailChase(0f).sparks(0).play();
    }

    /** Hollow King: a lance of soul fire races along the lane and soul flames burst up behind it. */
    private static void soulLance(Ctx c) {
        Vec3 a = c.pos.add(0, 1, 0), b = c.aim.add(0, 1, 0);
        double len = a.distanceTo(b);
        if (len < 0.5) return;
        Brush.comet(a, b, 6, 0.6f, Paint.ink(0x061412, c.c2), null);
        Brush.comet(a, b, 6, 0.22f, Paint.light(lighten(c.c2, 0.4f), 1.1f), () -> Brush.glow(b, 1.2f, 8, c.c2));
        int steps = (int) Math.min(18, len);
        for (int k = 1; k <= steps; k++) {
            Vec3 g = c.pos.lerp(c.aim, k / (double) steps);
            at(k * 6 / Math.max(1, steps), () -> Brush.spike(g, g.add(gauss() * 0.15, 1.0 + rnd() * 0.6, gauss() * 0.15), 0.26f, 10, Paint.light(c.c2, 1.1f)));
        }
    }

    /** Glacial Warden: a crack runs down the lane and ice spikes burst out of it one after another. */
    private static void iceSpikes(Ctx c) {
        Vec3 a = c.pos.add(0, 0.05, 0), b = c.aim.add(0, 0.05, 0);
        double len = a.distanceTo(b);
        if (len < 0.5) return;
        Vec3 dir = b.subtract(a).normalize(), side = across(dir);
        SigCryo.BLUE.on(Ribbon.line(a, b, side)).width(0.2f).time(24, 8).hold(0.6f).tailChase(0.3f).sparks(0).play();
        int steps = (int) Math.min(18, len);
        for (int k = 0; k < steps; k++) {
            int s = k;
            at(k / 2, () -> {
                Vec3 g = a.add(dir.scale(s + 1));
                SigCryo.cluster(g, 1.6, 3, 24, side.scale(rnd() < 0.5 ? 1 : -1));
                Brush.glow(g.add(0, 0.3, 0), 0.6f, 6, SigCryo.ICE);
            });
        }
    }

    /** Infernal Colossus: a burning rock falls out of the dark and lands on the warning ring as it goes off. */
    private static void meteor(Ctx c) {
        int warn = Math.max(12, c.ticks);
        double r = Math.max(1, c.aim.distanceTo(c.pos));
        Vec3 g = c.pos;
        Vec3 sky = g.add(-4 + gauss(), 16, -3 + gauss());
        at(warn - 10, () -> {
            Brush.comet(sky, g, 10, 0.8f, FLAME, () -> {
                Brush.fire(g, r * 0.35, 6, 0.6f, FIRE, FIRE_CORE);
                Brush.shock(g.add(0, 0.1, 0), Brush.UP, 0.3, r * 1.1, 0.32f, 10, Paint.light(HOT, 1.2f));
                Brush.raysUp(g.add(0, 0.1, 0), 7, r * 0.8, 0.1f, 8, FLAME);
                Brush.embers(g.add(0, 0.4, 0), 12, 0.2, FIRE);
                Brush.mist(g, 0.6, 9, 0x1A1410, 0.6f, 1.2f, 24);
            });
            Brush.comet(sky, g, 10, 0.3f, Paint.light(FIRE_CORE, 1.1f), null);
        });
    }

    /** Glacial Warden: a storm over one spot. Wind bands circle it and snow drives down until it ends. */
    private static void blizzard(Ctx c) {
        int life = Math.max(40, c.ticks);
        double r = Math.max(2, c.aim.distanceTo(c.pos));
        Vec3 g = c.pos;
        Brush.circle(g, r, life, 6, SigCryo.FROST, SigCryo.BLUE);
        Brush.pool(g, r, life, 0x1E5AA8, 0.25f);
        during(0, life, t -> {
            if (t % 5 == 0) {
                for (int k = 0; k < 2; k++) {
                    double y = 0.4 + rnd() * 3.2, rr = r * (0.4 + rnd() * 0.6);
                    Vec3 mid = rotY(new Vec3(1, 0, 0), rnd() * Math.PI * 2);
                    Paint p = rnd() < 0.3 ? SigCryo.FROST : SigCryo.SNOW;
                    p.on(Ribbon.arc(g.add(0, y, 0), mid, Brush.UP, rr, 1.6, 2.2, 0.9)).width(0.14f).time(16, 4).hold(0.2f).tailChase(0.8f).sparks(0)
                            .segments(16).play();
                }
            }
            if (t % 3 == 0) {
                for (int k = 0; k < n(3); k++) {
                    double a = rnd() * Math.PI * 2, rr = Math.sqrt(rnd()) * r;
                    Vec3 top = g.add(Math.cos(a) * rr, 3 + rnd() * 1.5, Math.sin(a) * rr);
                    Vec3 bottom = top.add(0.6 + gauss() * 0.2, -2.5, 0.3 + gauss() * 0.2);
                    SigCryo.SNOW.on(Brush.lineCam(top, bottom)).width(0.12f).time(8, 3).hold(0.1f).tailChase(1f).sparks(0).segments(8).play();
                }
            }
        });
    }

    /** Infernal Colossus: a wave of flame rolls out in a ring. It moves as fast as the damage does, so jumping it works. */
    private static void flameWave(Ctx c) {
        int life = Math.max(10, c.ticks);
        double max = Math.max(3, c.aim.distanceTo(c.pos));
        Vec3 g = c.pos.add(0, 0.1, 0);
        during(0, life, t -> {
            if (t % 2 != 0) return;
            double rr = Math.min(max, 1 + t / 2.0);
            groundRing(g, rr, 0.5f, 5, FLAME);
            int count = Math.min(n(6), 2 + (int) (rr / 2));
            for (int k = 0; k < count; k++) {
                double a = rnd() * Math.PI * 2;
                Brush.flame(g.add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.9 + rnd() * 0.4, 0.35f, 7, FIRE, FIRE_CORE);
            }
        });
    }

    /** A boss roars (enraged or calling help): a ring bursts from its head toward you and another rolls along the floor. */
    private static void roar(Ctx c) {
        Entity boss = c.caster();
        Vec3 feet = boss == null || boss.isRemoved() ? c.pos : boss.position();
        Vec3 head = boss == null || boss.isRemoved() ? c.pos.add(0, 2.5, 0) : boss.position().add(0, boss.getBbHeight() * 0.85, 0);
        Paint p = Paint.light(c.c2, 1.2f);
        Brush.shock(head, Brush.camera().subtract(head).normalize(), 0.4, 4, 0.26f, 12, p);
        Brush.rays(head, 10, 3, 0.1f, 10, p);
        Brush.shock(feet.add(0, 0.1, 0), Brush.UP, 0.5, 7, 0.3f, 14, Paint.light(lighten(c.c2, 0.3f), 1f));
    }

    /** A world gate: two rings turning in the arch. When someone walks in, light pulls into it and bursts. */
    private static void gate(Ctx c) {
        Vec3 o = c.pos;
        Vec3 n = c.look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : c.look;
        Paint p = Paint.light(c.c1, 0.9f);
        Brush.ring(o, n, 1.2, 0.14f, 34, 1.2, p);
        Brush.ring(o, n, 0.7, 0.09f, 34, -1.6, Paint.light(lighten(c.c1, 0.5f), 0.8f));
        Brush.glow(o, 1.0f, 24, c.c1);
        if (c.ticks == 1) {
            Brush.converge(o, 2.6, 8, 14, 0.07f, Paint.light(lighten(c.c1, 0.3f), 1f));
            at(12, () -> {
                Brush.shock(o, n, 0.3, 2.4, 0.24f, 12, Paint.light(c.c1, 1.2f));
                Brush.glow(o, 2f, 12, c.c1);
            });
        }
    }
}
