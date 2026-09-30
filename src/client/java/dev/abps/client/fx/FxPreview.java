package dev.abps.client.fx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;

/**
 * Development only. ./gradlew runClient -Pfxtest=name joins the dev server, plays one effect in front of the
 * player in third person, and takes screenshots through its life, then quits. Several names can be given with commas.
 */
public final class FxPreview {

    private static int ticks = -1;
    private static int index;
    private static int started = -1;

    private FxPreview() {
    }

    public static void init() {
        String which = System.getProperty("abps.fxtest");
        if (which == null || which.isEmpty()) return;
        String[] list = which.split(",");
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc, list));
    }

    /** How long each preview runs, and at which ticks to take screenshots. */
    private static int[] shots(String name) {
        return switch (name) {
            case "tree" -> new int[]{6, 20, 60, 150};
            case "pillar", "vortex" -> new int[]{4, 12, 30};
            default -> new int[]{2, 5, 9, 14};
        };
    }

    private static void play(String name, Vec3 feet, Vec3 flat) {
        Vec3 ahead = feet.add(flat.scale(name.equals("tree") ? 11 : 6));
        Vec3 up = new Vec3(0, 1, 0);
        switch (name) {
            case "ink" -> FxEffects.slash(new double[]{feet.x, feet.y, feet.z, flat.x, 0, flat.z, 2.2, 3.6, 0.12}, new int[]{0xFFFFFF}, Skin.of(Skin.ASSASSIN), Skin.ASSASSIN);
            case "tree" -> Signatures.play(new double[]{ahead.x + flat.z * 5, ahead.y, ahead.z - flat.x * 5, flat.x, 0, flat.z, ahead.x, ahead.y, ahead.z}, new int[]{Skin.DRUID, 6, -1, -1, 0x76FF03, 0x1B5E20});
            case "ring" -> FxEffects.ring(new double[]{ahead.x, ahead.y + 0.1, ahead.z, 0, 1, 0, 0.5, 5, 0.15}, new int[]{0, 14, 0xFF7043}, Skin.of(Skin.PYRO));
            case "pillar" -> FxEffects.pillar(new double[]{ahead.x, ahead.y, ahead.z, 0.8, 7}, new int[]{8, 20, 8, 0x64FFDA}, Skin.of(Skin.NECRO));
            case "vortex" -> FxEffects.vortex(new double[]{ahead.x, ahead.y, ahead.z, 3, 0.2, 1.5}, new int[]{12, 40, 0x29B6F6}, Skin.of(Skin.WIND));
            case "beam" -> FxEffects.beam(new double[]{feet.x, feet.y + 1.3, feet.z, ahead.x + flat.z * 3, ahead.y + 1.5, ahead.z - flat.x * 3, 0.12}, new int[]{8, 0x9CCC65}, Skin.of(Skin.ARCHER));
            case "line" -> Ribbon.along(Ribbon.line(feet.add(0, 1.3, 0), ahead.add(flat.z * 3, 1.5, -flat.x * 3), up)).energy(0x9CCC65).width(0.3f).time(40, 4).play();
            case "sphere" -> FxEffects.sphere(new double[]{ahead.x, ahead.y + 1.5, ahead.z, 0.4, 2.8, 0.15}, new int[]{40, 14, 0xE53935}, Skin.of(Skin.VAMPIRE));
            default -> FxEffects.slash(new double[]{feet.x, feet.y, feet.z, flat.x, 0, flat.z, 2.2, 3.6, 0.12}, new int[]{0xFF3050}, Skin.of(Skin.NONE), Skin.NONE);
        }
    }

    private static void tick(Minecraft mc, String[] list) {
        if (mc.player == null || !FxSystem.ready || !FxSprites.loaded()) return;
        ticks++;
        if (ticks == 1) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.connection.sendCommand("time set noon");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
        if (ticks < 60) mc.player.setXRot(-12);
        if (ticks < 60) return;
        if (index >= list.length) {
            mc.stop();
            return;
        }
        String name = list[index];
        int[] shots = shots(name);
        if (started < 0) {
            started = ticks;
            Vec3 look = mc.player.getLookAngle();
            play(name, mc.player.position(), new Vec3(look.x, 0, look.z).normalize());
        }
        int age = ticks - started;
        for (int s : shots) if (age == s) Screenshot.grab(mc, false);
        if (age > shots[shots.length - 1] + 20) {
            index++;
            started = -1;
        }
    }
}
