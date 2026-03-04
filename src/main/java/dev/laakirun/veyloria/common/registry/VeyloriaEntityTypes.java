package dev.laakirun.veyloria.common.registry;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.entity.HomingArrowEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VeyloriaEntityTypes {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, VeyloriaConstants.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<HomingArrowEntity>> HOMING_ARROW =
        ENTITY_TYPES.register("homing_arrow", () -> EntityType.Builder.<HomingArrowEntity>of(HomingArrowEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(4)
            .updateInterval(20)
            .build(VeyloriaConstants.MOD_ID + ":homing_arrow"));

    private VeyloriaEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
