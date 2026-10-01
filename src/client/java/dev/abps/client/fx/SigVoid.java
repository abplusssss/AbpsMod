package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Voidwalker: black ink with a violet rim, swirling disks that fall into nothing, and tears in space. */
final class SigVoid {

    private SigVoid() {
    }

    static final int VIOLET = 0xB388FF, MAGENTA = 0xE040FB, DEEP = 0x4527A0;
    static final Paint VOID = Paint.ink(0x07020F, 0xC9A6FF);
    static final Paint GLOW = Paint.light(VIOLET, 1.2f);
    static final Paint HOT = Paint.light(MAGENTA, 0.9f);

    static final int CUE_TEAR = 11;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> blink(c);
            case 2 -> bolt(c);
            case 3 -> hole(c.aim, 7, Math.max(40, c.ticks), 0.8);
            case 4 -> phase(c);
            case 6 -> horizon(c);
            case CUE_TEAR -> tear(c);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    /** A tear in space: two crescents meeting like a lens standing upright, facing the camera. */
    static void rift(Vec3 c, double h, int life) {
        Vec3 face = Brush.flat(Brush.camera().subtract(c));
        Vec3 side = new Vec3(-face.z, 0, face.x);
        for (int k = -1; k <= 1; k += 2) {
            int kk = k;
            VOID.on(Ribbon.curve((s, t) -> c.add(0, (s - 0.5) * h, 0).add(side.scale(kk * Math.sin(s * Math.PI) * h * 0.22 * (0.4 + 0.6 * Math.min(1, t * 3)))),
                    (s, t) -> side.scale(kk))).width((float) (h * 0.12)).time(life, 3).hold(0.6f).tailChase(0.3f).sparks(0).segments(20).play();
        }
        GLOW.on(Brush.lineCam(c.add(0, -h * 0.45, 0), c.add(0, h * 0.45, 0))).width((float) (h * 0.06)).time(life - 2, 3).hold(0.5f).tailChase(0.4f).sparks(0).play();
        Brush.glow(c, (float) (h * 0.35), life / 2, VIOLET);
    }

    /** Strokes of ink spiralling down into a point, like matter falling into a black hole. */
    static void inflow(Vec3 c, double r, int count, int life) {
        for (int k = 0; k < n(count); k++) {
            double a0 = rnd() * Math.PI * 2, tilt = gauss() * 0.15, r0 = r * (0.7 + rnd() * 0.3);
            Paint p = k % 3 == 0 ? GLOW : VOID;
            p.on(Ribbon.curve((s, t) -> {
                double f = Math.min(1, s * 0.7 + t * 0.6);
                double rr = r0 * (1 - f) + 0.2;
                double a = a0 + f * 5;
                return c.add(Math.cos(a) * rr, tilt * rr, Math.sin(a) * rr);
            }, (s, t) -> Brush.UP)).width((float) (0.08 + r * 0.02)).time(life, life / 2).hold(0.2f).tailChase(1f).sparks(0).segments(24).play();
        }
    }

    /** The heart of a black hole: a dark core with turning rings around it. */
    static void core(Vec3 c, double r, int life) {
        for (int k = 0; k < 3; k++) {
            Vec3 n = rotAbout(rotY(new Vec3(0, 1, 0.3), k * 2.1), new Vec3(1, 0, 0), k * 0.7).normalize();
            Brush.ring(c, n, r, (float) (r * 0.18), life, k % 2 == 0 ? 2.5 : -2.5, k == 0 ? GLOW : VOID);
        }
        sp("glow", c).size((float) r * 2.2f, (float) r * 2.4f).life(life).colors(VIOLET, DEEP).envelope(0.1f, 0.8f, 0.7f);
        // The dark heart itself: solid black smoke balls, so it reads as a hole even in daylight
        for (int k = 0; k < 2; k++) {
            sp("smoke", c).size((float) r * 0.9f, (float) r * 1.0f).life(life + 2).colors(0x05010A, 0x000000).spin((float) (gauss() * 0.05))
                    .envelope(0.1f, 0.85f, 0.95f);
        }
    }

    /** A black hole that lasts life ticks: a core, an accretion disk falling in, and a burst at the end. */
    static void hole(Vec3 ground, double radius, int life, double coreR) {
        Vec3 c = ground.add(0, 0.9, 0);
        Brush.circle(ground, radius, life, 5, GLOW, HOT);
        Brush.pool(ground, radius, life, 0x2A0A5A, 0.35f);
        during(0, life, t -> {
            if (t % 10 == 0) core(c, coreR, 12);
            if (t % 4 == 0) inflow(c, Math.min(radius * 0.7, 5), 2, 18);
            if (t % 3 == 0) {
                Vec3 p = c.add(rndDir().scale(radius * (0.6 + rnd() * 0.4)));
                sp("streak", p).size(0.3f, 0.05f).life(10).vel(c.subtract(p).scale(0.09)).axial().colors(WHITE, VIOLET).envelope(0.05f, 0.4f, 0.9f);
            }
        });
        at(life, () -> {
            Brush.glow(c, (float) radius * 0.35f, 10, VIOLET);
            Brush.shock(ground.add(0, 0.1, 0), Brush.UP, 0.4, radius * 0.7, 0.3f, 12, VOID);
            Brush.dome(c, 0.3, radius * 0.5, 0.12f, 12, GLOW);
            Brush.rays(c, 10, radius * 0.4, 0.1f, 9, HOT);
        });
    }

    // ------------------------------------------------------------------ abilities

    /** Blink: a rift opens where you stand, you streak through, and it closes behind you where you come out. */
    private static void blink(Ctx c) {
        Vec3 from = c.pos.add(0, 1, 0), to = c.aim.add(0, 1, 0);
        rift(from, 2.2, 20);
        Brush.comet(from, to, 3, 0.2f, VOID, () -> {
            rift(to, 2.0, 16);
            Brush.shock(c.aim.add(0, 0.08, 0), Brush.UP, 0.3, 2.2, 0.14f, 10, GLOW);
        });
        Brush.comet(from, to, 3, 0.07f, GLOW, null);
        inflow(from, 1.6, 4, 12);
    }

    /** Void Bolt: a stroke of black light tears through the line, with a violet spiral around it. */
    private static void bolt(Ctx c) {
        Vec3 from = c.hand(), to = c.aim;
        int ticks = (int) Math.max(2, Math.min(6, from.distanceTo(to) / 5));
        Brush.glow(from, 0.7f, 6, VIOLET);
        Brush.comet(from, to, ticks, 0.26f, VOID, () -> {
            Brush.glow(to, 1.2f, 8, VIOLET);
            inflow(to, 1.4, 5, 10);
            Brush.rays(to, 6, 1.4, 0.07f, 8, HOT);
        });
        Brush.comet(from, to, ticks, 0.08f, HOT, null);
        Vec3 d = to.subtract(from);
        Vec3[] uv = axes(d.normalize());
        GLOW.on(Ribbon.curve((s, t) -> from.add(d.scale(s)).add(uv[0].scale(Math.cos(s * 16 + t * 8) * 0.3)).add(uv[1].scale(Math.sin(s * 16 + t * 8) * 0.3)),
                (s, t) -> Brush.faceCam(from.add(d.scale(s)), d.normalize()))).width(0.05f).time(ticks + 10, ticks).hold(0.2f).tailChase(1f).sparks(0).play();
    }

    /** Phase Shift: you fade into ink; rings of void close around you and a dark outline trails as you move. */
    private static void phase(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        int life = Math.max(40, c.ticks);
        rift(c.chest(), 2.4, 14);
        Brush.helix(feet, 0.7, 2.1, 1.5, 0.12f, 16, 0, VOID);
        during(4, life, t -> {
            if (t % 8 == 0) Brush.helix(feet, 0.6, 2.0, 1.2, 0.07f, 14, t * 0.6, t % 16 == 0 ? VOID : GLOW);
            if (t % 3 == 0) {
                Vec3 p = feet.get().add(gauss() * 0.3, rnd() * 1.8, gauss() * 0.3);
                sp("smoke", p).size(0.3f, 0.6f).life(14).colors(0x2A1050, 0x05010A).vel(0, 0.01, 0).envelope(0.1f, 0.4f, 0.55f);
            }
        });
        at(life, () -> rift(feet.get().add(0, 1, 0), 2.0, 12));
    }

    /** Event Horizon: a huge singularity with a lens of bent light around it that collapses at the end. */
    private static void horizon(Ctx c) {
        Vec3 g = c.aim;
        int life = Math.max(60, c.ticks);
        Vec3 core = g.add(0, 0.9, 0);
        Brush.converge(c.hand(), 1.5, 6, 6, 0.06f, GLOW);
        Brush.comet(c.hand(), core, 5, 0.2f, VOID, null);
        hole(g, 10, life, 1.3);
        during(0, life, t -> {
            if (t % 16 == 0) Brush.ring(core, Brush.camera().subtract(core).normalize(), 2.4, 0.2f, 18, t % 32 == 0 ? 1.5 : -1.5, HOT);
        });
        at(life, () -> {
            Brush.shock(g.add(0, 0.1, 0), Brush.UP, 1, 10, 0.5f, 16, VOID);
            Brush.shock(g.add(0, 0.3, 0), Brush.UP, 0.5, 8, 0.2f, 13, GLOW);
            Brush.pillar(g, 1.0, 8, 20, 4, VOID);
        });
    }

    // ------------------------------------------------------------------ cues

    private static void tear(Ctx c) {
        Entity t = c.target();
        Vec3 p = t == null || t.isRemoved() ? c.aim : t.position().add(0, t.getBbHeight() * 0.5, 0);
        rift(p, 1.4, 10);
        Entity me = c.caster();
        Supplier<Vec3> mine = () -> me == null || me.isRemoved() ? c.pos.add(0, 1, 0) : me.position().add(0, 1, 0);
        Supplier<Vec3> theirs = () -> t == null || t.isRemoved() ? p : t.position().add(0, t.getBbHeight() * 0.5, 0);
        Brush.tether(theirs, mine, 0.08f, 8, 0, 0.12, GLOW);
    }
}
