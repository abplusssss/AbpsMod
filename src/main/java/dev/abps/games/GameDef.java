package dev.abps.games;

import java.util.LinkedHashMap;
import java.util.Map;

/** One activity on the Party tab. */
public record GameDef(String id, String name, String blurb, Kind kind, int difficulty, String icon, int color, int minPlayers) {

    public enum Kind {
        /** Co-op tower defense: protect the Heart through every wave. */
        SIEGE,
        /** Two teams steal each other's flag. */
        CTF,
        /** Hold the hill alone to score. */
        KOTH,
        /** Dig the floor out from under everyone else. */
        SPLEEF
    }

    public boolean coop() {
        return kind == Kind.SIEGE;
    }

    public static final Map<String, GameDef> ALL = new LinkedHashMap<>();

    private static void add(GameDef d) {
        ALL.put(d.id, d);
    }

    public static GameDef get(String id) {
        return ALL.get(id);
    }

    static {
        add(new GameDef("siege_easy", "Siege: Outpost", "Hold the Heart through 10 waves. Kills earn coins for towers.", Kind.SIEGE, 1,
                "minecraft:heart_of_the_sea", 0x69F0AE, 1));
        add(new GameDef("siege_normal", "Siege: Fortress", "15 waves, tougher raiders and a ravager every fifth wave.", Kind.SIEGE, 2,
                "minecraft:crossbow", 0xFFD54F, 1));
        add(new GameDef("siege_hard", "Siege: Last Stand", "20 waves that hit hard. Bring friends and build towers fast.", Kind.SIEGE, 3,
                "minecraft:netherite_sword", 0xFF5252, 1));
        add(new GameDef("ctf", "Capture the Flag", "Two teams. Grab their wool and bring it home. First to 3 captures.", Kind.CTF, 2,
                "minecraft:red_banner", 0x40C4FF, 2));
        add(new GameDef("koth", "King of the Hill", "Stand on the hill alone to score. First to 60 points wins.", Kind.KOTH, 2,
                "minecraft:golden_helmet", 0xFFAB40, 2));
        add(new GameDef("spleef", "Spleef", "Shovels out. Dig the snow out from under everyone. Last one standing wins.", Kind.SPLEEF, 1,
                "minecraft:diamond_shovel", 0xE1F5FE, 2));
    }
}
