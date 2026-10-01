package dev.abps.util;

import dev.abps.data.PlayerData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Shared code for summoned helpers (Necromancer undead). */
public final class Minions {

    private Minions() {
    }

    public static Entity find(ServerLevel level, UUID id) {
        return level.getEntity(id);
    }

    /**
     * Removes dead or expired minions, keeps them near the owner and picks targets.
     * onExpire runs for each minion that timed out, right before it is removed.
     * Returns true if at least one minion expired this tick.
     */
    public static boolean tick(ServerPlayer owner, PlayerData d, Consumer<Mob> onExpire) {
        if (d.minions.isEmpty()) return false;
        ServerLevel level = (ServerLevel) owner.level();
        long now = System.currentTimeMillis();
        boolean expired = false;
        Iterator<Map.Entry<UUID, Long>> it = d.minions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity e = find(level, entry.getKey());
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                if (e == null) Targets.forgetMinion(entry.getKey());
                it.remove();
            } else if (now >= entry.getValue()) {
                if (onExpire != null) onExpire.accept(mob);
                crumble(mob);
                it.remove();
                expired = true;
            }
        }
        if (d.tickCount % 4 != 0) return expired; // retarget once a second

        LivingEntity focus = null;
        if (d.lastHit != null && now - d.lastHitTime < 15_000) {
            Entity e = level.getEntity(d.lastHit);
            if (e instanceof LivingEntity le && le.isAlive() && Targets.isEnemy(owner, le)) focus = le;
        }
        for (UUID id : d.minions.keySet()) {
            if (!(find(level, id) instanceof Mob mob)) continue;
            if (mob.distanceToSqr(owner) > 30 * 30) {
                mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                continue;
            }
            LivingEntity target = focus != null ? focus : nearestHostile(owner, mob);
            if (target != null && target != mob.getTarget()) {
                mob.setTarget(target);
            } else if (target == null && mob.getTarget() == null && mob.distanceToSqr(owner) > 36) {
                mob.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.2);
            }
        }
        return expired;
    }

    private static LivingEntity nearestHostile(ServerPlayer owner, Mob minion) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = minion.getBoundingBox().inflate(14);
        for (LivingEntity le : minion.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!(le instanceof Enemy) || !Targets.isEnemy(owner, le)) continue;
            double dist = le.distanceToSqr(minion);
            if (dist < bestDist) {
                bestDist = dist;
                best = le;
            }
        }
        return best;
    }

    public static void crumble(Entity e) {
        if (e.level() instanceof ServerLevel level) {
            dev.abps.util.Fx.burst(level, ParticleTypes.SOUL, new net.minecraft.world.phys.Vec3(e.getX(), e.getY() + 1, e.getZ()), 10, 0.3, 0.5, 0.3, 0.02);
        }
        Targets.forgetMinion(e.getUUID());
        e.discard();
    }

    public static void removeAll(ServerPlayer owner, PlayerData d) {
        ServerLevel level = (ServerLevel) owner.level();
        for (UUID id : new ArrayList<>(d.minions.keySet())) {
            Entity e = find(level, id);
            if (e != null) crumble(e);
            else Targets.forgetMinion(id);
        }
        d.minions.clear();
    }
}
