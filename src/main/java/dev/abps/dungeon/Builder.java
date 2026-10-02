package dev.abps.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds dungeon rooms out of blocks. Placements are queued and done a few thousand at a time each tick, so
 * building a whole dungeon never freezes the server.
 */
public final class Builder {

    private record Put(BlockPos pos, BlockState state) {
    }

    private final List<Put> queue = new ArrayList<>();
    private int cursor;

    public static final int PER_TICK = 6000;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();

    public void set(BlockPos p, BlockState s) {
        queue.add(new Put(p.immutable(), s));
    }

    public void set(int x, int y, int z, BlockState s) {
        queue.add(new Put(new BlockPos(x, y, z), s));
    }

    /** Fills a box, both corners included. */
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }

    /** Places the queued blocks for one tick. Returns true when everything is placed. */
    public boolean tick(ServerLevel level) {
        int end = Math.min(queue.size(), cursor + PER_TICK);
        for (; cursor < end; cursor++) {
            Put p = queue.get(cursor);
            level.setBlock(p.pos, p.state, Block.UPDATE_CLIENTS);
        }
        return cursor >= queue.size();
    }

    public boolean done() {
        return cursor >= queue.size();
    }

    public int progress() {
        return queue.isEmpty() ? 100 : cursor * 100 / queue.size();
    }

    // ------------------------------------------------------------------ pieces

    /** A light that hangs from a ceiling if it can (lanterns), or is set into the ceiling otherwise. */
    private void light(int x, int yCeil, int z, DungeonDef.Palette pal) {
        BlockState l = pal.light();
        if (l.hasProperty(BlockStateProperties.HANGING)) set(x, yCeil - 1, z, l.setValue(BlockStateProperties.HANGING, true));
        else set(x, yCeil, z, l);
    }

    /**
     * A room: floor, walls, ceiling, pillars in the corners, a band of trim, lights in a grid, and a 3 wide door in
     * the middle of its west and east walls. The east door starts barred if lockExit is set.
     */
    public void room(BlockPos min, int w, int h, int d, DungeonDef.Palette pal, Random rnd, boolean entry, boolean exit, boolean lockExit) {
        int x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
        int x1 = x0 + w - 1, y1 = y0 + h - 1, z1 = z0 + d - 1;
        // Clear the inside, then the shell
        fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, AIR);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean checker = ((x + z) & 1) == 0;
                set(x, y0, z, rnd.nextInt(6) == 0 ? pal.floorAlt() : checker ? pal.floor() : pal.floor());
                set(x, y1, z, pal.ceiling());
            }
        for (int y = y0 + 1; y < y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, rnd.nextInt(4) == 0 ? pal.wallAlt() : pal.wall());
                set(x, y, z1, rnd.nextInt(4) == 0 ? pal.wallAlt() : pal.wall());
            }
            for (int z = z0; z <= z1; z++) {
                set(x0, y, z, rnd.nextInt(4) == 0 ? pal.wallAlt() : pal.wall());
                set(x1, y, z, rnd.nextInt(4) == 0 ? pal.wallAlt() : pal.wall());
            }
        }
        // Trim band one block up the walls
        for (int x = x0 + 1; x < x1; x++) {
            set(x, y0 + 1, z0, pal.trim());
            set(x, y0 + 1, z1, pal.trim());
        }
        for (int z = z0 + 1; z < z1; z++) {
            set(x0, y0 + 1, z, pal.trim());
            set(x1, y0 + 1, z, pal.trim());
        }
        // Pillars a little in from each corner
        if (w >= 11 && d >= 11) {
            int[][] corners = {{x0 + 2, z0 + 2}, {x1 - 2, z0 + 2}, {x0 + 2, z1 - 2}, {x1 - 2, z1 - 2}};
            for (int[] c : corners) fill(c[0], y0 + 1, c[1], c[0], y1 - 1, c[1], pal.pillar());
        }
        for (int x = x0 + 3; x < x1 - 1; x += 5)
            for (int z = z0 + 3; z < z1 - 1; z += 5) light(x, y1, z, pal);
        int mz = z0 + d / 2;
        if (entry) fill(x0, y0 + 1, mz - 1, x0, y0 + 3, mz + 1, AIR);
        if (exit) fill(x1, y0 + 1, mz - 1, x1, y0 + 3, mz + 1, lockExit ? BARS : AIR);
    }

    /** A corridor running east from (x, y, z) for len blocks, 3 wide inside and 4 tall, centered on z. */
    public void corridor(int x, int y, int z, int len, DungeonDef.Palette pal, Random rnd) {
        for (int i = 0; i < len; i++) {
            int cx = x + i;
            fill(cx, y, z - 2, cx, y, z + 2, pal.floor());
            fill(cx, y + 5, z - 2, cx, y + 5, z + 2, pal.ceiling());
            fill(cx, y + 1, z - 2, cx, y + 4, z - 2, rnd.nextInt(4) == 0 ? pal.wallAlt() : pal.wall());
            fill(cx, y + 1, z + 2, cx, y + 4, z + 2, rnd.nextInt(4) == 0 ? pal.wallAlt() : pal.wall());
            fill(cx, y + 1, z - 1, cx, y + 4, z + 1, AIR);
        }
        light(x + len / 2, y + 5, z, pal);
    }

    /** Opens a barred door: the 3x3 hole in a wall at x, centered on z. */
    public static void open(ServerLevel level, int x, int y, int z) {
        for (int dz = -1; dz <= 1; dz++)
            for (int dy = 1; dy <= 3; dy++) level.setBlock(new BlockPos(x, y + dy, z + dz), AIR, Block.UPDATE_ALL);
    }

    /** Queues clearing a box back to air (when a run is over). */
    public void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(x0, y0, z0, x1, y1, z1, AIR);
    }
}
