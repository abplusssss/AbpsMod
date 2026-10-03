package dev.abps.content;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The farm: seasons, sprinklers, greenhouse glass, rich compost, crop quality, crows and scarecrows, milk bottles, and
 * where new seeds and fruit saplings come from.
 */
public final class Farming {

    private Farming() {
    }

    // ------------------------------------------------------------------ seasons

    public enum Season {
        SPRING("Spring", "#9CCC65", "Crops near you grow faster."),
        SUMMER("Summer", "#FFD54F", "Better odds of high-quality crops."),
        AUTUMN("Autumn", "#FF8A65", "Bigger harvests and more fruit."),
        WINTER("Winter", "#80DEEA", "Crops only get help under greenhouse glass.");

        public final String title, color, perk;

        Season(String title, String color, String perk) {
            this.title = title;
            this.color = color;
            this.perk = perk;
        }
    }

    public static final int DAYS_PER_SEASON = 7;

    public static Season season() {
        MinecraftServer s = AbpsMod.server();
        if (s == null || s.overworld() == null) return Season.SPRING;
        long day = Time.dayTime(s.overworld()) / 24000L;
        return Season.values()[(int) ((day / DAYS_PER_SEASON) % 4)];
    }

    public static int seasonDay() {
        MinecraftServer s = AbpsMod.server();
        if (s == null || s.overworld() == null) return 1;
        return (int) ((Time.dayTime(s.overworld()) / 24000L) % DAYS_PER_SEASON) + 1;
    }

    // ------------------------------------------------------------------ wiring

    private static Set<String> fertile = new HashSet<>();
    private static final Gson GSON = new Gson();
    private static Season lastSeason;
    /** The block the player is breaking right now, so the drop hook knows where it was. */
    private static BlockPos breaking;

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (stack.is(Food.item("rich_compost"))) {
                BlockPos soil = state.getBlock() instanceof CropBlock ? pos.below() : pos;
                if (!(level.getBlockState(soil).getBlock() instanceof FarmlandBlock)) return InteractionResult.PASS;
                if (level instanceof ServerLevel sl) {
                    fertile.add(key(sl, soil));
                    if (!player.isCreative()) stack.shrink(1);
                    Fx.burst(sl, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(soil).add(0, 0.6, 0), 10, 0.4, 0.02);
                    Fx.sound(sl, Vec3.atCenterOf(soil), SoundEvents.COMPOSTER_FILL_SUCCESS, 1f, 1f);
                    if (player instanceof ServerPlayer p) AbpsMod.service().actionBar(p, "<#8D6E63>Rich soil! <gray>Crops here come up bigger and better.");
                    save();
                }
                return InteractionResult.SUCCESS;
            }
            // Bone meal on a fruit sapling: a good chance to grow it right away
            if (stack.is(Items.BONE_MEAL) && state.getBlock() instanceof FarmBlocks.FruitSapling sapling) {
                if (level instanceof ServerLevel sl) {
                    if (!player.isCreative()) stack.shrink(1);
                    Fx.burst(sl, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(pos), 8, 0.4, 0.02);
                    if (sl.getRandom().nextFloat() < 0.4f) sapling.grow(sl, pos, sl.getRandom());
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        // Glass bottles on cows and goats
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.GLASS_BOTTLE)) return InteractionResult.PASS;
            boolean cow = entity.getType() == EntityTypes.COW || entity.getType() == EntityTypes.MOOSHROOM;
            boolean goat = entity.getType() == EntityTypes.GOAT;
            if (!cow && !goat) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                if (!player.isCreative()) stack.shrink(1);
                ItemStack milk = new ItemStack(Food.item(goat ? "goat_milk" : "milk_bottle"));
                if (!player.getInventory().add(milk)) ModContent.drop(player, milk);
                level.playSound(null, entity.blockPosition(), goat ? SoundEvents.GOAT_MILK : SoundEvents.COW_MILK, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            breaking = pos;
            return true;
        });
        loot();
        ServerTickEvents.END_SERVER_TICK.register(Farming::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
    }

    /** New seeds hide in grass; fruit saplings fall from leaves now and then. */
    private static void loot() {
        LootTableEvents.MODIFY.register((key, table, source, provider) -> {
            if (!source.isBuiltin()) return;
            String path = key.identifier().getPath();
            if (path.equals("blocks/short_grass") || path.equals("blocks/tall_grass") || path.equals("blocks/fern")) {
                LootPool.Builder pool = LootPool.lootPool().add(EmptyLootItem.emptyItem().setWeight(60));
                for (String c : Food.CROPS) pool.add(LootItem.lootTableItem(Food.item(c + "_seeds")).setWeight(2));
                table.withPool(pool);
            } else if (path.equals("blocks/oak_leaves") || path.equals("blocks/birch_leaves") || path.equals("blocks/jungle_leaves")
                    || path.equals("blocks/acacia_leaves") || path.equals("blocks/dark_oak_leaves")) {
                LootPool.Builder pool = LootPool.lootPool().add(EmptyLootItem.emptyItem().setWeight(200));
                for (String f : Food.FRUITS) pool.add(LootItem.lootTableItem(Food.block(f + "_sapling").asItem()).setWeight(2));
                table.withPool(pool);
            }
        });
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
    }

    // ------------------------------------------------------------------ growth helpers

    /** Moves a crop (or anything bone meal works on) along one stage. */
    static boolean nudge(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof CropBlock crop) {
            if (crop.isMaxAge(s)) return false;
            if (s.hasProperty(CropBlock.AGE)) {
                level.setBlock(pos, s.setValue(CropBlock.AGE, Math.min(7, s.getValue(CropBlock.AGE) + 1)), 2);
                return true;
            }
        }
        if (s.getBlock() instanceof FarmBlocks.FruitSapling sapling) return sapling.grow(level, pos, level.getRandom());
        if (s.getBlock() instanceof BonemealableBlock && !(s.getBlock() instanceof net.minecraft.world.level.block.GrassBlock)) {
            return dev.abps.classes.Harvester.grow(level, pos);
        }
        return false;
    }

    /** Sprinkler random tick: wet farmland and help crops along. */
    static void sprinkle(ServerLevel level, BlockPos pos, int r) {
        int helped = 0;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-r, -1, -r), pos.offset(r, 0, r))) {
            BlockState s = level.getBlockState(at);
            if (s.getBlock() instanceof FarmlandBlock && s.hasProperty(FarmlandBlock.MOISTURE) && s.getValue(FarmlandBlock.MOISTURE) < 7) {
                level.setBlock(at, s.setValue(FarmlandBlock.MOISTURE, 7), 2);
            }
            BlockPos crop = at.above();
            if (level.getBlockState(crop).getBlock() instanceof CropBlock && level.getRandom().nextFloat() < (season() == Season.WINTER ? 0.15f : 0.45f)) {
                if (nudge(level, crop.immutable())) helped++;
            }
        }
        Fx.ring(level, ParticleTypes.SPLASH, Vec3.atCenterOf(pos).add(0, 0.4, 0), r, 30);
        if (helped > 0) Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.WEATHER_RAIN, 0.3f, 1.6f);
    }

    /** Greenhouse glass random tick: the crop or sapling in the column below gets a boost. */
    static void greenhouse(ServerLevel level, BlockPos pos) {
        for (int d = 1; d <= 10; d++) {
            BlockPos at = pos.below(d);
            BlockState s = level.getBlockState(at);
            if (s.isAir()) continue;
            if (s.getBlock() instanceof CropBlock || s.getBlock() instanceof FarmBlocks.FruitSapling) {
                if (level.getRandom().nextFloat() < (season() == Season.WINTER ? 0.8f : 0.5f)) nudge(level, at);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ quality

    /** Called from the block drop hook: crop quality, bigger harvests from rich soil and in autumn. */
    public static void modifyDrops(ServerPlayer p, BlockState state, List<ItemStack> drops) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        ServerLevel level = (ServerLevel) p.level();
        boolean rich = breaking != null && fertile.contains(key(level, breaking.below()));
        double fine = 0.10 + (rich ? 0.15 : 0) + (season() == Season.SUMMER ? 0.08 : 0) + (season() == Season.WINTER ? -0.05 : 0);
        double prime = 0.02 + (rich ? 0.05 : 0) + (season() == Season.SUMMER ? 0.03 : 0);
        int n = drops.size();
        for (int i = 0; i < n; i++) {
            ItemStack d = drops.get(i);
            if (!d.has(DataComponents.FOOD)) continue;
            if ((rich || season() == Season.AUTUMN) && level.getRandom().nextFloat() < 0.35f) d.grow(1);
            double roll = level.getRandom().nextDouble();
            if (roll < prime) quality(d, 2);
            else if (roll < prime + fine) quality(d, 1);
        }
    }

    /** Marks a food stack Fine (1) or Prime (2): a nicer name and more filling. */
    public static void quality(ItemStack s, int q) {
        FoodProperties f = s.get(DataComponents.FOOD);
        if (f == null) return;
        String base = s.getHoverName().getString();
        s.set(DataComponents.ITEM_NAME, dev.abps.util.Text.mm(q == 2 ? "<#FFD54F>★★ Prime " + base : "<#E0E0E0>★ Fine " + base));
        s.set(DataComponents.FOOD, new FoodProperties(f.nutrition() + q, f.saturation() * (1 + 0.5f * q), f.canAlwaysEat()));
        CompoundTag t = new CompoundTag();
        t.putInt("abps_quality", q);
        CustomData.set(DataComponents.CUSTOM_DATA, s, t);
    }

    // ------------------------------------------------------------------ ticking: seasons, spring growth and crows

    private static long ticks;

    private static void tick(MinecraftServer server) {
        ticks++;
        ServerLevel world = server.overworld();
        if (world == null) return;
        Season now = season();
        if (lastSeason != null && now != lastSeason) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                AbpsMod.service().banner(p, "<bold><" + now.color + ">" + now.title + "</" + now.color + "></bold>", "<gray>" + now.perk, Integer.parseInt(now.color.substring(1), 16), 80);
            }
        }
        lastSeason = now;
        List<ServerPlayer> players = world.players();
        if (players.isEmpty()) return;
        // Spring: crops around players come along on their own
        if (now == Season.SPRING && ticks % 100 == 0) {
            for (ServerPlayer p : players) {
                for (int k = 0; k < 30; k++) {
                    BlockPos at = p.blockPosition().offset(world.getRandom().nextInt(49) - 24, world.getRandom().nextInt(7) - 3, world.getRandom().nextInt(49) - 24);
                    if (world.getBlockState(at).getBlock() instanceof CropBlock && world.getRandom().nextFloat() < 0.3f) nudge(world, at);
                }
            }
        }
        // Crows: in the daytime they peck at grown crops that no scarecrow is watching
        long time = Time.dayTime(world) % 24000;
        if (ticks % 2400 == 1200 && time < 12000) {
            for (ServerPlayer p : players) crows(world, p);
        }
    }

    private static void crows(ServerLevel world, ServerPlayer p) {
        int pecked = 0;
        BlockPos where = null;
        for (int k = 0; k < 40; k++) {
            BlockPos at = p.blockPosition().offset(world.getRandom().nextInt(41) - 20, world.getRandom().nextInt(7) - 3, world.getRandom().nextInt(41) - 20);
            BlockState s = world.getBlockState(at);
            if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s) || !s.hasProperty(CropBlock.AGE)) continue;
            if (!world.canSeeSky(at) || guarded(world, at) || world.getRandom().nextFloat() > 0.3f) continue;
            world.setBlock(at, s.setValue(CropBlock.AGE, 4), 2);
            Fx.burst(world, ParticleTypes.POOF, Vec3.atCenterOf(at), 6, 0.3, 0.02);
            pecked++;
            where = at;
        }
        if (pecked > 0) {
            Fx.sound(world, Vec3.atCenterOf(where), SoundEvents.PARROT_AMBIENT, 1f, 0.6f);
            AbpsMod.service().actionBar(p, "<gray>Crows pecked at " + pecked + " of your crops! <dark_gray>A Scarecrow keeps them away.");
        }
    }

    private static boolean guarded(ServerLevel world, BlockPos crop) {
        Block scare = Food.block("scarecrow");
        for (BlockPos at : BlockPos.betweenClosed(crop.offset(-8, -2, -8), crop.offset(8, 3, 8))) {
            if (world.getBlockState(at).is(scare)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ saving

    private static Path file() {
        return AbpsMod.data().root().resolve("farming.json");
    }

    private static void load() {
        try {
            if (Files.exists(file())) {
                Set<String> s = GSON.fromJson(Files.readString(file()), new TypeToken<HashSet<String>>() {
                }.getType());
                if (s != null) fertile = s;
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not read farm data: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), GSON.toJson(fertile));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not save farm data: {}", e.toString());
        }
    }
}
