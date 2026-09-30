package dev.abps.util;

import dev.abps.AbpsMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The particle types the client effects engine draws with. They exist on both sides so the game registries match,
 * but the server never sends them: it sends VfxPayload messages and the client makes the particles itself.
 * Textures live in assets/abpsmod/textures/particle (made by tools/gen_fx_textures.py).
 */
public final class FxTypes {

    public static final SimpleParticleType GLOW = FabricParticleTypes.simple();
    public static final SimpleParticleType SPARK = FabricParticleTypes.simple();
    public static final SimpleParticleType FLARE = FabricParticleTypes.simple();
    public static final SimpleParticleType RING = FabricParticleTypes.simple();
    public static final SimpleParticleType SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType SLASH = FabricParticleTypes.simple();
    public static final SimpleParticleType SIGIL = FabricParticleTypes.simple();
    public static final SimpleParticleType SMOKE = FabricParticleTypes.simple();

    private FxTypes() {
    }

    public static void register() {
        add("fx_glow", GLOW);
        add("fx_spark", SPARK);
        add("fx_flare", FLARE);
        add("fx_ring", RING);
        add("fx_shard", SHARD);
        add("fx_slash", SLASH);
        add("fx_sigil", SIGIL);
        add("fx_smoke", SMOKE);
    }

    private static void add(String name, SimpleParticleType type) {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, AbpsMod.id(name), type);
    }
}
