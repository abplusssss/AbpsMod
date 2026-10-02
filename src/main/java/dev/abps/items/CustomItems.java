package dev.abps.items;

import dev.abps.AbpsMod;
import dev.abps.classes.Hit;
import dev.abps.util.Fx;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import dev.abps.util.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Custom weapons, tools and gear found in dungeons. Each is a normal item with a name, a description, a few
 * enchantments and a hidden tag that tells the mod which special power it has.
 */
public final class CustomItems {

    public enum Tier {
        RARE("<#4FC3F7>", Rarity.RARE), EPIC("<#B388FF>", Rarity.EPIC), LEGENDARY("<gradient:#FFD54F:#FF6D00>", Rarity.EPIC);

        public final String color;
        public final Rarity rarity;

        Tier(String color, Rarity rarity) {
            this.color = color;
            this.rarity = rarity;
        }

        public String label() {
            return switch (this) {
                case RARE -> "<#4FC3F7>Rare";
                case EPIC -> "<#B388FF>Epic";
                case LEGENDARY -> "<gold>Legendary";
            };
        }
    }

    /** One custom item: what it is made from, what it's called, what it does. */
    public record Def(String id, Item base, String name, Tier tier, List<String> lore, Map<ResourceKey<Enchantment>, Integer> enchants,
                      List<Object[]> attributes) {
    }

    public static final Map<String, Def> ALL = new LinkedHashMap<>();
    private static final String TAG = "abps_item";

    private static Map<ResourceKey<Enchantment>, Integer> ench(Object... kv) {
        Map<ResourceKey<Enchantment>, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked") ResourceKey<Enchantment> k = (ResourceKey<Enchantment>) kv[i];
            m.put(k, (Integer) kv[i + 1]);
        }
        return m;
    }

    private static void add(String id, Item base, String name, Tier tier, List<String> lore, Map<ResourceKey<Enchantment>, Integer> enchants, Object[]... attrs) {
        ALL.put(id, new Def(id, base, name, tier, lore, enchants, List.of(attrs)));
    }

    static {
        add("bloodfang", Items.DIAMOND_SWORD, "Bloodfang", Tier.RARE, List.of("Heals you for 15% of the damage it deals."),
                ench(Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3));
        add("frostbite", Items.DIAMOND_SWORD, "Frostbite", Tier.RARE, List.of("Hits slow the target.", "20% chance to freeze it in place for 1s."),
                ench(Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3));
        add("stormcaller", Items.DIAMOND_SWORD, "Stormcaller", Tier.EPIC, List.of("20% chance for lightning to arc to", "3 nearby enemies for 4 damage."),
                ench(Enchantments.SHARPNESS, 4, Enchantments.UNBREAKING, 3));
        add("sunblade", Items.GOLDEN_SWORD, "Sunblade", Tier.EPIC, List.of("Deals 50% more damage to undead.", "Sets what it hits on fire."),
                ench(Enchantments.SHARPNESS, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
        add("voidrender", Items.NETHERITE_SWORD, "Voidrender", Tier.LEGENDARY, List.of("Every 4th hit tears space: 6 extra damage", "and pulls the target to you."),
                ench(Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
        add("reaper", Items.NETHERITE_HOE, "Reaper's Scythe", Tier.LEGENDARY, List.of("A hoe that cuts like a greatsword.", "Kills heal you 2 hearts and give a burst of speed."),
                ench(Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1), new Object[]{Attributes.ATTACK_DAMAGE, 8.0, "reaper"},
                new Object[]{Attributes.ATTACK_SPEED, -0.6, "reaper_speed"});
        add("earthsplitter", Items.DIAMOND_AXE, "Earthsplitter", Tier.EPIC, List.of("Critical hits send a shockwave through", "the ground: 4 damage to enemies around."),
                ench(Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3));
        add("timberfall", Items.DIAMOND_AXE, "Timberfall", Tier.RARE, List.of("Chops down a whole tree at once."),
                ench(Enchantments.EFFICIENCY, 4, Enchantments.UNBREAKING, 3));
        add("quarry_pick", Items.DIAMOND_PICKAXE, "Quarry Pick", Tier.RARE, List.of("Mines a 3x3 area. Sneak to mine one block."),
                ench(Enchantments.EFFICIENCY, 4, Enchantments.UNBREAKING, 3));
        add("molten_pick", Items.DIAMOND_PICKAXE, "Molten Pick", Tier.EPIC, List.of("Ores come out already smelted."),
                ench(Enchantments.EFFICIENCY, 4, Enchantments.UNBREAKING, 3, Enchantments.FORTUNE, 2));
        add("veinripper", Items.NETHERITE_PICKAXE, "Veinripper", Tier.LEGENDARY, List.of("Mines a whole ore vein at once (up to 24 blocks)."),
                ench(Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3, Enchantments.FORTUNE, 3, Enchantments.MENDING, 1));
        add("earthmover", Items.DIAMOND_SHOVEL, "Earthmover", Tier.RARE, List.of("Digs a 3x3 area. Sneak to dig one block."),
                ench(Enchantments.EFFICIENCY, 4, Enchantments.UNBREAKING, 3));
        add("bulwark", Items.DIAMOND_CHESTPLATE, "Bulwark Plate", Tier.EPIC, List.of("4 extra hearts while worn."),
                ench(Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3), new Object[]{Attributes.MAX_HEALTH, 8.0, "bulwark"});
        add("windrunners", Items.DIAMOND_BOOTS, "Windrunners", Tier.EPIC, List.of("Run 15% faster.", "Fall a lot further before it hurts."),
                ench(Enchantments.PROTECTION, 3, Enchantments.FEATHER_FALLING, 4, Enchantments.UNBREAKING, 3),
                new Object[]{Attributes.MOVEMENT_SPEED, 0.15, "windrunners", true}, new Object[]{Attributes.SAFE_FALL_DISTANCE, 6.0, "windrunners_fall"});
        add("phoenix_feather", Items.FEATHER, "Phoenix Feather", Tier.LEGENDARY, List.of("Keep it in your inventory: once every 10 minutes", "it saves you from a killing blow."),
                ench());

        // Weapons with a right-click power (the hotbar shows when it's ready again)
        add("thunder_hammer", Items.NETHERITE_AXE, "Thunderlord Hammer", Tier.LEGENDARY, List.of("A slow, crushing warhammer.",
                "Right-click: Thunder Slam. Enemies within 5 blocks", "take 9 damage and are thrown up. (8s)"),
                ench(Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3), new Object[]{Attributes.ATTACK_DAMAGE, 3.0, "thunder_hammer"},
                new Object[]{Attributes.ATTACK_SPEED, -0.2, "thunder_hammer_speed"});
        add("ember_staff", Items.BLAZE_ROD, "Ember Staff", Tier.EPIC, List.of("Right-click: hurl a fire bolt up to 24 blocks.", "7 damage and sets the target on fire. (1.5s)"),
                ench());
        add("frost_staff", Items.BREEZE_ROD, "Frost Staff", Tier.EPIC, List.of("Right-click: an ice lance up to 24 blocks.", "6 damage, slows hard, sometimes freezes. (2s)"),
                ench());
        add("soul_staff", Items.STICK, "Soulreaver Staff", Tier.LEGENDARY, List.of("Right-click: a beam that drains 5 health", "from your target and heals you for 3. (3s)"),
                ench());
        add("shadow_daggers", Items.IRON_SWORD, "Shadowfang Daggers", Tier.EPIC, List.of("Light and very fast.", "Right-click: dash 6 blocks through enemies,", "6 damage to each, and vanish for a moment. (6s)"),
                ench(Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3), new Object[]{Attributes.ATTACK_SPEED, 1.2, "shadow_daggers_speed"},
                new Object[]{Attributes.ATTACK_DAMAGE, -1.0, "shadow_daggers"});
        add("dragonbone_greatsword", Items.NETHERITE_SWORD, "Dragonbone Greatsword", Tier.LEGENDARY, List.of("Huge and slow. Hits like a falling tower.",
                "Right-click: Whirlwind. 8 damage to everything", "within 4 blocks and knocks it away. (10s)"),
                ench(Enchantments.SHARPNESS, 4, Enchantments.UNBREAKING, 3), new Object[]{Attributes.ATTACK_DAMAGE, 5.0, "dragonbone"},
                new Object[]{Attributes.ATTACK_SPEED, -1.0, "dragonbone_speed"});
        add("tide_spear", Items.DIAMOND_SWORD, "Tidebreaker Spear", Tier.EPIC, List.of("Reaches 1.5 blocks further.", "Right-click: lunge forward and impale the first", "enemy for 9 damage. (6s)"),
                ench(Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 3), new Object[]{Attributes.ENTITY_INTERACTION_RANGE, 1.5, "tide_spear_reach"});
        add("echo_chakram", Items.IRON_SWORD, "Echo Chakram", Tier.EPIC, List.of("Right-click: throw it 12 blocks. It comes back,", "hitting everything on the way out and back for 6. (4s)"),
                ench(Enchantments.SHARPNESS, 2, Enchantments.UNBREAKING, 3));

        // Tools
        add("prospector_pick", Items.DIAMOND_PICKAXE, "Prospector's Pick", Tier.RARE, List.of("Right-click: ores within 10 blocks glow", "through walls for 10s. Only you see it. (30s)"),
                ench(Enchantments.EFFICIENCY, 4, Enchantments.FORTUNE, 2, Enchantments.UNBREAKING, 3));
        add("titan_drill", Items.NETHERITE_PICKAXE, "Titan Drill", Tier.LEGENDARY, List.of("Mines a 3x3 area two blocks deep.", "Sneak to mine one block."),
                ench(Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
        add("harvest_scythe", Items.DIAMOND_HOE, "Harvest Scythe", Tier.RARE, List.of("Right-click: harvest every ripe crop in a", "9x9 area and replant it. (2s)"),
                ench(Enchantments.UNBREAKING, 3, Enchantments.FORTUNE, 2));
        add("gravedigger", Items.DIAMOND_SHOVEL, "Gravedigger", Tier.EPIC, List.of("Digs a 3x3 area. Sneak to dig one block.", "Everything you dig goes straight into your bag."),
                ench(Enchantments.EFFICIENCY, 4, Enchantments.UNBREAKING, 3));
    }

    // ------------------------------------------------------------------ creative menu

    /** A tab in the creative inventory with every AbpsMod item in it. */
    public static void registerTab() {
        net.minecraft.world.item.CreativeModeTab tab = net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                .title(Component.literal("AbpsMod"))
                .icon(() -> {
                    ItemStack icon = new ItemStack(Items.NETHERITE_SWORD);
                    icon.set(DataComponents.ITEM_MODEL, AbpsMod.id("dragonbone_greatsword"));
                    return icon;
                })
                .displayItems((params, output) -> {
                    for (String id : ALL.keySet()) {
                        ItemStack s = create(id, params.holders());
                        if (!s.isEmpty()) output.accept(s);
                    }
                })
                .build();
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB, AbpsMod.id("items"), tab);
    }

    // ------------------------------------------------------------------ making items

    public static ItemStack create(String id, ServerLevel level) {
        return create(id, level.registryAccess());
    }

    /** Makes the item with nothing but registries, so the creative menu can build it on the client too. */
    public static ItemStack create(String id, net.minecraft.core.HolderLookup.Provider registries) {
        Def def = ALL.get(id);
        if (def == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(def.base);
        s.set(DataComponents.CUSTOM_NAME, Text.mm("<!italic><bold>" + def.tier.color + def.name).copy());
        List<Component> lore = new ArrayList<>();
        lore.add(Text.mm("<!italic>" + def.tier.label() + " <dark_gray>dungeon item"));
        for (String line : def.lore) lore.add(Text.mm("<!italic><gray>" + line));
        s.set(DataComponents.LORE, new ItemLore(lore));
        s.set(DataComponents.RARITY, def.tier.rarity);
        // Its own texture (the client mod ships the model; see tools/gen_item_textures.py)
        s.set(DataComponents.ITEM_MODEL, AbpsMod.id(id));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, id);
        CustomData.set(DataComponents.CUSTOM_DATA, s, tag);
        var registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (Map.Entry<ResourceKey<Enchantment>, Integer> e : def.enchants.entrySet()) s.enchant(registry.getOrThrow(e.getKey()), e.getValue());
        if (!def.attributes.isEmpty()) {
            ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            for (Object[] a : def.attributes) {
                @SuppressWarnings("unchecked") Holder<Attribute> attr = (Holder<Attribute>) a[0];
                boolean mult = a.length > 3 && Boolean.TRUE.equals(a[3]);
                EquipmentSlotGroup slot = def.base == Items.DIAMOND_CHESTPLATE ? EquipmentSlotGroup.CHEST : def.base == Items.DIAMOND_BOOTS ? EquipmentSlotGroup.FEET
                        : EquipmentSlotGroup.MAINHAND;
                mods = mods.withModifierAdded(attr, new AttributeModifier(AbpsMod.id((String) a[2]), (Double) a[1],
                        mult ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE), slot);
            }
            s.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
        }
        return s;
    }

    /** Which custom item this is, or null for a normal item. */
    public static String idOf(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr(TAG, "");
        return id.isEmpty() ? null : id;
    }

    private static boolean is(ItemStack s, String id) {
        return id.equals(idOf(s));
    }

    // ------------------------------------------------------------------ powers

    private static final Map<UUID, Integer> VOID_HITS = new HashMap<>();
    private static final Map<UUID, Long> PHOENIX = new HashMap<>();
    private static boolean breaking;

    /** Damage multiplier for a hit made with this item. */
    public static double outgoing(ServerPlayer p, LivingEntity victim, Hit hit) {
        if (!hit.melee()) return 1;
        if (is(hit.weapon(), "sunblade") && Targets.UNDEAD.contains(victim.getType())) return 1.5;
        return 1;
    }

    public static void afterHit(ServerPlayer p, LivingEntity victim, float dealt, Hit hit) {
        if (!hit.melee()) return;
        String id = idOf(hit.weapon());
        if (id == null) return;
        ServerLevel level = (ServerLevel) p.level();
        switch (id) {
            case "bloodfang" -> p.heal(dealt * 0.15f);
            case "frostbite" -> {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                if (p.getRandom().nextFloat() < 0.2f) Targets.root(victim, 20);
            }
            case "stormcaller" -> {
                if (p.getRandom().nextFloat() >= 0.2f) return;
                int arcs = 0;
                for (LivingEntity e : Targets.enemiesNear(p, victim.position(), 6)) {
                    if (e == victim || arcs >= 3) continue;
                    arcs++;
                    Targets.damage(e, 4, p);
                    dev.abps.util.Fancy.lightning(level, victim.position().add(0, 1, 0), e.position().add(0, 1, 0), 0x80D8FF, 0xFFFFFF);
                }
                if (arcs > 0) Fx.sound(level, victim, SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 1.8f);
            }
            case "sunblade" -> victim.igniteForSeconds(4);
            case "voidrender" -> {
                int n = VOID_HITS.merge(p.getUUID(), 1, Integer::sum);
                if (n % 4 != 0) return;
                Targets.damage(victim, 6, p);
                Vec3 pull = p.position().subtract(victim.position());
                if (pull.lengthSqr() > 1) Targets.velocity(victim, pull.normalize().scale(0.7).add(0, 0.2, 0));
                Fx.sound(level, victim, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.6f);
            }
            case "earthsplitter" -> {
                boolean crit = p.fallDistance > 0 && !p.onGround() && !p.isInWater();
                if (!crit) return;
                for (LivingEntity e : Targets.enemiesNear(p, victim.position(), 3.5)) {
                    if (e == victim) continue;
                    Targets.damage(e, 4, p);
                    Targets.velocity(e, new Vec3(0, 0.5, 0));
                }
                dev.abps.util.Vfx.groundRing(level, victim.position(), 0.5, 3.5, 16, dev.abps.util.Vfx.tint(0x8D6E63), 0.14f, 10, 0xFFAB40);
                Fx.sound(level, victim, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.4f);
            }
            default -> {
            }
        }
    }

    public static void onKill(ServerPlayer p, LivingEntity victim) {
        if (!is(p.getMainHandItem(), "reaper")) return;
        p.heal(4);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1));
    }

    /** Phoenix Feather: returns false (and saves the player) if it can. */
    public static boolean allowDeath(ServerPlayer p) {
        boolean has = false;
        for (int i = 0; i < p.getInventory().getContainerSize() && !has; i++) has = is(p.getInventory().getItem(i), "phoenix_feather");
        if (!has) return true;
        long now = System.currentTimeMillis();
        Long last = PHOENIX.get(p.getUUID());
        if (last != null && now - last < 600_000) return true;
        PHOENIX.put(p.getUUID(), now);
        p.setHealth(p.getMaxHealth() * 0.5f);
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        ServerLevel level = (ServerLevel) p.level();
        Fx.sound(level, p, SoundEvents.TOTEM_USE, 1f, 1.2f);
        dev.abps.util.Vfx.pillar(level, p.position(), 0.8, 5, dev.abps.util.Vfx.tint(0xFF6D00), 3, 8, 8, 0xFFD54F);
        AbpsMod.service().actionBar(p, "<gradient:#FFD54F:#FF6D00><bold>The Phoenix Feather saved you!");
        return false;
    }

    /** 3x3 mining, whole veins and whole trees, after the first block breaks. */
    public static void afterBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        if (breaking) return;
        ItemStack tool = p.getMainHandItem();
        String id = idOf(tool);
        if (id == null) return;
        List<BlockPos> more = new ArrayList<>();
        switch (id) {
            case "quarry_pick", "earthmover", "gravedigger", "titan_drill" -> {
                if (p.isShiftKeyDown()) return;
                // The plane facing the player: look mostly up or down mines flat, otherwise a wall
                Vec3 look = p.getLookAngle();
                boolean vertical = Math.abs(look.y) > 0.75;
                boolean alongX = Math.abs(look.x) > Math.abs(look.z);
                for (int a = -1; a <= 1; a++)
                    for (int b = -1; b <= 1; b++) {
                        if (a == 0 && b == 0) continue;
                        BlockPos q = vertical ? pos.offset(a, 0, b) : alongX ? pos.offset(0, a, b) : pos.offset(a, b, 0);
                        BlockState s = level.getBlockState(q);
                        if (!s.isAir() && tool.isCorrectToolForDrops(s) && s.getDestroySpeed(level, q) >= 0) more.add(q);
                    }
                // Titan Drill: one more layer behind, so it digs 3x3 two deep
                if (id.equals("titan_drill")) {
                    int dx = vertical ? 0 : alongX ? (look.x > 0 ? 1 : -1) : 0, dy = vertical ? (look.y > 0 ? 1 : -1) : 0, dz = vertical || alongX ? 0 : (look.z > 0 ? 1 : -1);
                    for (int a = -1; a <= 1; a++)
                        for (int b = -1; b <= 1; b++) {
                            BlockPos q0 = vertical ? pos.offset(a, 0, b) : alongX ? pos.offset(0, a, b) : pos.offset(a, b, 0);
                            BlockPos q = q0.offset(dx, dy, dz);
                            BlockState s = level.getBlockState(q);
                            if (!s.isAir() && tool.isCorrectToolForDrops(s) && s.getDestroySpeed(level, q) >= 0) more.add(q);
                        }
                }
            }
            case "veinripper" -> {
                if (!isOre(state)) return;
                more.addAll(flood(level, pos, s -> s.getBlock() == state.getBlock() || sameOre(s, state), 24));
            }
            case "timberfall" -> {
                if (!state.is(BlockTags.LOGS)) return;
                more.addAll(flood(level, pos, s -> s.is(BlockTags.LOGS), 64));
            }
            default -> {
                return;
            }
        }
        breaking = true;
        try {
            for (BlockPos q : more) {
                if (tool.isEmpty() || tool.getDamageValue() >= tool.getMaxDamage() - 2) break; // don't break the tool
                p.gameMode.destroyBlock(q);
            }
        } finally {
            breaking = false;
        }
    }

    private static boolean isOre(BlockState s) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return id.endsWith("_ore") || s.is(Blocks.ANCIENT_DEBRIS);
    }

    private static boolean sameOre(BlockState a, BlockState b) {
        // Deepslate and normal versions of the same ore count as one vein
        String x = a.getBlock().getDescriptionId().replace("deepslate_", ""), y = b.getBlock().getDescriptionId().replace("deepslate_", "");
        return x.equals(y);
    }

    private static List<BlockPos> flood(ServerLevel level, BlockPos start, java.util.function.Predicate<BlockState> match, int max) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && out.size() < max) {
            BlockPos c = q.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.offset(dx, dy, dz);
                        if (!seen.add(n) || n.distSqr(start) > 400) continue;
                        if (match.test(level.getBlockState(n))) {
                            out.add(n);
                            q.add(n);
                            if (out.size() >= max) return out;
                        }
                    }
        }
        return out;
    }

    /** Molten Pick: swaps ore drops for what they smelt into. */
    public static void modifyDrops(ServerPlayer p, BlockState state, List<ItemStack> drops) {
        if (is(p.getMainHandItem(), "gravedigger")) {
            // Straight into the inventory; anything that doesn't fit still drops
            List<ItemStack> left = new ArrayList<>();
            for (ItemStack d : drops) {
                ItemStack copy = d.copy();
                if (!p.getInventory().add(copy) && !copy.isEmpty()) left.add(copy);
            }
            drops.clear();
            drops.addAll(left);
            return;
        }
        if (!is(p.getMainHandItem(), "molten_pick")) return;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack d = drops.get(i);
            Item smelted = d.is(Items.RAW_IRON) ? Items.IRON_INGOT : d.is(Items.RAW_GOLD) ? Items.GOLD_INGOT : d.is(Items.RAW_COPPER) ? Items.COPPER_INGOT
                    : d.is(Items.ANCIENT_DEBRIS) ? Items.NETHERITE_SCRAP : d.is(Items.COBBLESTONE) ? Items.STONE : d.is(Items.SAND) ? Items.GLASS : null;
            if (smelted != null) drops.set(i, new ItemStack(smelted, d.getCount()));
        }
    }

    // ------------------------------------------------------------------ right-click powers

    /** Dungeon cue numbers for the weapon effects (drawn by SigDungeon on the client). */
    public static final int FX_THUNDER = 30, FX_FIRE = 31, FX_FROST = 32, FX_SOUL = 33, FX_DASH = 34, FX_WHIRL = 35, FX_LUNGE = 36,
            FX_CHAKRAM = 37, FX_PULSE = 38, FX_HARVEST = 39;

    private static void fx(ServerPlayer p, int cue, Vec3 from, Vec3 to, LivingEntity target, int extra, int c1, int c2) {
        int outer = dev.abps.util.Vfx.theme(dev.abps.dungeon.Dungeons.THEME);
        try {
            Vec3 dir = to.subtract(from);
            dev.abps.util.Vfx.cue((ServerLevel) p.level(), cue, from, dir.lengthSqr() < 1e-6 ? p.getLookAngle() : dir.normalize(), to, p, target, c1, c2, extra);
        } finally {
            dev.abps.util.Vfx.theme(outer);
        }
    }

    /** The first enemy along the look line within range, and where the line ends. */
    private static Object[] lookHit(ServerPlayer p, double range) {
        LivingEntity t = Targets.lookTarget(p, range);
        Vec3 end = t != null ? t.position().add(0, t.getBbHeight() * 0.5, 0) : Targets.aimPoint(p, range);
        return new Object[]{t, end};
    }

    /**
     * Right-click with a custom weapon or tool. Returns true when it did something, so the click is used up.
     * Each power puts the item on cooldown, which the hotbar shows.
     */
    public static boolean use(ServerPlayer p, ItemStack stack) {
        String id = idOf(stack);
        if (id == null) return false;
        if (p.getCooldowns().isOnCooldown(stack)) return true;
        ServerLevel level = (ServerLevel) p.level();
        Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.3, 0);
        int cd;
        switch (id) {
            case "thunder_hammer" -> {
                for (LivingEntity e : Targets.enemiesNear(p, p.position(), 5)) {
                    Targets.damage(e, 9, p);
                    Targets.velocity(e, e.getDeltaMovement().add(0, 0.8, 0));
                    fx(p, FX_THUNDER, e.position().add(0, 12, 0), e.position(), e, 1, 0x40C4FF, 0xFFFFFF);
                }
                fx(p, FX_THUNDER, p.position(), p.position(), null, 0, 0x40C4FF, 0xFFFFFF);
                Fx.sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
                Fx.shakeNear(level, p.position(), 8, 6, 0.4f);
                cd = 160;
            }
            case "ember_staff", "frost_staff", "soul_staff" -> {
                Object[] hit = lookHit(p, 24);
                LivingEntity t = (LivingEntity) hit[0];
                Vec3 end = (Vec3) hit[1];
                switch (id) {
                    case "ember_staff" -> {
                        fx(p, FX_FIRE, hand, end, t, 0, 0xFF6D00, 0xFFD180);
                        if (t != null) {
                            Targets.damage(t, 7, p);
                            t.igniteForSeconds(4);
                        }
                        Fx.sound(level, p, SoundEvents.BLAZE_SHOOT, 0.8f, 1.2f);
                        cd = 30;
                    }
                    case "frost_staff" -> {
                        fx(p, FX_FROST, hand, end, t, 0, 0x80DEEA, 0xE1F5FE);
                        if (t != null) {
                            Targets.damage(t, 6, p);
                            t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                            if (p.getRandom().nextFloat() < 0.25f) Targets.root(t, 20);
                        }
                        Fx.sound(level, p, SoundEvents.PLAYER_HURT_FREEZE, 0.8f, 1.4f);
                        cd = 40;
                    }
                    default -> {
                        if (t == null) {
                            AbpsMod.service().actionBar(p, "<gray>Look at an enemy within 24 blocks.");
                            return true;
                        }
                        fx(p, FX_SOUL, hand, end, t, 0, 0x64FFDA, 0x1DE9B6);
                        Targets.damage(t, 5, p);
                        p.heal(3);
                        Fx.sound(level, p, SoundEvents.SOUL_ESCAPE.value(), 1f, 0.8f);
                        cd = 60;
                    }
                }
            }
            case "shadow_daggers" -> {
                Vec3 from = p.position(), dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
                if (dir.lengthSqr() < 1e-4) return true;
                dir = dir.normalize();
                Vec3 to = from;
                for (double d = 0.5; d <= 6; d += 0.5) {
                    Vec3 step = from.add(dir.scale(d));
                    if (!level.noCollision(p, p.getBoundingBox().move(step.subtract(p.position())))) break;
                    to = step;
                }
                Vec3 seg = to.subtract(from);
                double len = seg.length();
                for (LivingEntity e : Targets.enemiesNear(p, from.add(seg.scale(0.5)), len / 2 + 1.5)) {
                    Vec3 rel = e.position().subtract(from);
                    double along = len < 0.01 ? 0 : rel.dot(seg) / len;
                    if (along < -0.5 || along > len + 0.5) continue;
                    if (rel.subtract(seg.scale(along / Math.max(0.01, len))).length() > 1.6) continue;
                    Targets.damage(e, 6, p);
                }
                fx(p, FX_DASH, from.add(0, 1, 0), to.add(0, 1, 0), null, 0, 0x5E35B1, 0xB388FF);
                p.teleportTo(level, to.x, to.y, to.z, java.util.Set.<net.minecraft.world.entity.Relative>of(), p.getYRot(), p.getXRot(), false);
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));
                Fx.sound(level, p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.6f);
                cd = 120;
            }
            case "dragonbone_greatsword" -> {
                for (LivingEntity e : Targets.enemiesNear(p, p.position(), 4)) {
                    Targets.damage(e, 8, p);
                    Targets.pushAway(p.position(), e, 1.1, 0.35);
                }
                fx(p, FX_WHIRL, p.position(), p.position(), null, 0, 0xEFEBE9, 0xD32F2F);
                Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
                Fx.sound(level, p, SoundEvents.RAVAGER_ROAR, 0.4f, 1.4f);
                cd = 200;
            }
            case "tide_spear" -> {
                Vec3 look = p.getLookAngle();
                LivingEntity t = Targets.lookTarget(p, 6);
                Targets.velocity(p, look.scale(1.4).add(0, 0.15, 0));
                if (t != null) {
                    Tasks.later(3, () -> {
                        if (t.isAlive() && !p.isRemoved()) Targets.damage(t, 9, p);
                    });
                }
                fx(p, FX_LUNGE, p.position().add(0, 1, 0), p.position().add(0, 1, 0).add(look.scale(5)), t, 0, 0x26A69A, 0x80CBC4);
                Fx.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_3, 0.8f, 1.2f);
                cd = 120;
            }
            case "echo_chakram" -> {
                Vec3 start = hand, look = p.getLookAngle();
                Set<UUID> hitOut = new HashSet<>(), hitBack = new HashSet<>();
                fx(p, FX_CHAKRAM, start, start.add(look.scale(12)), null, 0, 0xB0BEC5, 0x00E5FF);
                Tasks.repeat(24, 1, step -> {
                    if (p.isRemoved()) return;
                    boolean out = step < 12;
                    double d = out ? step + 1 : 24 - step;
                    Vec3 at = start.add(look.scale(d));
                    for (LivingEntity e : Targets.enemiesNear(p, at, 1.6)) {
                        if ((out ? hitOut : hitBack).add(e.getUUID())) Targets.damage(e, 6, p);
                    }
                    if (step % 4 == 0) Fx.sound(level, at, SoundEvents.TRIDENT_THROW.value(), 0.4f, 1.8f);
                });
                cd = 80;
            }
            case "prospector_pick" -> {
                List<Integer> ids = new ArrayList<>();
                BlockPos c = p.blockPosition();
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-10, -10, -10), c.offset(10, 10, 10))) {
                    BlockState s = level.getBlockState(q);
                    if (isOre(s)) ids.add(dev.abps.util.FakeBlocks.show(p, q.immutable(), s, 0xFFCA28));
                    if (ids.size() >= 60) break;
                }
                fx(p, FX_PULSE, p.position(), p.position(), null, 10, 0xFFCA28, 0x4DD0E1);
                Tasks.later(200, () -> dev.abps.util.FakeBlocks.hide(p, ids.stream().mapToInt(Integer::intValue).toArray()));
                AbpsMod.service().actionBar(p, "<gold>Found <white>" + ids.size() + "</white> ores.");
                Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
                cd = 600;
            }
            case "harvest_scythe" -> {
                BlockPos c = p.blockPosition();
                int n = 0;
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-4, -1, -4), c.offset(4, 1, 4))) {
                    BlockPos at = q.immutable();
                    BlockState s = level.getBlockState(at);
                    if (!dev.abps.classes.Harvester.ripe(s) || !p.gameMode.destroyBlock(at)) continue;
                    BlockState again = dev.abps.classes.Harvester.replanted(s);
                    if (level.getBlockState(at).isAir() && again.canSurvive(level, at)) level.setBlock(at, again, net.minecraft.world.level.block.Block.UPDATE_ALL);
                    n++;
                }
                if (n == 0) {
                    AbpsMod.service().actionBar(p, "<gray>No ripe crops nearby.");
                    return true;
                }
                fx(p, FX_HARVEST, p.position(), p.position(), null, 4, 0xC0CA33, 0x9CCC65);
                Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                cd = 40;
            }
            default -> {
                return false;
            }
        }
        p.getCooldowns().addCooldown(stack, cd);
        return true;
    }

    public static void forget(UUID id) {
        VOID_HITS.remove(id);
    }

}
