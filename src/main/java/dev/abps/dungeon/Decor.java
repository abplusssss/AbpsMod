package dev.abps.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Random;

/**
 * Themed props for dungeon rooms (the architecture itself is in {@link Architect}): coffins, skulls, candles and
 * bookshelves in the crypt; snow, ice spikes and frozen pillars in the frost halls; anvils, furnaces and lava
 * cauldrons in the forge; sculk, amethyst and candles in the depths.
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
                    b.set(x, y, z, Blocks.LAVA_CAULDRON.defaultBlockState());
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
}
