package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {

    private static final String GET_DROPS = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;";

    /** Miner bonus Fortune: pretend the pickaxe has more Fortune. */
    @ModifyVariable(method = GET_DROPS, at = @At("HEAD"), argsOnly = true)
    private static ItemInstance abps$tool(ItemInstance tool, BlockState state, ServerLevel level, BlockPos pos, BlockEntity be, Entity entity) {
        if (tool instanceof ItemStack stack) return DamageHooks.dropTool(entity, state, stack);
        return tool;
    }

    /** Smelting ores, doubling crops and so on. */
    @Inject(method = GET_DROPS, at = @At("RETURN"), cancellable = true)
    private static void abps$drops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity be, Entity entity, ItemInstance tool,
                                   CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> changed = DamageHooks.modifyDrops(entity, state, new ArrayList<>(cir.getReturnValue()));
        if (changed != null) cir.setReturnValue(changed);
    }
}
