package dev.abps.client.fx;

import dev.abps.client.fx.Signatures.Ctx;
import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;
import static dev.abps.client.fx.Signatures.*;

/** Vampire: blood, bats and a red moon. Crimson over royal purple. */
final class SigVampire {

    private SigVampire() {
    }

    static void play(Ctx c) {
        switch (c.slot) {
            case 1 -> curse(c);
            case 2 -> bind(c);
            case 3 -> burst(c);
            case 4 -> moon(c);
            case 6 -> feast(c);
            default -> {
            }
        }
    }

    /** A bat that flies from one point to another along a wobbling path and dissolves into mist on arrival. */
    private static void bat(Vec3 from, Vec3 to, int life, float size, Ctx c) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        Vec3 d = len < 1.0e-4 ? c.look : dir.scale(1 / len);
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        Vec3 s = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        double phase = rnd() * 6.28, amp = 0.4 + rnd() * 0.9;
        sp("bat", from).size(size, size).life(life).colors(mix(c.c1, 0x100010, 0.85f), 0x000000).drag(1f).envelope(0.1f, 0.75f, 1f).bright()
                .motion((p, age) -> {
                    double t = Math.min(1.0, (age + 1) / (double) life);
                    Vec3 pos = from.lerp(to, t).add(s.scale(Math.sin(t * 9 + phase) * amp)).add(0, Math.cos(t * 7 + phase) * amp * 0.6, 0);
                    p.steer(pos.x, pos.y, pos.z);
                });
    }

    private static void curse(Ctx c) {
        Vec3 f = c.focus();
        Vec3 h = c.hand();
        bloom(h, 0.8f, 7, c.c1);
        // A colony of bats leaves the caster's hand and swallows the target
        for (int k = 0; k < 9; k++) {
            int delay = k;
            at(delay, () -> bat(c.hand(), f.add(gauss() * 0.3, gauss() * 0.4, gauss() * 0.3), 14 + (int) (rnd() * 4), 0.5f + (float) rnd() * 0.3f, c));
        }
        Vec3 toward = c.eye().subtract(f);
        Vec3 n = toward.lengthSqr() < 1.0e-4 ? c.look.scale(-1) : toward.normalize();
        at(12, () -> {
            bloom(f, 1.6f, 10, c.c1);
            sp("sigil", f).size(2.4f, 2.8f).life(40).colors(lighten(c.c1, 0.3f), c.c2).facing(n.x, n.y, n.z).spin(0.06f).envelope(0.1f, 0.7f, 0.95f);
            for (int k = 0; k < n(16); k++) c.skin.body(f.add(rndDir().scale(0.5)), rndDir().scale(0.05).add(0, 0.03, 0), 1.0f, 26, c.c2, 0.9f);
            for (int k = 0; k < n(12); k++) c.skin.mote(f.add(0, 1, 0), new Vec3(gauss() * 0.02, 0, gauss() * 0.02), 0.16f, 24, c.c1, 0.6f);
        });
        // A darkness settles over the target and bleeds downward
        during(12, 30, t -> {
            Vec3 tp = c.focus();
            c.skin.body(tp.add(gauss() * 0.4, 0.5 + rnd() * 0.6, gauss() * 0.4), new Vec3(0, 0.02, 0), 0.9f, 20, c.c2, 0.9f);
            if (t % 3 == 0) c.skin.mote(tp.add(gauss() * 0.3, 0.4, gauss() * 0.3), new Vec3(0, -0.02, 0), 0.14f, 20, c.c1, 0.6f);
        });
    }

    private static void bind(Ctx c) {
        Vec3 f = c.focus();
        bloom(f, 1.4f, 9, c.c1);
        // Chains of blood: three rings around the target that spin in opposite directions
        during(0, 24, t -> {
            Vec3 tp = c.focus();
            for (int k = 0; k < 3; k++) {
                double a = t * (k % 2 == 0 ? 0.3 : -0.3) + k * 2.1;
                Vec3 n = new Vec3(Math.sin(a) * 0.4, 1, Math.cos(a) * 0.4).normalize();
                ringFlat(tp.add(0, -0.6 + k * 0.6, 0), n, 1.0, 1.0, 2, k % 2 == 0 ? c.c1 : c.c2, "ring");
                for (int q = 0; q < 3; q++) {
                    double b = a * 2 + q * 2.09;
                    sp("droplet", tp.add(Math.cos(b) * 1.0, -0.6 + k * 0.6, Math.sin(b) * 1.0)).size(0.2f, 0.16f).life(3).colors(0xFF8080, c.c1).envelope(0.1f, 0.5f, 1f).bright();
                }
            }
        });
        // Life flowing from the victim to the caster, as drops and hearts
        during(4, 20, t -> {
            Vec3 tp = c.focus();
            Vec3 dest = c.live().add(0, 1.2, 0);
            Vec3 d = dest.subtract(tp);
            Vec3 dn = d.lengthSqr() < 1.0e-4 ? c.look : d.normalize();
            for (int k = 0; k < n(4); k++) {
                Vec3 start = tp.add(gauss() * 0.3, gauss() * 0.4, gauss() * 0.3);
                sp("droplet", start).size(0.2f, 0.14f).life(12).colors(0xFF8080, c.c1).vel(dn.scale(d.length() / 12.0)).drag(1f).envelope(0.05f, 0.7f, 1f).bright();
            }
            if (t % 4 == 0) sp("heart", tp).size(0.32f, 0.22f).life(14).colors(WHITE, c.c1).vel(dn.scale(d.length() / 14.0)).drag(1f).envelope(0.1f, 0.6f, 1f);
        });
        at(16, () -> {
            Vec3 chest = c.live().add(0, 1.2, 0);
            bloom(chest, 1.0f, 8, c.c1);
            sp("heart", chest.add(0, 0.8, 0)).size(0.4f, 0.3f).life(26).colors(WHITE, c.c1).vel(0, 0.05, 0).envelope(0.15f, 0.5f, 0.9f);
        });
    }

    private static void burst(Ctx c) {
        Vec3 p = c.live();
        bloom(p.add(0, 1, 0), 3.0f, 10, c.c1);
        sp("rays", p.add(0, 1.2, 0)).size(0.8f, 4.2f).life(12).colors(lighten(c.c1, 0.5f), c.c1).spin(-0.05f).envelope(0.05f, 0.3f, 0.9f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 7.5, 14, c.c1, "shockwave");
        c.skin.ring(p.add(0, 0.14, 0), new Vec3(0, 1, 0), 0.4, 6.5, 14, c.c1);
        // The body bursts: a sphere of blood, then splatter across the ground and a hanging mist
        for (int k = 0; k < n(90); k++) {
            Vec3 d = rndDir();
            c.skin.mote(p.add(0, 1.1, 0), d.scale(0.2 + rnd() * 0.35), 0.24f, 26 + (int) (rnd() * 10), c.c1, 0.6f);
        }
        for (int k = 0; k < 5; k++) {
            double a = rnd() * Math.PI * 2, r = 1 + rnd() * 4;
            sp("splat", p.add(Math.cos(a) * r, 0.05, Math.sin(a) * r)).size(1.4f + (float) rnd() * 1.4f, 1.6f).life(100).colors(mix(c.c1, 0x300000, 0.35f), mix(c.c1, 0x200000, 0.55f))
                    .facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.8f, 0.95f).bright();
        }
        for (int k = 0; k < n(20); k++) {
            double a = rnd() * Math.PI * 2, r = rnd() * 4;
            c.skin.body(p.add(Math.cos(a) * r, 0.4 + rnd() * 1.6, Math.sin(a) * r), new Vec3(Math.cos(a) * 0.05, 0.02, Math.sin(a) * 0.05), 1.6f, 30, c.c1, 0.8f);
        }
    }

    private static void moon(Ctx c) {
        Vec3 p = c.live();
        Vec3 sky = p.add(c.flat().scale(26)).add(0, 32, 0);
        bloom(p.add(0, 1, 0), 2.0f, 10, c.c1);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, 8, 16, c.c1, "shockwave");
        // A blood-red moon rising over the caster, with a halo and slow rays
        during(0, 110, t -> {
            double fade = t < 10 ? t / 10.0 : t > 96 ? Math.max(0, (110 - t) / 14.0) : 1.0;
            float f = (float) fade;
            sp("glow", sky).size(11f, 11f).life(3).colors(mix(c.c1, 0x300008, 0.35f), mix(c.c1, 0x300008, 0.35f)).envelope(0f, 1f, 0.9f * f);
            sp("glow", sky).size(7f, 7f).life(3).colors(lighten(c.c1, 0.4f), c.c1).envelope(0f, 1f, 0.95f * f);
            sp("glow", sky).size(4.5f, 4.5f).life(3).colors(lighten(c.c1, 0.8f), lighten(c.c1, 0.6f)).envelope(0f, 1f, 0.9f * f);
            sp("rays", sky).size(22f, 22f).life(3).colors(c.c1, c.c1).startRoll(t * 0.01f).envelope(0f, 1f, 0.35f * f);
            if (t % 6 == 0) ringFlat(sky, sky.subtract(c.eye()).normalize().scale(-1), 8, 8.2, 3, lighten(c.c1, 0.3f), "ring");
            if (t % 5 == 0) {
                double a = rnd() * Math.PI * 2, r = 3 + rnd() * 10;
                c.skin.body(c.live().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), new Vec3(0, 0.03, 0), 2.0f, 30, c.c1, 0.6f);
            }
        });
        // Bats wheeling round the caster for as long as the moon hangs there
        during(0, 100, t -> {
            Vec3 b = c.live();
            for (int k = 0; k < 4; k++) {
                double a = t * 0.11 + k * Math.PI / 2, r = 5 + Math.sin(t * 0.07 + k) * 1.5;
                Vec3 at = b.add(Math.cos(a) * r, 2.5 + Math.sin(t * 0.13 + k * 1.7) * 1.4, Math.sin(a) * r);
                sp("bat", at).size(0.55f, 0.55f).life(3).colors(mix(c.c1, 0x100010, 0.85f), 0x000000).vel(-Math.sin(a) * 0.08, 0, Math.cos(a) * 0.08).envelope(0.1f, 0.6f, 1f).bright();
            }
        });
    }

    private static void feast(Ctx c) {
        Vec3 p = c.live();
        double reach = 10;
        bloom(p.add(0, 1, 0), 3.4f, 12, c.c1);
        sp("sigil", p.add(0, 0.05, 0)).size((float) reach, (float) reach).life(130).colors(lighten(c.c1, 0.3f), c.c2).facing(0, 1, 0).spin(-0.04f).envelope(0.08f, 0.85f, 0.9f);
        ringFlat(p.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5, reach + 3, 18, c.c1, "shockwave");
        // For six seconds blood is pulled in from the whole circle toward the vampire, as drops, hearts and bats
        during(0, 120, t -> {
            Vec3 b = c.live();
            Vec3 dest = b.add(0, 1.2, 0);
            for (int k = 0; k < n(4); k++) {
                double a = rnd() * Math.PI * 2, r = 3 + rnd() * (reach - 3);
                Vec3 from = b.add(Math.cos(a) * r, 0.3 + rnd() * 2.0, Math.sin(a) * r);
                Vec3 d = dest.subtract(from);
                sp("droplet", from).size(0.22f, 0.14f).life(14).colors(0xFF8080, c.c1).vel(d.scale(1 / 14.0)).drag(1f).envelope(0.05f, 0.7f, 1f).bright();
            }
            if (t % 5 == 0) {
                double a = rnd() * Math.PI * 2, r = 5 + rnd() * (reach - 5);
                Vec3 from = b.add(Math.cos(a) * r, 0.5 + rnd() * 2, Math.sin(a) * r);
                sp("heart", from).size(0.32f, 0.22f).life(16).colors(WHITE, c.c1).vel(dest.subtract(from).scale(1 / 16.0)).drag(1f).envelope(0.1f, 0.6f, 1f);
            }
            if (t % 6 == 0) {
                double a = rnd() * Math.PI * 2;
                Vec3 from = b.add(Math.cos(a) * reach, 1 + rnd() * 3, Math.sin(a) * reach);
                bat(from, dest, 18, 0.6f, c);
            }
            if (t % 10 == 0) ringFlat(b.add(0, 0.14, 0), new Vec3(0, 1, 0), reach, 1.2, 14, c.c1, "ring");
            c.skin.column(b.add(Math.cos(t * 0.3) * 1.2, 0, Math.sin(t * 0.3) * 1.2), 0.5, 3, c.c1, t);
        });
    }
}
