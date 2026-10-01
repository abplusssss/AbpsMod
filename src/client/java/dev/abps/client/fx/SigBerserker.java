package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Berserker: hot red and orange light, heavy ground hits, sweeping blade arcs. */
final class SigBerserker {

    private SigBerserker() {
    }

    static final int RED = 0xFF2D3A, ORANGE = 0xFF8A1E, DUST = 0x5A4636;
    static final Paint FURY = Paint.light(RED, 1.4f);
    static final Paint FIRE = Paint.light(ORANGE, 1.2f);
    static final Paint DARK = Paint.ink(0x1A0505, 0xFF4A3A);

    static final int CUE_SLAM = 11, CUE_HIT = 12, CUE_FURY = 13, CUE_EXECUTE = 14;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> rage(c);
            case 2 -> leap(c);
            case 3 -> whirlwind(c);
            case 4 -> warcry(c);
            case 6 -> execute(c);
            case CUE_SLAM -> slam(c.pos, Math.max(2, c.ticks / 10.0));
            case CUE_HIT -> hit(c);
            case CUE_FURY -> fury(c);
            case CUE_EXECUTE -> cleave(c);
            default -> {
            }
        }
    }

    static Supplier<Vec3> feet(Ctx c) {
        Entity e = c.caster();
        return () -> e == null || e.isRemoved() ? c.pos : e.position();
    }

    /** Cracks running out across the ground from a point. */
    static void cracks(Vec3 g, double r, int count) {
        for (int k = 0; k < count; k++) {
            double a = rnd() * Math.PI * 2;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 bend = new Vec3(-d.z, 0, d.x).scale(gauss() * 0.4);
            double len = r * (0.6 + rnd() * 0.4);
            DARK.on(Ribbon.curve((s, t) -> g.add(d.scale(s * len)).add(bend.scale(Math.sin(s * 3) * len * 0.15)).add(0, 0.04, 0), (s, t) -> d))
                    .width(0.12f).time(30, 3).hold(0.6f).tailChase(0.2f).sparks(0).segments(14).play();
        }
    }

    /** A heavy hit on the ground: blast rings, cracks, flying debris and dust. */
    static void slam(Vec3 at, double r) {
        Vec3 g = at.add(0, 0.08, 0);
        Brush.glow(g.add(0, 0.5, 0), (float) r * 0.5f, 10, ORANGE);
        Brush.shock(g, Brush.UP, 0.5, r * 1.3, (float) (0.12 + r * 0.06), 13, FIRE);
        Brush.shock(g.add(0, 0.2, 0), Brush.UP, 0.3, r, 0.12f, 10, FURY);
        cracks(g, r, 8);
        Brush.raysUp(g, 8, r * 0.6, 0.1f, 9, FURY);
        for (int k = 0; k < n(14); k++) {
            sp("debris", g.add(gauss() * 0.4, 0.2, gauss() * 0.4)).size(0.14f, 0.1f).life(18 + (int) (rnd() * 10)).vel(rndUp().scale(0.25 + rnd() * 0.2))
                    .grav(0.05f).drag(0.96f).colors(0x8D6E63, 0x4E342E).spin((float) (gauss() * 0.3)).envelope(0.02f, 0.8f, 1f);
        }
        Brush.mist(g.add(0, 0.3, 0), r * 0.4, 8, DUST, 0.45f, 1.1f, 24);
    }

    /** Rage: a roar of red light bursts off you, and red flares lick up your body while it lasts. */
    private static void rage(Ctx c) {
        Supplier<Vec3> feet = feet(c);
        int life = Math.max(60, c.ticks);
        Brush.glow(c.chest(), 1.8f, 10, RED);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.4, 4, 0.25f, 12, FURY);
        Brush.pillar(c.pos, 0.6, 4.5, 16, 3, FURY);
        Brush.raysUp(c.chest(), 8, 2.5, 0.09f, 9, FIRE);
        during(8, life, t -> {
            if (t % 16 == 0) Brush.helix(feet, 0.55, 2.0, 1.2, 0.08f, 14, t * 0.9, t % 32 == 0 ? FURY : FIRE);
            if (t % 3 == 0) Brush.embers(feet.get().add(gauss() * 0.3, 0.5 + rnd(), gauss() * 0.3), 1, 0.05, ORANGE);
        });
    }

    /** Leap: the ground blasts under you and a red streak follows you through the air. */
    private static void leap(Ctx c) {
        Supplier<Vec3> feet = feet(c);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.3, 2.6, 0.18f, 10, FIRE);
        Brush.mist(c.pos.add(0, 0.2, 0), 0.6, 5, DUST, 0.4f, 0.9f, 18);
        Entity e = c.caster();
        during(0, 22, t -> {
            Vec3 p = feet.get().add(0, 1, 0);
            Vec3 v = e == null ? c.look : e.getDeltaMovement();
            if (v.lengthSqr() < 0.02) return;
            FURY.on(Brush.lineCam(p.subtract(v.normalize().scale(1.6)), p)).width(0.2f).time(8, 2).hold(0f).tailChase(1f).sparks(0).segments(10).play();
        });
    }

    /** Whirlwind: blade arcs sweep round you at chest height for the whole spin. */
    private static void whirlwind(Ctx c) {
        Supplier<Vec3> feet = feet(c);
        int life = Math.max(40, c.ticks);
        during(0, life, t -> {
            if (t % 3 != 0) return;
            Vec3 center = feet.get().add(0, 1.0 + Math.sin(t * 0.4) * 0.25, 0);
            Vec3 mid = rotY(new Vec3(1, 0, 0), t * 0.9);
            Vec3 n = rotAbout(Brush.UP, mid, Math.sin(t * 0.3) * 0.25);
            Paint p = t % 6 == 0 ? FURY : FIRE;
            p.on(Ribbon.arc(center, mid, n, 2.6, 2.6, 2.2, 1.05)).width(0.32f).time(9, 2).hold(0.2f).tailChase(0.6f).sparks(1).segments(20).play();
            if (t % 12 == 0) Brush.shock(feet.get().add(0, 0.1, 0), Brush.UP, 0.8, 3.6, 0.1f, 10, FIRE.bright(0.5f));
        });
    }

    /** War Cry: a wall of red light rolls out 10 blocks and a circle is branded on the ground. */
    private static void warcry(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        Brush.glow(c.chest(), 2.2f, 12, RED);
        Brush.shock(g, Brush.UP, 1, 10, 0.45f, 16, FURY);
        Brush.shock(g.add(0, 0.3, 0), Brush.UP, 0.6, 8, 0.18f, 13, FIRE);
        Brush.dome(c.chest(), 0.6, 4, 0.14f, 12, FIRE);
        Brush.circle(c.pos, 10, 36, 6, FURY, FIRE);
        Brush.raysUp(c.chest(), 12, 3.4, 0.1f, 10, FURY);
        Supplier<Vec3> feet = feet(c);
        during(10, Math.max(60, c.ticks), t -> {
            if (t % 4 == 0) Brush.embers(feet.get().add(gauss() * 0.3, 0.3 + rnd() * 1.4, gauss() * 0.3), 1, 0.04, RED);
        });
    }

    /** Executioner: a red mark locks on the target and a line of light ties you to it as you leap. */
    private static void execute(Ctx c) {
        Entity t = c.target();
        Supplier<Vec3> tm = () -> t == null || t.isRemoved() ? c.focus() : t.position().add(0, t.getBbHeight() * 0.55, 0);
        Supplier<Vec3> me = () -> feet(c).get().add(0, 1, 0);
        Brush.tether(me, tm, 0.06f, 24, 0, 0.05, FURY);
        Vec3 g = t == null ? c.focus().add(0, -0.9, 0) : t.position();
        Brush.ring(g.add(0, 0.08, 0), Brush.UP, 1.6, 0.12f, 24, 2, FURY);
        Brush.ring(g.add(0, 0.08, 0), Brush.UP, 1.0, 0.08f, 24, -2.5, FIRE);
        Brush.shock(c.pos.add(0, 0.1, 0), Brush.UP, 0.3, 2.6, 0.18f, 10, FIRE);
    }

    /** The Executioner's landing: one huge downward cut, then the ground breaks. */
    private static void cleave(Ctx c) {
        Entity t = c.target();
        Vec3 at = t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.55, 0);
        Vec3 dir = Brush.flat(at.subtract(c.pos));
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 normal = rotAbout(side, dir, 0.25);
        FURY.on(Ribbon.arc(at.add(0, 0.6, 0), rotAbout(dir, normal, 0.6), normal, 2.6, 3.0, -1.2, 1.1)).width(0.6f).time(14, 2).hold(0.3f).sparks(2).play();
        DARK.on(Ribbon.arc(at.add(0, 0.6, 0), rotAbout(dir, normal, 0.6), normal, 2.3, 2.6, -1.1, 1.08)).width(0.4f).time(13, 2).hold(0.3f).sparks(0).play();
        at(2, () -> {
            slam(at.add(0, -0.9, 0), 3.5);
            FURY.on(Brush.lineCam(at.add(0, 8, 0), at.add(0, -0.9, 0))).width(0.5f).time(12, 2).hold(0.3f).tailChase(0.6f).sparks(0).play();
        });
    }

    /** A melee hit: a few red sparks, more with more fury, and a ring of stars when it stuns. */
    private static void hit(Ctx c) {
        Entity t = c.target();
        Vec3 at = t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.55, 0);
        int stacks = c.ticks % 100;
        Brush.sparks(at, 3 + stacks, 0.3, stacks >= 4 ? RED : ORANGE);
        if (c.ticks >= 100) {
            Vec3 head = at.add(0, (t == null ? 1.9 : t.getBbHeight()) * 0.5 + 0.3, 0);
            Brush.ring(head, Brush.UP, 0.45, 0.06f, 20, 3, FIRE);
        }
    }

    private static void fury(Ctx c) {
        Supplier<Vec3> feet = feet(c);
        Brush.helix(feet, 0.7, 2.2, 1.6, 0.12f, 18, 0, FURY);
        Brush.helix(feet, 0.7, 2.2, 1.6, 0.07f, 18, Math.PI, FIRE);
        Brush.glow(c.chest(), 1.2f, 8, RED);
    }
}
