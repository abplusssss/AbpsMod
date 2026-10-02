package dev.abps.util;

/** The kinds of effect the server can ask the client mod to draw. Shared by both sides so the numbers always match. */
public final class FxKind {

    public static final int SHARD = 1;
    public static final int BURST = 2;
    public static final int RING = 3;
    public static final int BEAM = 4;
    public static final int ZIGZAG = 5;
    public static final int PILLAR = 6;
    public static final int JAWS = 7;
    public static final int SLASH = 8;
    public static final int ORBIT = 9;
    public static final int VORTEX = 10;
    public static final int FINS = 11;
    public static final int FLASH = 12;
    public static final int SPHERE = 13;
    public static final int HELIX = 14;
    public static final int TRAIL = 15;
    public static final int WAVE = 16;
    public static final int WAVE_STOP = 17;
    public static final int PARTICLES = 18;
    /** A whole ability's signature effect, made by the client from the caster's class and the ability number. */
    public static final int SIGNATURE = 19;

    /**
     * Theme numbers: which class an effect belongs to, so the client can draw it in that class's style. 0 means no
     * class. The number rides in the second byte of the effect kind.
     */
    public static final int THEME_ARCHER = 1, THEME_ASSASSIN = 2, THEME_BERSERKER = 3, THEME_MINER = 5,
            THEME_NECROMANCER = 6, THEME_PYROMANCER = 7, THEME_SHARK = 8, THEME_TANK = 9, THEME_VAMPIRE = 10, THEME_WINDWALKER = 11,
            THEME_CRYO = 12, THEME_CHRONO = 13, THEME_PALADIN = 14, THEME_VOID = 15, THEME_SAMURAI = 16,
            // 17 is dungeons
            THEME_OVERLORD = 18, THEME_HARVESTER = 19, THEME_LUMBERJACK = 20, THEME_ANGLER = 21, THEME_EXPLORER = 22;

    public static int theme(String classId) {
        return switch (classId) {
            case "archer" -> THEME_ARCHER;
            case "assassin" -> THEME_ASSASSIN;
            case "berserker" -> THEME_BERSERKER;
            case "miner" -> THEME_MINER;
            case "necromancer" -> THEME_NECROMANCER;
            case "pyromancer" -> THEME_PYROMANCER;
            case "shark" -> THEME_SHARK;
            case "tank" -> THEME_TANK;
            case "vampire" -> THEME_VAMPIRE;
            case "windwalker" -> THEME_WINDWALKER;
            case "cryomancer" -> THEME_CRYO;
            case "chronomancer" -> THEME_CHRONO;
            case "paladin" -> THEME_PALADIN;
            case "voidwalker" -> THEME_VOID;
            case "samurai" -> THEME_SAMURAI;
            case "overlord" -> THEME_OVERLORD;
            case "harvester" -> THEME_HARVESTER;
            case "lumberjack" -> THEME_LUMBERJACK;
            case "angler" -> THEME_ANGLER;
            case "explorer" -> THEME_EXPLORER;
            default -> 0;
        };
    }

    /** Looks for the PARTICLES kind: how a stand-in for a vanilla particle should be drawn. */
    public static final int STYLE_ENERGY = 0;
    public static final int STYLE_SMOKE = 1;
    public static final int STYLE_DEBRIS = 2;

    private FxKind() {
    }
}
