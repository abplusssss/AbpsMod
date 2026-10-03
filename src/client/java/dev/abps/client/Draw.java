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

    /**
     * A card or row, sunk into the window like an inventory slot: a colored edge, then a dark line along the top and
     * left inside it and a faint light line along the bottom and right.
     */
    public static void framed(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        if (w <= 0 || h <= 0) return;
        // The edge is drawn as four strips, not one filled box under the body, so a see-through body never picks up
        // the edge's color (which used to tint whole cards the same color as their text)
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (w > 6 && h > 6) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x48000000);
            g.fill(x + 1, y + 2, x + 2, y + h - 1, 0x30000000);
            g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0x16FFFFFF);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0x10FFFFFF);
        }
    }

    /**
     * A window in the style of Minecraft's item tooltips: a black outline with cut corners, a border that fades from
     * c1 at the top to c2 at the bottom, a dark body and a soft bevel inside.
     */
    public static void window(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2, int body) {
        panel(g, x - 1, y - 1, w + 2, h + 2, 0xFF050507);
        panel(g, x, y, w, h, body);
        int top = argb(c1, 0xD0), bottom = argb(c2, 0xA0);
        g.fill(x + 1, y, x + w - 1, y + 1, top);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, bottom);
        g.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
        g.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x1CFFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x50000000);
    }

    /**
     * A button shaped like Minecraft's: black outline, a body tinted with color, a light edge on top and a dark edge
     * along the bottom. Hovering turns the outline white like vanilla buttons do. Disabled buttons are flat and dark.
     */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, boolean hover, boolean enabled) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, enabled && hover ? 0xFFFFFFFF : 0xFF000000);
        // The body is the color brought to the same darkness whatever it is, so white text reads on gold and yellow
        // buttons as well as it does on blue ones
        int base = enabled ? deep(color, 0.26f) : 0x26262C;
        if (enabled && hover) base = Text.lerp(base, 0xFFFFFF, 0.12f);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, opaque(base));
        if (!enabled || h < 5) return;
        g.fill(x + 1, y + 1, x + w - 1, y + 2, opaque(Text.lerp(base, 0xFFFFFF, 0.35f)));
        g.fill(x + 1, y + 2, x + 2, y + h - 2, opaque(Text.lerp(base, 0xFFFFFF, 0.18f)));
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, opaque(Text.lerp(base, 0x000000, 0.3f)));
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, opaque(Text.lerp(base, 0x000000, 0.5f)));
    }

    /** How bright a color looks, 0 to 1. */
    public static float luma(int rgb) {
        return (0.299f * ((rgb >> 16) & 255) + 0.587f * ((rgb >> 8) & 255) + 0.114f * (rgb & 255)) / 255f;
    }

    /** The same hue brought to a set brightness, so text on top of it always has the same contrast. */
    public static int deep(int rgb, float target) {
        rgb &= 0xFFFFFF;
        float l = luma(rgb);
        if (l <= 0.001f) return Text.lerp(rgb, 0x808080, target);
        if (l > target) {
            float k = target / l;
            return ((int) (((rgb >> 16) & 255) * k) << 16) | ((int) (((rgb >> 8) & 255) * k) << 8) | (int) ((rgb & 255) * k);
        }
        return Text.lerp(rgb, 0xFFFFFF, Math.min(1f, (target - l) / Math.max(0.001f, 1 - l)));
    }

    /** A dark card with a colored strip down its left side: the main card style in the menu. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, boolean strong) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, opaque(Text.lerp(0x15151C, accent & 0xFFFFFF, strong ? 0.10f : 0.04f)));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, opaque(Text.lerp(0x24242E, accent & 0xFFFFFF, strong ? 0.25f : 0.08f)));
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF0B0B10);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, opaque(strong ? accent : deep(accent, 0.22f)));
    }

    /** A thin line with a dark line under it, so it looks pressed into the window. */
    public static void divider(GuiGraphicsExtractor g, int x, int y, int w, int from, int to) {
        hGradient(g, x, y, w, 1, from, to);
        hGradient(g, x, y + 1, w, 1, 0x70000000, 0);
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

    /** A progress bar like the XP bar: a dark trough, a gradient fill with a light top row and a darker bottom row. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int c1, int c2) {
        g.fill(x, y, x + w, y + h, 0xD0000000);
        int filled = Math.round(w * Math.max(0, Math.min(1, progress)));
        if (filled > 0) {
            hGradient(g, x, y, filled, h, opaque(c1), opaque(c2));
            g.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
            if (h >= 4) g.fill(x, y + h - 1, x + filled, y + h, 0x50000000);
        }
        if (filled < w) g.fill(x + filled, y, x + w, y + 1, 0x18FFFFFF);
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

    /** Centered text that shrinks to fit a width (never below 60%), and is shortened with "…" if it still doesn't fit. */
    public static void centeredFit(GuiGraphicsExtractor g, String markup, int cx, int y, int width) {
        int w = font().width(Text.mm(markup));
        if (w <= width) {
            centered(g, markup, cx, y);
            return;
        }
        float scale = Math.max(0.6f, width / (float) Math.max(1, w));
        if (w * scale > width) {
            String p = fit(Text.strip(markup), width);
            plain(g, p, cx - font().width(p) / 2, y, TEXT);
            return;
        }
        scaled(g, markup, cx, y + (1 - scale) * 4, scale, true);
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
            case "windwalker" -> new String[]{"feather", "wind_charge", "rabbit_foot", "breeze_rod", "lightning_rod"};
            case "necromancer" -> new String[]{"zombie_head", "soul_lantern", "bone", "wither_skeleton_skull", "echo_shard"};
            case "cryomancer" -> new String[]{"ice", "packed_ice", "snowball", "powder_snow_bucket", "blue_ice"};
            case "chronomancer" -> new String[]{"amethyst_shard", "recovery_compass", "end_crystal", "sugar", "clock"};
            case "paladin" -> new String[]{"golden_sword", "glowstone_dust", "golden_horse_armor", "golden_apple", "beacon"};
            case "voidwalker" -> new String[]{"ender_pearl", "shulker_shell", "ender_eye", "phantom_membrane", "dragon_egg"};
            case "samurai" -> new String[]{"iron_sword", "bamboo", "shield", "blaze_powder", "netherite_sword"};
            case "overlord" -> new String[]{"lightning_rod", "wither_skeleton_skull", "ender_pearl", "clock", "nether_star"};
            case "harvester" -> new String[]{"wheat", "iron_hoe", "bone_meal", "golden_carrot", "hay_block"};
            case "chef" -> new String[]{"bread", "blaze_powder", "cake", "furnace", "golden_apple"};
            case "lumberjack" -> new String[]{"iron_axe", "oak_sapling", "oak_log", "leather_chestplate", "diamond_axe"};
            case "angler" -> new String[]{"fishing_rod", "cod", "nautilus_shell", "prismarine_shard", "heart_of_the_sea"};
            case "explorer" -> new String[]{"compass", "spyglass", "map", "leather_boots", "filled_map"};
            case "shark" -> new String[]{"trident", "prismarine_crystals", "heart_of_the_sea", "nautilus_shell", "conduit", "nether_star"};
            default -> new String[]{"barrier", "barrier", "barrier", "barrier", "nether_star"};
        };
        int slot = idx >= 6 ? icons.length - 1 : Math.max(0, Math.min(icons.length - 2, idx - 1));
        return "minecraft:" + icons[slot];
    }
}
