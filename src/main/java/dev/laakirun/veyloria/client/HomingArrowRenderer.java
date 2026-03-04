package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.entity.HomingArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class HomingArrowRenderer extends ArrowRenderer<HomingArrowEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public HomingArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HomingArrowEntity arrow) {
        return TEXTURE;
    }
}
