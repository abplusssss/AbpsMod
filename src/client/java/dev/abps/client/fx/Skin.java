package dev.abps.client.fx;

import net.minecraft.world.phys.Vec3;

import static dev.abps.client.fx.FxKit.*;

/**
 * What the basic effect pieces look like for one class. The server describes an effect as "a burst here, a ring
 * there, a beam between these points"; the skin decides what those are made of: embers and flames for the
 * Pyromancer, drops and foam for the Shark, drifting leaves for the Druid, soul wisps for the Necromancer, and so on.
 * This is why the same kind of effect never looks the same on two classes.
 */
abstract class Skin {

    static final Skin[] ALL = new Skin[12];

    /** Theme numbers, matching what the server sends. 0 is the plain energy look. */
    static final int NONE = 0, ARCHER = 1, ASSASSIN = 2, BERSERKER = 3, DRUID = 4, MINER = 5, NECRO = 6, PYRO = 7,
            SHARK = 8, TANK = 9, VAMPIRE = 10, WIND = 11;

    static {
        ALL[NONE] = new Plain(0xFFFFFF, 0xC8C8FF);
        ALL[ARCHER] = new Archer(0x9CCC65, 0x00BFA5);
        ALL[ASSASSIN] = new Assassin(0xB388FF, 0x4A148C);
        ALL[BERSERKER] = new Berserker(0xFF5252, 0xFFAB00);
        ALL[DRUID] = new Druid(0x76FF03, 0x1B5E20);
        ALL[MINER] = new Miner(0xFFC107, 0xFF6F00);
        ALL[NECRO] = new Necro(0x64FFDA, 0x311B92);
        ALL[PYRO] = new Pyro(0xFFD600, 0xFF3D00);
        ALL[SHARK] = new Shark(0x0288D1, 0x4DD0E1);
        ALL[TANK] = new Tank(0xB0BEC5, 0x42A5F5);
        ALL[VAMPIRE] = new Vampire(0xE53935, 0x6A0DAD);
        ALL[WIND] = new Wind(0xE0F7FA, 0x29B6F6);
    }

    static Skin of(int theme) {
        return theme >= 0 && theme < ALL.length && ALL[theme] != null ? ALL[theme] : ALL[NONE];
    }

    final int accent, accent2;

    Skin(int accent, int accent2) {
        this.accent = accent;
        this.accent2 = accent2;
    }

    /** Styles a ribbon in this class's look: dark ink with a glowing rim for the shadowy classes, streaky light for the rest. */
    Ribbon.Builder stroke(Ribbon.Builder b, int col) {
        if (this instanceof Assassin) return b.ink(0x0C0A12, 0xE6DAFF);
        if (this instanceof Necro) return b.ink(0x100A20, 0x64FFDA);
        if (this instanceof Vampire) return b.ink(0x1C0307, 0xFF4B4B);
        return b.energy(tone(col));
    }

    /** Effects that ask for plain white get this class's own color instead. */
    int tone(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return r > 225 && g > 225 && b > 225 ? accent : c;
    }

    // ---- the pieces every skin provides

    /** One piece thrown out of something: a spark, ember, drop, leaf or wisp. gravity is 0 for floating, about 0.5 for heavy. */
    abstract void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity);

    /** A soft cloud piece: smoke, mist, foam, spores. strength is how solid it is, 0 to 1. */
    abstract void body(Vec3 p, Vec3 v, float size, int life, int col, float strength);

    /** The hot flash at the middle of an impact. */
    void flash(Vec3 p, float size, int life, int col) {
        bloom(p, size, life, tone(col));
    }

    /** An expanding ring around a normal. */
    void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
        ringFlat(c, n, r0, r1, life, tone(col), "ring");
    }

    /** Something to leave behind along beams, trails and flying objects. */
    void along(Vec3 p, Vec3 dir, float size, int col) {
        mote(p, new Vec3(gauss() * 0.02, gauss() * 0.02 + 0.01, gauss() * 0.02), size * 0.7f, 10, col, 0f);
    }

    /** A mark left on the ground after a big hit. */
    void ground(Vec3 p, float radius, int col, int life) {
    }

    /** One tick of a column effect (pillars, geysers, pillars of flame). h is how tall it is right now. */
    void column(Vec3 base, double radius, double h, int col, int age) {
        int c = tone(col);
        int count = n(3 + (int) (h * 0.8));
        for (int k = 0; k < count; k++) {
            double a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * radius * 0.8;
            sp("glow", base.x + Math.cos(a) * r, base.y + rnd() * h, base.z + Math.sin(a) * r).size((float) (radius * 1.6 + 0.25), (float) (radius + 0.12))
                    .life(5).colors(lighten(c, 0.4f), c).vel(0, 0.02, 0);
        }
    }

    /** The sprite for things that circle a caster. */
    String orbSprite() {
        return "glow";
    }

    /** One tick of one orbiting piece (called each tick, so pieces live for only a moment). tangent is its direction of travel. */
    void orbiter(Vec3 p, Vec3 tangent, float size, int col) {
        int c = tone(col);
        String s = orbSprite();
        sp("glow", p).size(size * 2.6f, size * 1.2f).life(3).colors(lighten(c, 0.6f), c).envelope(0.1f, 0.5f, 0.8f);
        FxParticle q = sp(s, p).size(size * 3.0f, size * 2.6f).life(3).colors(lighten(c, 0.5f), c).envelope(0.1f, 0.5f, 1f);
        if (s.equals("arrow") || s.equals("streak")) q.vel(tangent.scale(0.01)).axial().drag(1f);
        else if (s.equals("leaf") || s.equals("petal") || s.equals("bat") || s.equals("shard") || s.equals("debris")) q.startRoll((float) (rnd() * 6.28)).bright();
    }

    /** A piece of an arc: called at points along a slash. tangent points along the swing. */
    void slashPiece(Vec3 p, Vec3 tangent, float size, int col) {
        int c = tone(col);
        sp("glow", p).size(size * 1.4f, size * 0.5f).life(6).colors(lighten(c, 0.5f), c).envelope(0.05f, 0.3f, 0.9f);
        mote(p, tangent.scale(0.05).add(gauss() * 0.02, gauss() * 0.02, gauss() * 0.02), size * 0.6f, 9, c, 0f);
    }

    // ---- shared helpers

    static FxParticle add(String sprite, Vec3 p, float s0, float s1, int life, int c0, int c1) {
        return sp(sprite, p).size(s0, s1).life(life).colors(c0, c1);
    }

    /** Leaves a little glow behind a particle as it moves. */
    static FxParticle trail(FxParticle q, int c, float size, int every, int life) {
        return q.motion((pp, age) -> {
            if (age % every == 0) sp("glow", pp.px(), pp.py(), pp.pz()).size(size, size * 0.2f).life(life).colors(c, darken(c, 0.5f)).drag(1f).envelope(0.05f, 0.2f, 0.8f);
        });
    }

    /** Points spread around a ring of radius r. */
    static Vec3 onRing(Vec3 c, Vec3[] uv, double r, double angle) {
        return c.add(uv[0].scale(Math.cos(angle) * r)).add(uv[1].scale(Math.sin(angle) * r));
    }

    static Vec3 radial(Vec3[] uv, double angle) {
        return uv[0].scale(Math.cos(angle)).add(uv[1].scale(Math.sin(angle)));
    }

    // ================================================================== the skins

    /** No class: clean white-blue energy. */
    static final class Plain extends Skin {
        Plain(int a, int b) {
            super(a, b);
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            sp("spark", p).size(size * 2.2f, size * 0.3f).life(life).colors(lighten(col, 0.6f), col).vel(v).grav(gravity).drag(0.93f)
                    .startRoll((float) (rnd() * 3)).spin((float) (gauss() * 0.08));
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            add("smoke", p, size * 0.5f, size * 1.5f, life, lighten(col, 0.3f), col).vel(v).envelope(0.15f, 0.45f, strength * 0.5f).spin((float) (gauss() * 0.02));
        }
    }

    /** Green precision: wind lines that point where they are going, target reticles. */
    static final class Archer extends Skin {
        Archer(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "arrow";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            add("streak", p, size * 3.4f, size * 0.7f, life, WHITE, c).vel(v).axial().drag(0.95f).grav(gravity * 0.3f).envelope(0.03f, 0.5f, 1f);
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            int c = tone(col);
            add("smoke", p, size * 0.5f, size * 1.4f, life + 4, lighten(c, 0.5f), lighten(c, 0.8f)).vel(v.scale(0.3)).envelope(0.2f, 0.45f, 0.26f * strength);
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size * 0.9f, life, c);
            for (int k = 0; k < n(6); k++) {
                Vec3 d = rndDir();
                add("streak", p.add(d.scale(size * 0.4)), size * 2.6f, size * 0.4f, life, WHITE, c).vel(d.scale(0.2)).axial().drag(0.92f);
            }
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "ring");
            // Crosshair ticks pointing at the middle
            Vec3[] uv = axes(n);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + Math.PI / 4;
                Vec3 out = radial(uv, a);
                add("streak", onRing(c, uv, r0, a), 0.5f + (float) r1 * 0.14f, 0.2f, life, WHITE, col2).vel(out.scale((r1 - r0) / life)).drag(1f).axial().envelope(0.05f, 0.3f, 1f);
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            add("streak", p, size * 2.4f, size * 0.3f, 8, WHITE, tone(col)).vel(dir.scale(0.001)).axial().drag(1f).envelope(0.05f, 0.3f, 0.7f);
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (double y = 0; y < h; y += 0.8) sp("glow", base.x, base.y + y, base.z).size((float) radius * 0.9f + 0.1f, (float) radius * 0.4f).life(3).colors(WHITE, c);
            if (age % 2 == 0) add("streak", base.add(gauss() * radius * 0.5, 0.2, gauss() * radius * 0.5), 0.9f, 0.4f, 12, WHITE, c).vel(0, 0.4, 0).axial().drag(0.97f);
        }
    }

    /** Violet shadow: black smoke, tumbling shards, claw marks. */
    static final class Assassin extends Skin {
        Assassin(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "shard";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            add("shard", p, size * 2.0f, size * 0.4f, life, lighten(c, 0.6f), c).vel(v).drag(0.9f).grav(gravity * 0.5f)
                    .spin((float) (gauss() * 0.3)).startRoll((float) (rnd() * 6.28)).envelope(0.03f, 0.55f, 1f);
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            int c = tone(col);
            add("smoke", p, size * 0.6f, size * 1.7f, life + 4, mix(c, 0x0A0012, 0.75f), mix(c, 0x000000, 0.85f)).vel(v).envelope(0.12f, 0.4f, 0.75f * strength).spin((float) (gauss() * 0.03));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size * 0.8f, life, c);
            add("claw", p, size * 1.4f, size * 2.2f, life + 2, WHITE, c).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.3f, 1f);
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            body(p, new Vec3(gauss() * 0.01, 0.01, gauss() * 0.01), size * 1.2f, 12, col, 0.6f);
            if (rnd() < 0.35) mote(p, new Vec3(gauss() * 0.03, gauss() * 0.03, gauss() * 0.03), size * 0.6f, 10, col, 0f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            add("smoke", p.add(0, 0.05, 0), radius * 0.9f, radius * 1.3f, life, mix(tone(col), 0x000000, 0.8f), 0x000000).facing(0, 1, 0).envelope(0.15f, 0.5f, 0.6f);
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(3); k++) {
                double a = rnd() * 6.28;
                body(base.add(Math.cos(a) * radius * 0.7, rnd() * h, Math.sin(a) * radius * 0.7), new Vec3(0, 0.03, 0), (float) radius + 0.4f, 12, c, 0.8f);
            }
            if (age % 2 == 0) add("wisp", base.add(gauss() * radius * 0.6, 0.2, gauss() * radius * 0.6), 0.6f, 0.2f, 16, lighten(c, 0.4f), c).vel(0, 0.14, 0).envelope(0.1f, 0.5f, 0.9f);
        }

        @Override
        void slashPiece(Vec3 p, Vec3 tangent, float size, int col) {
            int c = tone(col);
            add("glow", p, size * 1.4f, size * 0.4f, 6, WHITE, c).envelope(0.05f, 0.3f, 0.9f);
            body(p, tangent.scale(0.03), size * 1.5f, 10, c, 0.5f);
        }
    }

    /** Fury: flying rubble, embers, shockwaves and cracked ground. */
    static final class Berserker extends Skin {
        Berserker(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "ember";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            if (rnd() < 0.45) {
                add("debris", p, size * 1.6f, size * 1.2f, life + 8, darken(c, 0.55f), darken(c, 0.75f)).vel(v).grav(0.55f).drag(0.96f)
                        .spin((float) (gauss() * 0.25)).startRoll((float) (rnd() * 6.28)).envelope(0.02f, 0.75f, 1f).bright();
            } else {
                trail(add("ember", p, size * 2.4f, size * 0.4f, life + 4, lighten(c, 0.7f), c).vel(v).grav(0.18f + gravity * 0.3f).drag(0.92f).flicker(0.4f), c, size * 1.1f, 2, 5);
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            int c = tone(col);
            add("smoke", p, size * 0.6f, size * 1.7f, life + 4, mix(c, 0x3A1A10, 0.6f), 0x2A2018).vel(v).envelope(0.12f, 0.45f, 0.6f * strength).spin((float) (gauss() * 0.03));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size, life, c);
            add("rays", p, size * 0.6f, size * 2.2f, life, WHITE, c).startRoll((float) (rnd() * 6.28)).spin((float) (gauss() * 0.03)).envelope(0.05f, 0.3f, 0.9f);
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            ringFlat(c, n, r0, r1, life, tone(col), "shockwave");
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            int c = tone(col);
            add("crack", p.add(0, 0.04, 0), radius, radius * 1.05f, life + 30, 0xFFFFFF, 0xFFFFFF).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.7f, 1f);
            add("crackglow", p.add(0, 0.06, 0), radius, radius * 1.05f, life, WHITE, mix(c, 0x400000, 0.5f)).facing(0, 1, 0).flicker(0.25f).envelope(0.05f, 0.3f, 1f);
            for (int k = 0; k < n(8); k++) {
                double a = rnd() * 6.28;
                mote(p.add(Math.cos(a) * radius * 0.3, 0.2, Math.sin(a) * radius * 0.3), new Vec3(Math.cos(a) * 0.16, 0.25 + rnd() * 0.25, Math.sin(a) * 0.16), 0.14f, 20, c, 0.5f);
            }
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(3); k++) {
                double a = rnd() * 6.28, r = Math.sqrt(rnd()) * radius;
                add("flame", base.add(Math.cos(a) * r, rnd() * h * 0.5, Math.sin(a) * r), (float) radius + 0.5f, (float) radius * 0.5f, 8, lighten(c, 0.7f), mix(c, 0x600000, 0.5f)).vel(0, 0.15, 0).flicker(0.3f).envelope(0.05f, 0.5f, 0.9f);
            }
            if (age % 3 == 0) mote(base.add(gauss() * radius * 0.5, 0.2, gauss() * radius * 0.5), new Vec3(gauss() * 0.05, 0.3, gauss() * 0.05), 0.15f, 16, c, 0.3f);
        }

        @Override
        void slashPiece(Vec3 p, Vec3 tangent, float size, int col) {
            int c = tone(col);
            add("glow", p, size * 1.6f, size * 0.5f, 6, lighten(c, 0.6f), c).envelope(0.05f, 0.3f, 0.95f);
            mote(p, tangent.scale(0.06).add(gauss() * 0.04, gauss() * 0.04, gauss() * 0.04), size * 0.7f, 12, c, 0.3f);
        }
    }

    /** Nature: drifting leaves and petals, pollen, spore mist, growing vines. */
    static final class Druid extends Skin {
        Druid(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "leaf";
        }

        private static void flutter(FxParticle q) {
            float phase = (float) (rnd() * 6.28);
            q.motion((pp, age) -> pp.addVel(Math.sin(age * 0.45 + phase) * 0.012, 0, Math.cos(age * 0.37 + phase) * 0.012));
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            double roll = rnd();
            if (roll < 0.55) {
                FxParticle l = add("leaf", p, size * 2.2f, size * 1.9f, life + 14, mix(c, 0xFFFFFF, 0.2f), mix(c, 0x3A5A10, 0.4f)).vel(v).grav(0.05f + gravity * 0.25f).drag(0.93f)
                        .spin((float) (gauss() * 0.12)).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.7f, 1f);
                flutter(l);
            } else if (roll < 0.75) {
                FxParticle l = add("petal", p, size * 1.8f, size * 1.5f, life + 14, mix(0xFFB7D5, WHITE, (float) rnd() * 0.5f), 0xFF80AB).vel(v).grav(0.04f).drag(0.93f)
                        .spin((float) (gauss() * 0.1)).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.7f, 1f);
                flutter(l);
            } else {
                add("glow", p, size * 1.6f, size * 0.3f, life + 8, mix(c, 0xFFFF80, 0.5f), c).vel(v.scale(0.6)).drag(0.93f).grav(-0.01f).flicker(0.5f).envelope(0.1f, 0.5f, 0.9f);
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            int c = tone(col);
            add("smoke", p, size * 0.5f, size * 1.4f, life + 6, mix(c, 0xD8FFB0, 0.35f), mix(c, 0x4B7A2A, 0.5f)).vel(v.scale(0.5)).envelope(0.2f, 0.45f, 0.32f * strength);
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            sp("glow", p).size(size * 1.3f, size * 2.6f).life(life + 6).colors(mix(c, WHITE, 0.5f), c).envelope(0.1f, 0.4f, 0.8f);
            sp("glow", p).size(size * 0.5f, size * 1.0f).life(life).colors(WHITE, mix(c, WHITE, 0.5f)).envelope(0.05f, 0.3f, 1f);
            for (int k = 0; k < n(6); k++) mote(p, rndDir().scale(0.14), size * 0.4f, 18, c, 0.2f);
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "ring");
            Vec3[] uv = axes(n);
            int count = n((int) Math.max(6, r1 * 2.2));
            double phase = rnd() * 6.28;
            for (int k = 0; k < count; k++) {
                double a = phase + Math.PI * 2 * k / count;
                Vec3 out = radial(uv, a);
                FxParticle l = add("leaf", onRing(c, uv, r0, a), 0.34f, 0.28f, life + 8, mix(col2, WHITE, 0.2f), mix(col2, 0x3A5A10, 0.4f)).vel(out.scale((r1 - r0) / life * 1.05)).drag(0.98f)
                        .spin((float) (gauss() * 0.15)).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.7f, 1f);
                flutter(l);
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            if (rnd() < 0.5) mote(p, new Vec3(gauss() * 0.02, -0.01, gauss() * 0.02), size * 0.9f, 16, col, 0.1f);
            else add("glow", p, size, 0.05f, 10, mix(tone(col), 0xFFFF80, 0.5f), tone(col)).vel(gauss() * 0.01, 0.015, gauss() * 0.01).flicker(0.4f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            add("sigil", p.add(0, 0.04, 0), radius, radius, life, lighten(tone(col), 0.3f), tone(col)).facing(0, 1, 0).spin(0.012f).envelope(0.15f, 0.5f, 0.8f);
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            // Vines: leaves spiralling up the column
            for (int k = 0; k < n(3); k++) {
                double t = rnd(), a = age * 0.35 + t * 9 + k * 2.1;
                double r = radius * (0.6 + 0.4 * rnd());
                FxParticle l = add(rnd() < 0.7 ? "leaf" : "petal", base.add(Math.cos(a) * r, t * h, Math.sin(a) * r), 0.3f, 0.24f, 14, mix(c, WHITE, 0.2f), mix(c, 0x3A5A10, 0.4f))
                        .vel(0, 0.03, 0).spin((float) (gauss() * 0.1)).startRoll((float) (rnd() * 6.28)).envelope(0.1f, 0.7f, 1f);
                flutter(l);
            }
            add("glow", base.add(gauss() * radius * 0.4, rnd() * h, gauss() * radius * 0.4), 0.3f, 0.05f, 12, mix(c, 0xFFFF80, 0.5f), c).vel(0, 0.03, 0).flicker(0.4f);
        }
    }

    /** Stone and gold: crystals, rubble, dust clouds, glittering sparks. */
    static final class Miner extends Skin {
        Miner(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "shard";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            if (rnd() < 0.5) {
                add("debris", p, size * 1.7f, size * 1.3f, life + 8, 0xB09070, 0x806040).vel(v).grav(0.6f).drag(0.96f)
                        .spin((float) (gauss() * 0.25)).startRoll((float) (rnd() * 6.28)).envelope(0.02f, 0.75f, 1f).bright();
            } else {
                add("shard", p, size * 2.0f, size * 0.7f, life + 4, lighten(c, 0.7f), c).vel(v).grav(0.25f + gravity * 0.2f).drag(0.94f)
                        .spin((float) (gauss() * 0.2)).startRoll((float) (rnd() * 6.28)).envelope(0.03f, 0.6f, 1f);
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            add("smoke", p, size * 0.6f, size * 1.8f, life + 6, 0xA88C68, 0x6E5A40).vel(v).envelope(0.15f, 0.45f, 0.6f * strength).spin((float) (gauss() * 0.03));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size, life, c);
            add("rays", p, size * 0.5f, size * 1.9f, life, WHITE, c).startRoll((float) (rnd() * 6.28)).spin(0.03f).envelope(0.05f, 0.3f, 0.9f);
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            ringFlat(c, n, r0, r1, life, tone(col), "shockwave");
            if (n.y > 0.9) {
                int count = n((int) Math.max(6, r1 * 2));
                for (int k = 0; k < count; k++) {
                    double a = Math.PI * 2 * k / count + rnd() * 0.3;
                    body(c.add(Math.cos(a) * r0, 0.3, Math.sin(a) * r0), new Vec3(Math.cos(a), 0.05, Math.sin(a)).scale((r1 - r0) / life), 0.8f, life, col, 0.5f);
                }
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            sp("spark", p).size(size * 1.2f, 0.05f).life(9).colors(WHITE, tone(col)).vel(gauss() * 0.02, gauss() * 0.02, gauss() * 0.02).drag(0.9f).spin(0.1f).envelope(0.05f, 0.4f, 1f);
            if (rnd() < 0.3) mote(p, new Vec3(gauss() * 0.03, 0.02, gauss() * 0.03), size * 0.6f, 14, col, 0.5f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            add("crack", p.add(0, 0.04, 0), radius, radius * 1.05f, life + 30, 0xFFFFFF, 0xFFFFFF).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.7f, 1f);
            for (int k = 0; k < n(6); k++) {
                double a = rnd() * 6.28;
                mote(p.add(Math.cos(a) * radius * 0.3, 0.2, Math.sin(a) * radius * 0.3), new Vec3(Math.cos(a) * 0.12, 0.3 + rnd() * 0.2, Math.sin(a) * 0.12), 0.16f, 22, col, 0.6f);
            }
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(2); k++) {
                double a = rnd() * 6.28, r = Math.sqrt(rnd()) * radius;
                mote(base.add(Math.cos(a) * r, rnd() * h, Math.sin(a) * r), new Vec3(0, 0.05, 0), 0.22f, 12, c, 0.1f);
            }
            for (double y = 0; y < h; y += 1.2) body(base.add(gauss() * radius * 0.4, y, gauss() * radius * 0.4), new Vec3(0, 0.02, 0), (float) radius + 0.3f, 8, c, 0.5f);
            sp("glow", base.x, base.y + h * rnd(), base.z).size((float) radius, (float) radius * 0.4f).life(4).colors(lighten(c, 0.5f), c);
        }
    }

    /** Souls: wisps that rise and curl, skulls, rune circles, cold fog. */
    static final class Necro extends Skin {
        Necro(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "wisp";
        }

        private static void curl(FxParticle q) {
            float phase = (float) (rnd() * 6.28);
            q.motion((pp, age) -> pp.addVel(Math.sin(age * 0.3 + phase) * 0.008, 0.0015, Math.cos(age * 0.27 + phase) * 0.008));
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            if (rnd() < 0.1) {
                add("skull", p, size * 2.2f, size * 3.2f, life + 10, lighten(c, 0.6f), c).vel(v.scale(0.4).add(0, 0.03, 0)).drag(0.94f).envelope(0.15f, 0.5f, 0.9f).flicker(0.3f);
            } else {
                curl(add("wisp", p, size * 3.0f, size * 1.2f, life + 10, lighten(c, 0.5f), mix(c, accent2, 0.6f)).vel(v.scale(0.6).add(0, 0.02, 0)).drag(0.94f)
                        .startRoll((float) (gauss() * 0.4)).envelope(0.1f, 0.5f, 0.95f));
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            int c = tone(col);
            add("smoke", p, size * 0.6f, size * 1.7f, life + 8, mix(c, 0x0A1F2A, 0.65f), mix(accent2, 0x000000, 0.6f)).vel(v.scale(0.5).add(0, 0.008, 0)).envelope(0.15f, 0.45f, 0.55f * strength).spin((float) (gauss() * 0.02));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size * 0.9f, life, c);
            add("rune", p, size * 0.8f, size * 1.6f, life + 6, lighten(c, 0.5f), c).spin(0.05f).startRoll((float) (rnd() * 6.28)).envelope(0.1f, 0.5f, 1f);
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "ring");
            if (r1 >= 1.5) add("sigil", c, (float) (r0 / 0.94), (float) (r1 / 0.94), life, lighten(col2, 0.3f), col2).facing(n.x, n.y, n.z).spin(0.03f).envelope(0.1f, 0.5f, 0.8f);
            Vec3[] uv = axes(n);
            for (int k = 0; k < n(5); k++) {
                double a = rnd() * 6.28;
                mote(onRing(c, uv, r0, a), radial(uv, a).scale((r1 - r0) / life * 0.6).add(0, 0.04, 0), 0.18f, life, col2, 0f);
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            curl(add("wisp", p, size * 2.0f, size * 0.6f, 16, lighten(tone(col), 0.5f), mix(tone(col), accent2, 0.6f)).vel(gauss() * 0.01, 0.02, gauss() * 0.01).envelope(0.1f, 0.5f, 0.8f));
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            int c = tone(col);
            add("sigil", p.add(0, 0.04, 0), radius, radius, life, lighten(c, 0.3f), c).facing(0, 1, 0).spin(0.02f).envelope(0.1f, 0.6f, 0.9f);
            for (int k = 0; k < n(6); k++) {
                double a = rnd() * 6.28, r = rnd() * radius;
                curl(add("wisp", p.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 0.7f, 0.3f, life / 2, lighten(c, 0.5f), c).vel(0, 0.05 + rnd() * 0.05, 0).envelope(0.15f, 0.5f, 0.9f));
            }
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(2); k++) {
                double t = rnd(), a = age * 0.28 + t * 10 + k * 3;
                curl(add("wisp", base.add(Math.cos(a) * radius * 0.8, t * h, Math.sin(a) * radius * 0.8), 0.7f, 0.3f, 14, lighten(c, 0.5f), c).vel(0, 0.08, 0).envelope(0.1f, 0.5f, 0.95f));
            }
            if (age % 6 == 0) add("skull", base.add(0, h * rnd(), 0), 0.5f, 0.7f, 14, lighten(c, 0.6f), c).vel(0, 0.06, 0).envelope(0.15f, 0.5f, 0.9f);
            body(base.add(gauss() * radius * 0.5, rnd() * h, gauss() * radius * 0.5), new Vec3(0, 0.02, 0), (float) radius + 0.5f, 10, c, 0.6f);
        }
    }

    /** Fire: rising embers and flames, ash smoke, scorch marks. */
    static final class Pyro extends Skin {
        Pyro(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "flame";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            if (rnd() < 0.55) {
                trail(add("ember", p, size * 2.6f, size * 0.4f, life + 8, lighten(c, 0.75f), mix(c, 0xFF3000, 0.6f)).vel(v).grav(-0.015f + gravity * 0.25f).drag(0.92f).flicker(0.5f).envelope(0.03f, 0.55f, 1f),
                        c, size * 1.2f, 2, 5);
            } else {
                add("flame", p, size * 3.4f, size * 0.9f, life + 4, lighten(c, 0.8f), mix(c, 0x8A1000, 0.6f)).vel(v.scale(0.7).add(0, 0.03, 0)).drag(0.93f).flicker(0.35f)
                        .startRoll((float) (gauss() * 0.15)).envelope(0.05f, 0.5f, 0.95f);
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            int c = tone(col);
            add("smoke", p, size * 0.6f, size * 1.8f, life + 8, mix(c, 0x201008, 0.7f), 0x141414).vel(v.scale(0.6).add(0, 0.02, 0)).envelope(0.12f, 0.5f, 0.55f * strength).spin((float) (gauss() * 0.03));
            add("flame", p, size * 1.1f, size * 0.3f, Math.max(5, life / 2), lighten(c, 0.6f), mix(c, 0xA01500, 0.5f)).vel(v.scale(0.5)).flicker(0.4f).envelope(0.05f, 0.4f, 0.8f);
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size, life, c);
            add("rays", p, size * 0.5f, size * 2.0f, life, WHITE, c).startRoll((float) (rnd() * 6.28)).spin((float) (gauss() * 0.03)).envelope(0.05f, 0.3f, 0.9f);
            for (int k = 0; k < n(4); k++) {
                Vec3 d = rndUp();
                add("flame", p, size * 1.2f, size * 0.4f, life + 4, lighten(c, 0.8f), mix(c, 0x8A1000, 0.6f)).vel(d.scale(0.12)).drag(0.92f).flicker(0.3f).envelope(0.05f, 0.5f, 0.95f);
            }
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "shockwave");
            // A ring of fire: flame tongues standing on the edge
            Vec3[] uv = axes(n);
            int count = n((int) Math.max(6, r1 * 2.4));
            for (int k = 0; k < count; k++) {
                double a = Math.PI * 2 * k / count + rnd() * 0.2;
                Vec3 out = radial(uv, a);
                add("flame", onRing(c, uv, r0, a), 0.55f, 0.9f, life + 4, lighten(col2, 0.8f), mix(col2, 0x8A1000, 0.6f)).vel(out.scale((r1 - r0) / life).add(0, 0.02, 0)).drag(1f).flicker(0.3f).envelope(0.1f, 0.5f, 0.9f);
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            int c = tone(col);
            add("flame", p, size * 1.6f, size * 0.4f, 9, lighten(c, 0.8f), mix(c, 0x8A1000, 0.6f)).vel(gauss() * 0.01, 0.03, gauss() * 0.01).flicker(0.3f).envelope(0.05f, 0.4f, 0.9f);
            if (rnd() < 0.4) mote(p, new Vec3(gauss() * 0.03, 0.03, gauss() * 0.03), size * 0.5f, 14, c, 0f);
            if (rnd() < 0.3) body(p, new Vec3(0, 0.03, 0), size * 1.2f, 14, c, 0.5f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            int c = tone(col);
            add("smoke", p.add(0, 0.04, 0), radius * 0.9f, radius * 1.15f, life + 40, 0x2A2420, 0x100C0A).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.1f, 0.75f, 0.75f);
            add("crackglow", p.add(0, 0.06, 0), radius, radius * 1.04f, life, WHITE, mix(c, 0x500000, 0.6f)).facing(0, 1, 0).flicker(0.3f).envelope(0.05f, 0.3f, 0.8f);
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(4); k++) {
                double a = rnd() * 6.28, r = Math.sqrt(rnd()) * radius;
                double y = rnd() * h * 0.85;
                add("flame", base.add(Math.cos(a) * r, y, Math.sin(a) * r), (float) (radius * 1.4 + 0.5), (float) radius * 0.6f, 8, lighten(c, 0.8f), mix(c, 0x8A1000, 0.6f + (float) (y / h) * 0.3f))
                        .vel(0, 0.2, 0).flicker(0.3f).envelope(0.05f, 0.5f, 0.9f);
            }
            sp("glow", base.x, base.y + h * 0.5 * rnd(), base.z).size((float) radius * 2f, (float) radius).life(5).colors(lighten(c, 0.5f), c).envelope(0.1f, 0.4f, 0.7f);
            if (age % 2 == 0) mote(base.add(gauss() * radius * 0.5, 0.3, gauss() * radius * 0.5), new Vec3(gauss() * 0.04, 0.25 + rnd() * 0.2, gauss() * 0.04), 0.14f, 22, c, 0f);
            if (age % 3 == 0) body(base.add(gauss() * radius * 0.3, h * (0.6 + rnd() * 0.4), gauss() * radius * 0.3), new Vec3(0, 0.05, 0), (float) radius + 0.6f, 18, c, 0.5f);
        }

        @Override
        void slashPiece(Vec3 p, Vec3 tangent, float size, int col) {
            add("flame", p, size * 2f, size * 0.6f, 8, lighten(tone(col), 0.8f), mix(tone(col), 0x8A1000, 0.6f)).vel(tangent.scale(0.04)).flicker(0.3f).envelope(0.05f, 0.4f, 0.95f);
        }
    }

    /** Water: drops, bubbles, foam, splash rings, geysers. */
    static final class Shark extends Skin {
        Shark(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "droplet";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            if (rnd() < 0.7) {
                add("droplet", p, size * 1.7f, size * 1.2f, life + 10, mix(c, WHITE, 0.7f), mix(c, WHITE, 0.4f)).vel(v).grav(0.55f).drag(0.985f).envelope(0.03f, 0.75f, 0.95f);
            } else {
                add("bubble", p, size * 1.6f, size * 2.4f, life + 14, mix(c, WHITE, 0.6f), mix(c, WHITE, 0.8f)).vel(v.scale(0.5).add(0, 0.02, 0)).drag(0.94f).grav(-0.02f).envelope(0.08f, 0.65f, 0.9f);
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            add("foam", p, size * 0.6f, size * 1.8f, life + 4, WHITE, mix(tone(col), WHITE, 0.6f)).vel(v).envelope(0.12f, 0.5f, 0.85f * strength).spin((float) (gauss() * 0.04));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            sp("glow", p).size(size * 1.2f, size * 2.4f).life(life + 4).colors(mix(c, WHITE, 0.6f), c).envelope(0.1f, 0.35f, 0.75f);
            add("shockwave", p, size * 0.3f, size * 1.8f, life + 2, WHITE, c).envelope(0.05f, 0.3f, 1f);
            for (int k = 0; k < n(8); k++) {
                Vec3 d = rndUp();
                add("droplet", p, size * 0.3f, size * 0.2f, life + 10, WHITE, mix(c, WHITE, 0.4f)).vel(d.scale(0.18 + rnd() * 0.15)).grav(0.55f).drag(0.985f).envelope(0.03f, 0.75f, 0.95f);
            }
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "ring");
            Vec3[] uv = axes(n);
            int count = n((int) Math.max(6, r1 * 2.2));
            for (int k = 0; k < count; k++) {
                double a = Math.PI * 2 * k / count + rnd() * 0.2;
                Vec3 out = radial(uv, a);
                add(rnd() < 0.6 ? "bubble" : "foam", onRing(c, uv, r0, a), 0.22f, 0.3f, life + 6, WHITE, mix(col2, WHITE, 0.6f)).vel(out.scale((r1 - r0) / life).add(0, 0.015, 0)).drag(1f).envelope(0.05f, 0.6f, 0.9f);
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            if (rnd() < 0.6) add("bubble", p, size * 1.0f, size * 1.6f, 16, WHITE, tone(col)).vel(gauss() * 0.01, 0.03, gauss() * 0.01).drag(0.95f).envelope(0.08f, 0.6f, 0.85f);
            else add("droplet", p, size, size * 0.7f, 14, WHITE, tone(col)).vel(gauss() * 0.03, 0.02, gauss() * 0.03).grav(0.5f).envelope(0.03f, 0.7f, 0.9f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            for (int k = 0; k < n(10); k++) {
                double a = rnd() * 6.28, r = Math.sqrt(rnd()) * radius;
                add("foam", p.add(Math.cos(a) * r, 0.12, Math.sin(a) * r), 0.9f, 1.5f, life, WHITE, mix(tone(col), WHITE, 0.6f)).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.15f, 0.5f, 0.6f);
            }
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(3); k++) {
                double a = rnd() * 6.28, r = Math.sqrt(rnd()) * radius * 0.7;
                double y = rnd() * h;
                add("foam", base.add(Math.cos(a) * r, y, Math.sin(a) * r), (float) radius + 0.5f, (float) radius + 0.9f, 7, mix(c, WHITE, 0.6f), WHITE).vel(0, 0.12, 0).envelope(0.15f, 0.5f, 0.85f);
            }
            add("streak", base.add(gauss() * radius * 0.5, rnd() * h, gauss() * radius * 0.5), 1.4f, 0.6f, 6, WHITE, c).vel(0, 0.5, 0).axial().drag(1f).envelope(0.1f, 0.4f, 0.7f);
            for (int k = 0; k < n(2); k++) {
                Vec3 s = base.add(gauss() * radius * 0.5, h * (0.7 + rnd() * 0.3), gauss() * radius * 0.5);
                add("droplet", s, 0.2f, 0.14f, 18, WHITE, mix(c, WHITE, 0.4f)).vel(gauss() * 0.06, 0.1 + rnd() * 0.1, gauss() * 0.06).grav(0.55f).drag(0.985f).envelope(0.03f, 0.75f, 0.95f);
            }
        }
    }

    /** Steel: hot sparks, chunks of stone, dust, shield rings. */
    static final class Tank extends Skin {
        Tank(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "shard";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            if (rnd() < 0.55) {
                add("streak", p, size * 3.0f, size * 0.5f, life, WHITE, mix(c, 0xFFC060, 0.35f)).vel(v).axial().grav(0.3f + gravity * 0.2f).drag(0.95f).envelope(0.03f, 0.5f, 1f);
            } else {
                add("debris", p, size * 1.5f, size * 1.1f, life + 8, 0xA0A8B0, 0x707880).vel(v).grav(0.6f).drag(0.96f).spin((float) (gauss() * 0.25)).startRoll((float) (rnd() * 6.28)).envelope(0.02f, 0.75f, 1f).bright();
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            add("smoke", p, size * 0.6f, size * 1.7f, life + 6, 0xB0B4B8, 0x707478).vel(v).envelope(0.15f, 0.45f, 0.5f * strength).spin((float) (gauss() * 0.03));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size, life, c);
            add("shockwave", p, size * 0.3f, size * 1.6f, life, WHITE, c).envelope(0.05f, 0.3f, 1f);
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "shockwave");
            ringFlat(c, n, r0 * 0.8, r1 * 0.9, Math.max(4, life - 2), mix(col2, WHITE, 0.5f), "ring");
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            add("streak", p, size * 1.8f, size * 0.3f, 8, WHITE, tone(col)).vel(gauss() * 0.05, gauss() * 0.05, gauss() * 0.05).axial().grav(0.3f).drag(0.94f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            add("crack", p.add(0, 0.04, 0), radius, radius * 1.05f, life + 30, 0xFFFFFF, 0xFFFFFF).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.7f, 1f);
            add("crackglow", p.add(0, 0.06, 0), radius, radius * 1.05f, life, WHITE, mix(tone(col), 0x102040, 0.4f)).facing(0, 1, 0).envelope(0.05f, 0.3f, 0.9f);
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(2); k++) {
                double a = rnd() * 6.28, r = radius * (0.8 + 0.2 * rnd());
                add("shard", base.add(Math.cos(a) * r, rnd() * h, Math.sin(a) * r), 0.3f, 0.12f, 12, lighten(c, 0.7f), c).vel(0, 0.12, 0).spin((float) (gauss() * 0.15)).envelope(0.1f, 0.6f, 1f);
            }
            for (double y = 0; y < h; y += 1.0) sp("glow", base.x, base.y + y, base.z).size((float) radius * 0.9f + 0.1f, (float) radius * 0.4f).life(3).colors(WHITE, lighten(c, 0.5f));
            if (age % 3 == 0) ringFlat(base.add(0, rnd() * h, 0), new Vec3(0, 1, 0), radius * 0.4, radius * 1.4, 8, c, "ring");
        }
    }

    /** Blood: red drops, bats, crimson mist, splatter. */
    static final class Vampire extends Skin {
        Vampire(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "bat";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            double roll = rnd();
            if (roll < 0.72) {
                add("droplet", p, size * 1.7f, size * 1.1f, life + 10, mix(c, 0xFF8080, 0.2f), mix(c, 0x5A0000, 0.5f)).vel(v).grav(0.6f).drag(0.985f).envelope(0.03f, 0.75f, 1f).bright();
            } else if (roll < 0.9) {
                FxParticle b = add("bat", p, size * 2.6f, size * 2.6f, life + 20, mix(c, 0x100010, 0.85f), mix(c, 0x000000, 0.9f)).vel(v.scale(0.6).add(0, 0.02, 0)).drag(0.95f)
                        .envelope(0.1f, 0.65f, 1f).bright();
                float phase = (float) (rnd() * 6.28);
                b.motion((pp, age) -> pp.addVel(Math.sin(age * 0.6 + phase) * 0.02, Math.cos(age * 0.5 + phase) * 0.012, Math.cos(age * 0.6 + phase) * 0.02));
            } else {
                add("glow", p, size * 1.5f, size * 0.3f, life + 6, lighten(c, 0.6f), c).vel(v.scale(0.5)).drag(0.93f).flicker(0.4f).envelope(0.05f, 0.5f, 0.9f);
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            int c = tone(col);
            add("smoke", p, size * 0.6f, size * 1.7f, life + 6, mix(c, 0x300008, 0.5f), mix(accent2, 0x000000, 0.7f)).vel(v.scale(0.6)).envelope(0.12f, 0.45f, 0.65f * strength).spin((float) (gauss() * 0.03));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size * 0.9f, life, c);
            add("rays", p, size * 0.5f, size * 1.6f, life, lighten(c, 0.5f), c).startRoll((float) (rnd() * 6.28)).spin(-0.03f).envelope(0.05f, 0.3f, 0.9f);
            for (int k = 0; k < n(6); k++) mote(p, rndUp().scale(0.16 + rnd() * 0.12), size * 0.3f, 18, c, 0.5f);
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "ring");
            if (r1 >= 2) add("sigil", c, (float) (r0 / 0.94), (float) (r1 / 0.94), life, lighten(col2, 0.3f), col2).facing(n.x, n.y, n.z).spin(-0.025f).envelope(0.1f, 0.5f, 0.75f);
            Vec3[] uv = axes(n);
            for (int k = 0; k < n(6); k++) {
                double a = rnd() * 6.28;
                mote(onRing(c, uv, r0, a), radial(uv, a).scale((r1 - r0) / life * 0.8).add(0, 0.05, 0), 0.16f, life, col2, 0.4f);
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            add("droplet", p, size, size * 0.7f, 16, mix(tone(col), 0xFF8080, 0.2f), mix(tone(col), 0x5A0000, 0.5f)).vel(gauss() * 0.02, 0.0, gauss() * 0.02).grav(0.6f).envelope(0.03f, 0.75f, 1f).bright();
            if (rnd() < 0.3) body(p, new Vec3(0, 0.01, 0), size * 1.3f, 12, col, 0.5f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            add("splat", p.add(0, 0.04, 0), radius, radius * 1.05f, life + 40, mix(tone(col), 0x300000, 0.35f), mix(tone(col), 0x200000, 0.55f)).facing(0, 1, 0).startRoll((float) (rnd() * 6.28)).envelope(0.05f, 0.75f, 0.95f).bright();
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            for (int k = 0; k < n(2); k++) {
                double t = rnd(), a = age * 0.4 + t * 8 + k * 3.1;
                FxParticle b = add("bat", base.add(Math.cos(a) * radius * 0.9, t * h, Math.sin(a) * radius * 0.9), 0.5f, 0.5f, 16, mix(c, 0x100010, 0.85f), 0x000000).vel(-Math.sin(a) * 0.1, 0.05, Math.cos(a) * 0.1)
                        .envelope(0.1f, 0.6f, 1f).bright();
            }
            body(base.add(gauss() * radius * 0.5, rnd() * h, gauss() * radius * 0.5), new Vec3(0, 0.03, 0), (float) radius + 0.5f, 10, c, 0.6f);
            if (age % 2 == 0) mote(base.add(gauss() * radius * 0.5, h * (0.5 + rnd() * 0.5), gauss() * radius * 0.5), new Vec3(gauss() * 0.02, 0, gauss() * 0.02), 0.16f, 20, c, 0.6f);
        }
    }

    /** Air: pointed wind lines, spinning discs, white puffs, tornados. */
    static final class Wind extends Skin {
        Wind(int a, int b) {
            super(a, b);
        }

        @Override
        String orbSprite() {
            return "swirl";
        }

        @Override
        void mote(Vec3 p, Vec3 v, float size, int life, int col, float gravity) {
            int c = tone(col);
            if (rnd() < 0.8) {
                add("streak", p, size * 3.6f, size * 0.8f, life, WHITE, mix(c, WHITE, 0.4f)).vel(v).axial().drag(0.96f).envelope(0.03f, 0.5f, 0.95f);
            } else {
                add("swirl", p, size * 1.8f, size * 3.2f, life + 4, WHITE, c).vel(v.scale(0.5)).drag(0.94f).spin((float) (0.2 + rnd() * 0.15)).envelope(0.1f, 0.4f, 0.7f);
            }
        }

        @Override
        void body(Vec3 p, Vec3 v, float size, int life, int col, float strength) {
            add("smoke", p, size * 0.5f, size * 1.5f, life + 4, 0xFFFFFF, mix(tone(col), WHITE, 0.5f)).vel(v).envelope(0.2f, 0.45f, 0.3f * strength).spin((float) (gauss() * 0.04));
        }

        @Override
        void flash(Vec3 p, float size, int life, int col) {
            int c = tone(col);
            bloom(p, size * 0.8f, life, c);
            add("swirl", p, size * 0.5f, size * 2.2f, life + 4, WHITE, c).spin(0.25f).envelope(0.1f, 0.4f, 0.85f);
        }

        @Override
        void ring(Vec3 c, Vec3 n, double r0, double r1, int life, int col) {
            int col2 = tone(col);
            ringFlat(c, n, r0, r1, life, col2, "ring");
            add("swirl", c, (float) (r0 / 0.95), (float) (r1 / 0.95), life + 2, WHITE, col2).facing(n.x, n.y, n.z).spin(0.18f).envelope(0.08f, 0.4f, 0.8f);
            Vec3[] uv = axes(n);
            int count = n((int) Math.max(6, r1 * 2));
            for (int k = 0; k < count; k++) {
                double a = Math.PI * 2 * k / count;
                Vec3 out = radial(uv, a);
                add("streak", onRing(c, uv, r0, a), 0.7f, 0.3f, life, WHITE, col2).vel(out.scale((r1 - r0) / life * 1.1)).axial().drag(1f).envelope(0.05f, 0.4f, 0.8f);
            }
        }

        @Override
        void along(Vec3 p, Vec3 dir, float size, int col) {
            add("streak", p, size * 2.6f, size * 0.4f, 9, WHITE, mix(tone(col), WHITE, 0.4f)).vel(dir.scale(0.001)).axial().drag(1f).envelope(0.05f, 0.4f, 0.7f);
            if (rnd() < 0.3) body(p, new Vec3(gauss() * 0.01, 0.01, gauss() * 0.01), size * 1.2f, 12, col, 0.5f);
        }

        @Override
        void ground(Vec3 p, float radius, int col, int life) {
            add("swirl", p.add(0, 0.05, 0), radius, radius, life, WHITE, tone(col)).facing(0, 1, 0).spin(0.12f).envelope(0.15f, 0.5f, 0.6f);
            for (int k = 0; k < n(6); k++) {
                double a = rnd() * 6.28, r = rnd() * radius;
                body(p.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), new Vec3(-Math.sin(a) * 0.06, 0.02, Math.cos(a) * 0.06), 0.9f, life / 2, col, 0.5f);
            }
        }

        @Override
        void column(Vec3 base, double radius, double h, int col, int age) {
            int c = tone(col);
            // A spinning column: streaks going round, bigger toward the top
            for (int k = 0; k < n(4); k++) {
                double t = rnd(), a = age * 0.6 + t * 11 + k * 1.7;
                double r = radius * (0.5 + 0.9 * t);
                Vec3 pos = base.add(Math.cos(a) * r, t * h, Math.sin(a) * r);
                add("streak", pos, 1.0f, 0.5f, 6, WHITE, mix(c, WHITE, 0.4f)).vel(-Math.sin(a) * 0.5, 0.05, Math.cos(a) * 0.5).axial().drag(1f).envelope(0.1f, 0.5f, 0.8f);
            }
            body(base.add(Math.cos(age * 0.5) * radius, rnd() * h, Math.sin(age * 0.5) * radius), new Vec3(0, 0.03, 0), (float) (radius * 0.8 + 0.4), 12, c, 0.5f);
        }
    }
}
