package dev.laakirun.veyloria.common.registry;

import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.item.RpgItemData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class VeyloriaAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VeyloriaConstants.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RpgItemData>> RPG_ITEM =
        ATTACHMENTS.register("rpg_item", () -> AttachmentType.serializable(RpgItemData::new).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerLoadoutData>> PLAYER_LOADOUT =
        ATTACHMENTS.register("player_loadout", () -> AttachmentType.serializable(PlayerLoadoutData::new).copyOnDeath().build());

    private VeyloriaAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
