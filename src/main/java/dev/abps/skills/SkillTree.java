package dev.abps.skills;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The skill tree every attribute shares. Both the server (to apply it) and the client (to draw it) build it from the
 * same few facts, so they always agree: how many abilities the attribute has, their names, and its role.
 *
 * Each level you buy is one skill point. Points go into nodes; a node can be taken once its parent (or one of its
 * parents) is taken. Points grow with level; at max level there are enough to take every node.
 *
 * Layout: the Awakening root sits at the top of the middle column. Under it the ability trunk unlocks abilities 2 to
 * 5. To the left is Offense (or Prosperity for gatherers), then Utility on the right of the trunk, then Defense.
 */
public final class SkillTree {

    public enum Kind {
        ROOT, ABILITY,
        DAMAGE, PVP_DAMAGE, MOB_DAMAGE, EXECUTE, BERSERK, LIFESTEAL, ATTACK_SPEED,
        HEALTH, ARMOR, RESIST, KNOCKBACK, SECOND_WIND, UNDYING,
        COOLDOWN, ULT_CHARGE, ULT_LOCK, SPEED, RECHARGE, OVERFLOW,
        LUCK, BREAK_SPEED, GATHER
    }

    public static final int OFFENSE = 0, TRUNK = 1, UTILITY = 2, DEFENSE = 3;
    public static final int[] BRANCH_COLORS = {0xFF5252, 0xFFD54F, 0x40C4FF, 0x69F0AE};
    public static final int PROSPERITY_COLOR = 0xFFB300;

    /**
     * One node. col and row place it on the grid (col can be a half for capstones that sit between two lanes).
     * It can be taken once any one of requires is owned. value is the size of its effect (0.05 is 5%).
     */
    public record Node(String id, String name, String desc, double col, int row, List<String> requires, Kind kind, double value, String icon,
                       int branch) {
    }

    private SkillTree() {
    }

    private static final Map<String, List<Node>> CACHE = new LinkedHashMap<>();

    /** The tree for an attribute with this many abilities (4 or 5), their names (1 to 5) and role id. */
    public static List<Node> build(int abilityCount, List<String> abilityNames, String role) {
        String key = abilityCount + "|" + String.join(",", abilityNames) + "|" + role;
        return CACHE.computeIfAbsent(key, k -> make(abilityCount, abilityNames, "gatherer".equals(role)));
    }

    public static Node find(List<Node> tree, String id) {
        for (Node n : tree) if (n.id().equals(id)) return n;
        return null;
    }

    private static List<Node> make(int abilityCount, List<String> names, boolean gatherer) {
        List<Node> t = new ArrayList<>();
        t.add(new Node("root", "Awakening", "Where every path starts. Always yours.", 2, 0, List.of(), Kind.ROOT, 0, "minecraft:nether_star", TRUNK));

        // The ability trunk
        String prev = "root";
        for (int i = 2; i <= Math.max(4, abilityCount); i++) {
            String name = i - 1 < names.size() && !names.get(i - 1).isEmpty() ? names.get(i - 1) : "Ability " + i;
            String id = "ab" + i;
            t.add(new Node(id, name, "Unlocks your ability " + i + ": " + name + ".", 2, i - 1, List.of(prev), Kind.ABILITY, i, "", TRUNK));
            prev = id;
        }

        if (gatherer) {
            // Prosperity: luck, faster work and more out of everything
            t.add(new Node("o1", "Lucky I", "+1 Luck: better loot from chests and fishing.", 1, 1, List.of("root"), Kind.LUCK, 1, "minecraft:rabbit_foot", OFFENSE));
            t.add(new Node("o2", "Lucky II", "+1 Luck.", 0, 2, List.of("o1"), Kind.LUCK, 1, "minecraft:rabbit_foot", OFFENSE));
            t.add(new Node("o3", "Industrious", "Break blocks 15% faster.", 1, 2, List.of("o1"), Kind.BREAK_SPEED, 0.15, "minecraft:golden_pickaxe", OFFENSE));
            t.add(new Node("o4", "Monster Hunter", "Deal 15% more damage to mobs.", 0, 3, List.of("o2"), Kind.MOB_DAMAGE, 0.15, "minecraft:iron_sword", OFFENSE));
            t.add(new Node("o5", "Efficient", "Break blocks another 15% faster.", 1, 3, List.of("o3"), Kind.BREAK_SPEED, 0.15, "minecraft:diamond_pickaxe", OFFENSE));
            t.add(new Node("o6", "Lucky III", "+2 Luck.", 0, 4, List.of("o4"), Kind.LUCK, 2, "minecraft:emerald", OFFENSE));
            t.add(new Node("o7", "Hardy Worker", "+2 hearts.", 1, 4, List.of("o5"), Kind.HEALTH, 4, "minecraft:golden_apple", OFFENSE));
            t.add(new Node("o8", "Bounty", "Your ultimate charges 35% faster from gathering.", 0.5, 5, List.of("o6", "o7"), Kind.GATHER, 0.35,
                    "minecraft:gold_block", OFFENSE));
        } else {
            t.add(new Node("o1", "Sharpened I", "Deal 4% more damage.", 1, 1, List.of("root"), Kind.DAMAGE, 0.04, "minecraft:iron_sword", OFFENSE));
            t.add(new Node("o2", "Sharpened II", "Deal 4% more damage.", 0, 2, List.of("o1"), Kind.DAMAGE, 0.04, "minecraft:diamond_sword", OFFENSE));
            t.add(new Node("o3", "Ruthless", "Deal 8% more damage to players.", 1, 2, List.of("o1"), Kind.PVP_DAMAGE, 0.08, "minecraft:skeleton_skull", OFFENSE));
            t.add(new Node("o4", "Executioner", "Deal 20% more damage to targets below 35% health.", 0, 3, List.of("o2"), Kind.EXECUTE, 0.20,
                    "minecraft:wither_skeleton_skull", OFFENSE));
            t.add(new Node("o5", "Bloodthirst", "Heal 5% of the damage you deal.", 1, 3, List.of("o3"), Kind.LIFESTEAL, 0.05, "minecraft:redstone", OFFENSE));
            t.add(new Node("o6", "Sharpened III", "Deal 6% more damage.", 0, 4, List.of("o4"), Kind.DAMAGE, 0.06, "minecraft:netherite_sword", OFFENSE));
            t.add(new Node("o7", "Frenzy", "Attack 10% faster.", 1, 4, List.of("o5"), Kind.ATTACK_SPEED, 0.10, "minecraft:blaze_powder", OFFENSE));
            t.add(new Node("o8", "Berserk", "Deal 15% more damage while below half health.", 0.5, 5, List.of("o6", "o7"), Kind.BERSERK, 0.15,
                    "minecraft:netherite_axe", OFFENSE));
        }

        // Utility
        t.add(new Node("u1", "Focus I", "Abilities recharge 5% faster.", 3, 1, List.of("root"), Kind.COOLDOWN, 0.05, "minecraft:clock", UTILITY));
        t.add(new Node("u2", "Focus II", "Abilities recharge 5% faster.", 3, 2, List.of("u1"), Kind.COOLDOWN, 0.05, "minecraft:clock", UTILITY));
        t.add(new Node("u3", "Overcharge", "Your ultimate charges 25% faster.", 4, 2, List.of("u1"), Kind.ULT_CHARGE, 0.25, "minecraft:glowstone_dust", UTILITY));
        t.add(new Node("u4", "Swiftness", "Move 6% faster.", 3, 3, List.of("u2"), Kind.SPEED, 0.06, "minecraft:sugar", UTILITY));
        t.add(new Node("u5", "Quick Return", "Your ultimate starts charging again 30% sooner after you use it.", 4, 3, List.of("u3"), Kind.ULT_LOCK, 0.30,
                "minecraft:recovery_compass", UTILITY));
        t.add(new Node("u6", "Focus III", "Abilities recharge 6% faster.", 3, 4, List.of("u4"), Kind.COOLDOWN, 0.06, "minecraft:amethyst_shard", UTILITY));
        t.add(new Node("u7", "Momentum", "Every kill takes 2 seconds off all your ability cooldowns.", 4, 4, List.of("u5"), Kind.RECHARGE, 2,
                "minecraft:feather", UTILITY));
        t.add(new Node("u8", "Overflow", "After you use your ultimate it starts 25% charged.", 3.5, 5, List.of("u6", "u7"), Kind.OVERFLOW, 0.25,
                "minecraft:end_crystal", UTILITY));

        // Defense
        t.add(new Node("d1", "Toughness I", "+2 hearts.", 5, 1, List.of("root"), Kind.HEALTH, 4, "minecraft:apple", DEFENSE));
        t.add(new Node("d2", "Toughness II", "+2 hearts.", 5, 2, List.of("d1"), Kind.HEALTH, 4, "minecraft:golden_apple", DEFENSE));
        t.add(new Node("d3", "Iron Skin", "Take 6% less damage.", 6, 2, List.of("d1"), Kind.RESIST, 0.06, "minecraft:iron_chestplate", DEFENSE));
        t.add(new Node("d4", "Steadfast", "40% less knockback.", 5, 3, List.of("d2"), Kind.KNOCKBACK, 0.4, "minecraft:anvil", DEFENSE));
        t.add(new Node("d5", "Second Wind", "When you drop below 30% health, get Regeneration II for 5s. Once a minute.", 6, 3, List.of("d3"),
                Kind.SECOND_WIND, 60, "minecraft:ghast_tear", DEFENSE));
        t.add(new Node("d6", "Toughness III", "+3 hearts.", 5, 4, List.of("d4"), Kind.HEALTH, 6, "minecraft:enchanted_golden_apple", DEFENSE));
        t.add(new Node("d7", "Bulwark", "Take 8% less damage.", 6, 4, List.of("d5"), Kind.RESIST, 0.08, "minecraft:shield", DEFENSE));
        t.add(new Node("d8", "Undying", "A killing blow leaves you at 1 heart with Absorption instead. Once every 3 minutes.", 5.5, 5, List.of("d6", "d7"),
                Kind.UNDYING, 180, "minecraft:totem_of_undying", DEFENSE));
        return t;
    }

    /** True if this node can be taken now: not owned yet, and the root or one of its parents is owned. */
    public static boolean available(Node n, List<String> owned) {
        if (n.kind() == Kind.ROOT || owned.contains(n.id())) return false;
        for (String r : n.requires()) if (r.equals("root") || owned.contains(r)) return true;
        return false;
    }

    /** The total of every owned node of a kind (5% plus 5% is 0.10). */
    public static double sum(List<Node> tree, List<String> owned, Kind kind) {
        double total = 0;
        for (Node n : tree) if (n.kind() == kind && owned.contains(n.id())) total += n.value();
        return total;
    }

    public static boolean has(List<String> owned, Kind kind, List<Node> tree) {
        for (Node n : tree) if (n.kind() == kind && owned.contains(n.id())) return true;
        return false;
    }
}
