package dev.abps.content;

import dev.abps.AbpsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every Cooking & Farming item and block: crops, fruit trees, ingredients, meals, feasts, street food, drinks and the
 * kitchen and farm blocks. Textures, models, recipes and loot are made by tools/gen_food.py.
 */
public final class Food {

    private Food() {
    }

    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    /** Feasts share their effects with everyone nearby (see Kitchen). */
    public static final Map<Item, List<MobEffectInstance>> FEASTS = new LinkedHashMap<>();
    /** What each meal or drink does, for the tooltip-free menu page and the Chef's longer buffs. */
    public static final Map<Item, List<MobEffectInstance>> EFFECTS = new LinkedHashMap<>();

    public static final String[] CROPS = {"tomato", "corn", "onion", "cabbage", "chili_pepper", "strawberry"};
    public static final String[] FRUITS = {"orange", "lemon", "peach", "plum"};

    public static Item item(String id) {
        return ITEMS.get(id);
    }

    public static Block block(String id) {
        return BLOCKS.get(id);
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceKey<Item> itemKey(String id) {
        return ResourceKey.create(Registries.ITEM, AbpsMod.id(id));
    }

    private static ResourceKey<Block> blockKey(String id) {
        return ResourceKey.create(Registries.BLOCK, AbpsMod.id(id));
    }

    private static Item register(String id, Function<Item.Properties, Item> factory, Item.Properties props) {
        Item i = Registry.register(BuiltInRegistries.ITEM, itemKey(id), factory.apply(props.setId(itemKey(id))));
        ITEMS.put(id, i);
        ModContent.ITEMS.add(i);
        return i;
    }

    private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props, boolean withItem) {
        Block b = Registry.register(BuiltInRegistries.BLOCK, blockKey(id), factory.apply(props.setId(blockKey(id))));
        BLOCKS.put(id, b);
        if (withItem) register(id, p -> new BlockItem(b, p), new Item.Properties().useBlockDescriptionPrefix());
        return b;
    }

    private static MobEffectInstance fx(Holder<MobEffect> effect, int seconds, int amp) {
        return new MobEffectInstance(effect, seconds * 20, amp);
    }

    private enum Kind {SNACK, BOWL, DRINK, PLAIN}

    private static Item food(String id, int nutrition, float saturation, Kind kind, MobEffectInstance... effects) {
        FoodProperties fp = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
        Consumable.Builder c = kind == Kind.DRINK ? Consumables.defaultDrink() : Consumables.defaultFood();
        if (effects.length > 0) c = c.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effects)));
        Item.Properties props = new Item.Properties().food(fp, c.build());
        if (kind == Kind.BOWL) props = props.stacksTo(16).usingConvertsTo(Items.BOWL);
        if (kind == Kind.DRINK) props = props.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE);
        Item item = register(id, Item::new, props);
        if (effects.length > 0) EFFECTS.put(item, List.of(effects));
        return item;
    }

    private static Item plain(String id) {
        return register(id, Item::new, new Item.Properties());
    }

    // ------------------------------------------------------------------ content

    public static void register() {
        // Crops: the seeds plant the crop, the crop drops produce
        for (String c : CROPS) {
            Block crop = block(c + "_crop", CropBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT), false);
            register(c + "_seeds", p -> new BlockItem(crop, p), new Item.Properties());
        }
        food("tomato", 3, 0.4f, Kind.SNACK);
        food("corn", 3, 0.4f, Kind.SNACK);
        food("onion", 2, 0.3f, Kind.SNACK);
        food("cabbage", 3, 0.4f, Kind.SNACK);
        food("chili_pepper", 2, 0.3f, Kind.SNACK, fx(MobEffects.SPEED, 8, 0));
        food("strawberry", 2, 0.3f, Kind.SNACK);

        // Fruit trees
        for (String f : FRUITS) {
            String id = f;
            Block leaves = block(f + "_leaves", p -> new FarmBlocks.FruitLeaves(() -> item(id), p), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES), true);
            block(f + "_sapling", p -> new FarmBlocks.FruitSapling(() -> leaves, p), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING), true);
        }
        food("orange", 4, 0.3f, Kind.SNACK);
        food("lemon", 2, 0.3f, Kind.SNACK, fx(MobEffects.HASTE, 10, 0));
        food("peach", 4, 0.4f, Kind.SNACK);
        food("plum", 4, 0.3f, Kind.SNACK);

        // Ingredients
        plain("flour");
        plain("rich_compost");
        food("chopped_vegetables", 2, 0.3f, Kind.SNACK);
        food("raw_patty", 2, 0.2f, Kind.SNACK);
        food("cooked_patty", 6, 0.8f, Kind.SNACK);
        food("fish_fillet", 2, 0.2f, Kind.SNACK);
        food("cooked_fillet", 5, 0.6f, Kind.SNACK);
        food("milk_bottle", 2, 0.2f, Kind.DRINK);
        food("goat_milk", 3, 0.4f, Kind.DRINK, fx(MobEffects.REGENERATION, 5, 0));
        food("cheese", 4, 0.5f, Kind.SNACK);
        food("butter", 2, 0.2f, Kind.SNACK);

        // Cooking pot meals
        food("tomato_soup", 7, 0.7f, Kind.BOWL, fx(MobEffects.REGENERATION, 30, 0));
        food("vegetable_stew", 8, 0.8f, Kind.BOWL, fx(MobEffects.RESISTANCE, 120, 0));
        food("corn_chowder", 8, 0.8f, Kind.BOWL, fx(MobEffects.HASTE, 180, 0));
        food("chili_con_carne", 10, 0.9f, Kind.BOWL, fx(MobEffects.STRENGTH, 120, 0), fx(MobEffects.FIRE_RESISTANCE, 120, 0));
        food("fish_stew", 9, 0.8f, Kind.BOWL, fx(MobEffects.WATER_BREATHING, 180, 0), fx(MobEffects.DOLPHINS_GRACE, 60, 0));
        food("stuffed_cabbage", 9, 0.8f, Kind.BOWL, fx(MobEffects.SPEED, 180, 0));

        // Stone oven
        food("pizza", 12, 0.9f, Kind.SNACK, fx(MobEffects.ABSORPTION, 60, 1));
        food("strawberry_pie", 8, 0.6f, Kind.SNACK, fx(MobEffects.SPEED, 120, 0), fx(MobEffects.JUMP_BOOST, 120, 0));
        food("corn_bread", 6, 0.7f, Kind.SNACK, fx(MobEffects.HASTE, 60, 0));
        food("grilled_cheese", 8, 0.8f, Kind.SNACK, fx(MobEffects.ABSORPTION, 120, 0));
        // Feasts are placed on a table: four servings for the party
        List<MobEffectInstance> roast = List.of(fx(MobEffects.REGENERATION, 20, 1), fx(MobEffects.STRENGTH, 180, 0), fx(MobEffects.HEALTH_BOOST, 300, 1));
        List<MobEffectInstance> seafood = List.of(fx(MobEffects.REGENERATION, 20, 1), fx(MobEffects.WATER_BREATHING, 300, 0), fx(MobEffects.LUCK, 300, 0));
        block("roast_feast", p -> new FarmBlocks.Feast(roast, p), BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE).noOcclusion(), true);
        block("seafood_feast", p -> new FarmBlocks.Feast(seafood, p), BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE).noOcclusion(), true);

        // Street food
        food("burger", 10, 0.8f, Kind.SNACK, fx(MobEffects.ABSORPTION, 30, 0));
        food("taco", 6, 0.6f, Kind.SNACK, fx(MobEffects.SPEED, 45, 0));
        food("hot_dog", 7, 0.6f, Kind.SNACK);
        food("fries", 6, 0.5f, Kind.SNACK);
        food("kebab", 8, 0.7f, Kind.SNACK, fx(MobEffects.STRENGTH, 45, 0));
        food("popcorn", 3, 0.3f, Kind.SNACK, fx(MobEffects.NIGHT_VISION, 30, 0));
        food("corn_on_the_cob", 6, 0.6f, Kind.SNACK);

        // Drinks
        food("orange_juice", 4, 0.4f, Kind.DRINK, fx(MobEffects.SPEED, 60, 0));
        food("lemonade", 3, 0.4f, Kind.DRINK, fx(MobEffects.HASTE, 60, 0));
        food("peach_tea", 3, 0.4f, Kind.DRINK, fx(MobEffects.REGENERATION, 10, 0));
        food("strawberry_smoothie", 5, 0.5f, Kind.DRINK, fx(MobEffects.JUMP_BOOST, 90, 0));
        food("hot_cocoa", 5, 0.5f, Kind.DRINK, fx(MobEffects.RESISTANCE, 60, 0));
        food("plum_juice", 4, 0.4f, Kind.DRINK, fx(MobEffects.NIGHT_VISION, 120, 0));
        food("apple_cider", 4, 0.4f, Kind.DRINK, fx(MobEffects.ABSORPTION, 60, 0));
        food("golden_cider", 6, 0.8f, Kind.DRINK, fx(MobEffects.ABSORPTION, 120, 1), fx(MobEffects.REGENERATION, 20, 0));
        food("plum_cordial", 6, 0.8f, Kind.DRINK, fx(MobEffects.NIGHT_VISION, 300, 0), fx(MobEffects.LUCK, 300, 0));

        // Kitchen and farm blocks
        block("ruby_rail", FarmBlocks.RubyRail::new, BlockBehaviour.Properties.ofFullCopy(Blocks.POWERED_RAIL), true);
        block("cooking_pot", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).noOcclusion(), true);
        block("stone_oven", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS), true);
        block("cutting_board", p -> new FarmBlocks.Shaped(Block.box(1, 0, 3, 15, 2, 13), p), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion(), true);
        block("aging_barrel", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL), true);
        block("sprinkler", p -> new FarmBlocks.Sprinkler(3, p), BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion(), true);
        block("quality_sprinkler", p -> new FarmBlocks.Sprinkler(5, p), BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion(), true);
        block("greenhouse_glass", FarmBlocks.GreenhouseGlass::new, BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS), true);
        block("scarecrow", p -> new FarmBlocks.Shaped(Block.box(1, 0, 5, 15, 16, 11), p), BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK).noOcclusion(), true);
    }

    @SuppressWarnings("unused")
    private static final List<Object> KEEP = new ArrayList<>();
}
