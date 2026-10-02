package dev.abps.dungeon;

import dev.abps.AbpsMod;
import dev.abps.classes.AttributeClass;
import dev.abps.items.CustomItems;
import dev.abps.util.Inv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewards. Every player gets their own loot straight into their inventory, so a party never has to fight over a
 * chest: valuables, items for upgrading their attribute, experience, and a chance at a custom dungeon item.
 */
final class Loot {

    private Loot() {
    }

    private static final String[] RARE = {"bloodfang", "frostbite", "timberfall", "quarry_pick", "earthmover", "prospector_pick", "harvest_scythe"};
    private static final String[] EPIC = {"stormcaller", "sunblade", "earthsplitter", "molten_pick", "bulwark", "windrunners", "ember_staff", "frost_staff",
            "shadow_daggers", "tide_spear", "echo_chakram", "gravedigger"};
    private static final String[] LEGENDARY = {"voidrender", "reaper", "veinripper", "phoenix_feather", "thunder_hammer", "soul_staff",
            "dragonbone_greatsword", "titan_drill"};

    private static void give(ServerPlayer p, ItemStack s, List<String> got) {
        if (s.isEmpty()) return;
        got.add(s.getCount() + "x " + s.getHoverName().getString());
        Inv.give(p, s, s.getCount());
    }

    /** One of the attribute's own upgrade items for its current tier, so dungeons help you level up. */
    private static void upgradeItems(ServerPlayer p, int amount, List<String> got) {
        AttributeClass c = AbpsMod.service().cls(p);
        if (c == null) return;
        int lvl = AbpsMod.data().get(p).level;
        String[] items = c.upgradeItems();
        int tier = lvl < 7 ? 0 : lvl < 13 ? 1 : lvl < 19 ? 2 : 3;
        int[] counts = c.upgradeCounts();
        int n = Math.max(1, counts[tier] * amount / 8);
        give(p, new ItemStack(Inv.item(items[tier]), n), got);
    }

    /** Rolls a custom item: rare at difficulty 1, a chance of epic and legendary higher up. */
    private static void customRoll(Run run, ServerPlayer p, double chance, int tierBonus, List<String> got) {
        if (run.rnd.nextDouble() >= chance) return;
        int d = run.def.difficulty() + tierBonus;
        double roll = run.rnd.nextDouble();
        String[] pool = roll < 0.06 * d ? LEGENDARY : roll < 0.25 * d ? EPIC : RARE;
        String id = pool[run.rnd.nextInt(pool.length)];
        ItemStack s = CustomItems.create(id, run.level);
        give(p, s, got);
        AbpsMod.service().broadcast("<gray>" + p.getName().getString() + " found " + s.getHoverName().getString() + "<gray> in " + run.def.name() + "!", null);
    }

    private static void report(ServerPlayer p, String title, List<String> got) {
        if (got.isEmpty()) return;
        AbpsMod.service().send(p, "<gold>" + title + ":</gold> <white>" + String.join("<gray>, </gray>", got));
    }

    /** A treasure room on the way. */
    static void treasure(Run run, ServerPlayer p) {
        List<String> got = new ArrayList<>();
        give(p, new ItemStack(Items.GOLD_INGOT, 4 + run.rnd.nextInt(5)), got);
        give(p, new ItemStack(Items.DIAMOND, 1 + run.rnd.nextInt(run.def.difficulty() + 1)), got);
        give(p, new ItemStack(Items.GOLDEN_APPLE, 1), got);
        upgradeItems(p, 2, got);
        customRoll(run, p, 0.15, 0, got);
        report(p, "Treasure", got);
    }

    /** The end of a run. Faster, harder runs pay more. */
    static void clear(Run run, ServerPlayer p, long timeMs) {
        List<String> got = new ArrayList<>();
        int d = run.def.difficulty();
        give(p, new ItemStack(Items.DIAMOND, 2 + d * 2 + run.rnd.nextInt(3)), got);
        give(p, new ItemStack(Items.EMERALD, 4 + run.rnd.nextInt(6)), got);
        if (d >= 2) give(p, new ItemStack(Items.NETHERITE_SCRAP, d - 1), got);
        if (d >= 3 && run.rnd.nextDouble() < 0.4) give(p, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), got);
        upgradeItems(p, 4 + d * 2, got);
        p.giveExperienceLevels(5 + d * 3);
        got.add((5 + d * 3) + " levels of experience");
        customRoll(run, p, 0.45 + d * 0.15, 1, got);
        report(p, "Your loot", got);
    }

    /** Every 5 waves in the arena. */
    static void wave(Run run, ServerPlayer p) {
        List<String> got = new ArrayList<>();
        int w = run.wave;
        give(p, new ItemStack(Items.DIAMOND, 1 + w / 5), got);
        give(p, new ItemStack(Items.GOLD_INGOT, 3 + w / 2), got);
        upgradeItems(p, 2 + w / 5, got);
        p.giveExperienceLevels(2 + w / 5);
        customRoll(run, p, Math.min(0.8, 0.1 + w * 0.02), w / 10, got);
        report(p, "Wave " + w + " reward", got);
    }
}
