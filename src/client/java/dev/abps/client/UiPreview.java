package dev.abps.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.Items;

/**
 * Development only. ./gradlew runClient -Puitest joins the dev server as an operator and uses the shop the way a
 * player would: it clicks the real buttons and types into the boxes, checks that each step worked, and takes
 * screenshots along the way. Results go to the log as [UiTest] lines.
 */
public final class UiPreview {

    private static int ticks = -1;
    private static int passed, failed;

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

    /**
     * Clicks at a menu position through the game's real mouse handler, the same path a physical click takes, so the
     * test catches problems like which number the game uses for the left button.
     */
    static void realClick(double guiX, double guiY, boolean right) {
        Minecraft mc = Minecraft.getInstance();
        var w = mc.getWindow();
        try {
            for (String f : new String[]{"xpos", "ypos"}) {
                var field = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
                field.setAccessible(true);
                double v = f.equals("xpos") ? guiX * w.getScreenWidth() / w.getGuiScaledWidth() : guiY * w.getScreenHeight() / w.getGuiScaledHeight();
                field.setDouble(mc.mouseHandler, v);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not move the test mouse", e);
        }
        var button = new net.minecraft.client.input.MouseButtonInfo(right ? com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT
                : com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
        mc.mouseHandler.onButton(w.handle(), button, 1);
        mc.mouseHandler.onButton(w.handle(), button, 0);
    }

    private static void check(String what, boolean ok) {
        if (ok) passed++;
        else failed++;
        dev.abps.AbpsMod.LOGGER.info("[UiTest] {} {}", ok ? "OK  " : "FAIL", what);
    }

    private static ShopScreen shop(Minecraft mc) {
        return mc.gui.screen() instanceof ShopScreen s ? s : null;
    }

    private static MenuScreen menu(Minecraft mc) {
        return mc.gui.screen() instanceof MenuScreen s ? s : null;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || ClientState.sync == null) return;
        ticks++;
        var c = mc.player.connection;
        ShopScreen s = shop(mc);
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
            case 45 -> Screenshot.grab(mc, false);
            case 50 -> check("Open shop button exists", menu(mc) != null && menu(mc).press("Open shop"));
            case 80 -> check("Opening a shop shows the shop screen", s != null && ClientState.shop != null && ClientState.shop.mine());
            case 82 -> {
                check("+ Add listing button", s != null && s.press("+ Add listing", 0));
                check("Add popup opens", s != null && s.popupName().equals("add"));
            }
            case 84 -> check("Pick iron from inventory", s != null && s.press("slot:" + slotOf(mc, Items.IRON_INGOT), 0));
            case 86 -> {
                check("Sell it", s != null && s.press("Sell it", 0));
                check("Click the amount box", s != null && s.pressQty());
                s.typeText("32");
                check("Typing 32 sets the amount", s.qtyValue() == 32);
                s.press("+1", 0);
                check("+1 makes it 33", s.qtyValue() == 33);
                s.press("-10", 0);
                check("-10 makes it 23", s.qtyValue() == 23);
            }
            case 88 -> check("Pick emerald as the price", s != null && s.press("cur:minecraft:emerald", 0));
            case 90 -> Screenshot.grab(mc, false);
            case 92 -> {
                check("List it", s != null && s.press("List it", 0));
                check("Popup closes after listing", s != null && s.popupName().isEmpty());
            }
            case 110 -> check("Listing shows up", ClientState.shop != null && ClientState.shop.listings().size() == 1
                    && ClientState.shop.listings().getFirst().stock() == 23);
            case 112 -> {
                if (s != null) s.press("+ Add listing", 0);
            }
            case 113 -> {
                if (s != null) s.press("slot:" + slotOf(mc, Items.COBBLESTONE), 0);
            }
            case 114 -> check("Buy it", s != null && s.press("Buy it", 0));
            case 116 -> {
                if (s == null) return;
                s.press("+1", 0);
                s.press("+1", 0);
            }
            case 118 -> check("List a wanted item", s != null && s.press("List it", 0));
            case 130 -> check("Wanted listing shows up", ClientState.shop != null && ClientState.shop.listings().size() == 2);
            case 132 -> {
                check("Click a listing card", s != null && ClientState.shop != null && s.press("card:" + ClientState.shop.listings().getFirst().id(), 0));
                check("Manage popup opens", s != null && s.popupName().equals("manage"));
            }
            case 140 -> Screenshot.grab(mc, false);
            case 142 -> {
                if (s != null) s.press("+10", 0);
            }
            case 144 -> check("Add to stock", s != null && s.press("Add 10 to stock", 0));
            case 160 -> {
                check("Stock went up by 10", ClientState.shop != null && ClientState.shop.listings().getFirst().stock() == 33);
                if (s != null) s.press("Done", 0);
            }
            case 162 -> {
                check("Rename button", s != null && s.press("Rename", 0));
            }
            case 163 -> {
                if (s != null) s.typeText("!");
            }
            case 164 -> check("Save rename", s != null && s.press("Save", 0));
            case 180 -> check("Name changed", ClientState.shop != null && ClientState.shop.name().endsWith("!"));
            case 182 -> Screenshot.grab(mc, false);
            case 184 -> check("Back to shops", s != null && s.press("← Shops", 0));
            case 200 -> {
                check("Shop list has my shop", menu(mc) != null && ClientState.shopList != null && ClientState.shopList.shops().size() == 1);
                Screenshot.grab(mc, false);
            }
            case 205 -> check("Travel tab", menu(mc) != null && menu(mc).press("tab:TRAVEL"));
            case 220 -> Screenshot.grab(mc, false);
            case 225 -> check("Profile tab", menu(mc) != null && menu(mc).press("tab:PROFILE"));
            case 240 -> Screenshot.grab(mc, false);
            case 241 -> mc.gui.setScreen(new MenuScreen("news"));
            case 243 -> Screenshot.grab(mc, false);
            case 244 -> mc.gui.setScreen(new MenuScreen("abilities"));
            case 246 -> Screenshot.grab(mc, false);
            case 247 -> mc.gui.setScreen(new MenuScreen("classes"));
            case 249 -> Screenshot.grab(mc, false);
            case 250 -> {
                AbpsClient.send("shop", "close");
                c.sendCommand("abps delhome base");
                dev.abps.AbpsMod.LOGGER.info("[UiTest] Done: {} passed, {} failed", passed, failed);
            }
            case 275 -> mc.stop();
            default -> {
            }
        }
    }
}
