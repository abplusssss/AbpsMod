package dev.abps.dungeon;

import dev.abps.util.Mods;
import dev.abps.util.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The dungeon's own monsters. Each is a vanilla mob dressed up (gear, size, stats, a name) with a small brain
 * that gives it real moves: charges, leaps, slams, bolts, summons, webs, blinks and auras. Big moves are warned
 * on the ground first, the same way bosses do it. Elites can also roll a trait, like Explosive or Frenzied.
 */
final class MobKit {

    private MobKit() {
    }

    enum Theme { CRYPT, FROST, FORGE, DEPTHS }

    enum Kind {
        GRAVE_KNIGHT("Grave Knight", EntityTypes.ZOMBIE, Theme.CRYPT, 1.2, 2.2, 1.2, 0x9E9E9E),
        BONE_CALLER("Bone Caller", EntityTypes.SKELETON, Theme.CRYPT, 1.0, 1.4, 1.0, 0x64FFDA),
        GHOUL("Ghoul", EntityTypes.HUSK, Theme.CRYPT, 0.85, 1.0, 1.0, 0xA1887F),
        CRYPT_WEAVER("Crypt Weaver", EntityTypes.CAVE_SPIDER, Theme.CRYPT, 1.4, 1.6, 1.0, 0x7E57C2),
        FROST_WRAITH("Frost Wraith", EntityTypes.STRAY, Theme.FROST, 1.1, 1.5, 1.0, 0x80DEEA),
        ICE_BRUTE("Ice Brute", EntityTypes.ZOMBIE, Theme.FROST, 1.6, 3.2, 1.4, 0x4FC3F7),
        FROST_SPRITE("Frost Sprite", EntityTypes.VEX, Theme.FROST, 1.0, 1.6, 0.8, 0xE1F5FE),
        MAGMA_BRUTE("Magma Brute", EntityTypes.PIGLIN_BRUTE, Theme.FORGE, 1.3, 1.8, 1.1, 0xFF6D00),
        EMBER_IMP("Ember Imp", EntityTypes.BLAZE, Theme.FORGE, 0.8, 1.0, 1.0, 0xFFAB40),
        FORGE_WARDEN("Forge Warden", EntityTypes.WITHER_SKELETON, Theme.FORGE, 1.45, 2.4, 1.2, 0x8D6E63),
        SCULK_LURKER("Sculk Lurker", EntityTypes.ZOMBIE, Theme.DEPTHS, 1.1, 1.8, 1.3, 0x00897B),
        ECHO_WITCH("Echo Witch", EntityTypes.WITCH, Theme.DEPTHS, 1.0, 1.5, 1.0, 0xB388FF),
        SHADE("Shade", EntityTypes.VEX, Theme.DEPTHS, 1.2, 1.8, 1.0, 0x311B92);

        final String title;
        final EntityType<? extends Mob> type;
        final Theme theme;
        final double scale, hp, dmg;
        final int color;

        Kind(String title, EntityType<? extends Mob> type, Theme theme, double scale, double hp, double dmg, int color) {
            this.title = title;
            this.type = type;
            this.theme = theme;
            this.scale = scale;
            this.hp = hp;
            this.dmg = dmg;
            this.color = color;
        }
    }

    enum Affix {
        SWIFT("Swift"), ARMORED("Armored"), REGENERATING("Regenerating"), MOLTEN("Molten"), EXPLOSIVE("Explosive"), FRENZIED("Frenzied"),
        SHIELDED("Shielded");

        final String label;

        Affix(String label) {
            this.label = label;
        }
    }

    /** One custom mob in a run and what it's doing. */
    static final class Brain {
        final UUID id;
        final Kind kind;
        final Affix affix;
        final Run.Room room;
        int ticks, cdA, cdB;
        boolean frenzied, exploded, revealed;
        final List<UUID> summons = new ArrayList<>();

        Brain(UUID id, Kind kind, Affix affix, Run.Room room) {
            this.id = id;
            this.kind = kind;
            this.affix = affix;
            this.room = room;
        }
    }

    static Theme themeOf(DungeonDef.Palette pal) {
        if (pal == DungeonDef.FROST) return Theme.FROST;
        if (pal == DungeonDef.FORGE) return Theme.FORGE;
        if (pal == DungeonDef.ANCIENT) return Theme.DEPTHS;
        return Theme.CRYPT;
    }

    static Kind pick(Run run, Theme theme) {
        List<Kind> pool = new ArrayList<>();
        for (Kind k : Kind.values()) if (k.theme == theme || run.def.mode() == DungeonDef.Mode.WAVES) pool.add(k);
        return pool.get(run.rnd.nextInt(pool.size()));
    }

    static Affix randomAffix(Run run) {
        Affix[] all = Affix.values();
        return all[run.rnd.nextInt(all.length)];
    }

    /** Spawns a custom mob in a room and starts its brain. */
    static Mob spawn(Run run, Kind kind, Vec3 at, Run.Room room, Affix affix, boolean elite) {
        String name = (affix != null ? affix.label + " " : "") + kind.title;
        String color = String.format("<#%06X>", kind.color);
        Mob m = run.spawn(kind.type, at, kind.hp * (elite ? 3 : 1), (elite ? "<gold><bold>" : color) + name, room);
        if (m == null) return null;
        Mods.setBase(m, Attributes.SCALE, kind.scale * (elite ? 1.25 : 1));
        if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) Mods.scaleBase(m, Attributes.ATTACK_DAMAGE, kind.dmg);
        m.setCustomNameVisible(elite);
        dress(m, kind);
        if (affix != null) applyAffix(m, affix);
        if (elite) m.setGlowingTag(true);
        Brain b = new Brain(m.getUUID(), kind, affix, room);
        b.cdA = 40 + run.rnd.nextInt(60);
        b.cdB = 60 + run.rnd.nextInt(80);
        run.brains.add(b);
        return m;
    }

    private static void wear(Mob m, EquipmentSlot slot, ItemStack s) {
        m.setItemSlot(slot, s);
        m.setDropChance(slot, 0);
    }

    /** Gear that makes each kind look like itself. */
    private static void dress(Mob m, Kind k) {
        switch (k) {
            case GRAVE_KNIGHT -> {
                wear(m, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                wear(m, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                wear(m, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                wear(m, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case BONE_CALLER -> {
                wear(m, EquipmentSlot.HEAD, new ItemStack(Items.SKELETON_SKULL));
                wear(m, EquipmentSlot.MAINHAND, new ItemStack(Items.BONE));
            }
            case GHOUL -> m.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 3600, 1, false, false));
            case FROST_WRAITH -> {
                wear(m, EquipmentSlot.HEAD, new ItemStack(Items.ICE));
                m.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 3600, 0, false, false));
            }
            case ICE_BRUTE -> {
                wear(m, EquipmentSlot.HEAD, new ItemStack(Items.PACKED_ICE));
                wear(m, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                Mods.setBase(m, Attributes.KNOCKBACK_RESISTANCE, 0.8);
                Mods.scaleBase(m, Attributes.MOVEMENT_SPEED, 0.8);
            }
            case MAGMA_BRUTE -> {
                wear(m, EquipmentSlot.HEAD, new ItemStack(Items.MAGMA_BLOCK));
                wear(m, EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
                m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 3600, 0, false, false));
            }
            case EMBER_IMP -> m.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 3600, 1, false, false));
            case FORGE_WARDEN -> {
                wear(m, EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                wear(m, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
                Mods.setBase(m, Attributes.KNOCKBACK_RESISTANCE, 0.6);
            }
            case SCULK_LURKER -> {
                wear(m, EquipmentSlot.HEAD, new ItemStack(Items.SCULK));
                m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 3600, 0, false, false));
            }
            case SHADE -> m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 3600, 0, false, false));
            default -> {
            }
        }
    }

    static void applyAffix(Mob m, Affix a) {
        switch (a) {
            case SWIFT -> m.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 3600, 1, false, true));
            case ARMORED -> m.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 3600, 1, false, true));
            case REGENERATING -> m.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 3600, 1, false, true));
            case MOLTEN -> {
                m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 3600, 0, false, true));
                m.setRemainingFireTicks(20 * 3600);
            }
            case SHIELDED -> m.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 3600, 4, false, true));
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ brains

    private static void hit(Run run, Mob m, ServerPlayer p, float dmg) {
        p.hurtServer(run.level, run.level.damageSources().mobAttack(m), (float) (dmg * run.dmgScale()));
    }

    private static ServerPlayer target(Mob m, List<ServerPlayer> ps) {
        if (m.getTarget() instanceof ServerPlayer sp && sp.isAlive() && ps.contains(sp)) return sp;
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : ps) {
            double d = p.distanceToSqr(m);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best != null) m.setTarget(best);
        return best;
    }

    /** Runs every tick for every custom mob in the run. */
    static void tick(Run run, List<ServerPlayer> ps) {
        if (ps.isEmpty()) return;
        for (Brain b : new ArrayList<>(run.brains)) {
            Entity e = run.level.getEntity(b.id);
            if (!(e instanceof Mob m) || !m.isAlive()) {
                if (b.affix == Affix.EXPLOSIVE && !b.exploded && e != null) explode(run, b, e.position());
                run.brains.remove(b);
                continue;
            }
            b.ticks++;
            // Nothing leaves its room (flying sprites and shades can pass through walls)
            if (b.room != null && !b.room.inside(m) && b.ticks % 20 == 0) {
                Vec3 c = b.room.center();
                m.teleportTo(c.x, c.y, c.z);
            }
            ServerPlayer t = target(m, ps);
            if (t == null) continue;
            if (b.affix == Affix.EXPLOSIVE && m.getHealth() < m.getMaxHealth() * 0.15f && !b.exploded) {
                // Warn, then blow up where it stands
                b.exploded = true;
                explode(run, b, m.position());
                m.kill(run.level);
                continue;
            }
            if (b.affix == Affix.FRENZIED && !b.frenzied && m.getHealth() < m.getMaxHealth() * 0.5f) {
                b.frenzied = true;
                m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 3600, 1));
                m.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 3600, 1));
                Dungeons.cue(run, Dungeons.CUE_ROAR, m.position(), m.position().add(0, 2, 0), m, 0);
            }
            if (b.affix == Affix.MOLTEN && b.ticks % 20 == 0) {
                for (ServerPlayer p : ps) if (p.distanceToSqr(m) < 6.25) {
                    hit(run, m, p, 2);
                    p.igniteForSeconds(2);
                }
            }
            if (--b.cdA <= 0) b.cdA = moveA(run, b, m, t, ps);
            if (--b.cdB <= 0) b.cdB = moveB(run, b, m, t, ps);
            passive(run, b, m, t, ps);
        }
    }

    private static void explode(Run run, Brain b, Vec3 at) {
        b.exploded = true;
        Vec3 g = new Vec3(at.x, b.room != null ? b.room.floor() + 1 : at.y, at.z);
        Dungeons.cue(run, Dungeons.CUE_RING_WARN, g, g.add(3, 0, 0), null, 25);
        Tasks.later(25, () -> {
            if (run.state == Run.State.OVER) return;
            Dungeons.cue(run, Dungeons.CUE_RING_HIT, g, g.add(3, 0, 0), null, 0);
            run.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.2f);
            for (ServerPlayer p : run.online()) {
                if (p.position().distanceToSqr(g.x, p.getY(), g.z) <= 9) {
                    p.hurtServer(run.level, run.level.damageSources().magic(), (float) (7 * run.dmgScale()));
                    dev.abps.util.Targets.velocity(p, p.position().subtract(g).normalize().scale(0.8).add(0, 0.4, 0));
                }
            }
        });
    }

    /** Each kind's first move. Returns ticks until it can do it again. */
    private static int moveA(Run run, Brain b, Mob m, ServerPlayer t, List<ServerPlayer> ps) {
        double dist = m.distanceTo(t);
        Vec3 ground = new Vec3(t.getX(), b.room != null ? b.room.floor() + 1 : t.getY(), t.getZ());
        switch (b.kind) {
            case GRAVE_KNIGHT, FORGE_WARDEN -> {
                if (dist < 3 || dist > 12) return 20;
                // Charge: a lane on the ground, then a rush down it
                Vec3 from = new Vec3(m.getX(), ground.y, m.getZ());
                Vec3 to = from.add(ground.subtract(from).normalize().scale(Math.min(10, dist + 2)));
                Dungeons.cue(run, Dungeons.CUE_LINE_WARN, from, to, null, 16);
                Tasks.later(16, () -> {
                    if (!m.isAlive() || run.state == Run.State.OVER) return;
                    dev.abps.util.Targets.velocity(m, to.subtract(from).normalize().scale(1.6).add(0, 0.1, 0));
                    Tasks.later(6, () -> {
                        for (ServerPlayer p : run.online()) {
                            if (p.distanceToSqr(m) < 3.2) {
                                hit(run, m, p, b.kind == Kind.FORGE_WARDEN ? 9 : 7);
                                dev.abps.util.Targets.velocity(p, to.subtract(from).normalize().scale(0.9).add(0, 0.4, 0));
                            }
                        }
                    });
                });
                run.sound(SoundEvents.RAVAGER_ROAR, 0.4f, 1.6f);
                return 140;
            }
            case GHOUL, SCULK_LURKER -> {
                if (dist < 2.5 || dist > 9) return 15;
                if (b.kind == Kind.SCULK_LURKER && !b.revealed) {
                    b.revealed = true;
                    m.removeEffect(MobEffects.INVISIBILITY);
                    run.sound(SoundEvents.WARDEN_EMERGE, 0.5f, 1.6f);
                }
                Vec3 v = t.position().subtract(m.position());
                dev.abps.util.Targets.velocity(m, v.normalize().scale(Math.min(1.3, dist * 0.18)).add(0, 0.45, 0));
                run.sound(SoundEvents.GOAT_LONG_JUMP, 0.6f, 0.8f);
                return 100;
            }
            case BONE_CALLER -> {
                b.summons.removeIf(id -> {
                    Entity s = run.level.getEntity(id);
                    return s == null || !s.isAlive();
                });
                if (b.summons.size() >= 4) return 60;
                for (int k = 0; k < 2; k++) {
                    double a = run.rnd.nextDouble() * Math.PI * 2;
                    Vec3 at = new Vec3(m.getX() + Math.cos(a) * 2, ground.y, m.getZ() + Math.sin(a) * 2);
                    Mob s = run.spawn(EntityTypes.SKELETON, at, 0.4, "<gray>Restless Bones", b.room);
                    if (s != null) {
                        Mods.setBase(s, Attributes.SCALE, 0.7);
                        s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                        b.summons.add(s.getUUID());
                    }
                }
                Dungeons.cue(run, Dungeons.CUE_ROAR, m.position(), m.position().add(0, 2, 0), m, 0);
                run.sound(SoundEvents.EVOKER_PREPARE_SUMMON, 0.8f, 1.2f);
                return 200;
            }
            case CRYPT_WEAVER -> {
                if (dist > 10) return 20;
                // A web lands under the target and melts after a few seconds
                BlockPos at = BlockPos.containing(t.getX(), ground.y, t.getZ());
                if (run.level.getBlockState(at).isAir()) {
                    run.level.setBlock(at, Blocks.COBWEB.defaultBlockState(), 3);
                    Tasks.later(70, () -> {
                        if (run.level.getBlockState(at).is(Blocks.COBWEB)) run.level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                    });
                }
                run.sound(SoundEvents.PHANTOM_BITE, 0.6f, 1.6f);
                return 140;
            }
            case FROST_WRAITH -> {
                // Blink behind the target
                Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
                if (run.level.getBlockState(BlockPos.containing(behind)).isAir() && run.level.getBlockState(BlockPos.containing(behind).above()).isAir()) {
                    Dungeons.cue(run, Dungeons.CUE_SPAWN, m.position(), m.position().add(0, 1, 0), m, 0);
                    m.teleportTo(behind.x, ground.y, behind.z);
                    run.sound(SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.4f);
                }
                return 160;
            }
            case ICE_BRUTE -> {
                if (dist > 4.5) return 15;
                Vec3 c = new Vec3(m.getX(), ground.y, m.getZ());
                Dungeons.cue(run, Dungeons.CUE_RING_WARN, c, c.add(3.5, 0, 0), null, 20);
                Tasks.later(20, () -> {
                    if (!m.isAlive() || run.state == Run.State.OVER) return;
                    Dungeons.cue(run, Dungeons.CUE_RING_HIT, c, c.add(3.5, 0, 0), null, 0);
                    for (ServerPlayer p : run.online()) {
                        if (p.position().distanceToSqr(c.x, p.getY(), c.z) <= 12.25) {
                            hit(run, m, p, 8);
                            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                        }
                    }
                    run.sound(SoundEvents.GLASS_BREAK, 0.8f, 0.6f);
                });
                return 140;
            }
            case EMBER_IMP -> {
                if (dist > 14) return 20;
                Dungeons.cue(run, Dungeons.CUE_RING_WARN, ground, ground.add(2.5, 0, 0), null, 18);
                Tasks.later(18, () -> {
                    if (!m.isAlive() || run.state == Run.State.OVER) return;
                    Dungeons.cue(run, Dungeons.CUE_RING_HIT, ground, ground.add(2.5, 0, 0), null, 0);
                    for (ServerPlayer p : run.online()) {
                        if (p.position().distanceToSqr(ground.x, p.getY(), ground.z) <= 6.25) {
                            hit(run, m, p, 6);
                            p.igniteForSeconds(3);
                        }
                    }
                });
                run.sound(SoundEvents.BLAZE_SHOOT, 0.6f, 1.4f);
                return 100;
            }
            case ECHO_WITCH -> {
                if (dist > 16) return 20;
                Dungeons.cue(run, dev.abps.items.CustomItems.FX_SOUL, m.position().add(0, 1.5, 0), t.position().add(0, 1, 0), t, 0);
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                hit(run, m, t, 3);
                run.sound(SoundEvents.ILLUSIONER_CAST_SPELL, 0.7f, 1.2f);
                return 120;
            }
            default -> {
                return 80;
            }
        }
    }

    /** A second move for kinds that have one. */
    private static int moveB(Run run, Brain b, Mob m, ServerPlayer t, List<ServerPlayer> ps) {
        double dist = m.distanceTo(t);
        switch (b.kind) {
            case FROST_WRAITH, FROST_SPRITE -> {
                if (dist > 16) return 20;
                Dungeons.cue(run, dev.abps.items.CustomItems.FX_FROST, m.position().add(0, 1.2, 0), t.position().add(0, 1, 0), t, 0);
                Tasks.later(4, () -> {
                    if (!t.isAlive() || run.state == Run.State.OVER) return;
                    hit(run, m, t, 5);
                    t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
                });
                run.sound(SoundEvents.PLAYER_HURT_FREEZE, 0.6f, 1.6f);
                return 100;
            }
            case SHADE -> {
                if (dist > 12) return 20;
                Dungeons.cue(run, dev.abps.items.CustomItems.FX_SOUL, m.position().add(0, 0.5, 0), t.position().add(0, 1, 0), t, 0);
                hit(run, m, t, 4);
                t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                return 120;
            }
            default -> {
                return 200;
            }
        }
    }

    /** Things that happen all the time, not on a cooldown. */
    private static void passive(Run run, Brain b, Mob m, ServerPlayer t, List<ServerPlayer> ps) {
        if (b.kind == Kind.MAGMA_BRUTE && b.ticks % 20 == 0) {
            for (ServerPlayer p : ps) {
                if (p.distanceToSqr(m) < 6.25) {
                    hit(run, m, p, 2);
                    p.igniteForSeconds(2);
                }
            }
        }
        if (b.kind == Kind.SCULK_LURKER && !b.revealed && m.distanceTo(t) < 4) {
            b.revealed = true;
            m.removeEffect(MobEffects.INVISIBILITY);
            run.sound(SoundEvents.WARDEN_EMERGE, 0.5f, 1.6f);
        }
    }

    /** Fills a room with a mix of custom and plain mobs. Returns how many it made. */
    static int populate(Run run, Run.Room room, int count, java.util.function.Supplier<Vec3> spot) {
        Theme theme = themeOf(run.palette);
        int made = 0;
        for (int k = 0; k < count; k++) {
            boolean custom = run.rnd.nextDouble() < 0.55;
            Affix affix = run.def.difficulty() >= 2 && run.rnd.nextDouble() < 0.12 * run.def.difficulty() ? randomAffix(run) : null;
            Mob m = custom ? spawn(run, pick(run, theme), spot.get(), room, affix, false) : run.spawn(run.pick(), spot.get(), 1, null, room);
            if (m != null) made++;
        }
        return made;
    }
}
