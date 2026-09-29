package dev.abps.classes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Info about a hit dealt by a player.
 *
 * @param melee      true for a normal hit with the hand
 * @param sweep      true for sword sweep damage
 * @param projectile the projectile, or null for melee
 * @param weapon     item in the main hand
 */
public record Hit(boolean melee, boolean sweep, Entity projectile, ItemStack weapon) {
}
