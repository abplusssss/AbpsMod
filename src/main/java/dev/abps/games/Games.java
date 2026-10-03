package dev.abps.games;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.dungeon.Builder;
import dev.abps.dungeon.Dungeons;
import dev.abps.dungeon.Party;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Starts, runs and tracks the party activities (Siege and the party games) in the arena world. */
public final class Games {

    private Games() {
    }

    private static final Map<Integer, Game> GAMES = new HashMap<>();
    private static final Map<Integer, Builder> TEARDOWN = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Per game id: player name to best wave (Siege) or total wins (party games). */
    private static Map<String, Map<String, Long>> boards = new LinkedHashMap<>();

    /** Siege kills, tower blueprints and no friendly fire in Capture the Flag. */
    public static void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof net.minecraft.world.entity.Mob mob) || !mob.entityTags().contains(Siege.MOB_TAG)) return;
            for (Game g : GAMES.values()) if (g instanceof Siege s && g.level == mob.level()) s.onKill(mob);
        });
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
                player instanceof ServerPlayer p && use(p, hand) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            if (!(victim instanceof ServerPlayer v) || !(source.getEntity() instanceof ServerPlayer a) || a == v) return true;
            Game g = gameOf(v);
            return !(g instanceof CaptureTheFlag ctf) || ctf.allowHit(a, v);
        });
    }

    /** Right-click with a game item (Siege blueprints). True if it was used. */
    public static boolean use(ServerPlayer p, net.minecraft.world.InteractionHand hand) {
        Game g = gameOf(p);
        return g instanceof Siege s && g.state == Game.State.ACTIVE && g.closing < 0 && s.use(p, p.getItemInHand(hand));
    }

    /** Siege blueprints keep working when right-clicked on a block in the arena world (checked on both sides). */
    public static boolean isBlueprint(net.minecraft.world.item.ItemStack s) {
        return Siege.TowerType.of(s) != null;
    }

    public static Game gameOf(ServerPlayer p) {
        for (Game g : GAMES.values()) if (g.players.contains(p.getUUID()) && g.state != Game.State.OVER) return g;
        return null;
    }

    private static void tell(ServerPlayer p, String msg) {
        AbpsMod.service().send(p, msg);
    }

    // ------------------------------------------------------------------ starting and leaving

    public static void start(ServerPlayer leader, String id) {
        GameDef def = GameDef.get(id);
        if (def == null) {
            tell(leader, "<red>There is no game called " + id + ". <gray>Pick one from the <yellow>Party</yellow> tab.");
            return;
        }
        if (!AbpsMod.config().dungeonsEnabled) {
            tell(leader, "<red>Party games are turned off on this server.");
            return;
        }
        ServerLevel level = Dungeons.level();
        if (level == null) {
            tell(leader, "<red>The arena world is missing. Restart the server once after installing this version.");
            return;
        }
        Party party = Party.ensure(leader);
        if (!party.isLeader(leader)) {
            tell(leader, "<red>Only the party leader can start.");
            return;
        }
        List<ServerPlayer> members = party.online();
        if (members.size() < def.minPlayers()) {
            tell(leader, "<red>" + def.name() + " needs at least " + def.minPlayers() + " players. <gray>Invite people from the Party tab.");
            return;
        }
        for (ServerPlayer m : members) {
            if (gameOf(m) != null || Dungeons.runOf(m) != null) {
                tell(leader, "<red>" + m.getName().getString() + " is already in a game.");
                return;
            }
            if (AbpsMod.data().get(m).inCombat()) {
                tell(leader, "<red>" + m.getName().getString() + " is in combat.");
                return;
            }
        }
        int slot = 0;
        while (GAMES.containsKey(slot) || TEARDOWN.containsKey(slot)) slot++;
        Game g = switch (def.kind()) {
            case SIEGE -> new Siege(slot, def, level, members);
            case CTF -> new CaptureTheFlag(slot, def, level, members);
            case KOTH -> new KingOfTheHill(slot, def, level, members);
            case SPLEEF -> new Spleef(slot, def, level, members);
        };
        g.build();
        GAMES.put(slot, g);
        for (ServerPlayer m : members) tell(m, "<gray>Building <white>" + def.name() + "</white>... you'll be pulled in when it's ready.");
    }

    public static boolean leave(ServerPlayer p) {
        Game g = gameOf(p);
        if (g == null) return false;
        g.players.remove(p.getUUID());
        g.onLeave(p);
        if (p.level() == g.level) g.sendHome(p);
        tell(p, "<gray>You left " + g.def.name() + ".");
        g.tell("<gray>" + p.getName().getString() + " left.");
        return true;
    }

    static void teardown(int slot, Builder b) {
        GAMES.remove(slot);
        TEARDOWN.put(slot, b);
    }

    public static void tick() {
        ServerLevel level = Dungeons.level();
        if (level == null) return;
        for (Game g : new ArrayList<>(GAMES.values())) {
            if (g.state == Game.State.BUILDING) {
                if (g.builder.tick(level)) {
                    g.state = Game.State.ACTIVE;
                    g.startedAt = System.currentTimeMillis();
                    for (UUID id : g.players) {
                        ServerPlayer p = AbpsMod.server().getPlayerList().getPlayer(id);
                        if (p != null) g.enter(p);
                    }
                    g.begin();
                }
                continue;
            }
            // Anyone who teleported out some other way is out of the game
            for (UUID id : new ArrayList<>(g.players)) {
                ServerPlayer p = AbpsMod.server().getPlayerList().getPlayer(id);
                if (p == null || (p.level() != level && g.ticks > 40)) {
                    g.players.remove(id);
                    if (p != null) g.onLeave(p);
                }
            }
            if (g.players.isEmpty() && g.state == Game.State.ACTIVE) {
                g.end();
                continue;
            }
            g.tick();
        }
        for (Map.Entry<Integer, Builder> e : new ArrayList<>(TEARDOWN.entrySet())) {
            if (e.getValue().tick(level)) TEARDOWN.remove(e.getKey());
        }
    }

    /** Called instead of dying. False when the death became a respawn. */
    public static boolean allowDeath(ServerPlayer p) {
        Game g = gameOf(p);
        if (g == null || p.level() != g.level || g.state != Game.State.ACTIVE) return true;
        return g.allowDeath(p);
    }

    public static boolean canBreak(ServerPlayer p, BlockPos pos) {
        Game g = gameOf(p);
        return g != null && g.state == Game.State.ACTIVE && g.closing < 0 && g.canBreak(p, pos);
    }

    public static boolean inGame(ServerPlayer p) {
        return gameOf(p) != null;
    }

    public static void onQuit(ServerPlayer p) {
        Game g = gameOf(p);
        if (g == null) return;
        g.players.remove(p.getUUID());
        g.onLeave(p);
        g.tell("<gray>" + p.getName().getString() + " left.");
        g.sendHome(p);
    }

    public static void shutdown() {
        for (Game g : new ArrayList<>(GAMES.values())) g.end();
        ServerLevel level = Dungeons.level();
        if (level != null) for (Builder b : TEARDOWN.values()) while (!b.tick(level)) ;
        TEARDOWN.clear();
        save();
    }

    // ------------------------------------------------------------------ records

    /** Records a Siege result: the best wave each player reached. */
    static void recordWave(Game g, int wave) {
        Map<String, Long> b = boards.computeIfAbsent(g.def.id(), k -> new LinkedHashMap<>());
        for (ServerPlayer p : g.online()) {
            b.merge(p.getName().getString(), (long) wave, Math::max);
            PlayerData d = AbpsMod.data().get(p);
            d.dungeonBest.merge(g.def.id(), (long) wave, Math::max);
            d.dungeonClears.merge(g.def.id(), 1, Integer::sum);
            AbpsMod.data().save(p, d);
        }
        save();
    }

    /** Records a party game win. */
    static void recordWin(GameDef def, ServerPlayer p) {
        boards.computeIfAbsent(def.id(), k -> new LinkedHashMap<>()).merge(p.getName().getString(), 1L, Long::sum);
        PlayerData d = AbpsMod.data().get(p);
        d.dungeonClears.merge(def.id(), 1, Integer::sum);
        AbpsMod.data().save(p, d);
        save();
    }

    public static List<String> board(GameDef def) {
        Map<String, Long> b = boards.getOrDefault(def.id(), Map.of());
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(b.entrySet());
        sorted.sort((a, c) -> Long.compare(c.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            var e = sorted.get(i);
            out.add((i + 1) + ". " + e.getKey() + "  " + (def.coop() ? "wave " + e.getValue() : e.getValue() + " win" + (e.getValue() == 1 ? "" : "s")));
        }
        return out;
    }

    private static Path file() {
        return AbpsMod.data().root().resolve("games.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, Map<String, Long>> m = GSON.fromJson(Files.readString(f), new TypeToken<LinkedHashMap<String, LinkedHashMap<String, Long>>>() {
                }.getType());
                if (m != null) boards = m;
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not read game records: {}", e.toString());
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(boards));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not save game records: {}", e.toString());
        }
    }
}
