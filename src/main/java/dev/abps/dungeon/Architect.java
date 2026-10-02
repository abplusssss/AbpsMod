package dev.abps.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Random;

/**
 * Builds the architecture of dungeon rooms and corridors, so they read as halls rather than boxes.
 *
 * Big fight rooms (boss, arena, miniboss) are round domed halls: concentric rings in the floor, a ring of columns, a
 * gallery running round the walls with lanterns hanging under it, a ring of lanterns standing on glowing floor
 * stones, ribs up the dome to a lit oculus and a chandelier in the middle. Other rooms are octagons (rectangles with
 * the corners cut) under a barrel vault with ribs and lit coffers.
 *
 * Every wall is dressed the same way: a plinth along the floor, columns set into the wall, a lattice diamond with a
 * glowing heart between each pair of columns, and a carved cornice with a corbelled lip at the top.
 * Doors get a gateway: jambs that stand out from the wall, a lintel with an overhang and a lit transom above.
 *
 * The walkway between the two doors is always left clear.
 */
final class Architect {

    private Architect() {
    }

    // ------------------------------------------------------------------ materials

    /** The blocks one theme is built from, a richer set than the palette. */
    record Style(BlockState wall, BlockState wallAlt, BlockState lattice, BlockState floor, BlockState floorAlt, BlockState floorDark,
                 BlockState ring, BlockState pillar, BlockState fancy, BlockState trim, BlockState glow, BlockState lantern,
                 BlockState stairs, BlockState slab, BlockState rail, BlockState ceiling, BlockState fire) {
    }

    private static BlockState d(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState();
    }

    static Style style(MobKit.Theme theme) {
        return switch (theme) {
            case FROST -> new Style(d(Blocks.QUARTZ_BRICKS), d(Blocks.PACKED_ICE), d(Blocks.BLUE_ICE), d(Blocks.SMOOTH_QUARTZ), d(Blocks.CALCITE),
                    d(Blocks.PACKED_ICE), d(Blocks.BLUE_ICE), d(Blocks.QUARTZ_PILLAR), d(Blocks.CHISELED_QUARTZ_BLOCK), d(Blocks.QUARTZ_BLOCK),
                    d(Blocks.SEA_LANTERN), d(Blocks.SOUL_LANTERN), d(Blocks.QUARTZ_STAIRS), d(Blocks.SMOOTH_QUARTZ_SLAB), d(Blocks.IRON_BARS),
                    d(Blocks.PACKED_ICE), d(Blocks.SOUL_CAMPFIRE));
            case FORGE -> new Style(d(Blocks.NETHER_BRICKS), d(Blocks.CRACKED_NETHER_BRICKS), d(Blocks.RED_NETHER_BRICKS),
                    d(Blocks.POLISHED_BLACKSTONE_BRICKS), d(Blocks.BLACKSTONE), d(Blocks.POLISHED_BLACKSTONE), d(Blocks.GILDED_BLACKSTONE),
                    d(Blocks.POLISHED_BASALT), d(Blocks.CHISELED_POLISHED_BLACKSTONE), d(Blocks.CHISELED_NETHER_BRICKS), d(Blocks.SHROOMLIGHT),
                    d(Blocks.LANTERN), d(Blocks.NETHER_BRICK_STAIRS), d(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB), d(Blocks.NETHER_BRICK_WALL),
                    d(Blocks.BLACKSTONE), d(Blocks.CAMPFIRE));
            case DEPTHS -> new Style(d(Blocks.DEEPSLATE_BRICKS), d(Blocks.CRACKED_DEEPSLATE_BRICKS), d(Blocks.POLISHED_DEEPSLATE),
                    d(Blocks.DEEPSLATE_TILES), d(Blocks.POLISHED_DEEPSLATE), d(Blocks.COBBLED_DEEPSLATE), d(Blocks.SCULK), d(Blocks.POLISHED_DEEPSLATE),
                    d(Blocks.CHISELED_DEEPSLATE), d(Blocks.CHISELED_DEEPSLATE), d(Blocks.OCHRE_FROGLIGHT), d(Blocks.SOUL_LANTERN),
                    d(Blocks.DEEPSLATE_BRICK_STAIRS), d(Blocks.DEEPSLATE_TILE_SLAB), d(Blocks.DEEPSLATE_BRICK_WALL), d(Blocks.DEEPSLATE_TILES),
                    d(Blocks.SOUL_CAMPFIRE));
            default -> new Style(d(Blocks.STONE_BRICKS), d(Blocks.MOSSY_STONE_BRICKS), d(Blocks.DEEPSLATE_BRICKS), d(Blocks.DEEPSLATE_TILES),
                    d(Blocks.POLISHED_DEEPSLATE), d(Blocks.COBBLED_DEEPSLATE), d(Blocks.CHISELED_DEEPSLATE), d(Blocks.POLISHED_DEEPSLATE),
                    d(Blocks.CHISELED_STONE_BRICKS), d(Blocks.CHISELED_STONE_BRICKS), d(Blocks.VERDANT_FROGLIGHT), d(Blocks.SOUL_LANTERN),
                    d(Blocks.STONE_BRICK_STAIRS), d(Blocks.STONE_BRICK_SLAB), d(Blocks.STONE_BRICK_WALL), d(Blocks.DEEPSLATE_BRICKS),
                    d(Blocks.SOUL_CAMPFIRE));
        };
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> prop, T value) {
        return s.hasProperty(prop) ? s.setValue(prop, value) : s;
    }

    private static BlockState hanging(BlockState lantern) {
        return with(lantern, BlockStateProperties.HANGING, true);
    }

    private static BlockState standing(BlockState lantern) {
        return with(lantern, BlockStateProperties.HANGING, false);
    }

    /** An upside-down stair with its back against the wall in direction toWall: a corbel or cornice lip. */
    private static BlockState corbel(Style st, Direction toWall) {
        return with(with(st.stairs(), BlockStateProperties.HORIZONTAL_FACING, toWall), BlockStateProperties.HALF, Half.TOP);
    }

    /** A step that rises toward direction up. */
    private static BlockState step(Style st, Direction up) {
        return with(with(st.stairs(), BlockStateProperties.HORIZONTAL_FACING, up), BlockStateProperties.HALF, Half.BOTTOM);
    }

    private static BlockState topSlab(Style st) {
        return with(st.slab(), BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
    }

    private static BlockState lit(BlockState s) {
        return with(s, BlockStateProperties.LIT, true);
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // ------------------------------------------------------------------ shape

    /** Which columns of the room's box are open floor, and how high the ceiling is over each. */
    private static final class Shape {
        final int x0, z0, x1, z1, y0, mid, spring, rise;
        final boolean round, entry, exit;
        final double cx, cz, rx, rz;
        final int chamfer;

        Shape(Run.Room r, boolean round, boolean entry, boolean exit) {
            x0 = r.min.getX();
            z0 = r.min.getZ();
            x1 = r.x1();
            z1 = z0 + r.d - 1;
            y0 = r.floor();
            mid = r.midZ();
            this.round = round;
            this.entry = entry;
            this.exit = exit;
            cx = (x0 + x1) / 2.0;
            cz = (z0 + z1) / 2.0;
            rx = (x1 - x0) / 2.0 - 0.6;
            rz = (z1 - z0) / 2.0 - 0.6;
            chamfer = round || r.type == Run.RoomType.TRAP ? 0 : Math.max(2, Math.min(r.w, r.d) / 4);
            // The walls stand to the old ceiling height; the vault or dome rises above that
            spring = y0 + r.h - 1;
            rise = round ? Math.max(3, (int) Math.round(Math.min(rx, rz) * 0.5)) : Math.max(2, Math.min(5, r.d / 4));
        }

        boolean door(int x, int z) {
            return Math.abs(z - mid) <= 1 && ((entry && x > x0 && x <= x0 + 3) || (exit && x < x1 && x >= x1 - 3));
        }

        boolean in(int x, int z) {
            if (x <= x0 || x >= x1 || z <= z0 || z >= z1) return false;
            if (door(x, z)) return true;
            if (round) {
                double dx = (x - cx) / rx, dz = (z - cz) / rz;
                return dx * dx + dz * dz <= 1.0;
            }
            int ix = x - x0, iz = z - z0, jx = x1 - x, jz = z1 - z;
            int c = chamfer;
            return c == 0 || !(ix + iz <= c || jx + iz <= c || ix + jz <= c || jx + jz <= c);
        }

        /** 0 at the middle of the room to 1 at the wall. */
        double norm(int x, int z) {
            if (round) {
                double dx = (x - cx) / rx, dz = (z - cz) / rz;
                return Math.sqrt(dx * dx + dz * dz);
            }
            return Math.abs(z - mid) / Math.max(1.0, (z1 - z0) / 2.0 - 1);
        }

        /** The highest open block over a column. */
        int top(int x, int z) {
            double n = Math.min(1, norm(x, z));
            return spring - 1 + (int) Math.round(rise * Math.sqrt(Math.max(0, 1 - n * n)));
        }

        int maxTop() {
            return spring - 1 + rise;
        }

        boolean path(int x, int z) {
            if (Math.abs(z - mid) <= 2) return true;
            return Math.abs(z - mid) <= 3 && (x <= x0 + 4 || x >= x1 - 4);
        }

        /** Direction from an open column to the wall next to it, or null if it isn't against a wall. */
        Direction wallSide(int x, int z) {
            if (!in(x, z)) return null;
            if (!in(x, z - 1)) return Direction.NORTH;
            if (!in(x, z + 1)) return Direction.SOUTH;
            if (!in(x - 1, z)) return Direction.WEST;
            if (!in(x + 1, z)) return Direction.EAST;
            return null;
        }
    }

    // ------------------------------------------------------------------ rooms

    static void room(Builder b, Run.Room r, DungeonDef.Palette pal, Random rnd, boolean entry, boolean exit, boolean lockExit) {
        MobKit.Theme theme = MobKit.themeOf(pal);
        Style st = style(theme);
        boolean round = r.type == Run.RoomType.BOSS || r.type == Run.RoomType.ARENA || r.type == Run.RoomType.MINIBOSS;
        Shape sh = new Shape(r, round, entry, exit);

        shell(b, sh, st, rnd);
        walls(b, sh, st, rnd);
        floor(b, sh, st, r, rnd);
        ceiling(b, sh, st, rnd);
        if (entry) gate(b, sh, st, sh.x0, 1, r.h >= 9);
        if (exit) gate(b, sh, st, sh.x1, -1, r.h >= 9);

        if (round) {
            columns(b, sh, st, r);
            if (Math.min(sh.rx, sh.rz) >= 11) gallery(b, sh, st);
            lanternRing(b, sh, st);
            chandelier(b, sh, st, (int) sh.cx, (int) sh.cz, Math.min(5, sh.rise + 2));
        } else {
            nave(b, sh, st, r, rnd);
        }
        props(b, sh, st, theme, r, rnd);

        switch (r.type) {
            case BOSS -> throne(b, sh, st);
            case START -> {
                b.set(sh.x0 + 2, sh.y0 + 1, sh.mid - 3, lit(st.fire()));
                b.set(sh.x0 + 2, sh.y0 + 1, sh.mid + 3, lit(st.fire()));
            }
            default -> {
            }
        }

        // Door holes last, so nothing is built across them
        if (entry) b.fill(sh.x0, sh.y0 + 1, sh.mid - 1, sh.x0, sh.y0 + 3, sh.mid + 1, AIR);
        if (exit) b.fill(sh.x1, sh.y0 + 1, sh.mid - 1, sh.x1, sh.y0 + 3, sh.mid + 1, lockExit ? Builder.BARS : AIR);
    }

    /** Solid walls round the open shape, open air inside it, and a two block thick ceiling following the vault. */
    private static void shell(Builder b, Shape sh, Style st, Random rnd) {
        int wallTop = sh.maxTop() + 2;
        for (int x = sh.x0; x <= sh.x1; x++) {
            for (int z = sh.z0; z <= sh.z1; z++) {
                if (sh.in(x, z)) {
                    int top = sh.top(x, z);
                    b.fill(x, sh.y0 + 1, z, x, top, z, AIR);
                    b.set(x, top + 1, z, st.ceiling());
                    b.set(x, top + 2, z, st.ceiling());
                    continue;
                }
                if (!near(sh, x, z, 2)) continue;
                b.set(x, sh.y0, z, st.floorDark());
                for (int y = sh.y0 + 1; y <= wallTop; y++) b.set(x, y, z, rnd.nextInt(5) == 0 ? st.wallAlt() : st.wall());
            }
        }
    }

    private static boolean near(Shape sh, int x, int z, int dist) {
        for (int dx = -dist; dx <= dist; dx++)
            for (int dz = -dist; dz <= dist; dz++) if (sh.in(x + dx, z + dz)) return true;
        return false;
    }

    /** Dresses every block of wall that faces into the room. */
    private static void walls(Builder b, Shape sh, Style st, Random rnd) {
        for (int x = sh.x0; x <= sh.x1; x++) {
            for (int z = sh.z0; z <= sh.z1; z++) {
                if (sh.in(x, z)) continue;
                // The open neighbour this wall block faces, and how high it is open
                int faceTop = -1;
                boolean alongX = false;
                int[][] n = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
                for (int[] o : n) {
                    if (sh.in(x + o[0], z + o[1])) {
                        faceTop = Math.max(faceTop, sh.top(x + o[0], z + o[1]));
                        if (o[0] == 0) alongX = true;
                    }
                }
                if (faceTop < 0) continue;
                int u;
                if (sh.round) {
                    double a = Math.atan2(z - sh.cz, x - sh.cx);
                    u = (int) Math.round(a * Math.min(sh.rx, sh.rz));
                } else {
                    u = alongX ? x - sh.x0 : z - sh.z0;
                }
                for (int y = sh.y0 + 1; y <= faceTop + 1; y++) b.set(x, y, z, face(sh, st, rnd, u, y));
            }
        }
        // A corbelled lip under the cornice on straight walls
        if (!sh.round) {
            int y = sh.spring - 1;
            if (y <= sh.y0 + 3) return;
            for (int x = sh.x0 + 1; x < sh.x1; x++) {
                for (int z = sh.z0 + 1; z < sh.z1; z++) {
                    Direction side = sh.wallSide(x, z);
                    if (side == null || sh.door(x, z) || sh.top(x, z) < y) continue;
                    b.set(x, y, z, corbel(st, side));
                }
            }
        }
    }

    /** What one block of wall face is, by its height and how far along the wall it is. */
    private static BlockState face(Shape sh, Style st, Random rnd, int u, int y) {
        int ry = y - sh.y0;
        if (ry == 1) return st.trim();
        if (y == sh.spring - 1 && ry > 3) return st.fancy();
        if (y >= sh.spring) return rnd.nextInt(6) == 0 ? st.wallAlt() : st.wall();
        int m = Math.floorMod(u, 4);
        if (m == 0) return ry == 2 ? st.fancy() : st.pillar();
        // Between each pair of columns, a lattice diamond with a glowing heart, stacked up tall walls
        int k = Math.floorMod(ry - 2, 4);
        if (m == 2 && k == 2) return st.glow();
        if ((m == 2 && (k == 1 || k == 3)) || ((m == 1 || m == 3) && k == 2)) return st.lattice();
        return rnd.nextInt(7) == 0 ? st.wallAlt() : st.wall();
    }

    /** Floor patterns: concentric rings in round halls, a bordered checkerboard with a runner between the doors elsewhere. */
    private static void floor(Builder b, Shape sh, Style st, Run.Room r, Random rnd) {
        for (int x = sh.x0 + 1; x < sh.x1; x++) {
            for (int z = sh.z0 + 1; z < sh.z1; z++) {
                if (!sh.in(x, z)) continue;
                BlockState f;
                if (sh.round) {
                    double dist = Math.hypot(x - sh.cx, z - sh.cz), rr = Math.min(sh.rx, sh.rz);
                    int band = (int) Math.floor(dist);
                    if (dist < 1.6) f = st.glow();
                    else if (dist < 3.2) f = st.fancy();
                    else if (band == (int) (rr * 0.45)) f = st.ring();
                    else if (dist > rr - 1.5) f = st.floorDark();
                    else if (dist > rr - 2.8) f = st.trim();
                    else if (band % 3 == 0) f = st.floorDark();
                    else f = ((x + z) & 1) == 0 ? st.floor() : st.floorAlt();
                } else {
                    boolean edge = !sh.in(x - 1, z) || !sh.in(x + 1, z) || !sh.in(x, z - 1) || !sh.in(x, z + 1);
                    int dz = Math.abs(z - sh.mid);
                    if (edge) f = st.floorDark();
                    else if (!near1(sh, x, z, 1)) f = st.trim();
                    else if (dz == 2) f = st.floorDark();
                    else if (dz <= 1) f = (x & 1) == 0 ? st.ring() : st.floorDark();
                    else f = (((x >> 1) + (z >> 1)) & 1) == 0 ? st.floor() : st.floorAlt();
                    if (rnd.nextInt(14) == 0 && dz > 2 && !edge) f = st.floorAlt();
                }
                b.set(x, sh.y0, z, f);
            }
        }
        if (!sh.round && r.type != Run.RoomType.TRAP) {
            // A diamond medallion either side of the runner
            for (int side = -1; side <= 1; side += 2) {
                int mz = sh.mid + side * Math.max(4, (sh.z1 - sh.z0) / 4 + 1), mx = (sh.x0 + sh.x1) / 2;
                if (!sh.in(mx, mz + side * 2)) continue;
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        int m = Math.abs(dx) + Math.abs(dz);
                        if (m == 2) b.set(mx + dx, sh.y0, mz + dz, st.ring());
                        if (m == 1) b.set(mx + dx, sh.y0, mz + dz, st.floorDark());
                    }
                b.set(mx, sh.y0, mz, st.glow());
            }
        }
    }

    /** True if every column within dist is open (so this one is not in the border band). */
    private static boolean near1(Shape sh, int x, int z, int dist) {
        for (int dx = -dist; dx <= dist; dx++)
            for (int dz = -dist; dz <= dist; dz++) if (!sh.in(x + dx, z + dz)) return false;
        return true;
    }

    /** Ribs across the vault or up the dome, and lit coffers or an oculus between them. */
    private static void ceiling(Builder b, Shape sh, Style st, Random rnd) {
        for (int x = sh.x0 + 1; x < sh.x1; x++) {
            for (int z = sh.z0 + 1; z < sh.z1; z++) {
                if (!sh.in(x, z)) continue;
                int top = sh.top(x, z);
                if (sh.round) {
                    double dx = x - sh.cx, dz = z - sh.cz, dist = Math.hypot(dx, dz);
                    double a = Math.toDegrees(Math.atan2(dz, dx));
                    boolean spoke = Math.abs(((a % 45) + 45) % 45 - 22.5) > 19.5 && dist > 2.5;
                    boolean ringRib = Math.abs(dist - Math.min(sh.rx, sh.rz) * 0.55) < 0.6;
                    if (dist < 1.6) b.set(x, top + 1, z, st.glow());
                    else if (dist < 2.6) b.set(x, top + 1, z, st.fancy());
                    else if (spoke || ringRib) {
                        b.set(x, top + 1, z, st.pillar());
                        if (top > sh.spring) b.set(x, top, z, st.fancy());
                    }
                } else {
                    boolean rib = Math.floorMod(x - sh.x0, 4) == 0;
                    if (rib) {
                        b.set(x, top + 1, z, st.pillar());
                        if (top >= sh.spring) b.set(x, top, z, st.fancy());
                    } else if (Math.floorMod(x - sh.x0, 4) == 2 && Math.abs(z - sh.mid) <= 1) {
                        b.set(x, top + 1, z, z == sh.mid ? st.glow() : st.fancy());
                    }
                }
            }
        }
    }

    /**
     * A gateway on the inside of a door in the wall at x. dir points into the room. Jambs stand out from the wall,
     * a lintel spans the door with an overhang, and tall rooms get a glowing transom above.
     */
    private static void gate(Builder b, Shape sh, Style st, int x, int dir, boolean tall) {
        int y = sh.y0, m = sh.mid, xi = x + dir;
        Direction toWall = dir > 0 ? Direction.WEST : Direction.EAST;
        for (int side = -1; side <= 1; side += 2) {
            int z = m + side * 2;
            // Jamb in the wall plane and a column in front of it
            b.fill(x, y + 1, z, x, y + 5, z, st.pillar());
            b.set(xi, y + 1, z, st.fancy());
            b.fill(xi, y + 2, z, xi, y + 4, z, st.pillar());
            b.set(xi, y + 5, z, st.fancy());
            if (sh.top(xi, z) >= y + 6) b.set(xi, y + 6, z, standing(st.lantern()));
            b.set(x, y + 1, m + side * 3, st.fancy());
        }
        // Lintel and overhang
        b.fill(x, y + 4, m - 1, x, y + 4, m + 1, st.fancy());
        b.fill(x, y + 5, m - 1, x, y + 5, m + 1, st.trim());
        for (int z = m - 1; z <= m + 1; z++) b.set(xi, y + 5, z, corbel(st, toWall));
        if (tall && sh.spring - 1 > y + 8) {
            b.set(x, y + 6, m, st.glow());
            b.set(x, y + 6, m - 1, st.fancy());
            b.set(x, y + 6, m + 1, st.fancy());
            b.set(x, y + 7, m, st.fancy());
            b.set(x, y + 7, m - 1, st.glow());
            b.set(x, y + 7, m + 1, st.glow());
            b.set(x, y + 8, m, st.glow());
        }
    }

    /** A ring of columns in a round hall, clear of the doors. */
    private static void columns(Builder b, Shape sh, Style st, Run.Room r) {
        double rr = Math.min(sh.rx, sh.rz) - 3.2;
        int n = rr > 10 ? 12 : 8;
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * (k + 0.5) / n;
            int x = (int) Math.round(sh.cx + Math.cos(a) * rr), z = (int) Math.round(sh.cz + Math.sin(a) * rr);
            if (sh.path(x, z) || !sh.in(x, z)) continue;
            int top = sh.top(x, z);
            b.set(x, sh.y0 + 1, z, st.fancy());
            b.fill(x, sh.y0 + 2, z, x, top - 1, z, st.pillar());
            b.set(x, top, z, st.fancy());
            // A glowing band halfway up and lanterns hung from the capital
            b.set(x, sh.y0 + 4, z, st.glow());
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (sh.in(x + o[0], z + o[1]) && !sh.path(x + o[0], z + o[1])) {
                    b.set(x + o[0], top, z + o[1], corbel(st, o[0] == 1 ? Direction.WEST : o[0] == -1 ? Direction.EAST : o[1] == 1 ? Direction.NORTH : Direction.SOUTH));
                }
            }
            b.set(x + (sh.cx > x ? 1 : -1), top - 1, z, hanging(st.lantern()));
        }
    }

    /** A walkway round the top of the walls with a railing, and lanterns hanging under its edge. */
    private static void gallery(Builder b, Shape sh, Style st) {
        int gy = sh.y0 + 6;
        if (gy + 2 >= sh.spring) return;
        double rr = Math.min(sh.rx, sh.rz);
        for (int x = sh.x0 + 1; x < sh.x1; x++) {
            for (int z = sh.z0 + 1; z < sh.z1; z++) {
                if (!sh.in(x, z) || sh.door(x, z)) continue;
                double dist = Math.hypot(x - sh.cx, z - sh.cz);
                // Keep clear of the two gateways
                double a = Math.abs(Math.toDegrees(Math.atan2(z - sh.cz, x - sh.cx)));
                if (a < 26 || a > 154) continue;
                if (dist >= rr - 2.2) {
                    b.set(x, gy, z, topSlab(st));
                    if (dist < rr - 1.2) {
                        b.set(x, gy + 1, z, st.rail());
                        if (((x + z) & 3) == 0) b.set(x, gy - 1, z, hanging(st.lantern()));
                    }
                }
            }
        }
    }

    /** Lanterns standing on glowing stones in a ring on the floor. */
    private static void lanternRing(Builder b, Shape sh, Style st) {
        double big = Math.min(sh.rx, sh.rz);
        double rr = big >= 11 ? big * 0.62 : big - 1.8;
        int n = rr > 8 ? 16 : 10;
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * k / n;
            int x = (int) Math.round(sh.cx + Math.cos(a) * rr), z = (int) Math.round(sh.cz + Math.sin(a) * rr);
            if (sh.path(x, z) || !sh.in(x, z)) continue;
            b.set(x, sh.y0, z, st.glow());
            b.set(x, sh.y0 + 1, z, standing(st.lantern()));
        }
    }

    /** A chandelier hanging from the ceiling at (x, z): a stem, a glowing hub, four arms and a lantern under each. */
    private static void chandelier(Builder b, Shape sh, Style st, int x, int z, int drop) {
        int top = sh.top(x, z);
        int hub = top - drop;
        if (hub <= sh.y0 + 5) return;
        b.fill(x, hub + 1, z, x, top, z, st.rail());
        b.set(x, hub, z, st.glow());
        b.set(x, hub - 1, z, hanging(st.lantern()));
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            b.set(x + o[0], hub, z + o[1], topSlab(st));
            b.set(x + o[0] * 2, hub, z + o[1] * 2, st.fancy());
            b.set(x + o[0] * 2, hub - 1, z + o[1] * 2, hanging(st.lantern()));
        }
    }

    /** Rectangular rooms: a colonnade or braziers for fights, chandeliers down the middle of the vault. */
    private static void nave(Builder b, Shape sh, Style st, Run.Room r, Random rnd) {
        int len = sh.x1 - sh.x0;
        // Chandeliers along the vault, between the ribs
        if (sh.z1 - sh.z0 >= 12 && r.type != Run.RoomType.TRAP) {
            for (int x = sh.x0 + 6; x <= sh.x1 - 6; x += 8) chandelier(b, sh, st, x, sh.mid, 3);
        } else {
            for (int x = sh.x0 + 2; x < sh.x1 - 1; x += 4) {
                int top = sh.top(x, sh.mid);
                b.set(x, top, sh.mid, hanging(st.lantern()));
            }
        }
        if (r.type == Run.RoomType.COMBAT || r.type == Run.RoomType.ELITE) {
            int off = Math.max(4, (sh.z1 - sh.z0) / 4 + 1);
            if (rnd.nextBoolean()) {
                // A colonnade: two rows of columns under the ribs
                for (int x = sh.x0 + 4; x <= sh.x1 - 4; x += 4) {
                    for (int side = -1; side <= 1; side += 2) {
                        int z = sh.mid + side * off;
                        if (!sh.in(x, z) || sh.path(x, z)) continue;
                        int top = sh.top(x, z);
                        b.set(x, sh.y0 + 1, z, st.fancy());
                        b.fill(x, sh.y0 + 2, z, x, top - 1, z, st.pillar());
                        b.set(x, top, z, st.fancy());
                        b.set(x, sh.y0 + 3, z, st.glow());
                    }
                }
            } else {
                // Braziers on raised plinths either side of the runner
                for (int x = sh.x0 + 4; x <= sh.x1 - 4; x += Math.max(4, len / 3)) {
                    for (int side = -1; side <= 1; side += 2) {
                        int z = sh.mid + side * off;
                        if (!sh.in(x, z) || sh.path(x, z)) continue;
                        b.set(x, sh.y0 + 1, z, st.fancy());
                        b.set(x, sh.y0 + 2, z, lit(st.fire()));
                        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            if (sh.in(x + o[0], z + o[1]) && !sh.path(x + o[0], z + o[1]))
                                b.set(x + o[0], sh.y0 + 1, z + o[1], step(st, o[0] == 1 ? Direction.WEST : o[0] == -1 ? Direction.EAST : o[1] == 1 ? Direction.NORTH : Direction.SOUTH));
                        }
                    }
                }
            }
        }
    }

    /** Things standing against the walls and on the floor, by theme. */
    private static void props(Builder b, Shape sh, Style st, MobKit.Theme theme, Run.Room r, Random rnd) {
        boolean clearFloor = r.type == Run.RoomType.TRAP || r.type == Run.RoomType.SHRINE || r.type == Run.RoomType.ARENA;
        for (int x = sh.x0 + 1; x < sh.x1; x++) {
            for (int z = sh.z0 + 1; z < sh.z1; z++) {
                if (!sh.in(x, z) || sh.path(x, z) || sh.door(x, z)) continue;
                Direction side = sh.wallSide(x, z);
                if (side != null) {
                    if (r.type == Run.RoomType.TRAP) continue;
                    int u = side.getAxis() == Direction.Axis.Z ? x - sh.x0 : z - sh.z0;
                    if (!sh.round && Math.floorMod(u, 4) == 0) continue; // in front of a column
                    if (rnd.nextInt(r.type == Run.RoomType.TREASURE ? 2 : 4) == 0) {
                        if (r.type == Run.RoomType.TREASURE) b.set(x, sh.y0 + 1, z, treasure(rnd));
                        else Decor.wallProp(b, theme, rnd, x, sh.y0 + 1, z);
                    }
                    // Cobwebs high in the crypt and the depths
                    if ((theme == MobKit.Theme.CRYPT || theme == MobKit.Theme.DEPTHS) && rnd.nextInt(10) == 0) {
                        b.set(x, sh.top(x, z), z, Blocks.COBWEB.defaultBlockState());
                    }
                } else if (!clearFloor && rnd.nextInt(60) == 0 && near1(sh, x, z, 2)) {
                    Decor.floorPiece(b, theme, null, rnd, x, sh.y0 + 1, z, sh.spring - sh.y0);
                }
            }
        }
    }

    private static BlockState treasure(Random rnd) {
        return switch (rnd.nextInt(4)) {
            case 0 -> Blocks.GOLD_BLOCK.defaultBlockState();
            case 1 -> Blocks.DECORATED_POT.defaultBlockState();
            case 2 -> Blocks.BARREL.defaultBlockState();
            default -> Blocks.RAW_GOLD_BLOCK.defaultBlockState();
        };
    }

    /** Where the boss waits: a stepped dais, a carved seat with a tall back, a glowing crown and braziers. */
    private static void throne(Builder b, Shape sh, Style st) {
        int x = sh.x1 - 3, y = sh.y0, m = sh.mid;
        for (int z = m - 2; z <= m + 2; z++) {
            b.set(x - 2, y + 1, z, step(st, Direction.EAST));
            b.set(x - 1, y + 1, z, st.trim());
            b.set(x, y + 1, z, st.trim());
            b.set(x + 1, y + 1, z, st.trim());
        }
        b.set(x - 1, y + 2, m, step(st, Direction.EAST));
        b.set(x, y + 2, m, st.fancy());
        b.fill(x + 1, y + 2, m, x + 1, y + 6, m, st.pillar());
        b.set(x + 1, y + 7, m, st.glow());
        b.set(x + 1, y + 6, m - 1, st.fancy());
        b.set(x + 1, y + 6, m + 1, st.fancy());
        b.set(x + 1, y + 7, m - 1, st.glow());
        b.set(x + 1, y + 7, m + 1, st.glow());
        b.set(x + 1, y + 8, m, st.fancy());
        b.set(x, y + 2, m - 1, st.fancy());
        b.set(x, y + 2, m + 1, st.fancy());
        for (int side = -1; side <= 1; side += 2) {
            b.set(x - 1, y + 2, m + side * 3, st.fancy());
            b.set(x - 1, y + 3, m + side * 3, lit(st.fire()));
        }
    }

    // ------------------------------------------------------------------ corridors

    /**
     * A corridor running east from (x, y, z) for len blocks: five wide under a pointed vault, with a rib every other
     * block, glowing windows between the ribs, a runner down the middle and small props in the side bays.
     */
    static void corridor(Builder b, int x, int y, int z, int len, DungeonDef.Palette pal, Random rnd) {
        MobKit.Theme theme = MobKit.themeOf(pal);
        Style st = style(theme);
        for (int i = 0; i < len; i++) {
            int cx = x + i;
            boolean rib = i % 2 == 0;
            // Solid block first, then carve the passage out of it
            b.fill(cx, y, z - 3, cx, y + 8, z + 3, rib ? st.pillar() : st.wall());
            b.fill(cx, y + 1, z - 2, cx, y + 4, z + 2, AIR);
            b.fill(cx, y + 5, z - 1, cx, y + 5, z + 1, AIR);
            b.set(cx, y + 6, z, AIR);
            // Floor: dark edges, a patterned runner
            b.set(cx, y, z - 2, st.floorDark());
            b.set(cx, y, z + 2, st.floorDark());
            b.set(cx, y, z - 1, st.trim());
            b.set(cx, y, z + 1, st.trim());
            b.set(cx, y, z, (i & 1) == 0 ? st.ring() : st.floorDark());
            if (rib) {
                // The rib: a carved band round the arch
                b.set(cx, y + 1, z - 3, st.fancy());
                b.set(cx, y + 1, z + 3, st.fancy());
                b.set(cx, y + 5, z - 2, st.fancy());
                b.set(cx, y + 5, z + 2, st.fancy());
                b.set(cx, y + 6, z - 1, st.fancy());
                b.set(cx, y + 6, z + 1, st.fancy());
                b.set(cx, y + 7, z, st.fancy());
            } else {
                b.set(cx, y + 1, z - 3, st.trim());
                b.set(cx, y + 1, z + 3, st.trim());
                b.fill(cx, y + 2, z - 3, cx, y + 4, z - 3, st.lattice());
                b.fill(cx, y + 2, z + 3, cx, y + 4, z + 3, st.lattice());
                b.set(cx, y + 3, z - 3, st.glow());
                b.set(cx, y + 3, z + 3, st.glow());
                b.set(cx, y + 7, z, st.glow());
                if (rnd.nextBoolean()) Decor.wallProp(b, theme, rnd, cx, y + 1, z + (rnd.nextBoolean() ? 2 : -2));
            }
        }
        b.set(x + len / 2, y + 6, z, hanging(st.lantern()));
    }
}
