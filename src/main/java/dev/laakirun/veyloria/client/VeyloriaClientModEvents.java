package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.registry.VeyloriaEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = VeyloriaConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VeyloriaClientModEvents {
    private VeyloriaClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VeyloriaEntityTypes.HOMING_ARROW.get(), HomingArrowRenderer::new);
        event.registerEntityRenderer(VeyloriaEntityTypes.NPC.get(), NpcEntityRenderer::new);
    }
}
