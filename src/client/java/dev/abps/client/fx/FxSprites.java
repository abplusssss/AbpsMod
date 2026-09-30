package dev.abps.client.fx;

import dev.abps.util.FxTypes;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

import java.util.HashMap;
import java.util.Map;

/** The sprite sets for each effect texture. They are filled in when the resources load and stay valid after reloads. */
public final class FxSprites {

    private static final Map<String, SpriteSet> SETS = new HashMap<>();

    private FxSprites() {
    }

    public static void put(String name, SpriteSet set) {
        SETS.put(name, set);
    }

    public static SpriteSet get(String name) {
        return SETS.get(name);
    }

    /** The layer a sprite has to be drawn on: additive for light, normal for anything with real transparency. */
    public static SingleQuadParticle.Layer layer(String name) {
        return FxTypes.ADDITIVE.contains(name) ? FxLayers.ADDITIVE : FxLayers.NORMAL;
    }

    /** True once every texture has been loaded. */
    public static boolean loaded() {
        return SETS.size() >= FxTypes.TYPES.size();
    }
}
