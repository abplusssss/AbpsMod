package dev.abps.events;

import dev.abps.AbpsMod;
import dev.abps.Service;
import dev.abps.classes.AttributeClass;
import dev.abps.classes.Hit;
import dev.abps.data.PlayerData;
import dev.abps.util.Targets;
import dev.abps.util.Text;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.UUID;

/** Everything the mixins call. Each method is small and returns quickly when it has nothing to do. */
public final class DamageHooks {

    /** The player whose food bar is ticking right now. */
    public static ServerPlayer foodOwner;

    private DamageHooks() {
    }

    private static PlayerData data(ServerPlayer p) {
        return AbpsMod.data().get(p);
    }

    private static AttributeClass cls(PlayerData d) {
        return AbpsMod.service().active(d);
    }

    /** Resolves who dealt the hit and how. Null if it wasn't a player. */
    public static Hit hitOf(ServerPlayer attacker, DamageSource source) {
        if (source.is(DamageTypes.THORNS)) return null;
        Entity direct = source.getDirectEntity();
        boolean melee = direct == attacker && (source.is(DamageTypes.PLAYER_ATTACK));
        if (!melee && !(direct instanceof Projectile)) return null;
        return new Hit(melee, false, melee ? null : direct, attacker.getMainHandItem());
    }

    // ================= Damage =================
    public static float modify(LivingEntity victim, DamageSource source, float amount) {
        if (amount > 0) amount = dev.abps.content.Enchants.outgoing(victim, source, amount);
        if (!AbpsMod.running() || amount <= 0) return amount;
        double m = 1;
        if (!Targets.abilityDamage && source.getEntity() instanceof ServerPlayer p && p != victim) {
            PlayerData d = data(p);
            AttributeClass c = cls(d);
            Hit hit = hitOf(p, source);
            if (hit != null) m *= dev.abps.items.CustomItems.outgoing(p, victim, hit);
            if (c != null && hit != null) {
                double out = c.outgoing(p, d, victim, hit);
                if (out != 1 && d.debug) {
                    AbpsMod.service().raw(p, "<dark_gray>[Debug] <gray>Hit " + victim.getName().getString() + ": "
                            + Text.num(amount) + " → " + Text.num(amount * out) + " (" + Text.mult(out) + ")");
                }
                m *= out;
            }
            if (c != null) m *= dev.abps.skills.Skills.outgoing(p, d, c, victim);
            // Gatherers aren't built for fighting players
            if (c != null && c.role() == dev.abps.classes.Role.GATHERER && victim instanceof ServerPlayer) m *= dev.abps.classes.Role.GATHERER_PVP_DAMAGE;
        }
        // Ability damage skips the hit hooks above, but skill tree damage and the gatherer PvP cut still count
        if (Targets.abilityDamage && source.getEntity() instanceof ServerPlayer ap && ap != victim) {
            PlayerData ad = data(ap);
            AttributeClass ac = cls(ad);
            if (ac != null) {
                m *= dev.abps.skills.Skills.outgoing(ap, ad, ac, victim);
                if (ac.role() == dev.abps.classes.Role.GATHERER && victim instanceof ServerPlayer) m *= dev.abps.classes.Role.GATHERER_PVP_DAMAGE;
            }
        }
        if (victim instanceof ServerPlayer vp) {
            PlayerData d = data(vp);
            AttributeClass c = cls(d);
            if (c != null) {
                m *= dev.abps.skills.Skills.incoming(d, c);
                double in = Math.max(0, c.incoming(vp, d, source, amount));
                if (in != 1 && d.debug) {
                    AbpsMod.service().raw(vp, "<dark_gray>[Debug] <gray>Took " + source.getMsgId() + ": "
                            + Text.num(amount * m) + " → " + Text.num(amount * m * in) + " (" + Text.mult(in) + ")");
                }
                m *= in;
            }
        }
        return (float) (amount * m);
    }

    /** Decides if damage happens at all. Called by the Fabric ALLOW_DAMAGE event. */
    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim instanceof ServerPlayer sp && !dev.abps.content.SetBonuses.allowDamage(sp, source)) return false;
        if (!AbpsMod.running()) return true;
        Entity src = source.getEntity();
        // Minions never hurt their owner or each other, and owners can't hurt their own minions
        UUID srcOwner = src instanceof ServerPlayer ? src.getUUID() : Targets.minionOwner(src);
        if (srcOwner != null) {
            UUID victimOwner = victim instanceof ServerPlayer ? victim.getUUID() : Targets.minionOwner(victim);
            boolean srcIsMinion = !(src instanceof ServerPlayer);
            boolean victimIsMinion = !(victim instanceof ServerPlayer) && victimOwner != null;
            if ((srcIsMinion || victimIsMinion) && srcOwner.equals(victimOwner)) return false;
        }
        if (victim instanceof ServerPlayer p) {
            PlayerData d = data(p);
            if (source.is(DamageTypeTags.IS_FALL) && d.noFallUntil > System.currentTimeMillis()) return false;
            AttributeClass c = cls(d);
            if (c == null) return true;
            if (c.incoming(p, d, source, amount) <= 0) return false;
            if (!c.allowDamage(p, d, source)) return false;
        }
        return true;
    }

    // ================= Effects, food, hunger =================
    public static boolean immune(LivingEntity e, MobEffectInstance effect) {
        if (!(e instanceof ServerPlayer p) || !AbpsMod.running()) return false;
        PlayerData d = AbpsMod.data().peek(p.getUUID());
        if (d == null) return false;
        AttributeClass c = cls(d);
        return c != null && c.immuneTo(effect.getEffect());
    }

    public static float foodHeal(float amount) {
        ServerPlayer p = foodOwner;
        if (p == null || !AbpsMod.running()) return amount;
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        return c == null ? amount : (float) (amount * c.foodHealMultiplier(d));
    }

    public static float hunger(Player player, float amount) {
        if (!(player instanceof ServerPlayer p) || !AbpsMod.running()) return amount;
        PlayerData d = AbpsMod.data().peek(p.getUUID());
        if (d == null) return amount;
        AttributeClass c = cls(d);
        return c == null ? amount : (float) (amount * c.hungerMultiplier());
    }

    // ================= Mobs =================
    public static boolean allowTarget(Mob mob, LivingEntity target) {
        if (!AbpsMod.running() || mob.level().isClientSide()) return true;
        UUID owner = Targets.minionOwner(mob);
        if (owner != null) {
            // Minions only fight hostile mobs and whatever their owner is fighting
            if (target.getUUID().equals(owner) || owner.equals(Targets.minionOwner(target))) return false;
            ServerPlayer ownerPlayer = AbpsMod.server().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null && target.getUUID().equals(data(ownerPlayer).lastHit)) return true;
            return target instanceof Enemy;
        }
        if (target instanceof ServerPlayer p) {
            PlayerData d = AbpsMod.data().peek(p.getUUID());
            AttributeClass c = d == null ? null : cls(d);
            if (c != null && c.ignoredBy(p, d, mob)) return false;
        }
        return true;
    }

    public static boolean projectileHit(Projectile proj, HitResult hit) {
        if (!proj.level().isClientSide()) dev.abps.content.Enchants.projectileHit(proj, hit);
        if (!AbpsMod.running() || proj.level().isClientSide()) return false;
        if (!(proj.getOwner() instanceof ServerPlayer p)) return false;
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        return c != null && c.onProjectileHit(p, d, proj, hit);
    }

    // ================= Items and blocks =================
    public static boolean saveDurability(ServerPlayer p, ItemStack stack) {
        if (!AbpsMod.running()) return false;
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        return c != null && c.saveDurability(p, d, stack);
    }

    public static ItemStack dropTool(Entity entity, BlockState state, ItemStack tool) {
        if (!(entity instanceof ServerPlayer p) || !AbpsMod.running()) return tool;
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        return c == null ? tool : c.dropTool(p, d, state, tool);
    }

    public static List<ItemStack> modifyDrops(Entity entity, BlockState state, List<ItemStack> drops) {
        if (!(entity instanceof ServerPlayer p)) return null;
        if (AbpsMod.running()) {
            PlayerData d = data(p);
            AttributeClass c = cls(d);
            if (c != null) c.modifyDrops(p, d, state, drops);
            dev.abps.items.CustomItems.modifyDrops(p, state, drops);
        }
        dev.abps.content.Farming.modifyDrops(p, state, drops);
        dev.abps.content.Enchants.modifyDrops(p, drops);
        return drops;
    }

    // ================= Keys for players without the mod =================
    /** F (swap hands) for players without the mod. Returns true if it was used for an ability. */
    public static boolean swapKey(ServerPlayer p) {
        if (!AbpsMod.running() || !AbpsMod.config().vanillaSwapHandKeys || Service.hasMod(p)) return false;
        PlayerData d = data(p);
        if (cls(d) == null) return false;
        boolean sneak = p.isShiftKeyDown();
        int single = sneak ? 2 : 1;
        int dbl = sneak ? 4 : 3;
        Service s = AbpsMod.service();
        if (d.tapTicksLeft > 0) {
            d.tapTicksLeft = 0;
            if (d.tapSneak == sneak) {
                s.cast(p, dbl);
                return true;
            }
            s.cast(p, d.tapSneak ? 2 : 1);
        }
        // No double-tap ability yet, so don't make the player wait
        if (!s.unlocked(d, dbl)) {
            s.cast(p, single);
            return true;
        }
        d.tapSneak = sneak;
        d.tapTicksLeft = Math.max(2, AbpsMod.config().doubleTapMs / 50);
        return true;
    }

    /** Runs every tick: fires a waiting single F tap once the double-tap window closes. */
    public static void tickTap(ServerPlayer p, PlayerData d) {
        if (d.tapTicksLeft <= 0) return;
        if (--d.tapTicksLeft == 0) AbpsMod.service().cast(p, d.tapSneak ? 2 : 1);
    }

    /** Windwalker double jump for players without the mod. They get "may fly" and pressing jump twice toggles it. */
    public static boolean flyToggle(ServerPlayer p) {
        if (!AbpsMod.running()) return false;
        PlayerData d = data(p);
        if (!d.managedFlight || p.gameMode() == GameType.CREATIVE || p.gameMode() == GameType.SPECTATOR) return false;
        AttributeClass c = cls(d);
        if (c == null) return false;
        p.getAbilities().flying = false;
        c.onAirJump(p, d);
        p.onUpdateAbilities();
        return true;
    }
}
