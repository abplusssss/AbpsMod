package dev.abps.client.mixin;

import dev.abps.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanished Assassins are not drawn at all, not even their armor or held item. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void abps$vanish(E entity, Frustum frustum, double x, double y, double z, float pt, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && !ClientState.vanished.isEmpty() && ClientState.vanished.contains(entity.getUUID())
                && entity != Minecraft.getInstance().player) {
            cir.setReturnValue(false);
        }
    }
}
