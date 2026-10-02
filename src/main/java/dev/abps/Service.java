package dev.abps;

import dev.abps.classes.AttributeClass;
import dev.abps.data.PlayerData;
import dev.abps.net.Net;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import dev.abps.util.Tasks;
import dev.abps.util.Text;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Main game logic: giving attributes, levels, costs, abilities and ultimates. */
public final class Service {

    public static final String PREFIX = "<dark_gray>[<gradient:#FFD54F:#FF8F00>Abps</gradient>]</dark_gray> ";
    public static final String LINE = "<dark_gray><strikethrough>                                        </strikethrough>";

    private final MinecraftServer server;

    public Service(MinecraftServer server) {
        this.server = server;
    }

    private static Config cfg() {
        return AbpsMod.config();
    }

    public AttributeClass cls(PlayerData d) {
        AttributeClass c = Classes.get(d.classId);
        // Whatever effects the class's hooks make next are drawn in that class's style
        if (c != null) dev.abps.util.Vfx.theme(dev.abps.util.FxKind.theme(c.id()));
        return c;
    }

    /** The attribute whose powers are working right now: null while the player has turned their powers off. */
    public AttributeClass active(PlayerData d) {
        return d.powersOff ? null : cls(d);
    }

    /** Turns all of a player's attribute powers off or back on. */
    public void setPowersOff(ServerPlayer p, boolean off) {
        PlayerData d = data(p);
        if (d.powersOff == off) return;
        AttributeClass c = cls(d);
        if (off && c != null) c.cleanup(p, d);
        d.powersOff = off;
        reapply(p);
        AbpsMod.data().save(p, d);
        send(p, off ? "<gray>Your attribute powers are <red>off</red>. Abilities, passives and weaknesses do nothing until you turn them back on."
                : "<green>Your attribute powers are back on.");
    }

    public AttributeClass cls(ServerPlayer p) {
        return cls(AbpsMod.data().get(p));
    }

    public PlayerData data(ServerPlayer p) {
        return AbpsMod.data().get(p);
    }

    public static boolean hasMod(ServerPlayer p) {
        return ServerPlayNetworking.canSend(p, Net.SyncPayload.TYPE);
    }

    // ================= Messages =================
    public void send(ServerPlayer p, String markup) {
        p.sendSystemMessage(Text.mm(PREFIX + markup));
    }

    public void raw(ServerPlayer p, String markup) {
        p.sendSystemMessage(Text.mm(markup));
    }

    public static void send(CommandSourceStack s, String markup) {
        s.sendSystemMessage(Text.mm(PREFIX + markup));
    }

    public static void raw(CommandSourceStack s, String markup) {
        s.sendSystemMessage(Text.mm(markup));
    }

    public void actionBar(ServerPlayer p, String markup) {
        p.sendSystemMessage(Text.mm(markup), true);
    }

    public void title(ServerPlayer p, String title, String sub, int in, int stay, int out) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Text.mm(sub)));
        p.connection.send(new ClientboundSetTitleTextPacket(Text.mm(title)));
    }

    /** Big banner for modded clients, normal title for everyone else. */
    public void banner(ServerPlayer p, String title, String sub, int color, int ticks) {
        if (hasMod(p)) ServerPlayNetworking.send(p, new Net.BannerPayload(title, sub, color, ticks));
        else title(p, title, sub, 5, ticks, 12);
    }

    public void broadcast(String markup, ServerPlayer except) {
        Component c = Text.mm(PREFIX + markup);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != except) other.sendSystemMessage(c);
        }
    }

    // ================= Chat buttons for players without the mod =================
    private final Map<String, Runnable> tokens = new HashMap<>();

    /** A clickable chat button that runs code once. */
    public MutableComponent button(String label, String hover, Runnable action) {
        String token = Long.toHexString(ThreadLocalRandom.current().nextLong());
        tokens.put(token, action);
        Tasks.later(20 * 120, () -> tokens.remove(token));
        return Text.mm(label).withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand("/abps confirm " + token))
                .withHoverEvent(new HoverEvent.ShowText(Text.mm(hover))));
    }

    public boolean runToken(String token) {
        Runnable r = tokens.remove(token);
        if (r == null) return false;
        r.run();
        return true;
    }

    // ================= Join and quit =================
    public void handleJoin(ServerPlayer p) {
        PlayerData d = AbpsMod.data().load(p);
        Mods.clearAll(p);
        if (d.level > cfg().maxLevel) d.level = cfg().maxLevel;
        AttributeClass c = cls(d);
        if (c == null && d.classId != null) {
            // Their attribute was taken out of the game: give them a new one and let them keep their level
            int keep = Math.max(1, d.level);
            d.classId = null;
            Tasks.later(40, () -> {
                if (p.isRemoved() || cls(data(p)) != null) return;
                send(p, "<yellow>Your attribute was removed from the game. Here's a free new one, and you keep level " + keep + ".");
                roll(p, true, keep);
            });
        } else if (c == null) {
            d.classId = null;
            if (cfg().rollOnFirstJoin) {
                Tasks.later(40, () -> {
                    if (p.isRemoved() || cls(data(p)) != null) return;
                    send(p, "<yellow>Welcome! First, pick your role.");
                    askRole(p);
                });
            }
        } else {
            if (d.role.isEmpty()) d.role = c.adminOnly() ? dev.abps.classes.Role.PVP.id : c.role().id;
            // Coming from the old level system: their levels become skill points, abilities first
            if (d.skills.isEmpty() && d.level > 1) dev.abps.skills.Skills.autoAllocate(d, c);
            if (!d.powersOff) c.applyStatic(p, d);
            AbpsMod.state().updateLeaderboard(p.getUUID(), p.getName().getString(), d.classId, d.level);
            if (cfg().joinMessage) Tasks.later(30, () -> {
                if (!p.isRemoved()) welcome(p);
            });
        }
        updateTags(p);
        // Give the client a moment to finish joining before sending menu data
        Tasks.later(10, () -> {
            if (p.isRemoved()) return;
            sendCatalog(p);
            sync(p, true);
            if (hasMod(p)) ServerPlayNetworking.send(p, new Net.VanishPayload(new ArrayList<>(dev.abps.classes.Assassin.vanished())));
        });
    }

    public void handleQuit(ServerPlayer p) {
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        if (c != null) c.cleanup(p, d);
        Mods.clearAll(p);
        AbpsMod.data().save(p, d);
        AbpsMod.data().unload(p.getUUID());
    }

    /** Clears and re-adds all passive stats. */
    public void reapply(ServerPlayer p) {
        PlayerData d = data(p);
        Mods.clearAll(p);
        AttributeClass c = active(d);
        if (c != null) {
            c.applyStatic(p, d);
            dev.abps.skills.Skills.apply(p, d, c);
        }
        updateTags(p);
        sync(p, true);
    }

    // ================= Changing attribute and level =================
    public void setAttribute(ServerPlayer p, AttributeClass c, int level) {
        PlayerData d = data(p);
        AttributeClass old = cls(d);
        if (old != null) old.cleanup(p, d);
        d.resetRuntime();
        d.classId = c == null ? null : c.id();
        if (c != null && !c.adminOnly()) d.role = c.role().id;
        d.level = Math.max(1, Math.min(cfg().maxLevel, level));
        // A new attribute starts a new tree. Any levels it comes with go into its abilities first.
        d.skills.clear();
        dev.abps.skills.Skills.autoAllocate(d, c);
        d.ultCharge = 0;
        reapply(p);
        AbpsMod.data().save(p, d);
        AbpsMod.state().updateLeaderboard(p.getUUID(), p.getName().getString(), d.classId, d.level);
    }

    public void setLevel(ServerPlayer p, int level) {
        PlayerData d = data(p);
        d.level = Math.max(1, Math.min(cfg().maxLevel, level));
        dev.abps.skills.Skills.trim(d);
        reapply(p);
        AbpsMod.data().save(p, d);
        AbpsMod.state().updateLeaderboard(p.getUUID(), p.getName().getString(), d.classId, d.level);
    }

    public AttributeClass randomClass(String excludeId) {
        return randomClass(excludeId, null);
    }

    /** A random attribute of this role (any role if null), never the excluded one or an operator-only one. */
    public AttributeClass randomClass(String excludeId, dev.abps.classes.Role role) {
        List<AttributeClass> pool = new ArrayList<>();
        for (AttributeClass c : Classes.all()) {
            if (!c.id().equals(excludeId) && !c.adminOnly() && (role == null || c.role() == role)) pool.add(c);
        }
        if (pool.isEmpty()) for (AttributeClass c : Classes.all()) if (!c.adminOnly() && (role == null || c.role() == role)) pool.add(c);
        if (pool.isEmpty()) for (AttributeClass c : Classes.all()) if (!c.adminOnly()) pool.add(c);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** The role a player rolls in: the one they picked, else the one their attribute has, else PvP. */
    public dev.abps.classes.Role roleOf(PlayerData d) {
        dev.abps.classes.Role r = dev.abps.classes.Role.of(d.role);
        if (r != null) return r;
        AttributeClass c = cls(d);
        return c != null && !c.adminOnly() ? c.role() : dev.abps.classes.Role.PVP;
    }

    // ================= Roles =================

    /** Asks a new player to pick PvP or Gatherer: a menu popup with the mod, clickable chat buttons without it. */
    public void askRole(ServerPlayer p) {
        if (hasMod(p)) {
            ServerPlayNetworking.send(p, new Net.OpenMenuPayload("choose_role"));
            return;
        }
        raw(p, LINE);
        raw(p, " <gold><bold>Pick your role</bold>");
        for (dev.abps.classes.Role r : dev.abps.classes.Role.values()) {
            raw(p, " " + dev.abps.util.Text.colorTag(r.color) + "<bold>" + r.label + "</bold> <gray>" + r.blurb);
        }
        MutableComponent pvp = button("<#FF5252><bold>[ PvP ]</bold>", "<gray>Fighting attributes", () -> chooseRole(p, dev.abps.classes.Role.PVP));
        MutableComponent gath = button("<#69F0AE><bold>[ Gatherer ]</bold>", "<gray>Farming, mining, chopping, fishing, exploring",
                () -> chooseRole(p, dev.abps.classes.Role.GATHERER));
        p.sendSystemMessage(Text.mm("    ").append(pvp).append(Text.mm("     ")).append(gath));
        raw(p, LINE);
    }

    /** First pick: sets the role and rolls an attribute from it. Does nothing once they already have an attribute. */
    public void chooseRole(ServerPlayer p, dev.abps.classes.Role role) {
        PlayerData d = data(p);
        if (cls(d) != null || d.rolling || p.isRemoved()) return;
        d.role = role.id;
        AbpsMod.data().save(p, d);
        send(p, "<gray>You picked " + dev.abps.util.Text.colorTag(role.color) + "<bold>" + role.label + "</bold><gray>. Rolling your attribute...");
        roll(p, true);
    }

    public void promptSwitchRole(ServerPlayer p) {
        PlayerData d = data(p);
        if (hasMod(p)) {
            ServerPlayNetworking.send(p, new Net.OpenMenuPayload("switch_role"));
            return;
        }
        dev.abps.classes.Role to = roleOf(d).other();
        raw(p, LINE);
        raw(p, " <gold><bold>Switch to " + to.label + "?</bold>");
        raw(p, " <gray>" + to.blurb);
        raw(p, " <red>You roll a new " + to.label + " attribute and lose your levels and XP, like a reroll.");
        raw(p, " <gray>Price: " + cfg().rerollCost().describe(p));
        p.sendSystemMessage(buttons("Switch", "Pay and switch role", () -> confirmSwitchRole(p)));
        raw(p, LINE);
    }

    /** Pays the reroll price, moves to the other role and rolls an attribute from it. */
    public void confirmSwitchRole(ServerPlayer p) {
        PlayerData d = data(p);
        if (d.rolling || p.isRemoved()) return;
        if (cls(d) == null) {
            askRole(p);
            return;
        }
        Cost cost = cfg().rerollCost();
        if (!cost.canAfford(p)) {
            send(p, "<red>You can't afford this. You need " + cost.describe(p) + "<red>.");
            Fx.sound(level(p), p, SoundEvents.VILLAGER_NO, 1f, 1f);
            return;
        }
        cost.take(p);
        p.setExperienceLevels(0);
        p.setExperiencePoints(0);
        d.role = roleOf(d).other().id;
        d.rerolls++;
        roll(p, true);
    }

    /** Gatherers fill their ultimate by gathering. amount is a fraction of a full charge. */
    public void addGatherCharge(ServerPlayer p, PlayerData d, double amount) {
        AttributeClass c = active(d);
        if (amount <= 0 || c == null || d.ultCharge >= 1 || System.currentTimeMillis() < d.ultLockUntil) return;
        d.ultCharge = Math.min(1, d.ultCharge + amount * dev.abps.skills.Skills.gatherMult(d, c));
        notifyUltReady(p, d, c);
    }

    /** Gives a random attribute at level 1. */
    public void roll(ServerPlayer p, boolean animate) {
        roll(p, animate, 1);
    }

    /** Rolls a random attribute at this level. */
    public void roll(ServerPlayer p, boolean animate, int level) {
        PlayerData d = data(p);
        AttributeClass result = randomClass(cfg().rerollNoRepeat ? d.classId : null, roleOf(d));
        setAttribute(p, result, Math.max(1, Math.min(cfg().maxLevel, level)));
        if (!animate) {
            rollResult(p, result);
            return;
        }
        d.rolling = true;
        if (hasMod(p)) {
            // The client plays a slot machine animation that takes about 3 seconds
            ServerPlayNetworking.send(p, new Net.RollPayload(result.id()));
            Tasks.later(64, () -> {
                data(p).rolling = false;
                if (!p.isRemoved()) rollResult(p, result);
            });
            return;
        }
        List<AttributeClass> all = new ArrayList<>(Classes.all());
        int start = ThreadLocalRandom.current().nextInt(all.size());
        Tasks.repeat(17, 3, step -> {
            if (p.isRemoved()) return;
            if (step < 16) {
                AttributeClass show = all.get((start + step) % all.size());
                title(p, show.colored(show.symbol()) + " " + show.display(), "<gray>Rolling" + ".".repeat(step % 4), 0, 12, 0);
                Fx.sound(level(p), p, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7f, 0.7f + step * 0.06f);
                return;
            }
            data(p).rolling = false;
            rollResult(p, result);
        });
    }

    private static ServerLevel level(ServerPlayer p) {
        return (ServerLevel) p.level();
    }

    private void rollResult(ServerPlayer p, AttributeClass c) {
        if (!hasMod(p)) title(p, c.colored(c.symbol()) + " " + c.display(), "<gray>" + c.tagline(), 2, 60, 12);
        Fx.sound(level(p), p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        celebrate(p, c.rgb(), false);
        send(p, "You are now a " + c.display() + "<gray>!");
        raw(p, "<gray>Press <yellow>M</yellow> or type <yellow>!Menu</yellow> to see everything it does.");
        if (cfg().broadcastRolls) broadcast("<white>" + p.getName().getString() + " <gray>rolled " + c.display() + "<gray>!", p);
    }

    /** Firework-style burst of particles above the player. */
    public void celebrate(ServerPlayer p, int color, boolean big) {
        if (!cfg().milestoneFireworks) return;
        ServerLevel lvl = level(p);
        Vec3 at = p.position().add(0, 2.5, 0);
        Fx.burst(lvl, Fx.dust(color, 1.6f), at, big ? 80 : 40, 1.2, 0.05);
        Fx.burst(lvl, ParticleTypes.FIREWORK, at, big ? 60 : 25, 0.4, 0.25);
        Fx.burst(lvl, ParticleTypes.END_ROD, at, 20, 0.5, 0.15);
        Fx.sound(lvl, p, SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 1f);
        if (big) Fx.sound(lvl, p, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 1f);
    }

    // ================= Reroll and upgrade =================
    public void promptReroll(ServerPlayer p) {
        PlayerData d = data(p);
        if (d.rolling) {
            send(p, "<red>You are already rolling.");
            return;
        }
        if (hasMod(p)) {
            ServerPlayNetworking.send(p, new Net.OpenMenuPayload("confirm_reroll"));
            return;
        }
        Cost cost = cfg().rerollCost();
        AttributeClass c = cls(d);
        raw(p, LINE);
        raw(p, " <gold><bold>Reroll Attribute</bold>");
        if (c != null) {
            raw(p, " <gray>Current: " + c.display() + " <gray>(Level " + d.level + ")");
            if (d.level > 1) raw(p, " <red>You will lose all " + d.level + " levels.");
            raw(p, " <red>All of your XP levels are wiped too.");
        }
        raw(p, " <gray>Price: " + cost.describe(p));
        p.sendSystemMessage(buttons("Reroll", "Pay and roll a new attribute", () -> confirmReroll(p)));
        raw(p, LINE);
    }

    public void confirmReroll(ServerPlayer p) {
        PlayerData d = data(p);
        if (d.rolling || p.isRemoved()) return;
        Cost cost = cfg().rerollCost();
        if (!cost.canAfford(p)) {
            send(p, "<red>You can't afford this. You need " + cost.describe(p) + "<red>.");
            Fx.sound(level(p), p, SoundEvents.VILLAGER_NO, 1f, 1f);
            return;
        }
        cost.take(p);
        // A reroll wipes every XP level you have, not just the price
        p.setExperienceLevels(0);
        p.setExperiencePoints(0);
        d.rerolls++;
        roll(p, true);
    }

    public void promptUpgrade(ServerPlayer p) {
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        if (c == null) {
            send(p, "<red>You don't have an attribute yet.");
            return;
        }
        if (d.level >= cfg().maxLevel) {
            send(p, "<green>Your " + c.display() + " <green>is already max level!");
            return;
        }
        if (hasMod(p)) {
            ServerPlayNetworking.send(p, new Net.OpenMenuPayload("confirm_upgrade"));
            return;
        }
        int next = d.level + 1;
        Cost cost = cfg().upgradeCost(d.level, cls(d));
        raw(p, LINE);
        raw(p, " <gold><bold>Buy a skill point</bold> <gray>(" + c.name() + ")");
        raw(p, " <gray>Level " + d.level + " <dark_gray>→ <green>Level " + next + "</green><gray>: one more point for your Skill Tree.");
        if (next == cfg().maxLevel) raw(p, " <gold>Unlocks Mastery: <yellow>" + c.mastery());
        raw(p, " <gray>Price: " + cost.describe(p));
        p.sendSystemMessage(buttons("Upgrade", "Pay and level up", () -> confirmUpgrade(p)));
        raw(p, LINE);
    }

    public void confirmUpgrade(ServerPlayer p) {
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        if (c == null || d.level >= cfg().maxLevel || p.isRemoved()) return;
        Cost cost = cfg().upgradeCost(d.level, cls(d));
        if (!cost.canAfford(p)) {
            send(p, "<red>You can't afford this. You need " + cost.describe(p) + "<red>.");
            Fx.sound(level(p), p, SoundEvents.VILLAGER_NO, 1f, 1f);
            return;
        }
        cost.take(p);
        setLevel(p, d.level + 1);
        celebrateLevel(p, d, c);
    }

    private void celebrateLevel(ServerPlayer p, PlayerData d, AttributeClass c) {
        boolean max = d.level >= cfg().maxLevel;
        banner(p, c.gradient(max ? "<bold>MASTERED</bold>" : "<bold>LEVEL " + d.level + "</bold>"),
                "<gray>" + c.name() + (max ? " is now max level" : " upgraded"), c.rgb(), 50);
        Fx.sound(level(p), p, max ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
        // A level-up effect in the class's colors, for everyone nearby with the mod
        int outer = dev.abps.util.Vfx.theme(dev.abps.util.FxKind.theme(c.id()));
        dev.abps.util.Vfx.cue(level(p), 20, p.position(), new net.minecraft.world.phys.Vec3(0, 1, 0), p.position().add(0, 1, 0), p, null, c.rgb(), c.rgb2(), max ? 1 : 0);
        dev.abps.util.Vfx.theme(outer);
        send(p, "<green>Level " + d.level + "! " + bar(d.level, cfg().maxLevel, 12, c) + " <gray>You have <white>" + dev.abps.skills.Skills.points(d)
                + "</white> skill point" + (dev.abps.skills.Skills.points(d) == 1 ? "" : "s") + " to spend in the <gold>Skill Tree</gold> (<yellow>!Skills</yellow>).");
        if (d.level % 5 == 0) celebrate(p, c.rgb(), max);
        if (max) {
            send(p, "<gold>Mastery unlocked: <yellow>" + c.mastery());
            if (cfg().broadcastMaxLevel) {
                broadcast("<gold>★ <white>" + p.getName().getString() + " <gold>has mastered " + c.display() + "<gold>! ★", null);
            }
        }
    }

    private MutableComponent buttons(String action, String hover, Runnable onYes) {
        MutableComponent yes = button("<green><bold>[ YES ]</bold>", "<green>" + hover, onYes);
        MutableComponent no = button("<red><bold>[ NO ]</bold>", "<red>Cancel", () -> {
        });
        return Text.mm("    ").append(yes).append(Text.mm("     ")).append(no);
    }

    public static String bar(int value, int max, int length, AttributeClass c) {
        int filled = (int) Math.round(length * (value / (double) max));
        StringBuilder sb = new StringBuilder();
        if (filled > 0) sb.append(c.gradient("■".repeat(filled)));
        if (filled < length) sb.append("<dark_gray>").append("■".repeat(length - filled)).append("</dark_gray>");
        return sb.toString();
    }

    // ================= Abilities =================
    /** Cooldown for this player, with their skill tree's cooldown nodes. */
    public long cooldownMs(AttributeClass c, int idx, PlayerData d) {
        return (long) (cooldownMs(c, idx, d) * dev.abps.skills.Skills.cooldownMult(d, c));
    }

    public long cooldownMs(AttributeClass c, int idx, int level) {
        double cut = cfg().cooldownReductionAtMax * cfg().scale(level);
        return (long) (c.baseCooldown(idx) * 1000 * (1 - cut) * c.cooldownMultiplier(level));
    }

    /** Bracketed label used by the styled action bar messages, like 【⏳ COOLDOWN】. */
    private static String tag(String label, String from, String to) {
        return "<dark_gray>【</dark_gray><gradient:" + from + ":" + to + "><bold>" + label + "</bold></gradient><dark_gray>】</dark_gray>";
    }

    /** A small progress bar in the class colors. */
    private static String meter(AttributeClass c, double fraction) {
        int slots = 10;
        int filled = (int) Math.round(slots * Math.max(0, Math.min(1, fraction)));
        String on = filled == 0 ? "" : "<gradient:" + c.color() + ":" + c.color2() + ">" + "▰".repeat(filled) + "</gradient>";
        return on + "<dark_gray>" + "▱".repeat(slots - filled) + "</dark_gray>";
    }

    /** Soft "can't do that right now" sound. */
    private static void denied(ServerPlayer p) {
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.NOTE_BLOCK_BASS, 0.5f, 0.7f);
    }

    // ---- Client effects engine ----
    private final java.util.Set<UUID> vfxReady = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A player's client says it can draw the custom effects itself. */
    public void setVfxReady(ServerPlayer p, boolean ready) {
        if (ready) vfxReady.add(p.getUUID());
        else vfxReady.remove(p.getUUID());
    }

    public boolean vfxReady(ServerPlayer p) {
        return vfxReady.contains(p.getUUID());
    }

    public String keyName(ServerPlayer p, int idx) {
        if (hasMod(p)) {
            return switch (idx) {
                case 1 -> "R";
                case 2 -> "C";
                case 3 -> "V";
                case 4 -> "G";
                case 5 -> "X";
                default -> "Z";
            };
        }
        if (!cfg().vanillaSwapHandKeys) return "!Cast " + idx;
        return switch (idx) {
            case 1 -> "F";
            case 2 -> "Shift + F";
            case 3 -> "Double-tap F";
            case 4 -> "Shift + Double-tap F";
            case 5 -> "!Cast 5";
            default -> "!Ult";
        };
    }

    public boolean unlocked(PlayerData d, int idx) {
        if (idx == AttributeClass.ULTIMATE || idx == 1) return true;
        return dev.abps.skills.Skills.owns(d, "ab" + idx);
    }

    public void cast(ServerPlayer p, int idx) {
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        if (c == null) {
            actionBar(p, "<red>You don't have an attribute.");
            return;
        }
        if (d.rolling || idx < 1 || idx > AttributeClass.ULTIMATE || !p.isAlive() || p.isSpectator()) return;
        if (d.powersOff) {
            actionBar(p, tag("⏻ POWERS OFF", "#9E9E9E", "#E0E0E0") + " <gray>Turn them on in Settings or with <white>!Powers on");
            denied(p);
            return;
        }
        if (idx != AttributeClass.ULTIMATE && idx > c.abilityCount()) return;
        if (idx == AttributeClass.ULTIMATE) {
            castUltimate(p, d, c);
            return;
        }
        if (!unlocked(d, idx)) {
            actionBar(p, tag("🔒 LOCKED", "#FF5252", "#FF8A65") + " <white>" + c.abilityName(idx) + "</white> <gray>Unlock it in the <gold><bold>Skill Tree</bold></gold>");
            denied(p);
            return;
        }
        long left = d.cooldownLeft(idx);
        if (left > 0 && !d.noCooldown) {
            actionBar(p, tag("⏳ COOLDOWN", "#FF5252", "#FF8A65") + " <white>" + c.abilityName(idx) + "</white> "
                    + meter(c, 1 - (double) left / Math.max(1, cooldownMs(c, idx, d))) + " <gold><bold>" + Text.time(left));
            denied(p);
            return;
        }
        boolean worked;
        try {
            worked = c.useAbility(idx, p, d);
        } catch (Exception ex) {
            AbpsMod.LOGGER.warn("Ability {} failed for {}", c.abilityName(idx), p.getName().getString(), ex);
            actionBar(p, "<red>Something went wrong with that ability.");
            return;
        }
        if (worked) {
            long cd = cooldownMs(c, idx, d);
            d.cooldownEnd[idx] = System.currentTimeMillis() + cd;
            d.readyNotified[idx] = cd < 10_000; // only ding for longer cooldowns
            d.abilitiesUsed++;
            if (d.debug) send(p, "<dark_gray>[Debug] Used " + c.abilityName(idx) + ", cooldown "
                    + Text.time(cd) + (d.noCooldown ? " (ignored)" : ""));
            sync(p, true);
        } else if (d.debug) {
            send(p, "<dark_gray>[Debug] " + c.abilityName(idx) + " did not go off: "
                    + (c.lastFail == null ? "no reason given" : c.lastFail));
        }
    }

    // ================= Ultimate =================
    private void castUltimate(ServerPlayer p, PlayerData d, AttributeClass c) {
        if (d.ultCharge < 1 && !d.noCooldown) {
            long lock = d.ultLockUntil - System.currentTimeMillis();
            if (lock > 0) {
                actionBar(p, tag("★ ULTIMATE", "#FFD54F", "#FF8F00") + " <gray>recharging</gray> <gold><bold>" + Text.time(lock)
                        + "</bold></gold> <gray>until it starts filling");
            } else {
                actionBar(p, tag("★ ULTIMATE", "#FFD54F", "#FF8F00") + " " + meter(c, d.ultCharge) + " <gold><bold>"
                        + Math.round(d.ultCharge * 100) + "%</bold></gold> <gray>deal damage to players to charge");
            }
            denied(p);
            return;
        }
        boolean worked;
        try {
            worked = c.useAbility(AttributeClass.ULTIMATE, p, d);
        } catch (Exception ex) {
            AbpsMod.LOGGER.warn("Ultimate {} failed for {}", c.abilityName(AttributeClass.ULTIMATE), p.getName().getString(), ex);
            actionBar(p, "<red>Something went wrong with your ultimate.");
            return;
        }
        if (!worked) {
            if (d.debug) send(p, "<dark_gray>[Debug] Ultimate did not go off: " + c.lastFail);
            return;
        }
        d.ultCharge = dev.abps.skills.Skills.overflow(d, c);
        d.ultReadyNotified = false;
        d.ultLockUntil = System.currentTimeMillis() + (long) (cfg().ultimateLockoutSeconds * 1000L * dev.abps.skills.Skills.ultLockMult(d, c));
        d.ultsUsed++;
        d.abilitiesUsed++;
        banner(p, c.gradient("<bold>" + c.abilityName(AttributeClass.ULTIMATE).toUpperCase() + "</bold>"), "<gray>Ultimate", c.rgb(), 30);
        Fx.screen(p, Fx.FLASH, c.rgb(), 10, 0.35f);
        // Everyone nearby hears it
        Fx.sound(level(p), p, SoundEvents.END_PORTAL_SPAWN, 0.6f, 1.6f);
        sync(p, true);
    }

    /** Dealing damage fills the ultimate. Players count fully, mobs count a little. */
    public void addUltCharge(ServerPlayer p, PlayerData d, float damage, boolean victimIsPlayer) {
        if (damage <= 0 || d.ultCharge >= 1 || cls(d) == null) return;
        if (System.currentTimeMillis() < d.ultLockUntil) return;
        AttributeClass cl = cls(d);
        // Gatherers aren't meant to fight players, so monsters charge them fully
        boolean gatherer = cl != null && cl.role() == dev.abps.classes.Role.GATHERER;
        double worth = victimIsPlayer || gatherer ? 1 : cfg().ultimateMobDamageFactor;
        d.ultCharge = Math.min(1, d.ultCharge + damage * worth / cfg().ultimateDamageToCharge * dev.abps.skills.Skills.ultChargeMult(d, cl));
        notifyUltReady(p, d, cl);
    }

    private void notifyUltReady(ServerPlayer p, PlayerData d, AttributeClass c) {
        if (d.ultCharge >= 1 && !d.ultReadyNotified && c != null) {
            d.ultReadyNotified = true;
            banner(p, c.gradient("<bold>ULTIMATE READY</bold>"), "<gray>" + c.abilityName(AttributeClass.ULTIMATE) + " <dark_gray>(" + keyName(p, AttributeClass.ULTIMATE) + ")", c.rgb(), 40);
            Fx.sound(level(p), p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.5f);
        }
    }

    // ================= Per player tick =================
    /** Runs every 5 ticks for each player. */
    public void tickPlayer(ServerPlayer p, PlayerData d, AttributeClass c) {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= c.abilityCount(); i++) {
            if (!d.readyNotified[i] && d.cooldownEnd[i] != 0 && now >= d.cooldownEnd[i]) {
                d.readyNotified[i] = true;
                Fx.sound(level(p), p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                if (!hasMod(p)) actionBar(p, c.gradient("<bold>✦ " + c.abilityName(i) + "</bold>") + " <green>is ready!");
            }
        }
        if (cfg().maxLevelAura && c.mastered(d) && !d.vanished && !p.isInvisible() && d.tickCount % 2 == 0) {
            double a = d.tickCount * 0.35;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * (Math.PI * 2 / 3);
                Fx.burst(level(p), Fx.dust(i % 2 == 0 ? c.rgb() : c.rgb2(), 0.9f),
                        new Vec3(p.getX() + Math.cos(ang) * 0.7, p.getY() + 0.15, p.getZ() + Math.sin(ang) * 0.7), 1, 0, 0, 0, 0);
            }
        }
        if (hasMod(p)) {
            if (d.tickCount % 2 == 0) sync(p, false);
        } else if (d.hud && d.tickCount % 2 == 0) {
            vanillaHud(p, d, c);
        }
    }

    /** Action bar cooldowns for players without the mod. */
    private void vanillaHud(ServerPlayer p, PlayerData d, AttributeClass c) {
        long now = System.currentTimeMillis();
        boolean show = d.inCombat();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= c.abilityCount(); i++) {
            if (!unlocked(d, i)) continue;
            long left = d.cooldownEnd[i] - now;
            if (d.cooldownEnd[i] != 0 && left > 0 && left < 120_000) show = true;
            if (!sb.isEmpty()) sb.append(" <dark_gray>|</dark_gray> ");
            sb.append(c.colored(String.valueOf(i))).append(' ').append(left > 0 ? "<red>" + Text.time(left) : "<green>✔");
        }
        sb.append(" <dark_gray>|</dark_gray> ").append(c.colored("Ult ")).append(d.ultCharge >= 1 ? "<gold>READY" : "<gray>" + Math.round(d.ultCharge * 100) + "%");
        if (d.inCombat()) sb.append(" <dark_gray>|</dark_gray> <red>⚔ ").append(Text.time(d.combatUntil - now));
        if (show) actionBar(p, sb.toString());
    }

    // ================= Syncing to the client mod =================
    public void sync(ServerPlayer p, boolean force) {
        if (!hasMod(p)) return;
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        long now = System.currentTimeMillis();
        long[] left = new long[7];
        long[] total = new long[7];
        for (int i = 1; i <= 5; i++) {
            left[i] = d.noCooldown ? 0 : Math.max(0, d.cooldownEnd[i] - now);
            total[i] = c == null ? 0 : cooldownMs(c, i, d);
        }
        boolean max = d.level >= cfg().maxLevel;
        Cost up = cfg().upgradeCost(d.level, cls(d));
        Cost re = cfg().rerollCost();
        // 1 = unlocked, 0 = locked (take it in the skill tree), -1 = this attribute has no such ability
        int[] unlock = new int[5];
        for (int i = 1; i <= 5; i++) unlock[i - 1] = c != null && i > c.abilityCount() ? -1 : unlocked(d, i) ? 1 : 0;
        Net.SyncPayload payload = new Net.SyncPayload(c == null ? "" : c.id(), d.level, cfg().maxLevel, unlock, left, total,
                (float) d.ultCharge, Math.max(0, d.ultLockUntil - now), Math.max(0, d.combatUntil - now), d.noCooldown,
                d.abilitiesUsed, d.rerolls, max ? "" : up.describe(p), !max && up.canAfford(p), re.describe(p),
                re.canAfford(p), d.hud, d.sidebar, cfg().cooldownReductionAtMax,
                dev.abps.command.Commands.isAdmin(p.createCommandSourceStack()),
                (d.powersOff ? Net.SyncPayload.POWERS_OFF : 0) | (d.pyroAura ? Net.SyncPayload.PYRO_AURA : 0),
                new ArrayList<>(d.skills), dev.abps.skills.Skills.points(d));
        // Only send when something the player can see changed (cooldowns tick down on the client)
        int hash = Objects.hash(payload.classId(), payload.level(), Arrays.hashCode(roundUp(left)), Math.round(d.ultCharge * 200),
                payload.ultLockLeft() / 1000, payload.combatLeft() / 1000, payload.noCooldown(), payload.upgradeCost(),
                payload.canUpgrade(), payload.rerollCost(), payload.canReroll(), payload.hud(), payload.panel(), d.abilitiesUsed, payload.admin(),
                payload.flags(), payload.skills(), payload.points(), Arrays.hashCode(unlock));
        if (!force && hash == d.lastSyncHash) return;
        d.lastSyncHash = hash;
        ServerPlayNetworking.send(p, payload);
    }

    private static long[] roundUp(long[] ms) {
        long[] out = new long[ms.length];
        for (int i = 0; i < ms.length; i++) out[i] = ms[i] == 0 ? 0 : 1; // only care if it's on cooldown or not
        return out;
    }

    public void sendCatalog(ServerPlayer p) {
        if (!hasMod(p)) return;
        List<Net.ClassInfo> list = new ArrayList<>();
        int max = cfg().maxLevel;
        boolean admin = dev.abps.command.Commands.isAdmin(p.createCommandSourceStack());
        for (AttributeClass c : Classes.all()) {
            if (c.adminOnly() && !admin) continue; // only operators see the operator-only attribute
            List<List<String>> passives = new ArrayList<>();
            List<List<String>> descs = new ArrayList<>();
            for (int lvl = 1; lvl <= max; lvl++) {
                passives.add(c.passives(lvl));
                List<String> ds = new ArrayList<>();
                for (int i = 1; i <= 6; i++) ds.add(i == 5 && c.abilityCount() < 5 ? "" : c.abilityDesc(i, lvl));
                descs.add(ds);
            }
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= 6; i++) names.add(i == 5 && c.abilityCount() < 5 ? "" : c.abilityName(i)); // slot 5 is empty for most
            List<Double> cds = new ArrayList<>();
            for (int i = 1; i <= 5; i++) cds.add(c.baseCooldown(i));
            list.add(new Net.ClassInfo(c.id(), c.name(), c.rgb(), c.rgb2(), c.symbol(), c.tagline(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.icon()).toString(),
                    passives, c.negatives(), c.mastery(), names, descs, cds, AbpsMod.state().count(c.id()),
                    c.adminOnly() ? "admin" : c.role().id));
        }
        ServerPlayNetworking.send(p, new Net.CatalogPayload(list));
    }

    public void sendBoard(ServerPlayer p, String filter) {
        if (!hasMod(p)) return;
        List<Net.BoardEntry> entries = new ArrayList<>();
        for (var e : AbpsMod.state().top(filter == null || filter.isEmpty() ? null : filter, 10)) {
            entries.add(new Net.BoardEntry(e.name(), e.classId(), e.level()));
        }
        ServerPlayNetworking.send(p, new Net.BoardPayload(filter == null ? "" : filter, entries));
    }

    // ================= Tags and welcome =================
    public String classHover(AttributeClass c, int level) {
        StringBuilder sb = new StringBuilder(c.colored(c.symbol()) + " " + c.display()
                + " <gray>Level " + level + "\n<dark_gray><italic>" + c.tagline() + "</italic>\n");
        for (String line : c.passives(level)) sb.append("\n<green>+ <gray>").append(line);
        for (String line : c.negatives()) sb.append("\n<red>- <gray>").append(line);
        sb.append("\n");
        for (int i = 1; i <= c.abilityCount(); i++) sb.append("\n<gold>✦ ").append(c.abilityName(i));
        sb.append("\n<light_purple>✹ ").append(c.abilityName(AttributeClass.ULTIMATE)).append(" <dark_gray>(Ultimate)");
        return sb.toString();
    }

    public void updateTags(ServerPlayer p) {
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        if (c == null) {
            d.chatTag = null;
            return;
        }
        boolean max = c.mastered(d);
        String lvl = max ? "★" : String.valueOf(d.level);
        String title = d.title == null || d.title.isEmpty() ? "" : " <gradient:#FFD54F:#FF8F00>«" + d.title + "»</gradient>";
        d.chatTag = Text.mm("<dark_gray>[</dark_gray>" + c.gradient(c.symbol() + " " + (max ? "<bold>" + c.name() + "</bold>" : c.name()))
                        + " <white>" + lvl + "</white><dark_gray>]</dark_gray>" + title)
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Text.mm(classHover(c, d.level)))));
    }

    /** Name shown in the tab list. */
    public Component tabName(ServerPlayer p) {
        if (!cfg().tabTags) return null;
        PlayerData d = AbpsMod.data().peek(p.getUUID());
        if (d == null) return null;
        AttributeClass c = cls(d);
        if (c == null) return null;
        String lvl = c.mastered(d) ? "★" : String.valueOf(d.level);
        String title = d.title == null || d.title.isEmpty() ? "" : " <gold>«" + d.title + "»";
        return Text.mm(c.colored(c.symbol()) + " <white>" + p.getName().getString()
                + " <dark_gray>[</dark_gray>" + c.colored(lvl) + "<dark_gray>]" + title);
    }

    public void welcome(ServerPlayer p) {
        PlayerData d = data(p);
        AttributeClass c = cls(d);
        if (c == null) return;
        boolean max = c.mastered(d);
        String lvl = max ? "<gold><bold>MAX</bold> ★" : "Level " + d.level;
        banner(p, c.colored(c.symbol()) + " " + c.display(), "<gray>" + lvl + " <dark_gray>• <gray>Welcome back", c.rgb(), 50);
        Fx.sound(level(p), p, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.6f);
        raw(p, LINE);
        raw(p, " <gray>Welcome back, <white>" + p.getName().getString() + "<gray>!");
        raw(p, " <gray>You are a " + c.colored(c.symbol()) + " " + c.display() + " <gray>"
                + (max ? "<gold>(Max level ★)" : "(Level <white>" + d.level + "<gray>/" + cfg().maxLevel + ")"));
        raw(p, " " + bar(d.level, cfg().maxLevel, 20, c));
        if (hasMod(p)) raw(p, " <gray>Press <yellow>M</yellow> to open your menu.");
        else raw(p, " <gray>Type <yellow>!Menu</yellow> or <yellow>!Help</yellow>. Install <gold>AbpsMod</gold> for the full HUD and menu.");
        raw(p, LINE);
    }

    public void showInfo(CommandSourceStack to, ServerPlayer target) {
        PlayerData d = data(target);
        AttributeClass c = cls(d);
        if (c == null) {
            send(to, "<red>" + target.getName().getString() + " has no attribute.");
            return;
        }
        boolean self = to.getEntity() == target;
        raw(to, LINE);
        raw(to, " " + c.colored(c.symbol()) + " " + (self ? "" : "<white>" + target.getName().getString() + "'s ") + c.display()
                + " <gray>Level <white>" + d.level + "<gray>/" + cfg().maxLevel);
        raw(to, " " + bar(d.level, cfg().maxLevel, 20, c));
        raw(to, " <gray><italic>" + c.tagline());
        raw(to, " <green><bold>Passives");
        for (String line : c.passives(d.level)) raw(to, "  <green>+ <gray>" + line);
        raw(to, " <red><bold>Weaknesses");
        for (String line : c.negatives()) raw(to, "  <red>- <gray>" + line);
        raw(to, " <gold><bold>Abilities");
        for (int i = 1; i <= c.abilityCount(); i++) {
            boolean locked = !unlocked(d, i);
            String head = "  <yellow>[" + keyName(target, i) + "] " + c.gradient("<bold>" + c.abilityName(i) + "</bold>");
            if (locked) raw(to, head + " <dark_gray>(locked: take it in the Skill Tree)");
            else raw(to, head + " <dark_gray>(" + Text.seconds(cooldownMs(c, i, d) / 1000.0) + " cooldown)");
            raw(to, "    <gray>" + c.abilityDesc(i, d.level));
        }
        raw(to, " <light_purple><bold>✹ Ultimate</bold> <dark_gray>[" + keyName(target, AttributeClass.ULTIMATE) + "] " + c.gradient("<bold>" + c.abilityName(AttributeClass.ULTIMATE) + "</bold>")
                + " <gray>(" + Math.round(d.ultCharge * 100) + "% charged)");
        raw(to, "    <gray>" + c.abilityDesc(AttributeClass.ULTIMATE, d.level));
        boolean mastered = c.mastered(d);
        raw(to, " <gold><bold>★ Mastery</bold> " + (mastered ? "<green>(Unlocked)" : "<dark_gray>(Level " + cfg().maxLevel + ")"));
        raw(to, "    " + (mastered ? "<yellow>" : "<dark_gray>") + c.mastery());
        raw(to, LINE);
    }

    public UUID uuid(ServerPlayer p) {
        return p.getUUID();
    }
}
