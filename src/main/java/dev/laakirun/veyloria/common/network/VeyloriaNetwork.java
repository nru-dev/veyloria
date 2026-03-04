package dev.laakirun.veyloria.common.network;

import dev.laakirun.veyloria.client.VeyloriaClientState;
import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import dev.laakirun.veyloria.common.menu.VeyloriaInventoryMenu;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = VeyloriaConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VeyloriaNetwork {
    private static final String NETWORK_VERSION = "1";

    private VeyloriaNetwork() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(OpenInventoryPayload.TYPE, OpenInventoryPayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    openInventory(player);
                }
            }));
        registrar.playToServer(SelectActionSlotPayload.TYPE, SelectActionSlotPayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    VeyloriaServerRuntime.instance().playerLoadoutService().selectActionSlot(player, payload.actionSlot());
                }
            }));
        registrar.playToServer(UseConsumablePayload.TYPE, UseConsumablePayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    VeyloriaServerRuntime.instance().playerLoadoutService().useConsumable(player, payload.consumableSlot());
                }
            }));
        registrar.playToClient(ConsumableUseStatePayload.TYPE, ConsumableUseStatePayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (payload.active()) {
                    long deadlineTick = context.player() == null ? 0L : context.player().tickCount + 120L;
                    VeyloriaClientState.instance().startAutoConsumableUse(payload.consumableSlot(), deadlineTick);
                } else {
                    VeyloriaClientState.instance().stopAutoConsumableUse();
                }
            }));
        registrar.playToClient(LoadoutSnapshotPayload.TYPE, LoadoutSnapshotPayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                PlayerLoadoutData loadout = new PlayerLoadoutData();
                loadout.deserializeNBT(context.player().registryAccess(), payload.loadoutTag());
                VeyloriaClientState.instance().setLoadout(loadout);
            }));
    }

    private static void openInventory(ServerPlayer player) {
        if (player == null) {
            return;
        }
        VeyloriaServerRuntime.instance().playerLoadoutService().initializePlayer(player);
        player.openMenu(new SimpleMenuProvider(
            (containerId, playerInventory, owner) -> new VeyloriaInventoryMenu(containerId, playerInventory, player),
            Component.literal("Инвентарь")
        ));
    }

    public static LoadoutSnapshotPayload loadoutSnapshot(PlayerLoadoutData loadout, HolderLookup.Provider provider) {
        return new LoadoutSnapshotPayload(loadout.serializeNBT(provider));
    }

    public static ConsumableUseStatePayload consumableUseState(int consumableSlot, boolean active) {
        return new ConsumableUseStatePayload(consumableSlot, active);
    }

    public record OpenInventoryPayload() implements CustomPacketPayload {
        public static final Type<OpenInventoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "open_inventory"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenInventoryPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenInventoryPayload());

        @Override
        public Type<OpenInventoryPayload> type() {
            return TYPE;
        }
    }

    public record SelectActionSlotPayload(int actionSlot) implements CustomPacketPayload {
        public static final Type<SelectActionSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "select_action_slot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectActionSlotPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SelectActionSlotPayload::actionSlot, SelectActionSlotPayload::new);

        @Override
        public Type<SelectActionSlotPayload> type() {
            return TYPE;
        }
    }

    public record UseConsumablePayload(int consumableSlot) implements CustomPacketPayload {
        public static final Type<UseConsumablePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "use_consumable"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UseConsumablePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, UseConsumablePayload::consumableSlot, UseConsumablePayload::new);

        @Override
        public Type<UseConsumablePayload> type() {
            return TYPE;
        }
    }

    public record ConsumableUseStatePayload(int consumableSlot, boolean active) implements CustomPacketPayload {
        public static final Type<ConsumableUseStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "consumable_use_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConsumableUseStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                ConsumableUseStatePayload::consumableSlot,
                ByteBufCodecs.BOOL,
                ConsumableUseStatePayload::active,
                ConsumableUseStatePayload::new
            );

        @Override
        public Type<ConsumableUseStatePayload> type() {
            return TYPE;
        }
    }

    public record LoadoutSnapshotPayload(CompoundTag loadoutTag) implements CustomPacketPayload {
        public static final Type<LoadoutSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "loadout_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, LoadoutSnapshotPayload::loadoutTag, LoadoutSnapshotPayload::new);

        @Override
        public Type<LoadoutSnapshotPayload> type() {
            return TYPE;
        }
    }
}
