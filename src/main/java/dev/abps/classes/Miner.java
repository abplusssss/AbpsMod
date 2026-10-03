package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.FakeBlocks;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Miner extends AttributeClass {

    private static final int SENSE_RADIUS = 12;
    private static final int SENSE_LIMIT = 80;

    @Override public String id() { return "miner"; }
    @Override public String name() { return "Miner"; }
    @Override public String color() { return "#FFC107"; }
    @Override public String color2() { return "#FF6F00"; }
    @Override public Item icon() { return Items.DIAMOND_PICKAXE; }
    @Override public String symbol() { return "⛏"; }
    @Override public String tagline() { return "Dig deep and dig fast."; }
    @Override public String mastery() { return "Raw iron, gold and copper drop as ingots."; }
    @Override public Role role() { return Role.GATHERER; }

    @Override
    public double gatherCharge(BlockState state) {
        return isOre(state) ? 0.01 : state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? 0.0008 : 0;
    }

    private double oreSpeed(int lvl) { return lerp(lvl, 0.70, 1.60); }
    private double stoneSpeed(int lvl) { return lerp(lvl, 0.55, 1.20); }
    private int prospect(int lvl) { return (int) Math.round(lerp(lvl, 3, 10)); }
    private double reach(int lvl) { return lerp(lvl, 1.0, 2.0); }
    private int fortune(int lvl) { return lvl >= 25 ? 3 : lvl >= 15 ? 2 : 1; }
    private double oreXp(int lvl) { return lerp(lvl, 1.5, 2.5); }
    private double durability(int lvl) { return lerp(lvl, 0.30, 0.60); }
    private double senseTime(int lvl) { return lerp(lvl, 10, 16); }
    private int tunnelDepth(int lvl) { return (int) Math.round(lerp(lvl, 8, 12)); }
    private double rushTime(int lvl) { return lerp(lvl, 20, 30); }
    private double shatterDamage(int lvl) { return lerp(lvl, 12, 17); }

    public static boolean isOre(BlockState state) {
        if (state.is(Blocks.ANCIENT_DEBRIS)) return true;
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_ore");
    }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Ores break " + pct(oreSpeed(lvl)) + " faster",
                "Stone and other pickaxe blocks break " + pct(stoneSpeed(lvl)) + " faster",
                "Mine at full speed underwater",
                "+" + num(reach(lvl)) + " block mining reach",
                "+" + fortune(lvl) + " Fortune on ores with a pickaxe",
                "Ores drop " + mult(oreXp(lvl)) + " XP",
                "Pickaxes take " + pct(durability(lvl)) + " less durability damage",
                "Prospector: mining an ore also mines up to " + prospect(lvl) + " touching ores of the same kind (sneak to stop it)");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Take 30% more fall damage",
                "Deal 20% less damage with swords",
                "Swim slower in water");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Excavate";
            case 2 -> "Ore Sense";
            case 3 -> "Tunnel Bore";
            case 4 -> "Gold Rush";
            default -> "Earthshatter";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Break a 3x3x3 cube where you look. Needs a pickaxe.";
            case 2 -> "Ores within " + SENSE_RADIUS + " blocks glow through walls for " + num(senseTime(lvl)) + "s. Only you see it.";
            case 3 -> "Dig a 3x3 tunnel " + tunnelDepth(lvl) + " blocks deep where you look. Needs a pickaxe.";
            case 4 -> "For " + num(rushTime(lvl)) + "s get Haste III, +2 Fortune and double ore XP.";
            default -> "Slam the ground. A line of stone spikes erupts 14 blocks forward, dealing "
                    + num(shatterDamage(lvl)) + " damage, launching and slowing everything it hits.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 3;
            case 2 -> 30;
            case 3 -> 25;
            default -> 240;
        };
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.SUBMERGED_MINING_SPEED, "miner_underwater", 0.8, Mods.ADD);
        Mods.set(p, Attributes.BLOCK_INTERACTION_RANGE, "miner_reach", reach(d.level), Mods.ADD);
        Mods.set(p, Attributes.FALL_DAMAGE_MULTIPLIER, "miner_fall", 0.3, Mods.ADD);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        // Slower swimming: more water drag and less push
        boolean water = p.isInWater();
        Mods.toggle(p, water, Attributes.WATER_MOVEMENT_EFFICIENCY, "miner_swim", 0.5, Mods.ADD);
        Mods.toggle(p, water, Attributes.MOVEMENT_SPEED, "miner_swim", -0.7, Mods.MULT);

        // Drop outlines for ores that are gone
        if (!d.highlights.isEmpty() && d.tickCount % 2 == 0) {
            d.highlights.entrySet().removeIf(e -> {
                if (isOre(p.level().getBlockState(BlockPos.of(e.getKey())))) return false;
                FakeBlocks.hide(p, e.getValue());
                return true;
            });
        }
        if (d.buff("goldrush") && d.tickCount % 2 == 0) {
            Fx.burst(level(p), ParticleTypes.WAX_ON, p.position().add(0, 1, 0), 2, 0.4, 0.05);
        }
    }

    @Override
    public void onBlockAttack(ServerPlayer p, PlayerData d, BlockState state) {
        double bonus = isOre(state) ? oreSpeed(d.level) : state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? stoneSpeed(d.level) : 0;
        Mods.toggle(p, bonus > 0, Attributes.BLOCK_BREAK_SPEED, "miner_break", bonus, Mods.MULT);
    }

    private Holder<Enchantment> ench(ServerPlayer p, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    @Override
    public ItemStack dropTool(ServerPlayer p, PlayerData d, BlockState state, ItemStack tool) {
        if (!isOre(state) || !tool.is(ItemTags.PICKAXES)) return tool;
        Holder<Enchantment> silk = ench(p, Enchantments.SILK_TOUCH);
        if (tool.getEnchantments().getLevel(silk) > 0) return tool;
        Holder<Enchantment> fortune = ench(p, Enchantments.FORTUNE);
        ItemStack fake = tool.copy();
        int bonus = fortune(d.level) + (d.buff("goldrush") ? 2 : 0);
        fake.enchant(fortune, tool.getEnchantments().getLevel(fortune) + bonus);
        return fake;
    }

    @Override
    public void modifyDrops(ServerPlayer p, PlayerData d, BlockState state, List<ItemStack> drops) {
        if (!mastered(d) || !isOre(state)) return;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack s = drops.get(i);
            Item ingot = s.is(Items.RAW_IRON) ? Items.IRON_INGOT : s.is(Items.RAW_GOLD) ? Items.GOLD_INGOT
                    : s.is(Items.RAW_COPPER) ? Items.COPPER_INGOT : null;
            if (ingot != null) drops.set(i, new ItemStack(ingot, s.getCount()));
        }
    }

    /** Rough average XP vanilla gives for each ore, used to add the bonus. */
    private static double baseXp(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("diamond") || path.contains("emerald")) return 5;
        if (path.contains("lapis") || path.contains("quartz")) return 3.5;
        if (path.contains("redstone")) return 3;
        if (path.contains("coal")) return 1;
        if (path.contains("nether_gold")) return 0.5;
        return 0;
    }

    @Override
    public void afterBlockBreak(ServerPlayer p, PlayerData d, ServerLevel level, BlockPos pos, BlockState state) {
        if (!isOre(state)) return;
        Integer id = d.highlights.remove(pos.asLong());
        if (id != null) FakeBlocks.hide(p, id);
        dev.abps.util.Vfx.burst(level, Vec3.atCenterOf(pos), dev.abps.util.Vfx.tint(oreColor(state)), 8, 0.14, 0.14f, 14, oreColor(state));
        if (p.isCreative()) return;
        prospect(p, d, level, pos, state);
        double base = baseXp(state);
        if (base <= 0) return;
        double factor = oreXp(d.level) * (d.buff("goldrush") ? 2 : 1) - 1;
        int extra = (int) Math.round(base * factor);
        if (extra > 0) ExperienceOrb.award(level, Vec3.atCenterOf(pos), extra);
        if (mastered(d)) Fx.burst(level, ParticleTypes.FLAME, Vec3.atCenterOf(pos), 4, 0.2, 0.01);
    }

    private static boolean prospecting;

    /** Prospector: a pickaxe pulls a few touching ores of the same kind out with the first one. */
    private void prospect(ServerPlayer p, PlayerData d, ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = p.getMainHandItem();
        if (prospecting || p.isShiftKeyDown() || !tool.is(ItemTags.PICKAXES)) return;
        String kind = state.getBlock().getDescriptionId().replace("deepslate_", "");
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(java.util.List.of(pos));
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(java.util.List.of(pos));
        int max = prospect(d.level);
        while (!queue.isEmpty() && found.size() < max) {
            BlockPos c = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos n = c.relative(dir);
                if (!seen.add(n)) continue;
                BlockState s = level.getBlockState(n);
                if (isOre(s) && s.getBlock().getDescriptionId().replace("deepslate_", "").equals(kind)) {
                    found.add(n);
                    queue.add(n);
                    if (found.size() >= max) break;
                }
            }
        }
        prospecting = true;
        try {
            for (BlockPos n : found) {
                if (tool.isEmpty() || tool.getDamageValue() >= tool.getMaxDamage() - 2) break;
                p.gameMode.destroyBlock(n);
            }
        } finally {
            prospecting = false;
        }
    }

    @Override
    public boolean saveDurability(ServerPlayer p, PlayerData d, ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) && rand() < durability(d.level);
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        return hit.melee() && hit.weapon().is(ItemTags.SWORDS) ? 0.8 : 1;
    }

    private static boolean canBreak(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        float hardness = s.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness < 50;
    }

    private boolean holdingPickaxe(ServerPlayer p) {
        return p.getMainHandItem().is(ItemTags.PICKAXES);
    }

    private BlockHitResult lookBlock(ServerPlayer p) {
        double range = Mods.value(p, Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 eye = p.getEyePosition();
        return p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
    }

    // ---- Ability 1: Excavate ----
    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        if (!holdingPickaxe(p)) {
            fail(p, "Hold a pickaxe to use Excavate.");
            return false;
        }
        BlockHitResult hit = lookBlock(p);
        if (hit.getType() != HitResult.Type.BLOCK) {
            fail(p, "Look at a block first.");
            return false;
        }
        ServerLevel level = level(p);
        BlockPos center = hit.getBlockPos();
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!holdingPickaxe(p)) break; // tool broke
            BlockPos at = pos.immutable();
            // destroyBlock goes through the normal break events, so land claim mods still work
            if (canBreak(level, at) && p.gameMode.destroyBlock(at)) broken++;
        }
        if (broken == 0) {
            fail(p, "Nothing here can be broken.");
            return false;
        }
        Vec3 c = Vec3.atCenterOf(center);
        castAim = c;
        dev.abps.util.Fancy.impact(level, c, 1.8f, rgb(), rgb2());
        dev.abps.util.Vfx.burst(level, c, Blocks.STONE.defaultBlockState(), 14, 0.26, 0.24f, 18, -1);
        dev.abps.util.Vfx.zigzag(level, c.add(0, 2, 0), c.add(0, -2, 0), 4, 0.9, 0.09f, dev.abps.util.Vfx.tint(rgb()), 10, rgb());
        Fx.burst(level, ParticleTypes.CLOUD, c, 20, 1, 0.02);
        Fx.burst(level, Fx.dust(rgb(), 1.2f), c, 25, 1.2, 0);
        Fx.sound(level, c, SoundEvents.DEEPSLATE_BREAK, 1f, 0.7f);
        Fx.shakeNear(level, c, 6, 4, 0.3f);
        used(p, 1);
        return true;
    }

    // ---- Ability 2: Ore Sense ----
    private static int oreColor(BlockState state) {
        String n = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (n.contains("diamond")) return 0x4DD0E1;
        if (n.contains("emerald")) return 0x00E676;
        if (n.contains("gold")) return 0xFFD600;
        if (n.contains("iron")) return 0xD7A77A;
        if (n.contains("redstone")) return 0xFF1744;
        if (n.contains("lapis")) return 0x2962FF;
        if (n.contains("copper")) return 0xFF8A50;
        if (n.contains("quartz")) return 0xFFFFFF;
        if (state.is(Blocks.ANCIENT_DEBRIS)) return 0x8D6E63;
        return 0x616161; // coal
    }

    private void clearHighlights(ServerPlayer p, PlayerData d) {
        if (d.highlights.isEmpty()) return;
        FakeBlocks.hide(p, d.highlights.values().stream().mapToInt(Integer::intValue).toArray());
        d.highlights.clear();
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        clearHighlights(p, d);
        ServerLevel level = level(p);
        BlockPos c = p.blockPosition();
        int minY = Math.max(level.getMinY(), c.getY() - SENSE_RADIUS);
        int maxY = Math.min(level.getMaxY(), c.getY() + SENSE_RADIUS);
        int found = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        outer:
        for (int x = -SENSE_RADIUS; x <= SENSE_RADIUS; x++) {
            for (int z = -SENSE_RADIUS; z <= SENSE_RADIUS; z++) {
                for (int y = minY; y <= maxY; y++) {
                    m.set(c.getX() + x, y, c.getZ() + z);
                    BlockState s = level.getBlockState(m);
                    if (!isOre(s)) continue;
                    d.highlights.put(m.asLong(), FakeBlocks.show(p, m.immutable(), s, oreColor(s)));
                    if (++found >= SENSE_LIMIT) break outer;
                }
            }
        }
        if (found == 0) {
            fail(p, "No ores within " + SENSE_RADIUS + " blocks.");
            return false;
        }
        long ms = (long) (senseTime(d.level) * 1000);
        Tasks.later(ms / 50, () -> clearHighlights(p, d));
        Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
        // A pulse ring going out from the player
        Tasks.repeat(6, 2, step -> Fx.ring(level, Fx.dust(rgb(), 1f), p.position(), 1.5 + step * 1.8, 20 + step * 8));
        dev.abps.util.Fancy.sigil(level, p.position(), SENSE_RADIUS, 10, rgb(), rgb2(), 30);
        dev.abps.util.Vfx.sphere(level, p.position().add(0, 1, 0), 1, SENSE_RADIUS, 36, dev.abps.util.Vfx.tint(rgb()), 0.18f, 20, rgb());
        used(p, 2);
        dev.abps.AbpsMod.service().actionBar(p, gradient("<bold>✦ Ore Sense</bold>") + " <gray>found <white>" + found + "</white> ores.");
        return true;
    }

    // ---- Ability 3: Tunnel Bore ----
    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        if (!holdingPickaxe(p)) {
            fail(p, "Hold a pickaxe to use Tunnel Bore.");
            return false;
        }
        Vec3 look = p.getLookAngle();
        double ax = Math.abs(look.x), ay = Math.abs(look.y), az = Math.abs(look.z);
        int sx = 0, sy = 0, sz = 0;
        if (ay >= ax && ay >= az) sy = look.y > 0 ? 1 : -1;
        else if (ax >= az) sx = look.x > 0 ? 1 : -1;
        else sz = look.z > 0 ? 1 : -1;

        // Horizontal tunnels are centered on your chest, vertical ones on your feet
        BlockPos origin = sy == 0 ? p.blockPosition().above() : p.blockPosition();
        int depth = tunnelDepth(d.level);
        ServerLevel level = level(p);
        castAim = Vec3.atCenterOf(origin.offset(sx * depth, sy * depth, sz * depth));
        int[] broken = {0};
        final int fx = sx, fy = sy, fz = sz;
        // Dig one slice per tick so it looks like a drill going forward
        Tasks.repeat(depth, 1, step -> {
            if (p.isRemoved() || !holdingPickaxe(p)) return;
            BlockPos center = origin.offset(fx * (step + 1), fy * (step + 1), fz * (step + 1));
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    BlockPos t = fx != 0 ? center.offset(0, a, b) : fy != 0 ? center.offset(a, 0, b) : center.offset(a, b, 0);
                    if (canBreak(level, t) && p.gameMode.destroyBlock(t)) broken[0]++;
                }
            }
            Fx.burst(level, ParticleTypes.CLOUD, Vec3.atCenterOf(center), 6, 0.8, 0.02);
            dev.abps.util.Vfx.ring(level, Vec3.atCenterOf(center), new Vec3(fx, fy, fz), 0.5, 2.3, 12, dev.abps.util.Vfx.tint(rgb()), 0.1f, 7, rgb());
            if (step % 3 == 0) dev.abps.util.Fancy.impact(level, Vec3.atCenterOf(center), 0.9f, rgb(), rgb2());
            if (step % 3 == 0) Fx.sound(level, Vec3.atCenterOf(center), SoundEvents.GRINDSTONE_USE, 0.8f, 0.6f);
        });
        if (sy < 0) d.noFallUntil = now() + 4000;
        Fx.sound(level, p, SoundEvents.DEEPSLATE_BREAK, 1f, 0.5f);
        used(p, 3);
        return true;
    }

    // ---- Ability 4: Gold Rush ----
    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        long ms = (long) (rushTime(d.level) * 1000);
        d.setBuff("goldrush", ms);
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, (int) (ms / 50), 2));
        ServerLevel level = level(p);
        Fx.burst(level, ParticleTypes.WAX_ON, p.position().add(0, 1, 0), 50, 0.6, 0.3);
        Fx.spiral(level, Fx.dust(0xFFD600, 1.2f), p.position(), 1.2, 2.5, 40, 0);
        dev.abps.util.Vfx.helix(level, p.position(), 1.1, 2.6, 2.5, 24, dev.abps.util.Vfx.tint(0xFFD600), 0.15f, 16, 0xFFD600);
        dev.abps.util.Vfx.sphere(level, p.position().add(0, 1, 0), 0.4, 3, 24, dev.abps.util.Vfx.tint(0xFFD600), 0.16f, 14, 0xFFD600);
        dev.abps.util.Fancy.aura(level, p, (int) (ms / 50), 0xFFD600, 0xFF6F00);
        Fx.sound(level, p, SoundEvents.PLAYER_LEVELUP, 1f, 0.7f);
        Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        Fx.screen(p, Fx.TINT, 0xFFD600, 20, 0.15f);
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Earthshatter ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (dir.lengthSqr() < 0.01) {
            fail(p, "Look forward, not straight up or down.");
            return false;
        }
        dir = dir.normalize();
        ServerLevel level = level(p);
        double dmg = shatterDamage(d.level);
        Set<UUID> hit = new HashSet<>();
        Vec3 start = p.position();
        Vec3 step = dir;
        dev.abps.util.Fancy.sigil(level, start, 4, 8, rgb(), rgb2(), 26);
        dev.abps.util.Vfx.beam(level, start.add(0, 0.15, 0), start.add(dir.scale(15)).add(0, 0.15, 0), 0.16f, dev.abps.util.Vfx.tint(0x8D6E63), 20, 0x8D6E63);
        Fx.sound(level, p, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.6f);
        Tasks.repeat(14, 1, i -> {
            if (p.isRemoved()) return;
            Vec3 at = start.add(step.scale(i + 1.5));
            // Find the ground under this point
            BlockPos ground = BlockPos.containing(at);
            for (int k = 0; k < 4 && level.getBlockState(ground).isAir(); k++) ground = ground.below();
            BlockState under = level.getBlockState(ground);
            if (under.isAir()) under = Blocks.STONE.defaultBlockState();
            Vec3 top = new Vec3(at.x, ground.getY() + 1, at.z);
            float h = 1.2f + (float) (rand() * 1.4);
            FakeBlocks.temp(level, top, Blocks.POINTED_DRIPSTONE.defaultBlockState(), new Vector3f(0.6f, 0.05f, 0.6f),
                    new Vector3f(0.9f, h, 0.9f), 3, 30);
            Fx.burst(level, Fx.block(under), top, 16, 0.4, 0.1, 0.4, 0.1);
            dev.abps.util.Vfx.groundRing(level, top, 0.3, 2.4, 12, dev.abps.util.Vfx.tint(0x8D6E63), 0.18f, 8, 0x8D6E63);
            dev.abps.util.Vfx.flash(level, top.add(0, 0.6, 0), 1.6f, dev.abps.util.Vfx.tint(rgb()), 5, rgb());
            if (i % 2 == 0) Fx.sound(level, top, SoundEvents.POINTED_DRIPSTONE_LAND, 1f, 0.6f);
            for (LivingEntity e : Targets.enemiesNear(p, top.add(0, 0.5, 0), 1.9)) {
                if (!hit.add(e.getUUID())) continue;
                Targets.damage(e, dmg, p);
                Targets.velocity(e, e.getDeltaMovement().add(0, 0.9, 0));
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            }
            Fx.shakeNear(level, top, 8, 3, 0.5f);
        });
        Fx.burst(level, ParticleTypes.EXPLOSION, start, 2, 0.3, 0);
        used(p, ULTIMATE);
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        Mods.remove(p, Attributes.BLOCK_BREAK_SPEED, "miner_break");
        clearHighlights(p, d);
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        dev.abps.util.Vfx.jaws(level, at, ult ? 6 : 2.6, ult ? 16 : 7, ult ? 3.5 : 1.3, net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState(), rgb());
        dev.abps.util.Vfx.burst(level, at.add(0, 0.5, 0), net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE.defaultBlockState(), ult ? 30 : 12, 0.2, 0.22f, 18, -1);
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return switch (idx) {
            case 3 -> tunnelDepth(d.level);
            case 4 -> (int) (rushTime(d.level) * 20);
            default -> 0;
        };
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:ancient_debris"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{32, 24, 10, 3};
    }
}
