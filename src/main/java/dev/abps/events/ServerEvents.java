package dev.abps.events;

import dev.abps.AbpsMod;
import dev.abps.Combat;
import dev.abps.Service;
import dev.abps.Teleports;
import dev.abps.classes.AttributeClass;
import dev.abps.classes.Hit;
import dev.abps.classes.Necromancer;
import dev.abps.command.Commands;
import dev.abps.data.PlayerData;
import dev.abps.net.Net;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import dev.abps.util.Text;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.UUID;

public final class ServerEvents {

    private ServerEvents() {
    }

    private static PlayerData data(ServerPlayer p) {
        return AbpsMod.data().get(p);
    }

    private static AttributeClass cls(ServerPlayer p) {
        return AbpsMod.service().active(data(p));
    }

    public static void register() {
        // ---- Lifecycle ----
        ServerLifecycleEvents.SERVER_STARTED.register(AbpsMod::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            dev.abps.dungeon.Dungeons.shutdown();
            for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) AbpsMod.service().handleQuit(p);
            Tasks.clear();
            AbpsMod.stop();
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerEvents::tick);
        dev.abps.dungeon.Gates.register();

        // ---- Join and leave ----
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.player;
            String lockout = Combat.lockoutMessage(p);
            if (lockout != null) {
                handler.disconnect(Text.mm(lockout));
                return;
            }
            AbpsMod.service().handleJoin(p);
            // After the welcome, so these land below it in chat
            dev.abps.util.Tasks.later(60, () -> {
                if (p.hasDisconnected()) return;
                AbpsMod.shops().onJoin(p);
                dev.abps.Profiles.onJoin(p);
                if (dev.abps.Updater.ready() != null && dev.abps.command.Commands.isAdmin(p.createCommandSourceStack())) {
                    AbpsMod.service().send(p, "<aqua>AbpsMod " + dev.abps.Updater.ready() + " is downloaded. Restart the server to use it.");
                }
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!AbpsMod.running()) return;
            ServerPlayer p = handler.player;
            AbpsMod.service().setVfxReady(p, false);
            if (AbpsMod.data().peek(p.getUUID()) == null) return; // was kicked for a lockout before loading
            dev.abps.dungeon.Dungeons.onQuit(p);
            dev.abps.items.CustomItems.forget(p.getUUID());
            Combat.onQuit(p);
            Teleports.forget(p.getUUID());
            AbpsMod.service().handleQuit(p);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                Tasks.later(1, () -> {
                    if (!newPlayer.isRemoved()) AbpsMod.service().reapply(newPlayer);
                }));

        // ---- Damage ----
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(DamageHooks::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(ServerEvents::afterDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer p) || !AbpsMod.running()) return true;
            // In a dungeon you are downed instead of dying; elsewhere a Phoenix Feather can save you
            if (!dev.abps.dungeon.Dungeons.allowDeath(p)) return false;
            if (!dev.abps.items.CustomItems.allowDeath(p)) return false;
            AttributeClass c = cls(p);
            if (c != null && !c.allowDeath(p, data(p), source)) return false;
            return c == null || dev.abps.skills.Skills.allowDeath(p, data(p), c);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register(ServerEvents::afterDeath);

        // ---- Blocks and items ----
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player instanceof ServerPlayer p && AbpsMod.running()) {
                AttributeClass c = cls(p);
                if (c != null) c.onBlockAttack(p, data(p), level.getBlockState(pos));
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer p && AbpsMod.running()) {
                AttributeClass c = cls(p);
                if (c != null) {
                    c.afterBlockBreak(p, data(p), (ServerLevel) level, pos, state);
                    if (c.role() == dev.abps.classes.Role.GATHERER && !p.isCreative()) AbpsMod.service().addGatherCharge(p, data(p), c.gatherCharge(state));
                }
                dev.abps.items.CustomItems.afterBreak(p, (ServerLevel) level, pos, state);
            }
        });
        // Dungeons can't be dug through or built in (creative players can still edit them)
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(AbpsMod.running() && level.dimension() == dev.abps.dungeon.Dungeons.WORLD && !player.isCreative()));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                AbpsMod.running() && level.dimension() == dev.abps.dungeon.Dungeons.WORLD && !player.isCreative()
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer p && AbpsMod.running()) {
                AttributeClass c = cls(p);
                ItemStack stack = p.getItemInHand(hand);
                if (c != null && !c.allowUseItem(p, data(p), stack)) return InteractionResult.FAIL;
                // Custom dungeon weapons and tools have a right-click power
                if (dev.abps.items.CustomItems.use(p, stack)) return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        EntityElytraEvents.ALLOW.register(entity -> {
            if (!(entity instanceof ServerPlayer p) || !AbpsMod.running()) return true;
            AttributeClass c = cls(p);
            if (c != null && c.id().equals("tank")) {
                AbpsMod.service().actionBar(p, "<red>You are too heavy to fly.");
                return false;
            }
            return true;
        });

        // ---- Entities appearing ----
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!AbpsMod.running()) return;
            // Summons and visuals never survive a restart
            if ((entity.entityTags().contains("abps_fx") && !dev.abps.util.Vfx.isLive(entity))
                    || (entity.entityTags().contains(dev.abps.dungeon.Rig.TAG) && !dev.abps.dungeon.Rig.isLive(entity))
                    || entity.entityTags().contains(Targets.MINION_TAG) && Targets.minionOwner(entity) == null) {
                entity.discard();
                return;
            }
            if (entity instanceof AbstractArrow arrow && arrow.tickCount == 0 && arrow.getOwner() instanceof ServerPlayer p
                    && !arrow.entityTags().contains("abps_ability")) {
                AttributeClass c = cls(p);
                if (c != null) c.onShoot(p, data(p), arrow);
            }
        });

        // ---- Chat ----
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.signedContent().trim();
            if (text.length() < 2 || text.charAt(0) != '!') return true;
            String label = text.substring(1).split("\\s+")[0];
            if (!Commands.isCommand(label)) return true; // normal "!" messages still go through
            sender.level().getServer().execute(() -> Commands.handle(sender.createCommandSourceStack(), text.substring(1)));
            return false;
        });
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.STYLING_PHASE, (sender, message) -> {
            if (sender == null || !AbpsMod.running() || !AbpsMod.config().chatTags) return message;
            PlayerData d = AbpsMod.data().peek(sender.getUUID());
            Component tag = d == null ? null : d.chatTag;
            if (tag == null) return message;
            return Component.empty().append(tag).append(Component.literal(" ")).append(message);
        });

        // ---- Packets from the client mod ----
        ServerPlayNetworking.registerGlobalReceiver(Net.CastPayload.TYPE, (payload, ctx) ->
                AbpsMod.service().cast(ctx.player(), payload.slot()));
        ServerPlayNetworking.registerGlobalReceiver(Net.ActionPayload.TYPE, (payload, ctx) -> {
            // A broken menu action must never take the server (or a singleplayer game) down with it
            try {
                action(ctx.player(), payload.action(), payload.arg());
            } catch (Throwable t) {
                AbpsMod.LOGGER.error("Menu action '{}' ({}) failed for {}", payload.action(), payload.arg(), ctx.player().getName().getString(), t);
                AbpsMod.service().send(ctx.player(), "<red>That didn't work (" + t.getClass().getSimpleName() + "). The details are in the log.");
            }
        });
    }

    // ================= Tick =================
    private static long ticks;

    private static void tick(MinecraftServer server) {
        if (!AbpsMod.running()) return;
        dev.abps.util.Vfx.theme(0);
        Tasks.tick();
        ticks++;
        if (ticks % (20L * 60 * 60 * 6) == 0) AbpsMod.checkForUpdate();
        dev.abps.dungeon.Dungeons.tick();
        boolean slow = ticks % 5 == 0;
        Service s = AbpsMod.service();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerData d = data(p);
            DamageHooks.tickTap(p, d);
            Teleports.tick(p, d);
            if (ticks % 20 == 0) d.playSeconds++;
            if (!slow) continue;
            AttributeClass c = s.active(d);
            if (c == null) {
                if (Service.hasMod(p) && ticks % 20 == 0) s.sync(p, false);
                continue;
            }
            d.tickCount++;
            try {
                c.tick(p, d);
                dev.abps.skills.Skills.tick(p, d, c);
                s.tickPlayer(p, d, c);
            } catch (Exception ex) {
                if (d.tickCount % 200 == 1) AbpsMod.LOGGER.warn("Error while ticking {} for {}", c.name(), p.getName().getString(), ex);
            }
        }
        if (ticks % 1200 == 0) AbpsMod.state().saveIfDirty();
        // Autosave every 5 minutes on top of saving on every change
        if (ticks % 6000 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) AbpsMod.data().save(p, data(p));
        }
    }

    // ================= Damage =================
    private static void afterDamage(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source,
                                    float base, float taken, boolean blocked) {
        if (!AbpsMod.running() || taken <= 0) return;
        Service s = AbpsMod.service();
        var src = source.getEntity();

        if (victim instanceof ServerPlayer vp && src != vp) data(vp).damageTaken += taken;
        if (src instanceof ServerPlayer p && p != victim) {
            PlayerData d = data(p);
            d.damageDealt += taken;
            d.lastHit = victim.getUUID();
            d.lastHitTime = System.currentTimeMillis();
            Hit hit = DamageHooks.hitOf(p, source);
            if (hit != null && hit.melee()) {
                d.lastMelee = victim.getUUID();
                d.lastMeleeTime = d.lastHitTime;
            }
            s.addUltCharge(p, d, taken, victim instanceof ServerPlayer);
            AttributeClass c = s.active(d);
            if (c != null && hit != null && !Targets.abilityDamage) c.afterHit(p, d, victim, taken, hit);
            if (c != null) dev.abps.skills.Skills.afterHit(p, d, c, taken);
            if (hit != null && !Targets.abilityDamage) dev.abps.items.CustomItems.afterHit(p, victim, taken, hit);
            if (victim instanceof ServerPlayer vp) Combat.tag(p, vp);
        } else if (src != null) {
            // Necromancer minions heal their owner
            UUID owner = Targets.minionOwner(src);
            ServerPlayer op = owner == null ? null : AbpsMod.server().getPlayerList().getPlayer(owner);
            if (op != null && s.active(data(op)) instanceof Necromancer necro) {
                necro.minionDealt(op, data(op), taken);
                s.addUltCharge(op, data(op), taken * 0.5f, victim instanceof ServerPlayer);
            }
        }

        if (victim instanceof ServerPlayer vp) {
            PlayerData d = data(vp);
            AttributeClass c = s.active(d);
            if (c != null) c.afterDamaged(vp, d, source, taken);
        }
    }

    private static void afterDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        if (!AbpsMod.running()) return;
        Targets.forgetMinion(entity.getUUID());
        int lostStreak = 0;
        if (entity instanceof ServerPlayer dead) {
            PlayerData dd = data(dead);
            dd.back = Teleports.here(dead);
            dd.deaths++;
            lostStreak = dd.killStreak;
            dd.killStreak = 0;
        }
        var src = source.getEntity();
        if (src instanceof ServerPlayer killer && killer != entity) {
            PlayerData kd = data(killer);
            if (entity instanceof ServerPlayer victim) {
                kd.kills++;
                kd.killStreak++;
                kd.bestStreak = Math.max(kd.bestStreak, kd.killStreak);
                dev.abps.Streaks.onPlayerKill(killer, kd, victim, lostStreak);
            } else if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
                kd.mobKills++;
            }
            AttributeClass c = cls(killer);
            if (c != null) {
                c.onKill(killer, data(killer), entity);
                dev.abps.skills.Skills.onKill(killer, data(killer), c);
            }
            dev.abps.items.CustomItems.onKill(killer, entity);
            return;
        }
        UUID owner = Targets.minionOwner(src);
        ServerPlayer op = owner == null ? null : AbpsMod.server().getPlayerList().getPlayer(owner);
        if (op != null && cls(op) instanceof Necromancer necro) necro.tryRise(op, data(op), entity);
    }

    // ================= Menu buttons and client actions =================
    private static final java.util.Set<String> VANILLA_ADMIN = java.util.Set.of(
            "gamemode", "time", "weather", "kick", "ban", "op", "deop", "tp", "kill");

    private static void action(ServerPlayer p, String action, String arg) {
        Service s = AbpsMod.service();
        PlayerData d = data(p);
        switch (action) {
            case "upgrade" -> s.confirmUpgrade(p);
            case "reroll" -> s.confirmReroll(p);
            case "toggle_hud" -> {
                d.hud = !d.hud;
                AbpsMod.data().save(p, d);
                s.sync(p, true);
            }
            case "toggle_powers" -> s.setPowersOff(p, !d.powersOff);
            case "role" -> {
                dev.abps.classes.Role r = dev.abps.classes.Role.of(arg);
                if (r != null) s.chooseRole(p, r);
            }
            case "switchrole" -> s.switchRole(p);
            case "rerollother" -> s.confirmRerollOther(p);
            case "skill" -> dev.abps.skills.Skills.buy(p, arg);
            case "respec" -> dev.abps.skills.Skills.respec(p);
            case "toggle_aura" -> {
                d.pyroAura = !d.pyroAura;
                AbpsMod.data().save(p, d);
                s.send(p, d.pyroAura ? "<gold>Heat aura on: <gray>enemies near you catch fire." : "<gray>Heat aura off: <gray>enemies near you won't catch fire.");
                s.sync(p, true);
            }
            case "toggle_panel" -> {
                d.sidebar = !d.sidebar;
                AbpsMod.data().save(p, d);
                s.sync(p, true);
            }
            case "board" -> s.sendBoard(p, arg);
            case "catalog" -> s.sendCatalog(p);
            case "vfx_ready" -> s.setVfxReady(p, "1".equals(arg));
            case "airjump" -> {
                AttributeClass c = s.active(d);
                if (c != null && !p.onGround() && !p.getAbilities().flying && !p.isInWater()) c.onAirJump(p, d);
            }
            case "admin" -> {
                // Menu Admin tab: runs one of the mod's operator commands as this player
                var src = p.createCommandSourceStack();
                if (!Commands.isAdmin(src)) {
                    s.actionBar(p, "<red>Only operators can do that.");
                    return;
                }
                String label = arg.trim().split("\\s+")[0];
                if (Commands.isAdminCommand(label)) Commands.handle(src, arg);
            }
            case "vanilla" -> {
                // Menu Admin tab: a short list of vanilla operator commands. Vanilla still checks the player's own permissions.
                var src = p.createCommandSourceStack();
                if (!Commands.isAdmin(src)) {
                    s.actionBar(p, "<red>Only operators can do that.");
                    return;
                }
                String root = arg.trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
                if (VANILLA_ADMIN.contains(root) && arg.matches("[A-Za-z0-9_@ .:\\-]{1,100}")) {
                    AbpsMod.server().getCommands().performPrefixedCommand(src, arg);
                }
            }
            case "home" -> Teleports.home(p, arg.isEmpty() ? null : arg);
            case "spawn" -> Teleports.spawn(p);
            case "shop" -> AbpsMod.shops().handle(p, arg);
            case "travel" -> dev.abps.Profiles.travel(p, arg);
            case "profile" -> dev.abps.Profiles.sendProfile(p);
            case "daily" -> dev.abps.Profiles.claimDaily(p);
            case "dungeon" -> dev.abps.dungeon.Dungeons.handle(p, arg);
            default -> {
            }
        }
    }
}
