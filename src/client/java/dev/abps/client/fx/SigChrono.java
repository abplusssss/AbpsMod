package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Chronomancer: warm gold light and violet, drawn as clock faces, moving hands and spirals of time. */
final class SigChrono {

    private SigChrono() {
    }

    static final int GOLD = 0xFFD970, VIOLET = 0x9575CD, DEEP = 0x5E35B1;
    static final Paint SUN = Paint.light(GOLD, 1.2f);
    static final Paint DUSK = Paint.light(VIOLET, 1.1f);
    static final Paint FAINT = Paint.light(0xFFF3C4, 0.6f);

    static final int CUE_DEJAVU = 11, CUE_RELEASE = 12, CUE_STOPPED = 13;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> bolt(c);
            case 2 -> rewind(c);
            case 3 -> stasis(c);
            case 4 -> surge(c);
            case 6 -> stop(c);
            case CUE_DEJAVU -> dejaVu(c);
            case CUE_RELEASE -> release(c);
            case CUE_STOPPED -> stopped(c);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    static Supplier<Vec3> mid(Entity e, Vec3 fallback) {
        return () -> e == null || e.isRemoved() ? fallback : e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** One hand of a clock: a stroke from the center outward, turning as it lives. turns is how far it goes round. */
    static void hand(Supplier<Vec3> center, Vec3 n, double length, double start, double turns, float width, int life, Paint p) {
        Vec3[] uv = axes(n);
        p.on((s, time, out) -> {
            double a = start + turns * Math.PI * 2 * time;
            Vec3 dir = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            out[0] = center.get().add(dir.scale(length * s));
            out[1] = dir;
            out[2] = uv[0].scale(-Math.sin(a)).add(uv[1].scale(Math.cos(a)));
        }).width(width).time(life, 3).hold(0.7f).tailChase(0f).sparks(0).segments(10).play();
    }

    /** A clock face: a rim, twelve marks, and two hands that turn (backward when reverse is set). */
    static void clock(Vec3 c, Vec3 n, double r, int life, boolean reverse) {
        Vec3[] uv = axes(n);
        Brush.ring(c, n, r, (float) Math.max(0.08, r * 0.06), life, reverse ? -0.6 : 0.6, SUN);
        Brush.ring(c, n, r * 0.84, (float) Math.max(0.04, r * 0.025), life, reverse ? 0.9 : -0.9, DUSK);
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            Vec3 dir = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            double inner = k % 3 == 0 ? 0.62 : 0.72;
            Vec3 p0 = c.add(dir.scale(r * inner)), p1 = c.add(dir.scale(r * 0.8));
            int delay = k;
            at(delay / 2, () -> (delay % 3 == 0 ? SUN : FAINT).on(Ribbon.line(p0, p1, uv[0].scale(-Math.sin(a)).add(uv[1].scale(Math.cos(a)))))
                    .width((float) Math.max(0.05, r * (delay % 3 == 0 ? 0.05 : 0.03))).time(Math.max(8, life - delay / 2), 2).hold(0.7f).tailChase(0f)
                    .sparks(0).segments(6).play());
        }
        Supplier<Vec3> at = () -> c;
        double dir = reverse ? -1 : 1;
        hand(at, n, r * 0.55, Math.PI / 2, dir * 0.25, (float) Math.max(0.07, r * 0.06), life, SUN);
        hand(at, n, r * 0.75, Math.PI / 2, dir * 1.5, (float) Math.max(0.05, r * 0.035), life, DUSK);
        Brush.glow(c, (float) (r * 0.25), Math.min(life, 20), GOLD);
    }

    /** Time standing still around a point: motes of light that hang in the air without moving. */
    static void stillness(Vec3 c, double r, int count, int life) {
        for (int k = 0; k < n(count); k++) {
            Vec3 p = c.add(rndDir().scale(Math.sqrt(rnd()) * r));
            sp("spark", p).size(0.08f, 0.08f).life(life).colors(k % 2 == 0 ? GOLD : VIOLET, WHITE).flicker(0.3f).envelope(0.15f, 0.8f, 0.9f);
        }
    }

    // ------------------------------------------------------------------ abilities

    /** Time Bolt: a shard of frozen time, wrapped in a violet spiral, that strikes with a ticking clock face. */
    private static void bolt(Ctx c) {
        Vec3 from = c.hand(), to = c.focus();
        int ticks = (int) Math.max(2, Math.min(7, from.distanceTo(to) / 4.5));
        Vec3 d = to.subtract(from);
        Vec3[] uv = axes(d.normalize());
        clock(from, c.look, 0.5, 10, false);
        Brush.comet(from, to, ticks, 0.2f, SUN, () -> {
            Vec3 p = mid(c.target(), to).get();
            Vec3 face = Brush.camera().subtract(p).normalize();
            clock(p, face, 1.1, 18, true);
            Brush.rays(p, 6, 1.4, 0.07f, 8, DUSK);
            stillness(p, 1.2, 10, 24);
        });
        DUSK.on(Ribbon.curve((s, t) -> from.add(d.scale(s)).add(uv[0].scale(Math.cos(s * 18) * 0.22)).add(uv[1].scale(Math.sin(s * 18) * 0.22)),
                (s, t) -> Brush.faceCam(from.add(d.scale(s)), d.normalize()))).width(0.06f).time(ticks + 8, ticks).hold(0.1f).tailChase(1f).sparks(0).play();
    }

    /** Rewind: a ribbon of your own path pulls you back, with a clock running backward where you land. */
    private static void rewind(Ctx c) {
        Vec3 from = c.pos.add(0, 1, 0), to = c.aim.add(0, 1, 0);
        clock(from, Brush.camera().subtract(from).normalize(), 1.0, 16, true);
        Vec3 bend = to.subtract(from).cross(Brush.UP);
        Vec3 b = bend.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : bend.normalize();
        for (int k = 0; k < 3; k++) {
            double off = (k - 1) * 0.4;
            Paint p = k == 1 ? SUN : DUSK;
            p.on(Ribbon.curve((s, t) -> from.lerp(to, s).add(b.scale(Math.sin(s * Math.PI) * off)).add(0, Math.sin(s * Math.PI) * 0.6, 0),
                    (s, t) -> Brush.faceCam(from.lerp(to, s), to.subtract(from).normalize()))).width(k == 1 ? 0.22f : 0.1f).time(16, 5).hold(0.3f)
                    .tailChase(1f).sparks(0).play();
        }
        at(4, () -> {
            clock(c.aim.add(0, 0.06, 0), Brush.UP, 1.6, 24, true);
            Brush.helix(() -> c.aim, 0.8, 2.2, -1.6, 0.1f, 20, 0, SUN);
            Brush.helix(() -> c.aim, 0.8, 2.2, -1.6, 0.06f, 20, Math.PI, DUSK);
            Brush.glow(to, 1.2f, 10, GOLD);
        });
    }

    /** Stasis Field: a dome of slowed time with a clock face on the ground and still motes hanging inside. */
    private static void stasis(Ctx c) {
        Vec3 g = c.aim;
        int life = Math.max(60, c.ticks);
        clock(g.add(0, 0.06, 0), Brush.UP, 5, life, false);
        Brush.pool(g, 5, life, 0x4A3A8A, 0.3f);
        Brush.dome(g.add(0, 0.5, 0), 0.5, 5, 0.1f, 14, DUSK);
        during(0, life, t -> {
            if (t % 30 == 0) {
                // The dome wall turning very slowly
                for (int k = 0; k < 3; k++) {
                    Vec3 n = rotAbout(rotY(new Vec3(0, 0, 1), k * Math.PI / 3 + t * 0.01), new Vec3(1, 0, 0), 0.25 + k * 0.5);
                    Vec3[] uv = axes(n);
                    FAINT.on(Ribbon.arc(g, uv[0], n, 5, Math.PI * 1.8, 0.4, 1.0)).width(0.07f).time(36, 8).hold(0.6f).tailChase(0.3f).sparks(0).play();
                }
            }
            if (t % 6 == 0) stillness(g.add(0, 1.4, 0), 4, 3, 34);
        });
    }

    /** Haste Surge: golden spirals racing around you and streaks of light trailing behind for as long as it lasts. */
    private static void surge(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        int life = Math.max(60, c.ticks);
        clock(c.pos.add(0, 0.06, 0), Brush.UP, 1.8, 18, false);
        Brush.helix(feet, 0.8, 2.2, 2.5, 0.12f, 18, 0, SUN);
        Brush.helix(feet, 0.8, 2.2, 2.5, 0.07f, 18, Math.PI, DUSK);
        during(6, life, t -> {
            if (t % 14 == 0) Brush.helix(feet, 0.7, 2.0, 2.0, 0.06f, 14, t * 0.4, t % 28 == 0 ? SUN : DUSK);
            if (t % 2 == 0 && e != null) {
                Vec3 v = e.getDeltaMovement();
                if (v.horizontalDistanceSqr() > 0.01) {
                    Vec3 p = e.position().add(gauss() * 0.3, 0.3 + rnd() * 1.4, gauss() * 0.3);
                    sp("streak", p).size(0.4f, 0.1f).life(8).vel(v.scale(-0.2)).axial().colors(WHITE, GOLD).envelope(0.05f, 0.3f, 0.9f);
                }
            }
        });
    }

    /** Time Stop: a huge clock face spreads over the ground, a pale wave washes out and everything hangs. */
    private static void stop(Ctx c) {
        Vec3 g = c.pos.add(0, 0.06, 0);
        Brush.converge(c.chest(), 3, 10, 7, 0.07f, SUN);
        at(6, () -> {
            clock(g, Brush.UP, 14, 64, false);
            Brush.glow(c.chest(), 3f, 16, GOLD);
            Brush.dome(c.chest(), 1, 14, 0.25f, 18, FAINT);
            Brush.shock(g, Brush.UP, 1, 14, 0.4f, 18, DUSK);
            Brush.pillar(c.pos, 0.8, 7, 24, 3, SUN);
            stillness(c.chest(), 12, 50, 60);
        });
    }

    // ------------------------------------------------------------------ cues

    private static void dejaVu(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        clock(feet.get().add(0, 1, 0), Brush.camera().subtract(feet.get().add(0, 1, 0)).normalize(), 1.2, 20, true);
        Brush.helix(feet, 0.8, 2.2, -2, 0.12f, 22, 0, SUN);
        Brush.helix(feet, 0.8, 2.2, -2, 0.07f, 22, Math.PI, DUSK);
    }

    private static void stopped(Ctx c) {
        Entity t = c.target();
        Supplier<Vec3> at = mid(t, c.aim);
        int life = Math.max(20, c.ticks);
        float w = t == null ? 0.6f : t.getBbWidth();
        // A frozen ring around the body that holds, and a tiny clock above the head
        for (int k = 0; k < 2; k++) {
            int kk = k;
            DUSK.on((s, time, out) -> {
                Vec3 cc = at.get().add(0, (kk - 0.5) * 0.7, 0);
                double a = s * Math.PI * 2;
                Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                out[0] = cc.add(radial.scale(w + 0.45));
                out[1] = new Vec3(-Math.sin(a), 0, Math.cos(a));
                out[2] = radial;
            }).width(0.08f).time(life, 4).hold(0.85f).tailChase(0.1f).sparks(0).segments(24).play();
        }
        Vec3 head = at.get().add(0, (t == null ? 1.9 : t.getBbHeight()) * 0.5 + 0.7, 0);
        clock(head, Brush.camera().subtract(head).normalize(), 0.45, life, false);
        stillness(at.get(), 0.9, 6, life);
    }

    private static void release(Ctx c) {
        Vec3 p = mid(c.target(), c.aim).get();
        Brush.glow(p, 1.6f, 10, GOLD);
        Brush.shock(p, Brush.camera().subtract(p).normalize(), 0.3, 2.4, 0.16f, 10, SUN);
        Brush.rays(p, 10, 2.0, 0.09f, 9, DUSK);
        Brush.sparks(p, 10, 0.4, GOLD);
    }
}
