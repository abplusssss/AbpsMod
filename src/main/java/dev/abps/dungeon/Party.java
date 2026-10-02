package dev.abps.dungeon;

import dev.abps.AbpsMod;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Groups of up to 4 players who run dungeons together. The first member is the leader. */
public final class Party {

    public static final int MAX = 4;

    private static final Map<UUID, Party> BY_PLAYER = new HashMap<>();
    /** Invites waiting for an answer: invited player to {party, time sent}. */
    private static final Map<UUID, Object[]> INVITES = new HashMap<>();

    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

    private Party(UUID leader) {
        members.add(leader);
    }

    public static Party of(ServerPlayer p) {
        return BY_PLAYER.get(p.getUUID());
    }

    /** The player's party, or a new one with just them in it. */
    public static Party ensure(ServerPlayer p) {
        return BY_PLAYER.computeIfAbsent(p.getUUID(), Party::new);
    }

    public UUID leader() {
        return members.iterator().next();
    }

    public boolean isLeader(ServerPlayer p) {
        return leader().equals(p.getUUID());
    }

    public Set<UUID> ids() {
        return members;
    }

    public int size() {
        return members.size();
    }

    /** The members who are online right now. */
    public List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayer p = AbpsMod.server().getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static void tell(ServerPlayer p, String msg) {
        AbpsMod.service().send(p, msg);
    }

    private void tellAll(String msg) {
        for (ServerPlayer m : online()) tell(m, msg);
    }

    public static void invite(ServerPlayer from, ServerPlayer to) {
        if (to == from) {
            tell(from, "<red>You can't invite yourself.");
            return;
        }
        Party p = ensure(from);
        if (!p.isLeader(from)) {
            tell(from, "<red>Only the party leader can invite.");
            return;
        }
        if (p.size() >= MAX) {
            tell(from, "<red>Your party is full (" + MAX + " players).");
            return;
        }
        if (of(to) != null && of(to).size() > 1) {
            tell(from, "<red>" + to.getName().getString() + " is already in a party.");
            return;
        }
        INVITES.put(to.getUUID(), new Object[]{p, System.currentTimeMillis()});
        tell(from, "<green>Invited <white>" + to.getName().getString() + "</white> to your party.");
        tell(to, "<aqua>" + from.getName().getString() + "</aqua> <gray>invited you to their dungeon party. Open the <white>Dungeons</white> tab or type <yellow>!party accept</yellow>.");
    }

    /** The party that invited this player in the last two minutes, or null. */
    public static Party invitedTo(ServerPlayer p) {
        Object[] inv = INVITES.get(p.getUUID());
        if (inv == null || System.currentTimeMillis() - (long) inv[1] > 120_000) return null;
        return (Party) inv[0];
    }

    public static void accept(ServerPlayer p) {
        Party target = invitedTo(p);
        INVITES.remove(p.getUUID());
        if (target == null || target.size() == 0) {
            tell(p, "<red>You have no party invite.");
            return;
        }
        if (target.size() >= MAX) {
            tell(p, "<red>That party is full.");
            return;
        }
        if (Dungeons.runOf(p) != null) {
            tell(p, "<red>Finish your dungeon run first.");
            return;
        }
        leave(p, false);
        target.members.add(p.getUUID());
        BY_PLAYER.put(p.getUUID(), target);
        target.tellAll("<aqua>" + p.getName().getString() + "</aqua> <gray>joined the party (" + target.size() + "/" + MAX + ").");
    }

    public static void decline(ServerPlayer p) {
        INVITES.remove(p.getUUID());
    }

    public static void leave(ServerPlayer p, boolean announce) {
        Party party = BY_PLAYER.remove(p.getUUID());
        if (party == null) return;
        party.members.remove(p.getUUID());
        if (announce) {
            tell(p, "<gray>You left the party.");
            party.tellAll("<gray>" + p.getName().getString() + " left the party.");
        }
    }

    public static void kick(ServerPlayer leader, ServerPlayer who) {
        Party party = of(leader);
        if (party == null || !party.isLeader(leader) || !party.members.contains(who.getUUID()) || who == leader) {
            tell(leader, "<red>You can't kick them.");
            return;
        }
        leave(who, false);
        tell(who, "<gray>You were removed from the party.");
        party.tellAll("<gray>" + who.getName().getString() + " was removed from the party.");
    }

    /** Called when a player leaves the server. */
    public static void onQuit(ServerPlayer p) {
        INVITES.remove(p.getUUID());
        Party party = of(p);
        if (party != null && party.size() > 1) leave(p, true);
        else BY_PLAYER.remove(p.getUUID());
    }
}
