package dev.abps.dungeon;

import com.mojang.math.Transformation;
import dev.abps.AbpsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives a dungeon monster a body of its own. The real mob is made invisible and only provides the hitbox and the AI;
 * its body is a set of block displays riding it, posed every couple of ticks: legs and arms swing as it walks, the
 * head turns to look where the mob looks, ghosts bob in the air, spiders scuttle, sprites' shards circle them, capes
 * flare out behind. Hits flash the body red, and when the mob dies the body topples over and crumbles.
 *
 * Bodies are written as small text specs (see {@link RigModels}); tools/preview_rigs.py draws them from the same specs.
 */
public final class Rig {

    enum Bone { ROOT, BODY, HEAD, ARM_L, ARM_R, LEG_L, LEG_R, TAIL, CAPE, WING_L, WING_R, ORBIT, LEGS, QLEGS }

    record Part(BlockState state, Bone bone, Vector3f from, Vector3f size, Quaternionf rot, Vector3f offset, float phase, boolean bright) {
    }

    /** A body: boxes on bones, where each bone pivots, and how it moves. Sizes are in pixels (16 to a block). */
    static final class Model {
        final List<Part> parts = new ArrayList<>();
        final EnumMap<Bone, Vector3f> pivots = new EnumMap<>(Bone.class);
        float floatHeight = 0, bodyPitch = 0, armPitch = 0, swing = 0.7f, orbitSpeed = 1f, size = 1f;

        /** True if part of the body moves on its own (floating, circling, flapping, swaying). */
        boolean animated() {
            if (floatHeight > 0) return true;
            for (Part p : parts) {
                Bone b = p.bone();
                if (b == Bone.ORBIT || b == Bone.WING_L || b == Bone.WING_R || b == Bone.TAIL || b == Bone.CAPE) return true;
            }
            return false;
        }

        Vector3f pivot(Bone b) {
            return pivots.getOrDefault(b, new Vector3f());
        }

        /** Reads a spec, one line per command. Unknown lines are skipped with a warning, so a typo never crashes a dungeon. */
        static Model parse(String name, String... lines) {
            Model m = new Model();
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] t = line.split("\\s+");
                try {
                    switch (t[0]) {
                        case "pivot" -> m.pivots.put(Bone.valueOf(t[1]), new Vector3f(f(t[2]), f(t[3]), f(t[4])));
                        case "float" -> m.floatHeight = f(t[1]);
                        case "pitch" -> m.bodyPitch = (float) Math.toRadians(f(t[1]));
                        case "arms" -> m.armPitch = (float) Math.toRadians(f(t[1]));
                        case "swing" -> m.swing = f(t[1]);
                        case "orbit" -> m.orbitSpeed = f(t[1]);
                        case "size" -> m.size = f(t[1]);
                        case "box", "glow" -> {
                            Bone bone = Bone.valueOf(t[1]);
                            BlockState state = block(t[2]);
                            Vector3f from = new Vector3f(f(t[3]), f(t[4]), f(t[5]));
                            Vector3f size = new Vector3f(f(t[6]), f(t[7]), f(t[8]));
                            Quaternionf rot = new Quaternionf();
                            Vector3f offset = new Vector3f();
                            float phase = 0;
                            for (int i = 9; i < t.length; i++) {
                                if (t[i].startsWith("r=")) {
                                    String[] r = t[i].substring(2).split(",");
                                    rot = new Quaternionf().rotateY((float) Math.toRadians(f(r[1]))).rotateX((float) Math.toRadians(f(r[0])))
                                            .rotateZ((float) Math.toRadians(f(r[2])));
                                } else if (t[i].startsWith("o=")) {
                                    String[] o = t[i].substring(2).split(",");
                                    offset = new Vector3f(f(o[0]), f(o[1]), f(o[2]));
                                } else if (t[i].startsWith("p=")) {
                                    phase = (float) Math.toRadians(f(t[i].substring(2)));
                                }
                            }
                            m.parts.add(new Part(state, bone, from, size, rot, offset, phase, t[0].equals("glow")));
                        }
                        default -> AbpsMod.LOGGER.warn("Rig {}: unknown line '{}'", name, line);
                    }
                } catch (RuntimeException e) {
                    AbpsMod.LOGGER.warn("Rig {}: bad line '{}' ({})", name, line, e.toString());
                }
            }
            return m;
        }

        private static float f(String s) {
            return Float.parseFloat(s);
        }
    }

    /**
     * A block by name. "concrete:red" and "glass:cyan" pick colored blocks; "name[lit]" lights it; "name[south]" etc.
     * turns it. Anything not found falls back to stone with a warning.
     */
    static BlockState block(String spec) {
        String name = spec;
        String props = "";
        int br = spec.indexOf('[');
        if (br >= 0) {
            name = spec.substring(0, br);
            props = spec.substring(br + 1, spec.length() - 1);
        }
        BlockState s;
        if (name.startsWith("concrete:") || name.startsWith("glass:")) {
            boolean glass = name.startsWith("glass:");
            String c = name.substring(name.indexOf(':') + 1);
            var set = glass ? Blocks.STAINED_GLASS : Blocks.CONCRETE;
            Block b = switch (c) {
                case "white" -> set.white();
                case "orange" -> set.orange();
                case "magenta" -> set.magenta();
                case "light_blue" -> set.lightBlue();
                case "yellow" -> set.yellow();
                case "lime" -> set.lime();
                case "pink" -> set.pink();
                case "gray" -> set.gray();
                case "light_gray" -> set.lightGray();
                case "cyan" -> set.cyan();
                case "purple" -> set.purple();
                case "blue" -> set.blue();
                case "brown" -> set.brown();
                case "green" -> set.green();
                case "red" -> set.red();
                default -> set.black();
            };
            s = b.defaultBlockState();
        } else {
            Identifier id = Identifier.tryParse("minecraft:" + name);
            Block b = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
            if (b == null || b == Blocks.AIR) {
                AbpsMod.LOGGER.warn("Rig block '{}' not found, using stone", name);
                b = Blocks.STONE;
            }
            s = b.defaultBlockState();
        }
        for (String p : props.split(",")) {
            switch (p.trim()) {
                case "lit" -> {
                    if (s.hasProperty(BlockStateProperties.LIT)) s = s.setValue(BlockStateProperties.LIT, true);
                }
                case "south", "north", "east", "west" -> {
                    if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                        s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.byName(p.trim()));
                }
                default -> {
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ live bodies

    public static final String TAG = "abps_rig";
    private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();

    /** True for body parts this server made since it started; anything else with the tag is left over and removed. */
    public static boolean isLive(Entity e) {
        return LIVE.contains(e.getUUID());
    }

    final Mob mob;
    final Model model;
    final float scale;
    final int glow;
    final List<Display.BlockDisplay> parts = new ArrayList<>();
    final Map<Display.BlockDisplay, Vector3f> offsets = new HashMap<>();
    boolean hidden, dead, flashing;
    float lastYaw = Float.NaN, lastHead, lastPitch;
    boolean wasHidden;
    int t, act;
    float phase;
    double lastX, lastZ;

    private Rig(Mob mob, Model model, float scale, int glow) {
        this.mob = mob;
        this.model = model;
        this.scale = scale;
        this.glow = glow;
        this.lastX = mob.getX();
        this.lastZ = mob.getZ();
    }

    /**
     * Puts a body on a mob. The mob turns invisible and anything it holds or wears is taken off (it would float in
     * the air otherwise). glow is an outline color for elites and bosses, or -1.
     */
    static Rig attach(ServerLevel level, Mob mob, Model model, float scale, int glow) {
        Rig rig = new Rig(mob, model, scale, glow);
        mob.setInvisible(true);
        mob.setGlowingTag(false);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) mob.setItemSlot(slot, ItemStack.EMPTY);
        for (Part p : model.parts) {
            Display.BlockDisplay d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            d.setPos(mob.getX(), mob.getY(), mob.getZ());
            d.setBlockState(p.state());
            d.addTag(TAG);
            d.setSilent(true);
            d.setViewRange(1.2f);
            if (p.bright()) d.setBrightnessOverride(Brightness.FULL_BRIGHT);
            if (glow >= 0) {
                d.setGlowingTag(true);
                d.setGlowColorOverride(glow);
            }
            LIVE.add(d.getUUID());
            level.addFreshEntity(d);
            rig.parts.add(d);
            mount(d, mob);
        }
        rig.pose(0, null, 1f, 0);
        return rig;
    }

    /** Rides a part on its mob. Done with the /ride command, which lets any number of parts ride one mob. */
    private static void mount(Entity passenger, Entity vehicle) {
        MinecraftServer server = AbpsMod.server();
        if (server == null) return;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "ride " + passenger.getStringUUID() + " mount " + vehicle.getStringUUID());
        } catch (RuntimeException e) {
            AbpsMod.LOGGER.warn("Couldn't mount a rig part: {}", e.toString());
        }
    }

    /** Shows a body that started hidden (a lurker coming out of hiding). */
    void reveal() {
        hidden = false;
    }

    /** Raises the arms for a moment, for a swing or a cast. */
    void act() {
        act = 10;
    }

    /** Runs every tick. Returns false once the body is gone. */
    boolean tick() {
        t++;
        if (dead) return false;
        if (!mob.isAlive() || mob.isRemoved()) {
            collapse();
            return false;
        }
        // Parts that fell off (a teleport, a chunk reload) get back on
        if (t % 20 == 0) {
            for (Display.BlockDisplay d : parts) if (!d.isRemoved() && d.getVehicle() != mob) mount(d, mob);
        }
        boolean hurt = mob.hurtTime > 0;
        if (hurt != flashing) {
            flashing = hurt;
            for (Display.BlockDisplay d : parts) {
                if (hurt) {
                    d.setGlowingTag(true);
                    d.setGlowColorOverride(0xFF3B30);
                } else if (glow >= 0) {
                    d.setGlowColorOverride(glow);
                } else {
                    d.setGlowingTag(false);
                }
            }
        }
        if (act > 0) act--;
        if (t % 3 != 0) return true;
        double dx = mob.getX() - lastX, dz = mob.getZ() - lastZ;
        lastX = mob.getX();
        lastZ = mob.getZ();
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        phase += speed * 2.8f;
        float amount = Math.min(1f, speed * 5f);
        // Standing still and not turning: nothing to send, unless something on the body moves by itself
        boolean still = amount < 0.02f && act == 0 && mob.yBodyRot == lastYaw && mob.yHeadRot == lastHead && mob.getXRot() == lastPitch
                && hidden == wasHidden && !model.animated();
        lastYaw = mob.yBodyRot;
        lastHead = mob.yHeadRot;
        lastPitch = mob.getXRot();
        wasHidden = hidden;
        if (!still) pose(amount, null, 1f, 4);
        return true;
    }

    /** How a bone is turned right now. */
    private Quaternionf boneRot(Bone b, float amount) {
        float sw = (float) Math.sin(phase) * model.swing * amount;
        float time = t * 0.1f;
        float raise = act > 0 ? -1.5f * Math.min(1f, act / 5f) : 0;
        return switch (b) {
            case LEG_L -> new Quaternionf().rotateX(sw);
            case LEG_R -> new Quaternionf().rotateX(-sw);
            case ARM_L -> new Quaternionf().rotateX(model.armPitch - sw * 0.8f + raise);
            case ARM_R -> new Quaternionf().rotateX(model.armPitch + sw * 0.8f + raise);
            case HEAD -> {
                float yaw = Mth.clamp(Mth.wrapDegrees(mob.yHeadRot - mob.yBodyRot), -70, 70);
                float pitch = Mth.clamp(mob.getXRot(), -40, 40);
                yield new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch));
            }
            case BODY -> new Quaternionf().rotateX(model.bodyPitch);
            case TAIL -> new Quaternionf().rotateY((float) Math.sin(time * 2) * 0.35f);
            case CAPE -> new Quaternionf().rotateX(0.08f + 0.3f * amount + (float) Math.sin(time * 1.3f) * 0.04f);
            case WING_L -> new Quaternionf().rotateY((float) Math.sin(time * 4) * 0.55f);
            case WING_R -> new Quaternionf().rotateY((float) -Math.sin(time * 4) * 0.55f);
            case ORBIT -> new Quaternionf().rotateY(time * model.orbitSpeed);
            default -> new Quaternionf();
        };
    }

    /**
     * Sets every part's transformation. fall tips the whole body over (for dying), shrink scales it, and duration
     * is how many ticks the client takes to blend there.
     */
    private void pose(float amount, Quaternionf fall, float shrink, int duration) {
        float k = scale * model.size / 16f;
        Quaternionf yaw = new Quaternionf().rotateY((float) Math.toRadians(-mob.yBodyRot));
        float time = t * 0.1f;
        float bob = model.floatHeight * k + (model.floatHeight > 0 ? (float) Math.sin(time * 1.6f) * 0.08f * scale * model.size : 0)
                + (float) Math.abs(Math.sin(phase)) * 0.04f * amount * scale;
        EnumMap<Bone, Quaternionf> rots = new EnumMap<>(Bone.class);
        for (int i = 0; i < parts.size(); i++) {
            Display.BlockDisplay d = parts.get(i);
            if (d.isRemoved()) continue;
            Part p = model.parts.get(i);
            Vector3f off = offsets.computeIfAbsent(d, x -> new Vector3f());
            if (!dead) off.set((float) (mob.getX() - d.getX()), (float) (mob.getY() - d.getY()), (float) (mob.getZ() - d.getZ()));
            Quaternionf rb;
            Quaternionf rp = new Quaternionf(p.rot());
            if (p.bone() == Bone.LEGS) {
                // Spider legs: each one sweeps around its own root, alternating
                rb = new Quaternionf();
                rp = new Quaternionf().rotateY((float) Math.sin(phase * 1.5f + p.phase()) * 0.35f * amount).mul(rp);
            } else if (p.bone() == Bone.QLEGS) {
                // Four-legged walk: each leg swings forward and back around its own root
                rb = new Quaternionf();
                rp = new Quaternionf().rotateX((float) Math.sin(phase + p.phase()) * 0.5f * amount).mul(rp);
            } else {
                rb = rots.computeIfAbsent(p.bone(), b -> boneRot(b, amount));
            }
            Vector3f pivot = new Vector3f(model.pivot(p.bone())).mul(k);
            Vector3f from = new Vector3f(p.from()).mul(k).add(rp.transform(new Vector3f(p.offset()).mul(k)));
            Vector3f local = rb.transform(from).add(pivot).add(0, bob, 0);
            Quaternionf left = new Quaternionf(rb).mul(rp);
            if (fall != null) {
                local = fall.transform(local);
                left = new Quaternionf(fall).mul(left);
            }
            Vector3f translation = yaw.transform(local).add(off);
            Vector3f size = hidden ? new Vector3f(0.001f) : new Vector3f(p.size()).mul(k * shrink);
            Transformation tr = new Transformation(translation, new Quaternionf(yaw).mul(left), size, new Quaternionf());
            d.setTransformationInterpolationDelay(0);
            d.setTransformationInterpolationDuration(duration);
            d.setTransformation(tr);
        }
    }

    /** The mob died: the body falls over, then crumbles away. */
    void collapse() {
        if (dead) return;
        dead = true;
        for (Display.BlockDisplay d : parts) {
            d.stopRiding();
            d.setGlowingTag(false);
        }
        pose(0, new Quaternionf().rotateX(1.35f), 0.95f, 8);
        dev.abps.util.Tasks.later(12, () -> pose(0, new Quaternionf().rotateX(1.5f), 0.02f, 8));
        dev.abps.util.Tasks.later(22, this::remove);
    }

    /** Takes every part out of the world at once. */
    void remove() {
        dead = true;
        for (Display.BlockDisplay d : parts) {
            LIVE.remove(d.getUUID());
            d.discard();
        }
        parts.clear();
    }
}
