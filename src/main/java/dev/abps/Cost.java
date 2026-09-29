package dev.abps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A price in XP levels and items. */
public record Cost(int xp, Map<String, Integer> items) {

    public Map<Item, Integer> resolved() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == Items.AIR || e.getValue() <= 0) continue;
            out.merge(item, e.getValue(), Integer::sum);
        }
        return out;
    }

    public static int count(ServerPlayer p, Item item) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    public boolean canAfford(ServerPlayer p) {
        if (p.experienceLevel < xp) return false;
        for (Map.Entry<Item, Integer> e : resolved().entrySet()) {
            if (count(p, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public void take(ServerPlayer p) {
        p.giveExperienceLevels(-xp);
        Inventory inv = p.getInventory();
        for (Map.Entry<Item, Integer> e : resolved().entrySet()) {
            int left = e.getValue();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(e.getKey())) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        inv.setChanged();
    }

    public void give(ServerPlayer p) {
        p.giveExperienceLevels(xp);
        for (Map.Entry<Item, Integer> e : resolved().entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                int amount = Math.min(left, e.getKey().getDefaultMaxStackSize());
                left -= amount;
                ItemStack stack = new ItemStack(e.getKey(), amount);
                if (!p.getInventory().add(stack)) p.spawnAtLocation((net.minecraft.server.level.ServerLevel) p.level(), stack);
            }
        }
    }

    public static String prettyName(Item item) {
        return item.getName(new ItemStack(item)).getString();
    }

    /** Green parts you can pay, red parts you can't. Pass null to show everything in gold. */
    public String describe(ServerPlayer p) {
        List<String> parts = new ArrayList<>();
        if (xp > 0) parts.add(color(p == null || p.experienceLevel >= xp, p) + xp + " XP Levels");
        for (Map.Entry<Item, Integer> e : resolved().entrySet()) {
            boolean has = p == null || count(p, e.getKey()) >= e.getValue();
            parts.add(color(has, p) + e.getValue() + "x " + prettyName(e.getKey()));
        }
        if (parts.isEmpty()) return "<green>Free";
        return String.join("<gray>, ", parts);
    }

    private static String color(boolean has, ServerPlayer p) {
        if (p == null) return "<gold>";
        return has ? "<green>" : "<red>";
    }

    /** Plain lines for the menu screen: text and whether the player has it. */
    public List<Line> lines(ServerPlayer p) {
        List<Line> out = new ArrayList<>();
        if (xp > 0) out.add(new Line(xp + " XP Levels", p.experienceLevel >= xp));
        for (Map.Entry<Item, Integer> e : resolved().entrySet()) {
            out.add(new Line(e.getValue() + "x " + prettyName(e.getKey()), count(p, e.getKey()) >= e.getValue()));
        }
        return out;
    }

    public record Line(String text, boolean has) {
    }
}
