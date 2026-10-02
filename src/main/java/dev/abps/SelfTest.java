package dev.abps;

import dev.abps.classes.AttributeClass;
import dev.abps.command.Commands;
import dev.abps.data.PlayerData;
import dev.abps.util.Tasks;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Development only. Start the dev server with -Dabps.selftest=true and it runs every ability of every class
 * on a fake player with real mobs around, runs every command, prints a report, then stops.
 */
public final class SelfTest {

    private static final List<String> report = new ArrayList<>();
    private static int problems;

    private SelfTest() {
    }

    public static void maybeRun(MinecraftServer server) {
        if (!Boolean.getBoolean("abps.selftest")) return;
        Tasks.later(40, () -> run(server));
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = new BlockPos(0, 100, 0);
        // A stone floor to stand on and a wall to mine
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-12, -1, -12), base.offset(12, -1, 20))) level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-12, 0, -12), base.offset(12, 8, 20))) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());

        FakePlayer fake = FakePlayer.get(level);
        fake.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0); // facing +Z
        Service s = AbpsMod.service();
        PlayerData d = s.data(fake);
        d.noCooldown = true;

        List<AttributeClass> all = new ArrayList<>(Classes.all());
        int[] index = {0};
        // One class every 3 seconds so scheduled effects get time to run
        Tasks.schedule(0, 60, all.size() + 1, step -> {
            if (step == all.size()) {
                testCommands(server);
                testShops(level);
                // Dungeons take a few minutes of real ticks, so they report back when they're done
                dev.abps.dungeon.DungeonTest.run(server, (what, ok) -> {
                    if (!ok) problems++;
                    report.add(what + ": " + (ok ? "OK" : "FAILED"));
                }, () -> finish(server));
                return;
            }
            AttributeClass c = all.get(index[0]++);
            testClass(level, base, fake, d, c);
        });
    }

    private static void testClass(ServerLevel level, BlockPos base, FakePlayer fake, PlayerData d, AttributeClass c) {
        Service s = AbpsMod.service();
        // Clear old mobs and set up
        for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(base).inflate(40))) m.discard();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-12, 0, -12), base.offset(12, 8, 20))) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        fake.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        fake.setHealth(fake.getMaxHealth());
        s.setAttribute(fake, c, AbpsMod.config().maxLevel);
        d.noCooldown = true;
        ItemStack hand = switch (c.id()) {
            case "miner" -> new ItemStack(Items.DIAMOND_PICKAXE);
            case "archer" -> new ItemStack(Items.BOW);
            case "berserker" -> new ItemStack(Items.IRON_AXE);
            default -> new ItemStack(Items.IRON_SWORD);
        };
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hand);
        if (c.id().equals("miner")) {
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, 2), base.offset(3, 4, 16))) level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(3, 0, 6), Blocks.DIAMOND_ORE.defaultBlockState());
        }
        List<Mob> mobs = new ArrayList<>();
        int[][] spots = {{0, 4}, {2, 3}, {-2, 5}};
        for (int[] spot : spots) {
            Mob husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (husk == null) continue;
            husk.snapTo(base.getX() + spot[0] + 0.5, base.getY(), base.getZ() + spot[1] + 0.5, 180, 0);
            husk.setNoAi(true);
            level.addFreshEntity(husk);
            mobs.add(husk);
        }
        // A real melee hit so "last target" abilities have one, and to test the damage hooks
        if (!mobs.isEmpty() && !c.id().equals("miner")) {
            mobs.getFirst().hurtServer(level, fake.damageSources().playerAttack(fake), 2f);
        }
        for (int i = 1; i <= 6; i++) {
            if (i == 5 && c.abilityCount() < 5) continue;
            if (i == 6) d.ultCharge = 1;
            fake.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
            try {
                boolean worked = c.useAbility(i, fake, d);
                String line = c.name() + " " + i + " " + c.abilityName(i) + ": " + (worked ? "OK" : "did not fire (" + c.lastFail + ")");
                report.add(line);
            } catch (Exception e) {
                problems++;
                report.add(c.name() + " " + i + " " + c.abilityName(i) + ": CRASH " + e);
                AbpsMod.LOGGER.error("Self test crash", e);
            }
        }
        // Tick passives a few times
        for (int t = 0; t < 8; t++) {
            try {
                d.tickCount++;
                c.tick(fake, d);
            } catch (Exception e) {
                problems++;
                report.add(c.name() + " tick: CRASH " + e);
            }
        }
    }

    private static void testCommands(MinecraftServer server) {
        String[] cmds = {"help", "attributes", "top", "stats @a", "savealll", "saveall", "reload", "giveattribute @r Miner",
                "clearcombat Nobody", "cooldowns", "home", "spawn", "tpr Nobody", "dungeon list", "party", "title"};
        for (String c : cmds) {
            try {
                Commands.handle(server.createCommandSourceStack(), c);
                report.add("command " + c + ": OK");
            } catch (Exception e) {
                problems++;
                report.add("command " + c + ": CRASH " + e);
            }
        }
    }

    /** Two fake players open a shop, list things, trade both ways, collect and close. Checks every count. */
    private static void testShops(ServerLevel level) {
        try {
            FakePlayer owner = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ShopOwner"));
            FakePlayer buyer = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ShopBuyer"));
            owner.getInventory().clearContent();
            buyer.getInventory().clearContent();
            var shops = AbpsMod.shops();
            java.util.function.BiConsumer<String, Boolean> check = (what, ok) -> {
                if (!ok) problems++;
                report.add("shop " + what + ": " + (ok ? "OK" : "FAILED"));
            };
            var diamond = new ItemStack(Items.DIAMOND);
            var iron = new ItemStack(Items.IRON_INGOT);
            var cobble = new ItemStack(Items.COBBLESTONE);

            // Can't open without the fee
            shops.handle(owner, "create|Test");
            check.accept("no fee, no shop", !shops.hasShop(owner.getUUID()));
            owner.getInventory().add(new ItemStack(Items.NETHERITE_INGOT));
            owner.getInventory().add(new ItemStack(Items.DIAMOND, 3));
            owner.getInventory().add(new ItemStack(Items.IRON_INGOT, 64));
            shops.handle(owner, "create|Test Shop");
            check.accept("create takes the fee", shops.hasShop(owner.getUUID()) && dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(diamond)) == 0
                    && dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(new ItemStack(Items.NETHERITE_INGOT))) == 0);

            // Sell 40 iron at 2 diamonds each
            int ironSlot = owner.getInventory().findSlotMatchingItem(iron);
            shops.handle(owner, "add|" + ironSlot + "|1|1|minecraft:diamond|2|40");
            check.accept("stocking takes the items", dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(iron)) == 24);

            // The buyer buys 10 for 20 diamonds
            buyer.getInventory().add(new ItemStack(Items.DIAMOND, 30));
            shops.handle(buyer, "trade|" + owner.getUUID() + "|1|10");
            check.accept("buying moves items and money", dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(iron)) == 10
                    && dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(diamond)) == 10);
            // Trying to buy more than they can pay for does nothing
            shops.handle(buyer, "trade|" + owner.getUUID() + "|1|50");
            check.accept("can't overspend", dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(diamond)) == 10
                    && dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(iron)) == 10);
            // A made up listing or a negative amount does nothing
            shops.handle(buyer, "trade|" + owner.getUUID() + "|99|1");
            shops.handle(buyer, "trade|" + owner.getUUID() + "|1|-5");
            shops.handle(buyer, "trade|garbage");
            check.accept("bad requests are ignored", dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(iron)) == 10);

            // Buy cobblestone: 1 diamond per 16, paid up for 5 trades
            owner.getInventory().add(new ItemStack(Items.COBBLESTONE));
            owner.getInventory().add(new ItemStack(Items.DIAMOND, 10));
            int cobbleSlot = owner.getInventory().findSlotMatchingItem(cobble);
            shops.handle(owner, "add|" + cobbleSlot + "|0|16|minecraft:diamond|1|5");
            check.accept("funding takes the money", dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(diamond)) == 5);
            buyer.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
            shops.handle(buyer, "trade|" + owner.getUUID() + "|2|3");
            check.accept("selling to a shop pays", dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(diamond)) == 13
                    && dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(cobble)) == 16);
            shops.handle(buyer, "trade|" + owner.getUUID() + "|2|3");
            check.accept("shop can't pay more than it has", dev.abps.util.Inv.count(buyer, dev.abps.util.Inv.same(cobble)) == 16);

            // The owner collects 20 diamonds earned and 48 cobblestone bought, then closes and gets the rest back
            shops.handle(owner, "collect");
            check.accept("collect", dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(diamond)) == 25
                    && dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(cobble)) == 49);
            shops.handle(owner, "close");
            check.accept("closing returns stock and money", !shops.hasShop(owner.getUUID())
                    && dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(iron)) == 54
                    && dev.abps.util.Inv.count(owner, dev.abps.util.Inv.same(diamond)) == 27);
        } catch (Exception e) {
            problems++;
            report.add("shop test: CRASH " + e);
            AbpsMod.LOGGER.error("Shop self test crash", e);
        }
    }

    private static void finish(MinecraftServer server) {
        AbpsMod.LOGGER.info("===== AbpsMod self test =====");
        for (String line : report) AbpsMod.LOGGER.info(line);
        AbpsMod.LOGGER.info("===== {} crashes or failures =====", problems);
        Tasks.later(40, () -> server.halt(false));
    }
}
