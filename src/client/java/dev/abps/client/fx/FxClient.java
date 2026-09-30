package dev.abps.client.fx;

import dev.abps.AbpsMod;
import dev.abps.net.Net;
import dev.abps.util.FxTypes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;

import java.util.Map;

/**
 * Sets the effects engine up. Everything here is wrapped so that if a rendering detail is different from what was
 * expected the game still starts; the server then keeps using its own effects for this player.
 */
public final class FxClient {

    private FxClient() {
    }

    public static void init() {
        try {
            // Building the layer registers the additive render pipeline, so do it now rather than at first use
            if (FxLayers.ADDITIVE == null) throw new IllegalStateException("no additive layer");

            for (Map.Entry<String, SimpleParticleType> e : FxTypes.TYPES.entrySet()) {
                provide(e.getKey(), e.getValue());
            }

            ClientPlayNetworking.registerGlobalReceiver(Net.VfxPayload.TYPE, (p, ctx) -> FxSystem.handle(p));
            ClientTickEvents.END_CLIENT_TICK.register(FxSystem::tick);
            FxSystem.ready = true;
        } catch (Throwable t) {
            FxSystem.ready = false;
            AbpsMod.LOGGER.warn("The effects engine could not start, the server's built-in effects will be used instead: {}", t.toString());
        }
    }

    /** Registers a provider that only remembers its sprite set. Effects create the particles themselves. */
    private static void provide(String name, SimpleParticleType type) {
        SingleQuadParticle.Layer layer = FxSprites.layer(name);
        ParticleProviderRegistry.getInstance().register(type, sprites -> {
            FxSprites.put(name, sprites);
            return (ParticleProvider<SimpleParticleType>) (options, level, x, y, z, xd, yd, zd, random) ->
                    new FxParticle(level, x, y, z, sprites.get(random), layer).vel(xd, yd, zd);
        });
    }
}
