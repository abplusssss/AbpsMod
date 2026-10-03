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
    private static final String[] KEY_FALLBACK = {"R", "C", "V", "G", "X", "Z"};

    private static long lastError;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        try {
            draw(g);
        } catch (Throwable t) {
            if (System.currentTimeMillis() - lastError > 10_000) {
                lastError = System.currentTimeMillis();
                dev.abps.AbpsMod.LOGGER.error("The HUD failed to draw", t);
            }
        }
    }

    private void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        overlays(g);
        Net.SyncPayload s = ClientState.sync;
        Net.ClassInfo c = ClientState.myClass();
        if (s != null && c != null && s.hud() && !(mc.gui.screen() instanceof MenuScreen) && !(mc.gui.screen() instanceof RollScreen)) {
            ClientPrefs prefs = ClientPrefs.get();
            float scale = Math.max(0.5f, Math.min(1.5f, prefs.hudScale));
            g.pose().pushMatrix();
            int[] pos = position(prefs, scale, g.guiWidth(), g.guiHeight());
            g.pose().scale(scale, scale);
            panel(g, s, c, pos[0], pos[1]);
            g.pose().popMatrix();
        }
        if (!(mc.gui.screen() instanceof MenuScreen)) {
            dungeon(g);
            party(g);
        }
        banners(g);
    }

    // ---------------- Party panel ----------------

    /** Your party's health bars, in the corner opposite the HUD panel (below the run panel when one is up). */
    private static void party(GuiGraphicsExtractor g) {
        Net.PartyPayload p = ClientState.party;
        if (p == null || p.names().isEmpty()) return;
        ClientPrefs prefs = ClientPrefs.get();
        int w = 110, row = 13, hh = 14 + p.names().size() * row;
        int x = prefs.hudOnRight ? 4 : g.guiWidth() - w - 4;
        int y = (g.guiWidth() < 182 + 2 * (RUN_WIDTH + 8) ? 44 : 4) + (ClientState.dungeonHud != null ? 56 : 0);
        int c1 = 0x00E5FF;
        Draw.window(g, x, y, w, hh, c1, Text.lerp(c1, 0x000000, 0.45f), 0xE80E0D14);
        Draw.text(g, "<bold>" + Draw.gradient(c1, 0xFFFFFF, "Party") + "</bold>", x + 5, y + 4);
        for (int i = 0; i < p.names().size(); i++) {
            int yy = y + 14 + i * row;
            int hp = p.health().get(i), max = Math.max(1, p.max().get(i));
            float f = Math.max(0, Math.min(1, hp / (float) max));
            int col = f > 0.5f ? 0xFF69F0AE : f > 0.25f ? 0xFFFFD54F : 0xFFFF5252;
            Draw.textFit(g, "<white>" + p.names().get(i), x + 5, yy, 52);
            int bx = x + 60, bw = w - 66;
            g.fill(bx, yy + 2, bx + bw, yy + 6, 0xFF26262E);
            g.fill(bx, yy + 2, bx + (int) (bw * f), yy + 6, col);
        }
    }

    // ---------------- Dungeon run panel ----------------

    public static final int RUN_WIDTH = 150;

    /** The run panel: dungeon name, clock, rooms, what to do now and how many downs the party has left. */
    private static void dungeon(GuiGraphicsExtractor g) {
        Net.DungeonHudPayload h = ClientState.dungeonHud;
        if (h == null || !h.active()) return;
        ClientPrefs prefs = ClientPrefs.get();
        int w = RUN_WIDTH, hh = 50;
        // Top corner on the other side from the HUD panel. Boss bars sit in the top middle, so on narrow screens
        // where the two would touch, the panel moves down below two bars' worth of space.
        int x = prefs.hudOnRight ? 4 : g.guiWidth() - w - 4;
        int y = g.guiWidth() < 182 + 2 * (w + 8) ? 44 : 4;
        int c1 = h.color() == 0 ? 0xB388FF : h.color(), c2 = Text.lerp(c1, 0xFFFFFF, 0.5f);
        Draw.window(g, x, y, w, hh, c1, Text.lerp(c1, 0x000000, 0.45f), 0xE80E0D14);
        Draw.hGradient(g, x + 1, y + 2, w - 2, 6, Draw.argb(c1, 0x34), Draw.argb(c2, 0x06));

        String clock = "<white>" + Text.time(ClientState.dungeonElapsed());
        int clockW = Draw.font().width(Text.mm(clock));
        Draw.textFit(g, "<bold>" + Draw.gradient(c1, c2, h.name()) + "</bold>", x + 5, y + 5, w - 14 - clockW);
        Draw.text(g, clock, x + w - 5 - clockW, y + 5);

        int by = y + 17;
        if (h.downsLeft() < 0 && h.rooms() == 0) {
            Draw.text(g, "<gray>Party game", x + 5, by);
        } else if (h.wave() > 0 || h.rooms() <= 1) {
            Draw.text(g, "<gray>Wave</gray> <gold><bold>" + Math.max(1, h.wave()) + "</bold></gold>", x + 5, by);
        } else {
            // One small block per room: done, current, still ahead
            int n = Math.max(1, h.rooms()), gap = 2;
            int bw = Math.max(3, (w - 10 - 40 - gap * (n - 1)) / n);
            for (int k = 0; k < n; k++) {
                int bx = x + 5 + k * (bw + gap);
                int col = k + 1 < h.room() ? Draw.opaque(c1) : k + 1 == h.room() ? Draw.opaque(Text.lerp(c1, 0xFFFFFF, Draw.pulse(1.2f) * 0.6f)) : 0xFF33333D;
                g.fill(bx, by + 2, bx + bw, by + 6, col);
            }
            String rooms = "<gray>" + h.room() + "/" + h.rooms();
            Draw.text(g, rooms, x + w - 5 - Draw.font().width(Text.mm(rooms)), by);
        }
        String[] lines = h.objective().split("[|]", 2);
        Draw.textFit(g, "<white>" + lines[0], x + 5, y + 28, w - 10);
        int downs = h.downsLeft();
        if (downs < 0) {
            // Party games put their own status on the second line
            if (lines.length > 1) Draw.textFit(g, lines[1], x + 5, y + 39, w - 10);
            return;
        }
        String d = downs == 0 ? "<#FF5252>No downs left, careful!" : "<#FF8A80>♥</#FF8A80> <gray>" + downs + " down" + (downs == 1 ? "" : "s") + " left";
        Draw.textFit(g, d, x + 5, y + 39, w - 10);
    }

    /** Where the panel is drawn, in the scaled coordinates the panel uses. Custom spot if it was dragged, otherwise a corner. */
    public static int[] position(ClientPrefs prefs, float scale, int guiWidth, int guiHeight) {
        int maxX = Math.max(0, (int) (guiWidth / scale) - WIDTH);
        int maxY = Math.max(0, (int) (guiHeight / scale) - 96);
        int x = prefs.hudX >= 0 ? prefs.hudX : prefs.hudOnRight ? maxX - 4 : 4;
        int y = prefs.hudY >= 0 ? prefs.hudY : 4;
        return new int[]{Math.max(0, Math.min(maxX, x)), Math.max(0, Math.min(maxY, y))};
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
        Draw.window(g, x, y, WIDTH, h, c1, c2, 0xE80E0D14);
        Draw.hGradient(g, x + 1, y + 2, WIDTH - 2, 6, Draw.argb(c1, 0x34), Draw.argb(c2, 0x0C));

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
        int count = ClientState.abilityCount(c);
        int size = count >= 5 ? 23 : 28;
        int step = count >= 5 ? 25 : 31;
        for (int i = 1; i <= count; i++) {
            int sx = x + 5 + (i - 1) * step;
            slot(g, s, c, i, sx, slotY, size);
        }

        // Ultimate bar
        int uy = slotY + 38;
        ultimate(g, s, c, x + 5, uy, WIDTH - 10);

        if (s.powersOff()) {
            // Everything greyed out with a label, so it's clear why nothing works
            g.fill(x + 1, y + 21, x + WIDTH - 1, y + 82, 0xC0101014);
            Draw.centered(g, "<gray>⏻ Powers off", x + WIDTH / 2, y + 40);
            Draw.scaled(g, "<dark_gray>Settings or !Powers on", x + WIDTH / 2f, y + 52, 0.75f, true);
        }
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
            Draw.centered(g, "<gray>🔒</gray>", x + size / 2, y + (size - 8) / 2);
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
        String name = c.abilityNames().size() >= 6 ? c.abilityNames().get(5) : "Ultimate";

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
        else if (ready) label = "<bold>" + Draw.gradient(c.color(), c.color2(), "★ " + name.toUpperCase()) + "</bold> <white>[" + keyLabel(ClientState.ULTIMATE) + "]</white>";
        else label = "<gray>" + name + "</gray> <white>" + Math.round(charge * 100) + "%</white>";
        Draw.scaled(g, label, x + w / 2f, y + 10, 0.75f, true);
    }

    // ---------------- Screen effects ----------------

    private static void overlays(GuiGraphicsExtractor g) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (ClientState.tintTicks > 0) {
            float life = (float) ClientState.tintTicks / Math.max(1, ClientState.tintTotal);
            int a = (int) (0x80 * Math.min(1f, ClientState.tintStrength) * Math.min(1f, life * 2f));
            int col = ClientState.tintColor;
            int edge = Math.max(20, h / 5);
            g.fillGradient(0, 0, w, edge, Draw.argb(col, a), Draw.argb(col, 0));
            g.fillGradient(0, h - edge, w, h, Draw.argb(col, 0), Draw.argb(col, a));
            Draw.hGradient(g, 0, 0, edge, h, Draw.argb(col, a), Draw.argb(col, 0));
            Draw.hGradient(g, w - edge, 0, edge, h, Draw.argb(col, 0), Draw.argb(col, a));
        }
        if (ClientState.flashTicks > 0) {
            float life = (float) ClientState.flashTicks / Math.max(1, ClientState.flashTotal);
            int a = (int) (0x60 * Math.min(1f, ClientState.flashStrength) * life * life);
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
