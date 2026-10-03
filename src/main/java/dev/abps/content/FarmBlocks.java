package dev.abps.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/** The few block classes the farm and kitchen need. */
public final class FarmBlocks {

    private FarmBlocks() {
    }

    /** A block with a smaller shape than a full cube (cutting board, sprinkler, scarecrow). */
    public static class Shaped extends Block {
        private final VoxelShape shape;

        public Shaped(VoxelShape shape, BlockBehaviour.Properties props) {
            super(props);
            this.shape = shape;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shape;
        }
    }

    /** Waters the farmland around it and helps the crops there along, every random tick. */
    public static class Sprinkler extends Shaped {
        private final int radius;

        public Sprinkler(int radius, BlockBehaviour.Properties props) {
            super(Block.box(3, 0, 3, 13, 11, 13), props.randomTicks());
            this.radius = radius;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return true;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            Farming.sprinkle(level, pos, radius);
        }
    }

    /** Crops under greenhouse glass grow faster. */
    public static class GreenhouseGlass extends Block {
        public GreenhouseGlass(BlockBehaviour.Properties props) {
            super(props.randomTicks());
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return true;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            Farming.greenhouse(level, pos);
        }
    }

    /** Fruit tree leaves: they slowly fruit, and right-clicking picks the fruit. */
    public static class FruitLeaves extends Block {
        public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
        private final Supplier<net.minecraft.world.item.Item> fruit;

        public FruitLeaves(Supplier<net.minecraft.world.item.Item> fruit, BlockBehaviour.Properties props) {
            super(props.randomTicks());
            this.fruit = fruit;
            registerDefaultState(stateDefinition.any().setValue(RIPE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(RIPE);
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return !state.getValue(RIPE);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (random.nextInt(Farming.season() == Farming.Season.WINTER ? 18 : 6) == 0) level.setBlock(pos, state.setValue(RIPE, true), 2);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!state.getValue(RIPE)) return InteractionResult.PASS;
            if (level instanceof ServerLevel sl) {
                int n = 1 + sl.random.nextInt(2) + (Farming.season() == Farming.Season.AUTUMN ? 1 : 0);
                Block.popResource(sl, pos.below(), new ItemStack(fruit.get(), n));
                sl.setBlock(pos, state.setValue(RIPE, false), 2);
                sl.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Ruby rail: a rail that keeps minecarts moving at full speed without redstone (see Extras). */
    public static class RubyRail extends net.minecraft.world.level.block.RailBlock {
        public RubyRail(BlockBehaviour.Properties props) {
            super(props);
        }
    }

    /** A feast on a platter: four servings, each one feeds whoever takes it and shares the feast's buffs. */
    public static class Feast extends Shaped {
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty SERVINGS =
                net.minecraft.world.level.block.state.properties.IntegerProperty.create("servings", 1, 4);
        private final java.util.List<net.minecraft.world.effect.MobEffectInstance> effects;

        public Feast(java.util.List<net.minecraft.world.effect.MobEffectInstance> effects, BlockBehaviour.Properties props) {
            super(Block.box(1, 0, 1, 15, 7, 15), props);
            this.effects = effects;
            registerDefaultState(stateDefinition.any().setValue(SERVINGS, 4));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(SERVINGS);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel sl) {
                var food = player.getFoodData();
                food.setFoodLevel(Math.min(20, food.getFoodLevel() + 8));
                food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + 10));
                for (var e : effects) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(e));
                int left = state.getValue(SERVINGS) - 1;
                if (left <= 0) sl.removeBlock(pos, false);
                else sl.setBlock(pos, state.setValue(SERVINGS, left), 3);
                sl.playSound(null, pos, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8f, 1f);
                sl.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.02);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** A fruit tree sapling: grows into a small tree of oak logs and fruit leaves. */
    public static class FruitSapling extends Block {
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);
        private final Supplier<Block> leaves;

        public FruitSapling(Supplier<Block> leaves, BlockBehaviour.Properties props) {
            super(props.randomTicks());
            this.leaves = leaves;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).is(BlockTags.DIRT);
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return true;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (random.nextInt(5) == 0 && level.getMaxLocalRawBrightness(pos.above()) >= 9) grow(level, pos, random);
        }

        /** Plants the tree. Returns false when there is no room. */
        public boolean grow(ServerLevel level, BlockPos pos, RandomSource random) {
            int h = 3 + random.nextInt(2);
            for (int y = 1; y <= h + 2; y++) if (!level.getBlockState(pos.above(y)).isAir()) return false;
            BlockState log = Blocks.OAK_LOG.defaultBlockState(), leaf = leaves.get().defaultBlockState();
            for (int y = 0; y < h; y++) level.setBlock(pos.above(y), log, 3);
            BlockPos top = pos.above(h - 1);
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = -1; dy <= 2; dy++)
                    for (int dz = -2; dz <= 2; dz++) {
                        int r = Math.abs(dx) + Math.abs(dz) + Math.max(0, dy);
                        if (r > 3 || (r == 3 && random.nextInt(2) == 0)) continue;
                        BlockPos at = top.offset(dx, dy, dz);
                        if (level.getBlockState(at).isAir()) level.setBlock(at, leaf, 3);
                    }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 20, 1.2, 1.2, 1.2, 0.05);
            return true;
        }
    }
}
