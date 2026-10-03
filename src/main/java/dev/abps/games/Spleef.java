package dev.abps.games;

import dev.abps.util.Fx;
import dev.abps.util.Targets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Spleef: two snow floors over the void. Dig the snow out from under everyone else; last one standing wins. */
final class Spleef extends Game {

    private static final int R = 14, GAP = 7;
    private final Set<UUID> alive = new LinkedHashSet<>();
    private int countdown;
    private boolean live;

    Spleef(int slot, GameDef def, ServerLevel level, List<ServerPlayer> party) {
        super(slot, def, level, party);
    }

    @Override
    int radius() {
        return R + 8;
    }

    private int y0() {
        return origin.getY();
    }

    @Override
    void build() {
        int cx = origin.getX(), cz = origin.getZ(), y = y0();
        for (int dx = -R - 6; dx <= R + 6; dx++)
            for (int dz = -R - 6; dz <= R + 6; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (r <= R) {
                    builder.set(x, y, z, Blocks.SNOW_BLOCK.defaultBlockState());
                    if (r <= R - 2) builder.set(x, y - GAP, z, Blocks.SNOW_BLOCK.defaultBlockState());
                    if (r > R - 2 && r <= R - 1) builder.set(x, y - GAP, z, Blocks.PACKED_ICE.defaultBlockState());
                } else if (r <= R + 1) {
                    // A clear rim so nobody walks off the edge by accident
                    builder.set(x, y, z, Blocks.PACKED_ICE.defaultBlockState());
                    builder.fill(x, y + 1, z, x, y + 3, z, Blocks.STAINED_GLASS.lightBlue().defaultBlockState());
                } else if (r <= R + 5) {
                    // The gallery for players who are out
                    BlockState floor = ((int) r & 1) == 0 ? Blocks.QUARTZ_BRICKS.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
                    builder.set(x, y + 4, z, floor);
                    if (r > R + 4) builder.fill(x, y + 5, z, x, y + 6, z, Blocks.GLASS.defaultBlockState());
                    if (r <= R + 1.8) builder.fill(x, y + 5, z, x, y + 6, z, Blocks.GLASS.defaultBlockState());
                }
            }
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            builder.set(cx + (int) Math.round(Math.cos(a) * (R + 3)), y + 5, cz + (int) Math.round(Math.sin(a) * (R + 3)), Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    @Override
    Vec3 spawnPoint(ServerPlayer p) {
        int i = new ArrayList<>(players).indexOf(p.getUUID());
        double a = Math.PI * 2 * Math.max(0, i) / Math.max(1, players.size());
        return new Vec3(origin.getX() + 0.5 + Math.cos(a) * (R - 4), y0() + 1, origin.getZ() + 0.5 + Math.sin(a) * (R - 4));
    }

    private Vec3 gallery() {
        double a = rnd.nextDouble() * Math.PI * 2;
        return new Vec3(origin.getX() + 0.5 + Math.cos(a) * (R + 3), y0() + 5, origin.getZ() + 0.5 + Math.sin(a) * (R + 3));
    }

    @Override
    Vec3 respawnPoint(ServerPlayer p) {
        return gallery();
    }

    @Override
    void begin() {
        countdown = 5 * 20;
        for (ServerPlayer p : online()) {
            alive.add(p.getUUID());
            give(p, new ItemStack(Items.DIAMOND_SHOVEL));
            Targets.root(p, countdown);
        }
        tell("<aqua><bold>Spleef!</bold></aqua> <gray>Break the snow under the others. Fall through both floors and you're out. Snowballs knock people around.");
    }

    @Override
    String hud() {
        return (live ? "Last one standing wins" : "Starting in " + (countdown / 20 + 1) + "...") + "|<gray>" + alive.size() + " still standing";
    }

    @Override
    void update() {
        if (!live) {
            if (countdown % 20 == 0) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1f, countdown == 0 ? 2f : 1.2f);
            if (countdown-- <= 0) {
                live = true;
                banner("<bold><aqua>GO!</aqua></bold>", "<gray>Dig!", 0x80DEEA);
            }
        }
        for (ServerPlayer p : online()) {
            if (alive.contains(p.getUUID()) && p.getY() < y0() - GAP - 4) out(p);
        }
        if (live && alive.size() <= 1 && closing < 0) {
            ServerPlayer winner = null;
            for (UUID id : alive) winner = dev.abps.AbpsMod.server().getPlayerList().getPlayer(id);
            if (winner != null) {
                Games.recordWin(def, winner);
                banner("<bold><gradient:#E1F5FE:#40C4FF>" + winner.getName().getString() + " WINS</gradient></bold>", "<gray>Spleef champion", 0x40C4FF);
                Fx.burst(level, ParticleTypes.FIREWORK, winner.position().add(0, 1, 0), 40, 0.6, 0.2);
            } else {
                banner("<bold><gray>Nobody wins</gray></bold>", "<gray>Everyone fell", 0x9E9E9E);
            }
            sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            finish(100);
        }
        if (ticks % 20 == 0) sendHud(0, 0, 0);
    }

    private void out(ServerPlayer p) {
        alive.remove(p.getUUID());
        tell("<gray>" + p.getName().getString() + " is out! <dark_gray>(" + alive.size() + " left)");
        respawn(p);
        sound(SoundEvents.PLAYER_HURT, 0.6f, 0.8f);
    }

    @Override
    boolean allowDeath(ServerPlayer p) {
        if (alive.contains(p.getUUID())) out(p);
        else respawn(p);
        return false;
    }

    @Override
    void onLeave(ServerPlayer p) {
        alive.remove(p.getUUID());
    }

    @Override
    boolean canBreak(ServerPlayer p, BlockPos pos) {
        if (!live || !alive.contains(p.getUUID())) return false;
        double dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
        return level.getBlockState(pos).is(Blocks.SNOW_BLOCK) && dx * dx + dz * dz <= R * R + 1;
    }
}
