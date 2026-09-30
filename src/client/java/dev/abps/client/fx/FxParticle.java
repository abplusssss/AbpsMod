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
    /** When set the quad lies along its direction of travel, turned toward the camera (for streaks and arrows). */
    private boolean axial;
    /** With axial: the sprite's up (not its front) runs along the axis. For upright things like lightning bolts. */
    private boolean axisY;
    /** With axial: a fixed axis instead of the velocity. */
    private Vector3f axisDir;
    /** Full control of the quad's orientation, for pieces of a surface. Overrides everything else. */
    private Quaternionf basis;
    private float flicker;
    private boolean lit;
    private Motion motion;

    /** Extra movement applied every tick, for paths that velocity and gravity alone can't make. */
    public interface Motion {
        void move(FxParticle p, int age);
    }

    public FxParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, Layer layer) {
        super(level, x, y, z, sprite);
        this.layer = layer;
        this.additive = layer == FxLayers.ADDITIVE;
        this.lit = additive;
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

    /** Forgets the count. The game throws all particles away when the world changes without telling them. */
    public static void resetCount() {
        alive = 0;
    }

    public FxParticle life(int ticks) {
        this.lifetime = Math.max(1, ticks);
        return this;
    }

    public FxParticle vel(net.minecraft.world.phys.Vec3 v) {
        return vel(v.x, v.y, v.z);
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

    /** Lies along its direction of travel. The sprite's +x axis is the front. */
    public FxParticle axial() {
        this.axial = true;
        return this;
    }

    /** Like axial(), but along a fixed direction that doesn't depend on how fast the sprite is moving. */
    public FxParticle axis(double x, double y, double z) {
        this.axial = true;
        this.axisDir = new Vector3f((float) x, (float) y, (float) z).normalize();
        return this;
    }

    /** With axial or axis: the sprite's up runs along the axis instead of its front. */
    public FxParticle upright() {
        this.axisY = true;
        return this;
    }

    /** Orients the quad exactly: local x is the quad's right, local y its up, local z its face normal. */
    public FxParticle orient(Quaternionf q) {
        this.basis = q;
        return this;
    }

    /** Random brightness flicker, 0 to 1. */
    public FxParticle flicker(float amount) {
        this.flicker = amount;
        return this;
    }

    /** Fully bright regardless of the light where it is. Light sprites always are; solid ones follow the world unless this is set. */
    public FxParticle bright() {
        this.lit = true;
        return this;
    }

    public FxParticle motion(Motion m) {
        this.motion = m;
        return this;
    }

    public int age() {
        return age;
    }

    public double px() {
        return x;
    }

    public double py() {
        return y;
    }

    public double pz() {
        return z;
    }

    public FxParticle at(double px, double py, double pz) {
        this.x = px;
        this.y = py;
        this.z = pz;
        this.xo = px;
        this.yo = py;
        this.zo = pz;
        return this;
    }

    public double vx() {
        return xd;
    }

    public double vy() {
        return yd;
    }

    public double vz() {
        return zd;
    }

    /** Sets the velocity so the next tick's move ends at the target. Use with drag(1) and no gravity, for orbiting and homing. */
    public FxParticle steer(double tx, double ty, double tz) {
        this.xd = tx - x;
        this.yd = ty - y;
        this.zd = tz - z;
        return this;
    }

    public FxParticle addVel(double dx, double dy, double dz) {
        this.xd += dx;
        this.yd += dy;
        this.zd += dz;
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

    /** Light sprites are always fully lit. Solid ones follow the world's light, but never go fully dark. */
    @Override
    protected int getLightCoords(float partialTick) {
        if (lit) return 0xF000F0;
        int packed = super.getLightCoords(partialTick);
        int block = Math.max(packed & 0xFFFF, 0x0080) & 0xFFFF;
        int sky = Math.max((packed >> 16) & 0xFFFF, 0x00A0);
        return (sky << 16) | block;
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
        if (motion != null && !removed) motion.move(this, age);
    }

    @Override
    public void remove() {
        if (!this.removed && alive > 0) alive--;
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
        if (flicker > 0f) fade *= 1f - flicker * (0.5f + 0.5f * (float) Math.sin((age + partialTick) * 2.3f + x * 7.0 + z * 3.0));
        fade *= FxView.visibility(camera, Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y), Mth.lerp(partialTick, zo, z), getQuadSize(partialTick));
        if (fade <= 0.004f) return; // fully faded, don't draw an invisible quad
        float r = Mth.lerp(t, r0, r1), g = Mth.lerp(t, g0, g1), b = Mth.lerp(t, b0, b1);
        if (additive) {
            // Additive blending: the light is the color, so fading means dimming the color
            fade *= FxView.crowd();
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
        if (basis != null) {
            extractRotatedQuad(state, camera, basis, partialTick);
        } else if (axial) {
            double px = Mth.lerp(partialTick, xo, x), py = Mth.lerp(partialTick, yo, y), pz = Mth.lerp(partialTick, zo, z);
            Vector3f ax = axisDir != null ? new Vector3f(axisDir) : new Vector3f((float) xd, (float) yd, (float) zd);
            if (ax.lengthSquared() < 1.0e-8f) ax.set(0, 1, 0);
            ax.normalize();
            Vector3f toCam = new Vector3f((float) (camera.position().x - px), (float) (camera.position().y - py), (float) (camera.position().z - pz));
            Vector3f n = new Vector3f(toCam).sub(new Vector3f(ax).mul(toCam.dot(ax)));
            if (n.lengthSquared() < 1.0e-8f) n.set(camera.upVector());
            n.normalize();
            Quaternionf q;
            if (axisY) {
                Vector3f right = new Vector3f(ax).cross(n).normalize();
                q = new Quaternionf().setFromNormalized(new org.joml.Matrix3f(right, ax, n));
            } else {
                Vector3f up = new Vector3f(n).cross(ax).normalize();
                q = new Quaternionf().setFromNormalized(new org.joml.Matrix3f(ax, up, n));
            }
            extractRotatedQuad(state, camera, q, partialTick);
        } else if (fixed == null) {
            super.extract(state, camera, partialTick);
        } else {
            Quaternionf q = new Quaternionf(fixed);
            if (roll != 0f || oRoll != 0f) q.rotateZ(Mth.lerp(partialTick, oRoll, roll));
            extractRotatedQuad(state, camera, q, partialTick);
        }
    }
}
