package dev.abps.dungeon;

import com.mojang.authlib.GameProfile;
import dev.abps.AbpsMod;
import dev.abps.classes.Hit;
import dev.abps.data.PlayerData;
import dev.abps.items.CustomItems;
import dev.abps.util.Mods;
import dev.abps.util.Tasks;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Development only, part of the self test. Checks every custom item power, then sends a fake player through every
 * dungeon: it walks into each room, kills what spawns, waits out the traps, beats the boss after letting it attack
 * for a while, and steps onto the exit. The arena is played to wave 3. Last, it walks into a world gate.
 */
public final class DungeonTest {

    /** Longest one dungeon may take before it counts as stuck (3 minutes). */
    private static final int TIMEOUT_TICKS = 20 * 180;

    private DungeonTest() {
    }

    public static void run(MinecraftServer server, BiConsumer<String, Boolean> check, Runnable done) {
        ServerLevel world = server.overworld();
        try {
            items(world, check);
        } catch (Exception e) {
            check.accept("custom items: CRASH " + e, false);
            AbpsMod.LOGGER.error("Custom item self test crash", e);
        }
        if (Dungeons.level() == null) {
            check.accept("the dungeon world exists", false);
            done.run();
            return;
        }
        List<DungeonDef> defs = new ArrayList<>(DungeonDef.ALL.values());
        next(world, defs, 0, check, () -> gate(world, check, done));
    }

    private static FakePlayer fake(ServerLevel world, String name) {
        FakePlayer p = FakePlayer.get(world, new GameProfile(UUID.randomUUID(), name));
        p.snapTo(0.5, 100, 0.5, 0, 0);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    // ------------------------------------------------------------------ custom items

    private static void items(ServerLevel world, BiConsumer<String, Boolean> check) {
        for (String id : CustomItems.ALL.keySet()) {
            ItemStack s = CustomItems.create(id, world);
            check.accept("item " + id + " is made and tagged", !s.isEmpty() && id.equals(CustomItems.idOf(s)));
        }
        FakePlayer p = fake(world, "ItemTester");
        Mob victim = EntityTypes.HUSK.create(world, EntitySpawnReason.COMMAND);
        if (victim == null) {
            check.accept("item test husk", false);
            return;
        }
        victim.snapTo(0.5, 100, 3.5, 180, 0);
        victim.setNoAi(true);
        Mods.setBase(victim, Attributes.MAX_HEALTH, 500);
        victim.setHealth(500);
        world.addFreshEntity(victim);
        try {
            ItemStack blood = CustomItems.create("bloodfang", world);
            p.setHealth(10);
            CustomItems.afterHit(p, victim, 10f, new Hit(true, false, null, blood));
            check.accept("item bloodfang heals 15% of damage", Math.abs(p.getHealth() - 11.5f) < 0.01f);

            CustomItems.afterHit(p, victim, 4f, new Hit(true, false, null, CustomItems.create("frostbite", world)));
            check.accept("item frostbite slows", victim.hasEffect(MobEffects.SLOWNESS));

            ItemStack sun = CustomItems.create("sunblade", world);
            Hit sunHit = new Hit(true, false, null, sun);
            check.accept("item sunblade hurts undead more", CustomItems.outgoing(p, victim, sunHit) > 1.4);
            CustomItems.afterHit(p, victim, 4f, sunHit);
            check.accept("item sunblade sets on fire", victim.getRemainingFireTicks() > 0);
            victim.clearFire();

            ItemStack voidr = CustomItems.create("voidrender", world);
            float before = victim.getHealth();
            for (int k = 0; k < 4; k++) CustomItems.afterHit(p, victim, 1f, new Hit(true, false, null, voidr));
            check.accept("item voidrender tears every 4th hit", victim.getHealth() < before);

            p.setItemInHand(InteractionHand.MAIN_HAND, CustomItems.create("reaper", world));
            p.setHealth(10);
            CustomItems.onKill(p, victim);
            check.accept("item reaper heals and speeds up on kills", p.getHealth() >= 13.9f && p.hasEffect(MobEffects.SPEED));

            List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.RAW_IRON, 2)));
            p.setItemInHand(InteractionHand.MAIN_HAND, CustomItems.create("molten_pick", world));
            CustomItems.modifyDrops(p, Blocks.IRON_ORE.defaultBlockState(), drops);
            check.accept("item molten pick smelts ores", drops.getFirst().is(Items.IRON_INGOT) && drops.getFirst().getCount() == 2);

            // Quarry Pick: break the middle of a 3x3 wall in front and the rest goes too
            BlockPos mid = new BlockPos(0, 101, 6);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++) world.setBlockAndUpdate(mid.offset(dx, dy, 0), Blocks.STONE.defaultBlockState());
            p.snapTo(0.5, 100, 4.5, 0, 0);
            p.setItemInHand(InteractionHand.MAIN_HAND, CustomItems.create("quarry_pick", world));
            p.gameMode.destroyBlock(mid);
            int left = 0;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++) if (!world.getBlockState(mid.offset(dx, dy, 0)).isAir()) left++;
            check.accept("item quarry pick mines 3x3", left == 0);

            check.accept("item bulwark and windrunners carry their stats",
                    CustomItems.create("bulwark", world).has(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS)
                            && CustomItems.create("windrunners", world).has(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS));

            p.getInventory().add(CustomItems.create("phoenix_feather", world));
            p.setHealth(1);
            boolean saved = !CustomItems.allowDeath(p);
            check.accept("item phoenix feather saves you", saved && p.getHealth() >= p.getMaxHealth() * 0.5f - 0.01f);
            check.accept("item phoenix feather has a cooldown", CustomItems.allowDeath(p));
        } finally {
            victim.discard();
            p.getInventory().clearContent();
        }
    }

    // ------------------------------------------------------------------ dungeons

    private static void next(ServerLevel world, List<DungeonDef> defs, int i, BiConsumer<String, Boolean> check, Runnable done) {
        if (i >= defs.size()) {
            done.run();
            return;
        }
        DungeonDef def = defs.get(i);
        Runnable after = () -> next(world, defs, i + 1, check, done);
        FakePlayer fake = fake(world, "Delver" + i);
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
        Dungeons.TEST_PLAYERS.put(fake.getUUID(), fake);
        Dungeons.start(fake, def.id());
        Run run = Dungeons.runOf(fake);
        if (run == null) {
            check.accept("dungeon " + def.id() + " starts", false);
            cleanup(fake);
            after.run();
            return;
        }
        long started = Tasks.now();
        boolean[] cleared = {false}, downTested = {false}, finished = {false};
        int[] lastRoom = {-1};
        Tasks.Handle[] handle = new Tasks.Handle[1];
        handle[0] = Tasks.schedule(5, 5, TIMEOUT_TICKS / 5 + 1, step -> {
            if (finished[0]) return;
            try {
                fake.setHealth(fake.getMaxHealth());
                fake.clearFire();
                // Tough enough that the boss can't burn through every down while it's being watched
                fake.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.RESISTANCE, 40, 3, false, false));
                boolean over = run.state == Run.State.OVER;
                boolean timedOut = Tasks.now() - started >= TIMEOUT_TICKS;
                if (!over && !timedOut) {
                    if (run.state == Run.State.ACTIVE && fake.level() == run.level) {
                        if (run.current != lastRoom[0]) {
                            lastRoom[0] = run.current;
                            AbpsMod.LOGGER.info("[dungeon test] {} room {}/{}: {}", def.id(), run.current + 1, run.rooms.size(), run.room().type);
                        }
                        drive(run, fake, def, check, downTested);
                    } else if (run.state == Run.State.CLEARED) {
                        cleared[0] = true;
                        Vec3 c = run.room().center();
                        fake.teleportTo(run.level, c.x, c.y, c.z, java.util.Set.<Relative>of(), 0, 0, false);
                    }
                    return;
                }
                finished[0] = true;
                handle[0].cancel();
                long secs = (Tasks.now() - started) / 20;
                if (timedOut && !over) {
                    check.accept("dungeon " + def.id() + " finishes (stuck in room " + (run.current + 1) + " " + run.room().type + " after " + secs + "s)", false);
                    Dungeons.leave(fake);
                } else if (def.mode() == DungeonDef.Mode.WAVES) {
                    PlayerData d = AbpsMod.data().get(fake);
                    check.accept("dungeon " + def.id() + " reaches wave 3 and records it (best wave " + d.bestWave + ", " + secs + "s)", d.bestWave >= 3);
                } else {
                    PlayerData d = AbpsMod.data().get(fake);
                    check.accept("dungeon " + def.id() + " is cleared and recorded in " + secs + "s", cleared[0] && d.dungeonClears.getOrDefault(def.id(), 0) >= 1
                            && !Dungeons.board(def.id()).isEmpty());
                }
                check.accept("dungeon " + def.id() + " sends you home", fake.level() == world);
                cleanup(fake);
                after.run();
            } catch (Exception e) {
                finished[0] = true;
                handle[0].cancel();
                check.accept("dungeon " + def.id() + ": CRASH " + e, false);
                AbpsMod.LOGGER.error("Dungeon self test crash", e);
                try {
                    Dungeons.leave(fake);
                } catch (Exception ignored) {
                }
                cleanup(fake);
                after.run();
            }
        });
    }

    /** One step of playing: go into the next room, kill what's there, and hit the boss once it has had time to attack. */
    private static void drive(Run run, FakePlayer fake, DungeonDef def, BiConsumer<String, Boolean> check, boolean[] downTested) {
        Run.Room r = run.room();
        if (def.mode() == DungeonDef.Mode.WAVES) {
            if (run.wave >= 3 && run.alive(r) == 0 && run.brain == null) {
                Dungeons.leave(fake);
                return;
            }
            killAll(run, r, fake, null);
            return;
        }
        if (!r.started) {
            Vec3 c = r.center();
            fake.teleportTo(run.level, c.x, c.y, c.z, java.util.Set.<Relative>of(), -90, 0, false);
            return;
        }
        // Once per test: being "killed" in a dungeon should down you instead
        if (!downTested[0] && r.type == Run.RoomType.COMBAT) {
            downTested[0] = true;
            int downs = run.downs;
            boolean died = Dungeons.allowDeath(fake);
            check.accept("dungeon " + def.id() + " downs you instead of killing you", !died && run.downs == downs + 1);
            return;
        }
        switch (r.type) {
            case BOSS -> {
                // Let the boss use its attacks for 8 seconds first, so the telegraphs and hits get tested too
                LivingEntity boss = run.brain == null ? null : run.brain.boss();
                killAll(run, r, fake, boss);
                if (boss != null && boss.isAlive() && System.currentTimeMillis() - r.startedAt > 8000) {
                    boss.invulnerableTime = 0;
                    boss.hurtServer(run.level, fake.damageSources().playerAttack(fake), 120f);
                }
            }
            default -> killAll(run, r, fake, null);
        }
    }

    private static void killAll(Run run, Run.Room r, FakePlayer fake, Entity spare) {
        for (UUID id : new ArrayList<>(r.mobs)) {
            Entity e = run.level.getEntity(id);
            if (e == null || e == spare || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
            le.invulnerableTime = 0;
            le.hurtServer(run.level, fake.damageSources().playerAttack(fake), 10_000f);
            if (le.isAlive()) le.kill(run.level);
        }
    }

    private static void cleanup(FakePlayer fake) {
        Party.leave(fake, false);
        Dungeons.TEST_PLAYERS.remove(fake.getUUID());
    }

    // ------------------------------------------------------------------ gates

    /** Builds a gate, walks a fake player into it and checks a Shifting Depths run starts. Removes the gate after. */
    private static void gate(ServerLevel world, BiConsumer<String, Boolean> check, Runnable done) {
        BlockPos gate;
        try {
            gate = Gates.build(world, 400, 400);
        } catch (Exception e) {
            check.accept("gate: CRASH " + e, false);
            AbpsMod.LOGGER.error("Gate self test crash", e);
            done.run();
            return;
        }
        if (gate == null) {
            // Water or another gate nearby; not a failure of the code
            AbpsMod.LOGGER.info("[dungeon test] no room for a test gate at 400 400, skipped");
            done.run();
            return;
        }
        check.accept("gate is built with its keystone", world.getBlockState(gate.above(4)).is(Blocks.CRYING_OBSIDIAN));
        FakePlayer fake = fake(world, "GateWalker");
        Dungeons.TEST_PLAYERS.put(fake.getUUID(), fake);
        // Stand in front of it, then step into the opening
        fake.teleportTo(world, gate.getX() + 0.5, gate.getY(), gate.getZ() - 2.5, java.util.Set.<Relative>of(), 0, 0, false);
        Tasks.later(10, () -> {
            fake.teleportTo(world, gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5, java.util.Set.<Relative>of(), 0, 0, false);
            Tasks.later(10, () -> {
                Run run = Dungeons.runOf(fake);
                check.accept("walking into a gate starts The Shifting Depths", run != null && run.def.id().equals(Gates.DUNGEON));
                if (run != null) Dungeons.leave(fake);
                Gates.forget(gate);
                cleanup(fake);
                Tasks.later(40, done);
            });
        });
    }
}
