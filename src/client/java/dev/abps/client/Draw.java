package dev.abps.client;

import dev.abps.util.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drawing helpers for the HUD and menus. All colors are 0xAARRGGBB. */
public final class Draw {

    public static final int PANEL = 0xE00E0E16;
    public static final int PANEL_LIGHT = 0xE01A1A26;
    public static final int LINE = 0x40FFFFFF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFAAAAB4;
    public static final int DIM = 0xFF6A6A76;
    public static final int GOOD = 0xFF69F0AE;
    public static final int BAD = 0xFFFF5252;
    public static final int GOLD = 0xFFFFD54F;

    private static final Map<String, ItemStack> ITEMS = new HashMap<>();

    private Draw() {
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** A panel with slightly cut corners so it looks rounded. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A panel with a thin colored border. */
    public static void framed(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        panel(g, x, y, w, h, border);
        panel(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** Left to right color gradient. */
    public static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int from, int to) {
        if (w <= 0) return;
        int step = Math.max(1, w / 48);
        for (int i = 0; i < w; i += step) {
            float t = w <= 1 ? 0 : (float) i / (w - 1);
            int rgb = Text.lerp(from & 0xFFFFFF, to & 0xFFFFFF, t);
            int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
            g.fill(x + i, y, Math.min(x + w, x + i + step), y + h, (a << 24) | rgb);
        }
    }

    /** A progress bar with a gradient fill. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int c1, int c2) {
        g.fill(x, y, x + w, y + h, 0xC0000000);
        int filled = Math.round(w * Math.max(0, Math.min(1, progress)));
        if (filled > 0) hGradient(g, x, y, filled, h, opaque(c1), opaque(c2));
        g.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    public static void text(GuiGraphicsExtractor g, String markup, int x, int y) {
        g.text(font(), Text.mm(markup), x, y, TEXT, true);
    }

    public static void text(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.text(font(), c, x, y, color, true);
    }

    public static void plain(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font(), s, x, y, color, true);
    }

    public static void centered(GuiGraphicsExtractor g, String markup, int cx, int y) {
        Component c = Text.mm(markup);
        g.text(font(), c, cx - font().width(c) / 2, y, TEXT, true);
    }

    /** Text drawn bigger or smaller. */
    public static void scaled(GuiGraphicsExtractor g, String markup, float x, float y, float scale, boolean center) {
        Component c = Text.mm(markup);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int ox = center ? -font().width(c) / 2 : 0;
        g.text(font(), c, ox, 0, TEXT, true);
        g.pose().popMatrix();
    }

    /** Wrapped text, returns the height used. */
    public static int wrapped(GuiGraphicsExtractor g, String markup, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font().split(Text.mm(markup), width);
        int yy = y;
        for (FormattedCharSequence line : lines) {
            g.text(font(), line, x, yy, color, true);
            yy += font().lineHeight + 1;
        }
        return yy - y;
    }

    public static int wrappedHeight(String markup, int width) {
        return font().split(Text.mm(markup), width).size() * (font().lineHeight + 1);
    }

    public static ItemStack item(String id) {
        return ITEMS.computeIfAbsent(id, key -> {
            Identifier i = Identifier.tryParse(key);
            Item item = i == null ? null : BuiltInRegistries.ITEM.getValue(i);
            return new ItemStack(item == null ? Items.BARRIER : item);
        });
    }

    public static void item(GuiGraphicsExtractor g, String id, float x, float y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.item(item(id), 0, 0);
        g.pose().popMatrix();
    }

    /** Plain text cut down to fit a width, ending in "…" when it had to be shortened. */
    public static String fit(String plain, int width) {
        if (width <= 0) return "";
        if (font().width(plain) <= width) return plain;
        return font().plainSubstrByWidth(plain, Math.max(0, width - font().width("…"))) + "…";
    }

    /** Text with markup scaled down just enough to fit a width (never below 60%), so long lines stay readable instead of running off. */
    public static void textFit(GuiGraphicsExtractor g, String markup, int x, int y, int width) {
        int w = font().width(Text.mm(markup));
        float scale = w <= width ? 1f : Math.max(0.6f, width / (float) w);
        if (scale >= 1f) {
            text(g, markup, x, y);
            return;
        }
        if (w * scale > width) {
            // Even at the smallest size it doesn't fit: drop the markup and shorten it
            plain(g, fit(Text.strip(markup), width), x, y, TEXT);
            return;
        }
        scaled(g, markup, x, y + (1 - scale) * 4, scale, false);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Pulses between 0 and 1 over time. */
    public static float pulse(float speed) {
        return (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * speed * Math.PI * 2));
    }

    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static String gradient(int c1, int c2, String text) {
        return "<gradient:" + hex(c1) + ":" + hex(c2) + ">" + text + "</gradient>";
    }

    /** An item icon for each ability, by class and ability number (1-5). */
    public static String abilityIcon(String classId, int idx) {
        // Five icons for most classes (four abilities and the ultimate), six for classes with a fifth ability
        String[] icons = switch (classId) {
            case "miner" -> new String[]{"diamond_pickaxe", "spyglass", "iron_pickaxe", "gold_ingot", "pointed_dripstone"};
            case "vampire" -> new String[]{"fermented_spider_eye", "iron_chain", "redstone", "crying_obsidian", "nether_wart"};
            case "berserker" -> new String[]{"blaze_powder", "rabbit_foot", "iron_sword", "goat_horn", "netherite_axe"};
            case "archer" -> new String[]{"arrow", "spectral_arrow", "lead", "crossbow", "end_rod"};
            case "tank" -> new String[]{"iron_chestplate", "bell", "shield", "netherite_chestplate", "iron_block"};
            case "assassin" -> new String[]{"glass_bottle", "phantom_membrane", "gunpowder", "ender_pearl", "netherite_sword"};
            case "pyromancer" -> new String[]{"fire_charge", "blaze_powder", "blaze_rod", "magma_block", "lava_bucket"};
            case "druid" -> new String[]{"golden_apple", "sweet_berries", "bone", "oak_log", "flowering_azalea"};
            case "windwalker" -> new String[]{"feather", "wind_charge", "rabbit_foot", "breeze_rod", "lightning_rod"};
            case "necromancer" -> new String[]{"zombie_head", "soul_lantern", "bone", "wither_skeleton_skull", "echo_shard"};
            case "shark" -> new String[]{"trident", "prismarine_crystals", "heart_of_the_sea", "nautilus_shell", "conduit", "nether_star"};
            default -> new String[]{"barrier", "barrier", "barrier", "barrier", "nether_star"};
        };
        int slot = idx >= 6 ? icons.length - 1 : Math.max(0, Math.min(icons.length - 2, idx - 1));
        return "minecraft:" + icons[slot];
    }
}
