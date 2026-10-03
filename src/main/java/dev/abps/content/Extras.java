package dev.abps.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The extras: backpacks, grappling hook, magnet charm, builder's wand, ruby apple, sleeping bag, mob trophies, hang
 * glider, Ender Wings, waystones, furniture you can sit on (and stairs and slabs too), display pedestals, plus
 * right-click harvesting, health readouts, party pings and inventory sorting.
 */
public final class Extras {

    private Extras() {
    }

    public static Item BACKPACK, RUBY_BACKPACK, GRAPPLING_HOOK, MAGNET_CHARM, BUILDERS_WAND, RUBY_APPLE, SLEEPING_BAG, MOB_TROPHY, HANG_GLIDER, ENDER_WINGS;
    public static Block WAYSTONE, OAK_CHAIR, SPRUCE_CHAIR, OAK_STOOL, OAK_TABLE, SPRUCE_TABLE, PEDESTAL;

    /** A small block that faces the way you placed it (chairs). */
    public static class Facing extends FarmBlocks.Shaped {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

        public Facing(net.minecraft.world.phys.shapes.VoxelShape shape, BlockBehaviour.Properties props) {
            super(shape, props);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        }
    }

    // ------------------------------------------------------------------ registration

    private static Item item(String id, java.util.function.Function<Item.Properties, Item> f, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AbpsMod.id(id));
        Item i = Registry.register(BuiltInRegistries.ITEM, key, f.apply(props.setId(key)));
        ModContent.ITEMS.add(i);
        return i;
    }

    private static Block block(String id, java.util.function.Function<BlockBehaviour.Properties, Block> f, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, AbpsMod.id(id));
        Block b = Registry.register(BuiltInRegistries.BLOCK, key, f.apply(props.setId(key)));
        item(id, p -> new BlockItem(b, p), new Item.Properties().useBlockDescriptionPrefix());
        return b;
    }

    public static void register() {
        BACKPACK = item("backpack", Item::new, new Item.Properties().stacksTo(1));
        RUBY_BACKPACK = item("ruby_backpack", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        GRAPPLING_HOOK = item("grappling_hook", Item::new, new Item.Properties().durability(160));
        MAGNET_CHARM = item("magnet_charm", Item::new, new Item.Properties().stacksTo(1));
        BUILDERS_WAND = item("builders_wand", Item::new, new Item.Properties().durability(640));
        RUBY_APPLE = item("ruby_apple", Item::new, new Item.Properties().rarity(Rarity.RARE).food(
                new FoodProperties.Builder().nutrition(5).saturationModifier(1.2f).alwaysEdible().build(),
                Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 2),
                        new MobEffectInstance(MobEffects.REGENERATION, 200, 1), new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0)))).build()));
        SLEEPING_BAG = item("sleeping_bag", Item::new, new Item.Properties().stacksTo(1));
        MOB_TROPHY = item("mob_trophy", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
        HANG_GLIDER = item("hang_glider", Item::new, new Item.Properties().stacksTo(1));
        ResourceKey<EquipmentAsset> wings = ResourceKey.create(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("minecraft", "equipment_asset")), AbpsMod.id("ender_wings"));
        ENDER_WINGS = item("ender_wings", Item::new, new Item.Properties().durability(640).rarity(Rarity.EPIC).fireResistant()
                .component(DataComponents.GLIDER, Unit.INSTANCE)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
                        .setAsset(wings).setDamageOnHurt(false).build()));

        WAYSTONE = block("waystone", p -> new FarmBlocks.Shaped(Block.box(1, 0, 1, 15, 16, 15), p),
                BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).noOcclusion().lightLevel(s -> 7));
        OAK_CHAIR = block("oak_chair", p -> new Facing(Block.box(1, 0, 1, 15, 10, 15), p), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
        SPRUCE_CHAIR = block("spruce_chair", p -> new Facing(Block.box(1, 0, 1, 15, 10, 15), p), BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).noOcclusion());
        OAK_STOOL = block("oak_stool", p -> new FarmBlocks.Shaped(Block.box(3, 0, 3, 13, 10, 13), p), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
        OAK_TABLE = block("oak_table", p -> new FarmBlocks.Shaped(Block.box(0, 0, 0, 16, 16, 16), p), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
        SPRUCE_TABLE = block("spruce_table", p -> new FarmBlocks.Shaped(Block.box(0, 0, 0, 16, 16, 16), p), BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).noOcclusion());
        PEDESTAL = block("display_pedestal", p -> new FarmBlocks.Shaped(Block.box(2, 0, 2, 14, 14, 14), p), BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_ANDESITE).noOcclusion());

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) {
                ItemStack s = player.getItemInHand(hand);
                return s.is(BACKPACK) || s.is(RUBY_BACKPACK) || s.is(GRAPPLING_HOOK) || s.is(MAGNET_CHARM) || s.is(SLEEPING_BAG) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return useItem(p, sl, hand);
        });
        UseBlockCallback.EVENT.register(Extras::useBlock);
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (state.is(WAYSTONE)) forgetWaystone(level, pos);
            if (state.is(PEDESTAL) && level instanceof ServerLevel sl) clearPedestal(sl, pos, true);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register(Extras::trophy);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
            if (source.getEntity() instanceof ServerPlayer p && victim != p && !(victim instanceof ServerPlayer) && victim.isAlive() && taken > 0) healthBar(p, victim);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(SEAT_TAG) && entity.getPassengers().isEmpty()) entity.discard();
        });
        ServerTickEvents.END_SERVER_TICK.register(Extras::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
    }

    // ------------------------------------------------------------------ item use

    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private static boolean ready(ServerPlayer p, String what, long ms) {
        String k = p.getUUID() + what;
        long now = System.currentTimeMillis();
        Long until = COOLDOWN.get(UUID.nameUUIDFromBytes(k.getBytes()));
        if (until != null && until > now) return false;
        COOLDOWN.put(UUID.nameUUIDFromBytes(k.getBytes()), now + ms);
        return true;
    }

    private static InteractionResult useItem(ServerPlayer p, ServerLevel level, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (s.is(BACKPACK) || s.is(RUBY_BACKPACK)) {
            openBackpack(p, s, s.is(RUBY_BACKPACK));
            return InteractionResult.SUCCESS;
        }
        if (s.is(GRAPPLING_HOOK)) {
            grapple(p, level, s, hand);
            return InteractionResult.SUCCESS;
        }
        if (s.is(MAGNET_CHARM)) {
            boolean on = !magnetOn(s);
            var t = new net.minecraft.nbt.CompoundTag();
            t.putBoolean("abps_on", on);
            net.minecraft.world.item.component.CustomData.set(DataComponents.CUSTOM_DATA, s, t);
            s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on);
            AbpsMod.service().actionBar(p, on ? "<#40C4FF>Magnet on: <gray>nearby items fly to you." : "<gray>Magnet off.");
            Fx.sound(level, p, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, on ? 1.6f : 0.8f);
            return InteractionResult.SUCCESS;
        }
        if (s.is(SLEEPING_BAG)) {
            sleep(p, level);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ---- backpacks

    private static final class Pack extends SimpleContainer {
        private final ItemStack stack;
        private final ServerPlayer owner;

        Pack(ItemStack stack, int size, ServerPlayer owner) {
            super(size);
            this.stack = stack;
            this.owner = owner;
            ItemContainerContents c = stack.get(DataComponents.CONTAINER);
            if (c != null) c.copyInto(getItems());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            // No backpacks inside backpacks
            NonNullList<ItemStack> items = getItems();
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (s.is(BACKPACK) || s.is(RUBY_BACKPACK)) {
                    items.set(i, ItemStack.EMPTY);
                    if (!owner.getInventory().add(s)) owner.drop(s, false);
                }
            }
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
    }

    private static void openBackpack(ServerPlayer p, ItemStack s, boolean big) {
        Pack pack = new Pack(s, big ? 54 : 27, p);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> big ? ChestMenu.sixRows(id, inv, pack) : ChestMenu.threeRows(id, inv, pack), s.getHoverName()));
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 1.2f);
    }

    // ---- grappling hook

    private static void grapple(ServerPlayer p, ServerLevel level, ItemStack s, InteractionHand hand) {
        if (!ready(p, "grapple", 1500)) return;
        HitResult hit = p.pick(32, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            AbpsMod.service().actionBar(p, "<gray>Nothing to hook onto (32 blocks).");
            return;
        }
        Vec3 target = hit.getLocation();
        Fx.line(level, ParticleTypes.CRIT, p.getEyePosition(), target, 0.5);
        Fx.sound(level, p, SoundEvents.FISHING_BOBBER_THROW, 1f, 0.6f);
        AbpsMod.data().get(p).noFallUntil = System.currentTimeMillis() + 4000;
        s.hurtAndBreak(1, p, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        dev.abps.util.Tasks.repeat(30, 1, step -> {
            if (p.isRemoved()) return;
            Vec3 to = target.subtract(p.position());
            if (to.length() < 1.6) return;
            Vec3 v = to.normalize().scale(Math.min(1.4, 0.5 + to.length() * 0.08));
            p.setDeltaMovement(v.add(0, 0.05, 0));
            p.hurtMarked = true;
            if (step % 3 == 0) Fx.line(level, ParticleTypes.CRIT, p.position().add(0, 1, 0), target, 1.2);
        });
    }

    private static boolean magnetOn(ItemStack s) {
        var d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBooleanOr("abps_on", false);
    }

    // ---- sleeping bag

    private static final Map<UUID, Long> BAGGED = new HashMap<>();

    private static void sleep(ServerPlayer p, ServerLevel level) {
        long t = level.getDayTime() % 24000;
        if (level.dimension() != Level.OVERWORLD || t < 12500 || t > 23400) {
            AbpsMod.service().actionBar(p, "<gray>You can only sleep at night, in the overworld.");
            return;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8), e -> e instanceof Enemy).isEmpty()) {
            AbpsMod.service().actionBar(p, "<red>You can't sleep with monsters nearby.");
            return;
        }
        BAGGED.put(p.getUUID(), System.currentTimeMillis() + 6000);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 9, false, false));
        AbpsMod.service().actionBar(p, "<gray>Zzz... <dark_gray>(you'll wake at dawn if everyone else is asleep too)");
    }

    private static void checkSleepers(MinecraftServer server) {
        ServerLevel world = server.overworld();
        if (world == null || BAGGED.isEmpty()) return;
        long now = System.currentTimeMillis();
        BAGGED.values().removeIf(until -> until < now - 2000);
        List<ServerPlayer> ps = world.players();
        if (ps.isEmpty()) return;
        boolean all = true;
        for (ServerPlayer p : ps) if (!p.isSleeping() && !BAGGED.containsKey(p.getUUID()) && !p.isSpectator()) all = false;
        if (all && BAGGED.values().stream().anyMatch(u -> u <= now)) {
            long day = world.getDayTime() / 24000L;
            world.setDayTime((day + 1) * 24000L);
            world.setWeatherParameters(6000, 0, false, false);
            for (ServerPlayer p : ps) AbpsMod.service().actionBar(p, "<gold>Good morning!");
            BAGGED.clear();
        }
    }

    // ------------------------------------------------------------------ block use

    private static InteractionResult useBlock(net.minecraft.world.entity.player.Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = player.getMainHandItem();
        boolean server = player instanceof ServerPlayer && level instanceof ServerLevel;
        if (held.is(BUILDERS_WAND)) {
            if (server) wand((ServerPlayer) player, (ServerLevel) level, pos, hit.getDirection(), held);
            return InteractionResult.SUCCESS;
        }
        if (state.is(WAYSTONE)) {
            if (server) waystone((ServerPlayer) player, (ServerLevel) level, pos);
            return InteractionResult.SUCCESS;
        }
        if (state.is(PEDESTAL)) {
            if (server) pedestal((ServerPlayer) player, (ServerLevel) level, pos);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() || !held.isEmpty()) return InteractionResult.PASS;
        // Right-click harvest: ripe crops come in and replant themselves
        if (dev.abps.classes.Harvester.ripe(state)) {
            if (server) {
                ServerPlayer p = (ServerPlayer) player;
                if (p.gameMode.destroyBlock(pos) && level.getBlockState(pos).isAir()) {
                    BlockState young = dev.abps.classes.Harvester.replanted(state);
                    if (young.canSurvive(level, pos)) level.setBlock(pos, young, Block.UPDATE_ALL);
                }
            }
            return InteractionResult.SUCCESS;
        }
        // Sitting: chairs, stools, and the bottom halves of stairs and slabs
        boolean seat = state.is(OAK_CHAIR) || state.is(SPRUCE_CHAIR) || state.is(OAK_STOOL)
                || (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM)
                || (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM);
        if (seat && level.getBlockState(pos.above()).isAir()) {
            if (server) sit((ServerPlayer) player, (ServerLevel) level, pos, state);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ---- builder's wand

    private static void wand(ServerPlayer p, ServerLevel level, BlockPos pos, Direction face, ItemStack wand) {
        BlockState state = level.getBlockState(pos);
        Item blockItem = state.getBlock().asItem();
        if (blockItem == Items.AIR || state.hasBlockEntity()) {
            AbpsMod.service().actionBar(p, "<gray>The wand can't copy that block.");
            return;
        }
        int have = p.isCreative() ? 999 : count(p, blockItem);
        int max = Math.min(25, have);
        List<BlockPos> out = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>(List.of(pos));
        Set<BlockPos> seen = new HashSet<>(List.of(pos));
        while (!q.isEmpty() && out.size() < max) {
            BlockPos c = q.poll();
            BlockPos front = c.relative(face);
            if (!level.getBlockState(c).is(state.getBlock()) || !level.getBlockState(front).canBeReplaced()) continue;
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(front)).isEmpty()) continue;
            out.add(front);
            for (Direction d : Direction.values()) {
                if (d.getAxis() == face.getAxis()) continue;
                BlockPos n = c.relative(d);
                if (seen.add(n) && n.distSqr(pos) <= 36) q.add(n);
            }
        }
        if (out.isEmpty()) {
            AbpsMod.service().actionBar(p, have == 0 ? "<red>You don't have any of that block." : "<gray>No room to build there.");
            return;
        }
        for (BlockPos b : out) level.setBlock(b, state, Block.UPDATE_ALL);
        if (!p.isCreative()) take(p, blockItem, out.size());
        wand.hurtAndBreak(1, p, EquipmentSlot.MAINHAND);
        Fx.sound(level, Vec3.atCenterOf(pos), state.getSoundType().getPlaceSound(), 1f, 1f);
        for (BlockPos b : out) Fx.burst(level, ParticleTypes.END_ROD, Vec3.atCenterOf(b), 1, 0.2, 0.01);
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static void take(ServerPlayer p, Item item, int n) {
        for (int i = 0; i < p.getInventory().getContainerSize() && n > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.is(item)) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t);
            n -= t;
        }
    }

    // ---- sitting

    static final String SEAT_TAG = "abps_seat";

    private static void sit(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        if (p.isPassenger()) return;
        double y = state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock ? 0.35 : 0.45;
        var seat = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5);
        seat.addTag(SEAT_TAG);
        level.addFreshEntity(seat);
        p.startRiding(seat, true);
    }

    // ---- display pedestals

    private static final String PEDESTAL_TAG = "abps_pedestal";

    private static List<Display.ItemDisplay> shown(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos.above()).inflate(0.2), d -> d.entityTags().contains(PEDESTAL_TAG));
    }

    private static void pedestal(ServerPlayer p, ServerLevel level, BlockPos pos) {
        List<Display.ItemDisplay> current = shown(level, pos);
        if (!current.isEmpty()) {
            clearPedestal(level, pos, false);
            for (Display.ItemDisplay d : current) {
                ItemStack s = d.getItemStack().copy();
                if (!p.getInventory().add(s)) p.drop(s, false);
            }
            return;
        }
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return;
        var d = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        d.setPos(pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5);
        d.setItemStack(held.copyWithCount(1));
        d.setYRot(p.getYRot() + 180);
        d.addTag(PEDESTAL_TAG);
        level.addFreshEntity(d);
        if (!p.isCreative()) held.shrink(1);
        Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.ITEM_FRAME_ADD_ITEM, 1f, 1f);
    }

    private static void clearPedestal(ServerLevel level, BlockPos pos, boolean drop) {
        for (Display.ItemDisplay d : shown(level, pos)) {
            if (drop) Block.popResource(level, pos, d.getItemStack().copy());
            d.discard();
        }
    }

    // ------------------------------------------------------------------ waystones

    record Waystone(String dim, long pos, String name) {
    }

    private static List<Waystone> waystones = new ArrayList<>();
    private static Map<String, Set<Long>> discovered = new HashMap<>();
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static void waystone(ServerPlayer p, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        Waystone here = null;
        for (Waystone w : waystones) if (w.dim().equals(dim) && w.pos() == pos.asLong()) here = w;
        if (here == null) {
            String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath().replace('_', ' ')).orElse("somewhere");
            here = new Waystone(dim, pos.asLong(), Character.toUpperCase(biome.charAt(0)) + biome.substring(1) + " (" + pos.getX() + ", " + pos.getZ() + ")");
            waystones.add(here);
        }
        Set<Long> mine = discovered.computeIfAbsent(p.getUUID().toString(), u -> new HashSet<>());
        if (mine.add(pos.asLong())) {
            AbpsMod.service().banner(p, "<bold><#B388FF>Waystone found</#B388FF></bold>", "<gray>" + here.name(), 0xB388FF, 50);
            Fx.burst(level, ParticleTypes.PORTAL, Vec3.atCenterOf(pos).add(0, 0.5, 0), 40, 0.4, 0.4);
            save();
            return;
        }
        List<Waystone> known = new ArrayList<>();
        for (Waystone w : waystones) if (mine.contains(w.pos()) && !(w.dim().equals(dim) && w.pos() == pos.asLong())) known.add(w);
        if (known.isEmpty()) {
            AbpsMod.service().actionBar(p, "<gray>Find another waystone to travel between them.");
            return;
        }
        int sel = Math.floorMod(SELECTED.getOrDefault(p.getUUID(), 0), known.size());
        if (p.isShiftKeyDown()) {
            sel = (sel + 1) % known.size();
            SELECTED.put(p.getUUID(), sel);
            AbpsMod.service().actionBar(p, "<#B388FF>→ " + known.get(sel).name() + " <dark_gray>(right-click to travel, sneak-right-click for the next)");
            return;
        }
        Waystone to = known.get(sel);
        ServerLevel dest = null;
        for (ServerLevel l : AbpsMod.server().getAllLevels()) if (l.dimension().identifier().toString().equals(to.dim())) dest = l;
        if (dest == null) return;
        if (!p.isCreative() && p.experienceLevel < 1) {
            AbpsMod.service().actionBar(p, "<red>Travelling costs 1 experience level.");
            return;
        }
        if (!p.isCreative()) p.giveExperienceLevels(-1);
        BlockPos tp = BlockPos.of(to.pos());
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, p.position().add(0, 1, 0), 30, 0.4, 0.2);
        p.teleportTo(dest, tp.getX() + 0.5, tp.getY() + 1, tp.getZ() + 1.5, Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
        Fx.sound(dest, p, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.2f);
        AbpsMod.service().actionBar(p, "<#B388FF>Arrived at " + to.name());
    }

    private static void forgetWaystone(Level level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        waystones.removeIf(w -> w.dim().equals(dim) && w.pos() == pos.asLong());
        save();
    }

    // ------------------------------------------------------------------ mob trophies and health readouts

    private static void trophy(LivingEntity dead, net.minecraft.world.damagesource.DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer p) || dead instanceof ServerPlayer || !(dead.level() instanceof ServerLevel level)) return;
        var type = dead.getType();
        boolean boss = type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN;
        if (!boss && level.random.nextFloat() > 0.012f) return;
        ItemStack t = new ItemStack(MOB_TROPHY);
        String name = type.getDescription().getString();
        t.set(DataComponents.ITEM_NAME, dev.abps.util.Text.mm((boss ? "<gradient:#FFD54F:#FF6D00>" : "<gold>") + name + " Trophy"));
        t.set(DataComponents.LORE, new ItemLore(List.of(dev.abps.util.Text.mm("<gray>Taken by " + p.getName().getString()))));
        Block.popResource(level, dead.blockPosition(), t);
        // Some mobs drop their head too
        Item head = type == EntityTypes.ZOMBIE ? Items.ZOMBIE_HEAD : type == EntityTypes.SKELETON ? Items.SKELETON_SKULL
                : type == EntityTypes.CREEPER ? Items.CREEPER_HEAD : type == EntityTypes.PIGLIN ? Items.PIGLIN_HEAD : null;
        if (head != null) Block.popResource(level, dead.blockPosition(), new ItemStack(head));
    }

    private static void healthBar(ServerPlayer p, LivingEntity e) {
        float hp = e.getHealth(), max = e.getMaxHealth();
        int cells = 20, full = (int) Math.ceil(cells * hp / max);
        String color = hp > max * 0.5 ? "<#69F0AE>" : hp > max * 0.25 ? "<#FFD54F>" : "<#FF5252>";
        AbpsMod.service().actionBar(p, "<white>" + e.getName().getString() + " " + color + "|".repeat(Math.max(0, full)) + "<dark_gray>" + "|".repeat(cells - Math.max(0, full))
                + " <gray>" + dev.abps.util.Text.num(hp) + "/" + dev.abps.util.Text.num(max));
    }

    // ------------------------------------------------------------------ party pings and sorting (commands)

    /** !Ping: marks the block you look at for your party. */
    public static void ping(ServerPlayer p) {
        HitResult hit = p.pick(96, 0, false);
        if (hit.getType() == HitResult.Type.MISS) {
            AbpsMod.service().actionBar(p, "<gray>Look at a block to ping it.");
            return;
        }
        Vec3 at = hit.getLocation();
        ServerLevel level = (ServerLevel) p.level();
        var party = dev.abps.dungeon.Party.of(p);
        List<ServerPlayer> who = party == null ? List.of(p) : party.online();
        dev.abps.util.Tasks.repeat(20, 10, step -> {
            Fx.line(level, Fx.dust(0x40E8F2, 1.4f), at, at.add(0, 12, 0), 0.5);
            Fx.ring(level, Fx.dust(0x40E8F2, 1f), at.add(0, 0.1, 0), 1.2, 16);
        });
        for (ServerPlayer o : who) {
            AbpsMod.service().send(o, "<#40E8F2>📍 " + p.getName().getString() + " pinged " + (int) at.x + ", " + (int) at.y + ", " + (int) at.z
                    + " <gray>(" + (int) o.position().distanceTo(at) + " blocks away)");
            Fx.sound((ServerLevel) o.level(), o, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.6f);
        }
    }

    /** !Sort: tidies the container you look at, or your own inventory (not the hotbar). */
    public static void sort(ServerPlayer p) {
        HitResult hit = p.pick(5, 0, false);
        net.minecraft.world.Container target = null;
        int from = 9, to = 36;
        if (hit instanceof BlockHitResult bh && p.level().getBlockEntity(bh.getBlockPos()) instanceof net.minecraft.world.Container c) {
            target = c;
            from = 0;
            to = c.getContainerSize();
        }
        if (target == null) target = p.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int i = from; i < to; i++) {
            ItemStack s = target.getItem(i);
            if (!s.isEmpty()) items.add(s.copy());
            target.setItem(i, ItemStack.EMPTY);
        }
        // Merge stacks, then order by item name
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack s : items) {
            for (ItemStack m : merged) {
                if (ItemStack.isSameItemSameComponents(m, s) && m.getCount() < m.getMaxStackSize()) {
                    int move = Math.min(s.getCount(), m.getMaxStackSize() - m.getCount());
                    m.grow(move);
                    s.shrink(move);
                }
                if (s.isEmpty()) break;
            }
            if (!s.isEmpty()) merged.add(s);
        }
        merged.sort(Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).thenComparing(s -> -s.getCount()));
        int slot = from;
        for (ItemStack s : merged) target.setItem(slot++, s);
        target.setChanged();
        AbpsMod.service().actionBar(p, "<green>Sorted " + (from == 0 ? "the container" : "your inventory") + ".");
    }

    // ------------------------------------------------------------------ ticking

    private static long ticks;
    private static final Map<UUID, Boolean> SNEAK = new HashMap<>();
    private static final Map<UUID, Long> WING_BOOST = new HashMap<>();

    private static void tick(MinecraftServer server) {
        ticks++;
        if (ticks % 20 == 0) checkSleepers(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) p.level();
            // Magnet charm: pull loose items and experience in
            if (ticks % 2 == 0) {
                boolean magnet = false;
                for (int i = 0; i < p.getInventory().getContainerSize() && !magnet; i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    magnet = s.is(MAGNET_CHARM) && magnetOn(s);
                }
                if (magnet && !p.isShiftKeyDown()) {
                    for (Entity e : level.getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(7), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                        Vec3 to = p.position().add(0, 0.5, 0).subtract(e.position());
                        if (to.lengthSqr() < 1) continue;
                        e.setDeltaMovement(to.normalize().scale(0.45));
                    }
                }
            }
            // Hang glider: held while falling, you glide down slowly
            ItemStack main = p.getMainHandItem();
            if ((main.is(HANG_GLIDER) || p.getOffhandItem().is(HANG_GLIDER)) && !p.onGround() && !p.isInWater() && !p.getAbilities().flying && p.getDeltaMovement().y < 0) {
                Vec3 v = p.getDeltaMovement();
                Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 nv = new Vec3(v.x * 0.9 + look.x * 0.06, Math.max(v.y, -0.12), v.z * 0.9 + look.z * 0.06);
                p.setDeltaMovement(nv);
                p.hurtMarked = true;
                p.fallDistance = 0;
                if (ticks % 6 == 0) Fx.burst(level, ParticleTypes.CLOUD, p.position().add(0, 2.2, 0), 1, 0.3, 0);
            }
            // Ender Wings: tap sneak while gliding for a boost every two seconds
            boolean sneaking = p.isShiftKeyDown();
            boolean was = SNEAK.getOrDefault(p.getUUID(), false);
            SNEAK.put(p.getUUID(), sneaking);
            if (sneaking && !was && p.isFallFlying() && p.getItemBySlot(EquipmentSlot.CHEST).is(ENDER_WINGS)) {
                long now = System.currentTimeMillis();
                if (now >= WING_BOOST.getOrDefault(p.getUUID(), 0L)) {
                    WING_BOOST.put(p.getUUID(), now + 2000);
                    p.setDeltaMovement(p.getDeltaMovement().add(p.getLookAngle().scale(0.9)));
                    p.hurtMarked = true;
                    Fx.burst(level, ParticleTypes.REVERSE_PORTAL, p.position(), 20, 0.4, 0.1);
                    Fx.sound(level, p, SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 1.4f);
                }
            }
        }
        // Empty seats go away
        if (ticks % 20 == 0) {
            for (ServerLevel l : server.getAllLevels()) {
                for (ServerPlayer p : l.players()) {
                    for (Display.TextDisplay seat : l.getEntitiesOfClass(Display.TextDisplay.class, p.getBoundingBox().inflate(16),
                            d -> d.entityTags().contains(SEAT_TAG) && d.getPassengers().isEmpty())) seat.discard();
                }
            }
        }
    }

    // ------------------------------------------------------------------ saving

    private static Path file() {
        return AbpsMod.data().root().resolve("waystones.json");
    }

    private record Saved(List<Waystone> waystones, Map<String, Set<Long>> discovered) {
    }

    private static void load() {
        try {
            if (Files.exists(file())) {
                Saved s = GSON.fromJson(Files.readString(file()), Saved.class);
                if (s != null) {
                    if (s.waystones() != null) waystones = new ArrayList<>(s.waystones());
                    if (s.discovered() != null) discovered = new HashMap<>(s.discovered());
                }
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not read waystones: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), GSON.toJson(new Saved(waystones, discovered)));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not save waystones: {}", e.toString());
        }
    }

    @SuppressWarnings("unused")
    private static final Object KEEP = new Object[]{LinkedHashMap.class, TypeToken.class, Component.class, BlockTags.class};
}
