package dev.abps.net;

import dev.abps.AbpsMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Every packet between the server and the mod on the client.
 * The server makes all decisions. The client only sends key presses and button clicks.
 */
public final class Net {

    private Net() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> newType(String path) {
        return new CustomPacketPayload.Type<>(AbpsMod.id(path));
    }

    // ---- Buffer helpers ----
    static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s);
    }

    static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }

    // =================== Client -> Server ===================

    /** Use an ability. Slot 1-4, or 5 for the ultimate. */
    public record CastPayload(int slot) implements CustomPacketPayload {
        public static final Type<CastPayload> TYPE = newType("cast");
        public static final StreamCodec<RegistryFriendlyByteBuf, CastPayload> CODEC =
                CustomPacketPayload.codec((p, buf) -> buf.writeVarInt(p.slot), buf -> new CastPayload(buf.readVarInt()));

        @Override
        public Type<CastPayload> type() {
            return TYPE;
        }
    }

    /** A button in the menu, or a client-side action like a double jump. */
    public record ActionPayload(String action, String arg) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = newType("action");
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.action);
                    buf.writeUtf(p.arg);
                },
                buf -> new ActionPayload(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<ActionPayload> type() {
            return TYPE;
        }
    }

    // =================== Server -> Client ===================

    /** Everything the HUD and menu need about the player. Sent when something changes. */
    public record SyncPayload(String classId, int level, int maxLevel, int[] unlock, long[] cdLeft, long[] cdTotal,
                              float ultCharge, long ultLockLeft, long combatLeft, boolean noCooldown,
                              int abilitiesUsed, int rerolls, String upgradeCost, boolean canUpgrade,
                              String rerollCost, boolean canReroll, boolean hud, boolean panel, double cooldownCut,
                              boolean admin, int flags)
            implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = newType("sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.classId);
                    buf.writeVarInt(p.level);
                    buf.writeVarInt(p.maxLevel);
                    for (int i = 0; i < 5; i++) buf.writeVarInt(p.unlock[i]);
                    for (int i = 0; i < 7; i++) buf.writeVarLong(p.cdLeft[i]);
                    for (int i = 0; i < 7; i++) buf.writeVarLong(p.cdTotal[i]);
                    buf.writeFloat(p.ultCharge);
                    buf.writeVarLong(p.ultLockLeft);
                    buf.writeVarLong(p.combatLeft);
                    buf.writeBoolean(p.noCooldown);
                    buf.writeVarInt(p.abilitiesUsed);
                    buf.writeVarInt(p.rerolls);
                    buf.writeUtf(p.upgradeCost);
                    buf.writeBoolean(p.canUpgrade);
                    buf.writeUtf(p.rerollCost);
                    buf.writeBoolean(p.canReroll);
                    buf.writeBoolean(p.hud);
                    buf.writeBoolean(p.panel);
                    buf.writeDouble(p.cooldownCut);
                    buf.writeBoolean(p.admin);
                    buf.writeVarInt(p.flags);
                },
                buf -> {
                    String classId = buf.readUtf();
                    int level = buf.readVarInt();
                    int maxLevel = buf.readVarInt();
                    int[] unlock = new int[5];
                    for (int i = 0; i < 5; i++) unlock[i] = buf.readVarInt();
                    long[] left = new long[7];
                    for (int i = 0; i < 7; i++) left[i] = buf.readVarLong();
                    long[] total = new long[7];
                    for (int i = 0; i < 7; i++) total[i] = buf.readVarLong();
                    return new SyncPayload(classId, level, maxLevel, unlock, left, total, buf.readFloat(),
                            buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                            buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(),
                            buf.readBoolean(), buf.readDouble(), buf.readBoolean(), buf.readVarInt());
                });

        /** Bits in flags. */
        public static final int POWERS_OFF = 1, PYRO_AURA = 2;

        public boolean powersOff() {
            return (flags & POWERS_OFF) != 0;
        }

        public boolean pyroAura() {
            return (flags & PYRO_AURA) != 0;
        }

        @Override
        public Type<SyncPayload> type() {
            return TYPE;
        }
    }

    /** Info about one class, for the menu. Passives and descriptions are listed for every level. */
    public record ClassInfo(String id, String name, int color, int color2, String symbol, String tagline, String icon,
                            List<List<String>> passivesByLevel, List<String> negatives, String mastery,
                            List<String> abilityNames, List<List<String>> descsByLevel, List<Double> baseCooldowns,
                            int players) {

        static void write(RegistryFriendlyByteBuf buf, ClassInfo c) {
            buf.writeUtf(c.id);
            buf.writeUtf(c.name);
            buf.writeInt(c.color);
            buf.writeInt(c.color2);
            buf.writeUtf(c.symbol);
            buf.writeUtf(c.tagline);
            buf.writeUtf(c.icon);
            buf.writeVarInt(c.passivesByLevel.size());
            for (List<String> l : c.passivesByLevel) writeStrings(buf, l);
            writeStrings(buf, c.negatives);
            buf.writeUtf(c.mastery);
            writeStrings(buf, c.abilityNames);
            buf.writeVarInt(c.descsByLevel.size());
            for (List<String> l : c.descsByLevel) writeStrings(buf, l);
            buf.writeVarInt(c.baseCooldowns.size());
            for (double d : c.baseCooldowns) buf.writeDouble(d);
            buf.writeVarInt(c.players);
        }

        static ClassInfo read(RegistryFriendlyByteBuf buf) {
            String id = buf.readUtf(), name = buf.readUtf();
            int color = buf.readInt(), color2 = buf.readInt();
            String symbol = buf.readUtf(), tagline = buf.readUtf(), icon = buf.readUtf();
            int n = buf.readVarInt();
            List<List<String>> passives = new ArrayList<>(n);
            for (int i = 0; i < n; i++) passives.add(readStrings(buf));
            List<String> negatives = readStrings(buf);
            String mastery = buf.readUtf();
            List<String> abilities = readStrings(buf);
            int m = buf.readVarInt();
            List<List<String>> descs = new ArrayList<>(m);
            for (int i = 0; i < m; i++) descs.add(readStrings(buf));
            int k = buf.readVarInt();
            List<Double> cds = new ArrayList<>(k);
            for (int i = 0; i < k; i++) cds.add(buf.readDouble());
            return new ClassInfo(id, name, color, color2, symbol, tagline, icon, passives, negatives, mastery,
                    abilities, descs, cds, buf.readVarInt());
        }
    }

    public record CatalogPayload(List<ClassInfo> classes) implements CustomPacketPayload {
        public static final Type<CatalogPayload> TYPE = newType("catalog");
        public static final StreamCodec<RegistryFriendlyByteBuf, CatalogPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.classes.size());
                    for (ClassInfo c : p.classes) ClassInfo.write(buf, c);
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<ClassInfo> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(ClassInfo.read(buf));
                    return new CatalogPayload(list);
                });

        @Override
        public Type<CatalogPayload> type() {
            return TYPE;
        }
    }

    /** Screen effects: shake, tint or flash. */
    public record FxPayload(int kind, int color, int ticks, float strength) implements CustomPacketPayload {
        public static final Type<FxPayload> TYPE = newType("fx");
        public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeInt(p.color);
                    buf.writeVarInt(p.ticks);
                    buf.writeFloat(p.strength);
                },
                buf -> new FxPayload(buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<FxPayload> type() {
            return TYPE;
        }
    }

    /** Plays the roll animation, landing on the given class. */
    public record RollPayload(String result) implements CustomPacketPayload {
        public static final Type<RollPayload> TYPE = newType("roll");
        public static final StreamCodec<RegistryFriendlyByteBuf, RollPayload> CODEC =
                CustomPacketPayload.codec((p, buf) -> buf.writeUtf(p.result), buf -> new RollPayload(buf.readUtf()));

        @Override
        public Type<RollPayload> type() {
            return TYPE;
        }
    }

    /** A big animated banner in the middle of the screen. */
    public record BannerPayload(String title, String subtitle, int color, int ticks) implements CustomPacketPayload {
        public static final Type<BannerPayload> TYPE = newType("banner");
        public static final StreamCodec<RegistryFriendlyByteBuf, BannerPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.title);
                    buf.writeUtf(p.subtitle);
                    buf.writeInt(p.color);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new BannerPayload(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readVarInt()));

        @Override
        public Type<BannerPayload> type() {
            return TYPE;
        }
    }

    public record BoardEntry(String name, String classId, int level) {
    }

    public record BoardPayload(String filter, List<BoardEntry> entries) implements CustomPacketPayload {
        public static final Type<BoardPayload> TYPE = newType("board");
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.filter);
                    buf.writeVarInt(p.entries.size());
                    for (BoardEntry e : p.entries) {
                        buf.writeUtf(e.name);
                        buf.writeUtf(e.classId);
                        buf.writeVarInt(e.level);
                    }
                },
                buf -> {
                    String filter = buf.readUtf();
                    int n = buf.readVarInt();
                    List<BoardEntry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(new BoardEntry(buf.readUtf(), buf.readUtf(), buf.readVarInt()));
                    return new BoardPayload(filter, list);
                });

        @Override
        public Type<BoardPayload> type() {
            return TYPE;
        }
    }

    /** Tells the client to open the menu (used by !Menu). */
    public record OpenMenuPayload(String tab) implements CustomPacketPayload {
        public static final Type<OpenMenuPayload> TYPE = newType("open_menu");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuPayload> CODEC =
                CustomPacketPayload.codec((p, buf) -> buf.writeUtf(p.tab), buf -> new OpenMenuPayload(buf.readUtf()));

        @Override
        public Type<OpenMenuPayload> type() {
            return TYPE;
        }
    }

    /** Players hidden by the Assassin's Vanish. Clients with the mod don't draw them at all. */
    public record VanishPayload(List<java.util.UUID> ids) implements CustomPacketPayload {
        public static final Type<VanishPayload> TYPE = newType("vanish");
        public static final StreamCodec<RegistryFriendlyByteBuf, VanishPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.ids.size());
                    for (java.util.UUID id : p.ids) buf.writeUUID(id);
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<java.util.UUID> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(buf.readUUID());
                    return new VanishPayload(list);
                });

        @Override
        public Type<VanishPayload> type() {
            return TYPE;
        }
    }

    /**
     * One custom visual effect for the client mod to draw with its own particle engine. The kind says which
     * effect it is (see Vfx), d holds the numbers (positions, sizes) and i holds the whole numbers (counts, colors).
     */
    public record VfxPayload(int kind, double[] d, int[] i) implements CustomPacketPayload {
        public static final Type<VfxPayload> TYPE = newType("vfx");
        public static final StreamCodec<RegistryFriendlyByteBuf, VfxPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeVarInt(p.d.length);
                    for (double v : p.d) buf.writeFloat((float) v);
                    buf.writeVarInt(p.i.length);
                    for (int v : p.i) buf.writeInt(v);
                },
                buf -> {
                    int kind = buf.readVarInt();
                    double[] d = new double[Math.min(64, buf.readVarInt())];
                    for (int k = 0; k < d.length; k++) d[k] = buf.readFloat();
                    int[] i = new int[Math.min(64, buf.readVarInt())];
                    for (int k = 0; k < i.length; k++) i[k] = buf.readInt();
                    return new VfxPayload(kind, d, i);
                });

        @Override
        public Type<VfxPayload> type() {
            return TYPE;
        }
    }

    // =================== Shops, travel, profile ===================

    /** One shop in the shop list. */
    public record ShopCard(java.util.UUID owner, String ownerName, String name, net.minecraft.world.item.ItemStack icon, int listings,
                           List<net.minecraft.world.item.ItemStack> preview, int sales, boolean online) {
    }

    /** Every shop on the server, and whether you can open one. */
    public record ShopListPayload(List<ShopCard> shops, boolean hasShop, String createCost, boolean canCreate, boolean enabled)
            implements CustomPacketPayload {
        public static final Type<ShopListPayload> TYPE = newType("shop_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopListPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.shops.size());
                    for (ShopCard c : p.shops) {
                        buf.writeUUID(c.owner);
                        buf.writeUtf(c.ownerName);
                        buf.writeUtf(c.name);
                        net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.icon);
                        buf.writeVarInt(c.listings);
                        net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, c.preview);
                        buf.writeVarInt(c.sales);
                        buf.writeBoolean(c.online);
                    }
                    buf.writeBoolean(p.hasShop);
                    buf.writeUtf(p.createCost);
                    buf.writeBoolean(p.canCreate);
                    buf.writeBoolean(p.enabled);
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<ShopCard> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new ShopCard(buf.readUUID(), buf.readUtf(), buf.readUtf(), net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                                buf.readVarInt(), net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean()));
                    }
                    return new ShopListPayload(list, buf.readBoolean(), buf.readUtf(), buf.readBoolean(), buf.readBoolean());
                });

        @Override
        public Type<ShopListPayload> type() {
            return TYPE;
        }
    }

    /**
     * One thing a shop trades. selling: the owner sells item to players. Otherwise the owner buys it from players.
     * item is shown at the bundle size; price is how many of priceItem one bundle costs.
     */
    public record ShopListing(int id, boolean selling, net.minecraft.world.item.ItemStack item, int bundle,
                              net.minecraft.world.item.ItemStack priceItem, int price, int stock, int funds) {
    }

    /** The inside of one shop. earnings and collected are only filled in for the owner. */
    public record ShopPayload(java.util.UUID owner, String ownerName, String name, net.minecraft.world.item.ItemStack icon, boolean mine,
                              List<ShopListing> listings, List<net.minecraft.world.item.ItemStack> earnings, List<Integer> earningCounts,
                              int collected, int sales, int maxListings) implements CustomPacketPayload {
        public static final Type<ShopPayload> TYPE = newType("shop");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUUID(p.owner);
                    buf.writeUtf(p.ownerName);
                    buf.writeUtf(p.name);
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.icon);
                    buf.writeBoolean(p.mine);
                    buf.writeVarInt(p.listings.size());
                    for (ShopListing l : p.listings) {
                        buf.writeVarInt(l.id);
                        buf.writeBoolean(l.selling);
                        net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, l.item);
                        buf.writeVarInt(l.bundle);
                        net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, l.priceItem);
                        buf.writeVarInt(l.price);
                        buf.writeVarInt(l.stock);
                        buf.writeVarInt(l.funds);
                    }
                    net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, p.earnings);
                    buf.writeVarInt(p.earningCounts.size());
                    for (int c : p.earningCounts) buf.writeVarInt(c);
                    buf.writeVarInt(p.collected);
                    buf.writeVarInt(p.sales);
                    buf.writeVarInt(p.maxListings);
                },
                buf -> {
                    java.util.UUID owner = buf.readUUID();
                    String ownerName = buf.readUtf(), name = buf.readUtf();
                    net.minecraft.world.item.ItemStack icon = net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    boolean mine = buf.readBoolean();
                    int n = buf.readVarInt();
                    List<ShopListing> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new ShopListing(buf.readVarInt(), buf.readBoolean(), net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                                buf.readVarInt(), net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                    }
                    List<net.minecraft.world.item.ItemStack> earnings = net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
                    int m = buf.readVarInt();
                    List<Integer> counts = new ArrayList<>(m);
                    for (int i = 0; i < m; i++) counts.add(buf.readVarInt());
                    return new ShopPayload(owner, ownerName, name, icon, mine, list, earnings, counts, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                });

        @Override
        public Type<ShopPayload> type() {
            return TYPE;
        }
    }

    /** A saved home, for the Travel tab. */
    public record HomeInfo(String name, String dimension, int x, int y, int z) {
    }

    /** Everything the Travel tab shows: homes, spawn, back, who is online and who asked to teleport. */
    public record TravelPayload(List<HomeInfo> homes, int maxHomes, boolean hasBack, List<String> online, List<String> requests,
                                long combatLeft, long cooldownLeft) implements CustomPacketPayload {
        public static final Type<TravelPayload> TYPE = newType("travel");
        public static final StreamCodec<RegistryFriendlyByteBuf, TravelPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.homes.size());
                    for (HomeInfo h : p.homes) {
                        buf.writeUtf(h.name);
                        buf.writeUtf(h.dimension);
                        buf.writeVarInt(h.x);
                        buf.writeVarInt(h.y);
                        buf.writeVarInt(h.z);
                    }
                    buf.writeVarInt(p.maxHomes);
                    buf.writeBoolean(p.hasBack);
                    writeStrings(buf, p.online);
                    writeStrings(buf, p.requests);
                    buf.writeVarLong(p.combatLeft);
                    buf.writeVarLong(p.cooldownLeft);
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<HomeInfo> homes = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) homes.add(new HomeInfo(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                    return new TravelPayload(homes, buf.readVarInt(), buf.readBoolean(), readStrings(buf), readStrings(buf), buf.readVarLong(), buf.readVarLong());
                });

        @Override
        public Type<TravelPayload> type() {
            return TYPE;
        }
    }

    /**
     * The Profile tab: fight stats, playtime and the daily reward streak. Stats are sent as label/value pairs so new
     * ones can be added on the server without changing the client.
     */
    /** One dungeon in the Dungeons tab. best is a time in ms, or the best wave for the arena. */
    public record DungeonCard(String id, String name, String blurb, String mode, int difficulty, String icon, int color, int clears, long best,
                              List<String> board) {
    }

    /** Everything the Dungeons tab shows: the dungeons, your party, invites and your titles. */
    public record DungeonsPayload(boolean enabled, List<DungeonCard> cards, List<String> party, boolean leader, String inviteFrom, String inRun,
                                  List<String> online, List<String> titles, String title) implements CustomPacketPayload {
        public static final Type<DungeonsPayload> TYPE = newType("dungeons");
        public static final StreamCodec<RegistryFriendlyByteBuf, DungeonsPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBoolean(p.enabled);
                    buf.writeVarInt(p.cards.size());
                    for (DungeonCard c : p.cards) {
                        buf.writeUtf(c.id);
                        buf.writeUtf(c.name);
                        buf.writeUtf(c.blurb);
                        buf.writeUtf(c.mode);
                        buf.writeVarInt(c.difficulty);
                        buf.writeUtf(c.icon);
                        buf.writeInt(c.color);
                        buf.writeVarInt(c.clears);
                        buf.writeVarLong(c.best);
                        writeStrings(buf, c.board);
                    }
                    writeStrings(buf, p.party);
                    buf.writeBoolean(p.leader);
                    buf.writeUtf(p.inviteFrom);
                    buf.writeUtf(p.inRun);
                    writeStrings(buf, p.online);
                    writeStrings(buf, p.titles);
                    buf.writeUtf(p.title);
                },
                buf -> {
                    boolean enabled = buf.readBoolean();
                    int n = buf.readVarInt();
                    List<DungeonCard> cards = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        cards.add(new DungeonCard(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readInt(),
                                buf.readVarInt(), buf.readVarLong(), readStrings(buf)));
                    }
                    List<String> party = readStrings(buf);
                    boolean leader = buf.readBoolean();
                    String invite = buf.readUtf(), inRun = buf.readUtf();
                    return new DungeonsPayload(enabled, cards, party, leader, invite, inRun, readStrings(buf), readStrings(buf), buf.readUtf());
                });

        @Override
        public Type<DungeonsPayload> type() {
            return TYPE;
        }
    }

    /** The small dungeon panel at the top of the screen during a run. */
    public record DungeonHudPayload(boolean active, String name, int room, int rooms, String objective, int downsLeft, long elapsed, int wave, int color)
            implements CustomPacketPayload {
        public static final Type<DungeonHudPayload> TYPE = newType("dungeon_hud");
        public static final StreamCodec<RegistryFriendlyByteBuf, DungeonHudPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBoolean(p.active);
                    buf.writeUtf(p.name);
                    buf.writeVarInt(p.room);
                    buf.writeVarInt(p.rooms);
                    buf.writeUtf(p.objective);
                    buf.writeVarInt(p.downsLeft);
                    buf.writeVarLong(p.elapsed);
                    buf.writeVarInt(p.wave);
                    buf.writeInt(p.color);
                },
                buf -> new DungeonHudPayload(buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarInt(), buf.readInt()));

        @Override
        public Type<DungeonHudPayload> type() {
            return TYPE;
        }
    }

    public record ProfilePayload(String name, List<String> labels, List<String> values, int streak, boolean canClaim,
                                 long nextClaimIn, List<List<net.minecraft.world.item.ItemStack>> rewards, boolean dailyEnabled)
            implements CustomPacketPayload {
        public static final Type<ProfilePayload> TYPE = newType("profile");
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfilePayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.name);
                    writeStrings(buf, p.labels);
                    writeStrings(buf, p.values);
                    buf.writeVarInt(p.streak);
                    buf.writeBoolean(p.canClaim);
                    buf.writeVarLong(p.nextClaimIn);
                    buf.writeVarInt(p.rewards.size());
                    for (List<net.minecraft.world.item.ItemStack> r : p.rewards) net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, r);
                    buf.writeBoolean(p.dailyEnabled);
                },
                buf -> {
                    String name = buf.readUtf();
                    List<String> labels = readStrings(buf), values = readStrings(buf);
                    int streak = buf.readVarInt();
                    boolean canClaim = buf.readBoolean();
                    long next = buf.readVarLong();
                    int n = buf.readVarInt();
                    List<List<net.minecraft.world.item.ItemStack>> rewards = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) rewards.add(net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf));
                    return new ProfilePayload(name, labels, values, streak, canClaim, next, rewards, buf.readBoolean());
                });

        @Override
        public Type<ProfilePayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(ShopListPayload.TYPE, ShopListPayload.CODEC, 1024 * 1024);
        PayloadTypeRegistry.clientboundPlay().registerLarge(ShopPayload.TYPE, ShopPayload.CODEC, 1024 * 1024);
        PayloadTypeRegistry.clientboundPlay().register(TravelPayload.TYPE, TravelPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ProfilePayload.TYPE, ProfilePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(DungeonsPayload.TYPE, DungeonsPayload.CODEC, 1024 * 1024);
        PayloadTypeRegistry.clientboundPlay().register(DungeonHudPayload.TYPE, DungeonHudPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VfxPayload.TYPE, VfxPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VanishPayload.TYPE, VanishPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CastPayload.TYPE, CastPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(CatalogPayload.TYPE, CatalogPayload.CODEC, 4 * 1024 * 1024);
        PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RollPayload.TYPE, RollPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BannerPayload.TYPE, BannerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BoardPayload.TYPE, BoardPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.TYPE, OpenMenuPayload.CODEC);
    }
}
