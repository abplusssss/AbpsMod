package dev.abps.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Fishing: 43 fish that bite in their own places, times and weather (including lava with a Magma Rod and the End's
 * void with a Void Rod), bait, a reel-in minigame for the big ones, a fish journal, tournaments and trophy mounts.
 */
public final class Fishing {

    private Fishing() {
    }

    public record Fish(String id, String name, int rarity, String where, String when, Item item) {
    }

    public static final List<Fish> FISH = new ArrayList<>();
    private static final Map<Item, Fish> BY_ITEM = new HashMap<>();
    public static Item RUBY_ROD, MAGMA_ROD, VOID_ROD, WORM_BAIT, GLOW_BAIT, GOLDEN_BAIT, TREASURE_MAP, TROPHY_MOUNT, SUSHI;
    private static final Random RND = new Random();
    private static final String[] RARITY = {"", "<white>Common", "<#69F0AE>Uncommon", "<#40C4FF>Rare", "<#E040FB>Epic", "<gradient:#FFD54F:#FF6D00>Legendary"};
    private static final int[] WEIGHT = {0, 60, 26, 10, 3, 1};

    // ------------------------------------------------------------------ registration

    private static Item register(String id, java.util.function.Function<Item.Properties, Item> f, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AbpsMod.id(id));
        Item i = Registry.register(BuiltInRegistries.ITEM, key, f.apply(props.setId(key)));
        ModContent.ITEMS.add(i);
        return i;
    }

    public static void register() {
        try (var in = Fishing.class.getResourceAsStream("/abpsmod/fish.json")) {
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                int r = o.get("rarity").getAsInt();
                Rarity rar = r >= 4 ? Rarity.EPIC : r == 3 ? Rarity.RARE : r == 2 ? Rarity.UNCOMMON : Rarity.COMMON;
                FoodProperties food = new FoodProperties.Builder().nutrition(1 + r).saturationModifier(0.1f + 0.05f * r).build();
                Item item = register(o.get("id").getAsString(), Item::new, new Item.Properties().food(food).rarity(rar));
                Fish fish = new Fish(o.get("id").getAsString(), o.get("name").getAsString(), r, o.get("where").getAsString(), o.get("when").getAsString(), item);
                FISH.add(fish);
                BY_ITEM.put(item, fish);
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.error("Could not load the fish list", e);
        }
        RUBY_ROD = register("ruby_rod", FishingRodItem::new, new Item.Properties().durability(256));
        MAGMA_ROD = register("magma_rod", FishingRodItem::new, new Item.Properties().durability(320).fireResistant());
        VOID_ROD = register("void_rod", FishingRodItem::new, new Item.Properties().durability(400).fireResistant());
        WORM_BAIT = register("worm_bait", Item::new, new Item.Properties());
        GLOW_BAIT = register("glow_bait", Item::new, new Item.Properties());
        GOLDEN_BAIT = register("golden_bait", Item::new, new Item.Properties());
        TREASURE_MAP = register("treasure_map", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
        TROPHY_MOUNT = register("trophy_mount", Item::new, new Item.Properties());
        SUSHI = register("sushi", Item::new, new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6f).build()));

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.tickCount == 0 && level instanceof ServerLevel sl) onCatch(sl, item);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                    && player instanceof ServerPlayer p && level instanceof ServerLevel sl && unmount(p, sl, hit.getBlockPos())) return InteractionResult.SUCCESS;
            if (!player.getItemInHand(hand).is(TROPHY_MOUNT) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (player instanceof ServerPlayer p && level instanceof ServerLevel sl) mount(p, sl, hit.getBlockPos(), hit.getDirection());
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Fishing::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
    }

    public static Fish fishOf(ItemStack s) {
        return BY_ITEM.get(s.getItem());
    }

    // ------------------------------------------------------------------ where are we fishing?

    private static String biome(ServerLevel level, BlockPos pos) {
        Holder<Biome> b = level.getBiome(pos);
        String path = b.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        return path;
    }

    private static boolean fits(Fish f, ServerLevel level, BlockPos pos, String special) {
        if (special != null) return f.where().equals(special);
        if (f.where().equals("lava") || f.where().equals("void")) return false;
        String b = biome(level, pos);
        boolean ocean = level.getBiome(pos).is(BiomeTags.IS_OCEAN);
        boolean river = level.getBiome(pos).is(BiomeTags.IS_RIVER) || (!ocean && !b.contains("swamp"));
        boolean where = switch (f.where()) {
            case "ocean" -> ocean;
            case "river" -> river;
            case "swamp" -> b.contains("swamp");
            case "cold" -> b.contains("frozen") || b.contains("cold") || b.contains("snowy");
            case "warm" -> b.contains("warm") || b.contains("lukewarm");
            case "jungle" -> b.contains("jungle");
            case "cave" -> pos.getY() < 40 && !level.canSeeSky(pos);
            default -> true;
        };
        if (!where) return false;
        long t = Time.dayTime(level) % 24000;
        boolean night = t > 13000 && t < 23000;
        return switch (f.when()) {
            case "day" -> !night;
            case "night" -> night;
            case "rain" -> level.isRaining();
            default -> true;
        };
    }

    /** Picks a fish for this spot. Luck shifts the odds toward the rare ones. */
    static Fish roll(ServerPlayer p, ServerLevel level, BlockPos pos, String special) {
        List<Fish> pool = new ArrayList<>();
        for (Fish f : FISH) if (fits(f, level, pos, special)) pool.add(f);
        if (pool.isEmpty()) return null;
        ItemStack rod = p.getMainHandItem().getItem() instanceof FishingRodItem ? p.getMainHandItem() : p.getOffhandItem();
        int luck = Enchants.level(p, rod, Enchantments.LUCK_OF_THE_SEA) + (rod.is(RUBY_ROD) ? 2 : 0) + (SetBonuses.fullRuby(p) ? 1 : 0);
        ItemStack bait = p.getOffhandItem();
        double[] mult = {0, 1, 1, 1, 1, 1};
        if (bait.is(WORM_BAIT)) mult[2] = 1.6;
        if (bait.is(GLOW_BAIT)) {
            mult[3] = 2;
            mult[4] = 1.6;
        }
        if (bait.is(GOLDEN_BAIT)) {
            mult[4] = 2.5;
            mult[5] = 4;
        }
        double total = 0;
        double[] w = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            int r = pool.get(i).rarity();
            w[i] = WEIGHT[r] * mult[r] * (r >= 3 ? 1 + 0.35 * luck : 1);
            total += w[i];
        }
        double x = RND.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            x -= w[i];
            if (x <= 0) return pool.get(i);
        }
        return pool.getLast();
    }

    private static boolean useBait(ServerPlayer p) {
        ItemStack bait = p.getOffhandItem();
        if (bait.is(WORM_BAIT) || bait.is(GLOW_BAIT) || bait.is(GOLDEN_BAIT)) {
            if (!p.isCreative()) bait.shrink(1);
            return true;
        }
        return false;
    }

    /** A caught fish with its size and where it came from written on it. */
    static ItemStack stackOf(Fish f, ServerLevel level, BlockPos pos) {
        ItemStack s = new ItemStack(f.item());
        double base = 12 + f.rarity() * 14;
        int cm = (int) Math.round(base * (0.6 + RND.nextDouble() * 0.9));
        List<Component> lore = new ArrayList<>();
        lore.add(dev.abps.util.Text.mm(RARITY[f.rarity()] + " <dark_gray>·</dark_gray> <gray>" + cm + " cm"));
        lore.add(dev.abps.util.Text.mm("<dark_gray>Caught in " + pretty(f.where().equals("lava") ? "lava" : f.where().equals("void") ? "the void" : biome(level, pos))));
        s.set(DataComponents.LORE, new ItemLore(lore));
        CompoundTag t = new CompoundTag();
        t.putInt("abps_size", cm);
        CustomData.set(DataComponents.CUSTOM_DATA, s, t);
        return s;
    }

    private static int sizeOf(ItemStack s) {
        var d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getIntOr("abps_size", 0);
    }

    private static String pretty(String id) {
        String[] parts = id.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : parts) if (!w.isEmpty()) sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }

    // ------------------------------------------------------------------ catches

    /** A vanilla catch just flew out of the water: swap plain fish for ours, and sometimes junk for a treasure map. */
    private static void onCatch(ServerLevel level, ItemEntity item) {
        ItemStack s = item.getItem();
        boolean vanillaFish = s.is(Items.COD) || s.is(Items.SALMON) || s.is(Items.TROPICAL_FISH) || s.is(Items.PUFFERFISH);
        boolean junk = s.is(Items.STICK) || s.is(Items.BOWL) || s.is(Items.LEATHER) || s.is(Items.ROTTEN_FLESH) || s.is(Items.STRING)
                || s.is(Items.LEATHER_BOOTS) || s.is(Items.INK_SAC) || s.is(Items.BONE) || s.is(Items.TRIPWIRE_HOOK);
        if (!vanillaFish && !junk) return;
        ServerPlayer angler = null;
        for (ServerPlayer p : level.players()) {
            var hook = p.fishing;
            if (hook != null && hook.position().distanceToSqr(item.position()) < 4) {
                angler = p;
                break;
            }
        }
        if (angler == null) return;
        if (junk) {
            if (RND.nextDouble() < 0.12) item.setItem(new ItemStack(TREASURE_MAP));
            return;
        }
        boolean bait = useBait(angler);
        if (RND.nextDouble() > (bait ? 0.9 : 0.75)) return;
        Fish f = roll(angler, level, item.blockPosition(), null);
        if (f == null) return;
        if (f.rarity() >= 4) {
            item.discard();
            startReel(angler, f, item.blockPosition());
            return;
        }
        ItemStack caught = stackOf(f, level, item.blockPosition());
        item.setItem(caught);
        logged(angler, f, caught);
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) ModContent.drop(p, s);
    }

    // ------------------------------------------------------------------ the reel-in minigame (epic and legendary fish)

    private static final class Reel {
        final Fish fish;
        final BlockPos at;
        int hits, misses, ticks;
        boolean wasSneaking;
        double zone = 0.35 + RND.nextDouble() * 0.3;

        Reel(Fish fish, BlockPos at) {
            this.fish = fish;
            this.at = at;
        }

        double marker() {
            double speed = 0.045 + fish.rarity() * 0.01 + hits * 0.012;
            double t = (ticks * speed) % 2;
            return t < 1 ? t : 2 - t;
        }
    }

    private static final Map<UUID, Reel> REELS = new HashMap<>();

    private static void startReel(ServerPlayer p, Fish f, BlockPos at) {
        Reel r = new Reel(f, at);
        r.wasSneaking = p.isShiftKeyDown();
        REELS.put(p.getUUID(), r);
        AbpsMod.service().banner(p, "<bold>" + RARITY[f.rarity()] + " bite!</bold>", "<gray>Press <yellow>sneak</yellow> when the marker is in the green. 3 hits to land it.",
                f.rarity() == 5 ? 0xFFD54F : 0xE040FB, 50);
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.FISHING_BOBBER_SPLASH, 1f, 0.7f);
    }

    private static void tickReel(ServerPlayer p, Reel r) {
        r.ticks++;
        double m = r.marker();
        double half = 0.11 - r.hits * 0.015;
        boolean in = Math.abs(m - r.zone) <= half;
        boolean sneaking = p.isShiftKeyDown();
        if (sneaking && !r.wasSneaking) {
            if (in) {
                r.hits++;
                r.zone = 0.2 + RND.nextDouble() * 0.6;
                Fx.sound((ServerLevel) p.level(), p, SoundEvents.FISHING_BOBBER_RETRIEVE, 1f, 1f + r.hits * 0.2f);
            } else {
                r.misses++;
                Fx.sound((ServerLevel) p.level(), p, SoundEvents.FISHING_BOBBER_SPLASH, 0.8f, 0.5f);
            }
        }
        r.wasSneaking = sneaking;
        if (r.hits >= 3) {
            REELS.remove(p.getUUID());
            ItemStack caught = stackOf(r.fish, (ServerLevel) p.level(), r.at);
            give(p, caught);
            AbpsMod.service().banner(p, "<bold>Landed!</bold>", RARITY[r.fish.rarity()] + " <white>" + r.fish.name(), 0x69F0AE, 60);
            Fx.burst((ServerLevel) p.level(), ParticleTypes.SPLASH, p.position().add(0, 1, 0), 30, 0.6, 0.2);
            logged(p, r.fish, caught);
            if (r.fish.rarity() == 5) {
                for (ServerPlayer o : AbpsMod.server().getPlayerList().getPlayers())
                    AbpsMod.service().send(o, "<gold>🎣 " + p.getName().getString() + " landed a legendary <bold>" + r.fish.name() + "</bold>!");
            }
            return;
        }
        if (r.misses >= 3 || r.ticks > 20 * 15) {
            REELS.remove(p.getUUID());
            AbpsMod.service().banner(p, "<bold><gray>It got away...</gray></bold>", "<dark_gray>" + r.fish.name(), 0x9E9E9E, 50);
            give(p, new ItemStack(Items.COD));
            return;
        }
        // Draw the bar: 30 cells, green zone, the marker
        StringBuilder sb = new StringBuilder();
        int cells = 30, mk = (int) Math.round(m * (cells - 1));
        for (int i = 0; i < cells; i++) {
            double c = i / (double) (cells - 1);
            boolean green = Math.abs(c - r.zone) <= half;
            if (i == mk) sb.append("<white><bold>|</bold>");
            else sb.append(green ? "<#69F0AE>■" : "<dark_gray>■");
        }
        AbpsMod.service().actionBar(p, sb + " <gray>" + r.hits + "/3" + (r.misses > 0 ? " <red>" + "✗".repeat(r.misses) : ""));
    }

    // ------------------------------------------------------------------ lava and void fishing

    private static final class Spot {
        int ticks, biteAt = 100 + RND.nextInt(200);
        String kind;
        Vec3 at;
    }

    private static final Map<UUID, Spot> SPOTS = new HashMap<>();

    private static void tickSpecial(ServerPlayer p) {
        var hook = p.fishing;
        Spot s = SPOTS.get(p.getUUID());
        if (hook == null) {
            // Reeled in: inside the bite window means a catch
            if (s != null && s.ticks >= s.biteAt && s.ticks <= s.biteAt + 30) {
                ServerLevel level = (ServerLevel) p.level();
                useBait(p);
                Fish f = roll(p, level, BlockPos.containing(s.at), s.kind);
                if (f != null) {
                    if (f.rarity() >= 4) startReel(p, f, BlockPos.containing(s.at));
                    else {
                        ItemStack caught = stackOf(f, level, BlockPos.containing(s.at));
                        give(p, caught);
                        logged(p, f, caught);
                    }
                }
            }
            SPOTS.remove(p.getUUID());
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        ItemStack rod = p.getMainHandItem().getItem() instanceof FishingRodItem ? p.getMainHandItem() : p.getOffhandItem();
        String kind = null;
        BlockPos at = hook.blockPosition();
        if (rod.is(MAGMA_ROD) && level.getFluidState(at).is(FluidTags.LAVA)) kind = "lava";
        if (rod.is(VOID_ROD) && level.dimension() == Level.END && hook.getY() < 40 && level.getBlockState(at.below(3)).isAir()) {
            kind = "void";
            hook.setNoGravity(true);
            hook.setDeltaMovement(Vec3.ZERO);
        }
        if (kind == null) {
            SPOTS.remove(p.getUUID());
            return;
        }
        if (s == null || !kind.equals(s.kind)) {
            s = new Spot();
            s.kind = kind;
            SPOTS.put(p.getUUID(), s);
        }
        s.ticks++;
        s.at = hook.position();
        if (s.ticks % 10 == 0) Fx.burst(level, kind.equals("lava") ? ParticleTypes.LAVA : ParticleTypes.PORTAL, s.at, 1, 0.1, 0.02);
        if (s.ticks == s.biteAt) {
            Fx.burst(level, kind.equals("lava") ? ParticleTypes.FLAME : ParticleTypes.REVERSE_PORTAL, s.at, 20, 0.3, 0.1);
            Fx.sound(level, s.at, SoundEvents.FISHING_BOBBER_SPLASH, 1f, kind.equals("lava") ? 0.6f : 1.6f);
            AbpsMod.service().actionBar(p, "<gold><bold>Bite!</bold> <gray>Reel in now!");
        }
        if (s.ticks > s.biteAt + 30) {
            s.ticks = 0;
            s.biteAt = 100 + RND.nextInt(200);
        }
    }

    // ------------------------------------------------------------------ journal

    /** Per player: fish id -> biggest one caught (cm). */
    private static Map<String, Map<String, Integer>> journal = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static void logged(ServerPlayer p, Fish f, ItemStack caught) {
        Map<String, Integer> j = journal.computeIfAbsent(p.getUUID().toString(), u -> new LinkedHashMap<>());
        int size = sizeOf(caught);
        Integer old = j.get(f.id());
        if (old == null) {
            j.put(f.id(), size);
            AbpsMod.service().send(p, "<aqua>📖 New fish:</aqua> " + RARITY[f.rarity()] + " " + f.name() + " <gray>(" + j.size() + "/" + FISH.size() + " in your journal)");
            milestone(p, j.size());
        } else if (size > old) {
            j.put(f.id(), size);
            AbpsMod.service().actionBar(p, "<aqua>New record " + f.name() + ": " + size + " cm!");
        }
        tournamentCatch(p, f);
        save();
    }

    private static void milestone(ServerPlayer p, int n) {
        ItemStack reward = switch (n) {
            case 10 -> new ItemStack(GOLDEN_BAIT, 4);
            case 20 -> new ItemStack(RUBY_ROD);
            case 30 -> new ItemStack(TREASURE_MAP);
            default -> ItemStack.EMPTY;
        };
        if (!reward.isEmpty()) {
            give(p, reward);
            AbpsMod.service().banner(p, "<bold><aqua>" + n + " species!</aqua></bold>", "<gray>A reward for your journal", 0x40C4FF, 60);
        }
        if (n == FISH.size()) {
            var d = AbpsMod.data().get(p);
            if (!d.titles.contains("Master Angler")) d.titles.add("Master Angler");
            AbpsMod.data().save(p, d);
            AbpsMod.service().banner(p, "<bold><gradient:#FFD54F:#40C4FF>MASTER ANGLER</gradient></bold>", "<gray>Every fish caught. New title unlocked!", 0x40C4FF, 80);
        }
    }

    /** The journal as chat lines. */
    public static List<String> journalLines(ServerPlayer p) {
        Map<String, Integer> j = journal.getOrDefault(p.getUUID().toString(), Map.of());
        List<String> out = new ArrayList<>();
        out.add("<aqua><bold>Fish journal</bold></aqua> <gray>" + j.size() + "/" + FISH.size() + " caught");
        for (Fish f : FISH) {
            Integer cm = j.get(f.id());
            out.add(cm == null ? " <dark_gray>??? <dark_gray>(" + RARITY[f.rarity()].replaceAll("<[^>]+>", "") + ", " + hint(f) + ")"
                    : " " + RARITY[f.rarity()] + "</" + "> <white>" + f.name() + " <gray>" + cm + " cm");
        }
        return out;
    }

    private static String hint(Fish f) {
        String w = switch (f.where()) {
            case "lava" -> "lava, Magma Rod";
            case "void" -> "End void, Void Rod";
            case "cave" -> "deep caves";
            default -> f.where();
        };
        return w + (f.when().equals("any") ? "" : ", " + f.when());
    }

    // ------------------------------------------------------------------ tournaments

    private static long tournamentEnds;
    private static final Map<UUID, Integer> SCORES = new HashMap<>();
    private static long ticks;

    public static void startTournament(MinecraftServer server) {
        tournamentEnds = System.currentTimeMillis() + 5 * 60_000L;
        SCORES.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            AbpsMod.service().banner(p, "<bold><#40C4FF>FISHING TOURNAMENT</#40C4FF></bold>", "<gray>5 minutes. Rarer fish score more!", 0x40C4FF, 80);
        }
    }

    private static void tournamentCatch(ServerPlayer p, Fish f) {
        if (System.currentTimeMillis() > tournamentEnds) return;
        int pts = f.rarity() * f.rarity();
        int total = SCORES.merge(p.getUUID(), pts, Integer::sum);
        AbpsMod.service().actionBar(p, "<#40C4FF>+" + pts + " tournament points <gray>(" + total + ")");
    }

    private static void endTournament(MinecraftServer server) {
        tournamentEnds = 0;
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(SCORES.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder("<#40C4FF><bold>Tournament results</bold></#40C4FF>");
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            ServerPlayer p = server.getPlayerList().getPlayer(top.get(i).getKey());
            String name = p == null ? "?" : p.getName().getString();
            sb.append(" <gray>").append(i + 1).append(". <white>").append(name).append(" <gold>").append(top.get(i).getValue());
            if (p != null) {
                ItemStack prize = i == 0 ? new ItemStack(Items.DIAMOND, 5) : i == 1 ? new ItemStack(Items.EMERALD, 12) : new ItemStack(GOLDEN_BAIT, 3);
                give(p, prize);
                if (i == 0) give(p, new ItemStack(TREASURE_MAP));
            }
        }
        if (top.isEmpty()) sb.append(" <gray>Nobody caught anything!");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) AbpsMod.service().send(p, sb.toString());
        SCORES.clear();
    }

    // ------------------------------------------------------------------ trophy mounts

    static final String TROPHY_TAG = "abps_trophy";

    /** Right-click the side of a block with a Trophy Mount and a fish in your off hand: the fish goes up on the wall with a plaque. */
    private static void mount(ServerPlayer p, ServerLevel level, BlockPos pos, net.minecraft.core.Direction face) {
        ItemStack fish = p.getOffhandItem();
        Fish f = fishOf(fish);
        if (f == null || face.getAxis().isVertical()) {
            AbpsMod.service().actionBar(p, "<gray>Hold a fish in your off hand and right-click the side of a block.");
            return;
        }
        Vec3 c = Vec3.atCenterOf(pos).add(face.getStepX() * 0.53, 0.1, face.getStepZ() * 0.53);
        float yaw = face.toYRot();
        var display = new net.minecraft.world.entity.Display.ItemDisplay(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY, level);
        display.setPos(c.x, c.y, c.z);
        display.setYRot(yaw);
        display.setItemStack(fish.copyWithCount(1));
        display.setTransformation(new com.mojang.math.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                new org.joml.Vector3f(0.9f, 0.9f, 0.9f), new org.joml.Quaternionf()));
        display.addTag(TROPHY_TAG);
        level.addFreshEntity(display);
        var label = new net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY, level);
        label.setPos(c.x, c.y - 0.62, c.z);
        label.setYRot(yaw);
        label.setText(dev.abps.util.Text.mm(RARITY[f.rarity()] + " " + f.name() + "\n<gray>" + sizeOf(fish) + " cm · " + p.getName().getString()));
        label.setTransformation(new com.mojang.math.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                new org.joml.Vector3f(0.45f, 0.45f, 0.45f), new org.joml.Quaternionf()));
        label.addTag(TROPHY_TAG);
        level.addFreshEntity(label);
        if (!p.isCreative()) {
            fish.shrink(1);
            p.getMainHandItem().shrink(1);
        }
        Fx.sound(level, c, SoundEvents.ITEM_FRAME_ADD_ITEM, 1f, 1f);
    }

    /** Sneak-right-click a mounted trophy's wall with an empty hand to take it down. */
    static boolean unmount(ServerPlayer p, ServerLevel level, BlockPos pos) {
        boolean any = false;
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Display.class, new net.minecraft.world.phys.AABB(pos).inflate(0.8),
                d -> d.entityTags().contains(TROPHY_TAG))) {
            if (e instanceof net.minecraft.world.entity.Display.ItemDisplay item) {
                give(p, item.getItemStack().copy());
                give(p, new ItemStack(TROPHY_MOUNT));
            }
            e.discard();
            any = true;
        }
        return any;
    }

    // ------------------------------------------------------------------ ticking and saving

    private static void tick(MinecraftServer server) {
        ticks++;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Reel r = REELS.get(p.getUUID());
            if (r != null) tickReel(p, r);
            tickSpecial(p);
        }
        if (tournamentEnds > 0 && System.currentTimeMillis() > tournamentEnds) endTournament(server);
        // A tournament every three hours when at least two people are on
        if (ticks % (20L * 60 * 60 * 3) == 0 && server.getPlayerList().getPlayers().size() >= 2) startTournament(server);
    }

    private static Path file() {
        return AbpsMod.data().root().resolve("fishing.json");
    }

    private static void load() {
        try {
            if (Files.exists(file())) {
                Map<String, Map<String, Integer>> m = GSON.fromJson(Files.readString(file()), new TypeToken<HashMap<String, LinkedHashMap<String, Integer>>>() {
                }.getType());
                if (m != null) journal = m;
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not read the fish journal: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), GSON.toJson(journal));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not save the fish journal: {}", e.toString());
        }
    }
}
