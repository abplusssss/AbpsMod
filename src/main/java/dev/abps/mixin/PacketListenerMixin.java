package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PacketListenerMixin {

    private static final String SAME_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    /** Players without the mod can use F (swap hands) for abilities. */
    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER))
    private void abps$swapKey(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND && DamageHooks.swapKey(player)) {
            ci.cancel();
        }
    }

    /** Windwalker double jump for players without the mod (uses the fly toggle). */
    @Inject(method = "handlePlayerAbilities", cancellable = true, at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER))
    private void abps$flyToggle(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (packet.isFlying() && DamageHooks.flyToggle(player)) ci.cancel();
    }
}
