package dev.abps.client.fx;

import dev.abps.AbpsMod;
import dev.abps.client.ClientPrefs;
import dev.abps.net.Net;
import dev.abps.util.FxKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The client side of the effects engine. The server sends small messages; this turns each into real glowing
 * particles. Effects that last a while are emitters that this class ticks every game tick.
 */
public final class FxSystem {

    /** Something that keeps producing particles for a while. Returns false when it is finished. */
    public interface Emitter {
        boolean tick(ClientLevel level);
    }

    /** Set once the particle types, textures and the additive layer are all in place. */
    public static boolean ready;

    private static final List<Emitter> EMITTERS = new ArrayList<>();

    private FxSystem() {
    }

    public static void add(Emitter e) {
        EMITTERS.add(e);
    }

    public static void clear() {
        EMITTERS.clear();
    }

    /** 0.5, 1 or 1.5, from the quality setting. Scales how many pieces each effect is made of. */
    public static float density() {
        return switch (ClientPrefs.get().fxQuality) {
            case 0 -> 0.5f;
            case 2 -> 1.5f;
            default -> 1f;
        };
    }

    /** Hard limit on effect particles alive at once. */
    public static int maxParticles() {
        return (int) (2600 * density());
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            EMITTERS.clear();
            return;
        }
        if (mc.isPaused() || EMITTERS.isEmpty()) return;
        for (int i = EMITTERS.size() - 1; i >= 0; i--) {
            Emitter e = EMITTERS.get(i);
            boolean keep;
            try {
                keep = e.tick(level);
            } catch (Throwable t) {
                AbpsMod.LOGGER.warn("An effect emitter failed and was stopped: {}", t.toString());
                keep = false;
            }
            if (!keep) EMITTERS.remove(i);
        }
    }

    /** Handles one effect message from the server. Never throws: a broken effect must not crash the game. */
    public static void handle(Net.VfxPayload p) {
        if (!ready || ClientPrefs.get().fxQuality < 0 || Minecraft.getInstance().level == null) return;
        try {
            double[] d = p.d();
            int[] i = p.i();
            switch (p.kind()) {
                case FxKind.SHARD -> FxEffects.shard(d, i);
                case FxKind.BURST -> FxEffects.burst(d, i);
                case FxKind.RING -> FxEffects.ring(d, i);
                case FxKind.BEAM -> FxEffects.beam(d, i);
                case FxKind.ZIGZAG -> FxEffects.zigzag(d, i);
                case FxKind.PILLAR -> FxEffects.pillar(d, i);
                case FxKind.JAWS -> FxEffects.jaws(d, i);
                case FxKind.SLASH -> FxEffects.slash(d, i);
                case FxKind.ORBIT -> FxEffects.orbit(d, i);
                case FxKind.VORTEX -> FxEffects.vortex(d, i);
                case FxKind.FINS -> FxEffects.fins(d, i);
                case FxKind.FLASH -> FxEffects.flash(d, i);
                case FxKind.SPHERE -> FxEffects.sphere(d, i);
                case FxKind.HELIX -> FxEffects.helix(d, i);
                case FxKind.TRAIL -> FxEffects.trail(d, i);
                case FxKind.WAVE -> FxEffects.wave(d, i);
                case FxKind.WAVE_STOP -> FxEffects.waveStop(i);
                case FxKind.PARTICLES -> FxEffects.particles(d, i);
                default -> {
                }
            }
        } catch (Throwable t) {
            AbpsMod.LOGGER.warn("An effect could not be drawn: {}", t.toString());
        }
    }
}
