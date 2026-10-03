package dev.abps.games;

import dev.abps.AbpsMod;
import dev.abps.Teleports;
import dev.abps.data.PlayerData;
import dev.abps.dungeon.Builder;
import dev.abps.util.Fx;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * One running party activity (a Siege or a party game) in the arena world. It builds its arena, pulls everyone in,
 * runs, then sends everyone home and clears the arena away.
 */
public abstract class Game {

    public enum State {BUILDING, ACTIVE, OVER}

    /** Tag on items a game hands out, so they can be taken back when it ends. */
    public static final String ITEM_TAG = "abps_game_item";

    final int slot;
    public final GameDef def;
    final ServerLevel level;
    final BlockPos origin;
    final Random rnd = new Random();
    final LinkedHashSet<UUID> players = new LinkedHashSet<>();
    final Map<UUID, PlayerData.Loc> returns = new HashMap<>();
    final Builder builder = new Builder();
    State state = State.BUILDING;
    long startedAt;
    int ticks;
    /** Ticks left before the arena is torn down after the game ended (time to read the results). */
    int closing = -1;

    Game(int slot, GameDef def, ServerLevel level, List<ServerPlayer> party) {
        this.slot = slot;
        this.def = def;
        this.level = level;
        // Far from where dungeons used to be built, one arena per slot
        this.origin = new BlockPos(slot * 400, 100, 6000);
        for (ServerPlayer p : party) {
            players.add(p.getUUID());
            returns.put(p.getUUID(), Teleports.here(p));
        }
    }

    // ------------------------------------------------------------------ lifecycle hooks

    /** Queues the arena's blocks into builder. */
    abstract void build();

    /** Where a player appears when the game starts (and respawns, unless respawnPoint says otherwise). */
    abstract Vec3 spawnPoint(ServerPlayer p);

    Vec3 respawnPoint(ServerPlayer p) {
        return spawnPoint(p);
    }

    /** Called once everyone is in. */
    abstract void begin();

    /** Called every tick while ACTIVE. */
    abstract void update();

    /** The two HUD lines ("objective|status"). */
    abstract String hud();

    /** Half the width of the arena, for clean-up. */
    int radius() {
        return 40;
    }

    /** Returns false when a death should be turned into a respawn. */
    boolean allowDeath(ServerPlayer p) {
        respawn(p);
        return false;
    }

    /** Whether a player may break this block (normally nothing in the arena world can be broken). */
    boolean canBreak(ServerPlayer p, BlockPos pos) {
        return false;
    }

    void onLeave(ServerPlayer p) {
    }

    // ------------------------------------------------------------------ helpers

    public List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : players) {
            ServerPlayer p = AbpsMod.server().getPlayerList().getPlayer(id);
            if (p != null && p.level() == level) out.add(p);
        }
        return out;
    }

    void tell(String msg) {
        for (ServerPlayer p : online()) AbpsMod.service().send(p, msg);
    }

    void bar(String msg) {
        for (ServerPlayer p : online()) AbpsMod.service().actionBar(p, msg);
    }

    void banner(String title, String sub, int color) {
        for (ServerPlayer p : online()) AbpsMod.service().banner(p, title, sub, color, 70);
    }

    void sound(SoundEvent s, float vol, float pitch) {
        for (ServerPlayer p : online()) Fx.sound(level, p, s, vol, pitch);
    }

    void sound(Holder<SoundEvent> s, float vol, float pitch) {
        for (ServerPlayer p : online()) Fx.sound(level, p, s, vol, pitch);
    }

    void teleport(ServerPlayer p, Vec3 at, float yaw) {
        p.teleportTo(level, at.x, at.y, at.z, Set.<Relative>of(), yaw, 0, false);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
    }

    void respawn(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.clearFire();
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 4));
        teleport(p, respawnPoint(p), p.getYRot());
    }

    /** Gives a game item that is taken back when the game ends. */
    void give(ServerPlayer p, ItemStack stack) {
        net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tagged());
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    private static net.minecraft.nbt.CompoundTag tagged() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putBoolean(ITEM_TAG, true);
        return t;
    }

    static boolean isGameItem(ItemStack s) {
        var data = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(ITEM_TAG, false);
    }

    /** Takes back every item the game handed out. */
    static void takeItems(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isGameItem(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    void enter(ServerPlayer p) {
        teleport(p, spawnPoint(p), 0);
        p.setHealth(p.getMaxHealth());
        AbpsMod.service().banner(p, "<bold>" + dev.abps.util.Text.colorTag(def.color()) + def.name() + "</bold>", "<gray>" + def.blurb(), def.color(), 70);
    }

    void sendHome(ServerPlayer p) {
        takeItems(p);
        PlayerData.Loc back = returns.get(p.getUUID());
        if (back == null) back = Teleports.spawn();
        ServerLevel l = Teleports.levelOf(back);
        if (l == null) {
            back = Teleports.spawn();
            l = Teleports.levelOf(back);
        }
        hideHud(p);
        p.removeAllEffects();
        p.setGlowingTag(false);
        p.teleportTo(l, back.x(), back.y(), back.z(), Set.<Relative>of(), back.yaw(), back.pitch(), false);
        p.fallDistance = 0;
        p.clearFire();
    }

    static void hideHud(ServerPlayer p) {
        if (ServerPlayNetworking.canSend(p, dev.abps.net.Net.DungeonHudPayload.TYPE))
            ServerPlayNetworking.send(p, new dev.abps.net.Net.DungeonHudPayload(false, "", 0, 0, "", 0, 0, 0, 0));
    }

    void sendHud(int step, int steps, int wave) {
        long elapsed = startedAt == 0 ? 0 : System.currentTimeMillis() - startedAt;
        var payload = new dev.abps.net.Net.DungeonHudPayload(true, def.name(), step, steps, hud(), -1, elapsed, wave, def.color());
        for (ServerPlayer p : online()) {
            if (ServerPlayNetworking.canSend(p, dev.abps.net.Net.DungeonHudPayload.TYPE)) ServerPlayNetworking.send(p, payload);
            else if (ticks % 40 == 0) AbpsMod.service().actionBar(p, "<white>" + hud().replace("|", "  <dark_gray>·</dark_gray>  "));
        }
    }

    /** Ends the game: results stay up a few seconds, then everyone goes home. */
    void finish(int delayTicks) {
        if (closing >= 0) return;
        closing = delayTicks;
    }

    /** Sends everyone home and starts clearing the arena. */
    void end() {
        if (state == State.OVER) return;
        state = State.OVER;
        for (ServerPlayer p : online()) sendHome(p);
        players.clear();
        int r = radius();
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(origin.getX() - r, origin.getY() - 30, origin.getZ() - r,
                origin.getX() + r, origin.getY() + 40, origin.getZ() + r), e -> !(e instanceof ServerPlayer))) e.discard();
        Builder clear = new Builder();
        clear.clear(origin.getX() - r, origin.getY() - 20, origin.getZ() - r, origin.getX() + r, origin.getY() + 35, origin.getZ() + r);
        Games.teardown(slot, clear);
    }

    /** Called by Games every tick. */
    void tick() {
        ticks++;
        if (closing >= 0) {
            if (closing-- == 0) end();
            return;
        }
        update();
    }
}
