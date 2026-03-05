package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.entity.NpcEntity;
import dev.laakirun.veyloria.common.npc.NpcAppearance;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class NpcEntityRenderer extends MobRenderer<NpcEntity, NpcWitherModel> {
    private static final ResourceLocation WITHER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/wither/wither.png");

    public NpcEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcWitherModel(context.bakeLayer(ModelLayers.WITHER)), 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return switch (entity.appearance()) {
            case WITHER -> WITHER_TEXTURE;
        };
    }
}
