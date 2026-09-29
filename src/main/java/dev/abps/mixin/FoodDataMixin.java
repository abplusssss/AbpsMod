package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    /** Remembers who this food bar belongs to during the tick. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void abps$start(ServerPlayer player, CallbackInfo ci) {
        DamageHooks.foodOwner = player;
    }

    /** Healing from a full food bar is changed by some classes. */
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
    private float abps$foodHeal(float amount) {
        return DamageHooks.foodHeal(amount);
    }
}
