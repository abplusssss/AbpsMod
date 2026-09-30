package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {

    /**
     * Summoned minions never burn in daylight. This used to be done with a helmet, but a helmet with no armor
     * model still draws the item itself on the mob's head.
     */
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void abps$noSunBurn(CallbackInfoReturnable<Boolean> cir) {
        if (((Mob) (Object) this).entityTags().contains(dev.abps.util.Targets.MINION_TAG)) cir.setReturnValue(false);
    }

    /** Minions pick friendly targets only, undead ignore Necromancers, and so on. */
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void abps$target(LivingEntity target, CallbackInfo ci) {
        if (target != null && !DamageHooks.allowTarget((Mob) (Object) this, target)) ci.cancel();
    }
}
