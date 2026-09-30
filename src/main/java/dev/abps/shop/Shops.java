package dev.abps.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.abps.AbpsMod;
import dev.abps.Config;
import dev.abps.Cost;
import dev.abps.Service;
import dev.abps.net.Net;
import dev.abps.util.Fx;
import dev.abps.util.Inv;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Player shops. Anyone can open one for a fee. A shop holds real items: stock the owner put in to sell, money the
 * owner put up to buy things, and what it earned or bought, waiting for the owner to collect it. So shops work while
 * the owner is offline, and every trade moves items that exist. The server checks everything; the client only asks.
 *
 * Saved to world/abpsmod/shops.json after every change, written on a background thread through a temp file.
 */
public final class Shops {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_NAME = 24;
    private static final int MAX_PRICE = 4096;
    private static final int MAX_BUNDLE = 64;

    /** One trade offer. For a selling listing, stock is items the owner put in. For a buying one, funds is currency put up. */
    static final class Listing {
        int id;
        boolean selling;
        ItemStack item = ItemStack.EMPTY; // count 1, the exact item with its components
        int bundle = 1;                   // items per trade
        String priceItem = "minecraft:diamond";
        int price = 1;                    // currency per bundle
        int stock;                        // items, for selling listings
        int funds;                        // currency, for buying listings
    }

    /** Items waiting for the owner: the template and how many. */
    static final class Stored {
        ItemStack item;
        int count;

        Stored(ItemStack item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    static final class Shop {
        UUID owner;
        String ownerName = "";
        String name = "";
        ItemStack icon = new ItemStack(Items.CHEST);
        long created;
        int nextId = 1;
        int sales;
        final List<Listing> listings = new ArrayList<>();
        final Map<String, Integer> earnings = new LinkedHashMap<>();
        final List<Stored> collected = new ArrayList<>();

        Listing listing(int id) {
            for (Listing l : listings) if (l.id == id) return l;
            return null;
        }
    }

    private final MinecraftServer server;
    private final Path file;
    private final Map<UUID, Shop> shops = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AbpsMod shop saver");
        t.setDaemon(true);
        return t;
    });

    public Shops(MinecraftServer server, Path root) {
        this.server = server;
        this.file = root.resolve("shops.json");
        load();
    }

    private static Service s() {
        return AbpsMod.service();
    }

    private static Config cfg() {
        return AbpsMod.config();
    }

    // ================= Saving =================

    private RegistryOps<JsonElement> ops() {
        return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private JsonElement encode(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(ops(), stack.copyWithCount(1)).result().orElse(null);
    }

    private ItemStack decode(JsonElement json) {
        if (json == null || json.isJsonNull()) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(ops(), json).result().orElse(ItemStack.EMPTY);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("shops")) {
                JsonObject o = e.getAsJsonObject();
                Shop shop = new Shop();
                shop.owner = UUID.fromString(o.get("owner").getAsString());
                shop.ownerName = o.get("ownerName").getAsString();
                shop.name = o.get("name").getAsString();
                ItemStack icon = decode(o.get("icon"));
                if (!icon.isEmpty()) shop.icon = icon;
                shop.created = o.get("created").getAsLong();
                shop.nextId = o.get("nextId").getAsInt();
                shop.sales = o.has("sales") ? o.get("sales").getAsInt() : 0;
                for (JsonElement le : o.getAsJsonArray("listings")) {
                    JsonObject lo = le.getAsJsonObject();
                    Listing l = new Listing();
                    l.id = lo.get("id").getAsInt();
                    l.selling = lo.get("selling").getAsBoolean();
                    l.item = decode(lo.get("item"));
                    l.bundle = lo.get("bundle").getAsInt();
                    l.priceItem = lo.get("priceItem").getAsString();
                    l.price = lo.get("price").getAsInt();
                    l.stock = lo.get("stock").getAsInt();
                    l.funds = lo.get("funds").getAsInt();
                    if (l.item.isEmpty()) {
                        // The item no longer exists (a mod was removed). Keep the money so nothing is lost.
                        AbpsMod.LOGGER.warn("Shop {}: listing {} has an item that no longer exists and was dropped ({} in stock)", shop.name, l.id, l.stock);
                        if (l.funds > 0) shop.earnings.merge(l.priceItem, l.funds, Integer::sum);
                        continue;
                    }
                    shop.listings.add(l);
                }
                for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("earnings").entrySet()) shop.earnings.merge(en.getKey(), en.getValue().getAsInt(), Integer::sum);
                for (JsonElement ce : o.getAsJsonArray("collected")) {
                    JsonObject co = ce.getAsJsonObject();
                    ItemStack item = decode(co.get("item"));
                    if (!item.isEmpty()) shop.collected.add(new Stored(item, co.get("count").getAsInt()));
                }
                shops.put(shop.owner, shop);
            }
            AbpsMod.LOGGER.info("Loaded {} player shops", shops.size());
        } catch (Exception e) {
            // Never overwrite a file we couldn't read: keep a copy so nothing is lost
            AbpsMod.LOGGER.error("Could not read shops.json, keeping a copy as shops.json.broken: {}", e.toString());
            try {
                Files.copy(file, file.resolveSibling("shops.json.broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                // nothing more we can do
            }
        }
    }

    /** Builds the file on the server thread, then writes it in the background. */
    private void save() {
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        for (Shop shop : shops.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("owner", shop.owner.toString());
            o.addProperty("ownerName", shop.ownerName);
            o.addProperty("name", shop.name);
            o.add("icon", encode(shop.icon));
            o.addProperty("created", shop.created);
            o.addProperty("nextId", shop.nextId);
            o.addProperty("sales", shop.sales);
            JsonArray ls = new JsonArray();
            for (Listing l : shop.listings) {
                JsonObject lo = new JsonObject();
                lo.addProperty("id", l.id);
                lo.addProperty("selling", l.selling);
                lo.add("item", encode(l.item));
                lo.addProperty("bundle", l.bundle);
                lo.addProperty("priceItem", l.priceItem);
                lo.addProperty("price", l.price);
                lo.addProperty("stock", l.stock);
                lo.addProperty("funds", l.funds);
                ls.add(lo);
            }
            o.add("listings", ls);
            JsonObject earn = new JsonObject();
            shop.earnings.forEach(earn::addProperty);
            o.add("earnings", earn);
            JsonArray col = new JsonArray();
            for (Stored st : shop.collected) {
                JsonObject co = new JsonObject();
                co.add("item", encode(st.item));
                co.addProperty("count", st.count);
                col.add(co);
            }
            o.add("collected", col);
            list.add(o);
        }
        root.add("shops", list);
        String text = GSON.toJson(root);
        writer.execute(() -> {
            try {
                Path tmp = file.resolveSibling("shops.json.tmp");
                Files.writeString(tmp, text, StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                AbpsMod.LOGGER.error("Could not save shops.json: {}", e.toString());
            }
        });
    }

    public void shutdown() {
        writer.shutdown();
        try {
            writer.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ================= Sending to clients =================

    public void sendList(ServerPlayer p) {
        if (!Service.hasMod(p)) return;
        List<Net.ShopCard> cards = new ArrayList<>();
        List<Shop> sorted = new ArrayList<>(shops.values());
        sorted.sort(Comparator.comparingInt((Shop sh) -> -sh.sales).thenComparingLong(sh -> sh.created));
        for (Shop sh : sorted) {
            List<ItemStack> preview = new ArrayList<>();
            for (Listing l : sh.listings) {
                if (preview.size() >= 5) break;
                preview.add(l.item.copyWithCount(1));
            }
            cards.add(new Net.ShopCard(sh.owner, sh.ownerName, sh.name, sh.icon.copyWithCount(1), sh.listings.size(), preview, sh.sales,
                    server.getPlayerList().getPlayer(sh.owner) != null));
        }
        Cost cost = cfg().shopCreateCost();
        ServerPlayNetworking.send(p, new Net.ShopListPayload(cards, shops.containsKey(p.getUUID()), cost.describe(p), cost.canAfford(p), cfg().shopsEnabled));
    }

    public void open(ServerPlayer p, UUID owner) {
        Shop sh = shops.get(owner);
        if (sh == null) {
            s().send(p, "<red>That shop is closed.");
            sendList(p);
            return;
        }
        if (!Service.hasMod(p)) {
            s().send(p, "<gray>Shops need <gold>AbpsMod</gold> installed on your game.");
            return;
        }
        boolean mine = sh.owner.equals(p.getUUID());
        List<Net.ShopListing> ls = new ArrayList<>();
        for (Listing l : sh.listings) {
            ls.add(new Net.ShopListing(l.id, l.selling, l.item.copyWithCount(Math.max(1, Math.min(l.bundle, l.item.getMaxStackSize()))), l.bundle,
                    new ItemStack(Inv.item(l.priceItem)), l.price, l.stock, l.funds));
        }
        List<ItemStack> earn = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        int collected = 0;
        if (mine) {
            for (Map.Entry<String, Integer> e : sh.earnings.entrySet()) {
                if (e.getValue() <= 0) continue;
                earn.add(new ItemStack(Inv.item(e.getKey())));
                counts.add(e.getValue());
            }
            for (Stored st : sh.collected) collected += st.count;
        }
        ServerPlayNetworking.send(p, new Net.ShopPayload(sh.owner, sh.ownerName, sh.name, sh.icon.copyWithCount(1), mine, ls, earn, counts,
                collected, sh.sales, cfg().shopMaxListings));
    }

    // ================= Requests from the client =================

    /** Handles "shop" actions from the menu. arg is the operation and its values joined with |. */
    public void handle(ServerPlayer p, String arg) {
        String[] a = arg.split("\\|", -1);
        String op = a.length > 0 ? a[0] : "";
        if (!cfg().shopsEnabled && !op.equals("list")) {
            s().send(p, "<red>Shops are turned off on this server.");
            return;
        }
        try {
            switch (op) {
                case "list" -> sendList(p);
                case "open" -> open(p, UUID.fromString(a[1]));
                case "create" -> create(p, a.length > 1 ? a[1] : "");
                case "rename" -> rename(p, a[1]);
                case "icon" -> icon(p, Integer.parseInt(a[1]));
                case "add" -> add(p, Integer.parseInt(a[1]), a[2].equals("1"), Integer.parseInt(a[3]), a[4], Integer.parseInt(a[5]), Integer.parseInt(a[6]));
                case "restock" -> restock(p, Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                case "withdraw" -> withdraw(p, Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                case "price" -> price(p, Integer.parseInt(a[1]), Integer.parseInt(a[2]), a[3], Integer.parseInt(a[4]));
                case "remove" -> remove(p, Integer.parseInt(a[1]));
                case "trade" -> trade(p, UUID.fromString(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                case "collect" -> collect(p);
                case "close" -> close(p);
                default -> {
                }
            }
        } catch (RuntimeException e) {
            // A malformed request from a modified client; ignore it rather than crash
            AbpsMod.LOGGER.debug("Bad shop request from {}: {}", p.getName().getString(), arg);
        }
    }

    private Shop mine(ServerPlayer p) {
        Shop sh = shops.get(p.getUUID());
        if (sh == null) s().send(p, "<red>You don't have a shop. Open one from the Shops tab.");
        return sh;
    }

    private static String cleanName(String raw, String fallback) {
        String n = raw == null ? "" : raw.replaceAll("[\\u00A7<>|]", "").trim();
        if (n.length() > MAX_NAME) n = n.substring(0, MAX_NAME);
        return n.isEmpty() ? fallback : n;
    }

    private void create(ServerPlayer p, String name) {
        if (shops.containsKey(p.getUUID())) {
            s().send(p, "<red>You already have a shop.");
            open(p, p.getUUID());
            return;
        }
        Cost cost = cfg().shopCreateCost();
        if (!cost.canAfford(p)) {
            s().send(p, "<red>Opening a shop costs " + cost.describe(p) + "<red>.");
            return;
        }
        cost.take(p);
        Shop sh = new Shop();
        sh.owner = p.getUUID();
        sh.ownerName = p.getName().getString();
        sh.name = cleanName(name, sh.ownerName + "'s Shop");
        sh.created = System.currentTimeMillis();
        shops.put(sh.owner, sh);
        save();
        AbpsMod.LOGGER.info("{} opened a shop called {}", sh.ownerName, sh.name);
        s().send(p, "<green>Your shop <white>" + sh.name + "</white> is open! Add things to sell or buy with <yellow>+ Add listing</yellow>.");
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.3f);
        open(p, sh.owner);
    }

    private void rename(ServerPlayer p, String name) {
        Shop sh = mine(p);
        if (sh == null) return;
        sh.name = cleanName(name, sh.name);
        save();
        open(p, sh.owner);
    }

    private void icon(ServerPlayer p, int slot) {
        Shop sh = mine(p);
        if (sh == null || slot < 0 || slot >= Inv.MAIN) return;
        ItemStack held = p.getInventory().getItem(slot);
        if (held.isEmpty()) return;
        sh.icon = held.copyWithCount(1);
        save();
        open(p, sh.owner);
    }

    private static boolean validCurrency(Item item) {
        return item != Items.AIR;
    }

    private void add(ServerPlayer p, int slot, boolean selling, int bundle, String priceItem, int price, int amount) {
        Shop sh = mine(p);
        if (sh == null) return;
        if (sh.listings.size() >= cfg().shopMaxListings) {
            s().send(p, "<red>Your shop is full (" + cfg().shopMaxListings + " listings). Remove one first.");
            return;
        }
        if (slot < 0 || slot >= Inv.MAIN) return;
        ItemStack template = p.getInventory().getItem(slot);
        if (template.isEmpty()) {
            s().send(p, "<red>Pick an item from your inventory first.");
            return;
        }
        Item currency = Inv.item(priceItem);
        if (!validCurrency(currency)) {
            s().send(p, "<red>That price item doesn't exist.");
            return;
        }
        bundle = Math.max(1, Math.min(MAX_BUNDLE, bundle));
        price = Math.max(1, Math.min(MAX_PRICE, price));
        amount = Math.max(0, amount);
        Listing l = new Listing();
        l.selling = selling;
        l.item = template.copyWithCount(1);
        l.bundle = bundle;
        l.priceItem = Inv.id(currency);
        l.price = price;
        // Take what goes into the listing before creating it, so nothing is made out of thin air
        if (selling) {
            if (!Inv.take(p, Inv.same(l.item), amount)) {
                s().send(p, "<red>You don't have " + amount + " of that item.");
                return;
            }
            l.stock = amount;
        } else {
            long cost = (long) amount * price;
            if (cost > Integer.MAX_VALUE || !Inv.take(p, Inv.currency(currency), (int) cost)) {
                s().send(p, "<red>You need " + cost + " " + Cost.prettyName(currency) + " to pay for " + amount + " trades.");
                return;
            }
            l.funds = (int) cost;
        }
        l.id = sh.nextId++;
        sh.listings.add(l);
        save();
        s().send(p, "<green>Listed <white>" + l.item.getHoverName().getString() + "</white>.");
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.VILLAGER_YES, 0.7f, 1.2f);
        open(p, sh.owner);
    }

    /** Sell listings: add items. Buy listings: add money for more trades. */
    private void restock(ServerPlayer p, int id, int amount) {
        Shop sh = mine(p);
        if (sh == null || amount <= 0) return;
        Listing l = sh.listing(id);
        if (l == null) return;
        if (l.selling) {
            if (!Inv.take(p, Inv.same(l.item), amount)) {
                s().send(p, "<red>You don't have " + amount + " of that item.");
                return;
            }
            l.stock += amount;
        } else {
            Item currency = Inv.item(l.priceItem);
            long cost = (long) amount * l.price;
            if (cost > Integer.MAX_VALUE - l.funds || !Inv.take(p, Inv.currency(currency), (int) cost)) {
                s().send(p, "<red>You need " + cost + " " + Cost.prettyName(currency) + ".");
                return;
            }
            l.funds += (int) cost;
        }
        save();
        open(p, sh.owner);
    }

    /** Takes items (sell listings) or unspent money (buy listings) back out. */
    private void withdraw(ServerPlayer p, int id, int amount) {
        Shop sh = mine(p);
        if (sh == null || amount <= 0) return;
        Listing l = sh.listing(id);
        if (l == null) return;
        if (l.selling) {
            amount = Math.min(amount, l.stock);
            l.stock -= amount;
            Inv.give(p, l.item, amount);
        } else {
            amount = Math.min(amount, l.funds);
            l.funds -= amount;
            Inv.give(p, new ItemStack(Inv.item(l.priceItem)), amount);
        }
        save();
        open(p, sh.owner);
    }

    private void price(ServerPlayer p, int id, int bundle, String priceItem, int price) {
        Shop sh = mine(p);
        if (sh == null) return;
        Listing l = sh.listing(id);
        if (l == null) return;
        Item currency = Inv.item(priceItem);
        if (!validCurrency(currency)) return;
        if (!l.selling && l.funds > 0 && !Inv.id(currency).equals(l.priceItem)) {
            // The money already put up is in the old currency; hand it back before switching
            Inv.give(p, new ItemStack(Inv.item(l.priceItem)), l.funds);
            l.funds = 0;
            s().send(p, "<gray>The money you put up was returned because the price item changed.");
        }
        l.bundle = Math.max(1, Math.min(MAX_BUNDLE, bundle));
        l.price = Math.max(1, Math.min(MAX_PRICE, price));
        l.priceItem = Inv.id(currency);
        save();
        open(p, sh.owner);
    }

    private void remove(ServerPlayer p, int id) {
        Shop sh = mine(p);
        if (sh == null) return;
        Listing l = sh.listing(id);
        if (l == null) return;
        sh.listings.remove(l);
        if (l.stock > 0) Inv.give(p, l.item, l.stock);
        if (l.funds > 0) Inv.give(p, new ItemStack(Inv.item(l.priceItem)), l.funds);
        save();
        s().send(p, "<gray>Listing removed. Its items came back to you.");
        open(p, sh.owner);
    }

    /** Buys from (selling listing) or sells to (buying listing) a shop, bundles times. */
    private void trade(ServerPlayer p, UUID owner, int id, int bundles) {
        Shop sh = shops.get(owner);
        if (sh == null) return;
        Listing l = sh.listing(id);
        if (l == null || bundles <= 0) return;
        if (sh.owner.equals(p.getUUID())) {
            s().send(p, "<red>You can't trade with your own shop.");
            return;
        }
        Item currency = Inv.item(l.priceItem);
        String itemName = l.item.getHoverName().getString(), money = Cost.prettyName(currency);
        long items = (long) bundles * l.bundle, cost = (long) bundles * l.price;
        if (items > Integer.MAX_VALUE || cost > Integer.MAX_VALUE) return;
        ServerPlayer ownerOnline = server.getPlayerList().getPlayer(sh.owner);
        if (l.selling) {
            // The player buys: they pay currency, get the items
            if (l.stock < items) {
                s().send(p, "<red>The shop only has " + l.stock + " left.");
                open(p, owner);
                return;
            }
            if (Inv.room(p, l.item) < items) {
                s().send(p, "<red>You don't have room for " + items + " " + itemName + ".");
                return;
            }
            if (!Inv.take(p, Inv.currency(currency), (int) cost)) {
                s().send(p, "<red>You need " + cost + " " + money + ".");
                return;
            }
            l.stock -= (int) items;
            sh.earnings.merge(l.priceItem, (int) cost, Integer::sum);
            Inv.give(p, l.item, (int) items);
            s().send(p, "<green>Bought <white>" + items + " " + itemName + "</white> for <white>" + cost + " " + money + "</white>.");
            if (ownerOnline != null) s().send(ownerOnline, "<gold>" + p.getName().getString() + "</gold> <gray>bought</gray> " + items + " " + itemName
                    + " <gray>from your shop for</gray> " + cost + " " + money + "<gray>. Collect it in your shop.");
        } else {
            // The player sells: they give the items, get currency from what the owner put up
            if (l.funds < cost) {
                s().send(p, "<red>The shop can only pay for " + (l.funds / l.price) + " more.");
                open(p, owner);
                return;
            }
            if (Inv.room(p, new ItemStack(currency)) < cost) {
                s().send(p, "<red>You don't have room for " + cost + " " + money + ".");
                return;
            }
            if (!Inv.take(p, Inv.same(l.item), (int) items)) {
                s().send(p, "<red>You need " + items + " " + itemName + " to sell.");
                return;
            }
            l.funds -= (int) cost;
            addCollected(sh, l.item, (int) items);
            Inv.give(p, new ItemStack(currency), (int) cost);
            s().send(p, "<green>Sold <white>" + items + " " + itemName + "</white> for <white>" + cost + " " + money + "</white>.");
            if (ownerOnline != null) s().send(ownerOnline, "<gold>" + p.getName().getString() + "</gold> <gray>sold your shop</gray> " + items + " " + itemName
                    + "<gray>. Collect it in your shop.");
        }
        sh.sales++;
        AbpsMod.LOGGER.info("Shop trade: {} {} {} x{} at {}'s shop for {} {}", p.getName().getString(), l.selling ? "bought" : "sold", itemName, items,
                sh.ownerName, cost, l.priceItem);
        save();
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.VILLAGER_TRADE, 0.8f, 1.1f);
        open(p, owner);
    }

    private static void addCollected(Shop sh, ItemStack item, int count) {
        for (Stored st : sh.collected) {
            if (ItemStack.isSameItemSameComponents(st.item, item)) {
                st.count += count;
                return;
            }
        }
        sh.collected.add(new Stored(item.copyWithCount(1), count));
    }

    /** Hands the owner everything the shop earned and bought. */
    private void collect(ServerPlayer p) {
        Shop sh = mine(p);
        if (sh == null) return;
        int total = 0;
        for (Map.Entry<String, Integer> e : sh.earnings.entrySet()) {
            if (e.getValue() <= 0) continue;
            Inv.give(p, new ItemStack(Inv.item(e.getKey())), e.getValue());
            total += e.getValue();
        }
        sh.earnings.clear();
        for (Stored st : sh.collected) {
            Inv.give(p, st.item, st.count);
            total += st.count;
        }
        sh.collected.clear();
        save();
        s().send(p, total == 0 ? "<gray>Nothing to collect yet." : "<green>Collected <white>" + total + "</white> items from your shop.");
        if (total > 0) Fx.sound((ServerLevel) p.level(), p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        open(p, sh.owner);
    }

    /** Closes the shop and gives everything in it back. The opening fee is not refunded. */
    private void close(ServerPlayer p) {
        Shop sh = mine(p);
        if (sh == null) return;
        for (Listing l : sh.listings) {
            if (l.stock > 0) Inv.give(p, l.item, l.stock);
            if (l.funds > 0) Inv.give(p, new ItemStack(Inv.item(l.priceItem)), l.funds);
        }
        sh.listings.clear();
        for (Map.Entry<String, Integer> e : sh.earnings.entrySet()) {
            if (e.getValue() > 0) Inv.give(p, new ItemStack(Inv.item(e.getKey())), e.getValue());
        }
        for (Stored st : sh.collected) Inv.give(p, st.item, st.count);
        shops.remove(p.getUUID());
        save();
        AbpsMod.LOGGER.info("{} closed their shop {}", sh.ownerName, sh.name);
        s().send(p, "<gray>Your shop is closed. Everything in it came back to you.");
        sendList(p);
    }

    /** Keeps the owner's name up to date for the shop list. */
    public void onJoin(ServerPlayer p) {
        Shop sh = shops.get(p.getUUID());
        if (sh == null) return;
        String now = p.getName().getString();
        if (!now.equals(sh.ownerName)) {
            sh.ownerName = now;
            save();
        }
        int waiting = 0;
        for (int c : sh.earnings.values()) waiting += c;
        for (Stored st : sh.collected) waiting += st.count;
        if (waiting > 0) s().send(p, "<gold>Your shop has <white>" + waiting + "</white> items waiting. <gray>Open the Shops tab to collect them.");
    }

    public boolean hasShop(UUID id) {
        return shops.containsKey(id);
    }
}
