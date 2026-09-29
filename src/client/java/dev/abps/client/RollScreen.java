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

/** A slot machine that spins through the classes and lands on your new one. */
public final class RollScreen extends Screen {

    private static final long SPIN_MS = 3000;
    private static final long CLOSE_MS = SPIN_MS + 2600;

    private final String result;
    private final long start = System.currentTimeMillis();
    private int lastIndex = -1;
    private boolean landed;

    public RollScreen(String result) {
        super(Component.literal("Rolling"));
        this.result = result;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<Net.ClassInfo> order() {
        List<Net.ClassInfo> list = new ArrayList<>(ClientState.catalog.values());
        // Put the result last so the reel stops on it
        list.removeIf(c -> c.id().equals(result));
        Net.ClassInfo r = ClientState.catalog.get(result);
        if (r != null) list.add(r);
        return list;
    }

    /** How far along the reel is, in slots. Slows down near the end. */
    private double position(long t, int count) {
        int total = count * 3 - 1; // three laps, ending on the last slot
        double f = Math.min(1.0, t / (double) SPIN_MS);
        double eased = 1 - Math.pow(1 - f, 3);
        return eased * total;
    }

    @Override
    public void tick() {
        long t = System.currentTimeMillis() - start;
        if (t > CLOSE_MS) onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        List<Net.ClassInfo> list = order();
        int cxm = width / 2, cym = height / 2;
        if (list.isEmpty()) {
            Draw.centered(g, "<gray>Rolling...", cxm, cym);
            return;
        }
        long t = System.currentTimeMillis() - start;
        int n = list.size();
        double pos = position(t, n);
        int index = (int) Math.round(pos) % n;
        if (index != lastIndex && t < SPIN_MS) {
            lastIndex = index;
            float pitch = 0.8f + (float) Math.min(1.0, t / (double) SPIN_MS) * 0.8f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), pitch, 0.6f));
        }
        if (t >= SPIN_MS && !landed) {
            landed = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f));
        }

        Net.ClassInfo current = landed ? list.getLast() : list.get(index);
        int c1 = current.color(), c2 = current.color2();

        // Dark backdrop tinted by the current class
        g.fill(0, 0, width, height, 0x90000000);
        g.fillGradient(0, cym - 70, width, cym, Draw.argb(c1, 0), Draw.argb(c1, 0x50));
        g.fillGradient(0, cym, width, cym + 70, Draw.argb(c2, 0x50), Draw.argb(c2, 0));

        // The reel: 5 slots visible, the middle one is the pick
        int slot = 52;
        double frac = pos - Math.floor(pos);
        if (landed) frac = 0;
        for (int k = -2; k <= 2; k++) {
            int idx = Math.floorMod((int) Math.floor(pos) + k, n);
            if (landed) idx = Math.floorMod(n - 1 + k, n);
            Net.ClassInfo info = list.get(idx);
            float x = (float) (cxm + (k - frac) * (slot + 8)) - slot / 2f;
            float dist = Math.abs((float) (k - frac));
            float scale = Math.max(0.55f, 1f - dist * 0.22f);
            int alpha = (int) (0xFF * Math.max(0.15f, 1f - dist * 0.4f));
            int size = (int) (slot * scale);
            int sx = (int) (x + (slot - size) / 2f), sy = cym - size / 2 - 10;
            Draw.framed(g, sx, sy, size, size, Draw.argb(0x101018, Math.min(0xF0, alpha)), Draw.argb(info.color(), alpha));
            Draw.item(g, info.icon(), sx + size / 2f - 8 * scale * 1.6f, sy + size / 2f - 8 * scale * 1.6f, scale * 1.6f);
        }
        // Frame around the middle slot
        int fx = cxm - slot / 2 - 3, fy = cym - slot / 2 - 13;
        float glow = landed ? Draw.pulse(1f) : 0.5f;
        g.outline(fx, fy, slot + 6, slot + 6, Draw.opaque(Text.lerp(c1, 0xFFFFFF, glow * 0.5f)));
        g.outline(fx - 1, fy - 1, slot + 8, slot + 8, Draw.argb(c2, 0x80));

        // Name under the reel
        float pop = landed ? 1f + Math.max(0, 0.6f - (t - SPIN_MS) / 400f) : 1f;
        String name = "<bold>" + Draw.gradient(c1, c2, current.name()) + "</bold>";
        Draw.scaled(g, name, cxm, cym + slot / 2f + 2, (landed ? 2.2f : 1.6f) * pop, true);
        if (landed) {
            Draw.scaled(g, "<gray><italic>" + current.tagline(), cxm, cym + slot / 2f + 26, 1f, true);
            Draw.scaled(g, "<dark_gray>Click to close. Open the menu with " + AbpsClient.menuKey.getTranslatedKeyMessage().getString() + ".", cxm, cym + slot / 2f + 40, 0.75f, true);
            // Sparkles flying out
            long since = t - SPIN_MS;
            for (int i = 0; i < 24; i++) {
                double ang = i * (Math.PI * 2 / 24) + i * 0.37;
                double r = 20 + since / 8.0 * (0.6 + (i % 5) * 0.12);
                int a = (int) Math.max(0, 0xFF - since / 5);
                if (a <= 0) break;
                int sx = (int) (cxm + Math.cos(ang) * r), sy = (int) (cym - 10 + Math.sin(ang) * r * 0.7);
                g.fill(sx, sy, sx + 2, sy + 2, Draw.argb(i % 2 == 0 ? c1 : c2, a));
            }
        } else {
            Draw.scaled(g, "<gray>Rolling your attribute...", cxm, cym - slot / 2f - 32, 1f, true);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        // No blur, we draw our own backdrop
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (landed) onClose();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!landed) return true; // can't skip the spin
        return super.keyPressed(e);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return landed;
    }
}
