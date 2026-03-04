package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.registry.VeyloriaMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = VeyloriaConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VeyloriaClientModEvents {
    private VeyloriaClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(VeyloriaMenus.VEYLORIA_INVENTORY.get(), VeyloriaInventoryScreen::new);
    }
}
