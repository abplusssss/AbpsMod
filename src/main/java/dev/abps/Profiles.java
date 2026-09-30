package dev.abps;

import dev.abps.data.PlayerData;
import dev.abps.net.Net;
import dev.abps.util.Fx;
import dev.abps.util.Inv;
import dev.abps.util.Tasks;
import dev.abps.util.Text;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The Profile tab (stats and daily rewards) and the Travel tab (homes and teleports). */
public final class Profiles {

    private Profiles() {
    }

    private static Service s() {
        return AbpsMod.service();
    }

    private static long today() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    private static long msToTomorrow() {
        long next = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        return Math.max(0, next - System.currentTimeMillis());
    }

    // ================= Profile =================

    public static void sendProfile(ServerPlayer p) {
        if (!Service.hasMod(p)) return;
        PlayerData d = s().data(p);
        List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        double kd = d.deaths == 0 ? d.kills : d.kills / (double) d.deaths;
        stat(labels, values, "Player kills", String.valueOf(d.kills));
        stat(labels, values, "Deaths", String.valueOf(d.deaths));
        stat(labels, values, "K/D", String.format(java.util.Locale.ROOT, "%.2f", kd));
        stat(labels, values, "Best kill streak", String.valueOf(d.bestStreak));
        stat(labels, values, "Mobs killed", String.valueOf(d.mobKills));
        stat(labels, values, "Damage dealt", Text.num(d.damageDealt / 2) + " hearts");
        stat(labels, values, "Damage taken", Text.num(d.damageTaken / 2) + " hearts");
        stat(labels, values, "Abilities used", String.valueOf(d.abilitiesUsed));
        stat(labels, values, "Ultimates used", String.valueOf(d.ultsUsed));
        stat(labels, values, "Rerolls", String.valueOf(d.rerolls));
        stat(labels, values, "Time played", playtime(d.playSeconds));
        boolean can = AbpsMod.config().dailyRewardsEnabled && d.lastDaily < today();
        ServerPlayNetworking.send(p, new Net.ProfilePayload(p.getName().getString(), labels, values, currentStreak(d), can,
                can ? 0 : msToTomorrow(), rewardStacks(), AbpsMod.config().dailyRewardsEnabled));
    }

    private static void stat(List<String> labels, List<String> values, String label, String value) {
        labels.add(label);
        values.add(value);
    }

    private static String playtime(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    /** The streak as it stands today: it resets if a day was missed. */
    private static int currentStreak(PlayerData d) {
        long t = today();
        return d.lastDaily >= t - 1 ? d.dailyStreak : 0;
    }

    private static List<List<ItemStack>> rewardStacks() {
        List<List<ItemStack>> out = new ArrayList<>();
        for (Map<String, Integer> day : AbpsMod.config().dailyRewards) {
            List<ItemStack> items = new ArrayList<>();
            for (Map.Entry<String, Integer> e : day.entrySet()) {
                var item = Inv.item(e.getKey());
                if (item != net.minecraft.world.item.Items.AIR) items.add(new ItemStack(item, Math.max(1, Math.min(99, e.getValue()))));
            }
            out.add(items);
        }
        return out;
    }

    public static void claimDaily(ServerPlayer p) {
        PlayerData d = s().data(p);
        if (!AbpsMod.config().dailyRewardsEnabled || AbpsMod.config().dailyRewards.isEmpty()) {
            s().send(p, "<red>Daily rewards are turned off on this server.");
            return;
        }
        long t = today();
        if (d.lastDaily >= t) {
            s().send(p, "<gray>You already claimed today's reward. Come back in <white>" + Text.time(msToTomorrow()) + "</white>.");
            return;
        }
        int days = AbpsMod.config().dailyRewards.size();
        int streak = d.lastDaily == t - 1 ? d.dailyStreak % days + 1 : 1;
        d.lastDaily = t;
        d.dailyStreak = streak;
        for (Map.Entry<String, Integer> e : AbpsMod.config().dailyRewards.get(streak - 1).entrySet()) {
            var item = Inv.item(e.getKey());
            if (item != net.minecraft.world.item.Items.AIR) Inv.give(p, new ItemStack(item), Math.max(1, e.getValue()));
        }
        AbpsMod.data().save(p, d);
        s().send(p, "<gold><bold>Daily reward!</bold></gold> <gray>Day <white>" + streak + "</white> of " + days
                + (streak == days ? ". <gold>Full streak!" : ". Come back tomorrow to keep your streak."));
        s().banner(p, "<gradient:#FFD54F:#FF8F00><bold>DAY " + streak + " REWARD</bold></gradient>", "<gray>Come back tomorrow for day " + (streak % days + 1), 0xFFB300, 40);
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
        sendProfile(p);
    }

    /** Reminds players on join that a reward is waiting. */
    public static void onJoin(ServerPlayer p) {
        PlayerData d = s().data(p);
        if (AbpsMod.config().dailyRewardsEnabled && d.lastDaily < today()) {
            s().send(p, "<gold>Your daily reward is ready!</gold> <gray>Type <yellow>!Daily</yellow> or open the Profile tab.");
        }
    }

    // ================= Travel =================

    public static void sendTravel(ServerPlayer p) {
        if (!Service.hasMod(p)) return;
        PlayerData d = s().data(p);
        List<Net.HomeInfo> homes = new ArrayList<>();
        for (Map.Entry<String, PlayerData.Loc> e : d.homes.entrySet()) {
            PlayerData.Loc l = e.getValue();
            String dim = l.dimension();
            dim = dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
            homes.add(new Net.HomeInfo(e.getKey(), dim, (int) Math.floor(l.x()), (int) Math.floor(l.y()), (int) Math.floor(l.z())));
        }
        List<String> online = new ArrayList<>();
        for (ServerPlayer o : AbpsMod.server().getPlayerList().getPlayers()) {
            if (o != p) online.add(o.getName().getString());
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);
        long now = System.currentTimeMillis();
        ServerPlayNetworking.send(p, new Net.TravelPayload(homes, Teleports.maxHomes(p), d.back != null, online, Teleports.requestsFor(p.getUUID()),
                Math.max(0, d.combatUntil - now), Math.max(0, d.teleportReadyAt - now)));
    }

    /** Travel tab buttons: "op|value". Every one goes through the same checks as the chat commands. */
    public static void travel(ServerPlayer p, String arg) {
        String[] a = arg.split("\\|", 2);
        String op = a[0], v = a.length > 1 ? a[1] : "";
        ServerPlayer other = v.isEmpty() ? null : AbpsMod.server().getPlayerList().getPlayerByName(v);
        switch (op) {
            case "list" -> {
            }
            case "home" -> Teleports.home(p, v.isEmpty() ? null : v);
            case "sethome" -> Teleports.setHome(p, v.isEmpty() ? "home" : v);
            case "delhome" -> Teleports.delHome(p, v);
            case "spawn" -> Teleports.spawn(p);
            case "back" -> Teleports.back(p);
            case "tpr", "tphere" -> {
                if (other == null) s().send(p, "<red>That player is not online.");
                else Teleports.request(p, other, op.equals("tphere"));
            }
            case "accept" -> Teleports.accept(p, v.isEmpty() ? null : v);
            case "deny" -> Teleports.deny(p, v.isEmpty() ? null : v);
            default -> {
            }
        }
        // Refresh the tab a moment later so it shows the result
        Tasks.later(2, () -> {
            if (!p.hasDisconnected()) sendTravel(p);
        });
    }
}
