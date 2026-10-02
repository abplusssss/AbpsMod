package dev.abps.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntConsumer;

import static dev.abps.client.fx.FxKit.*;

/**
 * The hand-made effect of every ability. When a player casts, the server sends the class, the ability number, where
 * the caster stands and looks, and what they are aiming at; this class picks the choreography for that exact ability.
 * The effects that the ability itself causes (hits, waves, rings) arrive separately and are drawn in the class's
 * {@link Skin}; a signature is the cast itself: the wind-up at the hands and the release.
 */
final class Signatures {

    private Signatures() {
    }

    /** Everything a signature needs to know about the cast. */
    static final class Ctx {
        final int theme, slot, casterId, targetId, c1, c2;
        /** How long the ability lasts in ticks, or the cue's extra number. */
        final int ticks;
        final Vec3 pos, look, aim;
        final Skin skin;

        Ctx(double[] d, int[] i) {
            this.pos = vec(d, 0);
            this.look = vec(d, 3).normalize();
            this.aim = vec(d, 6);
            this.theme = i[0];
            this.slot = i[1];
            this.casterId = i[2];
            this.targetId = i[3];
            this.c1 = i[4];
            this.c2 = i[5];
            this.ticks = i.length > 6 ? i[6] : 0;
            this.skin = Skin.of(i[0]);
        }

        Entity caster() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? null : level.getEntity(casterId);
        }

        Entity target() {
            ClientLevel level = Minecraft.getInstance().level;
            return targetId < 0 || level == null ? null : level.getEntity(targetId);
        }

        /** Where the caster is right now, or where they were when they cast. */
        Vec3 live() {
            Entity e = caster();
            return e == null || e.isRemoved() ? pos : e.position();
        }

        Vec3 eye() {
            Entity e = caster();
            return e == null || e.isRemoved() ? pos.add(0, 1.62, 0) : e.getEyePosition();
        }

        /** The direction the caster faces, level with the ground. */
        Vec3 flat() {
            Vec3 f = new Vec3(look.x, 0, look.z);
            return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
        }

        /** To the caster's right. */
        Vec3 right() {
            Vec3 f = flat();
            return new Vec3(-f.z, 0, f.x);
        }

        /** About where the casting hand is. */
        Vec3 hand() {
            return eye().add(look.scale(0.9)).add(right().scale(-0.35)).add(0, -0.3, 0);
        }

        /** The middle of the target if there is one, otherwise the aimed point. */
        Vec3 focus() {
            Entity t = target();
            return t == null || t.isRemoved() ? aim : t.position().add(0, t.getBbHeight() * 0.5, 0);
        }

        Vec3 chest() {
            return live().add(0, 1.0, 0);
        }
    }

    // ------------------------------------------------------------------ timing helpers

    /** Runs something once, after a delay in ticks. */
    static void at(int delay, Runnable r) {
        if (delay <= 0) {
            r.run();
            return;
        }
        FxSystem.add(new FxSystem.Emitter() {
            int t;

            @Override
            public boolean tick(ClientLevel level) {
                if (++t >= delay) {
                    r.run();
                    return false;
                }
                return true;
            }
        });
    }

    /** Runs a step every tick for len ticks, starting after start ticks. The step gets 0, 1, 2 and so on. */
    static void during(int start, int len, IntConsumer body) {
        FxSystem.add(new FxSystem.Emitter() {
            int t;

            @Override
            public boolean tick(ClientLevel level) {
                if (t >= start) body.accept(t - start);
                t++;
                return t < start + len;
            }
        });
    }

    /** Pieces of the class's own material rushing in to a point, to show power being gathered. */
    static void gather(Ctx c, Vec3 center, double radius, int ticks, int perTick, int col) {
        during(0, ticks, t -> {
            for (int k = 0; k < n(perTick); k++) {
                Vec3 dir = rndDir();
                Vec3 from = center.add(dir.scale(radius * (0.6 + rnd() * 0.4)));
                int life = Math.max(4, ticks - t);
                c.skin.mote(from, dir.scale(-from.distanceTo(center) / life), 0.12f + (float) (t / (double) ticks) * 0.1f, life, col, 0f);
            }
            if (t == ticks - 1) bloom(center, 0.9f, 8, c.skin.tone(col));
        });
    }

    /**
     * Sends a glowing object from one point to another. The object is drawn with the class's orbiting piece, with
     * a bright core and the class's trail. onArrive runs when it lands.
     */
    static void projectile(Ctx c, Vec3 from, Vec3 to, double speed, float size, int col, Runnable onArrive) {
        Vec3 dv = to.subtract(from);
        double len = dv.length();
        Vec3 dir = len < 1.0e-4 ? c.look : dv.scale(1 / len);
        int steps = Math.max(1, (int) Math.ceil(len / speed));
        during(0, steps, t -> {
            double f = Math.min(len, (t + 1) * speed);
            Vec3 pos = from.add(dir.scale(f));
            c.skin.orbiter(pos, dir, size, col);
            sp("glow", pos).size(size * 2.2f, size * 0.6f).life(4).colors(WHITE, c.skin.tone(col)).envelope(0.1f, 0.4f, 0.9f);
            c.skin.along(pos, dir, size, col);
            if (t == steps - 1 && onArrive != null) onArrive.run();
        });
    }

    /** A ring standing upright around the aim line, like a portal or a lens. */
    static void lens(Vec3 at, Vec3 look, double r0, double r1, int life, int col) {
        ringFlat(at, look, r0, r1, life, col, "ring");
    }

    /** Levelling up, for any class: two rising spirals in its colors, a burst ring and a crown of light over the head. */
    private static void levelUp(Ctx c) {
        Entity e = c.caster();
        java.util.function.Supplier<Vec3> feet = () -> e == null || e.isRemoved() ? c.pos : e.position();
        Brush.Paint a = Brush.Paint.light(c.c1, 1.2f), b = Brush.Paint.light(c.c2, 1.1f);
        Brush.helix(feet, 0.8, 2.4, 2, 0.12f, 22, 0, a);
        Brush.helix(feet, 0.8, 2.4, 2, 0.12f, 22, Math.PI, b);
        Brush.shock(c.pos.add(0, 0.08, 0), Brush.UP, 0.3, 3.5, 0.2f, 14, a);
        Brush.raysUp(c.pos.add(0, 0.2, 0), 10, 3, 0.08f, 12, b);
        at(14, () -> {
            Vec3 head = feet.get().add(0, 2.3, 0);
            Brush.ring(head, Brush.UP, 0.45, 0.07f, 30, 3, a);
            Brush.glow(head, 0.9f, 12, c.c1);
        });
        if (c.ticks == 1) {
            // Max level: a full pillar and a circle on the ground
            Brush.pillar(c.pos, 1.0, 8, 30, 4, a);
            Brush.circle(c.pos, 4, 40, 8, a, b);
        }
    }

    static void play(double[] d, int[] i) {
        Ctx c = new Ctx(d, i);
        if (c.slot == 20) {
            levelUp(c);
            return;
        }
        switch (c.theme) {
            case Skin.ARCHER -> SigArcher.play(c);
            case Skin.ASSASSIN -> SigAssassin.play(c);
            case Skin.BERSERKER -> SigBerserker.play(c);
            case Skin.MINER -> SigMiner.play(c);
            case Skin.NECRO -> SigNecro.play(c);
            case Skin.PYRO -> SigPyro.play(c);
            case Skin.SHARK -> SigShark.play(c);
            case Skin.TANK -> SigTank.play(c);
            case Skin.VAMPIRE -> SigVampire.play(c);
            case Skin.WIND -> SigWind.play(c);
            case Skin.CRYO -> SigCryo.play(c);
            case Skin.CHRONO -> SigChrono.play(c);
            case Skin.PALADIN -> SigPaladin.play(c);
            case Skin.VOID -> SigVoid.play(c);
            case Skin.SAMURAI -> SigSamurai.play(c);
            case SigDungeon.THEME -> SigDungeon.play(c);
            case SigOverlord.THEME -> SigOverlord.play(c);
            case SigGather.HARVESTER, SigGather.LUMBERJACK, SigGather.ANGLER, SigGather.EXPLORER -> SigGather.play(c);
            default -> {
            }
        }
    }
}
