package dev.abps.content;

import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Exploration: ruins, camps, obelisks and sunken shrines that appear in new land; Crystal Hollows caverns deep
 * underground; caravans of travelling merchants; treasure maps that lead to buried chests; and butterflies, fireflies
 * and birds to make the world feel alive.
 */
public final class Explore {

    private Explore() {
    }

    private static final Random RND = new Random();
    private static final ConcurrentLinkedQueue<long[]> PENDING = new ConcurrentLinkedQueue<>();
    private static final String CARAVAN_TAG = "abps_caravan";
    private static final ConcurrentLinkedQueue<long[]> END_PENDING = new ConcurrentLinkedQueue<>();

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (generated && level.dimension() == Level.END && RND.nextInt(3) == 0) {
                END_PENDING.add(new long[]{chunk.getPos().getMinBlockX() + 8, chunk.getPos().getMinBlockZ() + 8});
                return;
            }
            if (!generated || level.dimension() != Level.OVERWORLD) return;
            int roll = RND.nextInt(900);
            if (roll < 4) PENDING.add(new long[]{chunk.getPos().getMinBlockX() + 8, chunk.getPos().getMinBlockZ() + 8, 0});
            else if (roll < 5) PENDING.add(new long[]{chunk.getPos().getMinBlockX() + 8, chunk.getPos().getMinBlockZ() + 8, 1});
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack s = player.getItemInHand(hand);
            if (!s.is(Fishing.TREASURE_MAP)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer p && level instanceof ServerLevel sl) readMap(p, sl, s);
            return InteractionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!entity.entityTags().contains(CARAVAN_TAG)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer p && hand == net.minecraft.world.InteractionHand.MAIN_HAND) trade(p, entity);
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Explore::tick);
    }

    // ------------------------------------------------------------------ loot

    private static ItemStack book(ServerLevel level) {
        String[] pool = {"lifesteal", "executioner", "venom", "frostbite", "dodge", "timber", "excavator", "smelting_touch", "magnetic", "replanting",
                "explosive_shot", "leaping", "night_owl", "second_wind", "thunderstrike", "vein_miner", "homing", "soulbound"};
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var key = net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, AbpsMod.id(pool[RND.nextInt(pool.length)]));
        var holder = reg.get(key);
        ItemStack b = new ItemStack(Items.ENCHANTED_BOOK);
        holder.ifPresent(h -> EnchantmentHelper.updateEnchantments(b, m -> m.set(h, 1 + RND.nextInt(Math.max(1, h.value().getMaxLevel())))));
        return b;
    }

    static List<ItemStack> loot(ServerLevel level, int tier) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.EMERALD, 3 + RND.nextInt(6) + tier * 3));
        out.add(new ItemStack(Items.GOLD_INGOT, 2 + RND.nextInt(4)));
        out.add(new ItemStack(Items.BREAD, 3 + RND.nextInt(4)));
        if (RND.nextInt(3) == 0) out.add(new ItemStack(Food.item(Food.CROPS[RND.nextInt(Food.CROPS.length)] + "_seeds"), 2 + RND.nextInt(4)));
        if (RND.nextInt(4) == 0) out.add(new ItemStack(Food.block(Food.FRUITS[RND.nextInt(Food.FRUITS.length)] + "_sapling").asItem()));
        if (RND.nextInt(3) <= tier) out.add(new ItemStack(ModContent.RUBY, 1 + RND.nextInt(2 + tier)));
        if (RND.nextInt(3) <= tier) out.add(book(level));
        if (RND.nextInt(4) == 0) out.add(new ItemStack(Fishing.TREASURE_MAP));
        if (RND.nextInt(3) == 0) out.add(new ItemStack(Fishing.GOLDEN_BAIT, 1 + RND.nextInt(2)));
        if (tier >= 2) out.add(new ItemStack(Items.DIAMOND, 1 + RND.nextInt(3)));
        if (tier >= 2 && RND.nextInt(3) == 0) out.add(new ItemStack(Items.NETHERITE_SCRAP));
        return out;
    }

    static void chest(ServerLevel level, BlockPos pos, int tier) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity c) {
            List<ItemStack> items = loot(level, tier);
            for (ItemStack s : items) {
                int slot;
                int tries = 0;
                do slot = RND.nextInt(c.getContainerSize()); while (!c.getItem(slot).isEmpty() && ++tries < 20);
                c.setItem(slot, s);
            }
        }
    }

    // ------------------------------------------------------------------ structures

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, 2);
    }

    private static void build(ServerLevel level, int x, int z, int kind) {
        if (!level.hasChunkAt(new BlockPos(x, 64, z))) return;
        if (kind == 1) {
            crystalHollow(level, x, z);
            return;
        }
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        BlockPos base = new BlockPos(x, y, z);
        var biome = level.getBiome(base);
        String path = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        boolean ocean = biome.is(BiomeTags.IS_OCEAN);
        boolean water = !level.getFluidState(base).isEmpty() || !level.getFluidState(base.above()).isEmpty();
        if (ocean) sunkenShrine(level, base);
        else if (water) return;
        else if (path.contains("desert") || path.contains("badlands")) obelisk(level, base);
        else if (RND.nextBoolean()) watchtower(level, base);
        else camp(level, base);
    }

    private static void watchtower(ServerLevel level, BlockPos b) {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState(), mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        int h = 9 + RND.nextInt(4);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > 3.4) continue;
                for (int d = 1; d <= 4; d++) set(level, b.offset(dx, -d, dz), brick);
                set(level, b.offset(dx, 0, dz), r < 2.5 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : brick);
                if (r >= 2.5) {
                    // Broken on one side, the top crumbling away
                    int top = h - (dx > 1 ? 4 + RND.nextInt(3) : RND.nextInt(3));
                    for (int k = 1; k <= top; k++) {
                        if (dx == 0 && dz == -3 && k <= 2) continue; // doorway
                        set(level, b.offset(dx, k, dz), RND.nextInt(5) == 0 ? mossy : RND.nextInt(6) == 0 ? cracked : brick);
                    }
                } else {
                    for (int k = 1; k <= h; k++) set(level, b.offset(dx, k, dz), Blocks.AIR.defaultBlockState());
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) set(level, b.offset(dx, h - 3, dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            }
        for (int k = 1; k < h - 3; k++) set(level, b.offset(0, k, 2), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        set(level, b.offset(0, h - 3, 2), Blocks.AIR.defaultBlockState());
        chest(level, b.offset(-1, h - 2, -1), 1);
        set(level, b.offset(1, h - 2, 1), Blocks.LANTERN.defaultBlockState());
    }

    private static void camp(ServerLevel level, BlockPos b) {
        BlockState[] wools = {Blocks.WOOL.red().defaultBlockState(), Blocks.WOOL.brown().defaultBlockState(), Blocks.WOOL.green().defaultBlockState()};
        for (int t = 0; t < 2; t++) {
            BlockPos c = b.offset(t == 0 ? -4 : 4, 0, RND.nextInt(3) - 1);
            BlockState w = wools[RND.nextInt(wools.length)];
            for (int dz = -2; dz <= 2; dz++) {
                for (int k = 0; k < 3; k++) {
                    set(level, c.offset(-2 + k, 1 + k, dz), w);
                    set(level, c.offset(2 - k, 1 + k, dz), w);
                }
                set(level, c.offset(0, 1, dz), Blocks.AIR.defaultBlockState());
                set(level, c.offset(0, 2, dz), Blocks.AIR.defaultBlockState());
            }
        }
        set(level, b.above(), Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        set(level, b.offset(2, 1, 3), Blocks.BARREL.defaultBlockState());
        set(level, b.offset(-2, 1, 3), Blocks.HAY_BLOCK.defaultBlockState());
        chest(level, b.offset(4, 1, 0), 0);
    }

    private static void obelisk(ServerLevel level, BlockPos b) {
        BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState(), chisel = Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) set(level, b.offset(dx, 0, dz), smooth);
        int h = 12 + RND.nextInt(5);
        for (int k = 1; k <= h; k++) {
            int w = k < 3 ? 1 : 0;
            for (int dx = -w; dx <= w; dx++)
                for (int dz = -w; dz <= w; dz++) set(level, b.offset(dx, k, dz), k % 4 == 0 ? chisel : cut);
        }
        set(level, b.offset(0, h + 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        // A hidden chamber under the plinth
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int k = -5; k <= -1; k++) {
                    boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || k == -5;
                    set(level, b.offset(dx, k, dz), wall ? cut : Blocks.AIR.defaultBlockState());
                }
        chest(level, b.offset(0, -4, 0), 1);
        set(level, b.offset(1, -4, 1), Blocks.LANTERN.defaultBlockState());
        set(level, b.offset(-1, -4, -1), Blocks.LANTERN.defaultBlockState());
    }

    private static void sunkenShrine(ServerLevel level, BlockPos b) {
        BlockState bricks = Blocks.PRISMARINE_BRICKS.defaultBlockState(), dark = Blocks.DARK_PRISMARINE.defaultBlockState();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                set(level, b.offset(dx, -1, dz), (dx + dz & 1) == 0 ? dark : bricks);
                if ((Math.abs(dx) == 4 && Math.abs(dz) % 4 == 0) || (Math.abs(dz) == 4 && Math.abs(dx) % 4 == 0)) {
                    int ph = 3 + RND.nextInt(3);
                    for (int k = 0; k < ph; k++) set(level, b.offset(dx, k, dz), bricks);
                    set(level, b.offset(dx, ph, dz), Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        chest(level, b, 2);
        set(level, b.offset(0, 0, 1), Blocks.SEA_LANTERN.defaultBlockState());
    }

    /** A cavern of crystals deep underground, with ruby ore in its walls. */
    private static void crystalHollow(ServerLevel level, int x, int z) {
        int y = -30 + RND.nextInt(20);
        int r = 10 + RND.nextInt(6);
        BlockPos c = new BlockPos(x, y, z);
        for (int dx = -r - 2; dx <= r + 2; dx++)
            for (int dy = -r / 2 - 2; dy <= r / 2 + 2; dy++)
                for (int dz = -r - 2; dz <= r + 2; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 4.0 + dz * dz);
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState here = level.getBlockState(p);
                    if (here.is(Blocks.BEDROCK) || here.hasBlockEntity()) continue;
                    if (d <= r) set(level, p, Blocks.AIR.defaultBlockState());
                    else if (d <= r + 1.6) {
                        int roll = RND.nextInt(20);
                        BlockState s = roll < 7 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : roll < 11 ? Blocks.CALCITE.defaultBlockState()
                                : roll < 13 ? Blocks.SMOOTH_BASALT.defaultBlockState() : roll < 14 ? ModContent.DEEPSLATE_RUBY_ORE.defaultBlockState()
                                : roll < 15 ? Blocks.BUDDING_AMETHYST.defaultBlockState() : roll < 16 ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState()
                                : Blocks.TUFF.defaultBlockState();
                        set(level, p, s);
                    }
                }
        // Crystal spires on the floor and hanging from the roof
        for (int k = 0; k < r * 2; k++) {
            int dx = RND.nextInt(r * 2) - r, dz = RND.nextInt(r * 2) - r;
            if (dx * dx + dz * dz > (r - 2) * (r - 2)) continue;
            boolean up = RND.nextBoolean();
            BlockPos start = c.offset(dx, up ? -r / 2 : r / 2, dz);
            while (level.getBlockState(start).isAir() && Math.abs(start.getY() - c.getY()) < r) start = start.offset(0, up ? -1 : 1, 0);
            int len = 2 + RND.nextInt(4);
            for (int i = 1; i <= len; i++) {
                BlockPos p = start.offset(0, up ? i : -i, 0);
                if (!level.getBlockState(p).isAir()) break;
                set(level, p, i == len ? Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, up ? Direction.UP : Direction.DOWN)
                        : Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
        }
        BlockPos floor = c;
        while (level.getBlockState(floor.below()).isAir() && floor.getY() > c.getY() - r) floor = floor.below();
        chest(level, floor, 2);
    }

    /** The Crystal Hollows End biome: crystal spires rising out of the end stone. */
    private static void endCrystals(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 64, z))) return;
        if (!level.getBiome(new BlockPos(x, 64, z)).is(ModContent.CRYSTAL_HOLLOWS)) return;
        for (int k = 0; k < 2 + RND.nextInt(3); k++) {
            int sx = x + RND.nextInt(13) - 6, sz = z + RND.nextInt(13) - 6;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
            if (y <= level.getMinY() + 1 || !level.getBlockState(new BlockPos(sx, y - 1, sz)).is(Blocks.END_STONE)) continue;
            int h = 3 + RND.nextInt(6);
            for (int i = 0; i < h; i++) {
                int w = i < h / 3 ? 1 : 0;
                for (int dx = -w; dx <= w; dx++)
                    for (int dz = -w; dz <= w; dz++) {
                        if (w == 1 && Math.abs(dx) + Math.abs(dz) == 2) continue;
                        set(level, new BlockPos(sx + dx, y + i, sz + dz), i == h - 1 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                                : RND.nextInt(5) == 0 ? Blocks.PURPUR_BLOCK.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
                    }
            }
            set(level, new BlockPos(sx, y + h, sz), Blocks.AMETHYST_CLUSTER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.UP));
            if (RND.nextInt(4) == 0) set(level, new BlockPos(sx + 1, y, sz), ModContent.ENDITE_ORE.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ treasure maps

    private static void readMap(ServerPlayer p, ServerLevel level, ItemStack map) {
        if (level.dimension() != Level.OVERWORLD) {
            AbpsMod.service().actionBar(p, "<gray>The map only makes sense in the overworld.");
            return;
        }
        var data = map.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = data == null ? new CompoundTag() : data.copyTag();
        if (!t.contains("tx")) {
            double a = RND.nextDouble() * Math.PI * 2, d = 300 + RND.nextInt(400);
            t.putInt("tx", (int) (p.getX() + Math.cos(a) * d));
            t.putInt("tz", (int) (p.getZ() + Math.sin(a) * d));
            CustomData.set(DataComponents.CUSTOM_DATA, map, t);
            map.set(DataComponents.ITEM_NAME, dev.abps.util.Text.mm("<gold>Treasure Map <gray>(" + t.getIntOr("tx", 0) + ", " + t.getIntOr("tz", 0) + ")"));
            AbpsMod.service().send(p, "<gold>The map shows an X at " + t.getIntOr("tx", 0) + ", " + t.getIntOr("tz", 0) + ". <gray>Hold it to follow the trail.");
        }
        Fx.sound(level, p, SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static void followMap(ServerPlayer p, ServerLevel level) {
        ItemStack map = p.getMainHandItem().is(Fishing.TREASURE_MAP) ? p.getMainHandItem() : p.getOffhandItem().is(Fishing.TREASURE_MAP) ? p.getOffhandItem() : null;
        if (map == null || level.dimension() != Level.OVERWORLD) return;
        var data = map.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().contains("tx")) return;
        CompoundTag t = data.copyTag();
        int tx = t.getIntOr("tx", 0), tz = t.getIntOr("tz", 0);
        double dx = tx + 0.5 - p.getX(), dz = tz + 0.5 - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 3) {
            // Dig it up: the chest is buried a few blocks down, right here
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) - 3;
            chest(level, new BlockPos(tx, y, tz), 2);
            Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, new Vec3(tx + 0.5, y + 3.5, tz + 0.5), 30, 0.6, 0.1);
            Fx.sound(level, p, SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
            AbpsMod.service().banner(p, "<bold><gold>X marks the spot!</gold></bold>", "<gray>Dig down right here", 0xFFD54F, 60);
            map.shrink(1);
            return;
        }
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot();
        int idx = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        AbpsMod.service().actionBar(p, "<gold>" + ARROWS[idx] + " <white>" + (int) dist + " blocks <gray>to the treasure");
    }

    // ------------------------------------------------------------------ caravans

    private record Deal(String what, int price, java.util.function.Supplier<ItemStack> item) {
    }

    private static List<Deal> deals() {
        List<Deal> d = new ArrayList<>();
        for (String c : Food.CROPS) d.add(new Deal("4 " + new ItemStack(Food.item(c + "_seeds")).getHoverName().getString(), 2, () -> new ItemStack(Food.item(c + "_seeds"), 4)));
        for (String f : Food.FRUITS) d.add(new Deal("a " + new ItemStack(Food.block(f + "_sapling")).getHoverName().getString(), 6, () -> new ItemStack(Food.block(f + "_sapling").asItem())));
        d.add(new Deal("a Ruby", 12, () -> new ItemStack(ModContent.RUBY)));
        d.add(new Deal("a Treasure Map", 16, () -> new ItemStack(Fishing.TREASURE_MAP)));
        d.add(new Deal("2 Golden Bait", 6, () -> new ItemStack(Fishing.GOLDEN_BAIT, 2)));
        d.add(new Deal("a Magma Rod", 24, () -> new ItemStack(Fishing.MAGMA_ROD)));
        d.add(new Deal("4 Sushi", 3, () -> new ItemStack(Fishing.SUSHI, 4)));
        return d;
    }

    private static void spawnCaravan(ServerLevel level, ServerPlayer near) {
        double a = RND.nextDouble() * Math.PI * 2;
        int x = (int) (near.getX() + Math.cos(a) * 24), z = (int) (near.getZ() + Math.sin(a) * 24);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) return;
        Mob trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
        if (trader == null) return;
        trader.snapTo(x + 0.5, y, z + 0.5, RND.nextFloat() * 360, 0);
        trader.addTag(CARAVAN_TAG);
        trader.setPersistenceRequired();
        List<Deal> all = deals();
        int pick = RND.nextInt(all.size());
        trader.addTag("abps_deal_" + pick);
        name(trader, all.get(pick));
        level.addFreshEntity(trader);
        for (int i = 0; i < 2; i++) {
            Mob llama = EntityTypes.TRADER_LLAMA.create(level, EntitySpawnReason.EVENT);
            if (llama == null) continue;
            llama.snapTo(x + 1.5 + i, y, z + 1.5, 0, 0);
            llama.addTag(CARAVAN_TAG + "_pack");
            level.addFreshEntity(llama);
        }
        AbpsMod.service().send(near, "<gold>🐪 A caravan has stopped nearby. <gray>Right-click the merchant with emeralds to buy; sneak-right-click to see the next deal.");
        // They move on after ten minutes
        dev.abps.util.Tasks.later(20 * 60 * 10, () -> {
            for (Entity e : level.getEntitiesOfClass(Entity.class, trader.getBoundingBox().inflate(32),
                    e -> e.entityTags().contains(CARAVAN_TAG) || e.entityTags().contains(CARAVAN_TAG + "_pack"))) e.discard();
        });
    }

    private static void name(Mob trader, Deal d) {
        trader.setCustomName(dev.abps.util.Text.mm("<gold>Caravan Merchant <gray>· " + d.what() + " for " + d.price() + " emeralds"));
        trader.setCustomNameVisible(true);
    }

    private static void trade(ServerPlayer p, Entity merchant) {
        List<Deal> all = deals();
        int pick = 0;
        for (String t : merchant.entityTags()) if (t.startsWith("abps_deal_")) pick = Integer.parseInt(t.substring(10));
        if (p.isShiftKeyDown()) {
            merchant.removeTag("abps_deal_" + pick);
            pick = (pick + 1) % all.size();
            merchant.addTag("abps_deal_" + pick);
            if (merchant instanceof Mob m) name(m, all.get(pick));
            return;
        }
        Deal d = all.get(Math.min(pick, all.size() - 1));
        int have = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.EMERALD)) have += p.getInventory().getItem(i).getCount();
        if (have < d.price()) {
            AbpsMod.service().actionBar(p, "<red>That costs " + d.price() + " emeralds (you have " + have + ").");
            return;
        }
        int left = d.price();
        for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.is(Items.EMERALD)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        ItemStack bought = d.item().get();
        if (!p.getInventory().add(bought)) ModContent.drop(p, bought);
        Fx.sound((ServerLevel) p.level(), merchant, SoundEvents.WANDERING_TRADER_YES, 1f, 1f);
        AbpsMod.service().actionBar(p, "<green>Bought " + d.what() + ".");
    }

    // ------------------------------------------------------------------ wildlife

    private static void wildlife(ServerLevel level, ServerPlayer p) {
        long t = Time.dayTime(level) % 24000;
        boolean night = t > 13000 && t < 23000;
        if (level.isRaining()) return;
        if (!night) {
            // Butterflies around flowers
            for (int k = 0; k < 6; k++) {
                BlockPos at = p.blockPosition().offset(RND.nextInt(17) - 8, RND.nextInt(5) - 2, RND.nextInt(17) - 8);
                if (!level.getBlockState(at).is(BlockTags.FLOWERS)) continue;
                int[] colors = {0xFFB74D, 0x81D4FA, 0xF48FB1, 0xFFF176, 0xCE93D8};
                int col = colors[RND.nextInt(colors.length)];
                Vec3 c = Vec3.atCenterOf(at).add(0, 0.6, 0);
                dev.abps.util.Tasks.repeat(10, 3, step -> Fx.burst(level, Fx.dust(col, 0.6f),
                        c.add(Math.sin(step * 0.9) * 0.6, step * 0.08, Math.cos(step * 0.7) * 0.6), 2, 0.05, 0));
            }
            // Now and then a flock of birds crosses the sky
            if (RND.nextInt(40) == 0 && level.canSeeSky(p.blockPosition())) {
                Vec3 start = p.position().add(RND.nextInt(40) - 20, 25 + RND.nextInt(10), -30);
                Vec3 dir = new Vec3(RND.nextDouble() - 0.5, 0, 1).normalize();
                dev.abps.util.Tasks.repeat(40, 2, step -> {
                    for (int b = 0; b < 5; b++) {
                        Vec3 at = start.add(dir.scale(step * 1.5)).add((b % 3) * 1.2 - 1.2, Math.sin(step * 0.6 + b) * 0.2, -(b / 2) * 1.0);
                        Fx.burst(level, Fx.dust(0x2B2B2B, 0.8f), at, 1, 0, 0);
                    }
                });
            }
        } else {
            // Fireflies in grass at night
            var biome = level.getBiome(p.blockPosition());
            String path = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            if (!(path.contains("swamp") || path.contains("forest") || path.contains("plains") || path.contains("meadow") || path.contains("river"))) return;
            for (int k = 0; k < 4; k++) {
                BlockPos at = p.blockPosition().offset(RND.nextInt(25) - 12, RND.nextInt(5) - 1, RND.nextInt(25) - 12);
                if (!level.getBlockState(at).isAir() || !level.canSeeSky(at)) continue;
                Fx.burst(level, Fx.dust(0xD4FF5C, 0.7f), Vec3.atCenterOf(at), 1, 0.3, 0);
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    private static long ticks;

    private static void tick(MinecraftServer server) {
        ticks++;
        ServerLevel world = server.overworld();
        if (world == null) return;
        long[] job;
        int built = 0;
        while (built < 1 && (job = PENDING.poll()) != null) {
            build(world, (int) job[0], (int) job[1], (int) job[2]);
            built++;
        }
        ServerLevel end = server.getLevel(Level.END);
        if (end != null && (job = END_PENDING.poll()) != null) endCrystals(end, (int) job[0], (int) job[1]);
        for (ServerPlayer p : world.players()) {
            if (ticks % 10 == 0) followMap(p, world);
            if (ticks % 20 == 0) wildlife(world, p);
        }
        // A caravan roughly every twenty minutes of daytime somewhere near someone
        long t = Time.dayTime(world) % 24000;
        if (ticks % 6000 == 3000 && t < 11000 && !world.players().isEmpty() && RND.nextInt(4) == 0) {
            ServerPlayer p = world.players().get(RND.nextInt(world.players().size()));
            spawnCaravan(world, p);
        }
    }

}
