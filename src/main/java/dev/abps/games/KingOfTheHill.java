package dev.abps.games;

import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** King of the Hill: score a point every second you hold the hill alone. First to 60, or the most after six minutes. */
final class KingOfTheHill extends Game {

    private static final int R = 22, HILL = 4, GOAL = 60;
    private static final long LIMIT_MS = 6 * 60_000L;
    private final Map<UUID, Integer> score = new HashMap<>();
    private String holder = "";

    KingOfTheHill(int slot, GameDef def, ServerLevel level, List<ServerPlayer> party) {
        super(slot, def, level, party);
    }

    @Override
    int radius() {
        return R + 4;
    }

    private int y0() {
        return origin.getY();
    }

    @Override
    void build() {
        int cx = origin.getX(), cz = origin.getZ(), y = y0();
        for (int dx = -R - 2; dx <= R + 2; dx++)
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (r > R + 1.5) continue;
                builder.fill(x, y - 3, z, x, y - 1, z, Blocks.STONE.defaultBlockState());
                if (r > R) {
                    builder.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                    builder.fill(x, y + 1, z, x, y + 4, z, Blocks.STONE_BRICKS.defaultBlockState());
                    builder.set(x, y + 5, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    continue;
                }
                builder.set(x, y, z, rnd.nextInt(7) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                // The hill: three tiers of steps up to a gold crown
                for (int tier = 1; tier <= 3; tier++) {
                    double tr = HILL + (3 - tier) * 2.2;
                    if (r <= tr) {
                        BlockState s = tier == 3 ? ((dx + dz & 1) == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.CHISELED_SANDSTONE.defaultBlockState())
                                : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                        builder.set(x, y + tier, z, s);
                    }
                }
            }
        builder.set(cx, y + 4, cz, Blocks.BEACON.defaultBlockState());
        builder.set(cx, y + 3, cz, Blocks.GOLD_BLOCK.defaultBlockState());
        // Cover: broken pillars around the field
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(a) * 14), z = cz + (int) Math.round(Math.sin(a) * 14);
            int h = 2 + rnd.nextInt(3);
            builder.fill(x, y + 1, z, x + 1, y + h, z + 1, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            builder.set(x, y + h + 1, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    @Override
    Vec3 spawnPoint(ServerPlayer p) {
        double a = rnd.nextDouble() * Math.PI * 2;
        return new Vec3(origin.getX() + 0.5 + Math.cos(a) * (R - 3), y0() + 1, origin.getZ() + 0.5 + Math.sin(a) * (R - 3));
    }

    @Override
    void begin() {
        var knock = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK);
        for (ServerPlayer p : online()) {
            score.put(p.getUUID(), 0);
            ItemStack stick = new ItemStack(Items.STICK);
            stick.enchant(knock, 2);
            stick.set(net.minecraft.core.component.DataComponents.ITEM_NAME, dev.abps.util.Text.mm("<gold>Shoving Stick"));
            give(p, stick);
        }
        tell("<gold><bold>King of the Hill!</bold></gold> <gray>Stand on the gold crown alone to score a point a second. First to " + GOAL + " wins.");
    }

    private boolean onHill(ServerPlayer p) {
        double dx = p.getX() - (origin.getX() + 0.5), dz = p.getZ() - (origin.getZ() + 0.5);
        return dx * dx + dz * dz <= (HILL + 0.6) * (HILL + 0.6) && p.getY() >= y0() + 3.5 && p.getY() < y0() + 7;
    }

    @Override
    String hud() {
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(score.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            ServerPlayer p = AbpsMod.server().getPlayerList().getPlayer(top.get(i).getKey());
            if (p == null) continue;
            if (!sb.isEmpty()) sb.append(" <dark_gray>·</dark_gray> ");
            sb.append("<white>").append(p.getName().getString()).append(" <gold>").append(top.get(i).getValue());
        }
        long left = Math.max(0, LIMIT_MS - (System.currentTimeMillis() - startedAt));
        return (holder.isEmpty() ? "The hill is open! " : holder + " holds the hill ") + "<gray>(" + dev.abps.util.Text.time(left) + ")|" + sb;
    }

    @Override
    void update() {
        if (ticks % 20 == 0) {
            List<ServerPlayer> on = new ArrayList<>();
            for (ServerPlayer p : online()) if (onHill(p)) on.add(p);
            if (on.size() == 1) {
                ServerPlayer king = on.getFirst();
                int s = score.merge(king.getUUID(), 1, Integer::sum);
                if (!holder.equals(king.getName().getString())) sound(SoundEvents.BELL_BLOCK, 0.6f, 1.4f);
                holder = king.getName().getString();
                Fx.burst(level, ParticleTypes.WAX_ON, king.position().add(0, 2.2, 0), 6, 0.3, 0.05);
                if (s >= GOAL) {
                    win(king);
                    return;
                }
            } else {
                holder = on.isEmpty() ? "" : "Contested! " + on.size() + " players";
            }
            if (System.currentTimeMillis() - startedAt > LIMIT_MS) {
                UUID best = null;
                for (var e : score.entrySet()) if (best == null || e.getValue() > score.get(best)) best = e.getKey();
                ServerPlayer w = best == null ? null : AbpsMod.server().getPlayerList().getPlayer(best);
                if (w != null) win(w);
                else finish(60);
                return;
            }
            sendHud(0, 0, 0);
        }
    }

    private void win(ServerPlayer king) {
        Games.recordWin(def, king);
        banner("<bold><gradient:#FFD54F:#FF6D00>" + king.getName().getString() + " IS KING</gradient></bold>", "<gray>King of the Hill", 0xFFD54F);
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Fx.burst(level, ParticleTypes.FIREWORK, king.position().add(0, 1, 0), 50, 0.6, 0.25);
        finish(100);
    }
}
