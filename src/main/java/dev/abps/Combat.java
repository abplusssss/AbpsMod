package dev.abps;

import dev.abps.data.PlayerData;
import dev.abps.util.Text;
import net.minecraft.server.level.ServerPlayer;

/**
 * Combat tag: hitting or being hit by a player puts you "in combat" for a few seconds.
 * You can't teleport while in combat, and leaving the game while in combat locks you out for a while.
 */
public final class Combat {

    private Combat() {
    }

    public static void tag(ServerPlayer a, ServerPlayer b) {
        tagOne(a);
        tagOne(b);
    }

    private static void tagOne(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        PlayerData d = AbpsMod.data().get(p);
        boolean was = d.inCombat();
        d.combatUntil = System.currentTimeMillis() + AbpsMod.config().combatTagSeconds * 1000L;
        if (!was) {
            AbpsMod.service().actionBar(p, "<red><bold>⚔ You are in combat!</bold> <gray>Don't log out.");
            Teleports.cancelWarmup(p, d, "<red>Teleport cancelled. You are in combat.");
        }
    }

    /** Called when a player leaves. Returns true if they got locked out. */
    public static boolean onQuit(ServerPlayer p) {
        PlayerData d = AbpsMod.data().peek(p.getUUID());
        int minutes = AbpsMod.config().combatLogBanMinutes;
        if (d == null || !d.inCombat() || minutes <= 0 || AbpsMod.server().isStopped()) return false;
        long until = System.currentTimeMillis() + minutes * 60_000L;
        AbpsMod.state().ban(p.getUUID(), until);
        AbpsMod.service().broadcast("<red>" + p.getName().getString() + " logged out in combat and can't join for " + minutes + " minutes.", p);
        AbpsMod.LOGGER.info("{} combat logged and is locked out for {} minutes", p.getName().getString(), minutes);
        return true;
    }

    /** Returns the kick message if this player is still locked out, or null. */
    public static String lockoutMessage(ServerPlayer p) {
        long until = AbpsMod.state().banUntil(p.getUUID());
        if (until <= 0) return null;
        return "<red><bold>You logged out during combat.</bold>\n\n<gray>You can join again in <white>"
                + Text.time(until - System.currentTimeMillis()) + "<gray>.";
    }
}
