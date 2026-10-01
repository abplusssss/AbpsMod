package dev.abps.client.fx;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.abps.AbpsMod;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The extra particle layer the glow effects use. It is the normal particle pipeline with additive blending, so
 * overlapping glows add up to a bright light instead of looking like flat stickers, and with depth writing turned
 * off so sprites never cut holes in each other.
 *
 * <p>Additive sprites store their light in the color channels (black adds nothing), so their alpha is always 1.
 * That also means this layer is not "translucent" in the vanilla sense, which keeps it off the order-independent
 * transparency path that needs a pipeline set of its own.
 */
public final class FxLayers {

    public static final SingleQuadParticle.Layer ADDITIVE;

    static {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
                .withLocation(AbpsMod.id("pipeline/additive_particle"))
                .withFragmentShader(AbpsMod.id("core/fx_additive"))
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                .build());
        ADDITIVE = new SingleQuadParticle.Layer(false, SingleQuadParticle.Layer.TRANSLUCENT.textureAtlasLocation(), pipeline);
    }

    /** Ordinary alpha blending, used for smoke and foam. */
    public static final SingleQuadParticle.Layer NORMAL = SingleQuadParticle.Layer.TRANSLUCENT;

    private FxLayers() {
    }
}
