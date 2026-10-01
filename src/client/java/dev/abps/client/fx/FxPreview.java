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
        if (name.startsWith("sig")) {
            // sig:theme:slot or sig:theme:slot:length, screenshots spread over the length
            String[] p = name.split(":");
            int len = p.length > 3 ? Integer.parseInt(p[3]) : 16;
            return new int[]{Math.max(2, len / 8), len / 3, len * 2 / 3, len};
        }
        return switch (name) {
            case "tree" -> new int[]{6, 20, 60, 150};
            case "pillar", "vortex" -> new int[]{4, 12, 30};
            case "gust" -> new int[]{2, 4, 6, 9};
            default -> new int[]{2, 5, 9, 14};
        };
    }

    private static Vec3 flatOf(Minecraft mc) {
        Vec3 look = mc.player.getLookAngle();
        return new Vec3(look.x, 0, look.z).normalize();
    }

    private static void play(String name, Vec3 feet, Vec3 flat) {
        Vec3 ahead = feet.add(flat.scale(name.equals("tree") ? 11 : 6));
        Vec3 up = new Vec3(0, 1, 0);
        if (name.startsWith("sig")) {
            String[] p = name.split(":");
            var pl = Minecraft.getInstance().player;
            Vec3 lk = pl.getLookAngle();
            int theme = Integer.parseInt(p[1]), slot = Integer.parseInt(p[2]);
            // Aim at the nearest mob, standing in for the target; its feet are the aimed ground
            net.minecraft.world.entity.LivingEntity mob = null;
            for (var e : pl.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, pl.getBoundingBox().inflate(16))) {
                if (e != pl && (mob == null || e.distanceToSqr(pl) < mob.distanceToSqr(pl))) mob = e;
            }
            Vec3 aim = mob != null ? mob.position() : ahead.add(flat.z * 2.5, 0, -flat.x * 2.5);
            // Cast across the screen instead of straight away from the camera, so lines and waves can be seen
            Vec3 side = FxKit.rotY(new Vec3(lk.x, 0, lk.z).normalize(), Boolean.getBoolean("abps.fxvideo") ? 0.45 : 0.9).add(0, lk.y, 0).normalize();
            Signatures.play(new double[]{feet.x, feet.y, feet.z, side.x, side.y, side.z, aim.x, aim.y, aim.z},
                    new int[]{theme, slot, pl.getId(), mob == null ? -1 : mob.getId(), Skin.of(theme).accent, Skin.of(theme).accent2,
                            p.length > 3 ? Math.max(20, Integer.parseInt(p[3]) - 10) : 100});
            return;
        }
        switch (name) {
            case "ink" -> FxEffects.slash(new double[]{feet.x, feet.y, feet.z, flat.x, 0, flat.z, 2.2, 3.6, 0.12}, new int[]{0xFFFFFF}, Skin.of(Skin.ASSASSIN), Skin.ASSASSIN);
            case "ring" -> FxEffects.ring(new double[]{ahead.x, ahead.y + 0.1, ahead.z, 0, 1, 0, 0.5, 5, 0.15}, new int[]{0, 14, 0xFF7043}, Skin.of(Skin.PYRO));
            case "pillar" -> FxEffects.pillar(new double[]{ahead.x, ahead.y, ahead.z, 0.8, 7}, new int[]{8, 20, 8, 0x64FFDA}, Skin.of(Skin.NECRO));
            case "vortex" -> FxEffects.vortex(new double[]{ahead.x, ahead.y, ahead.z, 3, 0.2, 1.5}, new int[]{12, 40, 0x29B6F6}, Skin.of(Skin.WIND));
            case "beam" -> FxEffects.beam(new double[]{feet.x, feet.y + 1.3, feet.z, ahead.x + flat.z * 3, ahead.y + 1.5, ahead.z - flat.x * 3, 0.12}, new int[]{8, 0x9CCC65}, Skin.of(Skin.ARCHER));
            case "gust" -> {
                var pl = net.minecraft.client.Minecraft.getInstance().player;
                Vec3 lk = pl.getLookAngle();
                Signatures.play(new double[]{feet.x, feet.y, feet.z, lk.x, lk.y, lk.z, ahead.x, ahead.y, ahead.z}, new int[]{Skin.WIND, 2, pl.getId(), -1, 0xE0F7FA, 0x29B6F6});
            }
            case "line" -> Ribbon.along(Ribbon.line(feet.add(0, 1.3, 0), ahead.add(flat.z * 3, 1.5, -flat.x * 3), up)).energy(0x9CCC65).width(0.3f).time(40, 4).play();
            case "sphere" -> FxEffects.sphere(new double[]{ahead.x, ahead.y + 1.5, ahead.z, 0.4, 2.8, 0.15}, new int[]{40, 14, 0xE53935}, Skin.of(Skin.VAMPIRE));
            default -> FxEffects.slash(new double[]{feet.x, feet.y, feet.z, flat.x, 0, flat.z, 2.2, 3.6, 0.12}, new int[]{0xFF3050}, Skin.of(Skin.NONE), Skin.NONE);
        }
    }

    private static void tick(Minecraft mc, String[] list) {
        if (mc.player == null || !FxSystem.ready || !FxSprites.loaded()) return;
        ticks++;
        // Recording must not stop if the window loses focus: no pause menu, and close anything that opens
        mc.options.pauseOnLostFocus = false;
        if (Boolean.getBoolean("abps.fxvideo") && mc.gui.screen() != null) mc.gui.setScreen(null);
        if (ticks == 1) {
            if (Boolean.getBoolean("abps.fxvideo")) {
                // A clean stage: no dropped items or old mobs, and a still zombie to aim at, ahead and to the right
                mc.player.connection.sendCommand("kill @e[type=!player]");
                // Earlier tests dig holes in the test world; patch the floor and clear the air above it
                mc.player.connection.sendCommand("fill -24 96 -24 24 99 24 minecraft:stone");
                mc.player.connection.sendCommand("fill -24 100 -24 24 115 24 minecraft:air");
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.connection.sendCommand("time set noon");
            mc.player.connection.sendCommand("weather clear");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
        if (ticks == 20 && Boolean.getBoolean("abps.fxvideo")) {
            // Seven blocks ahead on the ground, a little to the left (local ^ coordinates would follow the downward look into the floor)
            Vec3 f = flatOf(mc), at = mc.player.position().add(f.scale(7)).add(f.z, 0, -f.x);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "summon husk %.2f %.2f %.2f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}",
                    at.x, mc.player.getY(), at.z));
        }
        if (ticks == 35 && Boolean.getBoolean("abps.fxvideo")) mc.player.connection.sendCommand("kill @e[type=!player,type=!husk]");
        if (ticks < 60) mc.player.setXRot(list[0].startsWith("sig") ? 22 : -12);
        if (ticks < 60) return;
        if (index >= list.length) {
            mc.stop();
            return;
        }
        String name = list[index];
        String base = name.split("[+]")[0];
        int[] shots = shots(base);
        if (started < 0) {
            started = ticks;
            Vec3 look = mc.player.getLookAngle();
            play(base, mc.player.position(), new Vec3(look.x, 0, look.z).normalize());
        }
        int age = ticks - started;
        if (Boolean.getBoolean("abps.fxvideo")) {
            // Every tick is a frame of the clip, named so the frames sort in order
            if (age <= shots[shots.length - 1] + 6) {
                String clip = String.format("%02d_%s", index, name.replaceAll("[^A-Za-z0-9]+", "-"));
                Screenshot.grab(mc.gameDirectory, "clip_" + clip + "_" + String.format("%04d", age) + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> {
                });
            }
        } else {
            for (int s : shots) if (age == s) Screenshot.grab(mc, false);
        }
        // Cues that follow the cast: name+cue@start/every, for example sig:10:6:120+11@10/10
        String[] parts = name.split("[+]");
        for (int k = 1; k < parts.length; k++) {
            String[] cue = parts[k].split("[@/]");
            int slot = Integer.parseInt(cue[0]), from = Integer.parseInt(cue[1]), every = cue.length > 2 ? Integer.parseInt(cue[2]) : 9999;
            if (age >= from && (age - from) % every == 0) play(base.replaceFirst(":[0-9]+(:[0-9]+)?$",":" + slot), mc.player.position(), flatOf(mc));
        }
        if (age > shots[shots.length - 1] + 20) {
            index++;
            started = -1;
        }
    }
}
