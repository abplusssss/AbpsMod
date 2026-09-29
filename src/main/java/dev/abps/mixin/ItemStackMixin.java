package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    /** Miner pickaxes sometimes skip losing durability. */
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void abps$durability(int amount, ServerLevel level, ServerPlayer player, Consumer<?> onBreak, CallbackInfo ci) {
        if (player != null && DamageHooks.saveDurability(player, (ItemStack) (Object) this)) ci.cancel();
    }
}
