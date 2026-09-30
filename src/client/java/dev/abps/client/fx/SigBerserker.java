package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Berserker: rage made visible. Heat, rubble, shockwaves and huge cutting arcs. Red and amber. */
final class SigBerserker {

    private SigBerserker() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> rage(c);
            case 2 -> slam(c);
            case 3 -> whirlwind(c);
            case 4 -> warcry(c);
            case 6 -> executioner(c);
            default -> {
            }
        }
    }

    private static void rage(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 2.0f, 9, c.c1);
        sp("rays", p.add(0, 1.2, 0)).size(0.8f, 3.4f).life(14).colors(WHITE, c.c1).spin(0.05f).envelope(0.05f, 0.4f, 0.85f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 4.5, 12, c.c1, "shockwave");
        c.skin.ground(p, 2.6f, c.c1, 40);
        // Heat pouring off the body: embers and flames climbing, thick dark smoke on top
        during(0, 26, t -> {
            Vec3 b = c.live();
            for (int k = 0; k < n(4); k++) {
                double a = rnd() * Math.PI * 2;
                Vec3 at = b.add(Math.cos(a) * 0.5, rnd() * 1.8, Math.sin(a) * 0.5);
                sp("flame", at).size(0.5f, 0.15f).life(12).colors(lighten(c.c1, 0.7f), mix(c.c1, 0x600000, 0.5f)).vel(0, 0.09 + rnd() * 0.06, 0).flicker(0.3f).envelope(0.05f, 0.5f, 0.9f);
            }
            if (t % 2 == 0) c.skin.mote(b.add(gauss() * 0.4, 1.2, gauss() * 0.4), new Vec3(gauss() * 0.05, 0.12, gauss() * 0.05), 0.12f, 20, c.c2, 0f);
            if (t % 4 == 0) c.skin.body(b.add(gauss() * 0.3, 2.0, gauss() * 0.3), new Vec3(0, 0.04, 0), 0.9f, 20, c.c1, 0.7f);
        });
        for (int k = 0; k < n(10); k++) {
            Vec3 d = new Vec3(gauss(), 0.05, gauss()).normalize();
            c.skin.mote(p.add(0, 0.2, 0), d.scale(0.25 + rnd() * 0.15).add(0, 0.08, 0), 0.16f, 22, c.c1, 0.6f);
        }
    }

    private static void slam(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 0.5, 0), 2.6f, 10, c.c1);
        sp("rays", p.add(0, 0.6, 0)).size(1f, 4.5f).life(10).colors(WHITE, c.c1).spin(0.04f).envelope(0.05f, 0.3f, 0.9f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 8.0, 14, c.c1, "shockwave");
        ringFlat(p.add(0, 0.12, 0), new Vec3(0, 1, 0), 0.3, 5.5, 12, WHITE, "ring");
        c.skin.ground(p, 5.5f, c.c1, 50);
        // A wall of rubble thrown out in every direction, and a pillar of dust
        for (int k = 0; k < n(34); k++) {
            double a = rnd() * Math.PI * 2, sp = 0.15 + rnd() * 0.3;
            c.skin.mote(p.add(Math.cos(a) * 0.8, 0.2, Math.sin(a) * 0.8), new Vec3(Math.cos(a) * sp, 0.25 + rnd() * 0.4, Math.sin(a) * sp), 0.2f, 24, c.c1, 0.6f);
        }
        for (int k = 0; k < n(16); k++) {
            double a = rnd() * Math.PI * 2, r = 1 + rnd() * 3;
            c.skin.body(p.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), new Vec3(Math.cos(a) * 0.08, 0.06, Math.sin(a) * 0.08), 1.4f, 24, c.c1, 0.7f);
        }
        // The path of the leap, a red comet tail coming down
        Vec3 back = c.flat().scale(-1);
        for (int k = 0; k < 8; k++) {
            Vec3 at = p.add(back.scale(k * 0.9)).add(0, k * 0.8, 0);
            sp("glow", at).size(0.9f - k * 0.08f, 0.2f).life(10).colors(lighten(c.c1, 0.5f), c.c2).envelope(0.05f, 0.3f, 0.8f);
            c.skin.along(at, back, 0.3f, c.c1);
        }
    }

    private static void whirlwind(Ctx c) {
        // Three blades of red light whipping round the berserker for the whole spin
        during(0, 64, t -> {
            Vec3 b = c.live();
            for (int arm = 0; arm < 3; arm++) {
                double a = t * 0.62 + arm * Math.PI * 2 / 3;
                Vec3 at = b.add(0, 1.0, 0);
                sp("slash", at).size(3.7f, 3.7f).life(3).colors(WHITE, c.c1).facing(0, 1, 0).startRoll((float) a).envelope(0.1f, 0.4f, 0.95f);
                Vec3 tip = b.add(Math.cos(a + 0.9) * 3.3, 1.0, Math.sin(a + 0.9) * 3.3);
                Vec3 tangent = new Vec3(-Math.sin(a + 0.9), 0, Math.cos(a + 0.9));
                c.skin.slashPiece(tip, tangent, 0.35f, c.c1);
                if (t % 2 == 0) c.skin.mote(tip, tangent.scale(0.28).add(0, 0.03, 0), 0.14f, 14, c.c2, 0.3f);
            }
            if (t % 8 == 0) ringFlat(b.add(0, 0.12, 0), new Vec3(0, 1, 0), 2.0, 4.0, 8, c.c1, "ring");
            if (t % 3 == 0) c.skin.body(b.add(gauss() * 2, 0.3, gauss() * 2), new Vec3(0, 0.03, 0), 1.0f, 14, c.c1, 0.5f);
        });
        Vec3 p = c.live();
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 4.0, 10, c.c1, "shockwave");
    }

    private static void warcry(Ctx c) {
        Vec3 mouth = c.eye().add(c.look.scale(0.6));
        bloom(mouth, 1.0f, 8, c.c1);
        // Sound made visible: rings of force leaving the mouth, and the ground shuddering around
        for (int k = 0; k < 5; k++) {
            int delay = k * 2;
            at(delay, () -> ringFlat(c.eye().add(c.look.scale(0.7)), c.look, 0.4, 9.0, 12, k2(delay, c), "shockwave"));
        }
        Vec3 p = c.live();
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 10.5, 16, c.c1, "shockwave");
        ringFlat(p.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.3, 7.5, 12, c.c2, "ring");
        sp("rays", p.add(0, 1.4, 0)).size(1.0f, 5f).life(12).colors(WHITE, c.c1).spin(0.05f).envelope(0.05f, 0.3f, 0.8f);
        c.skin.ground(p, 4.5f, c.c1, 40);
        for (int k = 0; k < n(30); k++) {
            double a = rnd() * Math.PI * 2, r = 2 + rnd() * 8;
            c.skin.mote(p.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), new Vec3(0, 0.12 + rnd() * 0.15, 0), 0.16f, 20, c.c1, 0.4f);
        }
    }

    private static int k2(int delay, Ctx c) {
        return delay % 4 == 0 ? c.c1 : c.c2;
    }

    private static void executioner(Ctx c) {
        Vec3 f = c.focus();
        Vec3 p = c.live();
        Vec3 dir = c.flat();
        // The axe: a huge crescent hanging over the berserker, then falling
        during(0, 8, t -> {
            double swing = -1.3 + 2.6 * ease(t / 7.0);
            Vec3 at = c.live().add(dir.scale(1.6)).add(0, 2.4 + Math.cos(swing) * 1.5, 0);
            sp("slash", at).size(5.2f, 5.2f).life(3).colors(WHITE, c.c1).facing(c.right().x, 0, c.right().z).startRoll((float) (swing + 1.6)).envelope(0.1f, 0.4f, 1f);
            sp("glow", at).size(2.4f, 1.0f).life(3).colors(lighten(c.c1, 0.5f), c.c1);
            c.skin.along(at, dir, 0.5f, c.c1);
        });
        at(8, () -> {
            bloom(f, 4.0f, 12, c.c1);
            sp("rays", f).size(1.5f, 8f).life(14).colors(WHITE, c.c1).spin(0.04f).envelope(0.05f, 0.3f, 0.9f);
            ringFlat(f.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, 11, 16, c.c1, "shockwave");
            ringFlat(f.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.3, 8, 13, WHITE, "ring");
            ringFlat(f.add(0, 0.18, 0), new Vec3(0, 1, 0), 0.3, 5, 11, c.c2, "shockwave");
            c.skin.ground(new Vec3(f.x, p.y, f.z), 6.5f, c.c1, 60);
            for (int k = 0; k < n(46); k++) {
                double a = rnd() * Math.PI * 2, sp = 0.15 + rnd() * 0.4;
                c.skin.mote(f, new Vec3(Math.cos(a) * sp, 0.3 + rnd() * 0.5, Math.sin(a) * sp), 0.22f, 26, c.c1, 0.6f);
            }
            // A column of light and embers where the blow lands
            during(0, 14, t -> c.skin.column(f, 1.4, 12 * ease((t + 1) / 6.0), c.c1, t));
        });
    }
}
