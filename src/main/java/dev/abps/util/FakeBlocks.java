package dev.abps.util;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Glowing block outlines that only one player can see.
 * They are never added to the world. Only the packets are sent to that player.
 */
public final class FakeBlocks {

    private FakeBlocks() {
    }

    /** Shows a glowing copy of the block, just inside the real one, so only the outline is seen. Returns its id. */
    public static int show(ServerPlayer p, BlockPos pos, BlockState state, int glowColor) {
        Display.BlockDisplay d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, p.level());
        d.setPos(pos.getX(), pos.getY(), pos.getZ());
        d.setBlockState(state);
        d.setGlowingTag(true);
        d.setGlowColorOverride(glowColor);
        d.setTransformation(new Transformation(new Vector3f(0.002f), new Quaternionf(), new Vector3f(0.996f), new Quaternionf()));
        p.connection.send(new ClientboundAddEntityPacket(d.getId(), d.getUUID(), pos.getX(), pos.getY(), pos.getZ(),
                0f, 0f, EntityTypes.BLOCK_DISPLAY, 0, Vec3.ZERO, 0));
        p.connection.send(new ClientboundSetEntityDataPacket(d.getId(), d.getEntityData().getNonDefaultValues()));
        return d.getId();
    }

    public static void hide(ServerPlayer p, int... ids) {
        if (ids.length > 0) p.connection.send(new ClientboundRemoveEntitiesPacket(ids));
    }

    /**
     * A block visual everyone can see, like a stone spike or a tree. It grows from startScale to endScale
     * and is removed after lifeTicks. It has no hitbox and never saves.
     */
    public static Display.BlockDisplay temp(net.minecraft.server.level.ServerLevel level, Vec3 at, BlockState state,
                                            Vector3f startScale, Vector3f endScale, int growTicks, int lifeTicks) {
        Display.BlockDisplay d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        d.setPos(at.x, at.y, at.z);
        d.setBlockState(state);
        d.addTag("abps_fx");
        // Centered on the spot, growing up from the ground
        d.setTransformation(new Transformation(new Vector3f(-startScale.x / 2, 0, -startScale.z / 2), new Quaternionf(), startScale, new Quaternionf()));
        Vfx.track(d); // new effects must survive the "delete leftovers" check when they spawn
        level.addFreshEntity(d);
        Tasks.later(1, () -> {
            d.setTransformationInterpolationDelay(0);
            d.setTransformationInterpolationDuration(growTicks);
            d.setTransformation(new Transformation(new Vector3f(-endScale.x / 2, 0, -endScale.z / 2), new Quaternionf(), endScale, new Quaternionf()));
        });
        Tasks.later(lifeTicks, () -> Vfx.discard(d));
        return d;
    }
}
