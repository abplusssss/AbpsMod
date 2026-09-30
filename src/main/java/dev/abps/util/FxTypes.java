package dev.abps.util;

import dev.abps.AbpsMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The particle types the client effects engine draws with, one per sprite. They exist on both sides so the game
 * registries match, but the server never sends them: it sends VfxPayload messages and the client makes the particles
 * itself. Textures live in assets/abpsmod/textures/particle and their lists in assets/abpsmod/particles, both made by
 * tools/gen_fx_textures.py. A sprite's registry name is "fx_" plus the name here.
 */
public final class FxTypes {

    /** Sprites made of light: drawn with additive blending, black adds nothing. */
    public static final Set<String> ADDITIVE = Set.of(
            "glow", "spark", "flare", "ring", "shard", "slash", "sigil", "flame", "ember", "streak", "wisp", "rune", "bolt",
            "swirl", "rays", "claw", "arrow", "heart", "skull", "shockwave", "crackglow", "ribbon", "ribboncore");

    /** Sprites with real transparency: drawn with normal blending. */
    public static final Set<String> SOLID = Set.of(
            "smoke", "leaf", "petal", "droplet", "bubble", "foam", "watersheet", "debris", "crack", "splat", "bat", "thorn", "ink", "ribbonmask");

    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    static {
        for (String n : ADDITIVE) TYPES.put(n, FabricParticleTypes.simple());
        for (String n : SOLID) TYPES.put(n, FabricParticleTypes.simple());
    }

    private FxTypes() {
    }

    public static void register() {
        for (Map.Entry<String, SimpleParticleType> e : TYPES.entrySet()) {
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, AbpsMod.id("fx_" + e.getKey()), e.getValue());
        }
    }
}
