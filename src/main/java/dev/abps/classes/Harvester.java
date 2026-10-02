package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Gatherer. Farming: crops grow around you, harvests drop more, and whole fields come in at once. */
public final class Harvester extends AttributeClass {

    static final int CUE_HARVEST = 11;
    private static final int GREEN = 0x9CCC65, GOLD = 0xFFD54F;

    @Override public String id() { return "harvester"; }
    @Override public String name() { return "Harvester"; }
    @Override public String color() { return "#9CCC65"; }
    @Override public String color2() { return "#FFD54F"; }
    @Override public Item icon() { return Items.WHEAT; }
    @Override public String symbol() { return "❀"; }
    @Override public String tagline() { return "Every field is a feast."; }
    @Override public String mastery() { return "Crops you break replant themselves."; }
    @Override public Role role() { return Role.GATHERER; }

    private double extraDrops(int lvl) { return lerp(lvl, 0.30, 1.00); }
    private int growRadius(int lvl) { return (int) Math.round(lerp(lvl, 5, 9)); }
    private int growPerPulse(int lvl) { return (int) Math.round(lerp(lvl, 2, 6)); }
    private int reapRadius(int lvl) { return (int) Math.round(lerp(lvl, 3, 5)); }
    private int pulseRadius(int lvl) { return (int) Math.round(lerp(lvl, 4, 7)); }
    private double seasonTime(int lvl) { return lerp(lvl, 25, 40); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Crops you harvest have a " + pct(Math.min(1, extraDrops(lvl))) + " chance to drop double",
                "Crops within " + growRadius(lvl) + " blocks of you grow faster",
                "Food fills you up 50% more",
                "Hoes take 50% less durability damage",
                "Animals near you never run from you");
    }

    @Override
    public List<String> negatives() {
        return List.of("Gatherer: deal 25% less damage to players", "Take 10% more damage from monsters");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Reap";
            case 2 -> "Growth Pulse";
            case 3 -> "Herd Call";
            case 4 -> "Harvest Feast";
            default -> "Season of Plenty";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        int r = reapRadius(lvl) * 2 + 1;
        return switch (idx) {
            case 1 -> "Harvest every fully grown crop in a " + r + "x" + r + " area around you and replant it.";
            case 2 -> "A wave of green light grows every crop and sapling within " + pulseRadius(lvl) + " blocks a few stages.";
            case 3 -> "Animals within 20 blocks come to you, and the grown ones are ready to breed.";
            case 4 -> "Fill your hunger and the hunger of everyone near you, with Regeneration I for 10s.";
            default -> "For " + num(seasonTime(lvl)) + "s, crops within 12 blocks grow every second and every harvest drops double.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 6;
            case 2 -> 30;
            case 3 -> 45;
            default -> 60;
        };
    }

    @Override
    protected boolean authored(int idx) {
        return true;
    }

    @Override
    protected int fxTicks(int idx, PlayerData d) {
        return idx == ULTIMATE ? (int) (seasonTime(d.level) * 20) : idx == 1 ? reapRadius(d.level) : idx == 2 ? pulseRadius(d.level) : 0;
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:wheat", "minecraft:pumpkin", "minecraft:golden_carrot", "minecraft:enchanted_golden_apple"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{64, 24, 16, 1};
    }

    // ---- helpers ----

    /** A crop that is fully grown and can be harvested. */
    public static boolean ripe(BlockState s) {
        if (s.getBlock() instanceof CropBlock crop) return crop.isMaxAge(s);
        if (s.getBlock() instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
        return false;
    }

    static boolean crop(BlockState s) {
        return s.getBlock() instanceof CropBlock || s.getBlock() instanceof NetherWartBlock;
    }

    /** The same crop at age 0. */
    public static BlockState replanted(BlockState s) {
        if (s.getBlock() instanceof CropBlock crop) return crop.getStateForAge(0);
        if (s.getBlock() instanceof NetherWartBlock) return s.setValue(NetherWartBlock.AGE, 0);
        return s;
    }

    /** Grows a block one bone meal's worth, like using bone meal on it but free. */
    public static boolean grow(ServerLevel level, BlockPos pos) {
        return BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos);
    }

    /** Harvests one ripe crop through the normal block break (so claims and drops work) and puts it back at age 0. */
    private boolean harvest(ServerPlayer p, ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!ripe(s)) return false;
        if (!p.gameMode.destroyBlock(pos)) return false;
        if (level.getBlockState(pos).isAir() && replanted(s).canSurvive(level, pos)) {
            level.setBlock(pos, replanted(s), Block.UPDATE_ALL);
        }
        return true;
    }

    // ---- passives ----

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (d.tickCount % 8 != 0) return; // every 2 seconds
        ServerLevel level = level(p);
        int r = growRadius(d.level);
        int tries = growPerPulse(d.level) * (d.buff("season") ? 6 : 1);
        BlockPos c = p.blockPosition();
        for (int k = 0; k < tries * 6 && tries > 0; k++) {
            BlockPos at = c.offset((int) Math.round((rand() * 2 - 1) * r), (int) Math.round((rand() * 2 - 1) * 2), (int) Math.round((rand() * 2 - 1) * r));
            BlockState s = level.getBlockState(at);
            if (!crop(s) || ripe(s)) continue;
            if (grow(level, at)) {
                tries--;
                if (rand() < 0.3) cue(p, CUE_HARVEST, Vec3.atBottomCenterOf(at), Vec3.atBottomCenterOf(at).add(0, 1, 0), null, null, 0);
            }
        }
    }

    @Override
    public void modifyDrops(ServerPlayer p, PlayerData d, BlockState state, List<ItemStack> drops) {
        if (!ripe(state)) return;
        boolean doubled = d.buff("season") || rand() < extraDrops(d.level);
        if (!doubled) return;
        int n = drops.size();
        for (int i = 0; i < n; i++) drops.add(drops.get(i).copy());
    }

    @Override
    public void afterBlockBreak(ServerPlayer p, PlayerData d, ServerLevel level, BlockPos pos, BlockState state) {
        if (!mastered(d) || !ripe(state)) return;
        // Mastery: replant on the next tick so the break has finished
        Tasks.later(1, () -> {
            if (level.getBlockState(pos).isAir()) level.setBlock(pos, replanted(state), Block.UPDATE_ALL);
        });
    }

    @Override
    public double foodHealMultiplier(PlayerData d) {
        return 1.5;
    }

    @Override
    public boolean saveDurability(ServerPlayer p, PlayerData d, ItemStack stack) {
        return stack.is(ItemTags.HOES) && rand() < 0.5;
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return source.getEntity() instanceof net.minecraft.world.entity.monster.Enemy ? 1.1 : 1;
    }

    // ---- abilities ----

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        int r = reapRadius(d.level);
        BlockPos c = p.blockPosition();
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, 1, r))) {
            if (harvest(p, level, pos.immutable())) count++;
        }
        if (count == 0) {
            fail(p, "No fully grown crops within " + r + " blocks.");
            return false;
        }
        used(p, 1);
        Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
        dev.abps.AbpsMod.service().actionBar(p, gradient("<bold>✦ Reap</bold>") + " <gray>harvested <white>" + count + "</white> crops.");
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        int r = pulseRadius(d.level);
        BlockPos c = p.blockPosition();
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
            BlockPos at = pos.immutable();
            if (at.distSqr(c) > r * r + 4) continue;
            BlockState s = level.getBlockState(at);
            if (crop(s) && ripe(s)) continue;
            if (s.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock && !s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
                boolean any = false;
                for (int k = 0; k < 3; k++) any |= grow(level, at);
                if (any) grown++;
            }
        }
        if (grown == 0) {
            fail(p, "Nothing within " + r + " blocks can grow.");
            return false;
        }
        used(p, 2);
        Fx.sound(level, p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
        Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, new AABB(p.blockPosition()).inflate(20));
        if (animals.isEmpty()) {
            fail(p, "No animals within 20 blocks.");
            return false;
        }
        used(p, 3);
        for (Animal a : animals) {
            Vec3 to = p.position().subtract(a.position());
            double dist = to.length();
            if (dist > 3) a.getNavigation().moveTo(p, 1.3);
            if (a.getAge() == 0 && a.canFallInLove()) a.setInLove(p);
        }
        Fx.sound(level, p, SoundEvents.BELL_RESONATE, 1f, 1.4f);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        used(p, 4);
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(8))) {
            o.getFoodData().setFoodLevel(20);
            o.getFoodData().setSaturation(Math.max(o.getFoodData().getSaturationLevel(), 12f));
            o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            Fx.sound(level, o, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1f);
        }
        Fx.sound(level, p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        long ms = (long) (seasonTime(d.level) * 1000);
        d.setBuff("season", ms);
        used(p, ULTIMATE);
        ServerLevel level = level(p);
        Fx.sound(level, p, SoundEvents.PLAYER_LEVELUP, 1f, 0.6f);
        // Grow the whole field around you every second
        Tasks.repeat((int) (ms / 1000), 20, step -> {
            if (p.isRemoved() || !d.buff("season")) return;
            BlockPos c = p.blockPosition();
            int n = 0;
            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-12, -2, -12), c.offset(12, 2, 12))) {
                BlockState s = level.getBlockState(pos);
                if (crop(s) && !ripe(s) && rand() < 0.5) {
                    grow(level, pos.immutable());
                    if (++n > 160) break;
                }
            }
        });
        return true;
    }

    /** Gatherers: their ultimate charges from harvesting too. */
    @Override
    public double gatherCharge(BlockState state) {
        return ripe(state) ? 0.006 : 0;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
    }

    @SuppressWarnings("unused")
    private static boolean enemy(ServerPlayer p, LivingEntity e) {
        return Targets.isEnemy(p, e);
    }
}
