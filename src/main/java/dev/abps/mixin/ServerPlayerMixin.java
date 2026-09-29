package dev.abps.mixin;

import dev.abps.AbpsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    /** Class icon and level in the tab list. */
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void abps$tabName(CallbackInfoReturnable<Component> cir) {
        if (!AbpsMod.running()) return;
        Component name = AbpsMod.service().tabName((ServerPlayer) (Object) this);
        if (name != null) cir.setReturnValue(name);
    }
}
