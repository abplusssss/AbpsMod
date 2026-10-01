package dev.abps.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Finding and hurting the right things. */
public final class Targets {

    public static final Set<EntityType<?>> UNDEAD = Set.of(
            EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED, EntityTypes.ZOMBIE_VILLAGER,
            EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.WITHER_SKELETON, EntityTypes.BOGGED,
            EntityTypes.PHANTOM, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.ZOGLIN,
            EntityTypes.SKELETON_HORSE, EntityTypes.ZOMBIE_HORSE, EntityTypes.WITHER);

    /** True while an ability is dealing damage, so combat passives don't stack on it. */
    public static boolean abilityDamage = false;

    /** Summoned helper -> owner. Summons are never saved, so this only needs to live in memory. */
    private static final Map<UUID, UUID> minionOwners = new HashMap<>();
    public static final String MINION_TAG = "abps_minion";

    private Targets() {
    }

    public static void damage(LivingEntity target, double amount, ServerPlayer source) {
        if (!(target.level() instanceof ServerLevel level)) return;
        abilityDamage = true;
        try {
            target.hurtServer(level, source.damageSources().playerAttack(source), (float) amount);
        } finally {
            abilityDamage = false;
        }
    }

    public static boolean isPlayerTarget(Entity e) {
        return e instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator();
    }

    /** Mobs and players an ability is allowed to hit. */
    public static boolean isEnemy(ServerPlayer caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof ServerPlayer) return isPlayerTarget(e);
        if (e instanceof OwnableEntity own && own.getOwner() == caster) return false;
        return !caster.getUUID().equals(minionOwner(e));
    }

    public static List<LivingEntity> enemiesNear(ServerPlayer caster, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity le : caster.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (isEnemy(caster, le) && le.position().distanceToSqr(center) <= radius * radius) out.add(le);
        }
        return out;
    }

    /** The enemy the player is looking at, if nothing solid is in the way. */
    public static LivingEntity lookTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double maxDist = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        LivingEntity best = null;
        double bestDist = maxDist;
        AABB search = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
        for (Entity e : p.level().getEntities(p, search, en -> isEnemy(p, en))) {
            AABB box = e.getBoundingBox().inflate(0.4);
            Optional<Vec3> hit = box.clip(eye, end);
            if (box.contains(eye)) return (LivingEntity) e;
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    /** Where the player is aiming: the block they look at, or a point in the air. */
    /**
     * Where an area ability should land: the aimed spot, dropped onto the ground (or the water surface) below it.
     * Aiming at the sky puts it on the ground ahead of you instead of floating in the air, and aiming at a wall
     * puts it at the foot of the wall instead of inside it.
     */
    public static Vec3 groundPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        net.minecraft.world.phys.BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, p));
        Vec3 at = end;
        if (hit.getType() == HitResult.Type.BLOCK) {
            at = hit.getLocation();
            var face = hit.getDirection();
            // Step off a wall or ceiling so the drop below starts in open air
            if (face != net.minecraft.core.Direction.UP) at = at.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        }
        net.minecraft.world.phys.BlockHitResult down = p.level().clip(new ClipContext(at.add(0, 0.2, 0), at.add(0, -48, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, p));
        if (down.getType() == HitResult.Type.BLOCK) return down.getLocation().add(0, 0.05, 0);
        return new Vec3(at.x, p.getY(), at.z);
    }

    /** Where a straight line ability ends: the first block it hits, or its full range. */
    public static Vec3 aimPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.BLOCK) return hit.getLocation().add(0, 0.1, 0);
        return end;
    }

    public static void pushAway(Vec3 from, Entity target, double strength, double up) {
        Vec3 v = target.position().subtract(from);
        v = new Vec3(v.x, 0, v.z);
        if (v.lengthSqr() < 0.01) v = new Vec3(0, 0, 0.1);
        velocity(target, v.normalize().scale(strength).add(0, up, 0));
    }

    /** Sets velocity and makes sure players get it too. */
    public static void velocity(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.syncVelocity = true;
    }

    // ---- Rooting ----
    /** When each rooted entity is free again, so an earlier, shorter root doesn't free it early. */
    private static final java.util.Map<UUID, Long> rootedUntil = new java.util.concurrent.ConcurrentHashMap<>();

    public static void root(LivingEntity e, int ticks) {
        Mods.set(e, Attributes.MOVEMENT_SPEED, "root", -1, Mods.MULT);
        Mods.set(e, Attributes.JUMP_STRENGTH, "root", -1, Mods.MULT);
        velocity(e, new Vec3(0, Math.min(0, e.getDeltaMovement().y), 0));
        long until = Tasks.now() + ticks;
        rootedUntil.merge(e.getUUID(), until, Math::max);
        Tasks.later(ticks, () -> {
            Long end = rootedUntil.get(e.getUUID());
            if (end != null && end > Tasks.now()) return; // a longer root is still running
            rootedUntil.remove(e.getUUID());
            Mods.remove(e, Attributes.MOVEMENT_SPEED, "root");
            Mods.remove(e, Attributes.JUMP_STRENGTH, "root");
        });
    }

    public static boolean isRooted(LivingEntity e) {
        Long end = rootedUntil.get(e.getUUID());
        return end != null && end > Tasks.now();
    }

    // ---- Minions ----
    public static void markMinion(Entity e, UUID owner) {
        minionOwners.put(e.getUUID(), owner);
        e.addTag(MINION_TAG);
    }

    public static UUID minionOwner(Entity e) {
        return e == null ? null : minionOwners.get(e.getUUID());
    }

    public static void forgetMinion(UUID id) {
        minionOwners.remove(id);
    }
}
