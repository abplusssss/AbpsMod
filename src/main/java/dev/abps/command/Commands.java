package dev.abps.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.abps.AbpsMod;
import dev.abps.Classes;
import dev.abps.Combat;
import dev.abps.Cost;
import dev.abps.Service;
import dev.abps.Teleports;
import dev.abps.classes.AttributeClass;
import dev.abps.data.PlayerData;
import dev.abps.data.ServerState;
import dev.abps.net.Net;
import dev.abps.util.Text;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** The ! chat commands and /abps. Every command works both ways. */
public final class Commands {

    /** Same permission name the old plugin used, so LuckPerms setups keep working. */
    public static final Identifier ADMIN = Identifier.fromNamespaceAndPath("randomattributes", "admin");

    private record Entry(String name, String args, String desc, String group, String... aliases) {
        boolean admin() {
            return group.equals("admin");
        }
    }

    private static final List<Entry> entries = new ArrayList<>();
    private static final Map<String, Entry> lookup = new HashMap<>();

    private Commands() {
    }

    private static void add(Entry e) {
        entries.add(e);
        lookup.put(e.name.toLowerCase(Locale.ROOT), e);
        for (String a : e.aliases) lookup.put(a, e);
    }

    static {
        // Attribute commands
        add(new Entry("Help", "", "Shows this list.", "attr", "commands", "?"));
        add(new Entry("Menu", "", "Opens the attribute menu.", "attr", "gui", "m"));
        add(new Entry("Attribute", "", "Shows your attribute, level and stats.", "attr", "info", "myattribute", "me"));
        add(new Entry("Attributes", "", "Lists all attributes.", "attr", "list", "classes"));
        add(new Entry("Upgrade", "", "Buy a level: one more skill point.", "attr", "levelup", "up", "buypoint", "skillpoint"));
        add(new Entry("Skills", "[reset]", "Your skill tree: spend points, or reset them for free.", "attr", "skill", "tree", "skilltree", "road", "levelroad", "levels", "talents"));
        add(new Entry("Top", "[attribute]", "Shows the highest level players.", "attr", "leaderboard", "lb"));
        add(new Entry("RollAttribute", "[other | pvp | gatherer]", "Reroll your attribute for a price (or the other role's).", "attr", "reroll", "roll"));
        add(new Entry("Cooldowns", "", "Shows your ability cooldowns.", "attr", "cd"));
        add(new Entry("Cast", "<1-6>", "Uses an ability from chat. 6 is your ultimate (5 for attributes with only 4 abilities).", "attr", "ability"));
        add(new Entry("Ult", "", "Uses your ultimate when it is charged.", "attr", "ultimate"));
        add(new Entry("Hud", "", "Turns the cooldown display on or off.", "attr"));
        // Teleports
        add(new Entry("SetHome", "[name]", "Saves a home where you stand.", "tp", "sh", "createhome"));
        add(new Entry("Home", "[name]", "Teleports you to a home.", "tp", "h", "homes_go"));
        add(new Entry("Homes", "", "Lists your homes.", "tp", "listhomes", "hl"));
        add(new Entry("DelHome", "[name]", "Deletes a home.", "tp", "dh", "rmhome", "deletehome", "removehome"));
        add(new Entry("TpRequest", "<player>", "Asks to teleport to a player.", "tp", "tpr", "tprequest", "tpask", "tpto"));
        add(new Entry("TpaHere", "<player>", "Asks a player to teleport to you.", "tp", "tphere", "tprh", "tpahere"));
        add(new Entry("TpAccept", "[player]", "Accepts a teleport request.", "tp", "tpa", "tpaccept", "tpyes", "tpy"));
        add(new Entry("TpDeny", "[player]", "Denies a teleport request.", "tp", "tpd", "tpdeny", "tpno", "tpn"));
        add(new Entry("TpCancel", "", "Cancels requests you sent.", "tp", "tpc", "tpcancel"));
        add(new Entry("Spawn", "", "Teleports you to spawn.", "tp", "hub", "lobby"));
        add(new Entry("Back", "", "Goes back to where you last teleported or died.", "tp", "return"));
        // Dungeons
        add(new Entry("Dungeon", "[start <id> | leave | list]", "Opens the Party tab: Siege and party games. Start one or leave.", "dungeon", "dungeons", "dg", "raid", "games", "game", "siege", "minigames"));
        add(new Entry("Party", "[invite <name> | accept | decline | leave | kick <name>]", "Your dungeon party (up to 4 players).", "dungeon", "team", "group", "dparty"));
        add(new Entry("Powers", "[on | off]", "Turns your whole attribute off or back on (abilities, passives and weaknesses).", "attr",
                "power", "abilities", "togglepowers", "nopowers", "vanillamode"));
        add(new Entry("Role", "[pvp | gatherer | info]", "Switches between your PvP and Gatherer attributes (free, not in combat).", "attr",
                "roles", "switchrole", "job", "path"));
        add(new Entry("Aura", "[on | off]", "Pyromancer: turns your heat aura off or back on.", "attr", "heataura", "flameaura", "fireaura"));
        add(new Entry("Title", "[name | off]", "Shows or picks the title next to your name.", "dungeon", "titles", "settitle"));
        // Shops, rewards and profile
        add(new Entry("Shop", "", "Opens the player shops.", "extra", "shops", "market", "pshop", "playershops"));
        add(new Entry("Daily", "", "Claims your daily reward.", "extra", "reward", "claim", "dailyreward"));
        add(new Entry("Profile", "", "Shows your kills, deaths, playtime and streak.", "extra", "mystats", "pstats", "playerstats"));
        add(new Entry("Travel", "", "Opens your homes and teleports in the menu.", "extra", "warps", "tpmenu"));
        // Admin and testing
        add(new Entry("Meteors", "", "Starts a meteor shower now.", "admin", "meteorshower", "meteor"));
        add(new Entry("GiveAttribute", "@player <attribute>", "Sets a player's attribute.", "admin", "setattribute"));
        add(new Entry("GiveUpgrade", "@player <1-25>", "Sets a player's level.", "admin", "setlevel"));
        add(new Entry("GiveUlt", "[@player]", "Fully charges an ultimate.", "admin", "chargeult"));
        add(new Entry("ForceRoll", "@player", "Gives a free random roll.", "admin"));
        add(new Entry("ResetPlayer", "@player", "Wipes their data and rolls a new attribute.", "admin"));
        add(new Entry("CheckAttribute", "@player", "Shows a player's attribute.", "admin", "check"));
        add(new Entry("Stats", "[@player]", "Shows live stat values.", "admin"));
        add(new Entry("ResetCooldown", "[@player]", "Clears ability cooldowns.", "admin", "resetcd"));
        add(new Entry("NoCooldown", "", "Turns cooldowns off or on for you.", "admin", "nocd"));
        add(new Entry("GiveRollCost", "[@player]", "Gives the XP and items for one reroll.", "admin"));
        add(new Entry("ClearCombat", "@player", "Ends combat and removes a combat log lockout.", "admin", "unban"));
        add(new Entry("SetSpawn", "", "Sets spawn to where you stand.", "admin"));
        add(new Entry("Debug", "", "Shows damage math and ability info in chat.", "admin"));
        add(new Entry("SaveAll", "", "Saves everyone's data right now.", "admin", "save"));
        add(new Entry("Reload", "", "Reloads the config.", "admin"));
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(net.minecraft.commands.Commands.literal("abps")
                    .executes(ctx -> {
                        handle(ctx.getSource(), "help");
                        return 1;
                    })
                    .then(net.minecraft.commands.Commands.argument("command", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
                                boolean admin = isAdmin(ctx.getSource());
                                for (Entry e : entries) {
                                    if ((!e.admin() || admin) && e.name.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(e.name);
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                handle(ctx.getSource(), StringArgumentType.getString(ctx, "command"));
                                return 1;
                            })));

            // Every command is also a real slash command (/home, /tpa, /givelevel...) so the game can tab complete it.
            // Names that vanilla or another mod already uses are left alone.
            Set<String> done = new HashSet<>();
            for (Entry e : entries) {
                registerLabel(dispatcher, e.name.toLowerCase(Locale.ROOT), e, done);
                for (String alias : e.aliases) registerLabel(dispatcher, alias.toLowerCase(Locale.ROOT), e, done);
            }
        });
    }

    private static void registerLabel(CommandDispatcher<CommandSourceStack> dispatcher, String label, Entry e, Set<String> done) {
        if (!done.add(label) || dispatcher.getRoot().getChild(label) != null) return;
        LiteralArgumentBuilder<CommandSourceStack> cmd = net.minecraft.commands.Commands.literal(label)
                .requires(src -> !e.admin() || isAdmin(src))
                .executes(ctx -> {
                    handle(ctx.getSource(), e.name);
                    return 1;
                });
        if (!e.args.isEmpty()) {
            cmd.then(net.minecraft.commands.Commands.argument("args", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> suggestArgs(ctx, builder, e))
                    .executes(ctx -> {
                        handle(ctx.getSource(), e.name + " " + StringArgumentType.getString(ctx, "args"));
                        return 1;
                    }));
        }
        dispatcher.register(cmd);
    }

    /** Works out what the word being typed should be (a player, an attribute, a level...) from the usage text. */
    private static CompletableFuture<Suggestions> suggestArgs(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, Entry e) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        int index = 0;
        for (char ch : remaining.toCharArray()) if (ch == ' ') index++;
        SuggestionsBuilder sb = builder.createOffset(builder.getStart() + lastSpace + 1);
        String typed = remaining.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
        String[] tokens = e.args.split(" ");
        if (index >= tokens.length) return sb.buildFuture();
        String token = tokens[index].toLowerCase(Locale.ROOT);

        List<String> options = new ArrayList<>();
        if (token.contains("player")) {
            options.addAll(AbpsMod.server().getPlayerList().getPlayers().stream().map(p -> p.getName().getString()).toList());
            if (e.admin()) options.addAll(List.of("@s", "@a", "@r"));
        } else if (token.contains("attribute")) {
            for (AttributeClass c : Classes.all()) options.add(c.name());
        } else if (token.contains("1-25")) {
            for (int i = 1; i <= AbpsMod.config().maxLevel; i++) options.add(String.valueOf(i));
        } else if (token.contains("1-6")) {
            options.addAll(List.of("1", "2", "3", "4", "5", "6"));
        } else if (token.contains("name") && ctx.getSource().getPlayer() != null) {
            options.addAll(service().data(ctx.getSource().getPlayer()).homes.keySet());
        }
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(typed)) sb.suggest(o);
        }
        return sb.buildFuture();
    }

    public static boolean isCommand(String label) {
        return lookup.containsKey(label.toLowerCase(Locale.ROOT));
    }

    /** True for the operator-only commands, used by the menu's Admin tab. */
    public static boolean isAdminCommand(String label) {
        Entry e = lookup.get(label.toLowerCase(Locale.ROOT));
        return e != null && e.admin();
    }

    public static boolean isAdmin(CommandSourceStack s) {
        if (s.getPlayer() == null) return true; // console
        return s.checkPermission(ADMIN, PermissionLevel.GAMEMASTERS);
    }

    private static Service service() {
        return AbpsMod.service();
    }

    // ================= Entry point =================
    public static void handle(CommandSourceStack s, String input) {
        if (!AbpsMod.running()) return;
        String trimmed = input.trim();
        if (trimmed.startsWith("confirm ")) {
            // Clicked a chat button
            if (!service().runToken(trimmed.substring(8).trim())) Service.send(s, "<red>That button has expired.");
            return;
        }
        String[] parts = trimmed.split("\\s+");
        Entry e = lookup.get(parts[0].toLowerCase(Locale.ROOT));
        if (e == null) {
            Service.send(s, "<red>Unknown command. Type <yellow>!Help</yellow> for a list.");
            return;
        }
        if (e.admin() && !isAdmin(s)) {
            Service.send(s, "<red>You don't have permission for that.");
            return;
        }
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        try {
            run(s, e.name, args);
        } catch (Exception ex) {
            Service.send(s, "<red>That command failed. Check the server log.");
            AbpsMod.LOGGER.warn("Command {} failed", e.name, ex);
        }
    }

    /** Opens a menu tab for players with the mod, or tells others what to use instead. */
    private static void openTab(CommandSourceStack s, String tab, String withoutMod) {
        ServerPlayer p = needPlayer(s);
        if (p == null) return;
        if (Service.hasMod(p)) ServerPlayNetworking.send(p, new Net.OpenMenuPayload(tab));
        else Service.raw(s, withoutMod);
    }

    private static ServerPlayer needPlayer(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        if (p == null) Service.send(s, "<red>Only players can use this.");
        return p;
    }

    private static void run(CommandSourceStack s, String name, String[] args) {
        Service sv = service();
        ServerPlayer self = s.getPlayer();
        switch (name) {
            case "Help" -> help(s);
            case "Menu" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                if (Service.hasMod(p)) ServerPlayNetworking.send(p, new Net.OpenMenuPayload("overview"));
                else {
                    sv.showInfo(s, p);
                    Service.raw(s, "<gray>Install <gold>AbpsMod</gold> on your game to get the full menu.");
                }
            }
            case "Attribute" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) sv.showInfo(s, p);
            }
            case "Attributes" -> listAll(s);
            case "Upgrade" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) sv.promptUpgrade(p);
            }
            case "Skills" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                if (args.length > 0 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("respec"))) dev.abps.skills.Skills.respec(p);
                else if (Service.hasMod(p)) ServerPlayNetworking.send(p, new Net.OpenMenuPayload("skills"));
                else road(s, p);
            }
            case "Top" -> top(s, args);
            case "RollAttribute" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                // "!Reroll other" (or the other role's name) rerolls the attribute you're not playing
                dev.abps.classes.Role r = args.length > 0 ? dev.abps.classes.Role.of(args[0]) : null;
                boolean other = args.length > 0 && (args[0].equalsIgnoreCase("other") || (r != null && r != sv.roleOf(sv.data(p))));
                if (other) sv.promptRerollOther(p);
                else sv.promptReroll(p);
            }
            case "Cooldowns" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) cooldowns(p);
            }
            case "Cast" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                if (args.length < 1 || !args[0].matches("[1-6]")) {
                    Service.send(s, "<red>Use: !Cast 1 to 5, or 6 for your ultimate");
                    return;
                }
                int slot = Integer.parseInt(args[0]);
                AttributeClass mine = sv.cls(sv.data(p));
                // Attributes with only 4 abilities keep working with the old "!Cast 5" for the ultimate
                if (slot == 5 && mine != null && mine.abilityCount() < 5) slot = AttributeClass.ULTIMATE;
                sv.cast(p, slot);
            }
            case "Ult" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) sv.cast(p, AttributeClass.ULTIMATE);
            }
            case "Hud" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                PlayerData d = sv.data(p);
                d.hud = !d.hud;
                AbpsMod.data().save(p, d);
                sv.sync(p, true);
                Service.send(s, "Cooldown display is now " + (d.hud ? "<green>on" : "<red>off") + "<gray>.");
            }
            // ---- Teleports ----
            case "SetHome" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.setHome(p, args.length > 0 ? args[0] : "home");
            }
            case "Meteors" -> {
                dev.abps.content.Meteors.start(AbpsMod.server());
                Service.send(s, "<light_purple>A meteor shower begins.");
            }
            case "Dungeon" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                String op = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
                switch (op) {
                    case "start", "go", "play" -> {
                        if (args.length < 2) Service.send(s, "<red>Use: !Dungeon start <id>. <gray>Ids: <white>" + String.join(", ", dev.abps.games.GameDef.ALL.keySet()));
                        else dev.abps.dungeon.Dungeons.start(p, args[1].toLowerCase(java.util.Locale.ROOT));
                    }
                    case "leave", "quit", "exit" -> dev.abps.dungeon.Dungeons.leave(p);
                    case "gate", "makegate", "spawngate" -> {
                        if (!isAdmin(s)) {
                            Service.send(s, "<red>Only operators can make gates.");
                            return;
                        }
                        if (p.level() != AbpsMod.server().overworld()) {
                            Service.send(s, "<red>Gates only work in the overworld.");
                            return;
                        }
                        // A few blocks ahead of you, so you don't end up inside the arch
                        var ahead = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(6));
                        var gate = dev.abps.dungeon.Gates.build((net.minecraft.server.level.ServerLevel) p.level(), (int) Math.floor(ahead.x), (int) Math.floor(ahead.z));
                        Service.send(s, gate == null ? "<red>No room for a gate there (water, trees, or another gate within 200 blocks)."
                                : "<green>A dungeon gate formed at " + gate.getX() + " " + gate.getY() + " " + gate.getZ() + ".");
                    }
                    case "list", "" -> {
                        if (op.isEmpty() && Service.hasMod(p)) {
                            ServerPlayNetworking.send(p, new Net.OpenMenuPayload("dungeons"));
                            return;
                        }
                        Service.send(s, "<light_purple><bold>Party games</bold></light_purple> <gray>(start one with <yellow>!Dungeon start <id></yellow>)");
                        for (var d : dev.abps.games.GameDef.ALL.values()) {
                            Service.raw(s, " <white>" + d.name() + "</white> <dark_gray>(" + d.id() + ")</dark_gray> <gray>" + d.blurb());
                        }
                    }
                    default -> Service.send(s, "<red>Use: !Dungeon [start <id> | leave | list]");
                }
            }
            case "Party" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                String op = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
                switch (op) {
                    case "invite", "add", "inv" -> {
                        ServerPlayer to = args.length > 1 ? AbpsMod.server().getPlayerList().getPlayerByName(args[1]) : null;
                        if (to == null) Service.send(s, "<red>Use: !Party invite <online player>");
                        else dev.abps.dungeon.Party.invite(p, to);
                    }
                    case "accept", "join", "yes" -> dev.abps.dungeon.Party.accept(p);
                    case "decline", "deny", "no" -> dev.abps.dungeon.Party.decline(p);
                    case "leave", "quit" -> dev.abps.dungeon.Party.leave(p, true);
                    case "kick", "remove" -> {
                        ServerPlayer who = args.length > 1 ? AbpsMod.server().getPlayerList().getPlayerByName(args[1]) : null;
                        if (who == null) Service.send(s, "<red>Use: !Party kick <player>");
                        else dev.abps.dungeon.Party.kick(p, who);
                    }
                    default -> {
                        var party = dev.abps.dungeon.Party.of(p);
                        if (party == null || party.size() <= 1) Service.send(s, "<gray>You're not in a party. Invite someone with <yellow>!Party invite <name></yellow>.");
                        else {
                            java.util.List<String> names = new java.util.ArrayList<>();
                            for (ServerPlayer m : party.online()) names.add(m.getName().getString());
                            Service.send(s, "<aqua>Your party:</aqua> <white>" + String.join(", ", names));
                        }
                    }
                }
            }
            case "Role" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                PlayerData d = sv.data(p);
                dev.abps.classes.Role now = sv.roleOf(d);
                dev.abps.classes.Role want = args.length > 0 ? dev.abps.classes.Role.of(args[0]) : null;
                if (sv.cls(d) == null) {
                    if (want != null) sv.chooseRole(p, want);
                    else sv.askRole(p);
                    return;
                }
                if (args.length > 0 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("list"))) {
                    var other = sv.otherSlot(d);
                    AttributeClass oc = other == null ? null : Classes.get(other.classId);
                    Service.send(s, "<gray>Playing " + Text.colorTag(now.color) + now.label + "</gray><gray>: " + sv.cls(d).display() + " <gray>Lv " + d.level
                            + "  <dark_gray>|</dark_gray>  " + Text.colorTag(now.other().color) + now.other().label + "</gray><gray>: "
                            + (oc == null ? "<gray>none yet" : oc.display() + " <gray>Lv " + other.level) + "  <dark_gray>(!Role to switch)");
                    return;
                }
                if (want == now) Service.send(s, "<gray>You're already playing " + now.label + ".");
                else sv.switchRole(p);
            }
            case "Powers" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                PlayerData d = sv.data(p);
                String op = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
                boolean off = switch (op) {
                    case "off", "disable", "0", "false" -> true;
                    case "on", "enable", "1", "true" -> false;
                    default -> !d.powersOff;
                };
                sv.setPowersOff(p, off);
            }
            case "Aura" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                PlayerData d = sv.data(p);
                String op = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
                d.pyroAura = switch (op) {
                    case "off", "disable", "0", "false" -> false;
                    case "on", "enable", "1", "true" -> true;
                    default -> !d.pyroAura;
                };
                AbpsMod.data().save(p, d);
                sv.sync(p, true);
                Service.send(s, d.pyroAura ? "<gold>Heat aura on.</gold> <gray>Enemies near you catch fire (Pyromancer only)."
                        : "<gray>Heat aura off. Enemies near you won't catch fire.");
            }
            case "Title" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                PlayerData d = sv.data(p);
                if (args.length == 0) {
                    Service.send(s, d.titles.isEmpty() ? "<gray>You have no titles yet. Clear dungeons to earn them."
                            : "<gold>Your titles:</gold> <white>" + String.join(", ", d.titles) + "</white> <gray>(use !Title <name> or !Title off)");
                    return;
                }
                String want = String.join(" ", args);
                if (want.equalsIgnoreCase("off") || want.equalsIgnoreCase("none")) want = "";
                String pick = "";
                for (String t : d.titles) if (t.equalsIgnoreCase(want)) pick = t;
                if (!want.isEmpty() && pick.isEmpty()) {
                    Service.send(s, "<red>You don't have that title.");
                    return;
                }
                dev.abps.dungeon.Dungeons.handle(p, "title|" + pick);
            }
            case "Shop" -> openTab(s, "shops", "<gray>Shops need <gold>AbpsMod</gold> installed on your game.");
            case "Travel" -> openTab(s, "travel", "<gray>Use <yellow>!Home</yellow>, <yellow>!Spawn</yellow> and <yellow>!TPR</yellow> without the mod.");
            case "Daily" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) dev.abps.Profiles.claimDaily(p);
            }
            case "Profile" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                if (Service.hasMod(p)) ServerPlayNetworking.send(p, new Net.OpenMenuPayload("profile"));
                else {
                    var d = sv.data(p);
                    Service.raw(s, "<gold><bold>" + p.getName().getString() + "</bold> <gray>Kills <white>" + d.kills + "</white> Deaths <white>" + d.deaths
                            + "</white> Best streak <white>" + d.bestStreak + "</white> Mobs <white>" + d.mobKills + "</white> Played <white>" + (d.playSeconds / 3600) + "h");
                }
            }
            case "Home" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.home(p, args.length > 0 ? args[0] : null);
            }
            case "Homes" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.listHomes(p);
            }
            case "DelHome" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.delHome(p, args.length > 0 ? args[0] : null);
            }
            case "TpRequest", "TpaHere" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                if (args.length < 1) {
                    Service.send(s, "<red>Use: !" + name + " <player>");
                    return;
                }
                ServerPlayer t = onePlayer(s, args[0]);
                if (t != null) Teleports.request(p, t, name.equals("TpaHere"));
            }
            case "TpAccept" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.accept(p, args.length > 0 ? strip(args[0]) : null);
            }
            case "TpDeny" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.deny(p, args.length > 0 ? strip(args[0]) : null);
            }
            case "TpCancel" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.cancel(p);
            }
            case "Spawn" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.spawn(p);
            }
            case "Back" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.back(p);
            }
            // ---- Admin ----
            case "GiveAttribute" -> giveAttribute(s, args);
            case "GiveUpgrade" -> giveUpgrade(s, args);
            case "GiveUlt" -> {
                for (ServerPlayer t : targets(s, args, true)) {
                    PlayerData d = sv.data(t);
                    d.ultLockUntil = 0;
                    sv.addUltCharge(t, d, 100000, true);
                    Service.send(s, "<green>Charged " + t.getName().getString() + "'s ultimate.");
                }
            }
            case "ForceRoll" -> {
                for (ServerPlayer t : targets(s, args, false)) {
                    sv.roll(t, true);
                    Service.send(s, "<green>Rolled a new attribute for " + t.getName().getString() + ".");
                }
            }
            case "ResetPlayer" -> {
                for (ServerPlayer t : targets(s, args, false)) {
                    PlayerData d = sv.data(t);
                    d.rerolls = 0;
                    d.abilitiesUsed = 0;
                    sv.setAttribute(t, null, 1);
                    sv.roll(t, true);
                    Service.send(s, "<green>Reset " + t.getName().getString() + ".");
                }
            }
            case "CheckAttribute" -> {
                for (ServerPlayer t : targets(s, args, false)) {
                    sv.showInfo(s, t);
                    PlayerData d = sv.data(t);
                    Service.raw(s, " <gray>Rerolls used: <white>" + d.rerolls + " <gray>Abilities used: <white>" + d.abilitiesUsed
                            + " <gray>Homes: <white>" + d.homes.size());
                }
            }
            case "Stats" -> {
                for (ServerPlayer t : targets(s, args, true)) stats(s, t);
            }
            case "ResetCooldown" -> {
                for (ServerPlayer t : targets(s, args, true)) {
                    PlayerData d = sv.data(t);
                    Arrays.fill(d.cooldownEnd, 0);
                    sv.sync(t, true);
                    Service.send(s, "<green>Cleared cooldowns for " + t.getName().getString() + ".");
                }
            }
            case "NoCooldown" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                PlayerData d = sv.data(p);
                d.noCooldown = !d.noCooldown;
                sv.sync(p, true);
                Service.send(s, "No cooldown mode is now " + (d.noCooldown ? "<green>on" : "<red>off") + "<gray>.");
            }
            case "GiveRollCost" -> {
                Cost cost = AbpsMod.config().rerollCost();
                for (ServerPlayer t : targets(s, args, true)) {
                    cost.give(t);
                    Service.send(s, "<green>Gave " + t.getName().getString() + " " + cost.describe(null) + "<green>.");
                }
            }
            case "ClearCombat" -> {
                if (args.length < 1) {
                    Service.send(s, "<red>Use: !ClearCombat <player>");
                    return;
                }
                String who = strip(args[0]);
                ServerPlayer t = AbpsMod.server().getPlayerList().getPlayerByName(who);
                if (t != null) sv.data(t).combatUntil = 0;
                var profile = t != null ? t.getUUID() : offlineId(who);
                boolean unbanned = profile != null && AbpsMod.state().unban(profile);
                Service.send(s, "<green>Cleared combat for " + who + (unbanned ? " and removed their lockout." : "."));
            }
            case "SetSpawn" -> {
                ServerPlayer p = needPlayer(s);
                if (p != null) Teleports.setSpawn(p);
            }
            case "Debug" -> {
                ServerPlayer p = needPlayer(s);
                if (p == null) return;
                PlayerData d = sv.data(p);
                d.debug = !d.debug;
                Service.send(s, "Debug is now " + (d.debug ? "<green>on" : "<red>off") + "<gray>.");
            }
            case "SaveAll" -> {
                int n = 0;
                for (ServerPlayer p : AbpsMod.server().getPlayerList().getPlayers()) {
                    AbpsMod.data().save(p, sv.data(p));
                    n++;
                }
                AbpsMod.state().saveNow();
                Service.send(s, "<green>Saved data for " + n + " players.");
            }
            case "Reload" -> {
                AbpsMod.reloadConfig();
                for (ServerPlayer p : AbpsMod.server().getPlayerList().getPlayers()) {
                    sv.reapply(p);
                    sv.sendCatalog(p);
                }
                Service.send(s, "<green>Config reloaded.");
            }
            default -> Service.send(s, "<red>Unknown command.");
        }
    }

    /** Finds a lockout by name for players who are offline, using the leaderboard. */
    private static java.util.UUID offlineId(String name) {
        for (ServerState.Entry e : AbpsMod.state().top(null, Integer.MAX_VALUE)) {
            if (e.name().equalsIgnoreCase(name)) return e.id();
        }
        return null;
    }

    private static String strip(String raw) {
        return raw.startsWith("@") ? raw.substring(1) : raw;
    }

    private static ServerPlayer onePlayer(CommandSourceStack s, String raw) {
        ServerPlayer p = AbpsMod.server().getPlayerList().getPlayerByName(strip(raw));
        if (p == null) Service.send(s, "<red>Player not found: " + raw);
        return p;
    }

    /**
     * Reads a player argument. Supports @Name, Name, @s (you), @a (everyone) and @r (random).
     * When optional is true and no argument is given, it uses the sender.
     */
    private static List<ServerPlayer> targets(CommandSourceStack s, String[] args, boolean optional) {
        List<ServerPlayer> out = new ArrayList<>();
        if (args.length == 0) {
            if (optional && s.getPlayer() != null) out.add(s.getPlayer());
            else Service.send(s, "<red>You need to name a player. Example: @Steve");
            return out;
        }
        String name = strip(args[0]);
        List<ServerPlayer> online = AbpsMod.server().getPlayerList().getPlayers();
        switch (name.toLowerCase(Locale.ROOT)) {
            case "s", "me" -> {
                if (s.getPlayer() != null) out.add(s.getPlayer());
            }
            case "a", "all" -> out.addAll(online);
            case "r", "random" -> {
                if (!online.isEmpty()) out.add(online.get(ThreadLocalRandom.current().nextInt(online.size())));
            }
            default -> {
                ServerPlayer p = AbpsMod.server().getPlayerList().getPlayerByName(name);
                if (p != null) out.add(p);
            }
        }
        if (out.isEmpty()) Service.send(s, "<red>Player not found: " + args[0]);
        return out;
    }

    // ================= Output =================
    private static void help(CommandSourceStack s) {
        boolean admin = isAdmin(s);
        Service.raw(s, Service.LINE);
        Service.raw(s, " <gradient:#FFD54F:#FF8F00><bold>AbpsMod Commands</bold></gradient> <dark_gray>(click one to fill it in)");
        String[][] groups = {{"attr", "<gold><bold>Attributes"}, {"tp", "<aqua><bold>Teleports"}, {"dungeon", "<light_purple><bold>Dungeons"},
                {"extra", "<green><bold>Shops and Rewards"}, {"admin", "<red><bold>Admin / Testing"}};
        for (String[] g : groups) {
            if (g[0].equals("admin") && !admin) continue;
            Service.raw(s, " " + g[1]);
            for (Entry e : entries) if (e.group.equals(g[0])) s.sendSystemMessage(helpLine(e));
        }
        ServerPlayer p = s.getPlayer();
        if (p != null) {
            Service.raw(s, " <light_purple><bold>Ability keys");
            AttributeClass mine = service().cls(service().data(p));
            int count = mine == null ? 4 : mine.abilityCount();
            for (int i = 1; i <= count; i++) {
                Service.raw(s, "  <yellow>" + service().keyName(p, i) + " <dark_gray>- <gray>Ability " + i + (i == 1 ? "" : ", unlocked in the Skill Tree"));
            }
            Service.raw(s, "  <yellow>" + service().keyName(p, AttributeClass.ULTIMATE) + " <dark_gray>- <gray>Ultimate (charge it by hurting players)");
        }
        Service.raw(s, Service.LINE);
    }

    private static MutableComponent helpLine(Entry e) {
        String usage = "!" + e.name + (e.args.isEmpty() ? "" : " " + e.args);
        String aliases = e.aliases.length == 0 ? "" : "\n<dark_gray>Also: !" + String.join(", !", e.aliases);
        return Text.mm("  <yellow>" + usage + " <dark_gray>- <gray>" + e.desc).withStyle(st -> st
                .withHoverEvent(new HoverEvent.ShowText(Text.mm("<gray>Click to type <yellow>" + usage + aliases)))
                .withClickEvent(new ClickEvent.SuggestCommand("!" + e.name + (e.args.isEmpty() ? "" : " "))));
    }

    private static void listAll(CommandSourceStack s) {
        Service.raw(s, Service.LINE);
        Service.raw(s, " <gradient:#FFD54F:#FF8F00><bold>All Attributes</bold></gradient> <dark_gray>(hover for info)");
        for (AttributeClass c : Classes.all()) {
            if (c.adminOnly() && !isAdmin(s)) continue;
            MutableComponent line = Text.mm("  " + c.colored(c.symbol()) + " " + c.display() + " <dark_gray>- <gray>" + c.tagline());
            line.withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(Text.mm(service().classHover(c, 1)))));
            s.sendSystemMessage(line);
        }
        Service.raw(s, Service.LINE);
    }

    /** The skill tree in chat, for players without the mod: every node, what it does, and a click to take it. */
    private static void road(CommandSourceStack s, ServerPlayer p) {
        PlayerData d = service().data(p);
        AttributeClass c = service().cls(d);
        if (c == null) {
            Service.send(s, "<red>You don't have an attribute.");
            return;
        }
        var tree = dev.abps.skills.Skills.tree(c);
        int points = dev.abps.skills.Skills.points(d);
        Service.raw(s, Service.LINE);
        Service.raw(s, " " + c.gradient("<bold>Skill Tree</bold>") + " <gray>- " + c.name() + " · <white>" + points + "</white> point" + (points == 1 ? "" : "s")
                + " to spend <dark_gray>(buy more with !Upgrade, reset with !Skills reset)");
        String[] branches = {"Offense", "Abilities", "Utility", "Defense"};
        if (c.role() == dev.abps.classes.Role.GATHERER) branches[0] = "Prosperity";
        for (int b = 0; b < 4; b++) {
            Service.raw(s, " " + Text.colorTag(dev.abps.skills.SkillTree.BRANCH_COLORS[b]) + "<bold>" + branches[b]);
            for (var n : tree) {
                if (n.branch() != b || n.kind() == dev.abps.skills.SkillTree.Kind.ROOT) continue;
                boolean own = d.skills.contains(n.id()), open = dev.abps.skills.SkillTree.available(n, d.skills);
                String mark = own ? "<green>✔" : open ? "<yellow>◆" : "<dark_gray>✖";
                MutableComponent line = Text.mm("  " + mark + " " + (own ? "<white>" : open ? "<yellow>" : "<gray>") + n.name() + " <dark_gray>- <gray>" + n.desc());
                if (open && points > 0) {
                    final String id = n.id();
                    line.append(Text.mm(" ")).append(service().button("<green><bold>[Take]</bold>", "<green>Spend a point on " + n.name(),
                            () -> dev.abps.skills.Skills.buy(p, id)));
                }
                s.sendSystemMessage(line);
            }
        }
        Service.raw(s, "  <gray>Every level gives skill points and makes your passives stronger; max level fills the whole tree. Level " + AbpsMod.config().maxLevel + ": <gold>Mastery <gray>- " + c.mastery());
        Service.raw(s, Service.LINE);
    }

    private static void top(CommandSourceStack s, String[] args) {
        AttributeClass filter = null;
        if (args.length > 0) {
            filter = Classes.find(String.join("", args));
            if (filter == null) {
                Service.send(s, "<red>Unknown attribute.");
                return;
            }
        }
        ServerPlayer p = s.getPlayer();
        if (p != null && Service.hasMod(p)) {
            service().sendBoard(p, filter == null ? "" : filter.id());
            ServerPlayNetworking.send(p, new Net.OpenMenuPayload("top"));
            return;
        }
        var list = AbpsMod.state().top(filter == null ? null : filter.id(), 10);
        Service.raw(s, Service.LINE);
        Service.raw(s, " <gradient:#FFD54F:#FF8F00><bold>Top Players</bold></gradient>" + (filter == null ? "" : " <gray>- " + filter.display()));
        if (list.isEmpty()) Service.raw(s, "  <gray>Nobody yet.");
        String[] medals = {"<#FFD700>①", "<#C0C0C0>②", "<#CD7F32>③"};
        int rank = 0;
        for (var e : list) {
            AttributeClass c = Classes.get(e.classId());
            if (c == null) continue;
            String place = rank < 3 ? medals[rank] : "<gray>" + (rank + 1) + ".";
            boolean max = e.level() >= AbpsMod.config().maxLevel;
            Service.raw(s, "  " + place + " <white>" + e.name() + " <dark_gray>- " + c.colored(c.symbol() + " " + c.name())
                    + " <gray>Lv <white>" + e.level() + (max ? " <gold>★" : ""));
            rank++;
        }
        Service.raw(s, Service.LINE);
    }

    private static void cooldowns(ServerPlayer p) {
        PlayerData d = service().data(p);
        AttributeClass c = service().cls(d);
        if (c == null) {
            service().send(p, "<red>You don't have an attribute.");
            return;
        }
        service().send(p, "<gold>Cooldowns" + (d.noCooldown ? " <red>(no cooldown mode is on)" : ""));
        for (int i = 1; i <= c.abilityCount(); i++) {
            String state;
            if (!service().unlocked(d, i)) state = "<dark_gray>Locked (Skill Tree)";
            else {
                long left = d.cooldownLeft(i);
                state = left > 0 ? "<red>" + Text.time(left) : "<green>Ready";
            }
            service().raw(p, "  <yellow>[" + service().keyName(p, i) + "] " + c.colored(c.abilityName(i)) + " <dark_gray>- " + state);
        }
        service().raw(p, "  <light_purple>[" + service().keyName(p, 5) + "] " + c.colored(c.abilityName(5)) + " <dark_gray>- "
                + (d.ultCharge >= 1 ? "<gold>READY" : "<gray>" + Math.round(d.ultCharge * 100) + "% charged"));
    }

    private static void giveAttribute(CommandSourceStack s, String[] args) {
        if (args.length < 2) {
            Service.send(s, "<red>Use: !GiveAttribute @player <attribute>");
            return;
        }
        AttributeClass c = Classes.find(String.join("", Arrays.copyOfRange(args, 1, args.length)));
        if (c == null) {
            List<String> names = new ArrayList<>();
            for (AttributeClass x : Classes.all()) names.add(x.name());
            Service.send(s, "<red>Unknown attribute. Options: <gray>" + String.join(", ", names));
            return;
        }
        for (ServerPlayer t : targets(s, args, false)) {
            if (c.adminOnly() && !isAdmin(t.createCommandSourceStack())) {
                Service.send(s, "<red>" + t.getName().getString() + " isn't an operator, so they can't be " + c.name() + ".");
                continue;
            }
            service().setAttribute(t, c, 1);
            service().send(t, "You were given " + c.display() + "<gray>.");
            if (t != s.getPlayer()) Service.send(s, "<green>Gave " + t.getName().getString() + " " + c.name() + ".");
        }
    }

    private static void giveUpgrade(CommandSourceStack s, String[] args) {
        int max = AbpsMod.config().maxLevel;
        if (args.length < 2) {
            Service.send(s, "<red>Use: !GiveUpgrade @player <1-" + max + ">");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            Service.send(s, "<red>That is not a number.");
            return;
        }
        if (level < 1 || level > max) {
            Service.send(s, "<red>Level must be from 1 to " + max + ".");
            return;
        }
        for (ServerPlayer t : targets(s, args, false)) {
            if (service().cls(t) == null) {
                Service.send(s, "<red>" + t.getName().getString() + " has no attribute.");
                continue;
            }
            service().setLevel(t, level);
            service().send(t, "Your attribute is now level <green>" + level + "<gray>.");
            if (t != s.getPlayer()) Service.send(s, "<green>Set " + t.getName().getString() + " to level " + level + ".");
        }
    }

    private static void stats(CommandSourceStack s, ServerPlayer t) {
        PlayerData d = service().data(t);
        AttributeClass c = service().cls(d);
        Service.raw(s, Service.LINE);
        Service.raw(s, " <gold><bold>Stats for " + t.getName().getString() + "</bold> <gray>" + (c == null ? "(none)" : c.name() + " Lv " + d.level));
        stat(s, t, "Max Health", Attributes.MAX_HEALTH);
        stat(s, t, "Walk Speed", Attributes.MOVEMENT_SPEED);
        stat(s, t, "Attack Damage", Attributes.ATTACK_DAMAGE);
        stat(s, t, "Attack Speed", Attributes.ATTACK_SPEED);
        stat(s, t, "Armor", Attributes.ARMOR);
        stat(s, t, "Knockback Resist", Attributes.KNOCKBACK_RESISTANCE);
        stat(s, t, "Fall Damage Mult", Attributes.FALL_DAMAGE_MULTIPLIER);
        stat(s, t, "Safe Fall", Attributes.SAFE_FALL_DISTANCE);
        stat(s, t, "Jump Power", Attributes.JUMP_STRENGTH);
        stat(s, t, "Block Reach", Attributes.BLOCK_INTERACTION_RANGE);
        stat(s, t, "Break Speed", Attributes.BLOCK_BREAK_SPEED);
        stat(s, t, "Underwater Mining", Attributes.SUBMERGED_MINING_SPEED);
        stat(s, t, "Water Movement", Attributes.WATER_MOVEMENT_EFFICIENCY);
        stat(s, t, "Burn Time", Attributes.BURNING_TIME);
        stat(s, t, "Size", Attributes.SCALE);
        List<String> buffs = new ArrayList<>();
        for (String key : d.buffs.keySet()) if (d.buff(key)) buffs.add(key + " " + Text.time(d.buffLeft(key)));
        Service.raw(s, " <gray>Buffs: <white>" + (buffs.isEmpty() ? "none" : String.join(", ", buffs)));
        Service.raw(s, " <gray>Ultimate: <white>" + Math.round(d.ultCharge * 100) + "% <gray>In combat: <white>" + d.inCombat()
                + " <gray>No CD: <white>" + d.noCooldown + " <gray>Minions: <white>" + d.minions.size()
                + " <gray>Has mod: <white>" + Service.hasMod(t));
        Service.raw(s, Service.LINE);
    }

    private static void stat(CommandSourceStack s, ServerPlayer t, String label, Holder<Attribute> attribute) {
        AttributeInstance inst = t.getAttribute(attribute);
        if (inst == null) return;
        long ours = inst.getModifiers().stream().filter(m -> m.id().getNamespace().equals(AbpsMod.MOD_ID)).count();
        Service.raw(s, "  <gray>" + label + ": <white>" + String.format(Locale.ROOT, "%.3f", inst.getValue())
                + " <dark_gray>(base " + String.format(Locale.ROOT, "%.3f", inst.getBaseValue())
                + (ours > 0 ? ", " + ours + " from attribute" : "") + ")");
    }
}
