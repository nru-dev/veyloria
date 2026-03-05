package dev.laakirun.veyloria.common.registry;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.entity.HomingArrowEntity;
import dev.laakirun.veyloria.common.entity.NpcEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class VeyloriaEntityTypes {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, VeyloriaConstants.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<HomingArrowEntity>> HOMING_ARROW =
        ENTITY_TYPES.register("homing_arrow", () -> EntityType.Builder.<HomingArrowEntity>of(HomingArrowEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(4)
            .updateInterval(20)
            .build(VeyloriaConstants.MOD_ID + ":homing_arrow"));
    public static final DeferredHolder<EntityType<?>, EntityType<NpcEntity>> NPC =
        ENTITY_TYPES.register("npc", () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.CREATURE)
            .sized(0.9F, 3.5F)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build(VeyloriaConstants.MOD_ID + ":npc"));

    private VeyloriaEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    @EventBusSubscriber(modid = VeyloriaConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(NPC.get(), NpcEntity.createAttributes().build());
        }
    }
}
