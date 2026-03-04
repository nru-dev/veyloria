package dev.laakirun.veyloria;

import com.mojang.logging.LogUtils;
import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.registry.VeyloriaAttachments;
import dev.laakirun.veyloria.common.registry.VeyloriaEntityTypes;
import dev.laakirun.veyloria.common.registry.VeyloriaMenus;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.config.ConfigService;
import dev.laakirun.veyloria.server.game.VeyloriaServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

@Mod(VeyloriaConstants.MOD_ID)
public final class VeyloriaMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public VeyloriaMod(IEventBus modEventBus) {
        VeyloriaAttachments.register(modEventBus);
        VeyloriaEntityTypes.register(modEventBus);
        VeyloriaMenus.register(modEventBus);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        VeyloriaServerEvents.register();
        LOGGER.info("Initializing mod {}", VeyloriaConstants.MOD_ID);
    }

    @EventBusSubscriber(modid = VeyloriaConstants.MOD_ID)
    public static final class ServerEvents {
        private ServerEvents() {
        }

        @SubscribeEvent
        public static void onServerAboutToStart(ServerAboutToStartEvent event) {
            ConfigService configService = new ConfigService();
            VeyloriaServerRuntime.instance().initialize(configService.loadServerConfig(), configService.loadRatesConfig());
            VeyloriaServerRuntime.instance().testWorldLayoutService().onServerStarting(event.getServer());
        }
    }
}
