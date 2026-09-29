package dev.abps.util;

import dev.abps.net.Net.FxPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Particles, sounds and client screen effects. */
public final class Fx {

    private Fx() {
    }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb, size);
    }

    public static BlockParticleOption block(BlockState state) {
        return new BlockParticleOption(ParticleTypes.BLOCK, state);
    }

    public static void burst(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void burst(ServerLevel level, ParticleOptions p, Vec3 at, int count, double sx, double sy, double sz, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, sx, sy, sz, speed);
    }

    public static void ring(ServerLevel level, ParticleOptions p, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, center.x + Math.cos(a) * radius, center.y + 0.2, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void line(ServerLevel level, ParticleOptions p, Vec3 from, Vec3 to, double step) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 0.01) return;
        dir = dir.normalize().scale(step);
        Vec3 at = from;
        for (double walked = 0; walked < len; walked += step) {
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            at = at.add(dir);
        }
    }

    /** A rising spiral, good for tornados and summons. */
    public static void spiral(ServerLevel level, ParticleOptions p, Vec3 base, double radius, double height, int points, double turn) {
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double a = turn + t * Math.PI * 6;
            double r = radius * (0.4 + t * 0.6);
            level.sendParticles(p, base.x + Math.cos(a) * r, base.y + t * height, base.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Entity at, SoundEvent s, float vol, float pitch) {
        sound(level, at.position(), s, vol, pitch);
    }

    public static void sound(ServerLevel level, Entity at, Holder<SoundEvent> s, float vol, float pitch) {
        sound(level, at.position(), s, vol, pitch);
    }

    // ---- Client screen effects (only for players with the mod) ----
    public static final int SHAKE = 1, TINT = 2, FLASH = 3;

    /** Screen effect for one player. */
    public static void screen(ServerPlayer p, int type, int color, int ticks, float strength) {
        if (ServerPlayNetworking.canSend(p, FxPayload.TYPE)) {
            ServerPlayNetworking.send(p, new FxPayload(type, color, ticks, strength));
        }
    }

    /** Camera shake for everyone close to a spot. Stronger the closer you are. */
    public static void shakeNear(ServerLevel level, Vec3 at, double radius, int ticks, float strength) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d > radius) continue;
            screen(p, SHAKE, 0, ticks, (float) (strength * (1 - d / radius)));
        }
    }
}
