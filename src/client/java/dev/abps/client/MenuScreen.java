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
        SKILLS("Skill Tree", "minecraft:enchanted_book"),
        CLASSES("Attributes", "minecraft:book"),
        TOP("Top Players", "minecraft:totem_of_undying"),
        SHOPS("Shops", "minecraft:emerald"),
        TRAVEL("Travel", "minecraft:ender_pearl"),
        DUNGEONS("Party", "minecraft:crossbow"),
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
    private String pendingRespec = "";
    private long respecAt;
    private String classPick = "";
    private String boardFilter = "";
    private String roleFilter = "";
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
            case "road", "skills", "skill", "tree" -> this.tab = Tab.SKILLS;
            case "classes", "attributes" -> this.tab = Tab.CLASSES;
            case "top" -> this.tab = Tab.TOP;
            case "settings" -> this.tab = Tab.SETTINGS;
            case "keybinds", "keys" -> this.tab = Tab.KEYBINDS;
            case "admin" -> this.tab = Tab.ADMIN;
            case "shops", "shop" -> this.tab = Tab.SHOPS;
            case "travel" -> this.tab = Tab.TRAVEL;
            case "dungeons", "dungeon", "party", "titles" -> this.tab = Tab.DUNGEONS;
            case "profile" -> this.tab = Tab.PROFILE;
            case "confirm_upgrade" -> confirm = "upgrade";
            case "confirm_reroll" -> confirm = "reroll";
            case "confirm_reroll_other" -> confirm = "reroll_other";
            case "choose_role" -> this.tab = Tab.OVERVIEW; // Overview shows the role cards until an attribute is rolled
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
        Draw.button(g, x, y, w, h, color, hover, enabled);
        Draw.centeredFit(g, enabled ? label : "<dark_gray>" + Text.strip(label), x + w / 2, y + (h - 8) / 2 - (enabled && h >= 16 ? 1 : 0), w - 8);
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
        Draw.window(g, px, py, pw, ph, c1, c2, 0xF00E0D14);
        Draw.hGradient(g, px + 1, py + 2, pw - 2, 21, Draw.argb(c1, 0x34), Draw.argb(c2, 0x08));
        Draw.divider(g, px + 1, py + 23, pw - 2, Draw.argb(c1, 0xA0), Draw.argb(c2, 0x30));
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
            // The open tab is a raised button in the class color, the others sit flat until hovered
            if (active) {
                // A dark tinted slot with the class color down its left edge
                g.fill(px + 6, ty, px + 90, ty + tabH, Draw.opaque(Draw.deep(c1, 0.16f)));
                g.fill(px + 6, ty, px + 90, ty + 1, Draw.opaque(Draw.deep(c1, 0.32f)));
                g.fill(px + 6, ty + tabH - 1, px + 90, ty + tabH, 0xFF08080C);
                g.fill(px + 6, ty, px + 8, ty + tabH, Draw.opaque(c1));
            } else if (hover) {
                g.fill(px + 6, ty, px + 90, ty + tabH, 0x1CFFFFFF);
                g.fill(px + 6, ty, px + 7, ty + tabH, 0x50FFFFFF);
            }
            String icon = t == Tab.OVERVIEW && c != null ? c.icon() : t.icon;
            // The icon is sized to the tab's height and the label shrinks to fit what's left, so nothing pokes out of the tab
            // Two pixels clear of the edge on every side
            float iconScale = Math.max(0.4f, Math.min(1f, (tabH - 4) / 16f));
            Draw.item(g, icon, px + 9, ty + (tabH - 16 * iconScale) / 2f, iconScale);
            int textX = px + 9 + Math.round(16 * iconScale) + 4;
            Draw.textFit(g, (active ? "<white><bold>" : hover ? "<white>" : "<gray>") + t.label, textX, ty + (tabH - 8) / 2, px + 6 + 84 - 3 - textX);
            final Tab target = t;
            buttons.add(new Btn(px + 6, ty, 84, tabH, "tab:" + target.name(), () -> switchTab(target)));
            ty += step;
        }
        g.fill(px + 93, py + 30, px + 94, py + ph - 8, 0x60000000);
        g.fill(px + 94, py + 30, px + 95, py + ph - 8, 0x18FFFFFF);

        // Content
        if (s == null || !AbpsClient.connected()) {
            Draw.centered(g, "<gray>This server doesn't run AbpsMod, or it's still loading.", cx + cw / 2, cy + ch / 2 - 4);
        } else if (s.classId().isEmpty() && tab == Tab.OVERVIEW) {
            try {
                roleCards(g, mx, my);
            } catch (Throwable t) {
                drawError(g, t);
            }
        } else if (c == null && tab != Tab.CLASSES && tab != Tab.TOP && tab != Tab.SETTINGS && tab != Tab.ADMIN && tab != Tab.KEYBINDS
                && tab != Tab.SHOPS && tab != Tab.TRAVEL && tab != Tab.PROFILE && tab != Tab.NEWS && tab != Tab.DUNGEONS) {
            Draw.centered(g, "<gray>Loading your attribute...", cx + cw / 2, cy + ch / 2 - 4);
        } else {
            g.enableScissor(cx, cy, cx + cw, cy + ch);
            int top = cy - (int) scroll;
            boolean popup = !confirm.isEmpty();
            int mmx = popup ? -1 : mx, mmy = popup ? -1 : my;
            try {
            contentHeight = switch (tab) {
                case OVERVIEW -> overview(g, mmx, mmy, s, c, top);
                case ABILITIES -> abilities(g, mmx, mmy, s, c, top);
                case SKILLS -> skills(g, mmx, mmy, s, c, top);
                case CLASSES -> classes(g, mmx, mmy, top);
                case TOP -> top(g, mmx, mmy, top);
                case SETTINGS -> settings(g, mmx, mmy, s, top);
                case KEYBINDS -> keybinds(g, c, top);
                case ADMIN -> admin(g, mmx, mmy, s, top);
                case SHOPS -> shops(g, mmx, mmy, top);
                case TRAVEL -> travel(g, mmx, mmy, top);
                case DUNGEONS -> dungeons(g, mmx, mmy, top);
                case PROFILE -> profile(g, mmx, mmy, top);
                case NEWS -> news(g, mmx, mmy, top);
            };
            } catch (Throwable t) {
                // A broken tab shows what went wrong instead of crashing the game
                drawError(g, t);
                contentHeight = ch;
            }
            g.disableScissor();
            // Soft fades where the content scrolls under the edges, instead of text cut in half
            if (scroll > 0) g.fillGradient(cx, cy, cx + cw, cy + 10, 0xF00E0D14, 0x000E0D14);
            if (contentHeight > ch && scroll < contentHeight - ch - 1) g.fillGradient(cx, cy + ch - 10, cx + cw, cy + ch, 0x000E0D14, 0xF00E0D14);
            // Scroll bar: a thin track with a rounded thumb
            if (contentHeight > ch) {
                float frac = (float) ch / contentHeight;
                int barH = Math.max(14, (int) (ch * frac));
                int barY = cy + (int) ((ch - barH) * (scroll / Math.max(1, contentHeight - ch)));
                g.fill(px + pw - 5, cy, px + pw - 3, cy + ch, 0x50000000);
                int thumb = Draw.opaque(Draw.deep(c1, 0.55f));
                g.fill(px + pw - 6, barY + 1, px + pw - 2, barY + barH - 1, thumb);
                g.fill(px + pw - 5, barY, px + pw - 3, barY + barH, thumb);
                g.fill(px + pw - 5, barY + 1, px + pw - 4, barY + barH - 1, Draw.opaque(Draw.deep(c1, 0.75f)));
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
        String title = "<bold>" + Draw.gradient(c.color(), c.color2(), c.name()) + "</bold>";
        float ts = Math.min(2f, (cw - 70) / (float) Math.max(1, Draw.font().width(Text.mm(title))));
        Draw.scaled(g, title, x + 52, y + 2 + (2f - ts) * 4, ts, false);
        Draw.wrapped(g, "<gray><italic>" + c.tagline(), x + 52, y + 22, cw - 66, Draw.MUTED);

        y += 52;
        boolean max = s.level() >= s.maxLevel();
        Draw.text(g, "<gray>Level</gray> <white><bold>" + s.level() + "</bold></white><gray> / " + s.maxLevel() + "</gray>"
                + (max ? "  <gradient:#FFD54F:#FF8F00><bold>MASTERED</bold></gradient>" : ""), x, y);
        y += 11;
        Draw.bar(g, x, y, cw - 16, 5, (float) s.level() / s.maxLevel(), c.color(), c.color2());
        y += 11;

        // Buy a level
        if (!max) {
            button(g, mx, my, x, y, cw - 16, 18, "<white><bold>⬆ Buy a skill point", 0x2E7D32, true, () -> confirm = "upgrade");
            y += 22;
        }
        if (!max) y += Draw.wrapped(g, "<gray>Next level: </gray>" + s.upgradeCost(), x, y, cw - 16, Draw.MUTED);
        y += 6;

        // Both roles: the one you're playing and the one parked, each with its own reroll
        if (!"admin".equals(c.role())) y = roleSlots(g, mx, my, s, c, x, y);

        // Stats
        String role = switch (c.role()) {
            case "gatherer" -> "<#69F0AE>Gatherer</#69F0AE>";
            case "admin" -> "<#FFD54F>Operator</#FFD54F>";
            default -> "<#FF5252>PvP</#FF5252>";
        };
        Draw.textFit(g, "<gray>Role:</gray> " + role + "   <gray>Abilities used:</gray> <white>" + s.abilitiesUsed() + "</white>   <gray>Rerolls:</gray> <white>"
                + s.rerolls() + "</white>   <gray>Players:</gray> <white>" + c.players() + "</white>", x, y, cw - 16);
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

    private static long lastErrorLog;

    private void drawError(GuiGraphicsExtractor g, Throwable t) {
        if (System.currentTimeMillis() - lastErrorLog > 5000) {
            lastErrorLog = System.currentTimeMillis();
            dev.abps.AbpsMod.LOGGER.error("Menu tab {} failed to draw", tab, t);
        }
        Draw.wrapped(g, "<red>This tab hit an error: " + t.getClass().getSimpleName() + ". <gray>Please send the log (latest.log) so it can be fixed.",
                cx + 6, cy + 10, cw - 16, Draw.TEXT);
    }

    /** For players without an attribute yet: two big cards to pick PvP or Gatherer. */
    private void roleCards(GuiGraphicsExtractor g, int mx, int my) {
        int x = cx + 6, w = cw - 16, y = cy + 6;
        Draw.scaled(g, "<bold><gradient:#FFD54F:#FF8F00>Pick the role to start in</gradient></bold>", cx + cw / 2f, y, 1.5f, true);
        y += 18;
        y += Draw.wrapped(g, "<gray>You get an attribute for <white>both</white> roles. Pick which one you play first; switch between them any time for free.",
                x, y, w, Draw.MUTED) + 6;
        int cardW = (w - 8) / 2;
        String[][] roles = {
                {"pvp", "PvP", "minecraft:netherite_sword", "Built for fighting other players. Strong abilities, ultimates charged by hitting players."},
                {"gatherer", "Gatherer", "minecraft:diamond_pickaxe", "Built for getting items and progressing: farming, mining, chopping, fishing and exploring. Ultimates charge as you gather. Less damage to players."}};
        // Tall enough for the longer of the two descriptions, with room to spare at the bottom
        int textH = Math.max(Draw.wrappedHeight("<gray>" + roles[0][3], cardW - 16), Draw.wrappedHeight("<gray>" + roles[1][3], cardW - 16));
        int cardH = 60 + textH + 10;
        for (int k = 0; k < 2; k++) {
            int col = k == 0 ? 0xFF5252 : 0x69F0AE;
            int bx = x + k * (cardW + 8);
            boolean hover = Draw.inside(mx, my, bx, y, cardW, cardH);
            Draw.card(g, bx, y, cardW, cardH, hover ? Text.lerp(col, 0xFFFFFF, 0.3f) : col, hover);
            Draw.item(g, roles[k][2], bx + cardW / 2f - 16, y + 8, 2f);
            Draw.scaled(g, "<bold>" + Draw.gradient(col, Text.lerp(col, 0xFFFFFF, 0.5f), roles[k][1]) + "</bold>", bx + cardW / 2f, y + 44, 1.5f, true);
            Draw.wrapped(g, "<gray>" + roles[k][3], bx + 8, y + 60, cardW - 16, Draw.MUTED);
            final String id = roles[k][0];
            buttons.add(new Btn(bx, y, cardW, cardH, "role:" + id, () -> AbpsClient.send("role", id)));
        }
    }

    /** Two cards side by side, PvP and Gatherer: which attribute each role has, which one is in use, and buttons to switch or reroll. */
    private int roleSlots(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, Net.ClassInfo c, int x, int y) {
        y = section(g, "Your roles", 0x40C4FF, x, y);
        int w = cw - 16, cardW = (w - 6) / 2, cardH = 56;
        String[] ids = {"pvp", "gatherer"};
        String[] labels = {"PvP", "Gatherer"};
        int[] cols = {0xFF5252, 0x69F0AE};
        String key = AbpsClient.roleKey == null ? "" : AbpsClient.roleKey.getTranslatedKeyMessage().getString();
        for (int k = 0; k < 2; k++) {
            boolean active = ids[k].equals(s.role());
            Net.ClassInfo info = active ? c : ClientState.catalog.get(s.otherClass());
            int lvl = active ? s.level() : s.otherLevel();
            int bx = x + k * (cardW + 6), col = cols[k];
            Draw.card(g, bx, y, cardW, cardH, col, active);
            // Role name on the left, a small "in use" tag on the right
            Draw.text(g, "<bold><" + Draw.hex(col) + ">" + labels[k] + "</" + Draw.hex(col) + "></bold>", bx + 8, y + 5);
            if (active) {
                int tw = Draw.font().width("IN USE") + 6;
                g.fill(bx + cardW - tw - 5, y + 4, bx + cardW - 5, y + 14, Draw.opaque(Draw.deep(col, 0.30f)));
                Draw.plain(g, "IN USE", bx + cardW - tw - 2, y + 5, 0xFFFFFFFF);
            }
            if (info != null) {
                Draw.framed(g, bx + 7, y + 16, 18, 18, 0xFF0A0A10, 0xFF2E2E38);
                Draw.item(g, info.icon(), bx + 8, y + 17, 1f);
                Draw.textFit(g, "<white><bold>" + info.name() + "</bold>", bx + 29, y + 17, cardW - 34);
                Draw.textFit(g, "<gray>Level " + lvl, bx + 29, y + 27, cardW - 34);
            } else {
                Draw.textFit(g, "<gray>Rolling...", bx + 8, y + 22, cardW - 14);
            }
            int half = (cardW - 19) / 2;
            int by = y + cardH - 18;
            if (active) {
                Draw.textFit(g, "<dark_gray>" + (key.isEmpty() ? "" : "Switch: <gray>" + key), bx + 8, by + 4, half);
                button(g, mx, my, bx + cardW - half - 6, by, half, 14, "<white>🎲 Reroll", 0xC62828, true, () -> confirm = "reroll");
            } else {
                button(g, mx, my, bx + 8, by, half, 14, "<white>⇄ Switch", 0x1E88E5, info != null, () -> AbpsClient.send("switchrole", ""));
                button(g, mx, my, bx + cardW - half - 6, by, half, 14, "<white>🎲 Reroll", 0xC62828, info != null, () -> confirm = "reroll_other");
            }
        }
        return y + cardH + 8;
    }

    private int section(GuiGraphicsExtractor g, String title, int color, int x, int y) {
        Draw.text(g, "<bold>" + Draw.gradient(color, Text.lerp(color, 0xFFFFFF, 0.5f), title) + "</bold>", x, y);
        int tw = Draw.font().width(Text.mm("<bold>" + title));
        Draw.divider(g, x + tw + 6, y + 4, cw - 22 - tw, Draw.argb(color, 0x90), Draw.argb(color, 0));
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
        String[] big = {
                "<white><gold>Skill Tree</gold> replaces straight upgrades. Every level gives points (max level fills the tree): unlock abilities and pick damage, utility or defense skills. Reset it for free.",
                "<white><green>Two roles at once</green>: a <#FF5252>PvP</#FF5252> attribute and a <#69F0AE>Gatherer</#69F0AE> attribute, each with its own level and skills. Switch any time (Overview, !Role or <yellow>" + AbpsClient.roleKey.getTranslatedKeyMessage().getString() + "</yellow>). New gatherers: Harvester, Lumberjack, Angler, Explorer, Chef.",
                "<white>Turn your whole attribute off (Settings or <yellow>!Powers off</yellow>), and Pyromancers can turn off their heat aura (<yellow>!Aura off</yellow>)."};
        for (String n : big) y += Draw.wrapped(g, "<gold>•</gold> " + n, x, y, w, Draw.TEXT) + 3;
        button(g, mx, my, x, y + 1, 120, 18, "<white><bold>Open Skill Tree", 0xFFD54F, true, () -> switchTab(Tab.SKILLS));
        y += 26;
        y = section(g, "Ruby & Endite", 0xFF5370, x, y);
        String[] ores = {
                "<white><#FF5370>Ruby</#FF5370>: deep ore (Y 16 to bedrock, needs a diamond pickaxe). Ruby tools and armor sit between diamond and netherite. Full set: +2 hearts and Luck.",
                "<white>Ancient debris now needs a <#FF5370>ruby pickaxe</#FF5370>, and netherite upgrades start from ruby gear.",
                "<white><#CE93D8>Endite</#CE93D8>: ore on the End's outer islands (netherite pickaxe). 4 shards + a netherite ingot + 4 popped chorus fruit make an ingot. The upgrade template hides in End City chests.",
                "<white>Full endite set: no ender pearl damage, and it pulls you out of the void once every 10 minutes.",
                "<white><gold>18 new enchantments</gold>: Lifesteal, Executioner, Venom, Frostbite, Thunderstrike, Dodge, Timber, Excavator, Smelting Touch, Magnetic, Replanting, Explosive Shot, Leaping, Night Owl, Second Wind, plus treasure-only Vein Miner, Homing and Soulbound.",
                "<white><#FFC107>Miner</#FFC107> buff: faster mining, shorter cooldowns and the new Prospector passive."};
        for (String n : ores) y += Draw.wrapped(g, "<#FF5370>•</#FF5370> " + n, x, y, w, Draw.TEXT) + 3;
        y += 6;
        y = section(g, "Cooking & Farming", 0x9CCC65, x, y);
        String[] farm = {
                "<white><#9CCC65>6 new crops</#9CCC65> (tomato, corn, onion, cabbage, chili, strawberry): their seeds turn up in grass. <#FF8C1A>Fruit trees</#FF8C1A> (orange, lemon, peach, plum) grow from saplings that fall from leaves; right-click ripe leaves to pick.",
                "<white><gold>Kitchen</gold>: a <white>Cooking Pot</white> over fire, a <white>Stone Oven</white> fed with coal, a <white>Cutting Board</white> and an <white>Aging Barrel</white>. Add ingredients by right-clicking, then right-click empty-handed to cook. <yellow>!Recipes</yellow> lists them all.",
                "<white>Meals and drinks give buffs, feasts feed everyone nearby, and eating 6 different foods makes you <#69F0AE>Well Fed</#69F0AE> (+2 hearts). Street food: burgers, tacos, fries, kebabs, hot dogs, popcorn.",
                "<white><#80DEEA>Seasons</#80DEEA> change every 7 days (<yellow>!Season</yellow>). Sprinklers water and grow crops, greenhouse glass speeds up what's under it, rich compost makes better soil, and Fine and Prime quality crops fill you up more.",
                "<white>Crows peck at grown crops unless a <white>Scarecrow</white> is nearby. Use a glass bottle on cows and goats for milk.",
                "<white>New gatherer attribute: the <#FFAB40>Chef</#FFAB40>. Faster kitchens, extra servings, longer meal buffs and party-feeding abilities."};
        for (String n : farm) y += Draw.wrapped(g, "<#9CCC65>•</#9CCC65> " + n, x, y, w, Draw.TEXT) + 3;
        y += 6;
        y = section(g, "Siege & Party Games", 0xB388FF, x, y);
        String[] games = {
                "<white>Dungeons are gone. In their place: <gold>Siege</gold>, co-op tower defense. Hold the Heart through 10, 15 or 20 waves of raiders; kills earn coins for Archer, Frost, Bombard and Healing towers.",
                "<white>Party games for 2+ players: <#40C4FF>Capture the Flag</#40C4FF>, <#FFAB40>King of the Hill</#FFAB40> and <#E1F5FE>Spleef</#E1F5FE>, with win boards.",
                "<white>Make a party, then the leader starts a game from the <yellow>Party</yellow> tab.",
                "<white><light_purple>Meteor showers</light_purple> light up some nights. A few meteors land, leaving craters with ruby (and rarely endite) ore."};
        for (String n : games) y += Draw.wrapped(g, "<light_purple>•</light_purple> " + n, x, y, w, Draw.TEXT) + 3;
        button(g, mx, my, x, y + 1, 120, 18, "<white><bold>Open Party", 0xB388FF, true, () -> switchTab(Tab.DUNGEONS));
        y += 26;
        y = section(g, "Also new", 0xFFD54F, x, y);
        String[] latest = {
                "<white>The mod updates itself. Servers and players download new versions on their own; restart to use them.",
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
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        int lvlIdx = Math.max(0, Math.min(c.descsByLevel().size() - 1, s.level() - 1));
        int count = ClientState.abilityCount(c);
        for (int i = 1; i <= 6; i++) {
            if (i == 5 && count < 5) continue;
            boolean ult = i == ClientState.ULTIMATE;
            boolean unlocked = ClientState.unlocked(i);
            String desc = c.descsByLevel().get(lvlIdx).get(i - 1);
            int textX = x + 36, textW = w - 36 - 8;
            // Name row, the description, then a row for the key, the cooldown and the preview button
            int h = Math.max(48, 16 + Draw.wrappedHeight(desc, textW) + 21);
            int accent = ult ? Text.lerp(c.color(), c.color2(), Draw.pulse(0.6f)) : unlocked ? c.color() : 0x4A4A56;
            Draw.card(g, x, y, w, h, accent, ult || unlocked);
            // Icon in a slot
            Draw.framed(g, x + 8, y + 6, 22, 22, 0xFF0A0A10, unlocked || ult ? Draw.opaque(Draw.deep(accent, 0.45f)) : 0xFF2A2A32);
            Draw.item(g, Draw.abilityIcon(c.id(), i), x + 11, y + 9, 1f);
            if (!unlocked && !ult) g.fill(x + 9, y + 7, x + 29, y + 27, 0x90000000);
            String name = ult ? "<bold>" + Draw.gradient(c.color(), c.color2(), "★ " + c.abilityNames().get(5)) + "</bold>"
                    : (unlocked ? "<white><bold>" : "<gray>") + c.abilityNames().get(i - 1);
            Draw.textFit(g, name, textX, y + 6, textW);
            Draw.wrapped(g, desc, textX, y + 17, textW, unlocked || ult ? Draw.MUTED : Draw.DIM);
            // Bottom row
            int by = y + h - 17;
            String key = Hud.keyLabel(i);
            int kw = Math.max(13, Draw.font().width(key) + 6);
            Draw.framed(g, textX, by + 1, kw, 12, 0xFF1C1C24, 0xFF4A4A56);
            Draw.plain(g, key, textX + (kw - Draw.font().width(key)) / 2, by + 3, 0xFFFFE57F);
            String meta = ult ? "<gray>Charges by " + ("gatherer".equals(c.role()) ? "gathering" : "hitting players")
                    : !unlocked ? "<#FF8A80>Locked · take it in the Skill Tree" : "<gray>" + Text.time(s.cdTotal()[i]) + " cooldown";
            Draw.textFit(g, meta, textX + kw + 5, by + 3, textW - kw - 5 - 66);
            final int slot = i;
            button(g, mx, my, x + w - 66, by, 58, 14, "<white>▶ Preview", c.color(), true, () -> preview(c.id(), slot));
            y += h + 4;
        }
        Draw.wrapped(g, "<dark_gray>Change keys in Options > Controls > Key Binds > AbpsMod.", x, y + 2, w, Draw.DIM);
        return y - y0 + 18;
    }

    /** A thin line between two points, two pixels wide, drawn as small steps. */
    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int k = 0; k <= steps; k++) {
            int x = x0 + (x1 - x0) * k / Math.max(1, steps), y = y0 + (y1 - y0) * k / Math.max(1, steps);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    private int skills(GuiGraphicsExtractor g, int mx, int my, Net.SyncPayload s, Net.ClassInfo c, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        List<dev.abps.skills.SkillTree.Node> tree = dev.abps.skills.SkillTree.build(ClientState.abilityCount(c), c.abilityNames().subList(0, 5),
                "gatherer".equals(c.role()) ? "gatherer" : "pvp");
        List<String> owned = s.skills();
        int points = s.points();
        boolean max = s.level() >= s.maxLevel();

        // Header: points, buy one, reset
        Draw.text(g, "<gray>Skill points:</gray> " + (points > 0 ? "<gold><bold>" + points + "</bold></gold>" : "<white>0"), x, y + 5);
        int bw = 86;
        if (!max) button(g, mx, my, x + w - bw * 2 - 4, y, bw, 18, "<white>⬆ Buy a point", 0x2E7D32, true, () -> confirm = "upgrade");
        boolean armed = "respec".equals(pendingRespec) && System.currentTimeMillis() - respecAt < 3000;
        button(g, mx, my, x + w - bw, y, bw, 18, armed ? "<white><bold>Sure? Free" : "<white>↺ Reset", 0xBF360C, !owned.isEmpty(), () -> {
            if (armed) {
                pendingRespec = "";
                AbpsClient.send("respec", "");
            } else {
                pendingRespec = "respec";
                respecAt = System.currentTimeMillis();
            }
        });
        y += 24;

        // The grid: 7 columns, 6 rows
        int node = 20, sx = Math.max(node + 8, Math.min(44, (w - node) / 6)), sy = 30;
        int gw = sx * 6 + node, gx = x + (w - gw) / 2, gy = y + 12;
        String[] names = {"gatherer".equals(c.role()) ? "Prosperity" : "Offense", "Abilities", "Utility", "Defense"};
        double[] labelCols = {0.5, 2, 3.5, 5.5};
        for (int b = 0; b < 4; b++) {
            if (b == 1) continue; // the trunk is labelled by the root itself
            int col = b == 0 && "gatherer".equals(c.role()) ? dev.abps.skills.SkillTree.PROSPERITY_COLOR : dev.abps.skills.SkillTree.BRANCH_COLORS[b];
            Draw.scaled(g, "<bold>" + Draw.gradient(col, Text.lerp(col, 0xFFFFFF, 0.5f), names[b]) + "</bold>",
                    gx + (float) (labelCols[b] * sx) + node / 2f, gy - 10, 0.75f, true);
        }
        java.util.function.Function<dev.abps.skills.SkillTree.Node, int[]> at = n -> new int[]{gx + (int) Math.round(n.col() * sx), gy + n.row() * sy};

        // Lines first, so nodes sit on top of them
        for (dev.abps.skills.SkillTree.Node n : tree) {
            int[] b = at.apply(n);
            for (String r : n.requires()) {
                dev.abps.skills.SkillTree.Node parent = dev.abps.skills.SkillTree.find(tree, r);
                if (parent == null) continue;
                int[] a = at.apply(parent);
                boolean lit = owned.contains(n.id()) && (r.equals("root") || owned.contains(r));
                int col = branchColor(n, c);
                line(g, a[0] + node / 2 - 1, a[1] + node / 2 - 1, b[0] + node / 2 - 1, b[1] + node / 2 - 1, lit ? Draw.opaque(col) : 0x60FFFFFF);
            }
        }
        dev.abps.skills.SkillTree.Node hovered = null;
        for (dev.abps.skills.SkillTree.Node n : tree) {
            int[] p = at.apply(n);
            boolean own = n.kind() == dev.abps.skills.SkillTree.Kind.ROOT || owned.contains(n.id());
            boolean open = dev.abps.skills.SkillTree.available(n, owned);
            int col = branchColor(n, c);
            boolean hover = Draw.inside(mx, my, p[0], p[1], node, node);
            if (own) Draw.button(g, p[0], p[1], node, node, col, hover, true);
            else if (open) {
                int edge = Text.lerp(col, 0xFFFFFF, Draw.pulse(1f) * 0.5f);
                Draw.framed(g, p[0], p[1], node, node, 0xF0101016, Draw.opaque(hover ? 0xFFFFFF : edge));
            } else Draw.framed(g, p[0], p[1], node, node, 0xF00A0A0E, 0xFF2A2A32);
            String icon = n.kind() == dev.abps.skills.SkillTree.Kind.ABILITY ? Draw.abilityIcon(c.id(), (int) n.value()) : n.icon();
            Draw.item(g, icon, p[0] + 2, p[1] + 2, 1f);
            if (!own && !open) g.fill(p[0] + 1, p[1] + 1, p[0] + node - 1, p[1] + node - 1, 0xA0000000);
            if (hover) hovered = n;
            if (open) {
                final String id = n.id();
                buttons.add(new Btn(p[0], p[1], node, node, "skill:" + id, () -> {
                    if (s.points() > 0) AbpsClient.send("skill", id);
                    else confirm = "upgrade";
                }));
            }
        }
        if (hovered != null) {
            boolean own = hovered.kind() == dev.abps.skills.SkillTree.Kind.ROOT || owned.contains(hovered.id());
            boolean open = dev.abps.skills.SkillTree.available(hovered, owned);
            int col = branchColor(hovered, c);
            StringBuilder tip = new StringBuilder("<bold>" + Draw.gradient(col, Text.lerp(col, 0xFFFFFF, 0.5f), hovered.name()) + "</bold>\n<gray>" + hovered.desc());
            if (own) tip.append("\n<#69F0AE>✔ Learned");
            else if (open) tip.append(points > 0 ? "\n<yellow>Click to learn (1 point)" : "\n<#FF8A80>No points left. Click to buy one.");
            else {
                List<String> need = new ArrayList<>();
                for (String r : hovered.requires()) {
                    dev.abps.skills.SkillTree.Node pn = dev.abps.skills.SkillTree.find(tree, r);
                    if (pn != null) need.add(pn.name());
                }
                tip.append("\n<dark_gray>Needs ").append(String.join(" or ", need)).append(" first");
            }
            g.setTooltipForNextFrame(Draw.font().split(Text.mm(tip.toString()), 180), mx, my);
        }
        y = gy + 5 * sy + node + 8;
        y += Draw.wrapped(g, "<gray>Levelling up gives skill points and makes your passives stronger. At max level you have enough for the whole tree."
                + " Resetting is free when you're not in a fight.", x, y, w, Draw.MUTED) + 4;
        Draw.wrapped(g, "<gold>★ Level " + s.maxLevel() + " Mastery:</gold> " + c.mastery(), x, y, w, Draw.TEXT);
        return y - y0 + 24;
    }

    private static int branchColor(dev.abps.skills.SkillTree.Node n, Net.ClassInfo c) {
        if (n.branch() == dev.abps.skills.SkillTree.OFFENSE && "gatherer".equals(c.role())) return dev.abps.skills.SkillTree.PROSPERITY_COLOR;
        if (n.branch() == dev.abps.skills.SkillTree.TRUNK) return c.color();
        return dev.abps.skills.SkillTree.BRANCH_COLORS[n.branch()];
    }

    private int classes(GuiGraphicsExtractor g, int mx, int my, int y0) {
        if (ClientState.catalog.isEmpty()) {
            Draw.centered(g, "<gray>Loading...", cx + cw / 2, y0 + 20);
            return 40;
        }
        if (classPick.isEmpty()) classPick = ClientState.sync != null && !ClientState.sync.classId().isEmpty()
                ? ClientState.sync.classId() : ClientState.catalog.keySet().iterator().next();
        int x = cx + 6, y = y0 + 4;
        int listW = 108;
        // Role filter: all, PvP or Gatherer
        String[][] filters = {{"", "All"}, {"pvp", "PvP"}, {"gatherer", "Gather"}};
        int fw = (listW - 4) / 3;
        for (int k = 0; k < 3; k++) {
            final String f = filters[k][0];
            boolean on = roleFilter.equals(f);
            int col = k == 1 ? 0xFF5252 : k == 2 ? 0x69F0AE : 0x7C4DFF;
            button(g, mx, my, x + k * (fw + 2), y, fw, 15, (on ? "<white>" : "<gray>") + filters[k][1], on ? col : 0x55555F, true, () -> roleFilter = f);
        }
        y += 18;
        for (Net.ClassInfo info : ClientState.catalog.values()) {
            if (!roleFilter.isEmpty() && !roleFilter.equals(info.role())) continue;
            boolean picked = info.id().equals(classPick);
            boolean hover = Draw.inside(mx, my, x, y, listW, 20);
            Draw.panel(g, x, y, listW, 20, picked ? Draw.argb(info.color(), 0x55) : hover ? 0x30FFFFFF : 0x60000000);
            if (picked) g.fill(x, y + 3, x + 2, y + 17, Draw.opaque(info.color()));
            Draw.item(g, info.icon(), x + 4, y + 2, 1f);
            Draw.textFit(g, Draw.gradient(info.color(), info.color2(), info.name()), x + 23, y + 6, listW - 27);
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
        String big = "<bold>" + Draw.gradient(info.color(), info.color2(), info.name()) + "</bold>";
        float bigScale = Math.min(1.5f, (dw - 30) / (float) Math.max(1, Draw.font().width(Text.mm(big))));
        Draw.scaled(g, big, dx + 28, yy + 1 + (1.5f - bigScale) * 4, bigScale, false);
        String badge = switch (info.role()) {
            case "gatherer" -> "<#69F0AE>Gatherer</#69F0AE>";
            case "admin" -> "<#FFD54F>Operators only</#FFD54F>";
            default -> "<#FF5252>PvP</#FF5252>";
        };
        Draw.textFit(g, badge + " <dark_gray>·</dark_gray> <gray>" + info.players() + " player" + (info.players() == 1 ? "" : "s") + " have this", dx + 28, yy + 15, dw - 30);
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
            button(g, mx, my, dx + dw - 24, yy - 2, 24, 12, "<white>▶", info.color(), true, () -> preview(cid, slot));
            yy += Math.max(13, Draw.wrapped(g, n, dx + 12, yy, dw - 40, Draw.TEXT) + 3);
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
            Draw.textFit(g, "<white>" + e.name() + (mine ? " <gray>(you)" : ""), x + 46, y + 6, cw - 22 - 46 - Draw.font().width(Text.mm((info == null ? "" : info.name() + " ") + "Lv " + e.level())) - 6);
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
        y = section(g, "Your attribute", 0xFF9800, x, y);
        y = toggle(g, mx, my, x, y, "Use my attribute powers (off = no abilities, passives or weaknesses)", !s.powersOff(),
                () -> AbpsClient.send("toggle_powers", ""));
        if ("pyromancer".equals(s.classId())) {
            y = toggle(g, mx, my, x, y, "Heat aura (enemies near you catch fire)", s.pyroAura(), () -> AbpsClient.send("toggle_aura", ""));
        }
        y = section(g, "Display", 0x00E5FF, x, y + 4);
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
        Draw.framed(g, x, y, w, 18, hover ? 0x38FFFFFF : 0x70000000, hover ? 0x60FFFFFF : 0x40000000);
        Draw.textFit(g, "<white>" + label, x + 6, y + 5, w - 42);
        // A sunken track with a raised knob, like a lever: green and to the right when on
        int sx = x + w - 30;
        Draw.framed(g, sx, y + 4, 24, 10, on ? 0xFF1E5A40 : 0xFF202026, 0xFF000000);
        int knob = on ? sx + 13 : sx + 1;
        Draw.button(g, knob, y + 4, 10, 10, on ? 0x69F0AE : 0x9E9EA8, hover, true);
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
        Draw.textFit(g, "<white>" + title, x + 70, y + 4, w - 74);
        Draw.textFit(g, "<gray>" + note, x + 70, y + 14, w - 74);
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
        y = keyRow(g, x, y, w, AbpsClient.roleKey.getTranslatedKeyMessage().getString(), "Switch role", "Swap between your PvP and Gatherer attribute", 0x40C4FF);
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
        boolean reroll = confirm.equals("reroll"), switching = confirm.equals("reroll_other");
        Net.ClassInfo oc = ClientState.catalog.get(s.otherClass());
        int w = Math.min(pw - 40, 260), h = reroll || switching ? 150 : 130;
        int x = px + (pw - w) / 2, y = py + (ph - h) / 2;
        int col = reroll ? 0xFF5252 : switching ? 0x40C4FF : 0x69F0AE;
        Draw.window(g, x, y, w, h, col, Text.lerp(col, 0x000000, 0.4f), 0xF8100F16);
        int yy = y + 8;
        if (switching) {
            String roleName = "gatherer".equals(s.role()) ? "PvP" : "Gatherer";
            Draw.scaled(g, "<bold><gradient:#FF5252:#FFAB40>Reroll your " + roleName + " attribute?</gradient></bold>", x + w / 2f, yy, 1.25f, true);
            yy += 16;
            yy += Draw.wrapped(g, "<gray>Right now it's " + (oc == null ? "?" : Draw.gradient(oc.color(), oc.color2(), oc.name())) + " <gray>(level " + s.otherLevel()
                    + "). You get a random new " + roleName + " attribute.", x + 10, yy, w - 20, Draw.TEXT) + 3;
            yy += Draw.wrapped(g, "<red><bold>It goes back to level 1, and you lose every XP level you have.", x + 10, yy, w - 20, Draw.TEXT) + 3;
            yy += Draw.wrapped(g, "<gray>Price: </gray>" + s.rerollCost(), x + 10, yy, w - 20, Draw.MUTED) + 2;
            if (!s.canReroll()) Draw.text(g, "<red>You can't afford this yet.", x + 10, yy);
        } else if (reroll) {
            Draw.scaled(g, "<bold><gradient:#FF5252:#FFAB40>Reroll your attribute?</gradient></bold>", x + w / 2f, yy, 1.25f, true);
            yy += 16;
            yy += Draw.wrapped(g, "<gray>You get a random new attribute. <red><bold>You lose all " + s.level()
                    + " levels and every XP level you have.</bold></red>", x + 10, yy, w - 20, Draw.TEXT) + 4;
            yy += Draw.wrapped(g, "<gray>Price: </gray>" + s.rerollCost(), x + 10, yy, w - 20, Draw.MUTED) + 2;
            if (!s.canReroll()) Draw.text(g, "<red>You can't afford this yet.", x + 10, yy);
        } else {
            Draw.scaled(g, "<bold>" + Draw.gradient(c.color(), c.color2(), "Buy a skill point?") + "</bold>", x + w / 2f, yy, 1.25f, true);
            yy += 18;
            yy += Draw.wrapped(g, "<gray>Level " + s.level() + " → <white>" + (s.level() + 1) + "</white>: more points for your Skill Tree, and stronger passives.",
                    x + 10, yy, w - 20, Draw.TEXT) + 2;
            yy += Draw.wrapped(g, "<gray>Price: </gray>" + s.upgradeCost(), x + 10, yy, w - 20, Draw.MUTED) + 2;
            if (!s.canUpgrade()) Draw.text(g, "<red>You can't afford this yet.", x + 10, yy);
        }
        int by = y + h - 26, bw = (w - 30) / 2;
        boolean can = reroll || switching ? s.canReroll() : s.canUpgrade();
        button(g, mx, my, x + 10, by, bw, 18, switching ? "<white><bold>Reroll" : reroll ? "<white><bold>Reroll" : "<white><bold>Buy", col, can, () -> {
            AbpsClient.send(switching ? "rerollother" : reroll ? "reroll" : "upgrade", "");
            confirm = "";
            if (reroll || switching) onClose();
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
            case DUNGEONS -> {
                AbpsClient.send("dungeon", "list");
                dungeonsAskedAt = System.currentTimeMillis();
            }
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
            Draw.textFit(g, "<bold><gradient:#69F0AE:#00E5FF>Your shop is open</gradient></bold>", x + 8, y + 7, w - 112);
            Draw.textFit(g, "<gray>Stock it and collect earnings.", x + 8, y + 19, w - 112);
            button(g, mx, my, x + w - 96, y + 12, 88, 20, "<white><bold>Manage shop", 0x69F0AE, true, () -> {
                waitingShop = me;
                AbpsClient.send("shop", "open|" + me);
            });
        } else {
            Draw.textFit(g, "<bold><gradient:#69F0AE:#00E5FF>Open your own shop", x + 8, y + 6, w - 16);
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
            Draw.textFit(g, "<gray>No homes yet. Name one below and set it where you stand.", x, y + 2, cw - 16);
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
            Draw.textFit(g, "<gray>Nobody else is online.", x, y + 2, cw - 16);
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
            Draw.textFit(g, "<gray>" + pr.labels().get(k), cxp + 6, cyp + 5, cardW - 10);
            Draw.textFit(g, "<white><bold>" + pr.values().get(k), cxp + 6, cyp + 16, cardW - 10);
        }
        y += ((pr.labels().size() + cols - 1) / cols) * 34 + 6;

        return y - y0 + 6;
    }

    // ================= Dungeons tab =================

    private long dungeonsAskedAt;

    private static String modeName(String mode) {
        return switch (mode) {
            case "SIEGE" -> "Co-op tower defense";
            case "CTF" -> "Two teams · 2+ players";
            case "KOTH" -> "Free for all · 2+ players";
            case "SPLEEF" -> "Last one standing · 2+ players";
            case "WAVES" -> "Waves";
            case "RANDOM" -> "New layout every run";
            default -> "Story";
        };
    }

    private int dungeons(GuiGraphicsExtractor g, int mx, int my, int y0) {
        int x = cx + 6, y = y0 + 4, w = cw - 16;
        // Invites accepted or players leaving don't send anything, so ask again every few seconds while the tab is open
        if (System.currentTimeMillis() - dungeonsAskedAt > 3000) request(Tab.DUNGEONS);
        Net.DungeonsPayload dp = ClientState.dungeons;
        if (dp == null) {
            Draw.centered(g, "<gray>Loading...", cx + cw / 2, y + 20);
            return 40;
        }
        if (!dp.enabled()) {
            Draw.centered(g, "<gray>Party games are turned off on this server.", cx + cw / 2, y + 20);
            return 40;
        }
        String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().name();

        // In a run right now
        if (!dp.inRun().isEmpty()) {
            Draw.framed(g, x, y, w, 26, 0xE0181020, 0xFFB388FF);
            Draw.textFit(g, "<gray>You're in <white><bold>" + dp.inRun(), x + 8, y + 9, w - 16 - 84);
            button(g, mx, my, x + w - 80, y + 4, 74, 18, "<white><bold>Leave", 0xFF5252, true, () -> AbpsClient.send("dungeon", "leave"));
            y += 32;
        }
        // A party invite waiting for an answer
        if (!dp.inviteFrom().isEmpty()) {
            Draw.framed(g, x, y, w, 26, 0xE0181410, 0xFF5A4A20);
            Draw.textFit(g, "<aqua>" + dp.inviteFrom() + "</aqua> <gray>invited you to their party", x + 8, y + 9, w - 16 - 122);
            button(g, mx, my, x + w - 118, y + 5, 56, 16, "<#69F0AE>Accept", 0x69F0AE, true, () -> AbpsClient.send("dungeon", "accept"));
            button(g, mx, my, x + w - 58, y + 5, 52, 16, "<#FF5252>Decline", 0xFF5252, true, () -> AbpsClient.send("dungeon", "decline"));
            y += 32;
        }

        // The party
        y = section(g, "Your party (" + dp.party().size() + "/8)", 0x00E5FF, x, y);
        for (int k = 0; k < dp.party().size(); k++) {
            String name = dp.party().get(k);
            boolean leader = k == 0, mine = name.equals(me);
            boolean canKick = dp.leader() && !mine;
            Draw.framed(g, x, y, w, 20, mine ? 0xE0101C24 : 0xE0101016, 0xFF2A2A34);
            String label = (leader ? "<gold>♛</gold> " : "<dark_gray>•</dark_gray> ") + "<white>" + name + (mine ? " <gray>(you)" : "")
                    + (leader && dp.party().size() > 1 ? " <dark_gray>leader" : "");
            Draw.textFit(g, label, x + 6, y + 6, w - 12 - (canKick ? 50 : 0));
            if (canKick) button(g, mx, my, x + w - 48, y + 2, 44, 16, "<#FF5252>Kick", 0xFF5252, true, () -> AbpsClient.send("dungeon", "kick|" + name));
            y += 22;
        }
        if (dp.party().size() > 1) {
            button(g, mx, my, x, y, 90, 16, "<gray>Leave party", 0x777781, true, () -> AbpsClient.send("dungeon", "partyleave"));
            y += 20;
        }
        if (dp.leader() && dp.party().size() < 8) {
            List<String> labels = new ArrayList<>();
            List<Boolean> on = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            for (String n : dp.online()) {
                if (dp.party().contains(n)) continue;
                labels.add("+ " + n);
                on.add(false);
                acts.add(() -> AbpsClient.send("dungeon", "invite|" + n));
            }
            if (labels.isEmpty()) {
                Draw.textFit(g, "<gray>Nobody else is online to invite.", x, y + 2, cw - 16);
                y += 14;
            } else {
                Draw.text(g, "<gray>Invite:", x, y + 4);
                y = chips(g, mx, my, x + 42, y, w - 42, labels, on, 0x00E5FF, acts);
            }
        } else if (!dp.leader()) {
            y += Draw.wrapped(g, "<gray>Your party leader picks the game and starts it.", x, y + 2, w, Draw.MUTED) + 4;
        }

        // Siege and the party games
        y = section(g, "Siege & Party Games", 0xB388FF, x, y + 4);
        boolean canStart = dp.leader() && dp.inRun().isEmpty();
        int size = dp.party().size();
        for (Net.DungeonCard d : dp.cards()) {
            int col = d.color(), col2 = Text.lerp(col, 0xFFFFFF, 0.5f);
            boolean waves = d.mode().equals("SIEGE") || d.mode().equals("WAVES");
            int textW = w - 38 - 80;
            String blurb = "<gray>" + d.blurb();
            int blurbH = Draw.wrappedHeight(blurb, textW);
            int rows = Math.max(1, Math.min(5, d.board().size()));
            int top = Math.max(30 + blurbH, 34);
            int h = top + 11 + 11 + rows * 10 + 6;
            Draw.framed(g, x, y, w, h, 0xE0101016, Draw.argb(col, 0x90));
            Draw.item(g, d.icon(), x + 8, y + 6, 1.5f);
            Draw.textFit(g, "<bold>" + Draw.gradient(col, col2, d.name()) + "</bold>", x + 38, y + 6, textW);
            Draw.textFit(g, "<gold>" + "★".repeat(Math.max(0, d.difficulty())) + "</gold><dark_gray>" + "☆".repeat(Math.max(0, 4 - d.difficulty()))
                    + "</dark_gray> <dark_gray>·</dark_gray> <gray>" + modeName(d.mode()), x + 38, y + 17, textW);
            Draw.wrapped(g, blurb, x + 38, y + 29, textW, Draw.MUTED);
            String startLabel = !dp.inRun().isEmpty() ? "In a game" : !dp.leader() ? "Leader only" : size > 1 ? "<white><bold>Start (" + size + ")" : "<white><bold>Start";
            final String id = d.id();
            button(g, mx, my, x + w - 74, y + 8, 66, 18, startLabel, col, canStart, () -> AbpsClient.send("dungeon", "start|" + id));

            int yy = y + top;
            String stats;
            if (waves) stats = d.best() > 0 ? "<gray>Your best:</gray> <white>wave " + d.best() : "<gray>You haven't tried it yet.";
            else if (d.clears() > 0) stats = "<gray>You've won</gray> <white>" + d.clears() + "×";
            else stats = "<gray>No wins yet.";
            Draw.textFit(g, stats, x + 8, yy, w - 16);
            yy += 11;
            Draw.text(g, "<bold>" + Draw.gradient(col, col2, waves ? "Top 5 waves" : "Most wins") + "</bold>", x + 8, yy);
            yy += 11;
            if (d.board().isEmpty()) {
                Draw.textFit(g, "<dark_gray>Nobody yet. Be the first!", x + 12, yy, w - 24);
            } else {
                for (int k = 0; k < rows; k++) {
                    String line = d.board().get(k);
                    String medal = k == 0 ? "<#FFD54F>" : k == 1 ? "<#CFD8DC>" : k == 2 ? "<#FFAB91>" : "<gray>";
                    Draw.textFit(g, medal + line, x + 12, yy + k * 10, w - 24);
                }
            }
            y += h + 4;
        }

        // Titles
        y = section(g, "Titles", 0xFFD54F, x, y + 4);
        if (dp.titles().isEmpty()) {
            y += Draw.wrapped(g, "<gray>Titles you earned show next to your name in chat and the tab list.", x, y, w, Draw.MUTED) + 4;
        } else {
            List<String> labels = new ArrayList<>(List.of("None"));
            List<Boolean> on = new ArrayList<>(List.of(dp.title().isEmpty()));
            List<Runnable> acts = new ArrayList<>();
            acts.add(() -> AbpsClient.send("dungeon", "title|"));
            for (String t : dp.titles()) {
                labels.add(t);
                on.add(t.equals(dp.title()));
                acts.add(() -> AbpsClient.send("dungeon", "title|" + t));
            }
            y = chips(g, mx, my, x, y, w, labels, on, 0xFFD54F, acts);
        }
        y += Draw.wrapped(g, "<dark_gray>Siege: right-click a tower blueprint while standing on a gold-centred pad to build or upgrade a tower.",
                x, y + 2, w, Draw.DIM);
        return y - y0 + 10;
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
