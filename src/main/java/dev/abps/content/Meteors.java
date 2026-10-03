package dev.abps.content;

import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import dev.abps.util.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Meteor showers: some nights the sky fills with shooting stars, and a few of them land. Each one leaves a small
 * smoking crater with ruby ore (and, rarely, endite) in its glassy heart.
 */
public final class Meteors {

    private Meteors() {
    }

    private static final Random RND = new Random();
    private static final int SHOWER_TICKS = 20 * 150;
    private static int showerLeft;
    private static boolean checkedTonight;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Meteors::tick);
    }

    /** Starts a shower now (operators can call it with !Meteors). */
    public static void start(MinecraftServer server) {
        showerLeft = SHOWER_TICKS;
        for (ServerPlayer p : server.overworld().players()) {
            AbpsMod.service().banner(p, "<bold><gradient:#B388FF:#40C4FF>METEOR SHOWER</gradient></bold>", "<gray>Watch the sky. Some of them land.", 0xB388FF, 80);
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        if (world == null || world.players().isEmpty()) return;
        long time = Time.dayTime(world) % 24000;
        // Nightfall: one in five nights brings a shower
        if (time >= 13000 && time < 13100) {
            if (!checkedTonight) {
                checkedTonight = true;
                if (RND.nextInt(5) == 0) start(server);
            }
        } else if (time < 12000) {
            checkedTonight = false;
        }
        if (showerLeft <= 0) return;
        showerLeft--;
        List<ServerPlayer> players = world.players();
        // Shooting stars every few ticks around each player
        if (showerLeft % 6 == 0) {
            for (ServerPlayer p : players) streak(world, p.position(), false);
        }
        // A landing every ~25 seconds per shower
        if (showerLeft % 500 == 250) {
            ServerPlayer p = players.get(RND.nextInt(players.size()));
            land(world, p.position());
        }
        if (showerLeft == 0) {
            for (ServerPlayer p : players) AbpsMod.service().actionBar(p, "<gray>The meteor shower fades.");
        }
    }

    /** A shooting star crossing the sky high above a spot. Only particles. */
    private static void streak(ServerLevel world, Vec3 near, boolean big) {
        double a = RND.nextDouble() * Math.PI * 2;
        Vec3 from = near.add(Math.cos(a) * (30 + RND.nextInt(50)), 70 + RND.nextInt(30), Math.sin(a) * (30 + RND.nextInt(50)));
        Vec3 dir = new Vec3(-Math.cos(a) + RND.nextGaussian() * 0.3, -0.35, -Math.sin(a) + RND.nextGaussian() * 0.3).normalize();
        Vec3 to = from.add(dir.scale(big ? 50 : 18 + RND.nextInt(14)));
        Fx.line(world, ParticleTypes.END_ROD, from, to, big ? 0.4 : 0.8);
        Fx.line(world, ParticleTypes.FIREWORK, from, to, 1.6);
    }

    /** A meteor that lands 40-90 blocks from a spot: a falling fireball, then a crater. */
    public static void land(ServerLevel world, Vec3 near) {
        double a = RND.nextDouble() * Math.PI * 2, d = 40 + RND.nextInt(50);
        int x = (int) Math.floor(near.x + Math.cos(a) * d), z = (int) Math.floor(near.z + Math.sin(a) * d);
        if (!world.hasChunkAt(new BlockPos(x, 64, z))) return;
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos ground = new BlockPos(x, y, z);
        BlockState top = world.getBlockState(ground);
        // Only on natural ground: never on builds, water or trees
        if (!(top.is(BlockTags.DIRT) || top.is(BlockTags.SAND) || top.is(Blocks.STONE) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.GRAVEL)
                || top.is(Blocks.RED_SAND))) return;
        Vec3 impact = Vec3.atCenterOf(ground).add(0, 1, 0);
        Vec3 start = impact.add(RND.nextGaussian() * 25, 90, RND.nextGaussian() * 25);
        int steps = 30;
        Tasks.repeat(steps, 1, step -> {
            Vec3 at = start.lerp(impact, (step + 1) / (double) steps);
            Fx.burst(world, ParticleTypes.FLAME, at, 12, 0.6, 0.02);
            Fx.burst(world, ParticleTypes.LARGE_SMOKE, at, 4, 0.5, 0.01);
            if (step % 6 == 0) Fx.sound(world, at, SoundEvents.FIRECHARGE_USE, 2f, 0.5f);
            if (step == steps - 1) crater(world, ground);
        });
    }

    private static void crater(ServerLevel world, BlockPos c) {
        int r = 3 + RND.nextInt(2);
        Fx.burst(world, ParticleTypes.EXPLOSION_EMITTER, Vec3.atCenterOf(c), 1, 0, 0);
        Fx.sound(world, Vec3.atCenterOf(c), SoundEvents.GENERIC_EXPLODE, 4f, 0.6f);
        // Scoop out a bowl
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -r; dy <= r + 2; dy++) {
                    double dist = Math.sqrt(dx * dx + dz * dz + Math.max(0, dy) * Math.max(0, dy) * 0.3 + Math.min(0, dy) * Math.min(0, dy) * 1.6);
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = world.getBlockState(p);
                    if (s.is(Blocks.BEDROCK) || s.hasBlockEntity()) continue;
                    if (dist <= r) world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
        // Line the bowl with scorched rock and set the meteorite in the middle
        for (int dx = -r - 1; dx <= r + 1; dx++)
            for (int dz = -r - 1; dz <= r + 1; dz++)
                for (int dy = -r - 1; dy <= 1; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = world.getBlockState(p);
                    if (s.isAir() || s.is(Blocks.BEDROCK) || s.hasBlockEntity() || !world.getBlockState(p.above()).isAir()) continue;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > r + 1.2) continue;
                    int roll = RND.nextInt(10);
                    world.setBlock(p, roll < 4 ? Blocks.BLACKSTONE.defaultBlockState() : roll < 6 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : roll < 8 ? Blocks.BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState(), 3);
                }
        BlockPos core = c.below(r - 1);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 0; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if (world.getBlockState(p).is(Blocks.BEDROCK)) continue;
                    boolean centre = dx == 0 && dz == 0;
                    BlockState s = centre ? (RND.nextInt(6) == 0 ? ModContent.ENDITE_ORE.defaultBlockState() : ModContent.DEEPSLATE_RUBY_ORE.defaultBlockState())
                            : RND.nextInt(3) == 0 ? ModContent.RUBY_ORE.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
                    world.setBlock(p, s, 3);
                }
        // Smoke for a while afterwards
        Vec3 smoke = Vec3.atCenterOf(core).add(0, 1.5, 0);
        Tasks.repeat(60, 10, step -> Fx.burst(world, ParticleTypes.CAMPFIRE_COSY_SMOKE, smoke, 2, 0.6, 0.02));
        for (ServerPlayer p : world.players()) {
            if (p.position().distanceToSqr(Vec3.atCenterOf(c)) < 200 * 200) {
                AbpsMod.service().send(p, "<light_purple>☄ A meteor crashed down near </light_purple><white>" + c.getX() + ", " + c.getY() + ", " + c.getZ()
                        + "</white><light_purple>. Rubies glitter in the crater.");
            }
        }
    }
}
