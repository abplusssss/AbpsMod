package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void abps$hit(HitResult hit, CallbackInfo ci) {
        if (DamageHooks.projectileHit((Projectile) (Object) this, hit)) ci.cancel();
    }
}
