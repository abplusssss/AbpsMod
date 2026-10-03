package dev.abps.games;

import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Capture the Flag: two teams, two bases. Grab the other team's wool and bring it home while yours is safe. */
final class CaptureTheFlag extends Game {

    private static final int HALF_X = 30, HALF_Z = 13, GOAL = 3;
    private static final long LIMIT_MS = 10 * 60_000L;

    enum Team {
        RED("Red", "<#FF5252>", 0xFF5252, Blocks.WOOL.red().defaultBlockState(), Blocks.CONCRETE.red().defaultBlockState(), -1),
        BLUE("Blue", "<#40C4FF>", 0x40C4FF, Blocks.WOOL.blue().defaultBlockState(), Blocks.CONCRETE.blue().defaultBlockState(), 1);

        final String title, tag;
        final int color, side;
        final BlockState wool, floor;

        Team(String title, String tag, int color, BlockState wool, BlockState floor, int side) {
            this.title = title;
            this.tag = tag;
            this.color = color;
            this.wool = wool;
            this.floor = floor;
            this.side = side;
        }

        Team other() {
            return this == RED ? BLUE : RED;
        }
    }

    private final Map<UUID, Team> team = new HashMap<>();
    private final Map<Team, Integer> score = new HashMap<>();
    /** Who carries each team's flag, or null when it's home. */
    private final Map<Team, UUID> carrier = new HashMap<>();

    CaptureTheFlag(int slot, GameDef def, ServerLevel level, List<ServerPlayer> party) {
        super(slot, def, level, party);
        int i = 0;
        for (ServerPlayer p : party) team.put(p.getUUID(), i++ % 2 == 0 ? Team.RED : Team.BLUE);
        score.put(Team.RED, 0);
        score.put(Team.BLUE, 0);
    }

    @Override
    int radius() {
        return HALF_X + 4;
    }

    private int y0() {
        return origin.getY();
    }

    private BlockPos stand(Team t) {
        return new BlockPos(origin.getX() + t.side * (HALF_X - 4), y0() + 1, origin.getZ());
    }

    @Override
    void build() {
        int cx = origin.getX(), cz = origin.getZ(), y = y0();
        for (int dx = -HALF_X - 1; dx <= HALF_X + 1; dx++)
            for (int dz = -HALF_Z - 1; dz <= HALF_Z + 1; dz++) {
                int x = cx + dx, z = cz + dz;
                builder.fill(x, y - 3, z, x, y - 1, z, Blocks.STONE.defaultBlockState());
                if (Math.abs(dx) > HALF_X || Math.abs(dz) > HALF_Z) {
                    builder.fill(x, y, z, x, y + 4, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                    builder.set(x, y + 5, z, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                    continue;
                }
                BlockState floor;
                if (Math.abs(dx) >= HALF_X - 8) floor = (dx + dz & 1) == 0 ? (dx < 0 ? Team.RED : Team.BLUE).floor : Blocks.POLISHED_ANDESITE.defaultBlockState();
                else if (dx == 0) floor = Blocks.CONCRETE.white().defaultBlockState();
                else floor = rnd.nextInt(8) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                builder.set(x, y, z, floor);
            }
        // Mid-field cover, mirrored so neither side has the better half
        int[][] cover = {{8, 5}, {8, -5}, {15, 0}, {4, 10}, {4, -10}, {18, 8}, {18, -8}};
        for (int[] c : cover)
            for (int s : new int[]{-1, 1}) {
                int x = cx + c[0] * s, z = cz + c[1];
                builder.fill(x, y + 1, z - 1, x, y + 2, z + 1, Blocks.STONE_BRICKS.defaultBlockState());
                builder.set(x, y + 3, z, Blocks.LANTERN.defaultBlockState());
            }
        // Base walls with openings, and the flag stands
        for (Team t : Team.values()) {
            int bx = cx + t.side * (HALF_X - 9);
            for (int dz = -HALF_Z; dz <= HALF_Z; dz++) {
                if (Math.abs(dz) <= 2 || Math.abs(dz - 8) <= 1 || Math.abs(dz + 8) <= 1) continue;
                builder.fill(bx, y + 1, cz + dz, bx, y + 2, cz + dz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
            BlockPos s = stand(t);
            builder.fill(s.getX() - 1, y, s.getZ() - 1, s.getX() + 1, y, s.getZ() + 1, Blocks.GOLD_BLOCK.defaultBlockState());
            builder.set(s.getX(), y + 1, s.getZ(), Blocks.OAK_FENCE.defaultBlockState());
            builder.set(s.getX(), y + 2, s.getZ(), t.wool);
        }
    }

    @Override
    Vec3 spawnPoint(ServerPlayer p) {
        Team t = team.getOrDefault(p.getUUID(), Team.RED);
        BlockPos s = stand(t);
        return new Vec3(s.getX() + 0.5 - t.side * 3, y0() + 1, s.getZ() + 0.5 + rnd.nextInt(7) - 3);
    }

    @Override
    void begin() {
        for (ServerPlayer p : online()) {
            Team t = team.get(p.getUUID());
            AbpsMod.service().send(p, "<gray>You're on the " + t.tag + "<bold>" + t.title + "</bold><reset> <gray>team. Walk into the other team's wool to grab it, then bring it back to your own stand.");
        }
        tell("<gray>Teams: " + names(Team.RED) + " <dark_gray>vs</dark_gray> " + names(Team.BLUE) + "<gray>. First to " + GOAL + " captures wins.");
    }

    private String names(Team t) {
        StringBuilder sb = new StringBuilder(t.tag);
        for (ServerPlayer p : online()) {
            if (team.get(p.getUUID()) != t) continue;
            if (sb.length() > t.tag.length()) sb.append(", ");
            sb.append(p.getName().getString());
        }
        return sb.append("</").append(">").toString();
    }

    @Override
    String hud() {
        Team mine = null;
        long left = Math.max(0, LIMIT_MS - (System.currentTimeMillis() - startedAt));
        String flags = (carrier.get(Team.RED) == null ? "<#FF5252>⚑ home" : "<#FF5252>⚑ taken") + " <dark_gray>·</dark_gray> "
                + (carrier.get(Team.BLUE) == null ? "<#40C4FF>⚑ home" : "<#40C4FF>⚑ taken");
        return "<#FF5252>Red " + score.get(Team.RED) + "<reset> <gray>-</gray> <#40C4FF>" + score.get(Team.BLUE) + " Blue<reset> <gray>(" + dev.abps.util.Text.time(left) + ")|" + flags;
    }

    @Override
    void update() {
        for (ServerPlayer p : online()) {
            Team t = team.get(p.getUUID());
            if (t == null) continue;
            Team enemy = t.other();
            Vec3 at = p.position();
            // Grab the enemy flag
            if (carrier.get(enemy) == null && at.distanceToSqr(Vec3.atBottomCenterOf(stand(enemy))) < 3.2) {
                carrier.put(enemy, p.getUUID());
                level.setBlock(stand(enemy).above(), Blocks.AIR.defaultBlockState(), 3);
                p.setGlowingTag(true);
                tell(t.tag + p.getName().getString() + "<reset> <gray>grabbed the " + enemy.tag + enemy.title + "<reset> <gray>flag!");
                sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.6f);
            }
            // Bring it home (your own flag must be home too)
            if (p.getUUID().equals(carrier.get(enemy)) && carrier.get(t) == null && at.distanceToSqr(Vec3.atBottomCenterOf(stand(t))) < 4) {
                int s = score.merge(t, 1, Integer::sum);
                returnFlag(enemy);
                p.setGlowingTag(false);
                banner("<bold>" + t.tag + t.title + " scores!</bold>", "<gray>" + score.get(Team.RED) + " - " + score.get(Team.BLUE), t.color);
                sound(SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
                if (s >= GOAL) {
                    win(t);
                    return;
                }
            }
            if (p.getUUID().equals(carrier.get(enemy)) && ticks % 4 == 0) Fx.burst(level, Fx.dust(enemy.color, 1.4f), at.add(0, 2.3, 0), 3, 0.15, 0);
        }
        if (ticks % 20 == 0) {
            if (System.currentTimeMillis() - startedAt > LIMIT_MS) {
                int r = score.get(Team.RED), b = score.get(Team.BLUE);
                if (r == b) {
                    banner("<bold><gray>Draw!</gray></bold>", "<gray>" + r + " - " + b, 0x9E9E9E);
                    finish(100);
                } else win(r > b ? Team.RED : Team.BLUE);
                return;
            }
            sendHud(0, 0, 0);
        }
    }

    private void returnFlag(Team t) {
        carrier.remove(t);
        level.setBlock(stand(t).above(), t.wool, 3);
    }

    private void drop(ServerPlayer p) {
        for (Team t : Team.values()) {
            if (p.getUUID().equals(carrier.get(t))) {
                returnFlag(t);
                p.setGlowingTag(false);
                tell("<gray>The " + t.tag + t.title + "<reset> <gray>flag returned to its base.");
            }
        }
    }

    @Override
    boolean allowDeath(ServerPlayer p) {
        drop(p);
        respawn(p);
        return false;
    }

    @Override
    void onLeave(ServerPlayer p) {
        drop(p);
    }

    /** No friendly fire. */
    boolean allowHit(ServerPlayer attacker, ServerPlayer victim) {
        return team.get(attacker.getUUID()) != team.get(victim.getUUID());
    }

    private void win(Team t) {
        for (ServerPlayer p : online()) if (team.get(p.getUUID()) == t) Games.recordWin(def, p);
        banner("<bold>" + t.tag + t.title + " team wins!</bold>", "<gray>" + score.get(Team.RED) + " - " + score.get(Team.BLUE), t.color);
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        for (ServerPlayer p : online()) p.setGlowingTag(false);
        finish(100);
    }
}
