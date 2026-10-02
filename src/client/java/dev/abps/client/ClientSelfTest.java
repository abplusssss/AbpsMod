package dev.abps.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Development only. Run with ./gradlew runClient -Pclienttest while the dev server runs.
 * Joins, uses abilities, opens every screen and takes screenshots, then quits.
 */
public final class ClientSelfTest {

    private static int ticks = -1;

    private ClientSelfTest() {
    }

    public static void init() {
        if (!Boolean.getBoolean("abps.clienttest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand("abps " + c);
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc, false);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (ticks < 0) {
            if (ClientState.sync == null || ClientState.catalog.isEmpty()) return;
            ticks = 0;
        }
        ticks++;
        switch (ticks) {
            case 20 -> {
                cmd(mc, "giveattribute AbpsTester Necromancer");
                cmd(mc, "clearcombat AbpsTester");
            }
            case 40 -> cmd(mc, "giveupgrade AbpsTester 25");
            case 80 -> shot(mc);
            case 90 -> AbpsClient.cast(1);
            case 100 -> AbpsClient.cast(2);
            case 130 -> shot(mc);
            case 140 -> cmd(mc, "giveult AbpsTester");
            case 160 -> shot(mc);
            case 170 -> AbpsClient.cast(5);
            case 176 -> shot(mc);
            case 240 -> mc.gui.setScreen(new MenuScreen("overview"));
            case 260 -> shot(mc);
            case 270 -> mc.gui.setScreen(new MenuScreen("abilities"));
            case 290 -> shot(mc);
            case 300 -> mc.gui.setScreen(new MenuScreen("skills"));
            case 320 -> shot(mc);
            case 330 -> mc.gui.setScreen(new MenuScreen("classes"));
            case 350 -> shot(mc);
            case 360 -> mc.gui.setScreen(new MenuScreen("top"));
            case 390 -> shot(mc);
            case 400 -> mc.gui.setScreen(new MenuScreen("settings"));
            case 420 -> shot(mc);
            case 430 -> mc.gui.setScreen(new MenuScreen("confirm_reroll"));
            case 450 -> shot(mc);
            case 460 -> {
                mc.gui.setScreen(null);
                cmd(mc, "forceroll AbpsTester");
            }
            case 490 -> shot(mc);
            case 540 -> shot(mc);
            case 600 -> mc.stop();
            default -> {
            }
        }
    }
}
