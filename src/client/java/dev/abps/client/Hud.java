package dev.abps.client;

import dev.abps.net.Net;
import dev.abps.util.Text;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The always-on panel with your class, level, abilities and ultimate. Also draws banners and screen tints. */
public final class Hud implements HudElement {

    public static final int WIDTH = 132;
    private static final String[] KEY_FALLBACK = {"R", "C", "V", "G", "Z"};

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        overlays(g);
        Net.SyncPayload s = ClientState.sync;
        Net.ClassInfo c = ClientState.myClass();
        if (s != null && c != null && s.hud() && !(mc.gui.screen() instanceof MenuScreen) && !(mc.gui.screen() instanceof RollScreen)) {
            ClientPrefs prefs = ClientPrefs.get();
            float scale = Math.max(0.5f, Math.min(1.5f, prefs.hudScale));
            g.pose().pushMatrix();
            int x = prefs.hudOnRight ? (int) (g.guiWidth() / scale) - WIDTH - 4 : 4;
            g.pose().scale(scale, scale);
            panel(g, s, c, x, 4);
            g.pose().popMatrix();
        }
        banners(g);
    }

    public static String keyLabel(int idx) {
        var key = AbpsClient.ABILITY_KEYS[idx - 1];
        if (key == null) return KEY_FALLBACK[idx - 1];
        String name = key.getTranslatedKeyMessage().getString();
        return name.length() > 3 ? name.substring(0, 3) : name;
    }

    /** Draws the whole panel. Also used by the menu as a preview. */
    public static int panel(GuiGraphicsExtractor g, Net.SyncPayload s, Net.ClassInfo c, int x, int y) {
        int c1 = c.color(), c2 = c.color2();
        int h = 84;
        long combat = ClientState.combatLeft();
        if (combat > 0) h += 12;

        // Background with a glowing top edge in the class colors
        Draw.panel(g, x, y, WIDTH, h, Draw.PANEL);
        Draw.hGradient(g, x + 1, y, WIDTH - 2, 1, Draw.opaque(c1), Draw.opaque(c2));
        // Full width: the panel only has cut corners on its very first row, so the glow must reach both edges below it
        Draw.hGradient(g, x, y + 1, WIDTH, 6, Draw.argb(c1, 0x40), Draw.argb(c2, 0x10));

        // Header: icon, class name, level
        Draw.item(g, c.icon(), x + 4, y + 4, 0.625f); // 10px so it sits level with the name and clear of the level bar
        Draw.text(g, "<bold>" + Draw.gradient(c1, c2, c.name()) + "</bold>", x + 19, y + 5);
        boolean max = s.level() >= s.maxLevel();
        String lv = max ? "<gradient:#FFD54F:#FF8F00><bold>MAX</bold></gradient>" : "<gray>Lv</gray> <white>" + s.level() + "</white>";
        int lvW = Draw.font().width(Text.mm(lv));
        Draw.text(g, lv, x + WIDTH - 5 - lvW, y + 5);

        // Level bar
        float progress = (float) s.level() / Math.max(1, s.maxLevel());
        Draw.bar(g, x + 5, y + 16, WIDTH - 10, 3, progress, c1, c2);

        // Ability slots
        int slotY = y + 23;
        for (int i = 1; i <= 4; i++) {
            int sx = x + 5 + (i - 1) * 31;
            slot(g, s, c, i, sx, slotY, 28);
        }

        // Ultimate bar
        int uy = slotY + 38;
        ultimate(g, s, c, x + 5, uy, WIDTH - 10);

        if (combat > 0) {
            int cy = y + 84;
            float pulse = Draw.pulse(1.5f);
            Draw.panel(g, x + 5, cy - 1, WIDTH - 10, 10, Draw.argb(0xFF1744, (int) (0x30 + 0x30 * pulse)));
            Draw.centered(g, "<#FF5252>⚔ In combat</#FF5252> <white>" + Text.time(combat) + "</white>", x + WIDTH / 2, cy);
        }
        return h;
    }

    private static void slot(GuiGraphicsExtractor g, Net.SyncPayload s, Net.ClassInfo c, int i, int x, int y, int size) {
        boolean unlocked = ClientState.unlocked(i);
        long left = s.noCooldown() ? 0 : ClientState.cooldownLeft(i);
        long total = Math.max(1, s.cdTotal()[i]);
        boolean ready = unlocked && left <= 0;

        int border = ready ? Draw.argb(Text.lerp(c.color(), c.color2(), (i - 1) / 3f), 0xFF) : 0xFF33333D;
        Draw.framed(g, x, y, size, size, 0xE0141420, border);
        Draw.item(g, Draw.abilityIcon(c.id(), i), x + (size - 16) / 2f, y + (size - 16) / 2f, 1f);

        if (!unlocked) {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xC0000000);
            Draw.centered(g, "<gray>🔒</gray>", x + size / 2, y + 5);
            Draw.centered(g, "<dark_gray>" + s.unlock()[i - 1] + "</dark_gray>", x + size / 2, y + 16);
        } else if (left > 0) {
            // Dark cover that shrinks as the cooldown runs out
            float frac = Math.min(1f, (float) left / total);
            int coverH = Math.round((size - 2) * frac);
            g.fill(x + 1, y + size - 1 - coverH, x + size - 1, y + size - 1, 0xB0000000);
            String sec = left >= 10_000 ? String.valueOf(left / 1000) : String.format("%.1f", left / 1000.0);
            Draw.centered(g, "<white><bold>" + sec + "</bold></white>", x + size / 2, y + size / 2 - 4);
        }
        // Key hint under the slot
        String key = keyLabel(i);
        Draw.centered(g, (ready ? "<white>" : "<dark_gray>") + key, x + size / 2, y + size + 2);
    }

    private static void ultimate(GuiGraphicsExtractor g, Net.SyncPayload s, Net.ClassInfo c, int x, int y, int w) {
        float charge = Math.min(1f, s.ultCharge());
        long lock = ClientState.ultLockLeft();
        boolean ready = charge >= 1f && lock <= 0;
        String name = c.abilityNames().size() >= 5 ? c.abilityNames().get(4) : "Ultimate";

        if (ready) {
            float p = Draw.pulse(1.2f);
            Draw.panel(g, x - 1, y - 1, w + 2, 9, Draw.argb(Text.lerp(c.color(), c.color2(), p), (int) (0x60 + 0x60 * p)));
        }
        g.fill(x, y, x + w, y + 7, 0xD0000000);
        int filled = Math.round((w - 2) * charge);
        if (filled > 0) {
            int from = ready ? Text.lerp(c.color(), 0xFFFFFF, Draw.pulse(1.2f) * 0.4f) : c.color();
            Draw.hGradient(g, x + 1, y + 1, filled, 5, Draw.opaque(from), Draw.opaque(c.color2()));
        }
        // Little ticks every 25%
        for (int t = 1; t < 4; t++) g.fill(x + w * t / 4, y + 1, x + w * t / 4 + 1, y + 6, 0x50FFFFFF);

        String label;
        if (lock > 0) label = "<gray>Ultimate recharging</gray> <white>" + Text.time(lock) + "</white>";
        else if (ready) label = "<bold>" + Draw.gradient(c.color(), c.color2(), "★ " + name.toUpperCase()) + "</bold> <white>[" + keyLabel(5) + "]</white>";
        else label = "<gray>" + name + "</gray> <white>" + Math.round(charge * 100) + "%</white>";
        Draw.scaled(g, label, x + w / 2f, y + 10, 0.75f, true);
    }

    // ---------------- Screen effects ----------------

    private static void overlays(GuiGraphicsExtractor g) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (ClientState.tintTicks > 0) {
            float life = (float) ClientState.tintTicks / Math.max(1, ClientState.tintTotal);
            int a = (int) (0xB0 * Math.min(1f, ClientState.tintStrength) * Math.min(1f, life * 2f));
            int col = ClientState.tintColor;
            int edge = Math.max(20, h / 5);
            g.fillGradient(0, 0, w, edge, Draw.argb(col, a), Draw.argb(col, 0));
            g.fillGradient(0, h - edge, w, h, Draw.argb(col, 0), Draw.argb(col, a));
            Draw.hGradient(g, 0, 0, edge, h, Draw.argb(col, a), Draw.argb(col, 0));
            Draw.hGradient(g, w - edge, 0, edge, h, Draw.argb(col, 0), Draw.argb(col, a));
        }
        if (ClientState.flashTicks > 0) {
            float life = (float) ClientState.flashTicks / Math.max(1, ClientState.flashTotal);
            int a = (int) (0xC0 * Math.min(1f, ClientState.flashStrength) * life * life);
            g.fill(0, 0, w, h, Draw.argb(ClientState.flashColor, a));
        }
    }

    private static void banners(GuiGraphicsExtractor g) {
        if (ClientState.banners.isEmpty()) return;
        ClientState.Banner b = ClientState.banners.getFirst();
        int age = ClientState.bannerAge;
        float in = Math.min(1f, age / 6f);
        float out = Math.min(1f, Math.max(0f, (b.ticks() + 20 - age) / 10f));
        float alpha = Math.min(in, out);
        if (alpha <= 0.02f) return;
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 4;
        int second = Text.lerp(b.color(), 0xFFFFFF, 0.55f);

        // Soft band behind the text
        int bandW = Math.max(200, Draw.font().width(Text.strip(b.title())) * 3 + 40);
        int a = (int) (0x90 * alpha);
        Draw.hGradient(g, cx - bandW / 2, y - 6, bandW / 2, 40, Draw.argb(0, 0), Draw.argb(0, a));
        Draw.hGradient(g, cx, y - 6, bandW / 2, 40, Draw.argb(0, a), Draw.argb(0, 0));
        int lineA = (int) (0xFF * alpha);
        Draw.hGradient(g, cx - bandW / 2, y - 6, bandW / 2, 1, Draw.argb(b.color(), 0), Draw.argb(b.color(), lineA));
        Draw.hGradient(g, cx, y - 6, bandW / 2, 1, Draw.argb(b.color(), lineA), Draw.argb(b.color(), 0));
        Draw.hGradient(g, cx - bandW / 2, y + 33, bandW / 2, 1, Draw.argb(b.color(), 0), Draw.argb(b.color(), lineA));
        Draw.hGradient(g, cx, y + 33, bandW / 2, 1, Draw.argb(b.color(), lineA), Draw.argb(b.color(), 0));

        // Title pops in a bit bigger then settles
        float pop = 2.4f + (1f - in) * 0.8f;
        String title = b.title().contains("<") ? b.title() : "<bold>" + Draw.gradient(b.color(), second, b.title()) + "</bold>";
        if (alpha > 0.1f) {
            Draw.scaled(g, title, cx, y, pop, true);
            if (!b.subtitle().isEmpty()) Draw.scaled(g, b.subtitle().contains("<") ? b.subtitle() : "<gray>" + b.subtitle(), cx, y + 22, 1f, true);
        }
    }
}
