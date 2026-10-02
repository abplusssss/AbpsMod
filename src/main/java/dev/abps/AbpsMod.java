package dev.abps;

import dev.abps.command.Commands;
import dev.abps.data.DataStore;
import dev.abps.data.ServerState;
import dev.abps.events.ServerEvents;
import dev.abps.net.Net;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbpsMod implements ModInitializer {

	public static final String MOD_ID = "abpsmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Config config;
	private static MinecraftServer server;
	private static DataStore data;
	private static ServerState state;
	private static dev.abps.shop.Shops shops;
	private static Service service;

	@Override
	public void onInitialize() {
		config = Config.load();
		dev.abps.util.FxTypes.register();
		Net.register();
		Classes.init();
		ServerEvents.register();
		Commands.register();
		LOGGER.info("AbpsMod loaded {} attributes", Classes.all().size());
	}

	/** Called when a server (or singleplayer world) starts. */
	public static void start(MinecraftServer s) {
		// In development, apply every hook now so mistakes show up right away instead of on first join
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
			LOGGER.info("All AbpsMod hooks applied");
		}
		server = s;
		data = new DataStore(s);
		state = new ServerState(data.root());
		shops = new dev.abps.shop.Shops(s, data.root());
		service = new Service(s);
		dev.abps.dungeon.Dungeons.load();
		SelfTest.maybeRun(s);
		checkForUpdate();
	}

	/** Dedicated servers look for a newer AbpsMod at start and every 6 hours (see ServerEvents), if the config allows it. */
	public static void checkForUpdate() {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.SERVER || !config.autoUpdate) return;
		Updater.checkAsync(config.updateUrl, msg -> {
			MinecraftServer s = server;
			if (s != null) s.execute(() -> {
				for (var p : s.getPlayerList().getPlayers()) {
					if (dev.abps.command.Commands.isAdmin(p.createCommandSourceStack())) service.send(p, "<aqua>" + msg);
				}
			});
		});
	}

	public static void stop() {
		if (state != null) state.saveNow();
		if (shops != null) shops.shutdown();
		if (data != null) data.shutdown();
		server = null;
	}

	public static void reloadConfig() {
		config = Config.load();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static Config config() {
		return config;
	}

	public static MinecraftServer server() {
		return server;
	}

	public static DataStore data() {
		return data;
	}

	public static dev.abps.shop.Shops shops() {
		return shops;
	}

	public static ServerState state() {
		return state;
	}

	public static Service service() {
		return service;
	}

	public static boolean running() {
		return server != null && service != null;
	}
}
