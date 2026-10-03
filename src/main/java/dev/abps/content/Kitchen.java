package dev.abps.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The kitchen. Right-click a Cooking Pot or Stone Oven with ingredients to add them, then with an empty hand to cook.
 * The Cutting Board chops what you hold, and the Aging Barrel slowly turns drinks (and milk) into better ones.
 * Also: feasts feed everyone nearby, and eating a varied diet makes you Well Fed.
 */
public final class Kitchen {

    private Kitchen() {
    }

    // ------------------------------------------------------------------ recipes

    record Recipe(List<Item> needs, Item result, int count) {
    }

    private static final List<Recipe> POT = new ArrayList<>();
    private static final List<Recipe> OVEN = new ArrayList<>();
    private static final Map<Item, ItemStack> CHOP = new LinkedHashMap<>();
    /** Aging barrel: input -> (output, output count, in-game days). */
    private static final Map<Item, Object[]> AGING = new LinkedHashMap<>();

    private static Item f(String id) {
        return Food.item(id);
    }

    private static void pot(String result, int count, Item... needs) {
        POT.add(new Recipe(List.of(needs), f(result), count));
    }

    private static void oven(String result, int count, Item... needs) {
        OVEN.add(new Recipe(List.of(needs), f(result), count));
    }

    private static void recipes() {
        Item veg = f("chopped_vegetables");
        pot("tomato_soup", 2, f("tomato"), f("tomato"), f("onion"));
        pot("vegetable_stew", 2, veg, veg, Items.POTATO);
        pot("corn_chowder", 2, f("corn"), f("corn"), f("milk_bottle"));
        pot("chili_con_carne", 2, f("chili_pepper"), f("chili_pepper"), f("raw_patty"), f("tomato"));
        pot("fish_stew", 2, f("fish_fillet"), f("fish_fillet"), f("tomato"), f("onion"));
        pot("stuffed_cabbage", 2, f("cabbage"), f("raw_patty"), f("onion"));
        oven("pizza", 1, f("flour"), f("tomato"), f("cheese"));
        oven("strawberry_pie", 1, f("strawberry"), f("strawberry"), f("flour"), Items.EGG, Items.SUGAR);
        oven("corn_bread", 2, f("corn"), f("flour"), Items.EGG);
        oven("grilled_cheese", 2, Items.BREAD, f("cheese"), f("butter"));
        oven("roast_feast", 1, Items.CHICKEN, Items.BEEF, Items.POTATO, Items.POTATO, Items.CARROT, f("onion"));
        oven("seafood_feast", 1, f("fish_fillet"), f("fish_fillet"), Items.SALMON, Items.KELP, f("lemon"), f("butter"));
        for (Item v : List.of(Items.CARROT, Items.POTATO, Items.BEETROOT, f("tomato"), f("onion"), f("chili_pepper"), f("corn")))
            CHOP.put(v, new ItemStack(veg));
        CHOP.put(f("cabbage"), new ItemStack(veg, 2));
        CHOP.put(Items.BEEF, new ItemStack(f("raw_patty"), 2));
        CHOP.put(Items.PORKCHOP, new ItemStack(f("raw_patty"), 2));
        CHOP.put(Items.COD, new ItemStack(f("fish_fillet"), 2));
        CHOP.put(Items.SALMON, new ItemStack(f("fish_fillet"), 2));
        CHOP.put(Items.WHEAT, new ItemStack(f("flour")));
        CHOP.put(f("milk_bottle"), new ItemStack(f("butter")));
        CHOP.put(Items.MELON, new ItemStack(Items.MELON_SLICE, 9));
        AGING.put(Items.MILK_BUCKET, new Object[]{f("cheese"), 4, 1});
        AGING.put(f("apple_cider"), new Object[]{f("golden_cider"), 1, 2});
        AGING.put(f("plum_juice"), new Object[]{f("plum_cordial"), 1, 2});
    }

    // ------------------------------------------------------------------ state

    /** What sits in a pot or oven, and the cooking in progress. */
    static final class Station {
        final List<Item> contents = new ArrayList<>();
        int cooking; // ticks left
        Recipe recipe;
        int fuel; // ovens: bakes left
    }

    /** One drink aging in a barrel. */
    record Aging(String item, int count, long readyAt) {
    }

    private static final Map<String, Station> STATIONS = new HashMap<>();
    private static Map<String, Aging> barrels = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
    }

    private static BlockPos posOf(String key) {
        return BlockPos.of(Long.parseLong(key.substring(key.indexOf('|') + 1)));
    }

    // ------------------------------------------------------------------ wiring

    public static void register() {
        recipes();
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockState state = level.getBlockState(hit.getBlockPos());
            Block b = state.getBlock();
            boolean station = b == Food.block("cooking_pot") || b == Food.block("stone_oven") || b == Food.block("cutting_board") || b == Food.block("aging_barrel");
            if (!station || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (player.isShiftKeyDown() && !player.getMainHandItem().isEmpty()) return InteractionResult.PASS; // let them place blocks
            if (player instanceof ServerPlayer p && level instanceof ServerLevel sl) use(p, sl, hit.getBlockPos(), b);
            return InteractionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (!(level instanceof ServerLevel sl)) return;
            String k = key(level, pos);
            Station s = STATIONS.remove(k);
            if (s != null) for (Item i : s.contents) Block.popResource(sl, pos, new ItemStack(i));
            Aging a = barrels.remove(k);
            if (a != null) Block.popResource(sl, pos, new ItemStack(itemOf(a.item()), a.count()));
        });
        ServerTickEvents.END_SERVER_TICK.register(Kitchen::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
    }

    private static Item itemOf(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static String idOf(Item i) {
        return BuiltInRegistries.ITEM.getKey(i).toString();
    }

    private static void bar(ServerPlayer p, String msg) {
        AbpsMod.service().actionBar(p, msg);
    }

    private static String name(Item i) {
        return new ItemStack(i).getHoverName().getString();
    }

    private static void use(ServerPlayer p, ServerLevel level, BlockPos pos, Block b) {
        if (b == Food.block("cutting_board")) chop(p, level, pos);
        else if (b == Food.block("aging_barrel")) barrel(p, level, pos);
        else station(p, level, pos, b == Food.block("stone_oven"));
    }

    // ------------------------------------------------------------------ pot and oven

    private static boolean heated(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.CAMPFIRES) || below.is(BlockTags.FIRE) || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.LAVA);
    }

    private static void station(ServerPlayer p, ServerLevel level, BlockPos pos, boolean oven) {
        Station s = STATIONS.computeIfAbsent(key(level, pos), k -> new Station());
        String what = oven ? "oven" : "pot";
        if (s.cooking > 0) {
            bar(p, "<gray>Still cooking... <white>" + (s.cooking / 20 + 1) + "s");
            return;
        }
        ItemStack hand = p.getMainHandItem();
        // Ovens burn coal, charcoal or blaze rods; pots need a fire, campfire, magma or lava underneath
        if (oven && (hand.is(Items.COAL) || hand.is(Items.CHARCOAL) || hand.is(Items.BLAZE_ROD))) {
            int add = hand.is(Items.BLAZE_ROD) ? 12 : 8;
            s.fuel += add;
            if (!p.isCreative()) hand.shrink(1);
            bar(p, "<gold>The oven roars. <gray>Fuel for <white>" + s.fuel + "</white> bakes.");
            Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.FIRECHARGE_USE, 0.6f, 1f);
            return;
        }
        if (!hand.isEmpty()) {
            if (s.contents.size() >= 6) {
                bar(p, "<red>The " + what + " is full (6 ingredients). <gray>Right-click with an empty hand to cook or empty it.");
                return;
            }
            if (!(hand.has(net.minecraft.core.component.DataComponents.FOOD) || isIngredient(hand.getItem()))) {
                bar(p, "<red>That doesn't go in a " + what + ".");
                return;
            }
            s.contents.add(hand.getItem());
            if (!p.isCreative()) {
                ItemStack rest = hand.is(Items.MILK_BUCKET) ? new ItemStack(Items.BUCKET) : ItemStack.EMPTY;
                hand.shrink(1);
                if (!rest.isEmpty() && !p.getInventory().add(rest)) ModContent.drop(p, rest);
            }
            Fx.sound(level, Vec3.atCenterOf(pos), oven ? SoundEvents.WOOD_PLACE : SoundEvents.BUCKET_EMPTY, 0.5f, 1.4f);
            bar(p, "<gray>In the " + what + ": <white>" + list(s.contents));
            return;
        }
        // Empty hand: cook, or tip it out if nothing matches
        if (s.contents.isEmpty()) {
            bar(p, "<gray>Add ingredients by right-clicking with them. " + (oven ? "Ovens need coal or charcoal." : "Pots need heat underneath."));
            return;
        }
        Recipe r = match(oven ? OVEN : POT, s.contents);
        if (r == null) {
            for (Item i : s.contents) {
                ItemStack back = new ItemStack(i);
                if (!p.getInventory().add(back)) ModContent.drop(p, back);
            }
            s.contents.clear();
            bar(p, "<gray>That's not a recipe. You take the ingredients back. <dark_gray>(Type !Recipes to see them all.)");
            return;
        }
        if (!oven && !heated(level, pos)) {
            bar(p, "<red>The pot needs a fire, campfire, magma or lava under it.");
            return;
        }
        if (oven && s.fuel <= 0) {
            bar(p, "<red>The oven is cold. Right-click it with coal or charcoal.");
            return;
        }
        if (oven) s.fuel--;
        boolean chef = isChef(p);
        s.recipe = r;
        s.cooking = (oven ? 160 : 120) / (chef ? 2 : 1);
        if (chef && AbpsMod.data().get(p).buff("kitchen_rush")) s.cooking = 10;
        s.contents.clear();
        Fx.sound(level, Vec3.atCenterOf(pos), oven ? SoundEvents.FURNACE_FIRE_CRACKLE : SoundEvents.BREWING_STAND_BREW, 0.8f, 1f);
        bar(p, "<gold>Cooking " + name(r.result()) + "...");
        pending.put(key(level, pos), p.getUUID());
    }

    private static final Map<String, UUID> pending = new HashMap<>();

    private static boolean isIngredient(Item i) {
        if (i == f("flour") || i == Items.EGG || i == Items.SUGAR || i == Items.KELP || i == Items.MILK_BUCKET) return true;
        for (Recipe r : POT) if (r.needs().contains(i)) return true;
        for (Recipe r : OVEN) if (r.needs().contains(i)) return true;
        return false;
    }

    private static Recipe match(List<Recipe> book, List<Item> contents) {
        for (Recipe r : book) {
            if (r.needs().size() != contents.size()) continue;
            List<Item> left = new ArrayList<>(contents);
            boolean ok = true;
            for (Item need : r.needs()) ok &= left.remove(need);
            if (ok) return r;
        }
        return null;
    }

    private static String list(List<Item> items) {
        Map<Item, Integer> n = new LinkedHashMap<>();
        for (Item i : items) n.merge(i, 1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        for (var e : n.entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(e.getValue() > 1 ? e.getValue() + "x " : "").append(name(e.getKey()));
        }
        return sb.toString();
    }

    private static boolean isChef(ServerPlayer p) {
        var c = AbpsMod.service().active(AbpsMod.data().get(p));
        return c != null && c.id().equals("chef");
    }

    // ------------------------------------------------------------------ cutting board

    private static void chop(ServerPlayer p, ServerLevel level, BlockPos pos) {
        ItemStack hand = p.getMainHandItem();
        ItemStack out = CHOP.get(hand.getItem());
        if (out == null && Fishing.fishOf(hand) != null) out = new ItemStack(f("fish_fillet"), 2);
        if (out == null) {
            bar(p, "<gray>Hold something to chop: vegetables, beef, pork, fish, wheat, milk bottles or melons.");
            return;
        }
        ItemStack result = out.copy();
        if (isChef(p) && level.getRandom().nextInt(3) == 0) result.grow(1);
        if (!p.isCreative()) hand.shrink(1);
        if (!p.getInventory().add(result) && !result.isEmpty()) ModContent.drop(p, result);
        Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.WOOD_HIT, 0.8f, 1.6f);
        Fx.burst(level, ParticleTypes.CRIT, Vec3.atCenterOf(pos).add(0, 0.2, 0), 4, 0.2, 0.05);
    }

    // ------------------------------------------------------------------ aging barrel

    private static void barrel(ServerPlayer p, ServerLevel level, BlockPos pos) {
        String k = key(level, pos);
        Aging a = barrels.get(k);
        long now = level.getGameTime();
        if (a != null) {
            Object[] rule = AGING.get(itemOf(a.item()));
            if (rule == null) {
                barrels.remove(k);
                return;
            }
            if (now >= a.readyAt()) {
                barrels.remove(k);
                ItemStack done = new ItemStack((Item) rule[0], (int) rule[1] * a.count());
                if (!p.getInventory().add(done)) ModContent.drop(p, done);
                if (itemOf(a.item()) == Items.MILK_BUCKET) {
                    ItemStack buckets = new ItemStack(Items.BUCKET, a.count());
                    if (!p.getInventory().add(buckets)) ModContent.drop(p, buckets);
                }
                Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.BARREL_OPEN, 0.8f, 1f);
                bar(p, "<gold>Ready! <gray>You take out <white>" + name((Item) rule[0]));
            } else {
                long left = a.readyAt() - now;
                bar(p, "<gray>" + name(itemOf(a.item())) + " is aging. About <white>" + (left / 1200 + 1) + "</white> minutes left.");
            }
            return;
        }
        ItemStack hand = p.getMainHandItem();
        Object[] rule = AGING.get(hand.getItem());
        if (rule == null) {
            bar(p, "<gray>Age Apple Cider, Plum Juice or Milk Buckets in here.");
            return;
        }
        int n = Math.min(4, hand.getCount());
        int days = (int) rule[2];
        long time = 24000L * days / (isChef(p) ? 2 : 1);
        barrels.put(k, new Aging(idOf(hand.getItem()), n, now + time));
        if (!p.isCreative()) hand.shrink(n);
        Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.BARREL_CLOSE, 0.8f, 1f);
        bar(p, "<gray>" + n + "x " + name((Item) rule[0]) + " will be ready in " + (time / 1200) + " minutes.");
        save();
    }

    // ------------------------------------------------------------------ ticking: cooking, feasts and diet

    private static long ticks;

    private static void tick(MinecraftServer server) {
        ticks++;
        for (Map.Entry<String, Station> e : new ArrayList<>(STATIONS.entrySet())) {
            Station s = e.getValue();
            if (s.cooking <= 0) continue;
            ServerLevel level = levelOf(server, e.getKey());
            if (level == null) continue;
            BlockPos pos = posOf(e.getKey());
            Vec3 top = Vec3.atCenterOf(pos).add(0, 0.6, 0);
            if (ticks % 5 == 0) Fx.burst(level, s.fuel >= 0 && level.getBlockState(pos).getBlock() == Food.block("stone_oven") ? ParticleTypes.SMOKE : ParticleTypes.BUBBLE_POP, top, 3, 0.2, 0.02);
            if (--s.cooking == 0) {
                ItemStack out = new ItemStack(s.recipe.result(), s.recipe.count());
                UUID cook = pending.remove(e.getKey());
                ServerPlayer p = cook == null ? null : server.getPlayerList().getPlayer(cook);
                if (p != null && isChef(p) && level.getRandom().nextInt(3) == 0) out.grow(1);
                ItemEntity drop = new ItemEntity(level, top.x, top.y + 0.3, top.z, out);
                drop.setDeltaMovement(0, 0.2, 0);
                level.addFreshEntity(drop);
                Fx.sound(level, top, SoundEvents.PLAYER_LEVELUP, 0.4f, 1.8f);
                Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, top, 8, 0.3, 0.05);
                s.recipe = null;
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) eating(p);
        if (ticks % 6000 == 0) save();
    }

    private static ServerLevel levelOf(MinecraftServer server, String key) {
        String dim = key.substring(0, key.indexOf('|'));
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().identifier().toString().equals(dim)) return l;
        return null;
    }

    /** What each player is in the middle of eating, and the last foods they finished. */
    private static final Map<UUID, Item> EATING = new HashMap<>();
    private static final Map<UUID, Integer> FOOD_BEFORE = new HashMap<>();
    private static final Map<UUID, Deque<Item>> DIET = new HashMap<>();

    /** Spots a finished meal: the player stopped using a food item and got fuller. */
    private static void eating(ServerPlayer p) {
        UUID id = p.getUUID();
        if (p.isUsingItem() && p.getUseItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            if (!EATING.containsKey(id)) {
                EATING.put(id, p.getUseItem().getItem());
                FOOD_BEFORE.put(id, p.getFoodData().getFoodLevel() * 100 + (int) p.getFoodData().getSaturationLevel());
            }
            return;
        }
        Item ate = EATING.remove(id);
        Integer before = FOOD_BEFORE.remove(id);
        if (ate == null || before == null) return;
        int after = p.getFoodData().getFoodLevel() * 100 + (int) p.getFoodData().getSaturationLevel();
        if (after <= before && p.getFoodData().getFoodLevel() < 20) return; // stopped early
        ate(p, ate);
    }

    private static void ate(ServerPlayer p, Item item) {
        ServerLevel level = (ServerLevel) p.level();
        // Feasts feed the whole table
        List<MobEffectInstance> feast = Food.FEASTS.get(item);
        if (feast != null) {
            for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(10))) {
                for (MobEffectInstance e : feast) o.addEffect(new MobEffectInstance(e));
                o.getFoodData().setFoodLevel(Math.min(20, o.getFoodData().getFoodLevel() + 8));
                if (o != p) AbpsMod.service().actionBar(o, "<gold>" + p.getName().getString() + " shared a " + name(item) + " with you!");
            }
            Fx.burst(level, ParticleTypes.HEART, p.position().add(0, 1.5, 0), 10, 1.5, 0.05);
        }
        // Chefs make every meal's buff last half as long again
        if (isChef(p) && Food.EFFECTS.containsKey(item)) {
            for (MobEffectInstance e : Food.EFFECTS.get(item)) {
                p.addEffect(new MobEffectInstance(e.getEffect(), (int) (e.getDuration() * 1.5), e.getAmplifier()));
            }
        }
        // A balanced diet: six different foods in your last ten meals makes you Well Fed
        Deque<Item> diet = DIET.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
        diet.addLast(item);
        while (diet.size() > 10) diet.removeFirst();
        Set<Item> distinct = new HashSet<>(diet);
        boolean wellFed = distinct.size() >= 6;
        boolean was = hasWellFed(p);
        Mods.toggle(p, wellFed, Attributes.MAX_HEALTH, "well_fed", 4, Mods.ADD);
        if (wellFed && !was) AbpsMod.service().actionBar(p, "<#69F0AE>Well Fed! <gray>A varied diet gives you +2 hearts.");
        else if (diet.size() >= 6 && distinct.size() <= 2 && level.getRandom().nextInt(3) == 0)
            AbpsMod.service().actionBar(p, "<gray>You're getting tired of the same food. Mix it up to become <#69F0AE>Well Fed</#69F0AE>.");
    }

    private static boolean hasWellFed(ServerPlayer p) {
        var inst = p.getAttribute(Attributes.MAX_HEALTH);
        return inst != null && inst.hasModifier(AbpsMod.id("well_fed"));
    }

    /** How many different foods a player ate lately, for the menu. */
    public static int variety(ServerPlayer p) {
        Deque<Item> d = DIET.get(p.getUUID());
        return d == null ? 0 : new HashSet<>(d).size();
    }

    /** All recipes as menu lines. */
    public static List<String> recipeLines() {
        List<String> out = new ArrayList<>();
        for (Recipe r : POT) out.add("<gold>Pot</gold> <white>" + name(r.result()) + "</white><gray>: " + list(r.needs()));
        for (Recipe r : OVEN) out.add("<#FF8A65>Oven</#FF8A65> <white>" + name(r.result()) + "</white><gray>: " + list(r.needs()));
        for (var e : CHOP.entrySet()) out.add("<#A1887F>Board</#A1887F> <white>" + name(e.getKey()) + "</white><gray> → " + e.getValue().getCount() + "x " + name(e.getValue().getItem()));
        for (var e : AGING.entrySet())
            out.add("<#8D6E63>Barrel</#8D6E63> <white>" + name(e.getKey()) + "</white><gray> → " + name((Item) e.getValue()[0]) + " (" + e.getValue()[2] + " day" + ((int) e.getValue()[2] == 1 ? "" : "s") + ")");
        return out;
    }

    // ------------------------------------------------------------------ saving barrels

    private static Path file() {
        return AbpsMod.data().root().resolve("kitchen.json");
    }

    private static void load() {
        try {
            if (Files.exists(file())) {
                Map<String, Aging> m = GSON.fromJson(Files.readString(file()), new TypeToken<HashMap<String, Aging>>() {
                }.getType());
                if (m != null) barrels = m;
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not read aging barrels: {}", e.toString());
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(barrels));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not save aging barrels: {}", e.toString());
        }
    }

    @SuppressWarnings("unused")
    private static boolean unused(Player p) {
        return p == null;
    }
}
