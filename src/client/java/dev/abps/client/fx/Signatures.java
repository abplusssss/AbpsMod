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

    static void play(double[] d, int[] i) {
        Ctx c = new Ctx(d, i);
        switch (c.theme) {
            case Skin.ARCHER -> SigArcher.play(c);
            case Skin.ASSASSIN -> SigAssassin.play(c);
            case Skin.BERSERKER -> SigBerserker.play(c);
            case Skin.DRUID -> SigDruid.play(c);
            case Skin.MINER -> SigMiner.play(c);
            case Skin.NECRO -> SigNecro.play(c);
            case Skin.PYRO -> SigPyro.play(c);
            case Skin.SHARK -> SigShark.play(c);
            case Skin.TANK -> SigTank.play(c);
            case Skin.VAMPIRE -> SigVampire.play(c);
            case Skin.WIND -> SigWind.play(c);
            default -> {
            }
        }
    }
}
