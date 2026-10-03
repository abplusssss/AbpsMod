package dev.abps.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.abps.AbpsMod;
import dev.abps.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class AbpsClient implements ClientModInitializer {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(AbpsMod.id("keys"));
    /** Abilities 1 to 5, then the ultimate. */
    public static final KeyMapping[] ABILITY_KEYS = new KeyMapping[6];
    public static KeyMapping menuKey;
    /** Shows or hides the HUD panel (level, ability keys and cooldowns, ultimate bar). */
    public static KeyMapping hudKey;
    /** Switches between your PvP and Gatherer attributes. */
    public static KeyMapping roleKey;

    private static boolean jumpWasDown;
    private static boolean wasOnGround = true;
    private static boolean vfxSent;

    @Override
    public void onInitializeClient() {
        ClientPrefs.load();
        if (ClientPrefs.get().autoUpdate) dev.abps.Updater.checkAsync(null, null);
        int[] defaults = {InputConstants.KEY_R, InputConstants.KEY_C, InputConstants.KEY_V, InputConstants.KEY_G, InputConstants.KEY_X, InputConstants.KEY_Z};
        String[] names = {"ability1", "ability2", "ability3", "ability4", "ability5", "ultimate"};
        for (int i = 0; i < 6; i++) {
            ABILITY_KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.abpsmod." + names[i], defaults[i], CATEGORY));
        }
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.abpsmod.menu", InputConstants.KEY_M, CATEGORY));
        hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.abpsmod.hud", InputConstants.KEY_H, CATEGORY));
        roleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.abpsmod.role", InputConstants.KEY_B, CATEGORY));

        registerReceivers();
        dev.abps.client.fx.FxClient.init();
        HudElementRegistry.addLast(AbpsMod.id("hud"), new Hud());
        ClientTickEvents.END_CLIENT_TICK.register(AbpsClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            ClientState.reset();
            vfxSent = false;
            dev.abps.client.fx.FxSystem.clear();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            if (dev.abps.Updater.ready() != null) {
                mc.execute(() -> {
                    if (mc.player != null) mc.player.sendSystemMessage(dev.abps.util.Text.mm("<aqua>AbpsMod " + dev.abps.Updater.ready()
                            + " is downloaded. <gray>Restart Minecraft to use it."));
                });
            }
            ClientState.reset();
            vfxSent = false;
            dev.abps.client.fx.FxSystem.clear();
        });
        ClientSelfTest.init();
        dev.abps.client.fx.FxPreview.init();
        UiPreview.init();
    }

    /** The server knows about the mod once it sees these channels. */
    public static boolean connected() {
        return Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(Net.CastPayload.TYPE);
    }

    public static void send(String action, String arg) {
        if (connected()) ClientPlayNetworking.send(new Net.ActionPayload(action, arg));
    }

    public static void cast(int slot) {
        if (connected()) ClientPlayNetworking.send(new Net.CastPayload(slot));
    }

    private static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(Net.SyncPayload.TYPE, (p, ctx) -> {
            ClientState.sync = p;
            ClientState.syncAt = System.currentTimeMillis();
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.CatalogPayload.TYPE, (p, ctx) -> {
            ClientState.catalog.clear();
            for (Net.ClassInfo c : p.classes()) ClientState.catalog.put(c.id(), c);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.BoardPayload.TYPE, (p, ctx) -> ClientState.board = p);
        ClientPlayNetworking.registerGlobalReceiver(Net.ShopListPayload.TYPE, (p, ctx) -> ClientState.shopList = p);
        ClientPlayNetworking.registerGlobalReceiver(Net.ShopPayload.TYPE, (p, ctx) -> {
            ClientState.shop = p;
            // Opening a shop from the list: switch to the shop screen when its contents arrive
            if (ctx.client().gui.screen() instanceof MenuScreen m && m.waitingForShop(p.owner())) ctx.client().gui.setScreen(new ShopScreen());
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.TravelPayload.TYPE, (p, ctx) -> ClientState.travel = p);
        ClientPlayNetworking.registerGlobalReceiver(Net.ProfilePayload.TYPE, (p, ctx) -> ClientState.profile = p);
        ClientPlayNetworking.registerGlobalReceiver(Net.DungeonsPayload.TYPE, (p, ctx) -> ClientState.dungeons = p);
        ClientPlayNetworking.registerGlobalReceiver(Net.DungeonHudPayload.TYPE, (p, ctx) -> {
            Net.DungeonHudPayload old = ClientState.dungeonHud;
            ClientState.dungeonClock = p.active() && old != null && old.active() && p.elapsed() != old.elapsed();
            ClientState.dungeonHud = p.active() ? p : null;
            ClientState.dungeonHudAt = System.currentTimeMillis();
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.PartyPayload.TYPE, (p, ctx) -> ClientState.party = p.names().isEmpty() ? null : p);
        ClientPlayNetworking.registerGlobalReceiver(Net.VanishPayload.TYPE, (p, ctx) -> {
            ClientState.vanished.clear();
            ClientState.vanished.addAll(p.ids());
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.FxPayload.TYPE, (p, ctx) -> {
            ClientPrefs prefs = ClientPrefs.get();
            switch (p.kind()) {
                case 1 -> {
                    if (!prefs.screenShake) return;
                    // A new shake only replaces a weaker one
                    if (ClientState.shakeTicks <= 0 || p.strength() >= ClientState.shakeStrength) {
                        ClientState.shakeTicks = ClientState.shakeTotal = p.ticks();
                        ClientState.shakeStrength = p.strength();
                    }
                }
                case 2 -> {
                    if (!prefs.screenTint) return;
                    ClientState.tintColor = p.color();
                    ClientState.tintTicks = ClientState.tintTotal = p.ticks();
                    ClientState.tintStrength = p.strength();
                }
                case 3 -> {
                    if (!prefs.screenTint) return;
                    ClientState.flashColor = p.color();
                    ClientState.flashTicks = ClientState.flashTotal = p.ticks();
                    ClientState.flashStrength = p.strength();
                }
                default -> {
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.BannerPayload.TYPE, (p, ctx) -> {
            if (!ClientPrefs.get().showBanners) return;
            ClientState.banners.clear();
            ClientState.bannerAge = 0;
            ClientState.banners.add(new ClientState.Banner(p.title(), p.subtitle(), p.color(), p.ticks()));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.RollPayload.TYPE, (p, ctx) ->
                ctx.client().gui.setScreen(new RollScreen(p.result())));
        ClientPlayNetworking.registerGlobalReceiver(Net.OpenMenuPayload.TYPE, (p, ctx) ->
                ctx.client().gui.setScreen(new MenuScreen(p.tab())));
    }

    private static void tick(Minecraft mc) {
        ClientState.tick();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Tell the server this client can draw the glowing effects itself, so it can skip the block-based ones
        if (!vfxSent && dev.abps.client.fx.FxSystem.ready && dev.abps.client.fx.FxSprites.loaded() && connected()) {
            vfxSent = true;
            send("vfx_ready", "1");
        }

        while (hudKey.consumeClick()) {
            if (!connected() || ClientState.sync == null) continue;
            boolean nowShown = !ClientState.sync.hud();
            send("toggle_hud", "");
            mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(nowShown ? "HUD panel shown" : "HUD panel hidden (press "
                    + hudKey.getTranslatedKeyMessage().getString() + " to show it again)"));
        }
        while (roleKey.consumeClick()) {
            if (connected() && mc.gui.screen() == null) send("switchrole", "");
        }
        while (menuKey.consumeClick()) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new MenuScreen("overview"));
        }
        for (int i = 0; i < 6; i++) {
            while (ABILITY_KEYS[i].consumeClick()) {
                if (connected()) cast(i + 1);
                else player.sendSystemMessage(dev.abps.util.Text.mm("<red>This server doesn't run AbpsMod.</red>"));
            }
        }

        // Double jump: jump pressed again while in the air. The server checks if the class allows it.
        boolean jumpDown = mc.options.keyJump.isDown();
        boolean onGround = player.onGround();
        if (jumpDown && !jumpWasDown && !onGround && !wasOnGround && mc.gui.screen() == null
                && !player.getAbilities().flying && !player.isInWater() && !player.onClimbable()
                && ClientState.sync != null && "windwalker".equals(ClientState.sync.classId())) {
            send("airjump", "");
        }
        jumpWasDown = jumpDown;
        wasOnGround = onGround;
    }
}
