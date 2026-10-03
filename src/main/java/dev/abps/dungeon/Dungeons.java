package dev.abps.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Vfx;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Starts, runs and ends dungeon runs, and keeps the dungeon leaderboards. */
public final class Dungeons {

    public static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION, AbpsMod.id("dungeon"));
    /** The effect theme for dungeon cues (see SigDungeon on the client). */
    public static final int THEME = 17;

    // Cues the client draws
    public static final int CUE_SPAWN = 11, CUE_CLEAR = 12, CUE_LOOT = 13, CUE_WIN = 14, CUE_TRAP_MARK = 15, CUE_TRAP_BURST = 16,
            CUE_RING_WARN = 17, CUE_RING_HIT = 18, CUE_LINE_WARN = 19, CUE_SOUL_LANCE = 21, CUE_ICE_SPIKES = 22, CUE_METEOR = 23,
            CUE_BLIZZARD = 24, CUE_FLAME_WAVE = 25, CUE_ROAR = 26, CUE_GATE = 27;

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final Map<Integer, Builder> TEARDOWN = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Best clear times per dungeon and best waves in the arena. */
    public record Entry(String names, long value, int size) {
    }

    private static Map<String, List<Entry>> boards = new LinkedHashMap<>();

    private Dungeons() {
    }

    /** Fake players the self test runs through dungeons. They aren't in the server's player list. */
    static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();

    /** An online player by id, or a self test player. */
    static ServerPlayer player(UUID id) {
        ServerPlayer p = AbpsMod.server().getPlayerList().getPlayer(id);
        return p != null ? p : TEST_PLAYERS.get(id);
    }

    public static ServerLevel level() {
        return AbpsMod.server().getLevel(WORLD);
    }

    public static Run runOf(ServerPlayer p) {
        for (Run r : RUNS.values()) if (r.players.contains(p.getUUID()) && r.state != Run.State.OVER) return r;
        return null;
    }

    public static boolean inDungeon(Entity e) {
        return e.level().dimension() == WORLD;
    }

    private static void tell(ServerPlayer p, String msg) {
        AbpsMod.service().send(p, msg);
    }

    // ------------------------------------------------------------------ starting and leaving

    public static void start(ServerPlayer leader, String id) {
        // Dungeons were replaced by Siege and the party games
        dev.abps.games.Games.start(leader, id);
    }

    /** The old dungeon start, kept for the self test. */
    static void startDungeon(ServerPlayer leader, String id) {
        DungeonDef def = DungeonDef.get(id);
        if (def == null) {
            tell(leader, "<red>There is no dungeon called " + id + ".");
            return;
        }
        if (!AbpsMod.config().dungeonsEnabled) {
            tell(leader, "<red>Dungeons are turned off on this server.");
            return;
        }
        ServerLevel level = level();
        if (level == null) {
            tell(leader, "<red>The dungeon world is missing. Restart the server once after installing this version.");
            return;
        }
        Party party = Party.ensure(leader);
        if (!party.isLeader(leader)) {
            tell(leader, "<red>Only the party leader can start a dungeon.");
            return;
        }
        List<ServerPlayer> members = party.online();
        for (ServerPlayer m : members) {
            if (runOf(m) != null) {
                tell(leader, "<red>" + m.getName().getString() + " is already in a dungeon.");
                return;
            }
            if (AbpsMod.data().get(m).inCombat()) {
                tell(leader, "<red>" + m.getName().getString() + " is in combat.");
                return;
            }
        }
        int slot = 0;
        while (RUNS.containsKey(slot) || TEARDOWN.containsKey(slot)) slot++;
        Run run = new Run(slot, def, level, members);
        RUNS.put(slot, run);
        for (ServerPlayer m : members) {
            tell(m, "<gray>Building <white>" + def.name() + "</white>... you'll be pulled in when it's ready.");
        }
    }

    public static void leave(ServerPlayer p) {
        if (dev.abps.games.Games.leave(p)) return;
        Run r = runOf(p);
        if (r == null) {
            tell(p, "<red>You're not in a game.");
            return;
        }
        r.players.remove(p.getUUID());
        if (p.level() == r.level) r.sendHome(p);
        tell(p, "<gray>You left " + r.def.name() + ".");
        if (r.def.mode() == DungeonDef.Mode.WAVES && r.players.isEmpty()) r.fail();
        r.tell("<gray>" + p.getName().getString() + " left the dungeon.");
    }

    static void teardown(int slot, Builder b) {
        RUNS.remove(slot);
        TEARDOWN.put(slot, b);
    }

    public static void tick() {
        dev.abps.games.Games.tick();
        ServerLevel level = level();
        if (level == null) return;
        for (Run r : new ArrayList<>(RUNS.values())) {
            if (r.state == Run.State.BUILDING) {
                if (r.builder.tick(level)) {
                    r.state = Run.State.ACTIVE;
                    r.startedAt = System.currentTimeMillis();
                    for (UUID id : r.players) {
                        ServerPlayer p = Dungeons.player(id);
                        if (p != null) r.enter(p);
                    }
                }
                continue;
            }
            // People who teleported out some other way are out of the run
            for (UUID id : new ArrayList<>(r.players)) {
                ServerPlayer p = Dungeons.player(id);
                if (p != null && p.level() != level && r.ticks > 40) r.players.remove(id);
            }
            r.tick();
            if (r.ticks % 20 == 0 && r.state != Run.State.OVER) sendHud(r);
        }
        for (Map.Entry<Integer, Builder> e : new ArrayList<>(TEARDOWN.entrySet())) {
            if (e.getValue().tick(level)) TEARDOWN.remove(e.getKey());
        }
        // Anyone standing in the dungeon world without a run (after a restart, say) goes back to spawn
        if (level.getGameTime() % 40 == 0) {
            for (ServerPlayer p : level.players()) {
                if (runOf(p) == null && !dev.abps.games.Games.inGame(p) && !p.isCreative() && !p.isSpectator()) {
                    dev.abps.data.PlayerData.Loc spawn = dev.abps.Teleports.spawn();
                    ServerLevel l = dev.abps.Teleports.levelOf(spawn);
                    if (l != null) p.teleportTo(l, spawn.x(), spawn.y(), spawn.z(), java.util.Set.of(), spawn.yaw(), spawn.pitch(), false);
                }
            }
        }
    }

    /** Called instead of dying. Returns false when the death was turned into being downed. */
    public static boolean allowDeath(ServerPlayer p) {
        if (!dev.abps.games.Games.allowDeath(p)) return false;
        Run r = runOf(p);
        if (r == null || p.level() != r.level || r.state != Run.State.ACTIVE) return true;
        return r.down(p);
    }

    public static void onQuit(ServerPlayer p) {
        Run r = runOf(p);
        if (r != null) {
            r.players.remove(p.getUUID());
            r.tell("<gray>" + p.getName().getString() + " left the dungeon.");
            // Put them somewhere safe for when they log back in
            r.sendHome(p);
        }
        dev.abps.games.Games.onQuit(p);
        Party.onQuit(p);
    }

    public static void shutdown() {
        dev.abps.games.Games.shutdown();
        for (Run r : new ArrayList<>(RUNS.values())) r.end(false);
        ServerLevel level = level();
        if (level != null) for (Builder b : TEARDOWN.values()) while (!b.tick(level)) ;
        TEARDOWN.clear();
        save();
        Gates.save();
    }

    // ------------------------------------------------------------------ menu

    /** Sends the Dungeons tab to a player. */
    public static void sendMenu(ServerPlayer p) {
        PlayerData d = AbpsMod.data().get(p);
        List<dev.abps.net.Net.DungeonCard> cards = new ArrayList<>();
        for (dev.abps.games.GameDef def : dev.abps.games.GameDef.ALL.values()) {
            cards.add(new dev.abps.net.Net.DungeonCard(def.id(), def.name(), def.blurb(), def.kind().name(), def.difficulty(), def.icon(), def.color(),
                    d.dungeonClears.getOrDefault(def.id(), 0), d.dungeonBest.getOrDefault(def.id(), 0L), dev.abps.games.Games.board(def)));
        }
        Party party = Party.of(p);
        List<String> members = new ArrayList<>();
        if (party != null) {
            for (UUID id : party.ids()) {
                ServerPlayer m = Dungeons.player(id);
                members.add(m == null ? "?" : m.getName().getString());
            }
        } else {
            members.add(p.getName().getString());
        }
        Party invited = Party.invitedTo(p);
        String from = "";
        if (invited != null) {
            ServerPlayer l = Dungeons.player(invited.leader());
            from = l == null ? "" : l.getName().getString();
        }
        List<String> online = new ArrayList<>();
        for (ServerPlayer o : AbpsMod.server().getPlayerList().getPlayers()) if (o != p) online.add(o.getName().getString());
        var game = dev.abps.games.Games.gameOf(p);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.abps.net.Net.DungeonsPayload(AbpsMod.config().dungeonsEnabled, cards, members,
                party == null || party.isLeader(p), from, game == null ? "" : game.def.name(), online, new ArrayList<>(d.titles), d.title == null ? "" : d.title));
    }

    /** "dungeon" actions from the menu: list, start|id, leave, invite|name, accept, decline, kick|name, partyleave, title|name. */
    public static void handle(ServerPlayer p, String arg) {
        String[] a = arg.split("[|]", 2);
        String op = a[0], v = a.length > 1 ? a[1] : "";
        switch (op) {
            case "list" -> {
                sendMenu(p);
                return;
            }
            case "start" -> start(p, v);
            case "leave" -> leave(p);
            case "invite" -> {
                ServerPlayer to = AbpsMod.server().getPlayerList().getPlayerByName(v);
                if (to == null) tell(p, "<red>No player called " + v + " is online.");
                else Party.invite(p, to);
            }
            case "accept" -> Party.accept(p);
            case "decline" -> Party.decline(p);
            case "kick" -> {
                ServerPlayer who = AbpsMod.server().getPlayerList().getPlayerByName(v);
                if (who != null) Party.kick(p, who);
            }
            case "partyleave" -> Party.leave(p, true);
            case "title" -> {
                PlayerData d = AbpsMod.data().get(p);
                if (v.isEmpty() || d.titles.contains(v)) {
                    d.title = v;
                    AbpsMod.data().save(p, d);
                    AbpsMod.service().updateTags(p);
                    tell(p, v.isEmpty() ? "<gray>Title hidden." : "<gray>Your title is now <gold>" + v + "</gold>.");
                }
            }
            default -> {
            }
        }
        dev.abps.util.Tasks.later(2, () -> {
            if (!p.isRemoved()) sendMenu(p);
        });
    }

    /** The dungeon panel for everyone in a run, once a second. */
    private static void sendHud(Run r) {
        long elapsed = r.startedAt == 0 ? 0 : (r.state == Run.State.CLEARED ? r.finishedAt : System.currentTimeMillis()) - r.startedAt;
        Run.Room room = r.room();
        String obj = switch (r.state) {
            case CLEARED -> "Cleared! Step onto the light to leave";
            case BUILDING -> "Building...";
            default -> switch (room.type) {
                case START -> "Head through the door";
                case COMBAT -> room.started ? "Clear the room (" + r.alive(room) + " left)" : "Enter the next room";
                case ELITE -> room.started ? "Defeat the elite (" + r.alive(room) + " left)" : "Enter the next room";
                case TRAP -> room.started ? "Survive the traps" : "Enter the next room";
                case TREASURE -> "Take the treasure";
                case BOSS -> room.started ? "Final hall: wave " + Math.max(1, r.finalWave) + "/" + r.finalWaves + " (" + r.alive(room) + " left)" : "The final hall waits ahead";
                case SHRINE -> "Step on a pedestal to pick a blessing";
                case MINIBOSS -> room.started ? "Defeat the champion (" + r.alive(room) + " left)" : "Enter the next room";
                case ARENA -> "Survive wave " + Math.max(1, r.wave);
            };
        };
        int rooms = r.rooms.size();
        var payload = new dev.abps.net.Net.DungeonHudPayload(true, r.def.name(), Math.min(rooms, r.current + 1), rooms, obj,
                Math.max(0, r.maxDowns - r.downs), elapsed, r.wave, r.def.color());
        for (ServerPlayer p : r.online()) {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, dev.abps.net.Net.DungeonHudPayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, payload);
        }
    }

    static void hideHud(ServerPlayer p) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, dev.abps.net.Net.DungeonHudPayload.TYPE))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.abps.net.Net.DungeonHudPayload(false, "", 0, 0, "", 0, 0, 0, 0));
    }

    // ------------------------------------------------------------------ effects

    static void cue(Run run, int cue, Vec3 from, Vec3 to, Entity a, int extra) {
        int outer = Vfx.theme(THEME);
        try {
            int c2 = run.boss != null ? run.boss.color : run.def.color();
            Vec3 dir = to.subtract(from);
            boolean drawn = Vfx.cue(run.level, cue, from, dir.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : dir.normalize(), to, a, null, run.def.color(), c2, extra);
            if (!drawn) fallback(run, cue, from, to, extra);
        } finally {
            Vfx.theme(outer);
        }
    }

    /** Nobody nearby has the client mod: outline the boss warnings in red dust so they can still dodge. */
    private static void fallback(Run run, int cue, Vec3 from, Vec3 to, int warn) {
        if (cue != CUE_RING_WARN && cue != CUE_LINE_WARN) return;
        var red = dev.abps.util.Fx.dust(0xFF3B30, 1.2f);
        Vec3 a = from.add(0, 0.15, 0), b = to.add(0, 0.15, 0);
        double r = from.distanceTo(to);
        dev.abps.util.Tasks.repeat(Math.max(1, warn / 5), 5, step -> {
            if (run.state == Run.State.OVER) return;
            if (cue == CUE_RING_WARN) dev.abps.util.Fx.ring(run.level, red, a, r, (int) Math.max(12, r * 8));
            else dev.abps.util.Fx.line(run.level, red, a, b, 0.5);
        });
    }

    // ------------------------------------------------------------------ records and titles

    private static Path file() {
        return AbpsMod.data().root().resolve("dungeons.json");
    }

    public static void load() {
        Gates.load();
        dev.abps.games.Games.load();
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, List<Entry>> m = GSON.fromJson(Files.readString(f), new TypeToken<LinkedHashMap<String, List<Entry>>>() {
                }.getType());
                if (m != null) boards = m;
            }
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not read dungeon records: {}", e.toString());
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(boards));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not save dungeon records: {}", e.toString());
        }
    }

    public static List<Entry> board(String id) {
        return boards.getOrDefault(id, List.of());
    }

    private static String names(Run run) {
        List<String> n = new ArrayList<>();
        for (UUID id : run.returns.keySet()) {
            ServerPlayer p = Dungeons.player(id);
            n.add(p != null ? p.getName().getString() : "?");
        }
        return String.join(", ", n);
    }

    /** Lower is better for times, higher is better for waves. */
    private static void addEntry(String id, Entry e, boolean lowerBetter) {
        List<Entry> list = new ArrayList<>(boards.getOrDefault(id, List.of()));
        list.add(e);
        list.sort((a, b) -> lowerBetter ? Long.compare(a.value(), b.value()) : Long.compare(b.value(), a.value()));
        while (list.size() > 10) list.removeLast();
        boards.put(id, list);
        save();
    }

    public static void unlock(ServerPlayer p, String title) {
        PlayerData d = AbpsMod.data().get(p);
        if (d.titles.contains(title)) return;
        d.titles.add(title);
        AbpsMod.data().save(p, d);
        AbpsMod.service().banner(p, "<bold><gradient:#FFD54F:#FF6D00>NEW TITLE</gradient></bold>", "<white>" + title + " <gray>(pick it in the Dungeons tab)", 0xFFD54F, 60);
    }

    static void recordClear(Run run, long time) {
        addEntry(run.def.id(), new Entry(names(run), time, run.partySize), true);
        for (ServerPlayer p : run.online()) {
            PlayerData d = AbpsMod.data().get(p);
            d.dungeonClears.merge(run.def.id(), 1, Integer::sum);
            Long best = d.dungeonBest.get(run.def.id());
            if (best == null || time < best) d.dungeonBest.put(run.def.id(), time);
            AbpsMod.data().save(p, d);
            unlock(p, run.def.title());
            boolean all = true;
            for (DungeonDef def : DungeonDef.ALL.values()) if (def.mode() == DungeonDef.Mode.STORY && !d.dungeonClears.containsKey(def.id())) all = false;
            if (all) unlock(p, "Conqueror");
        }
        AbpsMod.service().broadcast("<gold>⚔ <white>" + names(run) + "</white> cleared <white>" + run.def.name() + "</white> in "
                + dev.abps.util.Text.time(time) + "!", null);
    }

    static void recordWave(Run run, int wave) {
        if (wave <= 0) return;
        addEntry(run.def.id(), new Entry(names(run), wave, run.partySize), false);
        for (UUID id : run.returns.keySet()) {
            ServerPlayer p = Dungeons.player(id);
            if (p == null) continue;
            PlayerData d = AbpsMod.data().get(p);
            d.bestWave = Math.max(d.bestWave, wave);
            AbpsMod.data().save(p, d);
            if (wave >= 10) unlock(p, "Gladiator");
            if (wave >= 25) unlock(p, "Champion of the Arena");
        }
    }
}
