package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.item.RpgItemData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class RpgItemUtils {
    private RpgItemUtils() {
    }

    public static RpgItemData read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag copied = data.copyTag();
        if (!copied.contains(RpgItemData.ROOT_KEY)) {
            return null;
        }
        return RpgItemData.fromTag(copied.getCompound(RpgItemData.ROOT_KEY));
    }
}
