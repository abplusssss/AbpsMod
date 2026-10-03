package dev.abps.content;

import dev.abps.util.Mods;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full-set bonuses for the new armor tiers.
 * Ruby: +2 hearts and Luck. Endite: ender pearls don't hurt, and falling into the void pulls you back to safe
 * ground once every ten minutes.
 */
public final class SetBonuses {

    private SetBonuses() {
    }

    private static final long RESCUE_COOLDOWN_MS = 10 * 60 * 1000L;
    private static final ResourceKey<DamageType> ENDER_PEARL =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("minecraft", "ender_pearl"));

    private record Safe(ResourceKey<net.minecraft.world.level.Level> dim, double x, double y, double z) {
    }

    private static final Map<UUID, Safe> SAFE = new HashMap<>();
    private static final Map<UUID, Long> LAST_RESCUE = new HashMap<>();

    private static boolean wearing(ServerPlayer p, Item helmet, Item chest, Item legs, Item boots) {
        return helmet != null
                && p.getItemBySlot(EquipmentSlot.HEAD).is(helmet)
                && p.getItemBySlot(EquipmentSlot.CHEST).is(chest)
                && p.getItemBySlot(EquipmentSlot.LEGS).is(legs)
                && p.getItemBySlot(EquipmentSlot.FEET).is(boots);
    }

    public static boolean fullRuby(ServerPlayer p) {
        return wearing(p, ModContent.RUBY_HELMET, ModContent.RUBY_CHESTPLATE, ModContent.RUBY_LEGGINGS, ModContent.RUBY_BOOTS);
    }

    public static boolean fullEndite(ServerPlayer p) {
        return wearing(p, ModContent.ENDITE_HELMET, ModContent.ENDITE_CHESTPLATE, ModContent.ENDITE_LEGGINGS, ModContent.ENDITE_BOOTS);
    }

    /** Called every tick per player. */
    public static void tick(ServerPlayer p, long ticks) {
        if (ticks % 10 == 0) {
            boolean ruby = fullRuby(p);
            Mods.toggle(p, ruby, Attributes.MAX_HEALTH, "ruby_set", 4, Mods.ADD);
            Mods.toggle(p, ruby, Attributes.LUCK, "ruby_set_luck", 1, Mods.ADD);
        }
        if (!fullEndite(p)) return;
        UUID id = p.getUUID();
        if (p.onGround() && !p.isSpectator()) {
            BlockPos below = p.blockPosition().below();
            if (!p.level().getBlockState(below).isAir()) {
                SAFE.put(id, new Safe(p.level().dimension(), p.getX(), p.getY(), p.getZ()));
            }
        }
        if (p.getY() < p.level().getMinY() - 10) {
            Safe s = SAFE.get(id);
            long now = System.currentTimeMillis();
            Long last = LAST_RESCUE.get(id);
            if (s == null || !s.dim().equals(p.level().dimension())) return;
            if (last != null && now - last < RESCUE_COOLDOWN_MS) return;
            LAST_RESCUE.put(id, now);
            p.teleportTo(s.x(), s.y(), s.z());
            p.setDeltaMovement(0, 0, 0);
            p.fallDistance = 0;
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
            p.sendSystemMessage(Component.literal("Your endite armor pulls you out of the void. (10 min cooldown)")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** False when the damage should be cancelled by a set bonus. */
    public static boolean allowDamage(ServerPlayer p, DamageSource source) {
        return !(source.is(ENDER_PEARL) && fullEndite(p));
    }

    public static void forget(UUID id) {
        SAFE.remove(id);
    }
}
