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

    /** Looks for the PARTICLES kind: how a stand-in for a vanilla particle should be drawn. */
    public static final int STYLE_ENERGY = 0;
    public static final int STYLE_SMOKE = 1;
    public static final int STYLE_DEBRIS = 2;

    private FxKind() {
    }
}
