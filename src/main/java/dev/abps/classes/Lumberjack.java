package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Gatherer. Trees: logs break fast, whole trees come down at once, and forests grow back behind you. */
public final class Lumberjack extends AttributeClass {

    static final int CUE_FELL = 11, CUE_CHIP = 12;
    private static final int BARK = 0x8D6E63, LEAF = 0xAED581;

    @Override public String id() { return "lumberjack"; }
    @Override public String name() { return "Lumberjack"; }
    @Override public String color() { return "#AED581"; }
    @Override public String color2() { return "#8D6E63"; }
    @Override public Item icon() { return Items.IRON_AXE; }
    @Override public String symbol() { return "♣"; }
    @Override public String tagline() { return "The forest falls in one swing."; }
    @Override public String mastery() { return "Felled trees drop apples and saplings, and leave a sapling planted."; }
    @Override public Role role() { return Role.GATHERER; }

    private double logSpeed(int lvl) { return lerp(lvl, 0.60, 1.50); }
    private double extraLog(int lvl) { return lerp(lvl, 0.15, 0.45); }
    private double durability(int lvl) { return lerp(lvl, 0.40, 0.75); }
    private int maxLogs(int lvl) { return (int) Math.round(lerp(lvl, 64, 192)); }
    private double vigorTime(int lvl) { return lerp(lvl, 20, 35); }
    private double slamDamage(int lvl) { return lerp(lvl, 5, 8); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Logs and wood break " + pct(logSpeed(lvl)) + " faster",
                pct(extraLog(lvl)) + " chance for logs to drop an extra log",
                "Axes take " + pct(durability(lvl)) + " less durability damage",
                "Leaves break instantly",
                "+2 hearts");
    }

    @Override
    public List<String> negatives() {
        return List.of("Gatherer: deal 25% less damage to players", "Swim 20% slower");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Timber";
            case 2 -> "Sapling Storm";
            case 3 -> "Woodsman's Vigor";
            case 4 -> "Trunk Slam";
            default -> "Clear-Cut";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Fell the whole tree you look at (up to " + maxLogs(lvl) + " logs). Needs an axe.";
            case 2 -> "Plant saplings from your inventory on open ground around you and grow them a few stages.";
            case 3 -> "Haste III and Speed I for " + num(vigorTime(lvl)) + "s.";
            case 4 -> "Slam a log into the ground: enemies within 4 blocks take " + num(slamDamage(lvl)) + " damage, get knocked back and slowed.";
            default -> "Every tree within 14 blocks comes crashing down, one after another.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 4;
            case 2 -> 25;
            case 3 -> 60;
            default -> 18;
        };
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return idx == 3 ? (int) (vigorTime(d.level) * 20) : 0;
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:oak_log", "minecraft:apple", "minecraft:golden_apple", "minecraft:enchanted_golden_apple"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{64, 24, 6, 1};
    }

    // ---- helpers ----

    static boolean log(BlockState s) {
        return s.is(BlockTags.LOGS);
    }

    /** The logs of the tree touching start, found by walking through touching logs (diagonals too). */
    static List<BlockPos> tree(ServerLevel level, BlockPos start, int max) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && out.size() < max) {
            BlockPos c = q.poll();
            if (!log(level.getBlockState(c))) continue;
            out.add(c);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - start.getX()) > 8 || Math.abs(n.getZ() - start.getZ()) > 8) continue;
                        if (seen.add(n)) q.add(n);
                    }
        }
        return out;
    }

    /** A tree only counts if it has leaves near its top, so player-built log walls aren't torn down. */
    static boolean natural(ServerLevel level, List<BlockPos> logs) {
        if (logs.isEmpty()) return false;
        BlockPos top = logs.getFirst();
        for (BlockPos b : logs) if (b.getY() > top.getY()) top = b;
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            if (level.getBlockState(p).is(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    /** Breaks a tree a few logs a tick from the bottom up, through the normal break so claims and drops work. */
    private void fell(ServerPlayer p, PlayerData d, ServerLevel level, List<BlockPos> logs) {
        logs.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        BlockPos base = logs.getFirst();
        BlockState baseState = level.getBlockState(base);
        int per = 4;
        int steps = (logs.size() + per - 1) / per;
        cue(p, CUE_FELL, Vec3.atBottomCenterOf(base), Vec3.atCenterOf(logs.getLast()), p, null, logs.size());
        Tasks.repeat(steps, 1, step -> {
            if (p.isRemoved()) return;
            for (int k = step * per; k < Math.min(logs.size(), (step + 1) * per); k++) {
                BlockPos at = logs.get(k);
                if (log(level.getBlockState(at))) p.gameMode.destroyBlock(at);
            }
            if (step % 3 == 0) Fx.sound(level, Vec3.atCenterOf(logs.get(Math.min(logs.size() - 1, step * per))), SoundEvents.GRINDSTONE_USE, 0.5f, 0.6f);
        });
        Tasks.later(steps + 2L, () -> {
            Fx.sound(level, Vec3.atCenterOf(base), SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
            if (!mastered(d)) return;
            // Mastery: a sapling where the tree stood, and a little extra
            Item sapling = saplingFor(baseState);
            if (sapling != null && level.getBlockState(base).isAir()) {
                BlockState plant = Block.byItem(sapling).defaultBlockState();
                if (plant.canSurvive(level, base)) level.setBlock(base, plant, Block.UPDATE_ALL);
            }
            Block.popResource(level, base, new ItemStack(Items.APPLE, 1 + (int) (rand() * 2)));
            if (sapling != null) Block.popResource(level, base, new ItemStack(sapling, 1 + (int) (rand() * 2)));
        });
    }

    static Item saplingFor(BlockState log) {
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath();
        if (path.startsWith("spruce")) return Items.SPRUCE_SAPLING;
        if (path.startsWith("birch")) return Items.BIRCH_SAPLING;
        if (path.startsWith("jungle")) return Items.JUNGLE_SAPLING;
        if (path.startsWith("acacia")) return Items.ACACIA_SAPLING;
        if (path.startsWith("dark_oak")) return Items.DARK_OAK_SAPLING;
        if (path.startsWith("cherry")) return Items.CHERRY_SAPLING;
        if (path.startsWith("mangrove")) return Items.MANGROVE_PROPAGULE;
        if (path.startsWith("oak")) return Items.OAK_SAPLING;
        return null;
    }

    private boolean holdingAxe(ServerPlayer p) {
        return p.getMainHandItem().is(ItemTags.AXES);
    }

    private BlockHitResult lookBlock(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        return p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
    }

    // ---- passives ----

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MAX_HEALTH, "lumber_hp", 4, Mods.ADD);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        boolean water = p.isInWater();
        Mods.toggle(p, water, Attributes.MOVEMENT_SPEED, "lumber_swim", -0.2, Mods.MULT);
    }

    @Override
    public void onBlockAttack(ServerPlayer p, PlayerData d, BlockState state) {
        double bonus = log(state) || state.is(BlockTags.MINEABLE_WITH_AXE) ? logSpeed(d.level) : state.is(BlockTags.LEAVES) ? 50 : 0;
        Mods.toggle(p, bonus > 0, Attributes.BLOCK_BREAK_SPEED, "lumber_break", bonus, Mods.MULT);
    }

    @Override
    public void modifyDrops(ServerPlayer p, PlayerData d, BlockState state, List<ItemStack> drops) {
        if (!log(state) || rand() >= extraLog(d.level)) return;
        drops.add(new ItemStack(state.getBlock().asItem()));
    }

    @Override
    public boolean saveDurability(ServerPlayer p, PlayerData d, ItemStack stack) {
        return stack.is(ItemTags.AXES) && rand() < durability(d.level);
    }

    @Override
    public double gatherCharge(BlockState state) {
        return log(state) ? 0.004 : 0;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        Mods.remove(p, Attributes.BLOCK_BREAK_SPEED, "lumber_break");
        Mods.remove(p, Attributes.MOVEMENT_SPEED, "lumber_swim");
    }

    // ---- abilities ----

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        if (!holdingAxe(p)) {
            fail(p, "Hold an axe to use Timber.");
            return false;
        }
        BlockHitResult hit = lookBlock(p, 6);
        ServerLevel level = level(p);
        if (hit.getType() != HitResult.Type.BLOCK || !log(level.getBlockState(hit.getBlockPos()))) {
            fail(p, "Look at a log.");
            return false;
        }
        List<BlockPos> logs = tree(level, hit.getBlockPos(), maxLogs(d.level));
        if (!natural(level, logs)) {
            fail(p, "That doesn't look like a tree (no leaves on top).");
            return false;
        }
        castAim = Vec3.atCenterOf(hit.getBlockPos());
        used(p, 1);
        fell(p, d, level, logs);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        BlockPos c = p.blockPosition();
        int planted = 0;
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-5, -2, -5), c.offset(5, 2, 5))) {
            if (planted >= 12) break;
            BlockPos at = pos.immutable();
            if ((at.getX() + at.getZ()) % 3 != 0) continue; // spread them out so they have room to grow
            if (!level.getBlockState(at).isAir()) continue;
            ItemStack sap = findSapling(p);
            if (sap == null) break;
            BlockState plant = Block.byItem(sap.getItem()).defaultBlockState();
            if (!plant.canSurvive(level, at)) continue;
            level.setBlock(at, plant, Block.UPDATE_ALL);
            if (!p.isCreative()) sap.shrink(1);
            for (int k = 0; k < 3; k++) Harvester.grow(level, at);
            planted++;
        }
        if (planted == 0) {
            fail(p, findSapling(p) == null ? "You need saplings in your inventory." : "No open ground nearby.");
            return false;
        }
        used(p, 2);
        Fx.sound(level, p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
        return true;
    }

    private static ItemStack findSapling(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(ItemTags.SAPLINGS)) return s;
        }
        return null;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        int ticks = (int) (vigorTime(d.level) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 2));
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0));
        used(p, 3);
        Fx.sound(level(p), p, SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        List<LivingEntity> hit = Targets.enemiesNear(p, p.position(), 4);
        used(p, 4);
        for (LivingEntity e : hit) {
            Targets.damage(e, slamDamage(d.level), p);
            Targets.pushAway(p.position(), e, 1.0, 0.45);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        }
        Fx.sound(level, p, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
        Fx.shakeNear(level, p.position(), 8, 5, 0.4f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        BlockPos c = p.blockPosition();
        List<List<BlockPos>> trees = new ArrayList<>();
        Set<BlockPos> taken = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-14, -3, -14), c.offset(14, 6, 14))) {
            if (trees.size() >= 16) break;
            BlockPos at = pos.immutable();
            if (taken.contains(at) || !log(level.getBlockState(at)) || log(level.getBlockState(at.below()))) continue;
            List<BlockPos> t = tree(level, at, maxLogs(d.level));
            taken.addAll(t);
            if (natural(level, t)) trees.add(t);
        }
        if (trees.isEmpty()) {
            fail(p, "No trees within 14 blocks.");
            return false;
        }
        used(p, ULTIMATE);
        for (int k = 0; k < trees.size(); k++) {
            List<BlockPos> t = trees.get(k);
            Tasks.later(k * 6L, () -> {
                if (!p.isRemoved()) fell(p, d, level, t);
            });
        }
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, 20 * 30, 2));
        Fx.sound(level, p, SoundEvents.RAVAGER_ROAR, 0.8f, 0.8f);
        return true;
    }
}
