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
import java.util.Collections;
import java.util.List;

/** The !Menu screen. Everything here is drawn by hand so it matches the HUD. */
public final class MenuScreen extends Screen {

    private enum Tab {
        OVERVIEW("Overview", "minecraft:nether_star"),
        ABILITIES("Abilities", "minecraft:blaze_powder"),
        ROAD("Level Road", "minecraft:experience_bottle"),
        CLASSES("Attributes", "minecraft:book"),
        TOP("Top Players", "minecraft:totem_of_undying"),
        SHOPS("Shops", "minecraft:emerald"),
        TRAVEL("Travel", "minecraft:ender_pearl"),
        PROFILE("Profile", "minecraft:name_tag"),
        NEWS("What's New", "minecraft:writable_book"),
        KEYBINDS("Keybinds", "minecraft:tripwire_hook"),
        SETTINGS("Settings", "minecraft:comparator"),
        ADMIN("Admin", "minecraft:command_block"); // only shown to operators

        final String label, icon;

        Tab(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private record Btn(int x, int y, int w, int h, String label, Runnable action) {
        Btn(int x, int y, int w, int h, Runnable action) {
            this(x, y, w, h, "", action);
        }
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

    // Admin tab state
    private String adminTarget = "@s";
    private String adminClass = "";
    private int adminLevel = 10;
    private String adminSent = "";
    private String pendingKey = "";
    private long pendingAt;

    // Layout, worked out in init() and layout()
    private int px, py, pw, ph, cx, cy, cw, ch;
    private int baseX, baseY;
    private double offX, offY;
    private boolean draggingWindow;

    public MenuScreen(String tab) {
        super(Component.literal("AbpsMod"));
        switch (tab) {
            case "abilities" -> this.tab = Tab.ABILITIES;
            case "news", "new" -> this.tab = Tab.NEWS;
            case "road" -> this.tab = Tab.ROAD;
            case "classes", "attributes" -> this.tab = Tab.CLASSES;
            case "top" -> this.tab = Tab.TOP;
            case "settings" -> this.tab = Tab.SETTINGS;
            case "keybinds", "keys" -> this.tab = Tab.KEYBINDS;
            case "admin" -> this.tab = Tab.ADMIN;
            case "shops", "shop" -> this.tab = Tab.SHOPS;
            case "travel" -> this.tab = Tab.TRAVEL;
            case "profile" -> this.tab = Tab.PROFILE;
            case "confirm_upgrade" -> confirm = "upgrade";
            case "confirm_reroll" -> confirm = "reroll";
            default -> {
            }
        }
        if (ClientState.catalog.isEmpty()) AbpsClient.send("catalog", "");
        if (this.tab == Tab.TOP) AbpsClient.send("board", "");
        request(this.tab);
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 420);
        ph = Math.min(height - 16, 260);
        baseX = (width - pw) / 2;
        baseY = (height - ph) / 2;
        layout();
    }

    /** Works out where everything goes. The window can be dragged by its title bar, which moves the offset. */
    private void layout() {
        px = Math.max(0, Math.min(width - pw, baseX + (int) Math.round(offX)));
        py = Math.max(0, Math.min(height - ph, baseY + (int) Math.round(offY)));
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
        if (enabled) buttons.add(new Btn(x, y, w, h, Text.strip(label), action));
    }

    // ================= Drawing =================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        layout();
        buttons.clear();
        shopName.hide();
        homeName.hide();
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
        boolean isAdmin = s != null && s.admin();
        int tabCount = Tab.values().length - (isAdmin ? 0 : 1);
        // Tabs shrink to fit the window, so every tab stays on screen even on short screens or big GUI scales
        int step = Math.max(11, Math.min(25, (ph - 36) / tabCount)), tabH = step - (step >= 17 ? 3 : 1);
        if (tab == Tab.ADMIN && !isAdmin) tab = Tab.OVERVIEW; // lost operator status while the menu was open
        for (Tab t : Tab.values()) {
            if (t == Tab.ADMIN && !isAdmin) continue;
            boolean active = t == tab;
            boolean hover = Draw.inside(mx, my, px + 6, ty, 84, tabH);
            if (active) {
                Draw.panel(g, px + 6, ty, 84, tabH, Draw.argb(c1, 0x50));
                g.fill(px + 6, ty + 3, px + 8, ty + tabH - 3, Draw.opaque(c1));
            } else if (hover) {
                Draw.panel(g, px + 6, ty, 84, tabH, 0x30FFFFFF);
            }
            String icon = t == Tab.OVERVIEW && c != null ? c.icon() : t.icon;
            float iconScale = tabH >= 20 ? 1f : tabH >= 14 ? 0.8f : 0.6f;
            Draw.item(g, icon, px + 11, ty + (tabH - 16 * iconScale) / 2f, iconScale);
            if (tabH >= 14) Draw.text(g, (active ? "<white>" : "<gray>") + t.label, px + 30, ty + (tabH - 8) / 2);
            else Draw.scaled(g, (active ? "<white>" : "<gray>") + t.label, px + 26, ty + (tabH - 6) / 2f, 0.75f, false);
            final Tab target = t;
            buttons.add(new Btn(px + 6, ty, 84, tabH, "tab:" + target.name(), () -> switchTab(target)));
            ty += step;
        }
        g.fill(px + 93, py + 30, px + 94, py + ph - 8, Draw.LINE);

        // Content
        if (s == null || !AbpsClient.connected()) {
            Draw.centered(g, "<gray>This server doesn't run AbpsMod, or it's still loading.", cx + cw / 2, cy + ch / 2 - 4);
        } else if (c == null && tab != Tab.CLASSES && tab != Tab.TOP && tab != Tab.SETTINGS && tab != Tab.ADMIN && tab != Tab.KEYBINDS
                && tab != Tab.SHOPS && tab != Tab.TRAVEL && tab != Tab.PROFILE && tab != Tab.NEWS) {
            Draw.centered(g, "<gray>Loading your attribute...", cx + cw / 2, cy + ch / 2 - 4);
        } else {
            g.enableScissor(cx, cy, cx + cw, cy + ch);
            int top = cy - (int) scroll;
            boolean popup = !confirm.isEmpty();
            int mmx = popup ? -1 : mx, mmy = popup ? -1 : my;
            contentHeight = switch (tab) {
                case OVERVIEW -> overview(g, mmx, mmy, s, c, top);
                case ABILITIES -> abilities(g, mmx, mmy, s, c, top);
                case ROAD -> road(g, mmx, mmy, s, c, top);
                case CLASSES -> classes(g, mmx, mmy, top);
                case TOP -> top(g, mmx, mmy, top);
                case SETTINGS -> settings(g, mmx, mmy, s, top);
                case KEYBINDS -> keybinds(g, c, top);
                case ADMIN -> admin(g, mmx, mmy, s, top);
                case SHOPS -> shops(g, mmx, mmy, top);
                case TRAVEL -> travel(g, mmx, mmy, top);
                case PROFILE -> profile(g, mmx, mmy, top);
                case NEWS -> news(g, mmx, mmy, top);
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
        request(t);
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
            if (u <= 0) continue; // no fifth ability for this attribute
            int mxp = x + (int) ((cw - 16) * (u / (float) s.maxLevel()));
            g.fill(mxp, y - 1, mxp + 1, y + 6, u <= s.level() ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        y += 11;

        // Buttons
        int bw = (cw - 22) / 2;
        button(g, mx, my, x, y, bw, 18, max ? "Max level" : "⬆ Upgrade", 0x69F0AE, !max, () -> confirm = "upgrade");
        button(g, mx, my, x + bw + 6, y, bw, 18, "🎲 Reroll", 0xFF5252, true, () -> confirm = "reroll");
        y += 22;
        if (!max) y += Draw.wrapped(g, "<gray>Next level: </gray>" + s.upgradeCost(), x, y, cw - 16, Draw.MUTED);
        y += 6;

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

    /** One new class in the What's New tab: its icon, name, tagline and a button to look at it. */
    private int newClass(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String id) {
        Net.ClassInfo info = ClientState.catalog.get(id);
        if (info == null) return y;
        Draw.framed(g, x, y, w, 30, 0xE0101016, Draw.argb(info.color(), 0x90));
        Draw.item(g, info.icon(), x + 6, y + 7, 1f);
        Draw.text(g, "<bold>" + Draw.gradient(info.color(), info.color2(), info.symbol() + " " + info.name()) + "</bold>", x + 28, y + 6);
        Draw.textFit(g, "<gray>" + info.tagline(), x + 28, y + 17, w - 28 - 70);
        button(g, mx, my, x + w - 64, y + 7, 58, 16, "<white>See it", info.color(), true, () -> {
            classPick = id;
            switchTab(Tab.CLASSES);
        });
        return y + 34;
    }

    private int news(GuiGraphicsExtractor g, int mx, int my, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        y = section(g, "New in " + dev.abps.Updater.current(), 0xFFD54F, x, y);
        String[] latest = {
                "<white>The mod now updates itself. Servers and players download new versions on their own; restart to use them.",
                "<white>Press <yellow>" + AbpsClient.hudKey.getTranslatedKeyMessage().getString() + "</yellow> to hide or show the HUD panel (also in Settings).",
                "<white>Shop buttons work with a real mouse again."};
        for (String n : latest) y += Draw.wrapped(g, "<gold>•</gold> " + n, x, y, w, Draw.TEXT) + 3;
        y += 4;
        y = section(g, "5 new attributes", 0x00E5FF, x, y);
        for (String id : new String[]{"cryomancer", "chronomancer", "paladin", "voidwalker", "samurai"}) y = newClass(g, mx, my, x, y, w, id);
        y += 4;
        String[] notes = {
                "<white>Every ability has a new hand-made effect: smooth strokes of light and ink instead of picture sprites.",
                "<white>Press <yellow>▶</yellow> next to any ability (Abilities or Attributes tab) to see its effect on yourself. Only you see it.",
                "<white>Effects no longer cover your screen, and the faint squares around glows are gone.",
                "<white>Druid was removed. Druid players got a free new attribute and kept their level.",
                "<white>Shops: every button works again, and long names and prices fit on screen.",
                "<white>Area abilities land on the ground even when you aim at the sky.",
                "<white>Kill streaks are announced. At 5 kills in a row you get a bounty, and whoever ends your streak gets paid.",
                "<white>Levelling up plays an effect in your class colors.",
                "<white>New setting: show <yellow>All</yellow>, <yellow>Fewer</yellow> or <yellow>no</yellow> effects from other players (Settings tab)."};
        y = section(g, "Changes", 0x69F0AE, x, y);
        for (String n : notes) y += Draw.wrapped(g, "<#69F0AE>•</#69F0AE> " + n, x, y, w, Draw.TEXT) + 3;
        return y - y0 + 6;
    }

    /** Closes the menu and plays an ability's effect on you, so you can see what it looks like. */
    private void preview(String classId, int slot) {
        Minecraft.getInstance().gui.setScreen(null);
        dev.abps.client.fx.FxSystem.preview(classId, slot);
    }

    private int abilities(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, Net.ClassInfo c, int y0) {
        int x = cx + 6, y = y0 + 4;
        int lvlIdx = Math.max(0, Math.min(c.descsByLevel().size() - 1, s.level() - 1));
        int count = ClientState.abilityCount(c);
        for (int i = 1; i <= 6; i++) {
            if (i == 5 && count < 5) continue;
            boolean ult = i == ClientState.ULTIMATE;
            boolean unlocked = ClientState.unlocked(i);
            String desc = c.descsByLevel().get(lvlIdx).get(i - 1);
            int textW = cw - 16 - 34 - 6;
            int h = Math.max(44, 14 + Draw.wrappedHeight(desc, textW) + 6);
            int border = ult ? Text.lerp(c.color(), c.color2(), Draw.pulse(0.6f)) : unlocked ? c.color() : 0x3A3A44;
            Draw.framed(g, x, y, cw - 16, h, ult ? 0xE0181420 : 0xE0121218, Draw.argb(border, 0xFF));
            Draw.framed(g, x + 5, y + 5, 24, 24, 0xFF0A0A10, Draw.argb(border, 0xA0));
            Draw.item(g, Draw.abilityIcon(c.id(), i), x + 9, y + 9, 1f);
            String name = (ult ? "<bold>" + Draw.gradient(c.color(), c.color2(), "★ " + c.abilityNames().get(5)) + "</bold>"
                    : (unlocked ? "<white><bold>" : "<gray>") + c.abilityNames().get(i - 1));
            Draw.text(g, name, x + 34, y + 5);
            String meta = "<dark_gray>[</dark_gray><yellow>" + Hud.keyLabel(i) + "</yellow><dark_gray>]</dark_gray> ";
            if (ult) meta += "<gray>Charges by hitting players</gray>";
            else if (!unlocked) meta += "<red>Unlocks at level " + s.unlock()[i - 1] + "</red>";
            else meta += "<gray>" + Text.time(s.cdTotal()[i]) + " cooldown</gray>";
            int mw = Draw.font().width(Text.mm(meta));
            Draw.scaled(g, meta, x + cw - 20 - mw * 0.75f, y + 6, 0.75f, false);
            Draw.wrapped(g, desc, x + 34, y + 17, textW, unlocked ? Draw.MUTED : Draw.DIM);
            final int slot = i;
            button(g, mx, my, x + 5, y + 31, 24, 10, "<white>▶", c.color(), true, () -> preview(c.id(), slot));
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
            for (int u = 0; u < 5; u++) if (s.unlock()[u] == lvl) unlockIdx = u + 1;
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
        for (int u = 0; u < 5; u++) {
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
        int shown = ClientState.abilityCount(info);
        for (int i = 1; i <= 6; i++) {
            if (i == 5 && shown < 5) continue;
            Draw.item(g, Draw.abilityIcon(info.id(), i), dx, yy - 2, 0.6f);
            String n = i == 6 ? "<gold>★ " + info.abilityNames().get(5) + "</gold> <dark_gray>(ultimate)" : "<white>" + info.abilityNames().get(i - 1);
            final int slot = i;
            final String cid = info.id();
            button(g, mx, my, dx + dw - 22, yy - 1, 22, 10, "<white>▶", info.color(), true, () -> preview(cid, slot));
            yy += Draw.wrapped(g, n, dx + 12, yy, dw - 36, Draw.TEXT) + 1;
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
        y = toggle(g, mx, my, x, y, "Show the HUD panel (level, ability keys, ultimate bar). Key: " + AbpsClient.hudKey.getTranslatedKeyMessage().getString(), s.hud(), () -> AbpsClient.send("toggle_hud", ""));
        y = toggle(g, mx, my, x, y, "HUD on the right side", prefs.hudOnRight, () -> {
            prefs.hudOnRight = !prefs.hudOnRight;
            prefs.hudX = -1;
            prefs.hudY = -1;
        });
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
        button(g, mx, my, x, y, cw - 16, 18, "✥ Move the HUD panel", 0x00E5FF, true,
                () -> Minecraft.getInstance().gui.setScreen(new HudEditScreen()));
        y += 22;
        y = section(g, "Effects", 0xFF4081, x, y + 4);
        Draw.text(g, "<white>Effect detail", x, y + 5);
        String[] qualityNames = {"Low", "Normal", "High"};
        int qx = x + cw - 16 - 3 * 46;
        for (int q = 0; q < 3; q++) {
            final int level = q;
            boolean on = prefs.fxQuality == q;
            button(g, mx, my, qx, y, 44, 16, (on ? "<white><bold>" : "<gray>") + qualityNames[q], on ? 0xFF4081 : 0x55555F, true,
                    () -> {
                        prefs.fxQuality = level;
                        prefs.save();
                    });
            qx += 46;
        }
        y += 22;
        Draw.text(g, "<white>Other players' effects", x, y + 5);
        String[] otherNames = {"All", "Fewer", "Off"};
        int ox = x + cw - 16 - 3 * 46;
        for (int q = 0; q < 3; q++) {
            final int mode = q;
            boolean on = prefs.othersFx == q;
            button(g, mx, my, ox, y, 44, 16, (on ? "<white><bold>" : "<gray>") + otherNames[q], on ? 0xFF4081 : 0x55555F, true,
                    () -> {
                        prefs.othersFx = mode;
                        prefs.save();
                    });
            ox += 46;
        }
        y += 22;
        y = toggle(g, mx, my, x, y, "Clear view (fade effects in front of your eyes)", prefs.clearView, () -> prefs.clearView = !prefs.clearView);
        y = section(g, "Updates", 0x00E5FF, x, y + 4);
        String ready = dev.abps.Updater.ready(), err = dev.abps.Updater.lastError();
        Draw.textFit(g, "<gray>You have <white>" + dev.abps.Updater.current() + "</white>"
                + (ready != null ? "  <aqua>" + ready + " is downloaded, restart to use it" : err != null ? "  <#FF8A80>last check failed" : ""), x, y + 2, cw - 16 - 104);
        button(g, mx, my, x + cw - 16 - 100, y - 2, 100, 16, "<white>Check now", 0x00E5FF, true, () -> dev.abps.Updater.checkAsync(null, null));
        y += 18;
        y = toggle(g, mx, my, x, y, "Download updates automatically", prefs.autoUpdate, () -> prefs.autoUpdate = !prefs.autoUpdate);
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

    // ================= Keybinds tab =================

    private static String fullKey(int idx) {
        var key = AbpsClient.ABILITY_KEYS[idx - 1];
        return key == null ? Hud.keyLabel(idx) : key.getTranslatedKeyMessage().getString();
    }

    private void keycap(GuiGraphicsExtractor g, int x, int y, String label, int color) {
        int w = Math.max(22, Draw.font().width(label) + 12);
        Draw.framed(g, x, y, w, 18, 0xFF15151C, Draw.opaque(color));
        g.fill(x + 2, y + 15, x + w - 2, y + 16, Draw.argb(color, 0x60));
        Draw.plain(g, label, x + (w - Draw.font().width(label)) / 2, y + 5, 0xFFFFFFFF);
    }

    private int keyRow(GuiGraphicsExtractor g, int x, int y, int w, String key, String title, String note, int color) {
        Draw.panel(g, x, y, w, 24, 0x60000000);
        keycap(g, x + 4, y + 3, key, color);
        Draw.text(g, "<white>" + title, x + 70, y + 4);
        Draw.text(g, "<gray>" + note, x + 70, y + 14);
        return y + 26;
    }

    private int keybinds(GuiGraphicsExtractor g, Net.ClassInfo c, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        int c1 = c == null ? 0x00E5FF : c.color();
        y = section(g, "Ability keys", c1, x, y);
        int have = ClientState.abilityCount(c);
        for (int i = 1; i <= 6; i++) {
            if (i == 5 && have < 5) continue;
            boolean ult = i == 6;
            String name = c == null ? (ult ? "Ultimate" : "Ability " + i) : c.abilityNames().get(i - 1);
            String note = ult ? "Ultimate. Charges by hitting players" : ClientState.unlocked(i) ? "Ability " + i : "Ability " + i + " (locked)";
            y = keyRow(g, x, y, w, fullKey(i), name, note, ult ? 0xFFD54F : c1);
        }
        y = section(g, "Menu and movement", 0x69F0AE, x + 0, y + 4);
        y = keyRow(g, x, y, w, AbpsClient.menuKey.getTranslatedKeyMessage().getString(), "Open this menu", "Press it again or Esc to close", 0x69F0AE);
        y = keyRow(g, x, y, w, Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage().getString(), "Double jump",
                "Press again in the air (Windwalker only)", 0x69F0AE);
        y = section(g, "Chat commands", 0xFF9800, x, y + 4);
        y += Draw.wrapped(g, "<gray>Everything also works from chat. Type <white>!Help</white> for the list or <white>/abps</white> and press Tab. "
                + "Commands like <white>/home</white>, <white>/tpa</white> and <white>/spawn</white> are real commands too.", x, y, w, Draw.MUTED) + 6;
        Draw.wrapped(g, "<dark_gray>Change any key in Options > Controls > Key Binds > AbpsMod.", x, y, w, Draw.DIM);
        return y - y0 + 26;
    }

    // ================= Admin tab =================

    private record Act(String label, int color, boolean enabled, boolean danger, Runnable run) {
    }

    private int chipWidth(String label) {
        return Draw.font().width(Text.mm(label)) + 10;
    }

    private void chip(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, boolean on, int color, Runnable action) {
        int w = chipWidth(label);
        boolean hover = Draw.inside(mx, my, x, y, w, 16);
        Draw.framed(g, x, y, w, 16, on ? Draw.argb(color, 0x70) : hover ? 0x40FFFFFF : 0xC0101016, on ? Draw.opaque(color) : 0xFF33333D);
        Draw.text(g, (on ? "<white>" : "<gray>") + label, x + 5, y + 4);
        buttons.add(new Btn(x, y, w, 16, action));
    }

    /** Draws chips left to right and wraps to a new line. Returns the y below the last row. */
    private int chips(GuiGraphicsExtractor g, int mx, int my, int x, int y, int width, List<String> labels, List<Boolean> on,
                      int color, List<Runnable> actions) {
        int fx = x;
        for (int i = 0; i < labels.size(); i++) {
            int w = chipWidth(labels.get(i));
            if (fx + w > x + width) {
                fx = x;
                y += 18;
            }
            chip(g, mx, my, fx, y, labels.get(i), on.get(i), color, actions.get(i));
            fx += w + 3;
        }
        return y + 20;
    }

    private void sendAdmin(String command) {
        AbpsClient.send("admin", command);
        adminSent = "!" + command;
    }

    private void sendVanilla(String command) {
        AbpsClient.send("vanilla", command);
        adminSent = "/" + command;
    }

    /** A grid of action buttons. Dangerous ones need a second click within 3 seconds. */
    private int grid(GuiGraphicsExtractor g, int mx, int my, int x, int y, int width, int cols, List<Act> acts) {
        int gap = 4;
        int bw = (width - gap * (cols - 1)) / cols;
        for (int i = 0; i < acts.size(); i++) {
            Act a = acts.get(i);
            int bx = x + (i % cols) * (bw + gap);
            int by = y + (i / cols) * 22;
            String key = a.label + "|" + adminTarget;
            boolean armed = a.danger && key.equals(pendingKey) && System.currentTimeMillis() - pendingAt < 3000;
            Runnable action = !a.danger ? a.run : () -> {
                if (armed) {
                    pendingKey = "";
                    a.run.run();
                } else {
                    pendingKey = key;
                    pendingAt = System.currentTimeMillis();
                }
            };
            button(g, mx, my, bx, by, bw, 18, armed ? "<white><bold>Sure?" : a.label, a.color, a.enabled, action);
        }
        return y + ((acts.size() + cols - 1) / cols) * 22 + 2;
    }

    private int admin(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        if (adminClass.isEmpty() && !s.classId().isEmpty()) adminClass = s.classId();
        adminLevel = Math.max(1, Math.min(s.maxLevel(), adminLevel));

        // Who the commands are aimed at
        y = section(g, "Target player", 0xFF5252, x, y);
        List<String> names = new ArrayList<>();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) for (var info : conn.getOnlinePlayers()) names.add(info.getProfile().name());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        List<String> ids = new ArrayList<>(List.of("@s", "@a", "@r"));
        List<String> labels = new ArrayList<>(List.of("Me", "Everyone", "Random"));
        for (String n : names) {
            ids.add(n);
            labels.add(n);
        }
        List<Boolean> on = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        for (String id : ids) {
            on.add(id.equals(adminTarget));
            acts.add(() -> adminTarget = id);
        }
        y = chips(g, mx, my, x, y, w, labels, on, 0xFF5252, acts);

        // Attribute and level pickers
        y = section(g, "Attribute and level", 0xFFD54F, x, y + 2);
        int fx = x;
        for (Net.ClassInfo info : ClientState.catalog.values()) {
            boolean picked = info.id().equals(adminClass);
            boolean hover = Draw.inside(mx, my, fx, y, 18, 18);
            Draw.framed(g, fx, y, 18, 18, picked ? Draw.argb(info.color(), 0x70) : hover ? 0x40FFFFFF : 0xC0101016,
                    picked ? Draw.opaque(info.color()) : 0xFF33333D);
            Draw.item(g, info.icon(), fx + 1, y + 1, 1f);
            final String id = info.id();
            buttons.add(new Btn(fx, y, 18, 18, () -> adminClass = id));
            if (hover) g.setTooltipForNextFrame(Text.mm(Draw.gradient(info.color(), info.color2(), info.name())), mx, my);
            fx += 20;
            if (fx + 18 > x + w) {
                fx = x;
                y += 20;
            }
        }
        y += 22;
        Draw.text(g, "<gray>Level</gray> <white><bold>" + adminLevel + "</bold></white><gray> / " + s.maxLevel(), x, y + 4);
        button(g, mx, my, x + 80, y, 18, 16, "-", 0x777781, true, () -> adminLevel = Math.max(1, adminLevel - 1));
        button(g, mx, my, x + 100, y, 18, 16, "+", 0x777781, true, () -> adminLevel = Math.min(s.maxLevel(), adminLevel + 1));
        button(g, mx, my, x + 122, y, 30, 16, "-5", 0x777781, true, () -> adminLevel = Math.max(1, adminLevel - 5));
        button(g, mx, my, x + 154, y, 30, 16, "+5", 0x777781, true, () -> adminLevel = Math.min(s.maxLevel(), adminLevel + 5));
        y += 22;

        // Mod commands aimed at the target
        String t = adminTarget;
        String named = t.startsWith("@") ? (t.equals("@s") && Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getGameProfile().name() : null) : t;
        boolean haveClass = !adminClass.isEmpty();
        y = section(g, "Player actions", 0x00E5FF, x, y);
        List<Act> player = List.of(
                new Act("Give Attribute", 0x69F0AE, haveClass, false, () -> sendAdmin("GiveAttribute " + t + " " + adminClass)),
                new Act("Set Level", 0x69F0AE, true, false, () -> sendAdmin("GiveUpgrade " + t + " " + adminLevel)),
                new Act("Charge Ultimate", 0xFFD54F, true, false, () -> sendAdmin("GiveUlt " + t)),
                new Act("Free Roll", 0x7C4DFF, true, false, () -> sendAdmin("ForceRoll " + t)),
                new Act("Reset Player", 0xFF5252, true, true, () -> sendAdmin("ResetPlayer " + t)),
                new Act("Inspect", 0x00E5FF, true, false, () -> sendAdmin("CheckAttribute " + t)),
                new Act("Live Stats", 0x00E5FF, true, false, () -> sendAdmin("Stats " + t)),
                new Act("Clear Cooldowns", 0x69F0AE, true, false, () -> sendAdmin("ResetCooldown " + t)),
                new Act("Give Reroll Cost", 0xFFD54F, true, false, () -> sendAdmin("GiveRollCost " + t)),
                new Act("Clear Combat", 0x69F0AE, named != null, false, () -> sendAdmin("ClearCombat " + named)));
        y = grid(g, mx, my, x, y, w, 3, player);

        // Commands that don't need a target
        y = section(g, "Server and testing", 0xFF9800, x, y + 2);
        List<Act> server = List.of(
                new Act(s.noCooldown() ? "No Cooldown: ON" : "No Cooldown: off", s.noCooldown() ? 0x69F0AE : 0x777781, true, false, () -> sendAdmin("NoCooldown")),
                new Act("Debug Mode", 0x777781, true, false, () -> sendAdmin("Debug")),
                new Act("Set Spawn Here", 0x00E5FF, true, false, () -> sendAdmin("SetSpawn")),
                new Act("Save All", 0x69F0AE, true, false, () -> sendAdmin("SaveAll")),
                new Act("Reload Config", 0xFFD54F, true, false, () -> sendAdmin("Reload")));
        y = grid(g, mx, my, x, y, w, 3, server);

        // A few vanilla operator commands
        y = section(g, "Vanilla operator tools", 0xB388FF, x, y + 2);
        List<Act> vanilla = List.of(
                new Act("Survival", 0x69F0AE, true, false, () -> sendVanilla("gamemode survival " + t)),
                new Act("Creative", 0x69F0AE, true, false, () -> sendVanilla("gamemode creative " + t)),
                new Act("Adventure", 0x69F0AE, true, false, () -> sendVanilla("gamemode adventure " + t)),
                new Act("Spectator", 0x69F0AE, true, false, () -> sendVanilla("gamemode spectator " + t)),
                new Act("Day", 0xFFD54F, true, false, () -> sendVanilla("time set day")),
                new Act("Night", 0x5C6BC0, true, false, () -> sendVanilla("time set night")),
                new Act("Clear Weather", 0x00E5FF, true, false, () -> sendVanilla("weather clear")),
                new Act("Rain", 0x00E5FF, true, false, () -> sendVanilla("weather rain")),
                new Act("Thunder", 0x00E5FF, true, false, () -> sendVanilla("weather thunder")),
                new Act("Bring To Me", 0x7C4DFF, true, false, () -> sendVanilla("tp " + t + " @s")),
                new Act("Kill", 0xFF5252, true, true, () -> sendVanilla("kill " + t)),
                new Act("Kick", 0xFF5252, named != null, true, () -> sendVanilla("kick " + named)),
                new Act("Ban", 0xFF5252, named != null, true, () -> sendVanilla("ban " + named)),
                new Act("Op", 0xFF9800, named != null, true, () -> sendVanilla("op " + named)),
                new Act("De-op", 0xFF9800, named != null, true, () -> sendVanilla("deop " + named)));
        y = grid(g, mx, my, x, y, w, 3, vanilla);

        y += 2;
        Draw.wrapped(g, adminSent.isEmpty()
                ? "<dark_gray>Red buttons need a second click. Results show up in chat."
                : "<gray>Sent <white>" + adminSent + "</white><dark_gray> - results show up in chat.", x, y, w, Draw.DIM);
        return y - y0 + 24;
    }

    private void popup(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, Net.ClassInfo c) {
        g.fill(px, py, px + pw, py + ph, 0xA0000000);
        boolean reroll = confirm.equals("reroll");
        int w = Math.min(pw - 40, 260), h = reroll ? 146 : 130;
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
            yy += Draw.wrapped(g, "<gray>You get a random new attribute. <red><bold>You lose all " + s.level()
                    + " levels and every XP level you have.</bold></red>", x + 10, yy, w - 20, Draw.TEXT) + 4;
            yy += Draw.wrapped(g, "<gray>Price: </gray>" + s.rerollCost(), x + 10, yy, w - 20, Draw.MUTED) + 2;
            if (!s.canReroll()) Draw.text(g, "<red>You can't afford this yet.", x + 10, yy);
        } else {
            Draw.scaled(g, "<bold>" + Draw.gradient(c.color(), c.color2(), "Upgrade to level " + (s.level() + 1) + "?") + "</bold>", x + w / 2f, yy, 1.25f, true);
            yy += 18;
            yy += Draw.wrapped(g, "<gray>Price: </gray>" + s.upgradeCost(), x + 10, yy, w - 20, Draw.MUTED) + 2;
            for (int u = 0; u < 5; u++) {
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

    // ================= Shops, Travel and Profile tabs =================

    private final TextInput shopName = new TextInput(24, false);
    private final TextInput homeName = new TextInput(16, false);
    private java.util.UUID waitingShop;

    /** Asks the server for whatever a tab shows. */
    private void request(Tab t) {
        switch (t) {
            case SHOPS -> AbpsClient.send("shop", "list");
            case TRAVEL -> AbpsClient.send("travel", "list");
            case PROFILE -> AbpsClient.send("profile", "");
            default -> {
            }
        }
    }

    /** True if the player just clicked this shop in the list, so its contents should open the shop screen. */
    public boolean waitingForShop(java.util.UUID owner) {
        return owner.equals(waitingShop);
    }

    private static String itemCount(net.minecraft.world.item.ItemStack stack, int count) {
        return count + " " + stack.getHoverName().getString();
    }

    private int shops(GuiGraphicsExtractor g, int mx, int my, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        Net.ShopListPayload list = ClientState.shopList;
        if (list == null) {
            Draw.centered(g, "<gray>Loading shops...", cx + cw / 2, y + 20);
            return 40;
        }
        if (!list.enabled()) {
            Draw.centered(g, "<gray>Shops are turned off on this server.", cx + cw / 2, y + 20);
            return 40;
        }
        // Your own shop, or a way to open one
        int boxH = list.hasShop() ? 44 : 56;
        Draw.framed(g, x, y, w, boxH, 0xE0101018, 0xFF2E7D5B);
        java.util.UUID me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        if (list.hasShop()) {
            Draw.text(g, "<bold><gradient:#69F0AE:#00E5FF>Your shop is open</gradient></bold>", x + 8, y + 7);
            Draw.text(g, "<gray>Stock it and collect earnings.", x + 8, y + 19);
            button(g, mx, my, x + w - 96, y + 12, 88, 20, "<white><bold>Manage shop", 0x69F0AE, true, () -> {
                waitingShop = me;
                AbpsClient.send("shop", "open|" + me);
            });
        } else {
            Draw.text(g, "<bold><gradient:#69F0AE:#00E5FF>Open your own shop", x + 8, y + 6);
            Draw.textFit(g, (list.canCreate() ? "<gray>Costs " : "<#FF8A80>You need ") + list.createCost(), x + 8, y + 18, w - 16);
            shopName.draw(g, x + 8, y + 31, w - 112, 18, 0x69F0AE, "Shop name (optional)");
            // Always clickable: if you can't pay, the server says exactly what's missing
            button(g, mx, my, x + w - 98, y + 31, 90, 18, "<white><bold>Open shop", list.canCreate() ? 0x69F0AE : 0x9E9E9E, true, () -> {
                AbpsClient.send("shop", "create|" + shopName.text().trim());
                waitingShop = me;
            });
        }
        y += boxH + 8;

        y = section(g, "Player shops (" + list.shops().size() + ")", 0x69F0AE, x, y);
        if (list.shops().isEmpty()) {
            Draw.centered(g, "<gray>No shops yet. Be the first!", cx + cw / 2, y + 8);
            return y - y0 + 30;
        }
        for (Net.ShopCard card : list.shops()) {
            boolean hover = Draw.inside(mx, my, x, y, w, 32);
            Draw.framed(g, x, y, w, 32, hover ? 0xF0181824 : 0xE0101016, hover ? 0xFF69F0AE : 0xFF2A2A34);
            g.item(card.icon(), x + 6, y + 8);
            int textW = w - 36 - card.preview().size() * 18 - 8;
            Draw.text(g, "<white><bold>" + Draw.fit(card.name(), textW - 6), x + 28, y + 6);
            String dot = card.online() ? "<#69F0AE>●</#69F0AE>" : "<dark_gray>●</dark_gray>";
            Draw.textFit(g, dot + " <gray>" + card.ownerName() + "  <dark_gray>" + card.listings() + " listings · " + card.sales() + " sales", x + 28, y + 18, textW);
            int ix = x + w - 8 - card.preview().size() * 18;
            for (net.minecraft.world.item.ItemStack st : card.preview()) {
                g.item(st, ix, y + 8);
                ix += 18;
            }
            final java.util.UUID owner = card.owner();
            buttons.add(new Btn(x, y, w, 32, "shop:" + card.name(), () -> {
                waitingShop = owner;
                AbpsClient.send("shop", "open|" + owner);
            }));
            y += 35;
        }
        return y - y0 + 6;
    }

    private int travel(GuiGraphicsExtractor g, int mx, int my, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        Net.TravelPayload t = ClientState.travel;
        if (t == null) {
            Draw.centered(g, "<gray>Loading...", cx + cw / 2, y + 20);
            return 40;
        }
        if (t.combatLeft() > 0) {
            Draw.panel(g, x, y, w, 14, Draw.argb(0xFF1744, 0x50));
            Draw.centered(g, "<#FF5252>⚔ In combat for " + Text.time(t.combatLeft()) + ". You can't teleport yet.", cx + cw / 2, y + 3);
            y += 18;
        } else if (t.cooldownLeft() > 0) {
            Draw.centered(g, "<gray>You can teleport again in " + Text.time(t.cooldownLeft()) + ".", cx + cw / 2, y + 2);
            y += 14;
        }

        // Quick travel
        int bw = (w - 6) / 2;
        button(g, mx, my, x, y, bw, 20, "<white><bold>⌂ Spawn", 0x00E5FF, true, () -> AbpsClient.send("travel", "spawn"));
        button(g, mx, my, x + bw + 6, y, bw, 20, "<white><bold>↺ Back", 0x7C4DFF, t.hasBack(), () -> AbpsClient.send("travel", "back"));
        y += 28;

        // Homes
        y = section(g, "Homes (" + t.homes().size() + "/" + t.maxHomes() + ")", 0x00E5FF, x, y);
        for (Net.HomeInfo h : t.homes()) {
            Draw.framed(g, x, y, w, 22, 0xE0101016, 0xFF2A2A34);
            Draw.item(g, "minecraft:red_bed", x + 4, y + 3, 1f);
            Draw.textFit(g, "<white><bold>" + Draw.fit(h.name(), 80) + "</bold> <dark_gray>" + h.dimension() + " " + h.x() + ", " + h.y() + ", " + h.z(), x + 24, y + 7, w - 24 - 84);
            final String name = h.name();
            button(g, mx, my, x + w - 78, y + 3, 44, 16, "<white>Go", 0x00E5FF, true, () -> AbpsClient.send("travel", "home|" + name));
            button(g, mx, my, x + w - 30, y + 3, 26, 16, "<#FF5252>✕", 0xFF5252, true, () -> AbpsClient.send("travel", "delhome|" + name));
            y += 25;
        }
        if (t.homes().isEmpty()) {
            Draw.text(g, "<gray>No homes yet. Name one below and set it where you stand.", x, y + 2);
            y += 14;
        }
        boolean full = t.homes().size() >= t.maxHomes();
        homeName.draw(g, x, y + 2, w - 108, 18, 0x00E5FF, "home name");
        button(g, mx, my, x + w - 102, y + 2, 102, 18, full ? "Homes full" : "<white><bold>+ Set home here", 0x00E5FF, !full, () -> {
            AbpsClient.send("travel", "sethome|" + (homeName.text().isBlank() ? "home" : homeName.text().trim()));
            homeName.set("");
        });
        y += 28;

        // Requests waiting for an answer
        if (!t.requests().isEmpty()) {
            y = section(g, "Teleport requests", 0xFFD54F, x, y);
            for (String r : t.requests()) {
                String name = r.contains(" (") ? r.substring(0, r.indexOf(" (")) : r;
                Draw.framed(g, x, y, w, 22, 0xE0181410, 0xFF5A4A20);
                Draw.textFit(g, "<white>" + r + " <gray>wants to teleport", x + 6, y + 7, w - 6 - 122);
                button(g, mx, my, x + w - 116, y + 3, 56, 16, "<#69F0AE>Accept", 0x69F0AE, true, () -> AbpsClient.send("travel", "accept|" + name));
                button(g, mx, my, x + w - 56, y + 3, 52, 16, "<#FF5252>Deny", 0xFF5252, true, () -> AbpsClient.send("travel", "deny|" + name));
                y += 25;
            }
        }

        // Everyone online
        y = section(g, "Players online (" + t.online().size() + ")", 0x7C4DFF, x, y);
        if (t.online().isEmpty()) {
            Draw.text(g, "<gray>Nobody else is online.", x, y + 2);
            y += 14;
        }
        for (String name : t.online()) {
            Draw.framed(g, x, y, w, 20, 0xE0101016, 0xFF2A2A34);
            Draw.text(g, "<white>" + Draw.fit(name, w - 6 - 130), x + 6, y + 6);
            button(g, mx, my, x + w - 124, y + 2, 60, 16, "<white>Go to", 0x7C4DFF, true, () -> AbpsClient.send("travel", "tpr|" + name));
            button(g, mx, my, x + w - 62, y + 2, 58, 16, "<white>Bring", 0x7C4DFF, true, () -> AbpsClient.send("travel", "tphere|" + name));
            y += 22;
        }
        return y - y0 + 6;
    }

    private int profile(GuiGraphicsExtractor g, int mx, int my, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        Net.ProfilePayload pr = ClientState.profile;
        if (pr == null) {
            Draw.centered(g, "<gray>Loading...", cx + cw / 2, y + 20);
            return 40;
        }
        Net.ClassInfo c = ClientState.myClass();
        int c1 = c == null ? 0x7C4DFF : c.color(), c2 = c == null ? 0x00E5FF : c.color2();
        Draw.scaled(g, "<bold>" + Draw.gradient(c1, c2, pr.name()) + "</bold>", x, y, 1.6f, false);
        if (c != null && ClientState.sync != null) Draw.text(g, "<gray>" + c.name() + " · Level " + ClientState.sync.level(), x, y + 16);
        y += 30;

        // Daily rewards: a week of cards
        if (pr.dailyEnabled() && !pr.rewards().isEmpty()) {
            y = section(g, "Daily rewards", 0xFFD54F, x, y);
            int days = pr.rewards().size();
            int nextDay = pr.canClaim() ? pr.streak() % days + 1 : 0;
            int dayW = (w - 3 * (days - 1)) / days;
            for (int k = 0; k < days; k++) {
                int day = k + 1, dx = x + k * (dayW + 3);
                boolean claimed = pr.canClaim() ? day < nextDay : day <= pr.streak();
                boolean today = day == nextDay;
                int border = today ? Draw.opaque(Text.lerp(0xFFD54F, 0xFFFFFF, Draw.pulse(1f) * 0.5f)) : claimed ? 0xFF2E7D5B : 0xFF2A2A34;
                Draw.framed(g, dx, y, dayW, 48, today ? 0xF0201808 : 0xE0101016, border);
                Draw.centered(g, (today ? "<gold><bold>" : claimed ? "<#69F0AE>" : "<gray>") + "Day " + day, dx + dayW / 2, y + 4);
                List<net.minecraft.world.item.ItemStack> items = pr.rewards().get(k);
                int iw = Math.min(2, items.size()) * 17;
                int ix = dx + (dayW - iw) / 2;
                for (int q = 0; q < Math.min(2, items.size()); q++) {
                    net.minecraft.world.item.ItemStack st = items.get(q);
                    g.item(st, ix, y + 16);
                    g.itemDecorations(Draw.font(), st, ix, y + 16);
                    ix += 17;
                }
                if (claimed) Draw.centered(g, "<#69F0AE>✔", dx + dayW / 2, y + 36);
                if (Draw.inside(mx, my, dx, y, dayW, 48) && !items.isEmpty()) {
                    StringBuilder tip = new StringBuilder("<gold>Day " + day + "</gold>");
                    for (net.minecraft.world.item.ItemStack st : items) tip.append("\n<white>").append(itemCount(st, st.getCount()));
                    g.setTooltipForNextFrame(Draw.font().split(Text.mm(tip.toString()), 200), mx, my);
                }
            }
            y += 54;
            if (pr.canClaim()) {
                button(g, mx, my, x + w / 2 - 70, y, 140, 20, "<white><bold>Claim day " + nextDay + " reward", 0xFFB300, true,
                        () -> AbpsClient.send("daily", ""));
            } else {
                long left = pr.nextClaimIn() / 60000;
                String when = left >= 60 ? (left / 60) + "h " + (left % 60) + "m" : Math.max(1, left) + "m";
                Draw.wrapped(g, "<gray>Next reward in <white>" + when + "</white>. Miss a day and the streak starts over.", x, y + 2, w, Draw.MUTED);
            }
            y += 28;
        }
        // Stat cards, three to a row
        int cols = 3, gap = 4, cardW = (w - gap * (cols - 1)) / cols;
        for (int k = 0; k < pr.labels().size(); k++) {
            int cxp = x + (k % cols) * (cardW + gap), cyp = y + (k / cols) * 34;
            Draw.framed(g, cxp, cyp, cardW, 30, 0xE0101016, Draw.argb(Text.lerp(c1, c2, k / (float) pr.labels().size()), 0x90));
            Draw.scaled(g, "<gray>" + pr.labels().get(k), cxp + 6, cyp + 5, 0.75f, false);
            Draw.text(g, "<white><bold>" + pr.values().get(k), cxp + 6, cyp + 16);
        }
        y += ((pr.labels().size() + cols - 1) / cols) * 34 + 6;

        return y - y0 + 6;
    }

    /** Development only: clicks the first button whose label starts with this, through the real mouse code. */
    boolean press(String label) {
        for (Btn b : List.copyOf(buttons)) {
            if (!b.label.startsWith(label)) continue;
            UiPreview.realClick(b.x + b.w / 2.0, b.y + b.h / 2.0, false);
            return true;
        }
        return false;
    }

    // ================= Input =================

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        dev.abps.AbpsMod.LOGGER.debug("[Abps menu] click at {},{} button {} with {} buttons registered, popup='{}'",
                (int) e.x(), (int) e.y(), e.button(), buttons.size(), confirm);
        // Text boxes only count inside the visible content area, and never under a popup
        boolean inContent = confirm.isEmpty() && Draw.inside(e.x(), e.y(), cx, cy, cw, ch);
        double fx = inContent ? e.x() : -9999, fy = inContent ? e.y() : -9999;
        boolean typed = shopName.click(fx, fy) | homeName.click(fx, fy);
        if (typed) return true;
        if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) { // left or right click, so swapped mouse buttons still work
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
            // Grab the title bar to move the window
            if (Draw.inside(e.x(), e.y(), px, py, pw, 24)) {
                draggingWindow = true;
                return true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!draggingWindow) return super.mouseDragged(e, dx, dy);
        offX += dx;
        offY += dy;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (draggingWindow) {
            draggingWindow = false;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!confirm.isEmpty()) return true;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - ch), scroll - sy * 16));
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (shopName.charTyped(e) || homeName.charTyped(e)) return true;
        return super.charTyped(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (shopName.keyPressed(e) || homeName.keyPressed(e)) return true;
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
