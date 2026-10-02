package dev.abps.dungeon;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.abps.AbpsMod;
import dev.abps.util.Vfx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Ruined gates found out in the overworld. A few new chunks get one: a broken stone arch with a glowing keystone.
 * Walking through it starts a run of The Shifting Depths for your party.
 */
public final class Gates {

    /** The dungeon a gate leads to. */
    public static final String DUNGEON = "depths";
    private static final int MAX_GATES = 2000;

    /** Chunks that were just made and rolled a gate. They are built on the next server tick, never inside the chunk event. */
    private static final ConcurrentLinkedQueue<long[]> PENDING = new ConcurrentLinkedQueue<>();
    /** The bottom middle of each gate's opening. */
    private static final List<BlockPos> GATES = new ArrayList<>();
    /** Players standing in a gate right now, so walking in counts once and coming back out of a dungeon into one does nothing. */
    private static final Set<UUID> INSIDE = new HashSet<>();
    private static final Map<UUID, Long> NEXT_MESSAGE = new HashMap<>();
    private static final Random RND = new Random();
    private static final Gson GSON = new Gson();

    private Gates() {
    }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (!generated || level.dimension() != Level.OVERWORLD || !AbpsMod.running()) return;
            var cfg = AbpsMod.config();
            if (!cfg.dungeonsEnabled || !cfg.dungeonGates) return;
            if (RND.nextInt(Math.max(1, cfg.gateRarity)) != 0) return;
            PENDING.add(new long[]{chunk.getPos().getMinBlockX() + 8, chunk.getPos().getMinBlockZ() + 8});
        });
    }

    public static List<BlockPos> all() {
        return GATES;
    }

    /** Removes a gate from the list (the self test cleans up after itself). The blocks stay. */
    static void forget(BlockPos gate) {
        GATES.remove(gate);
        save();
    }

    /** Called every server tick from Dungeons.tick. */
    static void tick() {
        ServerLevel world = AbpsMod.server().overworld();
        if (world == null) return;
        long[] job;
        while ((job = PENDING.poll()) != null) build(world, (int) job[0], (int) job[1]);
        long time = world.getGameTime();
        if (time % 5 == 0) checkPlayers(world);
        if (time % 40 == 0) shimmer(world);
    }

    // ------------------------------------------------------------------ building

    /** Builds a gate standing on the ground near x, z. Returns where its opening is, or null if the spot was no good. */
    public static BlockPos build(ServerLevel level, int x, int z) {
        if (GATES.size() >= MAX_GATES) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos ground = new BlockPos(x, y - 1, z);
        BlockState below = level.getBlockState(ground);
        // No gates on water, lava, trees or at the build limit
        if (!below.getFluidState().isEmpty() || below.isAir() || y >= level.getMaxY() - 8 || y <= level.getMinY() + 4) return null;
        if (below.is(net.minecraft.tags.BlockTags.LEAVES) || below.is(net.minecraft.tags.BlockTags.LOGS)) return null;
        for (BlockPos g : GATES) if (g.distSqr(ground) < 200 * 200) return null; // keep them spread out

        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState(), mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), chiseled = Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                key = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        // A worn floor 7 wide and 5 deep, with a bit of foundation under it so it doesn't float on slopes
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 2) continue;
                if (RND.nextInt(7) == 0) continue; // a few stones missing
                put(level, x + dx, y - 1, z + dz, RND.nextInt(3) == 0 ? mossy : RND.nextInt(3) == 0 ? cracked : brick);
                for (int d = 2; d <= 4; d++) {
                    BlockPos p = new BlockPos(x + dx, y - d, z + dz);
                    if (level.getBlockState(p).isAir() || !level.getBlockState(p).getFluidState().isEmpty()) put(level, p, brick);
                }
            }
        }
        // Clear the space inside and around the arch
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 0; dy <= 5; dy++) put(level, x + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
        // Two pillars, a lintel with a gap where it broke, and the glowing keystone in the middle
        for (int dy = 0; dy <= 3; dy++) {
            put(level, x - 2, y + dy, z, dy == 3 ? chiseled : RND.nextBoolean() ? mossy : brick);
            put(level, x + 2, y + dy, z, dy == 3 ? chiseled : RND.nextBoolean() ? cracked : brick);
        }
        put(level, x - 1, y + 4, z, mossy);
        put(level, x, y + 4, z, key);
        if (RND.nextBoolean()) put(level, x + 1, y + 4, z, cracked);
        put(level, x - 2, y + 4, z, brick);
        if (RND.nextBoolean()) put(level, x + 2, y + 4, z, mossy);
        // Fallen stones lying around
        for (int k = 0; k < 4; k++) {
            int rx = x + RND.nextInt(7) - 3, rz = z + (RND.nextBoolean() ? 2 : -2);
            put(level, rx, y, rz, RND.nextBoolean() ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
        put(level, x - 3, y, z, Blocks.SOUL_LANTERN.defaultBlockState());
        put(level, x + 3, y, z, Blocks.SOUL_LANTERN.defaultBlockState());

        BlockPos gate = new BlockPos(x, y, z);
        GATES.add(gate);
        save();
        AbpsMod.LOGGER.info("A dungeon gate formed at {} {} {}", x, y, z);
        return gate;
    }

    private static void put(ServerLevel level, int x, int y, int z, BlockState s) {
        put(level, new BlockPos(x, y, z), s);
    }

    private static void put(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    /** A gate is gone once its keystone is broken. */
    private static boolean intact(ServerLevel level, BlockPos gate) {
        BlockPos key = gate.above(4);
        return !level.isLoaded(key) || level.getBlockState(key).is(Blocks.CRYING_OBSIDIAN);
    }

    // ------------------------------------------------------------------ walking in

    private static boolean inOpening(ServerPlayer p, BlockPos g) {
        Vec3 v = p.position();
        return v.x > g.getX() - 1 && v.x < g.getX() + 2 && v.z > g.getZ() - 0.2 && v.z < g.getZ() + 1.2 && v.y >= g.getY() - 0.5 && v.y < g.getY() + 3;
    }

    private static void checkPlayers(ServerLevel world) {
        boolean removed = false;
        List<ServerPlayer> players = new ArrayList<>(AbpsMod.server().getPlayerList().getPlayers());
        players.addAll(Dungeons.TEST_PLAYERS.values());
        for (ServerPlayer p : players) {
            if (p.level() != world || p.isSpectator()) {
                if (p.level() == world) INSIDE.remove(p.getUUID());
                continue;
            }
            BlockPos hit = null;
            for (BlockPos g : GATES) {
                if (Math.abs(g.getX() - p.getX()) > 4 || Math.abs(g.getZ() - p.getZ()) > 4) continue;
                if (inOpening(p, g)) {
                    hit = g;
                    break;
                }
            }
            if (hit == null) {
                INSIDE.remove(p.getUUID());
                continue;
            }
            if (!intact(world, hit)) {
                GATES.remove(hit);
                removed = true;
                continue;
            }
            if (!INSIDE.add(p.getUUID())) continue; // was already standing in it
            enter(p, hit);
        }
        if (removed) save();
    }

    private static void enter(ServerPlayer p, BlockPos gate) {
        long now = System.currentTimeMillis();
        boolean chatty = now >= NEXT_MESSAGE.getOrDefault(p.getUUID(), 0L);
        NEXT_MESSAGE.put(p.getUUID(), now + 8000);
        if (!AbpsMod.config().dungeonsEnabled) {
            if (chatty) AbpsMod.service().send(p, "<gray>The gate is dark. Dungeons are turned off on this server.");
            return;
        }
        if (Dungeons.runOf(p) != null) return;
        Party party = Party.of(p);
        if (party != null && !party.isLeader(p)) {
            if (chatty) AbpsMod.service().send(p, "<gray>The gate hums. Only your party leader can open it.");
            return;
        }
        Vec3 c = Vec3.atBottomCenterOf(gate).add(0, 1.5, 0);
        int outer = Vfx.theme(Dungeons.THEME);
        try {
            DungeonDef def = DungeonDef.get(DUNGEON);
            int col = def == null ? 0xB388FF : def.color();
            Vfx.cue((ServerLevel) p.level(), Dungeons.CUE_GATE, c, new Vec3(0, 0, 1), c, p, null, col, 0xFFFFFF, 1);
        } finally {
            Vfx.theme(outer);
        }
        dev.abps.util.Fx.sound((ServerLevel) p.level(), p, SoundEvents.END_PORTAL_SPAWN, 0.4f, 1.6f);
        Dungeons.start(p, DUNGEON);
    }

    /** Gates near players shimmer now and then, so you can spot one from a distance. */
    private static void shimmer(ServerLevel world) {
        DungeonDef def = DungeonDef.get(DUNGEON);
        int col = def == null ? 0xB388FF : def.color();
        for (BlockPos g : GATES) {
            if (!world.isLoaded(g)) continue;
            boolean near = false;
            for (ServerPlayer p : world.players()) {
                if (p.blockPosition().distSqr(g) < 48 * 48) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;
            Vec3 c = Vec3.atBottomCenterOf(g).add(0, 1.5, 0);
            int outer = Vfx.theme(Dungeons.THEME);
            try {
                Vfx.cue(world, Dungeons.CUE_GATE, c, new Vec3(0, 0, 1), c, null, null, col, 0xFFFFFF, 0);
            } finally {
                Vfx.theme(outer);
            }
        }
    }

    // ------------------------------------------------------------------ saving

    private static Path file() {
        return AbpsMod.data().root().resolve("dungeon_gates.json");
    }

    static void load() {
        GATES.clear();
        INSIDE.clear();
        PENDING.clear();
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            List<int[]> list = GSON.fromJson(Files.readString(f), new TypeToken<List<int[]>>() {
            }.getType());
            if (list != null) for (int[] a : list) if (a.length == 3) GATES.add(new BlockPos(a[0], a[1], a[2]));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not read dungeon gates: {}", e.toString());
        }
    }

    static void save() {
        try {
            List<int[]> list = new ArrayList<>();
            for (BlockPos g : GATES) list.add(new int[]{g.getX(), g.getY(), g.getZ()});
            Files.writeString(file(), GSON.toJson(list));
        } catch (Exception e) {
            AbpsMod.LOGGER.warn("Could not save dungeon gates: {}", e.toString());
        }
    }
}
