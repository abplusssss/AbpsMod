package dev.abps.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Development only. ./gradlew runClient -Puitest joins the dev server as an operator, opens a shop, lists things,
 * and screenshots the shop, its popups and the Travel and Profile tabs, then closes the shop and quits.
 */
public final class UiPreview {

    private static int ticks = -1;

    private UiPreview() {
    }

    public static void init() {
        if (!Boolean.getBoolean("abps.uitest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(UiPreview::tick);
    }

    private static int slotOf(Minecraft mc, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || ClientState.sync == null) return;
        ticks++;
        var c = mc.player.connection;
        switch (ticks) {
            case 5 -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                c.sendCommand("time set noon");
                c.sendCommand("clear");
                c.sendCommand("give @s netherite_ingot 1");
                c.sendCommand("give @s diamond 64");
                c.sendCommand("give @s iron_ingot 64");
                c.sendCommand("give @s cobblestone 64");
                c.sendCommand("give @s golden_apple 8");
                c.sendCommand("abps sethome base");
            }
            case 30 -> mc.gui.setScreen(new MenuScreen("shops"));
            case 50 -> Screenshot.grab(mc, false);
            case 55 -> AbpsClient.send("shop", "create|Stephen's Store");
            case 75 -> {
                AbpsClient.send("shop", "add|" + slotOf(mc, Items.IRON_INGOT) + "|1|1|minecraft:diamond|2|40");
                AbpsClient.send("shop", "add|" + slotOf(mc, Items.GOLDEN_APPLE) + "|1|1|minecraft:emerald|6|8");
            }
            case 85 -> AbpsClient.send("shop", "add|" + slotOf(mc, Items.COBBLESTONE) + "|0|16|minecraft:diamond|1|5");
            case 100 -> mc.gui.setScreen(new ShopScreen());
            case 115 -> Screenshot.grab(mc, false);
            case 120 -> {
                mc.player.getInventory().setSelectedSlot(Math.max(0, slotOf(mc, Items.DIAMOND)));
                if (mc.gui.screen() instanceof ShopScreen s) s.preview("add", -1);
            }
            case 130 -> Screenshot.grab(mc, false);
            case 135 -> {
                if (mc.gui.screen() instanceof ShopScreen s && ClientState.shop != null && !ClientState.shop.listings().isEmpty())
                    s.preview("trade", ClientState.shop.listings().getFirst().id());
            }
            case 145 -> Screenshot.grab(mc, false);
            case 150 -> {
                if (mc.gui.screen() instanceof ShopScreen s && ClientState.shop != null && ClientState.shop.listings().size() > 2)
                    s.preview("manage", ClientState.shop.listings().get(2).id());
            }
            case 160 -> Screenshot.grab(mc, false);
            case 165 -> mc.gui.setScreen(new MenuScreen("shops"));
            case 180 -> Screenshot.grab(mc, false);
            case 185 -> mc.gui.setScreen(new MenuScreen("travel"));
            case 200 -> Screenshot.grab(mc, false);
            case 205 -> mc.gui.setScreen(new MenuScreen("profile"));
            case 220 -> Screenshot.grab(mc, false);
            case 222 -> AbpsClient.send("daily", "");
            case 240 -> Screenshot.grab(mc, false);
            case 245 -> {
                AbpsClient.send("shop", "close");
                c.sendCommand("abps delhome base");
            }
            case 270 -> mc.stop();
            default -> {
            }
        }
    }
}
