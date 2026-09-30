package dev.abps.client.fx;

import net.minecraft.client.particle.SpriteSet;

/** The sprite sets for each effect texture. They are filled in when the resources load and stay valid after reloads. */
public final class FxSprites {

    public static SpriteSet glow, spark, flare, ring, shard, slash, sigil, smoke;

    private FxSprites() {
    }

    /** True once every texture has been loaded. */
    public static boolean loaded() {
        return glow != null && spark != null && flare != null && ring != null && shard != null && slash != null && sigil != null && smoke != null;
    }
}
