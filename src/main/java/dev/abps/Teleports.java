package dev.abps;

import dev.abps.data.PlayerData;
import dev.abps.data.PlayerData.Loc;
import dev.abps.util.Fx;
import dev.abps.util.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Homes, teleport requests, spawn and back. */
public final class Teleports {

    /** from wants to go to to (or here = to comes to from). */
    public record Request(UUID from, UUID to, boolean here, long expires) {
    }

    /** Requests waiting for an answer, by the player who has to answer. */
    private static final Map<UUID, List<Request>> pending = new HashMap<>();

    private Teleports() {
    }

    private static Service s() {
        return AbpsMod.service();
    }

    private static Config cfg() {
        return AbpsMod.config();
    }

    // ---- Locations ----
    public static Loc here(ServerPlayer p) {
        return new Loc(p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
    }

    public static ServerLevel levelOf(Loc loc) {
        Identifier id = Identifier.tryParse(loc.dimension());
        if (id == null) return null;
        return AbpsMod.server().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static Loc spawn() {
        Loc custom = AbpsMod.state().spawn();
        if (custom != null) return custom;
        ServerLevel over = AbpsMod.server().overworld();
        LevelData.RespawnData data = over.getRespawnData();
        BlockPos pos = data.pos();
        ResourceKey<Level> dim = data.dimension();
        return new Loc(dim.identifier().toString(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
    }

    // ---- Teleporting ----
    /** Checks combat and cooldown, then teleports after a short warmup where you can't move. */
    public static void teleport(ServerPlayer p, Loc dest, String label) {
        PlayerData d = s().data(p);
        if (d.inCombat()) {
            s().send(p, "<red>You can't teleport while in combat. <gray>(" + Text.time(d.combatUntil - System.currentTimeMillis()) + " left)");
            return;
        }
        long wait = d.teleportReadyAt - System.currentTimeMillis();
        if (wait > 0 && !p.isCreative()) {
            s().send(p, "<red>You can teleport again in " + Text.time(wait) + ".");
            return;
        }
        ServerLevel level = levelOf(dest);
        if (level == null) {
            s().send(p, "<red>That place is in a world that doesn't exist anymore.");
            return;
        }
        int warmup = cfg().teleportWarmupSeconds;
        if (warmup <= 0 || p.isCreative()) {
            finish(p, d, dest, label);
            return;
        }
        d.pendingTeleport = () -> finish(p, d, dest, label);
        d.teleportTicksLeft = warmup * 20;
        d.teleportStart = p.position();
        s().actionBar(p, "<aqua>Teleporting to " + label + " in " + warmup + "s. <gray>Don't move.");
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.BEACON_AMBIENT, 0.8f, 1.6f);
    }

    /** Runs every tick for players waiting to teleport. */
    public static void tick(ServerPlayer p, PlayerData d) {
        if (d.pendingTeleport == null) return;
        if (d.teleportStart != null && p.position().distanceToSqr(d.teleportStart) > 0.5 * 0.5) {
            cancelWarmup(p, d, "<red>Teleport cancelled because you moved.");
            return;
        }
        d.teleportTicksLeft--;
        ServerLevel level = (ServerLevel) p.level();
        if (d.teleportTicksLeft % 4 == 0) {
            double a = d.teleportTicksLeft * 0.4;
            level.sendParticles(ParticleTypes.PORTAL, p.getX() + Math.cos(a) * 0.8, p.getY() + 1, p.getZ() + Math.sin(a) * 0.8, 6, 0.1, 0.4, 0.1, 0.1);
        }
        if (d.teleportTicksLeft % 20 == 0 && d.teleportTicksLeft > 0) {
            s().actionBar(p, "<aqua>Teleporting in " + (d.teleportTicksLeft / 20) + "s. <gray>Don't move.");
        }
        if (d.teleportTicksLeft <= 0) {
            Runnable r = d.pendingTeleport;
            d.pendingTeleport = null;
            r.run();
        }
    }

    public static void cancelWarmup(ServerPlayer p, PlayerData d, String message) {
        if (d.pendingTeleport == null) return;
        d.pendingTeleport = null;
        s().actionBar(p, message);
    }

    private static void finish(ServerPlayer p, PlayerData d, Loc dest, String label) {
        if (p.isRemoved()) return;
        if (d.inCombat()) {
            s().send(p, "<red>Teleport cancelled. You are in combat.");
            return;
        }
        ServerLevel level = levelOf(dest);
        if (level == null) return;
        d.back = here(p);
        ServerLevel from = (ServerLevel) p.level();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
        p.teleportTo(level, dest.x(), dest.y(), dest.z(), Set.of(), dest.yaw(), dest.pitch(), false);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x(), dest.y() + 1, dest.z(), 30, 0.3, 0.6, 0.3, 0.05);
        Fx.sound(level, new Vec3(dest.x(), dest.y(), dest.z()), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.2f);
        d.teleportReadyAt = System.currentTimeMillis() + cfg().teleportCooldownSeconds * 1000L;
        s().actionBar(p, "<green>Teleported to " + label + ".");
    }

    // ---- Homes ----
    public static int maxHomes(ServerPlayer p) {
        return dev.abps.command.Commands.isAdmin(p.createCommandSourceStack()) ? 100 : cfg().maxHomes;
    }

    public static void setHome(ServerPlayer p, String name) {
        PlayerData d = s().data(p);
        String key = name.toLowerCase();
        if (!d.homes.containsKey(key) && d.homes.size() >= maxHomes(p)) {
            s().send(p, "<red>You can only have " + maxHomes(p) + " homes. Delete one with <yellow>!DelHome <name></yellow>.");
            return;
        }
        d.homes.put(key, here(p));
        AbpsMod.data().save(p, d);
        s().send(p, "<green>Home <white>" + key + "</white> set.");
        Fx.sound((ServerLevel) p.level(), p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
    }

    public static void home(ServerPlayer p, String name) {
        PlayerData d = s().data(p);
        if (d.homes.isEmpty()) {
            s().send(p, "<red>You have no homes. Set one with <yellow>!SetHome</yellow>.");
            return;
        }
        String key = name == null ? (d.homes.containsKey("home") ? "home" : d.homes.keySet().iterator().next()) : name.toLowerCase();
        Loc loc = d.homes.get(key);
        if (loc == null) {
            s().send(p, "<red>No home named " + key + ". Your homes: <white>" + String.join(", ", d.homes.keySet()));
            return;
        }
        teleport(p, loc, "home " + key);
    }

    public static void delHome(ServerPlayer p, String name) {
        PlayerData d = s().data(p);
        String key = name == null ? "home" : name.toLowerCase();
        if (d.homes.remove(key) == null) {
            s().send(p, "<red>No home named " + key + ".");
            return;
        }
        AbpsMod.data().save(p, d);
        s().send(p, "<green>Deleted home " + key + ".");
    }

    public static void listHomes(ServerPlayer p) {
        PlayerData d = s().data(p);
        if (d.homes.isEmpty()) {
            s().send(p, "<gray>You have no homes. Set one with <yellow>!SetHome</yellow>.");
            return;
        }
        MutableComponent line = Text.mm(Service.PREFIX + "<gray>Homes (" + d.homes.size() + "/" + maxHomes(p) + "): ");
        boolean first = true;
        for (String name : d.homes.keySet()) {
            if (!first) line.append(Text.mm("<dark_gray>, "));
            first = false;
            line.append(s().button("<aqua><underlined>" + name + "</underlined>", "<gray>Click to go to " + name, () -> home(p, name)));
        }
        p.sendSystemMessage(line);
    }

    // ---- Spawn and back ----
    public static void spawn(ServerPlayer p) {
        teleport(p, spawn(), "spawn");
    }

    public static void setSpawn(ServerPlayer p) {
        AbpsMod.state().setSpawn(here(p));
        s().send(p, "<green>Spawn set to where you are standing.");
    }

    public static void back(ServerPlayer p) {
        PlayerData d = s().data(p);
        if (d.back == null) {
            s().send(p, "<red>There is nowhere to go back to.");
            return;
        }
        teleport(p, d.back, "your last spot");
    }

    // ---- Requests ----
    public static void request(ServerPlayer from, ServerPlayer to, boolean here) {
        if (from == to) {
            s().send(from, "<red>You can't send a request to yourself.");
            return;
        }
        PlayerData d = s().data(from);
        if (d.inCombat()) {
            s().send(from, "<red>You can't teleport while in combat.");
            return;
        }
        List<Request> list = pending.computeIfAbsent(to.getUUID(), k -> new ArrayList<>());
        list.removeIf(r -> r.from().equals(from.getUUID()));
        list.add(new Request(from.getUUID(), to.getUUID(), here, System.currentTimeMillis() + cfg().tpaExpireSeconds * 1000L));
        s().send(from, "<green>Request sent to <white>" + to.getName().getString() + "</white>. <gray>Cancel it with <yellow>!TpCancel</yellow>.");

        String what = here ? "wants you to teleport to them" : "wants to teleport to you";
        s().send(to, "<white>" + from.getName().getString() + " <aqua>" + what + ".");
        String name = from.getName().getString();
        MutableComponent buttons = Text.mm("    ")
                .append(s().button("<green><bold>[ ACCEPT ]</bold>", "<green>Same as !TPA " + name, () -> accept(to, name)))
                .append(Text.mm("   "))
                .append(s().button("<red><bold>[ DENY ]</bold>", "<red>Same as !TPD " + name, () -> deny(to, name)));
        to.sendSystemMessage(buttons);
        Fx.sound((ServerLevel) to.level(), to, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.4f);
    }

    /** Finds the request to answer: from the named player, or the newest one. */
    private static Request take(ServerPlayer target, String fromName) {
        List<Request> list = pending.get(target.getUUID());
        if (list == null) return null;
        long now = System.currentTimeMillis();
        list.removeIf(r -> r.expires() < now);
        Request found = null;
        for (Iterator<Request> it = list.iterator(); it.hasNext(); ) {
            Request r = it.next();
            ServerPlayer from = AbpsMod.server().getPlayerList().getPlayer(r.from());
            if (fromName == null || (from != null && from.getName().getString().equalsIgnoreCase(fromName))) found = r;
        }
        if (found != null) list.remove(found);
        return found;
    }

    public static void accept(ServerPlayer target, String fromName) {
        Request r = take(target, fromName);
        if (r == null) {
            s().send(target, "<red>You have no teleport requests" + (fromName == null ? "." : " from " + fromName + "."));
            return;
        }
        ServerPlayer from = AbpsMod.server().getPlayerList().getPlayer(r.from());
        if (from == null) {
            s().send(target, "<red>That player is not online anymore.");
            return;
        }
        s().send(target, "<green>Accepted " + from.getName().getString() + "'s request.");
        s().send(from, "<green>" + target.getName().getString() + " accepted your request.");
        if (r.here()) teleport(target, here(from), from.getName().getString());
        else teleport(from, here(target), target.getName().getString());
    }

    public static void deny(ServerPlayer target, String fromName) {
        Request r = take(target, fromName);
        if (r == null) {
            s().send(target, "<red>You have no teleport requests.");
            return;
        }
        s().send(target, "<gray>Request denied.");
        ServerPlayer from = AbpsMod.server().getPlayerList().getPlayer(r.from());
        if (from != null) s().send(from, "<red>" + target.getName().getString() + " denied your request.");
    }

    public static void cancel(ServerPlayer from) {
        boolean any = false;
        for (List<Request> list : pending.values()) any |= list.removeIf(r -> r.from().equals(from.getUUID()));
        s().send(from, any ? "<gray>Your teleport requests were cancelled." : "<red>You have no requests to cancel.");
    }

    public static void forget(UUID id) {
        pending.remove(id);
        for (List<Request> list : pending.values()) list.removeIf(r -> r.from().equals(id));
    }
}
