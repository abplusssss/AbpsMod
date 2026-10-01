package dev.abps;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Inv;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Kill streaks and bounties. Every few player kills in a row is announced to the server. From the bounty streak on,
 * the player has a price on their head: whoever ends the streak gets paid, more for longer streaks.
 */
public final class Streaks {

    private Streaks() {
    }

    private static Config cfg() {
        return AbpsMod.config();
    }

    /** How much a streak of this length is worth to whoever ends it, or 0 if it has no bounty yet. */
    public static int bounty(int streak) {
        if (!cfg().streaksEnabled || streak < cfg().bountyStartsAt) return 0;
        return Math.min(cfg().bountyMax, 1 + (streak - cfg().bountyStartsAt));
    }

    private static String item() {
        return Inv.item(cfg().bountyItem).getDefaultInstance().getHoverName().getString();
    }

    /** A player killed another player. lostStreak is the victim's streak before it was reset. */
    public static void onPlayerKill(ServerPlayer killer, PlayerData kd, ServerPlayer victim, int lostStreak) {
        if (!cfg().streaksEnabled) return;
        Service s = AbpsMod.service();
        ServerLevel level = (ServerLevel) killer.level();
        String kn = killer.getName().getString(), vn = victim.getName().getString();

        int pay = bounty(lostStreak);
        if (pay > 0) {
            Inv.give(killer, new ItemStack(Inv.item(cfg().bountyItem)), pay);
            s.broadcast("<gold>☠ <white>" + kn + "</white> ended <white>" + vn + "</white>'s <red>" + lostStreak
                    + " kill streak</red> and claimed the bounty: <aqua>" + pay + " " + item() + "</aqua>!", null);
            s.banner(killer, "<gold><bold>BOUNTY CLAIMED</bold>", "<aqua>+" + pay + " " + item(), 0xFFD54F, 50);
            Fx.sound(level, killer, SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
        } else if (lostStreak >= 3) {
            s.broadcast("<gray>" + kn + " ended " + vn + "'s " + lostStreak + " kill streak.", null);
        }

        int streak = kd.killStreak;
        boolean milestone = streak == 3 || streak == 5 || streak == 10 || (streak > 10 && streak % 5 == 0);
        if (!milestone) return;
        String name = switch (streak) {
            case 3 -> "on a roll";
            case 5 -> "unstoppable";
            case 10 -> "legendary";
            default -> "godlike";
        };
        String bountyNote = bounty(streak) > 0 ? " <gold>Bounty: " + bounty(streak) + " " + item() + ".</gold>" : "";
        s.broadcast("<red>⚔ <white>" + kn + "</white> is " + name + "! <white>" + streak + "</white> kills in a row." + bountyNote, null);
        s.banner(killer, "<red><bold>" + streak + " KILL STREAK</bold>", "<gray>You are " + name
                + (bounty(streak) > 0 ? ". <gold>There's a price on your head." : "."), 0xFF5252, 50);
        Fx.sound(level, killer, SoundEvents.RAID_HORN.value(), 0.6f, 1.4f);
    }
}
