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
        FxParticle.resetCount();
    }

    /** 0.35, 0.6 or 1, from the quality setting. Scales how many pieces each effect is made of. Kept low so effects read clearly instead of piling up. */
    public static float density() {
        return switch (ClientPrefs.get().fxQuality) {
            case 0 -> 0.35f;
            case 2 -> 1f;
            default -> 0.6f;
        };
    }

    /** Hard limit on effect particles alive at once. */
    public static int maxParticles() {
        return (int) (1500 * density());
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

    private static long lastPreview;

    /**
     * Plays one ability's effect on the local player, for the menu's Preview buttons. Only this player sees it and
     * it does nothing in the world. It starts a few ticks later so the menu has time to close.
     */
    public static void preview(String classId, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (!ready || !FxSprites.loaded() || mc.player == null || System.currentTimeMillis() - lastPreview < 800) return;
        lastPreview = System.currentTimeMillis();
        add(new Emitter() {
            int t;

            @Override
            public boolean tick(ClientLevel level) {
                if (++t < 4) return true;
                var pl = mc.player;
                if (pl == null) return false;
                int theme = FxKind.theme(classId);
                net.minecraft.world.phys.Vec3 pos = pl.position(), look = pl.getLookAngle();
                net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(look.x, 0, look.z);
                flat = flat.lengthSqr() < 1.0e-4 ? new net.minecraft.world.phys.Vec3(0, 0, 1) : flat.normalize();
                // Aim at what's under the crosshair, or a spot on the ground ahead when looking at the sky
                var hit = pl.pick(24, 0, false);
                net.minecraft.world.phys.Vec3 aim = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? pos.add(flat.scale(8)) : hit.getLocation();
                int target = mc.crosshairPickEntity == null ? -1 : mc.crosshairPickEntity.getId();
                Skin s = Skin.of(theme);
                Signatures.play(new double[]{pos.x, pos.y, pos.z, look.x, look.y, look.z, aim.x, aim.y, aim.z},
                        new int[]{theme, slot, pl.getId(), target, s.accent, s.accent2, 100});
                return false;
            }
        });
    }

    /** Handles one effect message from the server. Never throws: a broken effect must not crash the game. */
    public static void handle(Net.VfxPayload p) {
        if (!ready || !FxSprites.loaded() || Minecraft.getInstance().level == null) return;
        try {
            double[] d = p.d();
            int[] i = p.i();
            int kind = p.kind() & 0xFF;
            int theme = (p.kind() >> 8) & 0xFF;
            Skin s = Skin.of(theme);
            switch (kind) {
                case FxKind.SHARD -> FxEffects.shard(d, i, s);
                case FxKind.BURST -> FxEffects.burst(d, i, s);
                case FxKind.RING -> FxEffects.ring(d, i, s);
                case FxKind.BEAM -> FxEffects.beam(d, i, s);
                case FxKind.ZIGZAG -> FxEffects.zigzag(d, i, s);
                case FxKind.PILLAR -> FxEffects.pillar(d, i, s);
                case FxKind.JAWS -> FxEffects.jaws(d, i, s);
                case FxKind.SLASH -> FxEffects.slash(d, i, s, theme);
                case FxKind.ORBIT -> FxEffects.orbit(d, i, s);
                case FxKind.VORTEX -> FxEffects.vortex(d, i, s);
                case FxKind.FINS -> FxEffects.fins(d, i, s);
                case FxKind.FLASH -> FxEffects.flash(d, i, s);
                case FxKind.SPHERE -> FxEffects.sphere(d, i, s);
                case FxKind.HELIX -> FxEffects.helix(d, i, s);
                case FxKind.TRAIL -> FxEffects.trail(d, i, s);
                case FxKind.WAVE -> Tsunami.start(d, i);
                case FxKind.WAVE_STOP -> Tsunami.stop(i[0]);
                case FxKind.PARTICLES -> FxEffects.particles(d, i, s);
                case FxKind.SIGNATURE -> Signatures.play(d, i);
                default -> {
                }
            }
        } catch (Throwable t) {
            AbpsMod.LOGGER.warn("An effect could not be drawn: {}", t.toString());
        }
    }
}
