package dev.abps.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Random;

/**
 * Detail for dungeon rooms and corridors, by theme: floor borders and inlays, pillars set into the walls, wall torches,
 * ceiling beams with hanging lights, cobwebs and rubble in the corners, and props along the walls (coffins, skulls,
 * candles and bookshelves in the crypt; snow, ice spikes and frozen pillars in the frost halls; lava channels,
 * anvils and furnaces in the forge; sculk, amethyst and candles in the depths).
 *
 * Everything stays out of the walkway between the two doors, so no room can be blocked.
 */
final class Decor {

    private Decor() {
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> prop, T value) {
        return s.hasProperty(prop) ? s.setValue(prop, value) : s;
    }

    private static BlockState torch(MobKit.Theme theme, Direction facing) {
        BlockState t = theme == MobKit.Theme.CRYPT || theme == MobKit.Theme.DEPTHS ? Blocks.SOUL_WALL_TORCH.defaultBlockState() : Blocks.WALL_TORCH.defaultBlockState();
        return with(t, BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private static BlockState candles(Random rnd) {
        BlockState c = Blocks.CANDLE.defaultBlockState();
        c = with(c, BlockStateProperties.CANDLES, 1 + rnd.nextInt(4));
        return with(c, BlockStateProperties.LIT, true);
    }

    private static BlockState hanging(BlockState light) {
        return with(light, BlockStateProperties.HANGING, true);
    }

    private static BlockState skull(Random rnd) {
        return with(Blocks.SKELETON_SKULL.defaultBlockState(), BlockStateProperties.ROTATION_16, rnd.nextInt(16));
    }

    private static BlockState lit(BlockState s) {
        return with(s, BlockStateProperties.LIT, true);
    }

    /** True if (x, z) is in the walkway between the doors, or right by a door. Nothing tall goes there. */
    private static boolean path(Run.Room r, int x, int z) {
        int mz = r.midZ();
        if (Math.abs(z - mz) <= 2) return true;
        return (x <= r.min.getX() + 2 || x >= r.x1() - 2) && Math.abs(z - mz) <= 3;
    }

    // ------------------------------------------------------------------ rooms

    static void room(Builder b, Run.Room r, DungeonDef.Palette pal, Random rnd) {
        MobKit.Theme theme = MobKit.themeOf(pal);
        int x0 = r.min.getX(), y0 = r.floor(), z0 = r.min.getZ();
        int x1 = r.x1(), z1 = z0 + r.d - 1, y1 = y0 + r.h - 1;
        int cx = x0 + r.w / 2, cz = r.midZ();

        // Floor: a trim border one block in from the walls, and an inlay in the middle
        for (int x = x0 + 1; x < x1; x++) {
            b.set(x, y0, z0 + 1, pal.trim());
            b.set(x, y0, z1 - 1, pal.trim());
        }
        for (int z = z0 + 1; z < z1; z++) {
            b.set(x0 + 1, y0, z, pal.trim());
            b.set(x1 - 1, y0, z, pal.trim());
        }
        int ir = Math.max(2, Math.min(r.w, r.d) / 4);
        if (r.type != Run.RoomType.BOSS && r.type != Run.RoomType.ARENA) {
            for (int dx = -ir; dx <= ir; dx++)
                for (int dz = -ir; dz <= ir; dz++) {
                    int m = Math.abs(dx) + Math.abs(dz);
                    if (m == ir || m == ir - 2) b.set(cx + dx, y0, cz + dz, pal.floorAlt());
                    if (m == 0) b.set(cx, y0, cz, pal.trim());
                }
        }

        // Walls: pillars set into the walls every 4 blocks, with a torch on each
        for (int x = x0 + 4; x < x1 - 2; x += 4) {
            b.fill(x, y0 + 1, z0, x, y1 - 1, z0, pal.pillar());
            b.fill(x, y0 + 1, z1, x, y1 - 1, z1, pal.pillar());
            if (r.h >= 7) {
                b.set(x, y0 + 3, z0 + 1, torch(theme, Direction.SOUTH));
                b.set(x, y0 + 3, z1 - 1, torch(theme, Direction.NORTH));
            }
        }

        // Ceiling: beams across the room, and lights hanging from the middle of some of them
        if (r.h >= 8) {
            for (int x = x0 + 4; x < x1 - 2; x += 4) {
                b.fill(x, y1 - 1, z0 + 1, x, y1 - 1, z1 - 1, pal.pillar());
                if (rnd.nextBoolean()) {
                    b.set(x, y1 - 2, cz + (rnd.nextBoolean() ? 3 : -3), hanging(pal.light()));
                }
            }
        }

        // Corners: cobwebs up high, a little rubble down low
        int[][] corners = {{x0 + 1, z0 + 1}, {x1 - 1, z0 + 1}, {x0 + 1, z1 - 1}, {x1 - 1, z1 - 1}};
        for (int[] c : corners) {
            if (theme != MobKit.Theme.FORGE) b.set(c[0], y1 - 1, c[1], Blocks.COBWEB.defaultBlockState());
            if (theme == MobKit.Theme.FORGE || rnd.nextBoolean()) b.set(c[0], y1 - 2, c[1], Blocks.COBWEB.defaultBlockState());
            b.set(c[0], y0 + 1, c[1], rubble(theme, rnd));
        }

        boolean channels = theme == MobKit.Theme.FORGE && r.d >= 11 && r.type != Run.RoomType.TRAP;
        // Props along the north and south walls (forge rooms have lava channels there instead)
        for (int x = x0 + 2; x <= x1 - 2 && !channels; x++) {
            if ((x - x0) % 4 == 0) continue; // the pillars are there
            if (rnd.nextInt(3) != 0) continue;
            wallProp(b, theme, rnd, x, y0 + 1, z0 + 1);
            if (rnd.nextInt(3) == 0) wallProp(b, theme, rnd, x, y0 + 1, z1 - 1);
        }

        // Theme pieces on the floor, out of the walkway
        int pieces = r.type == Run.RoomType.SHRINE ? 0 : Math.max(2, r.w * r.d / 40); // shrine pedestals stay clear
        for (int k = 0; k < pieces; k++) {
            int x = x0 + 3 + rnd.nextInt(Math.max(1, r.w - 6)), z = z0 + 3 + rnd.nextInt(Math.max(1, r.d - 6));
            if (path(r, x, z)) continue;
            floorPiece(b, theme, pal, rnd, x, y0 + 1, z, r.h);
        }

        // Forge rooms: lava channels behind low walls along both long sides
        if (channels) {
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                if ((x - x0) % 4 == 0) continue;
                b.set(x, y0, z0 + 1, Blocks.LAVA.defaultBlockState());
                b.set(x, y0, z1 - 1, Blocks.LAVA.defaultBlockState());
                b.set(x, y0 + 1, z0 + 2, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
                b.set(x, y0 + 1, z1 - 2, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
            }
        }

        // Layout for fights: some rooms are pillared halls, some have raised ledges along the sides
        if (r.type == Run.RoomType.COMBAT || r.type == Run.RoomType.ELITE) {
            int layout = rnd.nextInt(3);
            if (layout == 0) {
                for (int x = x0 + 3; x <= x1 - 3; x += 3) {
                    for (int side = -1; side <= 1; side += 2) {
                        int z = cz + side * 4;
                        if (z <= z0 + 2 || z >= z1 - 2) continue;
                        b.fill(x, y0 + 1, z, x, y1 - 1, z, pal.pillar());
                        b.set(x, y0 + 1, z, pal.trim());
                    }
                }
            } else if (layout == 1 && r.d >= 15) {
                for (int x = x0 + 3; x <= x1 - 3; x++) {
                    b.set(x, y0 + 1, z0 + 2, slab(pal));
                    b.set(x, y0 + 1, z1 - 2, slab(pal));
                }
            }
        }

        switch (r.type) {
            case BOSS -> throne(b, theme, pal, x1 - 3, y0, cz);
            case TREASURE -> treasure(b, rnd, x0, y0, z0, x1, z1, cz);
            case START -> {
                // A small shrine at the back wall to start from
                b.set(x0 + 2, y0 + 1, cz - 3, lit(Blocks.SOUL_CAMPFIRE.defaultBlockState()));
                b.set(x0 + 2, y0 + 1, cz + 3, lit(Blocks.SOUL_CAMPFIRE.defaultBlockState()));
            }
            default -> {
            }
        }
    }

    private static BlockState slab(DungeonDef.Palette pal) {
        if (pal == DungeonDef.FROST) return Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
        if (pal == DungeonDef.FORGE) return Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState();
        if (pal == DungeonDef.ANCIENT) return Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
        return Blocks.STONE_BRICK_SLAB.defaultBlockState();
    }

    private static BlockState rubble(MobKit.Theme theme, Random rnd) {
        return switch (theme) {
            case FROST -> with(Blocks.SNOW.defaultBlockState(), BlockStateProperties.LAYERS, 2 + rnd.nextInt(3));
            case FORGE -> rnd.nextBoolean() ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE_SLAB.defaultBlockState();
            case DEPTHS -> rnd.nextBoolean() ? Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState() : Blocks.SCULK.defaultBlockState();
            default -> rnd.nextBoolean() ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState();
        };
    }

    /** Something standing against a wall. */
    static void wallProp(Builder b, MobKit.Theme theme, Random rnd, int x, int y, int z) {
        switch (theme) {
            case CRYPT -> {
                switch (rnd.nextInt(6)) {
                    case 0 -> {
                        b.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
                        b.set(x, y + 1, z, Blocks.BOOKSHELF.defaultBlockState());
                    }
                    case 1 -> b.set(x, y, z, candles(rnd));
                    case 2 -> b.set(x, y, z, skull(rnd));
                    case 3 -> b.set(x, y, z, Blocks.BONE_BLOCK.defaultBlockState());
                    case 4 -> {
                        // A gravestone: a wall with a carved top
                        b.set(x, y, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                        b.set(x, y + 1, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                    }
                    default -> b.set(x, y, z, Blocks.COBWEB.defaultBlockState());
                }
            }
            case FROST -> {
                switch (rnd.nextInt(5)) {
                    case 0 -> {
                        b.set(x, y, z, Blocks.PACKED_ICE.defaultBlockState());
                        b.set(x, y + 1, z, Blocks.BLUE_ICE.defaultBlockState());
                    }
                    case 1 -> b.set(x, y, z, with(Blocks.SNOW.defaultBlockState(), BlockStateProperties.LAYERS, 3 + rnd.nextInt(3)));
                    case 2 -> b.set(x, y, z, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState());
                    case 3 -> b.set(x, y, z, Blocks.BARREL.defaultBlockState());
                    default -> b.set(x, y, z, Blocks.SNOW_BLOCK.defaultBlockState());
                }
            }
            case FORGE -> {
                switch (rnd.nextInt(6)) {
                    case 0 -> b.set(x, y, z, Blocks.ANVIL.defaultBlockState());
                    case 1 -> b.set(x, y, z, lit(Blocks.BLAST_FURNACE.defaultBlockState()));
                    case 2 -> b.set(x, y, z, Blocks.LAVA_CAULDRON.defaultBlockState());
                    case 3 -> b.set(x, y, z, Blocks.SMITHING_TABLE.defaultBlockState());
                    case 4 -> b.set(x, y, z, Blocks.GILDED_BLACKSTONE.defaultBlockState());
                    default -> b.set(x, y, z, Blocks.BARREL.defaultBlockState());
                }
            }
            case DEPTHS -> {
                switch (rnd.nextInt(5)) {
                    case 0 -> b.set(x, y, z, candles(rnd));
                    case 1 -> b.set(x, y, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                    case 2 -> b.set(x, y, z, Blocks.SCULK.defaultBlockState());
                    case 3 -> {
                        b.set(x, y, z, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
                        b.set(x, y + 1, z, candles(rnd));
                    }
                    default -> b.set(x, y, z, Blocks.DECORATED_POT.defaultBlockState());
                }
            }
        }
    }

    /** A bigger piece standing on the floor. */
    static void floorPiece(Builder b, MobKit.Theme theme, DungeonDef.Palette pal, Random rnd, int x, int y, int z, int h) {
        switch (theme) {
            case CRYPT -> {
                if (rnd.nextBoolean()) {
                    // A coffin: two dark slabs end to end with a candle on top
                    b.set(x, y, z, Blocks.DARK_OAK_SLAB.defaultBlockState());
                    b.set(x + 1, y, z, Blocks.DARK_OAK_SLAB.defaultBlockState());
                } else {
                    b.set(x, y, z, skull(rnd));
                    b.set(x + 1, y, z, candles(rnd));
                }
            }
            case FROST -> {
                // An ice spike: thick at the bottom, thin at the top
                int tall = 2 + rnd.nextInt(Math.max(1, h - 4));
                for (int k = 0; k < tall; k++) b.set(x, y + k, z, k < tall / 2 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.BLUE_ICE.defaultBlockState());
                b.set(x, y + tall, z, Blocks.ICE.defaultBlockState());
            }
            case FORGE -> {
                if (rnd.nextBoolean()) {
                    b.set(x, y, z, lit(Blocks.CAMPFIRE.defaultBlockState()));
                } else {
                    b.set(x, y, z, Blocks.GOLD_BLOCK.defaultBlockState());
                    b.set(x, y + 1, z, Blocks.ANVIL.defaultBlockState());
                }
            }
            case DEPTHS -> {
                b.set(x, y, z, Blocks.SCULK.defaultBlockState());
                b.set(x, y + 1, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                b.set(x + 1, y - 1, z, Blocks.SCULK.defaultBlockState());
            }
        }
    }

    /** Where the boss waits: steps up to a carved seat, with a fire on either side. */
    private static void throne(Builder b, MobKit.Theme theme, DungeonDef.Palette pal, int x, int y, int cz) {
        b.fill(x - 1, y + 1, cz - 2, x + 1, y + 1, cz + 2, pal.trim());
        b.fill(x, y + 2, cz - 1, x + 1, y + 2, cz + 1, pal.pillar());
        b.set(x + 1, y + 3, cz, pal.trim());
        b.set(x + 1, y + 4, cz, pal.trim());
        BlockState fire = theme == MobKit.Theme.CRYPT || theme == MobKit.Theme.DEPTHS ? Blocks.SOUL_CAMPFIRE.defaultBlockState() : Blocks.CAMPFIRE.defaultBlockState();
        b.set(x - 1, y + 2, cz - 2, lit(fire));
        b.set(x - 1, y + 2, cz + 2, lit(fire));
    }

    private static void treasure(Builder b, Random rnd, int x0, int y0, int z0, int x1, int z1, int cz) {
        for (int k = 0; k < 6; k++) {
            int x = x0 + 2 + rnd.nextInt(Math.max(1, x1 - x0 - 3)), z = rnd.nextBoolean() ? z0 + 2 : z1 - 2;
            BlockState s = switch (rnd.nextInt(4)) {
                case 0 -> Blocks.GOLD_BLOCK.defaultBlockState();
                case 1 -> Blocks.DECORATED_POT.defaultBlockState();
                case 2 -> Blocks.BARREL.defaultBlockState();
                default -> Blocks.RAW_GOLD_BLOCK.defaultBlockState();
            };
            b.set(x, y0 + 1, z, s);
        }
    }

    // ------------------------------------------------------------------ corridors

    /** A corridor running east from (x, y, z) for len blocks: an arch at each end, torches, cobwebs and a runner on the floor. */
    static void corridor(Builder b, int x, int y, int z, int len, DungeonDef.Palette pal, Random rnd) {
        MobKit.Theme theme = MobKit.themeOf(pal);
        for (int i = 0; i < len; i++) b.set(x + i, y, z, pal.trim());
        for (int end : new int[]{x, x + len - 1}) {
            b.fill(end, y + 1, z - 2, end, y + 4, z - 2, pal.pillar());
            b.fill(end, y + 1, z + 2, end, y + 4, z + 2, pal.pillar());
            b.fill(end, y + 4, z - 1, end, y + 4, z + 1, pal.trim());
        }
        int mid = x + len / 2;
        b.set(mid, y + 2, z - 1, torch(theme, Direction.SOUTH));
        b.set(mid, y + 2, z + 1, torch(theme, Direction.NORTH));
        if (theme != MobKit.Theme.FORGE && rnd.nextBoolean()) b.set(x + 1 + rnd.nextInt(Math.max(1, len - 2)), y + 3, z + (rnd.nextBoolean() ? 1 : -1),
                Blocks.COBWEB.defaultBlockState());
        if (theme == MobKit.Theme.FROST) {
            for (int i = 1; i < len - 1; i++) if (rnd.nextInt(3) == 0) b.set(x + i, y + 1, z + (rnd.nextBoolean() ? 1 : -1), with(Blocks.SNOW.defaultBlockState(),
                    BlockStateProperties.LAYERS, 1));
        }
    }
}
