package dev.abps.client.fx;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * One piece of a ribbon. Unlike a normal sprite it shows only a slice of its texture, and a Ribbon moves it every
 * tick by setting where it should be next tick. Everything (place, turn, size, slice, brightness) is blended
 * between ticks so the ribbon flows smoothly at any frame rate.
 */
final class RibbonQuad extends SingleQuadParticle {

    private final Layer layer;
    private final boolean additive;
    private final int rgb;

    private final Quaternionf qPrev = new Quaternionf(), qCur = new Quaternionf(), qNext = new Quaternionf();
    private float sPrev, sCur, sNext;
    private float uaPrev, uaCur, uaNext, ubPrev, ubCur, ubNext;
    private float gPrev, gCur, gNext;
    private double nx, ny, nz;
    private boolean hasNext;
    private final Quaternionf work = new Quaternionf();
    private final org.joml.Vector3f face = new org.joml.Vector3f();

    RibbonQuad(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, Layer layer, int rgb, int life) {
        super(level, x, y, z, sprite);
        this.layer = layer;
        this.additive = layer == FxLayers.ADDITIVE;
        this.rgb = rgb;
        this.hasPhysics = false;
        this.gravity = 0;
        this.lifetime = life;
        this.xd = this.yd = this.zd = 0;
    }

    /** Where this piece should be at the next tick. The first call also places it right away. */
    void key(double x, double y, double z, Quaternionf q, float size, float ua, float ub, float gain, boolean first) {
        nx = x;
        ny = y;
        nz = z;
        qNext.set(q);
        sNext = size;
        uaNext = ua;
        ubNext = ub;
        gNext = gain;
        hasNext = true;
        if (first) {
            setPos(x, y, z); // also moves the bounding box, or the game culls the piece as if it were still at 0,0,0
            this.xo = x;
            this.yo = y;
            this.zo = z;
            qPrev.set(q);
            qCur.set(q);
            sPrev = sCur = size;
            uaPrev = uaCur = ua;
            ubPrev = ubCur = ub;
            gPrev = gCur = gain;
        }
    }

    void frame(TextureAtlasSprite s) {
        setSprite(s);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        qPrev.set(qCur);
        sPrev = sCur;
        uaPrev = uaCur;
        ubPrev = ubCur;
        gPrev = gCur;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (hasNext) {
            setPos(nx, ny, nz);
            qCur.set(qNext);
            sCur = sNext;
            uaCur = uaNext;
            ubCur = ubNext;
            gCur = gNext;
        }
    }

    @Override
    protected Layer getLayer() {
        return layer;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }


    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float pt) {
        double px = Mth.lerp(pt, xo, x), py = Mth.lerp(pt, yo, y), pz = Mth.lerp(pt, zo, z);
        float size = Mth.lerp(pt, sPrev, sCur);
        float gain = Mth.lerp(pt, gPrev, gCur);
        gain *= FxView.visibility(camera, px, py, pz, size * 0.35f);
        if (gain <= 0.004f || size <= 0.001f) return;
        float ua = Mth.lerp(pt, uaPrev, uaCur), ub = Mth.lerp(pt, ubPrev, ubCur);
        qPrev.slerp(qCur, pt, work);
        // Quads only have a front side. If this one faces away, turn it half way round its up axis so the
        // front faces the camera, and run the texture backwards so the stroke still points the same way.
        float rx = (float) (px - camera.position().x), ry = (float) (py - camera.position().y), rz = (float) (pz - camera.position().z);
        work.transform(0, 0, 1, face);
        if (face.x * rx + face.y * ry + face.z * rz > 0) {
            work.rotateY((float) Math.PI);
            float swap = ua;
            ua = ub;
            ub = swap;
        }
        int color;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        if (additive) {
            gain *= FxView.crowd();
            color = ARGB.color(255, Math.min(255, (int) (r * gain)), Math.min(255, (int) (g * gain)), Math.min(255, (int) (b * gain)));
        } else {
            color = ARGB.color(Math.min(255, (int) (255 * gain)), r, g, b);
        }
        state.add(layer, rx, ry, rz,
                work.x, work.y, work.z, work.w, size,
                sprite.getU(Mth.clamp(ua, 0f, 1f)), sprite.getU(Mth.clamp(ub, 0f, 1f)), sprite.getV0(), sprite.getV1(), color, 0xF000F0);
    }
}
