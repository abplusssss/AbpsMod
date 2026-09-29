package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {

    /** Minions pick friendly targets only, undead ignore Necromancers, and so on. */
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void abps$target(LivingEntity target, CallbackInfo ci) {
        if (target != null && !DamageHooks.allowTarget((Mob) (Object) this, target)) ci.cancel();
    }
}
