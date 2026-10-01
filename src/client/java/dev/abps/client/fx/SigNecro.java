package dev.abps.client.fx;

import dev.abps.client.fx.Brush.Paint;
import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Necromancer: dark ink with a ghostly teal rim, streams of souls, and graves that open in light. */
final class SigNecro {

    private SigNecro() {
    }

    static final int TEAL = 0x64FFDA, VIOLET = 0x7C4DFF;
    static final Paint INK = Paint.ink(0x100A20, 0x64FFDA);
    static final Paint SOUL = Paint.light(TEAL, 1.2f);
    static final Paint SPIRIT = Paint.light(VIOLET, 1.0f);

    static final int CUE_EXPLODE = 11, CUE_COMMAND = 13, CUE_SOUL = 14;

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> army(c);
            case 2 -> drain(c);
            case 3 -> command(c);
            case 4 -> knight(c);
            case 6 -> ult(c);
            case CUE_EXPLODE -> burst(c.pos);
            case CUE_COMMAND -> Brush.comet(c.pos, c.aim, 5, 0.07f, SPIRIT, () -> Brush.glow(c.aim, 0.5f, 5, VIOLET));
            case CUE_SOUL -> soul(c);
            default -> {
            }
        }
    }

    static Supplier<Vec3> mid(Entity e, Vec3 fallback) {
        return () -> e == null || e.isRemoved() ? fallback : e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /** Something rising out of the ground: a ring breaks open, ink spikes and teal light climb out. */
    static void rise(Vec3 g, double h) {
        Brush.shock(g.add(0, 0.08, 0), Brush.UP, 0.2, 1.4, 0.1f, 10, SOUL);
        Brush.raysUp(g.add(0, 0.1, 0), 5, h * 0.7, 0.07f, 9, INK);
        Brush.pillar(g, 0.35, h, 14, 2, SOUL);
        Brush.mist(g.add(0, 0.3, 0), 0.4, 3, 0x1A2A2A, 0.4f, 0.8f, 20);
    }

    /** Raise Army: a grave circle opens and each minion climbs out of its own pool of light. */
    private static void army(Ctx c) {
        Entity e = c.caster();
        Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        int n = Math.max(2, c.ticks);
        Brush.circle(c.pos, 5, 60, 7, INK, SOUL);
        Brush.glow(c.chest(), 1.4f, 10, TEAL);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n, r = i % 2 == 0 ? 2.0 : 3.0;
            at(i * 2, () -> rise(feet.get().add(Math.cos(a) * r, 0, Math.sin(a) * r), 2.6));
        }
    }

    /** Soul Drain: a stream of souls pulled out of the target and into you. */
    private static void drain(Ctx c) {
        Supplier<Vec3> from = mid(c.target(), c.focus()), to = mid(c.caster(), c.chest());
        Brush.tether(from, to, 0.18f, 18, 0.2, 0.25, INK);
        Brush.tether(from, to, 0.08f, 16, 0.15, 0.15, SOUL);
        Brush.glow(from.get(), 1.2f, 9, TEAL);
        Brush.rays(from.get(), 6, 1.4, 0.07f, 8, INK);
        at(8, () -> {
            Brush.glow(to.get(), 0.9f, 8, TEAL);
            Brush.helix(() -> to.get().add(0, -1, 0), 0.6, 2, 1.5, 0.07f, 14, 0, SOUL);
        });
    }

    /** Command: chains of ink lock around the target while your minions' lines of sight converge on it. */
    private static void command(Ctx c) {
        Supplier<Vec3> t = mid(c.target(), c.focus());
        for (int round = 0; round < 4; round++) {
            at(round * 20, () -> {
                for (int k = 0; k < 2; k++) {
                    Vec3 n = rotAbout(Brush.UP, rotY(new Vec3(1, 0, 0), rnd() * 6.28), 0.5);
                    Brush.ring(t.get(), n, 0.9, 0.09f, 22, k == 0 ? 2 : -2, k == 0 ? INK : SPIRIT);
                }
            });
        }
        Brush.comet(c.hand(), t.get(), 4, 0.08f, SPIRIT, () -> Brush.glow(t.get(), 1f, 8, VIOLET));
    }

    /** Death Knight: a great grave opens, a column of soul fire bursts up and the knight steps out of it. */
    private static void knight(Ctx c) {
        Vec3 g = c.aim;
        Brush.circle(g, 4, 50, 5, INK, SOUL);
        Brush.shock(g.add(0, 0.1, 0), Brush.UP, 0.4, 4.5, 0.3f, 14, INK);
        Brush.pillar(g, 1.0, 9, 26, 4, SOUL);
        Brush.raysUp(g.add(0, 0.2, 0), 10, 3.5, 0.1f, 11, INK);
        Brush.dome(g.add(0, 1.2, 0), 0.4, 3, 0.12f, 14, SPIRIT);
        Brush.mist(g.add(0, 0.4, 0), 1.2, 10, 0x102020, 0.45f, 1.1f, 30);
    }

    /** Army of the Damned: a wave of souls rolls out from you; each victim gets its own soul strike (cue). */
    private static void ult(Ctx c) {
        Vec3 g = c.pos.add(0, 0.08, 0);
        Brush.circle(c.pos, 10, 50, 7, INK, SOUL);
        Brush.shock(g, Brush.UP, 1, 10, 0.5f, 16, INK);
        Brush.shock(g.add(0, 0.3, 0), Brush.UP, 0.6, 9, 0.2f, 13, SOUL);
        Brush.pillar(c.pos, 1.0, 8, 26, 4, INK);
        Brush.glow(c.chest(), 2.4f, 14, TEAL);
        Brush.dome(c.chest(), 0.6, 4, 0.14f, 14, SPIRIT);
    }

    private static void soul(Ctx c) {
        Supplier<Vec3> t = mid(c.target(), c.aim);
        Brush.comet(c.pos, t.get(), 5, 0.14f, SOUL, () -> {
            Brush.glow(t.get(), 1.2f, 8, TEAL);
            Brush.rays(t.get(), 6, 1.4, 0.07f, 8, INK);
        });
    }

    static void burst(Vec3 at) {
        Vec3 p = at.add(0, 0.8, 0);
        Brush.glow(p, 1.4f, 9, TEAL);
        Brush.shock(at.add(0, 0.1, 0), Brush.UP, 0.3, 3.5, 0.2f, 11, INK);
        Brush.rays(p, 8, 2, 0.09f, 8, SOUL);
        Brush.mist(p, 0.6, 5, 0x102020, 0.45f, 1f, 20);
    }
}
