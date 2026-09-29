package dev.abps.client;

import dev.abps.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Lets you drag the HUD panel wherever you want it. */
public final class HudEditScreen extends Screen {

    private boolean dragging;
    private int grabX, grabY;

    public HudEditScreen() {
        super(Component.literal("Move HUD"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float scale() {
        return Math.max(0.5f, Math.min(1.5f, ClientPrefs.get().hudScale));
    }

    private int[] pos() {
        return Hud.position(ClientPrefs.get(), scale(), width, height);
    }

    private int panelHeight() {
        return 84 + (ClientState.combatLeft() > 0 ? 12 : 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x90000000);
        Net.SyncPayload s = ClientState.sync;
        Net.ClassInfo c = ClientState.myClass();
        float scale = scale();
        int[] p = pos();
        if (s != null && c != null) {
            g.pose().pushMatrix();
            g.pose().scale(scale, scale);
            Hud.panel(g, s, c, p[0], p[1]);
            g.pose().popMatrix();
            int x0 = (int) (p[0] * scale), y0 = (int) (p[1] * scale);
            int w = (int) (Hud.WIDTH * scale), h = (int) (panelHeight() * scale);
            boolean hover = Draw.inside(mx, my, x0, y0, w, h);
            g.outline(x0 - 1, y0 - 1, w + 2, h + 2, dragging ? 0xFFFFFFFF : hover ? 0xFFB0BEC5 : 0x80FFFFFF);
        } else {
            Draw.centered(g, "<gray>Your attribute is still loading.", width / 2, height / 2);
        }
        Draw.centered(g, "<bold><white>Drag the panel anywhere you like", width / 2, height / 2 - 30);
        Draw.centered(g, "<gray>Press <white>Esc</white> or click Done to save.", width / 2, height / 2 - 18);
        button(g, mx, my, width / 2 - 62, height / 2 - 2, 60, 18, "<white><bold>Done", 0x69F0AE);
        button(g, mx, my, width / 2 + 2, height / 2 - 2, 60, 18, "<white>Reset", 0xFF5252);
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, int color) {
        boolean hover = Draw.inside(mx, my, x, y, w, h);
        Draw.framed(g, x, y, w, h, Draw.argb(color, hover ? 0x80 : 0x40), Draw.opaque(color));
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0 && e.button() != 1) return super.mouseClicked(e, doubleClick);
        double mx = e.x(), my = e.y();
        if (Draw.inside(mx, my, width / 2 - 62, height / 2 - 2, 60, 18)) {
            onClose();
            return true;
        }
        if (Draw.inside(mx, my, width / 2 + 2, height / 2 - 2, 60, 18)) {
            ClientPrefs prefs = ClientPrefs.get();
            prefs.hudX = -1;
            prefs.hudY = -1;
            prefs.save();
            return true;
        }
        float scale = scale();
        int[] p = pos();
        if (Draw.inside(mx, my, (int) (p[0] * scale), (int) (p[1] * scale), (int) (Hud.WIDTH * scale), (int) (panelHeight() * scale))) {
            dragging = true;
            grabX = (int) (mx / scale) - p[0];
            grabY = (int) (my / scale) - p[1];
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!dragging) return super.mouseDragged(e, dx, dy);
        float scale = scale();
        int maxX = Math.max(0, (int) (width / scale) - Hud.WIDTH);
        int maxY = Math.max(0, (int) (height / scale) - 96);
        ClientPrefs prefs = ClientPrefs.get();
        prefs.hudX = Math.max(0, Math.min(maxX, (int) (e.x() / scale) - grabX));
        prefs.hudY = Math.max(0, Math.min(maxY, (int) (e.y() / scale) - grabY));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging) {
            dragging = false;
            ClientPrefs.get().save();
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public void onClose() {
        ClientPrefs.get().save();
        Minecraft.getInstance().gui.setScreen(null);
    }
}
