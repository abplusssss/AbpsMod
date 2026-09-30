package dev.abps.client.fx;

import dev.abps.client.ClientPrefs;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.joml.Vector3fc;

/**
 * Keeps effects out of your face. Sprites fade out when they get close to the camera, and anything sitting
 * right on your crosshair is dimmed, so you can always see what you are fighting.
 */
final class FxView {

    private FxView() {
    }

    /** How visible a sprite at this spot should be, 0 to 1. size is the sprite's half-width in blocks. */
    static float visibility(Camera camera, double x, double y, double z, float size) {
        if (!ClientPrefs.get().clearView) return 1f;
        double dx = x - camera.position().x, dy = y - camera.position().y, dz = z - camera.position().z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Big sprites need more room before they stop covering the screen
        float near = 0.6f + size * 1.4f;
        float far = near + 2.4f;
        float v = smooth((float) ((dist - near) / (far - near)));
        if (v <= 0.001f) return 0f;

        // Soften sprites right on the crosshair in the first few blocks, but only a little: your own forward casts must still show
        if (dist < 6 && dist > 0.001) {
            Vector3fc f = camera.forwardVector();
            double cos = (dx * f.x() + dy * f.y() + dz * f.z()) / dist;
            if (cos > 0.93) { // about 21 degrees
                float centered = (float) ((cos - 0.93) / 0.07); // 0 at the cone edge, 1 on the crosshair
                float closeness = 1f - (float) (dist / 6);
                v *= 1f - 0.4f * smooth(centered) * closeness;
            }
        }
        return v;
    }

    /** Dims light sprites as more of them pile up, so a big fight glows instead of turning the screen white. */
    static float crowd() {
        int alive = FxParticle.alive();
        return alive < 350 ? 1f : Mth.clamp(350f / alive, 0.45f, 1f);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
