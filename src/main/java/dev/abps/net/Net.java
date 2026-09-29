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
                              boolean admin)
            implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = newType("sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.classId);
                    buf.writeVarInt(p.level);
                    buf.writeVarInt(p.maxLevel);
                    for (int i = 0; i < 4; i++) buf.writeVarInt(p.unlock[i]);
                    for (int i = 0; i < 5; i++) buf.writeVarLong(p.cdLeft[i]);
                    for (int i = 0; i < 5; i++) buf.writeVarLong(p.cdTotal[i]);
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
                },
                buf -> {
                    String classId = buf.readUtf();
                    int level = buf.readVarInt();
                    int maxLevel = buf.readVarInt();
                    int[] unlock = new int[4];
                    for (int i = 0; i < 4; i++) unlock[i] = buf.readVarInt();
                    long[] left = new long[5];
                    for (int i = 0; i < 5; i++) left[i] = buf.readVarLong();
                    long[] total = new long[5];
                    for (int i = 0; i < 5; i++) total[i] = buf.readVarLong();
                    return new SyncPayload(classId, level, maxLevel, unlock, left, total, buf.readFloat(),
                            buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                            buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(),
                            buf.readBoolean(), buf.readDouble(), buf.readBoolean());
                });

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

    public static void register() {
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
