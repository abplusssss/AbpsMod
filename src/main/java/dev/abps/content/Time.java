package dev.abps.content;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * The time of day. 26.3 reworked world time into clocks and the method names moved, so this finds whichever one the
 * game has and falls back to the world's tick count (which matches the time of day unless it was changed).
 */
public final class Time {

    private Time() {
    }

    private static Method getter;
    private static boolean looked;

    public static long dayTime(Level level) {
        if (!looked) {
            looked = true;
            for (String name : new String[]{"getDayTime", "getOverworldClockTime", "getDefaultClockTime", "getClockTime", "getTimeOfDay"}) {
                try {
                    Method m = level.getClass().getMethod(name);
                    if (m.getReturnType() == long.class) {
                        getter = m;
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        if (getter != null) {
            try {
                return (long) getter.invoke(level);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return level.getGameTime();
    }

    /** Ticks into the current day (0 = sunrise, 13000 = night). */
    public static long ofDay(Level level) {
        return Math.floorMod(dayTime(level), 24000L);
    }

    /** Skips to the next morning and clears the weather, the same way the commands do it. */
    public static void morning(MinecraftServer server) {
        var src = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, "time set day");
        server.getCommands().performPrefixedCommand(src, "weather clear");
    }
}
