package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.FakeBlocks;
import dev.abps.util.Fx;
import dev.abps.util.Minions;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Druid extends AttributeClass {

    private static final Set<Block> NATURE = Set.of(Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM,
            Blocks.ROOTED_DIRT, Blocks.MOSS_CARPET, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.FARMLAND);
    private static final List<Holder<MobEffect>> BAD = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOWNESS,
            MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.HUNGER, MobEffects.NAUSEA, MobEffects.MINING_FATIGUE);
    private static final double ZONE_RADIUS = 6;

    @Override public String id() { return "druid"; }
    @Override public String name() { return "Druid"; }
    @Override public String color() { return "#76FF03"; }
    @Override public String color2() { return "#1B5E20"; }
    @Override public Item icon() { return Items.OAK_SAPLING; }
    @Override public String symbol() { return "❀"; }
    @Override public String tagline() { return "Nature heals you and your friends."; }
    @Override public String mastery() { return "Rejuvenate also clears bad effects and gives 4 hearts of Absorption."; }

    private double regen(int lvl) { return lerp(lvl, 0.60, 1.20); }
    private double bonusHp(int lvl) { return Math.round(lerp(lvl, 4, 8)); }
    private double poisonBonus(int lvl) { return lerp(lvl, 0.15, 0.30); }
    private double cropChance(int lvl) { return lerp(lvl, 0.30, 0.65); }
    private double grassEvery(int lvl) { return lerp(lvl, 2.5, 1); }
    private double healAmount(int lvl) { return lerp(lvl, 12, 20); }
    private double zoneTime(int lvl) { return lerp(lvl, 8, 12); }
    private double zoneDamage(int lvl) { return lerp(lvl, 3, 5); }
    private int wolfCount(int lvl) { return lvl >= 18 ? 5 : 4; }
    private double wolfTime(int lvl) { return lerp(lvl, 40, 60); }
    private double wolfDamage(int lvl) { return lerp(lvl, 6, 10); }
    private double wrathDamage(int lvl) { return lerp(lvl, 12, 18); }
    private double treeDamage(int lvl) { return lerp(lvl, 4, 6); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Heal " + pct(regen(lvl)) + " faster from food",
                "Standing on grass or moss heals 1 heart every " + num(grassEvery(lvl)) + "s",
                "Players near you heal 1 heart every 3s",
                "+" + num(bonusHp(lvl) / 2) + " hearts max health",
                "Deal +" + pct(poisonBonus(lvl)) + " damage to poisoned enemies",
                "Crops near you grow on their own",
                "Enemies that hit you in melee get poisoned for 4s",
                pct(cropChance(lvl)) + " chance for double crop drops",
                "Immune to Poison and Hunger");
    }

    @Override
    public List<String> negatives() {
        return List.of("Take 20% more damage in the Nether");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Rejuvenate";
            case 2 -> "Bramble Field";
            case 3 -> "Wolf Pack";
            case 4 -> "Nature's Wrath";
            default -> "World Tree";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Heal you and players within 10 blocks for " + num(healAmount(lvl) / 2) + " hearts, then Regeneration II for 5s.";
            case 2 -> "Grow thorns around you for " + num(zoneTime(lvl)) + "s. Enemies inside are slowed and take "
                    + num(zoneDamage(lvl)) + " damage a second. You regenerate inside.";
            case 3 -> "Call " + wolfCount(lvl) + " wolves that fight for you for " + num(wolfTime(lvl)) + "s. They deal " + num(wolfDamage(lvl)) + " damage.";
            case 4 -> "Roots burst from the ground. Enemies within 8 blocks take " + num(wrathDamage(lvl))
                    + " damage, get Poison II and can't move for 3s. Players nearby heal 8 hearts and get Regeneration II.";
            default -> "Grow a giant tree for 10s. Players within 8 blocks heal 1 heart a second and get Regeneration II and Resistance I. Enemies are slowed, poisoned and take "
                    + num(treeDamage(lvl)) + " damage a second.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 25;
            case 2 -> 30;
            case 3 -> 45;
            default -> 75;
        };
    }

    @Override
    public double foodHealMultiplier(PlayerData d) {
        return 1 + regen(d.level);
    }

    @Override
    public boolean immuneTo(Holder<MobEffect> effect) {
        return effect.equals(MobEffects.POISON) || effect.equals(MobEffects.HUNGER);
    }

    @Override
    public void applyStatic(ServerPlayer p, PlayerData d) {
        Mods.set(p, Attributes.MAX_HEALTH, "druid_hp", bonusHp(d.level), Mods.ADD);
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        return victim.hasEffect(MobEffects.POISON) ? 1 + poisonBonus(d.level) : 1;
    }

    @Override
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return p.level().dimension() == Level.NETHER ? 1.2 : 1;
    }

    @Override
    public void afterDamaged(ServerPlayer p, PlayerData d, DamageSource source, float taken) {
        if (Targets.abilityDamage || source.is(DamageTypeTags.IS_PROJECTILE)) return;
        if (source.getDirectEntity() instanceof LivingEntity att && att != p) {
            att.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
        }
    }

    private boolean onNature(ServerPlayer p) {
        BlockState feet = p.level().getBlockState(p.blockPosition());
        BlockState below = p.level().getBlockState(p.blockPosition().below());
        return NATURE.contains(feet.getBlock()) || NATURE.contains(below.getBlock()) || below.is(BlockTags.LEAVES);
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        if (!p.isAlive()) return;
        ServerLevel level = level(p);
        int every = (int) Math.round(grassEvery(d.level) * 4); // tick runs 4 times a second
        if (d.tickCount % every == 0 && onNature(p) && p.getHealth() < maxHp(p)) {
            heal(p, 2);
            Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 0.3, 0), 4, 0.3, 0.1, 0.3, 0);
        }
        if (d.tickCount % 12 == 0) {
            for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(6))) {
                if (other != p && other.isAlive() && other.gameMode() != GameType.SPECTATOR && other.getHealth() < maxHp(other)) heal(other, 2);
            }
        }
        if (d.tickCount % 20 == 10) growCrops(p);
        if (d.zone != null && d.tickCount % 4 == 0) zoneTick(p, d);
        if (Minions.tick(p, d, null) && d.minions.isEmpty()) {
            AbpsMod.service().actionBar(p, gradient("Your wolves run back into the wild."));
        }
    }

    /** Grows a few crops around the player. */
    private void growCrops(ServerPlayer p) {
        ServerLevel level = level(p);
        int grown = 0;
        for (int i = 0; i < 12 && grown < 3; i++) {
            BlockPos pos = p.blockPosition().offset((int) (rand() * 9 - 4.5), (int) (rand() * 3 - 1), (int) (rand() * 9 - 4.5));
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof CropBlock crop) || crop.isMaxAge(s)) continue;
            if (s.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, pos, s, net.minecraft.world.level.block.BonemealSource.MOB)) {
                bm.performBonemeal(level, level.getRandom(), pos, s, net.minecraft.world.level.block.BonemealSource.MOB);
                Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(pos), 3, 0.2, 0);
                grown++;
            }
        }
    }

    private void zoneTick(ServerPlayer p, PlayerData d) {
        if (now() > d.zoneUntil || !p.level().dimension().identifier().toString().equals(d.zoneDim)) {
            d.zone = null;
            return;
        }
        ServerLevel level = level(p);
        Vec3 c = d.zone;
        double dmg = zoneDamage(d.level);
        for (LivingEntity e : Targets.enemiesNear(p, c, ZONE_RADIUS)) {
            Targets.damage(e, dmg, p);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
        }
        if (p.position().distanceToSqr(c) <= ZONE_RADIUS * ZONE_RADIUS) {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, true, false, true));
        }
        Fx.ring(level, ParticleTypes.HAPPY_VILLAGER, c, ZONE_RADIUS, 30);
        Fx.burst(level, Fx.block(Blocks.OAK_LEAVES.defaultBlockState()), c.add(0, 0.2, 0), 25, ZONE_RADIUS / 2, 0.1, ZONE_RADIUS / 2, 0);
        Fx.sound(level, c, SoundEvents.SWEET_BERRY_BUSH_BREAK, 0.6f, 0.8f);
    }

    @Override
    public void modifyDrops(ServerPlayer p, PlayerData d, BlockState state, List<ItemStack> drops) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        if (rand() >= cropChance(d.level)) return;
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack s : drops) extra.add(s.copy());
        drops.addAll(extra);
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        double amount = healAmount(d.level);
        int count = 0;
        ServerLevel level = level(p);
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(10))) {
            if (other.gameMode() == GameType.SPECTATOR || !other.isAlive()) continue;
            heal(other, amount);
            other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            if (mastered(d)) {
                for (Holder<MobEffect> bad : BAD) other.removeEffect(bad);
                other.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
            }
            Fx.burst(level, ParticleTypes.HEART, other.position().add(0, 2, 0), 5, 0.4, 0.3, 0.4, 0);
            Fx.spiral(level, Fx.dust(0x76FF03, 1f), other.position(), 0.8, 2, 20, 0);
            if (other != p) count++;
        }
        Fx.ring(level, ParticleTypes.HAPPY_VILLAGER, p.position(), 3, 24);
        Fx.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        used(p, 1);
        if (count > 0) AbpsMod.service().actionBar(p, gradient("<bold>✦ Rejuvenate</bold>") + " <gray>healed you and " + count + " others.");
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        d.zone = p.position();
        d.zoneDim = p.level().dimension().identifier().toString();
        d.zoneUntil = now() + (long) (zoneTime(d.level) * 1000);
        ServerLevel level = level(p);
        // Small thorn bushes pop up around the edge
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Vec3 at = d.zone.add(Math.cos(a) * ZONE_RADIUS, 0, Math.sin(a) * ZONE_RADIUS);
            FakeBlocks.temp(level, at, Blocks.SWEET_BERRY_BUSH.defaultBlockState(), new Vector3f(0.2f), new Vector3f(0.9f), 6,
                    (int) (zoneTime(d.level) * 20));
        }
        Fx.sound(level, p, SoundEvents.AZALEA_LEAVES_PLACE, 1.2f, 0.6f);
        Fx.sound(level, p, SoundEvents.ROOTS_PLACE, 1.2f, 0.8f);
        zoneTick(p, d);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        Minions.removeAll(p, d);
        int n = wolfCount(d.level);
        long until = now() + (long) (wolfTime(d.level) * 1000);
        ServerLevel level = level(p);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 at = p.position().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5);
            Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (wolf == null) continue;
            wolf.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
            wolf.tame(p);
            wolf.setCustomName(dev.abps.util.Text.mm(gradient(p.getName().getString() + "'s Wolf")));
            wolf.setCustomNameVisible(false);
            Mods.setBase(wolf, Attributes.MAX_HEALTH, 40);
            Mods.setBase(wolf, Attributes.ATTACK_DAMAGE, wolfDamage(d.level));
            Mods.scaleBase(wolf, Attributes.MOVEMENT_SPEED, 1.2);
            wolf.setHealth(40);
            Targets.markMinion(wolf, p.getUUID());
            level.addFreshEntity(wolf);
            d.minions.put(wolf.getUUID(), until);
            Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, at.add(0, 0.5, 0), 10, 0.3, 0);
            Fx.burst(level, Fx.block(Blocks.OAK_LEAVES.defaultBlockState()), at.add(0, 0.5, 0), 15, 0.4, 0);
        }
        Fx.sound(level, p, SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 1.4f);
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        Vec3 c = p.position();
        double dmg = wrathDamage(d.level);
        ServerLevel level = level(p);
        for (LivingEntity e : Targets.enemiesNear(p, c, 8)) {
            Targets.damage(e, dmg, p);
            Targets.root(e, 60);
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            FakeBlocks.temp(level, e.position(), Blocks.OAK_LOG.defaultBlockState(), new Vector3f(0.3f, 0.1f, 0.3f),
                    new Vector3f(0.5f, 1.4f, 0.5f), 4, 60);
            Fx.burst(level, Fx.block(Blocks.OAK_LOG.defaultBlockState()), e.position(), 20, 0.3, 0.5, 0.3, 0);
        }
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(8))) {
            if (other.gameMode() == GameType.SPECTATOR || !other.isAlive()) continue;
            heal(other, 16);
            other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            Fx.burst(level, ParticleTypes.HEART, other.position().add(0, 2, 0), 4, 0.4, 0.3, 0.4, 0);
        }
        Tasks.repeat(6, 1, step -> Fx.ring(level, ParticleTypes.HAPPY_VILLAGER, c, 1.5 + step * 1.3, 12 + step * 8));
        Fx.burst(level, Fx.block(Blocks.MOSS_BLOCK.defaultBlockState()), c, 80, 4, 0.2, 4, 0);
        Fx.sound(level, c, SoundEvents.ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        Fx.sound(level, c, SoundEvents.EVOKER_CAST_SPELL, 1f, 0.8f);
        Fx.shakeNear(level, c, 10, 6, 0.5f);
        used(p, 4);
        return true;
    }

    // ---- Ultimate: World Tree ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 base = p.position();
        int life = 200;
        // Trunk and a big leafy top
        FakeBlocks.temp(level, base, Blocks.OAK_LOG.defaultBlockState(), new Vector3f(0.4f, 0.2f, 0.4f), new Vector3f(1.2f, 5f, 1.2f), 20, life);
        FakeBlocks.temp(level, base.add(0, 4.2, 0), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), new Vector3f(0.5f),
                new Vector3f(5f, 3f, 5f), 25, life);
        FakeBlocks.temp(level, base.add(0, 6.5, 0), Blocks.OAK_LEAVES.defaultBlockState(), new Vector3f(0.4f),
                new Vector3f(3f, 1.6f, 3f), 30, life);
        Fx.sound(level, base, SoundEvents.ROOTS_PLACE, 2f, 0.5f);
        Fx.sound(level, base, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
        Fx.shakeNear(level, base, 12, 10, 0.5f);
        double dmg = treeDamage(d.level);
        Tasks.repeat(20, 10, step -> {
            if (p.isRemoved()) return;
            Fx.ring(level, ParticleTypes.HAPPY_VILLAGER, base, 8, 40);
            Fx.burst(level, ParticleTypes.FALLING_SPORE_BLOSSOM, base.add(0, 6, 0), 20, 3, 1, 3, 0);
            Fx.burst(level, ParticleTypes.CHERRY_LEAVES, base.add(0, 5, 0), 10, 3, 1, 3, 0);
            if (step % 2 != 0) return;
            for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(base, base).inflate(8))) {
                if (other.gameMode() == GameType.SPECTATOR || !other.isAlive()) continue;
                heal(other, 2);
                other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1));
                other.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0));
                Fx.burst(level, ParticleTypes.HEART, other.position().add(0, 2, 0), 1, 0.3, 0);
            }
            for (LivingEntity e : Targets.enemiesNear(p, base, 8)) {
                Targets.damage(e, dmg, p);
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0));
            }
        });
        used(p, 5);
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        d.zone = null;
        Minions.removeAll(p, d);
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        dev.abps.util.Vfx.vortex(level, at, ult ? 6 : 3, ult ? 24 : 12, net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState(), 0.2f, ult ? 80 : 30, 0.7, -1);
        dev.abps.util.Vfx.vortex(level, at, ult ? 5 : 2.5, ult ? 14 : 6, net.minecraft.world.level.block.Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), 0.18f, ult ? 80 : 30, -0.9, -1);
        dev.abps.util.Vfx.burst(level, at.add(0, 0.3, 0), net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState(), 12, 0.14, 0.16f, 18, -1);
    }
}
