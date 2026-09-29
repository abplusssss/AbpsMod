package dev.abps.client.mixin;

import dev.abps.client.ClientState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen shake from big abilities. Only moves the camera, never where you aim. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void abps$shake(float partialTicks, CallbackInfo ci) {
        if (ClientState.shakeTicks <= 0) return;
        float left = (ClientState.shakeTicks - partialTicks) / Math.max(1f, ClientState.shakeTotal);
        float power = Math.max(0, left) * Math.min(3f, ClientState.shakeStrength) * 1.6f;
        double t = System.nanoTime() / 1.0e9;
        float dx = (float) (Math.sin(t * 47.0) + Math.sin(t * 91.0) * 0.5) * power;
        float dy = (float) (Math.cos(t * 53.0) + Math.sin(t * 77.0) * 0.5) * power;
        setRotation(yRot() + dx, xRot() + dy);
    }
}
