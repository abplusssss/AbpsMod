package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerMixin {

    /** Some classes get hungry faster. */
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float abps$hunger(float amount) {
        return DamageHooks.hunger((Player) (Object) this, amount);
    }
}
