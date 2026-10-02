package dev.abps.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A small text formatter so messages can be written like "<gold>Hello <gradient:#FF0000:#0000FF>world</gradient>".
 * Supports: named colors, &lt;#hex&gt;, &lt;color:#hex&gt;, gradients, bold, italic, strikethrough, underlined, reset.
 */
public final class Text {

    private static final Map<String, Integer> NAMED = new HashMap<>();

    static {
        NAMED.put("black", 0x000000);
        NAMED.put("dark_blue", 0x0000AA);
        NAMED.put("dark_green", 0x00AA00);
        NAMED.put("dark_aqua", 0x00AAAA);
        NAMED.put("dark_red", 0xAA0000);
        NAMED.put("dark_purple", 0xAA00AA);
        NAMED.put("gold", 0xFFAA00);
        NAMED.put("gray", 0xAAAAAA);
        NAMED.put("dark_gray", 0x555555);
        NAMED.put("blue", 0x5555FF);
        NAMED.put("green", 0x55FF55);
        NAMED.put("aqua", 0x55FFFF);
        NAMED.put("red", 0xFF5555);
        NAMED.put("light_purple", 0xFF55FF);
        NAMED.put("yellow", 0xFFFF55);
        NAMED.put("white", 0xFFFFFF);
    }

    private Text() {
    }

    /** Text styling at one point in the string. */
    private record State(Integer color, Boolean bold, Boolean italic, Boolean strike, Boolean underline) {
        static final State EMPTY = new State(null, null, null, null, null);

        Style style(Integer overrideColor) {
            Style s = Style.EMPTY;
            Integer c = overrideColor != null ? overrideColor : color;
            if (c != null) s = s.withColor(TextColor.fromRgb(c));
            if (bold != null) s = s.withBold(bold);
            if (italic != null) s = s.withItalic(italic);
            if (strike != null) s = s.withStrikethrough(strike);
            if (underline != null) s = s.withUnderlined(underline);
            return s;
        }
    }

    private record Frame(String tag, State previous) {
    }

    public static MutableComponent mm(String input) {
        MutableComponent out = Component.empty();
        if (input == null || input.isEmpty()) return out;
        parse(input, out);
        return out;
    }

    /** Removes all tags. */
    public static String strip(String input) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char ch = input.charAt(i);
            if (ch == '<') {
                int end = input.indexOf('>', i);
                if (end > i && isTag(input.substring(i + 1, end))) {
                    i = end + 1;
                    continue;
                }
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    public static int parseColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            Integer named = NAMED.get(hex.toLowerCase(Locale.ROOT));
            return named == null ? 0xFFFFFF : named;
        }
    }

    private static boolean isTag(String body) {
        String b = body.toLowerCase(Locale.ROOT);
        if (b.startsWith("/")) b = b.substring(1);
        if (b.startsWith("!")) b = b.substring(1);
        if (b.startsWith("#") && b.length() == 7) return true;
        int colon = b.indexOf(':');
        String name = colon >= 0 ? b.substring(0, colon) : b;
        return NAMED.containsKey(name) || switch (name) {
            case "color", "c", "gradient", "bold", "b", "italic", "i", "em", "strikethrough", "st",
                 "underlined", "u", "reset" -> true;
            default -> false;
        };
    }

    private static void parse(String input, MutableComponent out) {
        Deque<Frame> stack = new ArrayDeque<>();
        State state = State.EMPTY;
        StringBuilder run = new StringBuilder();
        // Gradient info while inside a gradient
        int[] gradColors = null;
        int gradLength = 0;
        int gradIndex = 0;

        int i = 0;
        while (i < input.length()) {
            char ch = input.charAt(i);
            if (ch == '<') {
                int end = input.indexOf('>', i);
                if (end > i) {
                    String body = input.substring(i + 1, end);
                    if (isTag(body)) {
                        flush(out, run, state, null);
                        String lower = body.toLowerCase(Locale.ROOT);
                        if (lower.startsWith("/")) {
                            String name = baseName(lower.substring(1));
                            if (name.equals("gradient")) gradColors = null;
                            // Pop back to the matching tag
                            while (!stack.isEmpty()) {
                                Frame f = stack.pop();
                                state = f.previous;
                                if (f.tag.equals(name) || (isColorTag(f.tag) && isColorTag(name))) break;
                            }
                        } else if (lower.equals("reset")) {
                            stack.clear();
                            state = State.EMPTY;
                            gradColors = null;
                        } else if (lower.startsWith("gradient")) {
                            String[] parts = body.split(":");
                            int[] cols = new int[Math.max(2, parts.length - 1)];
                            for (int k = 1; k < parts.length; k++) cols[k - 1] = parseColor(parts[k]);
                            if (parts.length == 2) cols[1] = cols[0];
                            int close = input.toLowerCase(Locale.ROOT).indexOf("</gradient>", end);
                            String inner = close < 0 ? input.substring(end + 1) : input.substring(end + 1, close);
                            gradColors = cols;
                            gradLength = Math.max(1, strip(inner).length());
                            gradIndex = 0;
                            stack.push(new Frame("gradient", state));
                        } else {
                            boolean negate = lower.startsWith("!");
                            String tag = negate ? lower.substring(1) : lower;
                            String name = baseName(tag);
                            stack.push(new Frame(name, state));
                            state = apply(state, tag, !negate);
                        }
                        i = end + 1;
                        continue;
                    }
                }
            }
            if (gradColors != null) {
                // Every character gets its own color in a gradient
                flush(out, run, state, null);
                float t = gradLength <= 1 ? 0 : (float) gradIndex / (gradLength - 1);
                run.append(ch);
                flush(out, run, state, lerpColors(gradColors, t));
                gradIndex++;
            } else {
                run.append(ch);
            }
            i++;
        }
        flush(out, run, state, null);
    }

    private static boolean isColorTag(String name) {
        return name.equals("color") || name.equals("c") || name.startsWith("#") || NAMED.containsKey(name);
    }

    private static String baseName(String tag) {
        int colon = tag.indexOf(':');
        return colon >= 0 ? tag.substring(0, colon) : tag;
    }

    private static State apply(State s, String tag, boolean on) {
        String name = baseName(tag);
        if (tag.startsWith("#")) return new State(parseColor(tag), s.bold, s.italic, s.strike, s.underline);
        if (NAMED.containsKey(name)) return new State(NAMED.get(name), s.bold, s.italic, s.strike, s.underline);
        return switch (name) {
            case "color", "c" -> new State(parseColor(tag.substring(tag.indexOf(':') + 1)), s.bold, s.italic, s.strike, s.underline);
            case "bold", "b" -> new State(s.color, on, s.italic, s.strike, s.underline);
            case "italic", "i", "em" -> new State(s.color, s.bold, on, s.strike, s.underline);
            case "strikethrough", "st" -> new State(s.color, s.bold, s.italic, on, s.underline);
            case "underlined", "u" -> new State(s.color, s.bold, s.italic, s.strike, on);
            default -> s;
        };
    }

    private static void flush(MutableComponent out, StringBuilder run, State state, Integer color) {
        if (run.isEmpty()) return;
        out.append(Component.literal(run.toString()).withStyle(state.style(color)));
        run.setLength(0);
    }

    public static int lerpColors(int[] colors, float t) {
        if (colors.length == 1) return colors[0];
        float scaled = t * (colors.length - 1);
        int idx = Math.min(colors.length - 2, (int) scaled);
        return lerp(colors[idx], colors[idx + 1], scaled - idx);
    }

    public static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    // ---- Number formatting ----
    public static String num(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf(Math.round(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public static String pct(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    public static String mult(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return "x" + s;
    }

    /** A MiniMessage color tag for an RGB color, like <#FF6D00>. */
    public static String colorTag(int rgb) {
        return String.format("<#%06X>", rgb & 0xFFFFFF);
    }

    public static String time(long ms) {
        if (ms < 60_000) return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
        long sec = ms / 1000;
        return (sec / 60) + "m " + (sec % 60) + "s";
    }

    public static String seconds(double s) {
        if (s >= 60) {
            long sec = Math.round(s);
            return (sec / 60) + "m" + (sec % 60 == 0 ? "" : " " + (sec % 60) + "s");
        }
        return num(s) + "s";
    }
}
