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
    public static final KeyMapping[] ABILITY_KEYS = new KeyMapping[5];
    public static KeyMapping menuKey;

    private static boolean jumpWasDown;
    private static boolean wasOnGround = true;

    @Override
    public void onInitializeClient() {
        ClientPrefs.load();
        int[] defaults = {InputConstants.KEY_R, InputConstants.KEY_C, InputConstants.KEY_V, InputConstants.KEY_G, InputConstants.KEY_Z};
        String[] names = {"ability1", "ability2", "ability3", "ability4", "ultimate"};
        for (int i = 0; i < 5; i++) {
            ABILITY_KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.abpsmod." + names[i], defaults[i], CATEGORY));
        }
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.abpsmod.menu", InputConstants.KEY_M, CATEGORY));

        registerReceivers();
        HudElementRegistry.addLast(AbpsMod.id("hud"), new Hud());
        ClientTickEvents.END_CLIENT_TICK.register(AbpsClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientState.reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> ClientState.reset());
        ClientSelfTest.init();
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

        while (menuKey.consumeClick()) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new MenuScreen("overview"));
        }
        for (int i = 0; i < 5; i++) {
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
