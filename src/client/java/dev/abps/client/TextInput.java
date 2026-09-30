package dev.abps.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A small text box drawn to match the menus. Click it to type. Can be limited to numbers, which is what the shop
 * uses for amounts and prices.
 */
public final class TextInput {

    private final int maxLength;
    private final boolean numbers;
    private String text = "";
    private boolean focused;
    private int x, y, w, h;

    public TextInput(int maxLength, boolean numbers) {
        this.maxLength = maxLength;
        this.numbers = numbers;
    }

    public String text() {
        return text;
    }

    public void set(String t) {
        text = t == null ? "" : t.length() > maxLength ? t.substring(0, maxLength) : t;
    }

    public int number(int fallback) {
        try {
            return text.isEmpty() ? fallback : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void setNumber(int n) {
        text = String.valueOf(n);
    }

    public boolean focused() {
        return focused;
    }

    public void focus(boolean f) {
        focused = f;
    }

    public void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, String placeholder) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Draw.framed(g, x, y, w, h, 0xF0060609, focused ? Draw.opaque(accent) : 0xFF3A3A44);
        boolean blink = focused && (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = text.isEmpty() && !focused ? "<dark_gray>" + placeholder : "<white>" + text + (blink ? "_" : "");
        Draw.text(g, shown, x + 4, y + (h - 8) / 2);
    }

    /** Focuses the box when it is clicked, and unfocuses it when anything else is. Returns true if the box was clicked. */
    public boolean click(double mx, double my) {
        focused = w > 0 && Draw.inside(mx, my, x, y, w, h);
        return focused;
    }

    public boolean charTyped(CharacterEvent e) {
        if (!focused) return false;
        String c = e.codepointAsString();
        if (numbers ? !c.matches("[0-9]") : !e.isAllowedChatCharacter() || c.equals("|") || c.equals("<") || c.equals(">")) return true;
        if (text.length() < maxLength) text += c;
        if (numbers && text.length() > 1 && text.startsWith("0")) text = text.substring(1);
        return true;
    }

    public boolean keyPressed(KeyEvent e) {
        if (!focused) return false;
        if (e.key() == InputConstants.KEY_BACKSPACE) {
            if (!text.isEmpty()) text = e.hasControlDown() ? "" : text.substring(0, text.length() - 1);
            return true;
        }
        if (e.isPaste()) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            for (int i = 0; i < clip.length(); i++) charTyped(new CharacterEvent(clip.charAt(i)));
            return true;
        }
        if (e.isConfirmation() || e.isEscape()) {
            focused = false;
            return true;
        }
        // Swallow other keys so typing a letter doesn't also trigger a keybind
        return !e.isCycleFocus();
    }
}
