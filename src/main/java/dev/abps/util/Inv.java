package dev.abps.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * Inventory helpers for trading. Only the main inventory is used (hotbar and the 27 slots above it), never armor
 * or the offhand, so nobody sells the chestplate they are wearing by accident.
 */
public final class Inv {

    /** Hotbar plus main inventory. */
    public static final int MAIN = 36;

    private Inv() {
    }

    public static Item item(String id) {
        Identifier i = id == null ? null : Identifier.tryParse(id);
        Item item = i == null ? null : BuiltInRegistries.ITEM.getValue(i);
        return item == null ? Items.AIR : item;
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** Matches exactly this item with the same enchantments, name and so on. */
    public static Predicate<ItemStack> same(ItemStack template) {
        return s -> !s.isEmpty() && ItemStack.isSameItemSameComponents(s, template);
    }

    /** Matches a plain currency item. Renamed or enchanted copies don't count, so nobody pays with a named diamond by mistake. */
    public static Predicate<ItemStack> currency(Item item) {
        ItemStack plain = new ItemStack(item);
        return s -> !s.isEmpty() && ItemStack.isSameItemSameComponents(s, plain);
    }

    public static int count(ServerPlayer p, Predicate<ItemStack> match) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < MAIN; i++) {
            ItemStack s = inv.getItem(i);
            if (match.test(s)) n += s.getCount();
        }
        return n;
    }

    /** Removes exactly n matching items, or nothing at all if the player doesn't have n. */
    public static boolean take(ServerPlayer p, Predicate<ItemStack> match, int n) {
        if (n <= 0) return true;
        if (count(p, match) < n) return false;
        Inventory inv = p.getInventory();
        int left = n;
        for (int i = 0; i < MAIN && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!match.test(s)) continue;
            int use = Math.min(left, s.getCount());
            s.shrink(use);
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            left -= use;
        }
        inv.setChanged();
        return true;
    }

    /** How many more of this item fit in the main inventory. */
    public static int room(ServerPlayer p, ItemStack template) {
        Inventory inv = p.getInventory();
        int max = template.getMaxStackSize();
        int room = 0;
        for (int i = 0; i < MAIN; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) room += max;
            else if (ItemStack.isSameItemSameComponents(s, template)) room += Math.max(0, max - s.getCount());
        }
        return room;
    }

    /** Gives count copies of the template, in full stacks. Anything that doesn't fit is dropped at the player's feet. */
    public static void give(ServerPlayer p, ItemStack template, int count) {
        int max = Math.max(1, template.getMaxStackSize());
        while (count > 0) {
            int n = Math.min(max, count);
            ItemStack stack = template.copyWithCount(n);
            count -= n;
            if (!p.getInventory().add(stack) && !stack.isEmpty()) p.spawnAtLocation((ServerLevel) p.level(), stack);
        }
        p.getInventory().setChanged();
    }
}
