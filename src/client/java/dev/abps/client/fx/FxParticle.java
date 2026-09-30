package dev.abps.client.fx;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One glowing sprite. It grows or shrinks, blends from one color to another, fades in and out, and can be turned to
 * face a fixed direction (for rings lying on the ground) instead of always facing the camera.
 * Setters return this so effects read as one line each.
 */
public final class FxParticle extends SingleQuadParticle {

    private static int alive;

    private final Layer layer;
    private final boolean additive;
    private float size0 = 0.3f, size1 = 0.3f;
    private float r0 = 1, g0 = 1, b0 = 1, r1 = 1, g1 = 1, b1 = 1;
    private float fadeIn = 0.12f, fadeOutStart = 0.4f, peak = 1f;
    private float spin;
    private Quaternionf fixed;

    public FxParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, Layer layer) {
        super(level, x, y, z, sprite);
        this.layer = layer;
        this.additive = layer == FxLayers.ADDITIVE;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 0.96f;
        this.lifetime = 20;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        alive++;
    }

    /** How many effect particles exist right now. Used to stop a busy fight from flooding the screen. */
    public static int alive() {
        return alive;
    }

    public FxParticle life(int ticks) {
        this.lifetime = Math.max(1, ticks);
        return this;
    }

    public FxParticle vel(double x, double y, double z) {
        this.xd = x;
        this.yd = y;
        this.zd = z;
        return this;
    }

    public FxParticle drag(float friction) {
        this.friction = friction;
        return this;
    }

    public FxParticle grav(float gravity) {
        this.gravity = gravity;
        return this;
    }

    /** Half-width of the sprite at the start and at the end of its life, in blocks. */
    public FxParticle size(float start, float end) {
        this.size0 = start;
        this.size1 = end;
        return this;
    }

    public FxParticle colors(int from, int to) {
        this.r0 = ((from >> 16) & 0xFF) / 255f;
        this.g0 = ((from >> 8) & 0xFF) / 255f;
        this.b0 = (from & 0xFF) / 255f;
        this.r1 = ((to >> 16) & 0xFF) / 255f;
        this.g1 = ((to >> 8) & 0xFF) / 255f;
        this.b1 = (to & 0xFF) / 255f;
        return this;
    }

    /** Fade in over the first fadeIn of its life, fade out from fadeOutStart, at brightness peak (0 to 1). */
    public FxParticle envelope(float fadeIn, float fadeOutStart, float peak) {
        this.fadeIn = fadeIn;
        this.fadeOutStart = fadeOutStart;
        this.peak = peak;
        return this;
    }

    /** Turns per tick in radians. */
    public FxParticle spin(float radiansPerTick) {
        this.spin = radiansPerTick;
        return this;
    }

    public FxParticle startRoll(float radians) {
        this.roll = radians;
        this.oRoll = radians;
        return this;
    }

    /** Makes the sprite lie flat facing along normal instead of facing the camera. */
    public FxParticle facing(double nx, double ny, double nz) {
        this.fixed = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) nx, (float) ny, (float) nz).normalize());
        return this;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return layer;
    }

    /** Always fully lit, day or night, indoors or out. */
    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
        return Mth.lerp(t, size0, size1);
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += spin;
    }

    @Override
    public void remove() {
        if (!this.removed) alive--;
        super.remove();
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float t = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
        float fade = 1f;
        if (fadeIn > 0f && t < fadeIn) fade = t / fadeIn;
        else if (t > fadeOutStart) fade = 1f - (t - fadeOutStart) / Math.max(0.001f, 1f - fadeOutStart);
        fade = Mth.clamp(fade, 0f, 1f);
        fade = fade * fade * (3f - 2f * fade) * peak;
        float r = Mth.lerp(t, r0, r1), g = Mth.lerp(t, g0, g1), b = Mth.lerp(t, b0, b1);
        if (additive) {
            // Additive blending: the light is the color, so fading means dimming the color
            this.rCol = r * fade;
            this.gCol = g * fade;
            this.bCol = b * fade;
            this.alpha = 1f;
        } else {
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.alpha = fade;
        }
        if (fixed == null) {
            super.extract(state, camera, partialTick);
        } else {
            Quaternionf q = new Quaternionf(fixed);
            if (roll != 0f || oRoll != 0f) q.rotateZ(Mth.lerp(partialTick, oRoll, roll));
            extractRotatedQuad(state, camera, q, partialTick);
        }
    }
}
