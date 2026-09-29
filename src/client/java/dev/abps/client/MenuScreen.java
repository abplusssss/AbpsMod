package dev.abps.client;

import dev.abps.net.Net;
import dev.abps.util.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/** The !Menu screen. Everything here is drawn by hand so it matches the HUD. */
public final class MenuScreen extends Screen {

    private enum Tab {
        OVERVIEW("Overview", "minecraft:nether_star"),
        ABILITIES("Abilities", "minecraft:blaze_powder"),
        ROAD("Level Road", "minecraft:experience_bottle"),
        CLASSES("Attributes", "minecraft:book"),
        TOP("Top Players", "minecraft:totem_of_undying"),
        SETTINGS("Settings", "minecraft:comparator");

        final String label, icon;

        Tab(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private record Btn(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Btn> buttons = new ArrayList<>();
    private Tab tab = Tab.OVERVIEW;
    private String confirm = ""; // "upgrade" or "reroll" when a popup is open
    private double scroll;
    private int contentHeight;
    private int roadLevel = -1;
    private String classPick = "";
    private String boardFilter = "";
    private long openedAt = System.currentTimeMillis();

    // Layout, worked out in init()
    private int px, py, pw, ph, cx, cy, cw, ch;

    public MenuScreen(String tab) {
        super(Component.literal("AbpsMod"));
        switch (tab) {
            case "abilities" -> this.tab = Tab.ABILITIES;
            case "road" -> this.tab = Tab.ROAD;
            case "classes", "attributes" -> this.tab = Tab.CLASSES;
            case "top" -> this.tab = Tab.TOP;
            case "settings" -> this.tab = Tab.SETTINGS;
            case "confirm_upgrade" -> confirm = "upgrade";
            case "confirm_reroll" -> confirm = "reroll";
            default -> {
            }
        }
        if (ClientState.catalog.isEmpty()) AbpsClient.send("catalog", "");
        if (this.tab == Tab.TOP) AbpsClient.send("board", "");
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 420);
        ph = Math.min(height - 16, 260);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 96;
        cy = py + 30;
        cw = pw - 96 - 8;
        ch = ph - 30 - 8;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        boolean hover = enabled && Draw.inside(mx, my, x, y, w, h);
        int border = enabled ? Draw.opaque(hover ? Text.lerp(color, 0xFFFFFF, 0.35f) : color) : 0xFF3A3A44;
        int fill = enabled ? Draw.argb(color, hover ? 0x70 : 0x38) : 0xC0202028;
        Draw.framed(g, x, y, w, h, fill, border);
        Draw.centered(g, enabled ? label : "<dark_gray>" + Text.strip(label), x + w / 2, y + (h - 8) / 2);
        if (enabled) buttons.add(new Btn(x, y, w, h, action));
    }

    // ================= Drawing =================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        buttons.clear();
        Net.SyncPayload s = ClientState.sync;
        Net.ClassInfo c = ClientState.myClass();
        int c1 = c == null ? 0x7C4DFF : c.color(), c2 = c == null ? 0x00E5FF : c.color2();

        // Fade and slide in
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        g.pose().pushMatrix();
        g.pose().translate(0, (1 - open) * 10);

        // Main panel
        Draw.panel(g, px - 1, py - 1, pw + 2, ph + 2, Draw.argb(c1, 0x60));
        Draw.panel(g, px, py, pw, ph, 0xF00C0C12);
        Draw.hGradient(g, px + 1, py, pw - 2, 1, Draw.opaque(c1), Draw.opaque(c2));
        Draw.hGradient(g, px, py + 1, pw, 1, Draw.opaque(c1), Draw.opaque(c2));
        Draw.hGradient(g, px, py + 2, pw, 22, Draw.argb(c1, 0x30), Draw.argb(c2, 0x08));
        Draw.text(g, "<bold>" + Draw.gradient(c1, c2, "ABPS") + "</bold> <gray>Attributes</gray>", px + 8, py + 9);
        if (s != null && c != null) {
            String right = Draw.gradient(c1, c2, c.symbol() + " " + c.name()) + " <dark_gray>|</dark_gray> <white>Lv " + s.level() + "</white><gray>/" + s.maxLevel() + "</gray>";
            Draw.text(g, right, px + pw - 8 - Draw.font().width(Text.mm(right)), py + 9);
        }

        // Tabs down the left side
        int ty = py + 30;
        for (Tab t : Tab.values()) {
            boolean active = t == tab;
            boolean hover = Draw.inside(mx, my, px + 6, ty, 84, 22);
            if (active) {
                Draw.panel(g, px + 6, ty, 84, 22, Draw.argb(c1, 0x50));
                g.fill(px + 6, ty + 3, px + 8, ty + 19, Draw.opaque(c1));
            } else if (hover) {
                Draw.panel(g, px + 6, ty, 84, 22, 0x30FFFFFF);
            }
            String icon = t == Tab.OVERVIEW && c != null ? c.icon() : t.icon;
            Draw.item(g, icon, px + 11, ty + 3, 1f);
            Draw.text(g, (active ? "<white>" : "<gray>") + t.label, px + 30, ty + 7);
            final Tab target = t;
            buttons.add(new Btn(px + 6, ty, 84, 22, () -> switchTab(target)));
            ty += 25;
        }
        g.fill(px + 93, py + 30, px + 94, py + ph - 8, Draw.LINE);

        // Content
        if (s == null || !AbpsClient.connected()) {
            Draw.centered(g, "<gray>This server doesn't run AbpsMod, or it's still loading.", cx + cw / 2, cy + ch / 2 - 4);
        } else if (c == null && tab != Tab.CLASSES && tab != Tab.TOP && tab != Tab.SETTINGS) {
            Draw.centered(g, "<gray>Loading your attribute...", cx + cw / 2, cy + ch / 2 - 4);
        } else {
            g.enableScissor(cx, cy, cx + cw, cy + ch);
            int top = cy - (int) scroll;
            boolean popup = !confirm.isEmpty();
            int mmx = popup ? -1 : mx, mmy = popup ? -1 : my;
            contentHeight = switch (tab) {
                case OVERVIEW -> overview(g, mmx, mmy, s, c, top);
                case ABILITIES -> abilities(g, s, c, top);
                case ROAD -> road(g, mmx, mmy, s, c, top);
                case CLASSES -> classes(g, mmx, mmy, top);
                case TOP -> top(g, mmx, mmy, top);
                case SETTINGS -> settings(g, mmx, mmy, s, top);
            };
            g.disableScissor();
            // Scroll bar
            if (contentHeight > ch) {
                float frac = (float) ch / contentHeight;
                int barH = Math.max(12, (int) (ch * frac));
                int barY = cy + (int) ((ch - barH) * (scroll / Math.max(1, contentHeight - ch)));
                g.fill(px + pw - 5, cy, px + pw - 3, cy + ch, 0x30FFFFFF);
                g.fill(px + pw - 5, barY, px + pw - 3, barY + barH, Draw.opaque(c1));
            }
            // Buttons hidden by the scroll area should not be clickable
            buttons.removeIf(b -> b.x >= cx && (b.y + b.h <= cy || b.y >= cy + ch));
        }

        if (!confirm.isEmpty() && s != null && c != null) {
            buttons.clear();
            popup(g, mx, my, s, c);
        }
        g.pose().popMatrix();
    }

    private void switchTab(Tab t) {
        if (tab == t) return;
        tab = t;
        scroll = 0;
        if (t == Tab.TOP) AbpsClient.send("board", boardFilter);
    }

    private int overview(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, Net.ClassInfo c, int y0) {
        int x = cx + 6, y = y0 + 4;
        // Big glowing icon
        float p = Draw.pulse(0.5f);
        Draw.panel(g, x, y, 44, 44, Draw.argb(Text.lerp(c.color(), c.color2(), p), 0x40));
        Draw.framed(g, x + 2, y + 2, 40, 40, 0xE0101018, Draw.opaque(c.color()));
        Draw.item(g, c.icon(), x + 6, y + 6, 2f);
        Draw.scaled(g, "<bold>" + Draw.gradient(c.color(), c.color2(), c.name()) + "</bold>", x + 52, y + 2, 2f, false);
        Draw.wrapped(g, "<gray><italic>" + c.tagline(), x + 52, y + 22, cw - 66, Draw.MUTED);

        y += 52;
        boolean max = s.level() >= s.maxLevel();
        Draw.text(g, "<gray>Level</gray> <white><bold>" + s.level() + "</bold></white><gray> / " + s.maxLevel() + "</gray>"
                + (max ? "  <gradient:#FFD54F:#FF8F00><bold>MASTERED</bold></gradient>" : ""), x, y);
        y += 11;
        Draw.bar(g, x, y, cw - 16, 5, (float) s.level() / s.maxLevel(), c.color(), c.color2());
        // Marks where abilities unlock
        for (int u : s.unlock()) {
            int mxp = x + (int) ((cw - 16) * (u / (float) s.maxLevel()));
            g.fill(mxp, y - 1, mxp + 1, y + 6, u <= s.level() ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        y += 11;

        // Buttons
        int bw = (cw - 22) / 2;
        button(g, mx, my, x, y, bw, 18, max ? "Max level" : "⬆ Upgrade", 0x69F0AE, !max, () -> confirm = "upgrade");
        button(g, mx, my, x + bw + 6, y, bw, 18, "🎲 Reroll", 0xFF5252, true, () -> confirm = "reroll");
        y += 22;
        if (!max) Draw.text(g, "<gray>Next level: </gray>" + s.upgradeCost(), x, y);
        y += 14;

        // Stats
        Draw.text(g, "<gray>Abilities used:</gray> <white>" + s.abilitiesUsed() + "</white>   <gray>Rerolls:</gray> <white>" + s.rerolls()
                + "</white>   <gray>Players with this class:</gray> <white>" + c.players() + "</white>", x, y);
        y += 16;

        y = section(g, "Passives", c.color(), x, y);
        List<String> passives = c.passivesByLevel().get(Math.max(0, Math.min(c.passivesByLevel().size() - 1, s.level() - 1)));
        for (String line : passives) y += Draw.wrapped(g, "<#69F0AE>✔</#69F0AE> " + line, x, y, cw - 16, Draw.TEXT) + 1;
        y += 4;
        y = section(g, "Weaknesses", 0xFF5252, x, y);
        for (String line : c.negatives()) y += Draw.wrapped(g, "<#FF5252>✘</#FF5252> " + line, x, y, cw - 16, Draw.TEXT) + 1;
        y += 4;
        y = section(g, "Mastery (Level " + s.maxLevel() + ")", 0xFFD54F, x, y);
        y += Draw.wrapped(g, (max ? "<#FFD54F>★</#FFD54F> " : "<dark_gray>🔒</dark_gray> <gray>") + c.mastery(), x, y, cw - 16, Draw.TEXT);
        return y - y0 + 8;
    }

    private int section(GuiGraphicsExtractor g, String title, int color, int x, int y) {
        Draw.text(g, "<bold>" + Draw.gradient(color, Text.lerp(color, 0xFFFFFF, 0.5f), title) + "</bold>", x, y);
        int tw = Draw.font().width(Text.mm("<bold>" + title));
        Draw.hGradient(g, x + tw + 6, y + 4, cw - 22 - tw, 1, Draw.argb(color, 0x90), Draw.argb(color, 0));
        return y + 12;
    }

    private int abilities(GuiGraphicsExtractor g, Net.SyncPayload s, Net.ClassInfo c, int y0) {
        int x = cx + 6, y = y0 + 4;
        int lvlIdx = Math.max(0, Math.min(c.descsByLevel().size() - 1, s.level() - 1));
        for (int i = 1; i <= 5; i++) {
            boolean ult = i == 5;
            boolean unlocked = ClientState.unlocked(i);
            String desc = c.descsByLevel().get(lvlIdx).get(i - 1);
            int textW = cw - 16 - 34 - 6;
            int h = Math.max(34, 14 + Draw.wrappedHeight(desc, textW) + 6);
            int border = ult ? Text.lerp(c.color(), c.color2(), Draw.pulse(0.6f)) : unlocked ? c.color() : 0x3A3A44;
            Draw.framed(g, x, y, cw - 16, h, ult ? 0xE0181420 : 0xE0121218, Draw.argb(border, 0xFF));
            Draw.framed(g, x + 5, y + 5, 24, 24, 0xFF0A0A10, Draw.argb(border, 0xA0));
            Draw.item(g, Draw.abilityIcon(c.id(), i), x + 9, y + 9, 1f);
            String name = (ult ? "<bold>" + Draw.gradient(c.color(), c.color2(), "★ " + c.abilityNames().get(4)) + "</bold>"
                    : (unlocked ? "<white><bold>" : "<gray>") + c.abilityNames().get(i - 1));
            Draw.text(g, name, x + 34, y + 5);
            String meta = "<dark_gray>[</dark_gray><yellow>" + Hud.keyLabel(i) + "</yellow><dark_gray>]</dark_gray> ";
            if (ult) meta += "<gray>Charges by hitting players</gray>";
            else if (!unlocked) meta += "<red>Unlocks at level " + s.unlock()[i - 1] + "</red>";
            else meta += "<gray>" + Text.time(s.cdTotal()[i]) + " cooldown</gray>";
            int mw = Draw.font().width(Text.mm(meta));
            Draw.scaled(g, meta, x + cw - 20 - mw * 0.75f, y + 6, 0.75f, false);
            Draw.wrapped(g, desc, x + 34, y + 17, textW, unlocked ? Draw.MUTED : Draw.DIM);
            y += h + 4;
        }
        Draw.wrapped(g, "<dark_gray>Change keys in Options > Controls > Key Binds > AbpsMod.", x, y + 2, cw - 16, Draw.DIM);
        return y - y0 + 18;
    }

    private int road(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, Net.ClassInfo c, int y0) {
        if (roadLevel < 1) roadLevel = Math.min(s.maxLevel(), s.level() + 1);
        int x = cx + 6, y = y0 + 4;
        int listW = 104;
        // Level list on the left
        for (int lvl = 1; lvl <= s.maxLevel(); lvl++) {
            boolean reached = lvl <= s.level();
            boolean current = lvl == s.level();
            boolean picked = lvl == roadLevel;
            int unlockIdx = -1;
            for (int u = 0; u < 4; u++) if (s.unlock()[u] == lvl) unlockIdx = u + 1;
            boolean hover = Draw.inside(mx, my, x, y, listW, 16);
            int fill = picked ? Draw.argb(c.color(), 0x50) : hover ? 0x30FFFFFF : 0x60000000;
            Draw.panel(g, x, y, listW, 16, fill);
            // Node on the "road"
            int dot = reached ? Draw.opaque(Text.lerp(c.color(), c.color2(), lvl / (float) s.maxLevel())) : 0xFF44444E;
            g.fill(x + 4, y + 4, x + 12, y + 12, dot);
            if (current) g.outline(x + 2, y + 2, 12, 12, 0xFFFFFFFF);
            String label = (reached ? "<white>" : "<gray>") + "Level " + lvl;
            if (unlockIdx > 0) label += " <yellow>✦</yellow>";
            if (lvl == s.maxLevel()) label += " <gold>★</gold>";
            else if (lvl % 5 == 0) label += " <aqua>◆</aqua>";
            Draw.text(g, label, x + 17, y + 4);
            final int target = lvl;
            buttons.add(new Btn(x, y, listW, 16, () -> roadLevel = target));
            y += 18;
        }
        int listH = y - y0;

        // Details for the picked level on the right, pinned to the top of the view
        int dx = x + listW + 8, dw = cw - 16 - listW - 8;
        int dy = cy + 4;
        int lvlIdx = roadLevel - 1;
        g.disableScissor();
        g.enableScissor(dx - 2, cy, cx + cw, cy + ch);
        Draw.framed(g, dx, dy, dw, ch - 8, 0xE0101016, Draw.argb(c.color(), 0x80));
        int yy = dy + 5;
        Draw.text(g, "<bold>" + Draw.gradient(c.color(), c.color2(), "Level " + roadLevel) + "</bold>"
                + (roadLevel <= s.level() ? " <#69F0AE>✔ reached</#69F0AE>" : " <gray>locked</gray>"), dx + 5, yy);
        yy += 12;
        for (int u = 0; u < 4; u++) {
            if (s.unlock()[u] == roadLevel) {
                yy += Draw.wrapped(g, "<yellow>✦ New ability:</yellow> <white>" + c.abilityNames().get(u), dx + 5, yy, dw - 10, Draw.TEXT) + 2;
            }
        }
        if (roadLevel == s.maxLevel()) yy += Draw.wrapped(g, "<gold>★ Mastery:</gold> " + c.mastery(), dx + 5, yy, dw - 10, Draw.TEXT) + 2;
        if (roadLevel % 5 == 0 && roadLevel != s.maxLevel()) yy += Draw.wrapped(g, "<aqua>◆ Milestone level</aqua> <gray>(costs a bit more)", dx + 5, yy, dw - 10, Draw.TEXT) + 2;
        yy += 2;
        Draw.text(g, "<gray>Passives at this level:", dx + 5, yy);
        yy += 11;
        List<String> now = c.passivesByLevel().get(lvlIdx);
        for (String line : now) {
            if (yy > dy + ch - 20) break;
            yy += Draw.wrapped(g, "<#69F0AE>•</#69F0AE> " + line, dx + 5, yy, dw - 10, Draw.MUTED) + 1;
        }
        g.disableScissor();
        g.enableScissor(cx, cy, cx + cw, cy + ch);
        return listH + 4;
    }

    private int classes(GuiGraphicsExtractor g, int mx, int my, int y0) {
        if (ClientState.catalog.isEmpty()) {
            Draw.centered(g, "<gray>Loading...", cx + cw / 2, y0 + 20);
            return 40;
        }
        if (classPick.isEmpty()) classPick = ClientState.sync != null && !ClientState.sync.classId().isEmpty()
                ? ClientState.sync.classId() : ClientState.catalog.keySet().iterator().next();
        int x = cx + 6, y = y0 + 4;
        int listW = 104;
        for (Net.ClassInfo info : ClientState.catalog.values()) {
            boolean picked = info.id().equals(classPick);
            boolean hover = Draw.inside(mx, my, x, y, listW, 20);
            Draw.panel(g, x, y, listW, 20, picked ? Draw.argb(info.color(), 0x55) : hover ? 0x30FFFFFF : 0x60000000);
            if (picked) g.fill(x, y + 3, x + 2, y + 17, Draw.opaque(info.color()));
            Draw.item(g, info.icon(), x + 4, y + 2, 1f);
            Draw.text(g, Draw.gradient(info.color(), info.color2(), info.name()), x + 23, y + 6);
            final String id = info.id();
            buttons.add(new Btn(x, y, listW, 20, () -> classPick = id));
            y += 22;
        }
        int listH = y - y0;

        Net.ClassInfo info = ClientState.catalog.get(classPick);
        if (info == null) return listH;
        int dx = x + listW + 8, dw = cw - 16 - listW - 8;
        int yy = y0 + 4;
        Draw.item(g, info.icon(), dx, yy, 1.5f);
        Draw.scaled(g, "<bold>" + Draw.gradient(info.color(), info.color2(), info.name()) + "</bold>", dx + 28, yy + 1, 1.5f, false);
        Draw.scaled(g, "<gray>" + info.players() + " player" + (info.players() == 1 ? "" : "s") + " have this", dx + 28, yy + 15, 0.75f, false);
        yy += 28;
        yy += Draw.wrapped(g, "<gray><italic>" + info.tagline(), dx, yy, dw, Draw.MUTED) + 4;
        Draw.text(g, "<bold><white>Abilities", dx, yy);
        yy += 11;
        for (int i = 1; i <= 5; i++) {
            Draw.item(g, Draw.abilityIcon(info.id(), i), dx, yy - 2, 0.6f);
            String n = i == 5 ? "<gold>★ " + info.abilityNames().get(4) + "</gold> <dark_gray>(ultimate)" : "<white>" + info.abilityNames().get(i - 1);
            yy += Draw.wrapped(g, n, dx + 12, yy, dw - 12, Draw.TEXT) + 1;
        }
        yy += 4;
        Draw.text(g, "<bold><#69F0AE>Passives</#69F0AE></bold> <dark_gray>(at level 1)", dx, yy);
        yy += 11;
        for (String line : info.passivesByLevel().getFirst()) yy += Draw.wrapped(g, "<#69F0AE>•</#69F0AE> " + line, dx, yy, dw, Draw.MUTED) + 1;
        yy += 4;
        Draw.text(g, "<bold><#FF5252>Weaknesses", dx, yy);
        yy += 11;
        for (String line : info.negatives()) yy += Draw.wrapped(g, "<#FF5252>•</#FF5252> " + line, dx, yy, dw, Draw.MUTED) + 1;
        yy += 4;
        yy += Draw.wrapped(g, "<gold>★ Mastery:</gold> " + info.mastery(), dx, yy, dw, Draw.MUTED);
        return Math.max(listH, yy - y0) + 8;
    }

    private int top(GuiGraphicsExtractor g, int mx, int my, int y0) {
        int x = cx + 6, y = y0 + 4;
        // Filter chips: all, then one per class
        int fx = x;
        boolean allOn = boardFilter.isEmpty();
        button(g, mx, my, fx, y, 28, 18, allOn ? "<white><bold>All" : "<gray>All", 0x7C4DFF, true, () -> filter(""));
        fx += 30;
        for (Net.ClassInfo info : ClientState.catalog.values()) {
            boolean on = info.id().equals(boardFilter);
            boolean hover = Draw.inside(mx, my, fx, y, 18, 18);
            Draw.framed(g, fx, y, 18, 18, on ? Draw.argb(info.color(), 0x70) : hover ? 0x40FFFFFF : 0xC0101016,
                    on ? Draw.opaque(info.color()) : 0xFF33333D);
            Draw.item(g, info.icon(), fx + 1, y + 1, 1f);
            final String id = info.id();
            buttons.add(new Btn(fx, y, 18, 18, () -> filter(id)));
            if (hover) g.setTooltipForNextFrame(Text.mm(Draw.gradient(info.color(), info.color2(), info.name())), mx, my);
            fx += 20;
            if (fx + 18 > cx + cw - 6) {
                fx = x + 30;
                y += 20;
            }
        }
        y += 26;

        Net.BoardPayload board = ClientState.board;
        if (board == null || !board.filter().equals(boardFilter)) {
            Draw.centered(g, "<gray>Loading...", cx + cw / 2, y + 10);
            return y - y0 + 30;
        }
        if (board.entries().isEmpty()) {
            Draw.centered(g, "<gray>Nobody here yet.", cx + cw / 2, y + 10);
            return y - y0 + 30;
        }
        int rank = 1;
        String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().name();
        for (Net.BoardEntry e : board.entries()) {
            Net.ClassInfo info = ClientState.catalog.get(e.classId());
            int col = info == null ? 0xAAAAAA : info.color();
            int col2 = info == null ? 0xFFFFFF : info.color2();
            boolean mine = e.name().equals(me);
            Draw.panel(g, x, y, cw - 16, 20, mine ? Draw.argb(col, 0x45) : 0x70000000);
            String medal = switch (rank) {
                case 1 -> "<gradient:#FFE082:#FFB300><bold>#1</bold></gradient>";
                case 2 -> "<gradient:#FFFFFF:#B0BEC5><bold>#2</bold></gradient>";
                case 3 -> "<gradient:#FFAB91:#BF360C><bold>#3</bold></gradient>";
                default -> "<gray>#" + rank;
            };
            Draw.text(g, medal, x + 5, y + 6);
            if (info != null) Draw.item(g, info.icon(), x + 26, y + 2, 1f);
            Draw.text(g, "<white>" + e.name() + (mine ? " <gray>(you)" : ""), x + 46, y + 6);
            String right = (info == null ? "" : Draw.gradient(col, col2, info.name()) + " ") + "<white>Lv " + e.level();
            Draw.text(g, right, x + cw - 22 - Draw.font().width(Text.mm(right)), y + 6);
            y += 22;
            rank++;
        }
        return y - y0 + 4;
    }

    private void filter(String id) {
        boardFilter = id;
        ClientState.board = null;
        AbpsClient.send("board", id);
    }

    private int settings(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, int y0) {
        ClientPrefs prefs = ClientPrefs.get();
        int x = cx + 6, y = y0 + 4;
        y = section(g, "Display", 0x00E5FF, x, y);
        y = toggle(g, mx, my, x, y, "Show the ability HUD", s.hud(), () -> AbpsClient.send("toggle_hud", ""));
        y = toggle(g, mx, my, x, y, "HUD on the right side", prefs.hudOnRight, () -> prefs.hudOnRight = !prefs.hudOnRight);
        y = toggle(g, mx, my, x, y, "Big banners for rolls and ultimates", prefs.showBanners, () -> prefs.showBanners = !prefs.showBanners);
        Draw.text(g, "<white>HUD size", x, y + 5);
        float[] sizes = {0.75f, 1f, 1.25f};
        int bx = x + cw - 16 - 3 * 34;
        for (float size : sizes) {
            boolean on = Math.abs(prefs.hudScale - size) < 0.01f;
            button(g, mx, my, bx, y, 32, 16, (on ? "<white><bold>" : "<gray>") + Math.round(size * 100) + "%", on ? 0x00E5FF : 0x55555F, true,
                    () -> {
                        prefs.hudScale = size;
                        prefs.save();
                    });
            bx += 34;
        }
        y += 22;
        y = section(g, "Effects", 0xFF4081, x, y + 4);
        y = toggle(g, mx, my, x, y, "Screen shake", prefs.screenShake, () -> prefs.screenShake = !prefs.screenShake);
        y = toggle(g, mx, my, x, y, "Screen tints and flashes", prefs.screenTint, () -> prefs.screenTint = !prefs.screenTint);
        y = section(g, "Chat panel", 0x69F0AE, x, y + 4);
        y = toggle(g, mx, my, x, y, "Action bar info (for players without the mod)", s.panel(), () -> AbpsClient.send("toggle_panel", ""));
        y += 4;
        Draw.wrapped(g, "<dark_gray>These only change your own screen. Keys are in Options > Controls > Key Binds.", x, y, cw - 16, Draw.DIM);
        return y - y0 + 24;
    }

    private int toggle(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, boolean on, Runnable flip) {
        int w = cw - 16;
        boolean hover = Draw.inside(mx, my, x, y, w, 18);
        Draw.panel(g, x, y, w, 18, hover ? 0x40FFFFFF : 0x60000000);
        Draw.text(g, "<white>" + label, x + 6, y + 5);
        int sx = x + w - 30;
        Draw.panel(g, sx, y + 4, 24, 10, on ? 0xFF2E7D5B : 0xFF3A3A44);
        int knob = on ? sx + 14 : sx + 1;
        Draw.panel(g, knob, y + 5, 9, 8, on ? 0xFF69F0AE : 0xFF9E9EA8);
        buttons.add(new Btn(x, y, w, 18, () -> {
            flip.run();
            ClientPrefs.get().save();
        }));
        return y + 20;
    }

    private void popup(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, Net.ClassInfo c) {
        g.fill(px, py, px + pw, py + ph, 0xA0000000);
        boolean reroll = confirm.equals("reroll");
        int w = Math.min(pw - 40, 260), h = reroll ? 118 : 100;
        int x = px + (pw - w) / 2, y = py + (ph - h) / 2;
        int col = reroll ? 0xFF5252 : 0x69F0AE;
        Draw.panel(g, x - 1, y - 1, w + 2, h + 2, Draw.argb(col, 0x90));
        Draw.panel(g, x, y, w, h, 0xF8101016);
        int col2 = Text.lerp(col, 0xFFFFFF, 0.5f);
        Draw.hGradient(g, x + 1, y, w - 2, 1, Draw.opaque(col), Draw.opaque(col2));
        Draw.hGradient(g, x, y + 1, w, 1, Draw.opaque(col), Draw.opaque(col2));
        int yy = y + 8;
        if (reroll) {
            Draw.scaled(g, "<bold><gradient:#FF5252:#FFAB40>Reroll your attribute?</gradient></bold>", x + w / 2f, yy, 1.25f, true);
            yy += 16;
            yy += Draw.wrapped(g, "<gray>You get a random new attribute. <red><bold>You lose all " + s.level() + " levels.</bold></red>", x + 10, yy, w - 20, Draw.TEXT) + 4;
            Draw.text(g, "<gray>Price: </gray>" + s.rerollCost(), x + 10, yy);
            yy += 12;
            if (!s.canReroll()) Draw.text(g, "<red>You can't afford this yet.", x + 10, yy);
        } else {
            Draw.scaled(g, "<bold>" + Draw.gradient(c.color(), c.color2(), "Upgrade to level " + (s.level() + 1) + "?") + "</bold>", x + w / 2f, yy, 1.25f, true);
            yy += 18;
            Draw.text(g, "<gray>Price: </gray>" + s.upgradeCost(), x + 10, yy);
            yy += 12;
            for (int u = 0; u < 4; u++) {
                if (s.unlock()[u] == s.level() + 1) {
                    Draw.text(g, "<yellow>✦ Unlocks " + c.abilityNames().get(u), x + 10, yy);
                    yy += 11;
                }
            }
            if (!s.canUpgrade()) Draw.text(g, "<red>You can't afford this yet.", x + 10, yy);
        }
        int by = y + h - 26, bw = (w - 30) / 2;
        boolean can = reroll ? s.canReroll() : s.canUpgrade();
        button(g, mx, my, x + 10, by, bw, 18, reroll ? "<white><bold>Reroll" : "<white><bold>Upgrade", col, can, () -> {
            AbpsClient.send(reroll ? "reroll" : "upgrade", "");
            confirm = "";
            if (reroll) onClose();
        });
        button(g, mx, my, x + 20 + bw, by, bw, 18, "Cancel", 0x777781, true, () -> confirm = "");
    }

    // ================= Input =================

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) {
            for (Btn b : List.copyOf(buttons)) {
                if (Draw.inside(e.x(), e.y(), b.x, b.y, b.w, b.h)) {
                    click();
                    b.action.run();
                    return true;
                }
            }
            // Clicking outside the popup closes it
            if (!confirm.isEmpty()) {
                confirm = "";
                return true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!confirm.isEmpty()) return true;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - ch), scroll - sy * 16));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (AbpsClient.menuKey.matches(e) || Minecraft.getInstance().options.keyInventory.matches(e)) {
            onClose();
            return true;
        }
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && !confirm.isEmpty()) {
            confirm = "";
            return true;
        }
        return super.keyPressed(e);
    }
}
