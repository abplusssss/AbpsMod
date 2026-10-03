package dev.abps.content;

import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the 18 AbpsMod enchantments do. Their definitions (levels, costs, which items take them) are data files made by
 * tools/gen_enchants.py; every effect lives here.
 */
public final class Enchants {

    private Enchants() {
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, AbpsMod.id(name));
    }

    public static final ResourceKey<Enchantment> LIFESTEAL = key("lifesteal"), EXECUTIONER = key("executioner"), VENOM = key("venom"),
            FROSTBITE = key("frostbite"), THUNDERSTRIKE = key("thunderstrike"), DODGE = key("dodge"), VEIN_MINER = key("vein_miner"),
            TIMBER = key("timber"), EXCAVATOR = key("excavator"), SMELTING_TOUCH = key("smelting_touch"), MAGNETIC = key("magnetic"),
            REPLANTING = key("replanting"), EXPLOSIVE_SHOT = key("explosive_shot"), HOMING = key("homing"), LEAPING = key("leaping"),
            NIGHT_OWL = key("night_owl"), SECOND_WIND = key("second_wind"), SOULBOUND = key("soulbound");

    /** The level of an enchantment on a stack, or 0. */
    public static int level(Entity holder, ItemStack stack, ResourceKey<Enchantment> k) {
        if (stack == null || stack.isEmpty()) return 0;
        return holder.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(k)
                .map(h -> stack.getEnchantments().getLevel(h)).orElse(0);
    }

    private static int worn(LivingEntity e, EquipmentSlot slot, ResourceKey<Enchantment> k) {
        return level(e, e.getItemBySlot(slot), k);
    }

    private static double rand() {
        return Math.random();
    }

    // ------------------------------------------------------------------ wiring

    private static boolean busy; // guards the multi-block breakers against re-triggering themselves
    private static final Map<UUID, Long> SECOND_WIND_READY = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> SOULBOUND_KEPT = new HashMap<>();
    private static final List<AbstractArrow> HOMING_ARROWS = new ArrayList<>();
    private static final String EXPLOSIVE_TAG = "abps_explosive_";

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Enchants::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(Enchants::afterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(Enchants::afterDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> giveBack(newPlayer));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (player instanceof ServerPlayer p && level instanceof ServerLevel sl) afterBreak(p, sl, pos, state);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AbstractArrow arrow && arrow.tickCount == 0 && arrow.getOwner() instanceof ServerPlayer p) onShoot(p, arrow);
        });
        ServerTickEvents.END_SERVER_TICK.register(Enchants::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveBack(handler.player));
    }

    // ------------------------------------------------------------------ combat

    /** Dodge: a chance to avoid an attack from a mob or player. */
    private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof ServerPlayer p) || source.getEntity() == null || source.getEntity() == p) return true;
        int lv = Math.min(3, worn(p, EquipmentSlot.CHEST, DODGE)) + Math.min(3, worn(p, EquipmentSlot.LEGS, DODGE));
        if (lv <= 0 || rand() >= 0.04 * lv) return true;
        ServerLevel level = (ServerLevel) p.level();
        Fx.burst(level, ParticleTypes.CLOUD, p.position().add(0, 1, 0), 8, 0.4, 0.04);
        Fx.sound(level, p, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 1.6f);
        AbpsMod.service().actionBar(p, "<#80DEEA>Dodged!");
        return false;
    }

    /** Executioner: more damage to enemies below 30% health. Applied by DamageHooks.modify. */
    public static float outgoing(LivingEntity victim, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer p) || source.getDirectEntity() != p || victim == p) return amount;
        int lv = level(p, p.getMainHandItem(), EXECUTIONER);
        if (lv > 0 && victim.getHealth() < victim.getMaxHealth() * 0.3f) return amount * (1f + 0.15f * lv);
        return amount;
    }

    private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
        if (taken <= 0) return;
        if (source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p && victim != p && !Targets.abilityDamage) {
            ItemStack weapon = p.getMainHandItem();
            ServerLevel level = (ServerLevel) p.level();
            int ls = level(p, weapon, LIFESTEAL);
            if (ls > 0) {
                p.heal(Math.min(4f, taken * 0.05f * ls));
                Fx.burst(level, ParticleTypes.DAMAGE_INDICATOR, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 2 + ls, 0.2, 0.05);
            }
            int venom = level(p, weapon, VENOM);
            if (venom > 0) victim.addEffect(new MobEffectInstance(MobEffects.POISON, 40 + 40 * venom, venom - 1), p);
            int frost = level(p, weapon, FROSTBITE);
            if (frost > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40 + 30 * frost, frost), p);
                Fx.burst(level, ParticleTypes.SNOWFLAKE, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 8, 0.3, 0.02);
            }
            int thunder = level(p, weapon, THUNDERSTRIKE);
            if (thunder > 0 && rand() < 0.07 * thunder) {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.snapTo(victim.getX(), victim.getY(), victim.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                Targets.damage(victim, 3 + 2 * thunder, p);
                victim.igniteForSeconds(2);
            }
        }
        // Second Wind: a burst of regeneration when you drop low, every two minutes
        if (victim instanceof ServerPlayer vp && vp.isAlive() && vp.getHealth() < vp.getMaxHealth() * 0.3f && worn(vp, EquipmentSlot.CHEST, SECOND_WIND) > 0) {
            long now = System.currentTimeMillis();
            if (now >= SECOND_WIND_READY.getOrDefault(vp.getUUID(), 0L)) {
                SECOND_WIND_READY.put(vp.getUUID(), now + 120_000L);
                vp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
                vp.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0));
                ServerLevel level = (ServerLevel) vp.level();
                Fx.burst(level, ParticleTypes.HEART, vp.position().add(0, 1.2, 0), 6, 0.5, 0.05);
                Fx.sound(level, vp, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
                AbpsMod.service().actionBar(vp, "<#FF8A80>Second Wind!");
            }
        }
    }

    // ------------------------------------------------------------------ soulbound

    /** Soulbound items that just dropped from a dead player are taken back and returned on respawn. */
    private static void afterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer p)) return;
        List<ItemStack> kept = new ArrayList<>();
        AABB box = p.getBoundingBox().inflate(4);
        for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, box)) {
            if (item.tickCount <= 1 && level(p, item.getItem(), SOULBOUND) > 0) {
                kept.add(item.getItem().copy());
                item.discard();
            }
        }
        if (!kept.isEmpty()) SOULBOUND_KEPT.computeIfAbsent(p.getUUID(), u -> new ArrayList<>()).addAll(kept);
    }

    /** Returns any soulbound items waiting for this player. Also called on join, in case they logged out dead. */
    public static void giveBack(ServerPlayer p) {
        List<ItemStack> kept = SOULBOUND_KEPT.remove(p.getUUID());
        if (kept == null) return;
        for (ItemStack s : kept) {
            if (!p.getInventory().add(s) && !s.isEmpty()) ModContent.drop(p, s);
        }
        AbpsMod.service().actionBar(p, "<#B388FF>Your soulbound items came back with you.");
    }

    // ------------------------------------------------------------------ mining

    private static void afterBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        if (busy || p.isCreative()) return;
        ItemStack tool = p.getMainHandItem();

        // Replanting works on any ripe crop broken with the hoe
        if (level(p, tool, REPLANTING) > 0) {
            BlockState young = young(state);
            if (young != null && level.getBlockState(pos).isAir() && young.canSurvive(level, pos)) level.setBlock(pos, young, Block.UPDATE_ALL);
        }
        if (p.isShiftKeyDown()) return;

        List<BlockPos> more = new ArrayList<>();
        if (level(p, tool, VEIN_MINER) > 0 && isOre(state)) {
            more.addAll(flood(level, pos, s -> s.getBlock() == state.getBlock() || sameOre(s, state), 32));
        } else if (level(p, tool, TIMBER) > 0 && state.is(BlockTags.LOGS)) {
            more.addAll(flood(level, pos, s -> s.is(BlockTags.LOGS), 96));
        } else {
            int ex = level(p, tool, EXCAVATOR);
            if (ex > 0 && tool.isCorrectToolForDrops(state)) {
                Vec3 look = p.getLookAngle();
                boolean vertical = Math.abs(look.y) > 0.75;
                boolean alongX = Math.abs(look.x) > Math.abs(look.z);
                int dx = vertical ? 0 : alongX ? (look.x > 0 ? 1 : -1) : 0, dy = vertical ? (look.y > 0 ? 1 : -1) : 0, dz = vertical || alongX ? 0 : (look.z > 0 ? 1 : -1);
                for (int depth = 0; depth < ex; depth++)
                    for (int a = -1; a <= 1; a++)
                        for (int b = -1; b <= 1; b++) {
                            if (depth == 0 && a == 0 && b == 0) continue;
                            BlockPos q = (vertical ? pos.offset(a, 0, b) : alongX ? pos.offset(0, a, b) : pos.offset(a, b, 0)).offset(dx * depth, dy * depth, dz * depth);
                            BlockState s = level.getBlockState(q);
                            if (!s.isAir() && tool.isCorrectToolForDrops(s) && s.getDestroySpeed(level, q) >= 0) more.add(q);
                        }
            }
        }
        if (more.isEmpty()) return;
        busy = true;
        try {
            for (BlockPos q : more) {
                if (tool.isEmpty() || tool.getDamageValue() >= tool.getMaxDamage() - 2) break; // never break the tool
                p.gameMode.destroyBlock(q);
            }
        } finally {
            busy = false;
        }
    }

    private static BlockState young(BlockState s) {
        if (s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) return crop.getStateForAge(0);
        if (s.getBlock() instanceof NetherWartBlock && s.getValue(NetherWartBlock.AGE) >= 3) return s.setValue(NetherWartBlock.AGE, 0);
        return null;
    }

    private static boolean isOre(BlockState s) {
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return id.endsWith("_ore") || s.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS);
    }

    private static boolean sameOre(BlockState a, BlockState b) {
        String x = a.getBlock().getDescriptionId().replace("deepslate_", ""), y = b.getBlock().getDescriptionId().replace("deepslate_", "");
        return x.equals(y);
    }

    private static List<BlockPos> flood(ServerLevel level, BlockPos start, java.util.function.Predicate<BlockState> match, int max) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && out.size() < max) {
            BlockPos c = q.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.offset(dx, dy, dz);
                        if (!seen.add(n) || n.distSqr(start) > 576) continue;
                        if (match.test(level.getBlockState(n))) {
                            out.add(n);
                            q.add(n);
                            if (out.size() >= max) return out;
                        }
                    }
        }
        return out;
    }

    /** Smelting Touch and Magnetic. Called from the block drop hook. */
    public static void modifyDrops(ServerPlayer p, List<ItemStack> drops) {
        ItemStack tool = p.getMainHandItem();
        if (level(p, tool, SMELTING_TOUCH) > 0) {
            for (int i = 0; i < drops.size(); i++) {
                ItemStack d = drops.get(i);
                Item out = smelted(d);
                if (out != null) drops.set(i, new ItemStack(out, d.getCount()));
            }
        }
        if (level(p, tool, MAGNETIC) > 0) {
            List<ItemStack> left = new ArrayList<>();
            for (ItemStack d : drops) {
                ItemStack copy = d.copy();
                if (!p.getInventory().add(copy) && !copy.isEmpty()) left.add(copy);
            }
            drops.clear();
            drops.addAll(left);
        }
    }

    private static Item smelted(ItemStack d) {
        if (d.is(Items.RAW_IRON)) return Items.IRON_INGOT;
        if (d.is(Items.RAW_GOLD)) return Items.GOLD_INGOT;
        if (d.is(Items.RAW_COPPER)) return Items.COPPER_INGOT;
        if (d.is(Items.ANCIENT_DEBRIS)) return Items.NETHERITE_SCRAP;
        if (d.is(Items.COBBLESTONE)) return Items.STONE;
        if (d.is(Items.COBBLED_DEEPSLATE)) return Items.DEEPSLATE;
        if (d.is(Items.SAND)) return Items.GLASS;
        if (d.is(Items.CLAY_BALL)) return Items.BRICK;
        if (d.is(Items.NETHERRACK)) return Items.NETHER_BRICK;
        if (d.is(Items.WET_SPONGE)) return Items.SPONGE;
        if (d.is(Items.KELP)) return Items.DRIED_KELP;
        if (d.is(ModContent.ENDITE_ORE.asItem())) return ModContent.ENDITE_SHARD;
        if (d.is(net.minecraft.tags.ItemTags.LOGS_THAT_BURN)) return Items.CHARCOAL;
        return null;
    }

    // ------------------------------------------------------------------ bows

    private static void onShoot(ServerPlayer p, AbstractArrow arrow) {
        ItemStack bow = p.getMainHandItem();
        if (bow.isEmpty() || !(bow.is(Items.BOW) || bow.is(Items.CROSSBOW))) bow = p.getOffhandItem();
        int ex = level(p, bow, EXPLOSIVE_SHOT);
        if (ex > 0) arrow.addTag(EXPLOSIVE_TAG + ex);
        if (level(p, bow, HOMING) > 0) HOMING_ARROWS.add(arrow);
    }

    /** Explosive Shot. Called when any projectile hits something; never cancels the hit. */
    public static void projectileHit(Projectile proj, HitResult hit) {
        if (!(proj.getOwner() instanceof ServerPlayer p) || !(proj.level() instanceof ServerLevel level)) return;
        int lv = 0;
        for (String t : proj.entityTags()) if (t.startsWith(EXPLOSIVE_TAG)) lv = Math.max(lv, Integer.parseInt(t.substring(EXPLOSIVE_TAG.length())));
        if (lv <= 0) return;
        proj.removeTag(EXPLOSIVE_TAG + lv);
        Vec3 at = hit.getLocation();
        for (LivingEntity e : Targets.enemiesNear(p, at, 2.5 + lv)) Targets.damage(e, 3 + 2.5 * lv, p);
        Fx.burst(level, ParticleTypes.EXPLOSION, at, 1 + lv, 0.4, 0);
        Fx.burst(level, ParticleTypes.FLAME, at, 14, 0.5, 0.08);
        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.3f);
    }

    // ------------------------------------------------------------------ ticking

    private static long ticks;

    private static void tick(MinecraftServer server) {
        ticks++;
        steerArrows();
        if (ticks % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int leap = Math.min(3, worn(p, EquipmentSlot.FEET, LEAPING));
            Mods.toggle(p, leap > 0, Attributes.JUMP_STRENGTH, "ench_leaping", 0.06 * leap, Mods.ADD);
            Mods.toggle(p, leap > 0, Attributes.SAFE_FALL_DISTANCE, "ench_leaping_fall", 1.5 * leap, Mods.ADD);
            if (worn(p, EquipmentSlot.HEAD, NIGHT_OWL) > 0 && p.level().getMaxLocalRawBrightness(p.blockPosition()) < 8) {
                MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 260) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
            }
        }
    }

    /** Homing: arrows bend toward the nearest enemy in front of them. */
    private static void steerArrows() {
        for (Iterator<AbstractArrow> it = HOMING_ARROWS.iterator(); it.hasNext(); ) {
            AbstractArrow a = it.next();
            Vec3 v = a.getDeltaMovement();
            if (a.isRemoved() || a.tickCount > 100 || v.lengthSqr() < 0.04 || !(a.getOwner() instanceof ServerPlayer p)) {
                it.remove();
                continue;
            }
            Vec3 dir = v.normalize();
            LivingEntity best = null;
            double bestScore = 0;
            for (LivingEntity e : Targets.enemiesNear(p, a.position(), 16)) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(a.position());
                double dot = to.normalize().dot(dir);
                if (dot < 0.5) continue;
                double score = dot / (1 + to.length());
                if (score > bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            if (best == null) continue;
            Vec3 want = best.position().add(0, best.getBbHeight() * 0.5, 0).subtract(a.position()).normalize();
            Vec3 steered = dir.scale(0.8).add(want.scale(0.2)).normalize().scale(v.length());
            a.setDeltaMovement(steered);
            a.syncVelocity = true;
        }
    }
}
